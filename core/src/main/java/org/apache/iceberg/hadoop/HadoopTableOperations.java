/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.iceberg.hadoop;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.iceberg.LocationProviders;
import org.apache.iceberg.LockManager;
import org.apache.iceberg.TableMetadata;
import org.apache.iceberg.TableMetadataParser;
import org.apache.iceberg.TableOperations;
import org.apache.iceberg.TableProperties;
import org.apache.iceberg.encryption.EncryptionManager;
import org.apache.iceberg.exceptions.CommitFailedException;
import org.apache.iceberg.exceptions.RuntimeIOException;
import org.apache.iceberg.exceptions.ValidationException;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.io.LocationProvider;
import org.apache.iceberg.relocated.com.google.common.annotations.VisibleForTesting;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.apache.iceberg.util.Pair;
import org.apache.iceberg.util.Tasks;
import org.apache.iceberg.util.ThreadPools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 文件级说明：基于文件系统原子 rename 的 {@link TableOperations} 实现。
 *
 * <p>所属模块：iceberg-core 的 hadoop 包。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>在表 location 的 {@code metadata/} 目录下维护版本化元数据文件（v1.metadata.json、v2 ...）。
 *   <li>通过 {@code version-hint.text} 文件记录当前最新版本号，加速启动时的版本定位。
 *   <li>以“写临时文件 + 原子 rename 到目标版本文件”的方式实现乐观并发提交。
 *   <li>支持按需删除被淘汰的历史元数据文件。
 * </ul>
 *
 * <p>设计意图：要求底层文件系统支持原子 rename（HDFS 满足，对象存储需特殊处理）。 提交时先写入随机名临时文件，再 rename 为 {@code
 * v<N>.metadata.json}； rename 失败说明已被其他并发提交占用，抛 {@link CommitFailedException}。 {@link LockManager}
 * 用于在 rename 前加锁，避免重复尝试。
 *
 * <p>上下游关系：由 {@link HadoopCatalog}、{@link HadoopTables} 创建； 使用 {@link FileIO} 读写元数据文件，使用 {@link
 * TableMetadataParser} 序列化元数据。
 */
public class HadoopTableOperations implements TableOperations {
  private static final Logger LOG = LoggerFactory.getLogger(HadoopTableOperations.class);
  private static final Pattern VERSION_PATTERN = Pattern.compile("v([^\\.]*)\\..*");

  private final Configuration conf;
  private final Path location;
  private final FileIO fileIO;
  private final LockManager lockManager;

  private volatile TableMetadata currentMetadata = null;
  private volatile Integer version = null;
  private volatile boolean shouldRefresh = true;

  /**
   * 构造表操作对象。
   *
   * @param location 表 location 路径
   * @param fileIO 用于读写元数据文件的 FileIO
   * @param conf Hadoop 配置
   * @param lockManager 提交时使用的锁管理器
   */
  protected HadoopTableOperations(
      Path location, FileIO fileIO, Configuration conf, LockManager lockManager) {
    this.conf = conf;
    this.location = location;
    this.fileIO = fileIO;
    this.lockManager = lockManager;
  }

  /**
   * 获取当前表元数据，必要时先刷新。
   *
   * <p>逻辑：若 {@code shouldRefresh} 为 true 则调用 {@link #refresh()} 重新加载， 否则直接返回缓存的 {@code
   * currentMetadata}。
   *
   * @return 当前表元数据；表不存在时返回 null
   */
  @Override
  public TableMetadata current() {
    if (shouldRefresh) {
      return refresh();
    }
    return currentMetadata;
  }

  /**
   * 原子地读取当前版本号与元数据（加锁）。
   *
   * @return 版本号与元数据的 Pair
   */
  private synchronized Pair<Integer, TableMetadata> versionAndMetadata() {
    return Pair.of(version, currentMetadata);
  }

  /**
   * 在版本号过期时更新版本号与元数据，并校验 UUID 一致性。
   *
   * <p>逻辑：仅当当前版本号为空或不等于 newVersion 时，重新读取元数据文件， 并通过 {@link #checkUUID(TableMetadata,
   * TableMetadata)} 校验表 UUID 未被外部篡改。
   *
   * @param newVersion 新版本号
   * @param metadataFile 新版本对应的元数据文件路径
   */
  private synchronized void updateVersionAndMetadata(int newVersion, String metadataFile) {
    // update if the current version is out of date
    if (version == null || version != newVersion) {
      this.version = newVersion;
      this.currentMetadata =
          checkUUID(currentMetadata, TableMetadataParser.read(io(), metadataFile));
    }
  }

  /**
   * 从文件系统刷新表元数据。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>取当前版本号（未知则用 {@link #findVersion()} 推断）。
   *   <li>读取该版本元数据文件；若版本为 0 且文件不存在，视为表尚未创建返回 null。
   *   <li>循环向后探测是否存在更高版本文件，找到则前进到最新版本。
   *   <li>调用 {@link #updateVersionAndMetadata(int, String)} 更新缓存并清空 shouldRefresh。
   * </ol>
   *
   * @return 最新表元数据；表不存在时返回 null
   * @throws ValidationException 元数据文件缺失时抛出
   * @throws RuntimeIOException 读取失败时抛出
   */
  @Override
  public TableMetadata refresh() {
    int ver = version != null ? version : findVersion();
    try {
      Path metadataFile = getMetadataFile(ver);
      if (version == null && metadataFile == null && ver == 0) {
        // no v0 metadata means the table doesn't exist yet
        return null;
      } else if (metadataFile == null) {
        throw new ValidationException("Metadata file for version %d is missing", ver);
      }

      Path nextMetadataFile = getMetadataFile(ver + 1);
      while (nextMetadataFile != null) {
        ver += 1;
        metadataFile = nextMetadataFile;
        nextMetadataFile = getMetadataFile(ver + 1);
      }

      updateVersionAndMetadata(ver, metadataFile.toString());

      this.shouldRefresh = false;
      return currentMetadata;
    } catch (IOException e) {
      throw new RuntimeIOException(e, "Failed to refresh the table");
    }
  }

  /**
   * 提交新的表元数据。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>乐观校验：base 必须与当前元数据一致，否则抛 {@link CommitFailedException}。
   *   <li>无变化时直接返回。
   *   <li>校验路径式表不可迁移 location、不可设置 WRITE_METADATA_LOCATION。
   *   <li>把新元数据写到随机名临时文件。
   *   <li>计算下一个版本号，通过 {@link #renameToFinal} 原子 rename 到目标版本文件。
   *   <li>更新 version-hint，按需删除被淘汰的历史元数据文件，标记需要刷新。
   * </ol>
   *
   * @param base 提交所基于的元数据
   * @param metadata 新的元数据
   * @throws CommitFailedException 并发冲突或 rename 失败时抛出
   */
  @Override
  public void commit(TableMetadata base, TableMetadata metadata) {
    Pair<Integer, TableMetadata> current = versionAndMetadata();
    if (base != current.second()) {
      throw new CommitFailedException("Cannot commit changes based on stale table metadata");
    }

    if (base == metadata) {
      LOG.info("Nothing to commit.");
      return;
    }

    Preconditions.checkArgument(
        base == null || base.location().equals(metadata.location()),
        "Hadoop path-based tables cannot be relocated");
    Preconditions.checkArgument(
        !metadata.properties().containsKey(TableProperties.WRITE_METADATA_LOCATION),
        "Hadoop path-based tables cannot relocate metadata");

    String codecName =
        metadata.property(
            TableProperties.METADATA_COMPRESSION, TableProperties.METADATA_COMPRESSION_DEFAULT);
    TableMetadataParser.Codec codec = TableMetadataParser.Codec.fromName(codecName);
    String fileExtension = TableMetadataParser.getFileExtension(codec);
    Path tempMetadataFile = metadataPath(UUID.randomUUID().toString() + fileExtension);
    TableMetadataParser.write(metadata, io().newOutputFile(tempMetadataFile.toString()));

    int nextVersion = (current.first() != null ? current.first() : 0) + 1;
    Path finalMetadataFile = metadataFilePath(nextVersion, codec);
    FileSystem fs = getFileSystem(tempMetadataFile, conf);

    // this rename operation is the atomic commit operation
    renameToFinal(fs, tempMetadataFile, finalMetadataFile, nextVersion);

    LOG.info("Committed a new metadata file {}", finalMetadataFile);

    // update the best-effort version pointer
    writeVersionHint(nextVersion);

    deleteRemovedMetadataFiles(base, metadata);

    this.shouldRefresh = true;
  }

  @Override
  public FileIO io() {
    return fileIO;
  }

  /**
   * 根据当前表 location 与属性构造 LocationProvider。
   *
   * @return 数据文件位置提供者
   */
  @Override
  public LocationProvider locationProvider() {
    return LocationProviders.locationsFor(current().location(), current().properties());
  }

  /**
   * 计算给定文件名在 metadata 目录下的完整路径。
   *
   * @param fileName 元数据文件名
   * @return 完整路径字符串
   */
  @Override
  public String metadataFileLocation(String fileName) {
    return metadataPath(fileName).toString();
  }

  /**
   * 返回一个以未提交元数据为视图的临时 TableOperations，供事务预演使用。
   *
   * @param uncommittedMetadata 未提交的元数据
   * @return 临时 TableOperations，不支持 refresh/commit
   */
  @Override
  public TableOperations temp(TableMetadata uncommittedMetadata) {
    return new TableOperations() {
      @Override
      public TableMetadata current() {
        return uncommittedMetadata;
      }

      @Override
      public TableMetadata refresh() {
        throw new UnsupportedOperationException(
            "Cannot call refresh on temporary table operations");
      }

      @Override
      public void commit(TableMetadata base, TableMetadata metadata) {
        throw new UnsupportedOperationException("Cannot call commit on temporary table operations");
      }

      @Override
      public String metadataFileLocation(String fileName) {
        return HadoopTableOperations.this.metadataFileLocation(fileName);
      }

      @Override
      public LocationProvider locationProvider() {
        return LocationProviders.locationsFor(
            uncommittedMetadata.location(), uncommittedMetadata.properties());
      }

      @Override
      public FileIO io() {
        return HadoopTableOperations.this.io();
      }

      @Override
      public EncryptionManager encryption() {
        return HadoopTableOperations.this.encryption();
      }

      @Override
      public long newSnapshotId() {
        return HadoopTableOperations.this.newSnapshotId();
      }
    };
  }

  /**
   * 查找指定版本号的元数据文件（尝试所有支持的压缩 codec）。
   *
   * <p>逻辑：遍历 {@link TableMetadataParser.Codec} 所有取值，按当前命名规则拼路径并检查存在性； 对 GZIP
   * 还需兼容旧命名（.metadata.json.gz）。
   *
   * @param metadataVersion 元数据版本号
   * @return 找到的文件路径；不存在返回 null
   * @throws IOException 检查存在性时发生 IO 异常
   */
  @VisibleForTesting
  Path getMetadataFile(int metadataVersion) throws IOException {
    for (TableMetadataParser.Codec codec : TableMetadataParser.Codec.values()) {
      Path metadataFile = metadataFilePath(metadataVersion, codec);
      FileSystem fs = getFileSystem(metadataFile, conf);
      if (fs.exists(metadataFile)) {
        return metadataFile;
      }

      if (codec.equals(TableMetadataParser.Codec.GZIP)) {
        // we have to be backward-compatible with .metadata.json.gz files
        metadataFile = oldMetadataFilePath(metadataVersion, codec);
        fs = getFileSystem(metadataFile, conf);
        if (fs.exists(metadataFile)) {
          return metadataFile;
        }
      }
    }

    return null;
  }

  private Path metadataFilePath(int metadataVersion, TableMetadataParser.Codec codec) {
    return metadataPath("v" + metadataVersion + TableMetadataParser.getFileExtension(codec));
  }

  private Path oldMetadataFilePath(int metadataVersion, TableMetadataParser.Codec codec) {
    return metadataPath("v" + metadataVersion + TableMetadataParser.getOldFileExtension(codec));
  }

  private Path metadataPath(String filename) {
    return new Path(metadataRoot(), filename);
  }

  private Path metadataRoot() {
    return new Path(location, "metadata");
  }

  /**
   * 从文件名中解析出版本号。
   *
   * <p>逻辑：用 {@link #VERSION_PATTERN} 匹配，取分组并转 int；不匹配或非数字返回 -1。
   *
   * @param fileName 元数据文件名
   * @return 版本号；无法解析返回 -1
   */
  private int version(String fileName) {
    Matcher matcher = VERSION_PATTERN.matcher(fileName);
    if (!matcher.matches()) {
      return -1;
    }
    String versionNumber = matcher.group(1);
    try {
      return Integer.parseInt(versionNumber);
    } catch (NumberFormatException ne) {
      return -1;
    }
  }

  /** 返回 version-hint 文件路径（仅供测试）。 */
  @VisibleForTesting
  Path versionHintFile() {
    return metadataPath(Util.VERSION_HINT_FILENAME);
  }

  /**
   * 尽力更新 version-hint 文件为最新版本号。
   *
   * <p>逻辑：先写随机名临时文件，再删除旧 hint，最后把临时文件 rename 为 hint。 失败仅告警不影响提交结果。
   *
   * @param versionToWrite 要写入的版本号
   */
  private void writeVersionHint(int versionToWrite) {
    Path versionHintFile = versionHintFile();
    FileSystem fs = getFileSystem(versionHintFile, conf);

    try {
      Path tempVersionHintFile = metadataPath(UUID.randomUUID().toString() + "-version-hint.temp");
      writeVersionToPath(fs, tempVersionHintFile, versionToWrite);
      fs.delete(versionHintFile, false /* recursive delete */);
      fs.rename(tempVersionHintFile, versionHintFile);
    } catch (IOException e) {
      LOG.warn("Failed to update version hint", e);
    }
  }

  /**
   * 把版本号以 UTF-8 文本写入指定路径（不覆盖已存在文件）。
   *
   * @param fs 文件系统
   * @param path 目标路径
   * @param versionToWrite 版本号
   * @throws IOException 写入失败时抛出
   */
  private void writeVersionToPath(FileSystem fs, Path path, int versionToWrite) throws IOException {
    try (FSDataOutputStream out = fs.create(path, false /* overwrite */)) {
      out.write(String.valueOf(versionToWrite).getBytes(StandardCharsets.UTF_8));
    }
  }

  /**
   * 查找当前最新版本号（仅供测试与内部使用）。
   *
   * <p>逻辑：优先读 version-hint 文件；读取失败时遍历 metadata 目录下所有 v*.metadata.json 文件， 取最大版本号兜底。表不存在时返回 0。
   *
   * @return 当前最新版本号
   */
  @VisibleForTesting
  int findVersion() {
    Path versionHintFile = versionHintFile();
    FileSystem fs = getFileSystem(versionHintFile, conf);

    try (InputStreamReader fsr =
            new InputStreamReader(fs.open(versionHintFile), StandardCharsets.UTF_8);
        BufferedReader in = new BufferedReader(fsr)) {
      return Integer.parseInt(in.readLine().replace("\n", ""));

    } catch (Exception e) {
      try {
        if (fs.exists(metadataRoot())) {
          LOG.warn("Error reading version hint file {}", versionHintFile, e);
        } else {
          LOG.debug("Metadata for table not found in directory {}", metadataRoot(), e);
          return 0;
        }

        // List the metadata directory to find the version files, and try to recover the max
        // available version
        FileStatus[] files =
            fs.listStatus(
                metadataRoot(), name -> VERSION_PATTERN.matcher(name.getName()).matches());
        int maxVersion = 0;

        for (FileStatus file : files) {
          int currentVersion = version(file.getPath().getName());
          if (currentVersion > maxVersion && getMetadataFile(currentVersion) != null) {
            maxVersion = currentVersion;
          }
        }

        return maxVersion;
      } catch (IOException io) {
        LOG.warn("Error trying to recover version-hint.txt data for {}", versionHintFile, e);
        return 0;
      }
    }
  }

  /**
   * 原子地把临时元数据文件 rename 为最终版本文件。
   *
   * <p>逻辑：通过 {@link LockManager} 加锁，校验目标文件不存在，再执行 rename； rename 失败时尝试删除临时文件并抛 {@link
   * CommitFailedException}。 finally 块释放锁。
   *
   * @param fs 文件系统
   * @param src 临时源文件
   * @param dst 最终目标文件
   * @param nextVersion 下一版本号
   * @throws CommitFailedException 目标已存在或 rename 失败时抛出
   */
  private void renameToFinal(FileSystem fs, Path src, Path dst, int nextVersion) {
    try {
      lockManager.acquire(dst.toString(), src.toString());
      if (fs.exists(dst)) {
        throw new CommitFailedException("Version %d already exists: %s", nextVersion, dst);
      }

      if (!fs.rename(src, dst)) {
        CommitFailedException cfe =
            new CommitFailedException("Failed to commit changes using rename: %s", dst);
        RuntimeException re = tryDelete(src);
        if (re != null) {
          cfe.addSuppressed(re);
        }
        throw cfe;
      }
    } catch (IOException e) {
      CommitFailedException cfe =
          new CommitFailedException(e, "Failed to commit changes using rename: %s", dst);
      RuntimeException re = tryDelete(src);
      if (re != null) {
        cfe.addSuppressed(re);
      }
      throw cfe;
    } finally {
      lockManager.release(dst.toString(), src.toString());
    }
  }

  /**
   * 尽力删除指定文件，捕获并返回运行期异常。
   *
   * @param path 待删除文件
   * @return 删除过程中捕获的 {@link RuntimeException}；成功返回 null
   */
  private RuntimeException tryDelete(Path path) {
    try {
      io().deleteFile(path.toString());
      return null;
    } catch (RuntimeException re) {
      return re;
    }
  }

  /**
   * 获取指定路径对应的 {@link FileSystem}。
   *
   * @param path Hadoop 路径
   * @param hadoopConf Hadoop 配置
   * @return 对应的 {@link FileSystem}
   */
  protected FileSystem getFileSystem(Path path, Configuration hadoopConf) {
    return Util.getFs(path, hadoopConf);
  }

  /**
   * 在提交后删除被淘汰的历史元数据文件。
   *
   * <p>逻辑：仅当 {@link TableProperties#METADATA_DELETE_AFTER_COMMIT_ENABLED} 为 true 时执行； 计算 base 与新
   * metadata 之间被移除的历史文件集合，在工作线程池中并行删除，失败仅告警。
   *
   * @param base 提交所基于的旧元数据
   * @param metadata 提交后的新元数据
   */
  private void deleteRemovedMetadataFiles(TableMetadata base, TableMetadata metadata) {
    if (base == null) {
      return;
    }

    boolean deleteAfterCommit =
        metadata.propertyAsBoolean(
            TableProperties.METADATA_DELETE_AFTER_COMMIT_ENABLED,
            TableProperties.METADATA_DELETE_AFTER_COMMIT_ENABLED_DEFAULT);

    if (deleteAfterCommit) {
      Set<TableMetadata.MetadataLogEntry> removedPreviousMetadataFiles =
          Sets.newHashSet(base.previousFiles());
      removedPreviousMetadataFiles.removeAll(metadata.previousFiles());
      Tasks.foreach(removedPreviousMetadataFiles)
          .executeWith(ThreadPools.getWorkerPool())
          .noRetry()
          .suppressFailureWhenFinished()
          .onFailure(
              (previousMetadataFile, exc) ->
                  LOG.warn(
                      "Delete failed for previous metadata file: {}", previousMetadataFile, exc))
          .run(previousMetadataFile -> io().deleteFile(previousMetadataFile.file()));
    }
  }

  /**
   * 校验刷新前后的表 UUID 一致，防止表被外部重建导致 UUID 漂移。
   *
   * @param currentMetadata 旧元数据
   * @param newMetadata 新元数据
   * @return 校验通过的新元数据
   * @throws IllegalStateException UUID 不一致时抛出
   */
  private static TableMetadata checkUUID(TableMetadata currentMetadata, TableMetadata newMetadata) {
    String newUUID = newMetadata.uuid();
    if (currentMetadata != null && currentMetadata.uuid() != null && newUUID != null) {
      Preconditions.checkState(
          newUUID.equals(currentMetadata.uuid()),
          "Table UUID does not match: current=%s != refreshed=%s",
          currentMetadata.uuid(),
          newUUID);
    }
    return newMetadata;
  }
}
