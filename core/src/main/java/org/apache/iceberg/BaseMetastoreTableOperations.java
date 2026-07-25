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
package org.apache.iceberg;

import static org.apache.iceberg.TableProperties.COMMIT_NUM_STATUS_CHECKS;
import static org.apache.iceberg.TableProperties.COMMIT_NUM_STATUS_CHECKS_DEFAULT;
import static org.apache.iceberg.TableProperties.COMMIT_STATUS_CHECKS_MAX_WAIT_MS;
import static org.apache.iceberg.TableProperties.COMMIT_STATUS_CHECKS_MAX_WAIT_MS_DEFAULT;
import static org.apache.iceberg.TableProperties.COMMIT_STATUS_CHECKS_MIN_WAIT_MS;
import static org.apache.iceberg.TableProperties.COMMIT_STATUS_CHECKS_MIN_WAIT_MS_DEFAULT;
import static org.apache.iceberg.TableProperties.COMMIT_STATUS_CHECKS_TOTAL_WAIT_MS;
import static org.apache.iceberg.TableProperties.COMMIT_STATUS_CHECKS_TOTAL_WAIT_MS_DEFAULT;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;
import org.apache.iceberg.encryption.EncryptionManager;
import org.apache.iceberg.exceptions.AlreadyExistsException;
import org.apache.iceberg.exceptions.CommitFailedException;
import org.apache.iceberg.exceptions.NoSuchTableException;
import org.apache.iceberg.exceptions.NotFoundException;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.io.LocationProvider;
import org.apache.iceberg.io.OutputFile;
import org.apache.iceberg.relocated.com.google.common.base.Objects;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.apache.iceberg.util.LocationUtil;
import org.apache.iceberg.util.PropertyUtil;
import org.apache.iceberg.util.Tasks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于元数据存储（metastore）的 {@link TableOperations} 抽象基类。
 *
 * <p>所属模块：iceberg-core（表操作核心抽象层，被各 catalog 集成模块继承）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>封装表元数据加载、刷新、提交的通用流程：包括元数据文件读写、版本解析、提交状态检查。
 *   <li>提供元数据文件位置计算、临时 {@link TableOperations} 视图、提交后历史元数据清理等能力。
 *   <li>处理与 metastore 通信时的常见问题：负缓存、重试、UUID 一致性校验。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>把"元数据文件存储在文件系统、元数据位置指针存储在 metastore"这一通用模式抽象出来， HiveCatalog/JdbcCatalog/RestCatalog 等子类只需实现
 *       {@link #doRefresh()} 和 {@link #doCommit()}。
 *   <li>提交采用乐观并发：先把新元数据文件写到存储，再尝试在 metastore 上 CAS 更新指针； 失败后通过 {@link #checkCommitStatus}
 *       检查实际是否成功，以应对网络抖动等不确定场景。
 *   <li>使用 UUID 标识防止不同表混淆；使用 overwrite 写入以规避 S3 负缓存。
 * </ul>
 *
 * <p>上下游关系：依赖 {@link FileIO}、{@link TableMetadataParser}；被各 catalog 子类 （如
 * HiveTableOperations、JdbcTableOperations）继承使用。
 */
public abstract class BaseMetastoreTableOperations implements TableOperations {
  private static final Logger LOG = LoggerFactory.getLogger(BaseMetastoreTableOperations.class);

  public static final String TABLE_TYPE_PROP = "table_type";
  public static final String ICEBERG_TABLE_TYPE_VALUE = "iceberg";
  public static final String METADATA_LOCATION_PROP = "metadata_location";
  public static final String PREVIOUS_METADATA_LOCATION_PROP = "previous_metadata_location";

  private static final String METADATA_FOLDER_NAME = "metadata";

  private TableMetadata currentMetadata = null;
  private String currentMetadataLocation = null;
  private boolean shouldRefresh = true;
  private int version = -1;

  protected BaseMetastoreTableOperations() {}

  /**
   * 返回用于日志输出的表全限定名（如 catalogName.database.table），仅用于日志，不参与业务逻辑。
   *
   * @return 表全名
   */
  protected abstract String tableName();

  /**
   * 返回当前表元数据；若标记需要刷新，则先触发 {@link #refresh()}。
   *
   * @return 当前 {@link TableMetadata}
   */
  @Override
  public TableMetadata current() {
    if (shouldRefresh) {
      return refresh();
    }
    return currentMetadata;
  }

  /**
   * 返回当前元数据文件位置。
   *
   * @return 当前元数据文件路径
   */
  public String currentMetadataLocation() {
    return currentMetadataLocation;
  }

  /**
   * 返回当前元数据文件版本号。
   *
   * @return 版本号；未加载时返回 -1
   */
  public int currentVersion() {
    return version;
  }

  /**
   * 从 metastore 刷新表元数据。
   *
   * <p>逻辑：调用 {@link #doRefresh()} 触发子类实现；若刷新过程中表不存在（NoSuchTableException），
   * 则清空本地缓存并将状态置为"需要刷新"，再向上抛出异常。
   *
   * @return 刷新后的 {@link TableMetadata}
   * @throws org.apache.iceberg.exceptions.NoSuchTableException 若表在 metastore 中已不存在
   */
  @Override
  public TableMetadata refresh() {
    boolean currentMetadataWasAvailable = currentMetadata != null;
    try {
      doRefresh();
    } catch (NoSuchTableException e) {
      if (currentMetadataWasAvailable) {
        LOG.warn("Could not find the table during refresh, setting current metadata to null", e);
        shouldRefresh = true;
      }

      currentMetadata = null;
      currentMetadataLocation = null;
      version = -1;
      throw e;
    }
    return current();
  }

  /**
   * 子类实现：从 metastore 实际加载最新元数据位置并刷新本地状态。
   *
   * <p>默认抛出 {@link UnsupportedOperationException}，子类必须覆盖。
   */
  protected void doRefresh() {
    throw new UnsupportedOperationException("Not implemented: doRefresh");
  }

  /**
   * 提交表元数据（乐观并发）。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>校验 base 与当前元数据一致（CAS 前置条件）；若不一致说明元数据过期或表已存在， 抛出对应异常。
   *   <li>若 base 与 metadata 是同一引用（无变更）则直接返回。
   *   <li>调用 {@link #doCommit} 执行实际提交，再清理被移除的历史元数据文件，最后标记需要刷新。
   * </ol>
   *
   * @param base 提交所基于的元数据快照
   * @param metadata 新元数据
   * @throws CommitFailedException 元数据已过期
   * @throws AlreadyExistsException 表已存在（base 为 null 但表存在时）
   */
  @Override
  public void commit(TableMetadata base, TableMetadata metadata) {
    // if the metadata is already out of date, reject it
    if (base != current()) {
      if (base != null) {
        throw new CommitFailedException("Cannot commit: stale table metadata");
      } else {
        // when current is non-null, the table exists. but when base is null, the commit is trying
        // to create the table
        throw new AlreadyExistsException("Table already exists: %s", tableName());
      }
    }
    // if the metadata is not changed, return early
    if (base == metadata) {
      LOG.info("Nothing to commit.");
      return;
    }

    long start = System.currentTimeMillis();
    doCommit(base, metadata);
    deleteRemovedMetadataFiles(base, metadata);
    requestRefresh();

    LOG.info(
        "Successfully committed to table {} in {} ms",
        tableName(),
        System.currentTimeMillis() - start);
  }

  /**
   * 子类实现：执行实际的 metastore 提交。
   *
   * <p>默认抛出 {@link UnsupportedOperationException}，子类必须覆盖。
   *
   * @param base 提交所基于的元数据
   * @param metadata 新元数据
   */
  protected void doCommit(TableMetadata base, TableMetadata metadata) {
    throw new UnsupportedOperationException("Not implemented: doCommit");
  }

  /** 标记下次访问 current() 时需要刷新。 */
  protected void requestRefresh() {
    this.shouldRefresh = true;
  }

  /** 禁用自动刷新，常用于子类在 refresh 内主动控制状态。 */
  protected void disableRefresh() {
    this.shouldRefresh = false;
  }

  /**
   * 必要时写入新的元数据文件，并返回其路径。
   *
   * <p>逻辑：若为新表且元数据已带文件位置，则直接复用该位置；否则调用 {@link #writeNewMetadata} 写入新版本文件。
   *
   * @param newTable 是否为新建表
   * @param metadata 待写入的元数据
   * @return 元数据文件路径
   */
  protected String writeNewMetadataIfRequired(boolean newTable, TableMetadata metadata) {
    return newTable && metadata.metadataFileLocation() != null
        ? metadata.metadataFileLocation()
        : writeNewMetadata(metadata, currentVersion() + 1);
  }

  /**
   * 把元数据写入新版本文件并返回路径。
   *
   * <p>设计要点：使用 {@link TableMetadataParser#overwrite} 覆盖写入以规避 S3 负缓存。 由于文件名含
   * UUID，覆盖写入也是安全的（路径不会与其他提交冲突）。
   *
   * @param metadata 待写入的元数据
   * @param newVersion 新版本号
   * @return 新元数据文件路径
   */
  protected String writeNewMetadata(TableMetadata metadata, int newVersion) {
    String newTableMetadataFilePath = newTableMetadataFilePath(metadata, newVersion);
    OutputFile newMetadataLocation = io().newOutputFile(newTableMetadataFilePath);

    // write the new metadata
    // use overwrite to avoid negative caching in S3. this is safe because the metadata location is
    // always unique because it includes a UUID.
    TableMetadataParser.overwrite(metadata, newMetadataLocation);

    return newMetadataLocation.location();
  }

  /**
   * 从给定元数据文件位置刷新本地状态（默认 20 次重试）。
   *
   * @param newLocation 新的元数据文件路径
   */
  protected void refreshFromMetadataLocation(String newLocation) {
    refreshFromMetadataLocation(newLocation, null, 20);
  }

  /**
   * 从给定元数据文件位置刷新本地状态（指定重试次数）。
   *
   * @param newLocation 新的元数据文件路径
   * @param numRetries 重试次数
   */
  protected void refreshFromMetadataLocation(String newLocation, int numRetries) {
    refreshFromMetadataLocation(newLocation, null, numRetries);
  }

  /**
   * 从给定元数据文件位置刷新本地状态（指定重试条件与次数，使用默认加载器）。
   *
   * @param newLocation 新的元数据文件路径
   * @param shouldRetry 自定义重试判断；可为 null
   * @param numRetries 重试次数
   */
  protected void refreshFromMetadataLocation(
      String newLocation, Predicate<Exception> shouldRetry, int numRetries) {
    refreshFromMetadataLocation(
        newLocation,
        shouldRetry,
        numRetries,
        metadataLocation -> TableMetadataParser.read(io(), metadataLocation));
  }

  /**
   * 从给定元数据文件位置刷新本地状态（核心实现）。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>仅当新位置与当前位置不同时才执行加载（新表位置可能为 null，使用 null-safe 比较）。
   *   <li>使用 {@link Tasks} 框架按指数退避重试加载元数据文件，遇到 NotFoundException 立即停止。
   *   <li>校验新元数据的 UUID 与本地缓存 UUID 一致，防止元数据被替换为其他表的元数据。
   *   <li>更新 currentMetadata、currentMetadataLocation、version，并关闭 shouldRefresh。
   * </ol>
   *
   * @param newLocation 新的元数据文件路径
   * @param shouldRetry 自定义重试判断；可为 null
   * @param numRetries 重试次数
   * @param metadataLoader 元数据加载函数
   */
  protected void refreshFromMetadataLocation(
      String newLocation,
      Predicate<Exception> shouldRetry,
      int numRetries,
      Function<String, TableMetadata> metadataLoader) {
    // use null-safe equality check because new tables have a null metadata location
    if (!Objects.equal(currentMetadataLocation, newLocation)) {
      LOG.info("Refreshing table metadata from new version: {}", newLocation);

      AtomicReference<TableMetadata> newMetadata = new AtomicReference<>();
      Tasks.foreach(newLocation)
          .retry(numRetries)
          .exponentialBackoff(100, 5000, 600000, 4.0 /* 100, 400, 1600, ... */)
          .throwFailureWhenFinished()
          .stopRetryOn(NotFoundException.class) // overridden if shouldRetry is non-null
          .shouldRetryTest(shouldRetry)
          .run(metadataLocation -> newMetadata.set(metadataLoader.apply(metadataLocation)));

      String newUUID = newMetadata.get().uuid();
      if (currentMetadata != null && currentMetadata.uuid() != null && newUUID != null) {
        Preconditions.checkState(
            newUUID.equals(currentMetadata.uuid()),
            "Table UUID does not match: current=%s != refreshed=%s",
            currentMetadata.uuid(),
            newUUID);
      }

      this.currentMetadata = newMetadata.get();
      this.currentMetadataLocation = newLocation;
      this.version = parseVersion(newLocation);
    }
    this.shouldRefresh = false;
  }

  /**
   * 计算元数据文件存放位置：优先使用表属性 {@link TableProperties#WRITE_METADATA_LOCATION} 指定的自定义路径，否则使用默认的
   * {tableLocation}/metadata/ 目录。
   *
   * @param metadata 表元数据
   * @param filename 文件名
   * @return 完整元数据文件路径
   */
  private String metadataFileLocation(TableMetadata metadata, String filename) {
    String metadataLocation = metadata.properties().get(TableProperties.WRITE_METADATA_LOCATION);

    if (metadataLocation != null) {
      return String.format("%s/%s", LocationUtil.stripTrailingSlash(metadataLocation), filename);
    } else {
      return String.format("%s/%s/%s", metadata.location(), METADATA_FOLDER_NAME, filename);
    }
  }

  /**
   * 基于当前元数据计算元数据文件位置（{@link TableOperations} 接口实现）。
   *
   * @param filename 文件名
   * @return 完整元数据文件路径
   */
  @Override
  public String metadataFileLocation(String filename) {
    return metadataFileLocation(current(), filename);
  }

  /**
   * 根据当前表位置和属性构造 {@link LocationProvider}。
   *
   * @return 位置提供者
   */
  @Override
  public LocationProvider locationProvider() {
    return LocationProviders.locationsFor(current().location(), current().properties());
  }

  /**
   * 返回一个临时 {@link TableOperations} 视图，使用未提交的元数据。
   *
   * <p>设计意图：在提交尚未完成时，调用方可能需要基于"假设提交成功"的元数据执行后续操作 （例如写入数据文件）。临时视图只读，不支持 refresh/commit，避免误用。
   *
   * @param uncommittedMetadata 未提交的元数据
   * @return 临时 TableOperations 视图
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
        return BaseMetastoreTableOperations.this.metadataFileLocation(
            uncommittedMetadata, fileName);
      }

      @Override
      public LocationProvider locationProvider() {
        return LocationProviders.locationsFor(
            uncommittedMetadata.location(), uncommittedMetadata.properties());
      }

      @Override
      public FileIO io() {
        return BaseMetastoreTableOperations.this.io();
      }

      @Override
      public EncryptionManager encryption() {
        return BaseMetastoreTableOperations.this.encryption();
      }

      @Override
      public long newSnapshotId() {
        return BaseMetastoreTableOperations.this.newSnapshotId();
      }
    };
  }

  /** 提交状态枚举：成功 / 失败 / 未知。 */
  protected enum CommitStatus {
    FAILURE,
    SUCCESS,
    UNKNOWN
  }

  /**
   * 检查提交状态：当提交异常无法判断成功与否时，回查 metastore 中是否已写入目标元数据位置。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>从表属性读取重试次数与退避参数（min/max/total 等待时间）。
   *   <li>使用 {@link Tasks} 按指数退避重试：每次刷新表元数据，比对当前或历史元数据位置是否 等于 newMetadataLocation；命中则视为 SUCCESS。
   *   <li>遍历完所有重试仍未命中则保持 UNKNOWN，并在日志中记录。
   * </ol>
   *
   * <p>设计意图：分布式提交场景下，metastore 写入可能成功但响应丢失，需要通过回查避免 误判失败而重复提交。
   *
   * @param newMetadataLocation 本次提交写入的元数据文件路径
   * @param config 用于读取提交检查相关配置的元数据
   * @return 提交状态
   */
  protected CommitStatus checkCommitStatus(String newMetadataLocation, TableMetadata config) {
    int maxAttempts =
        PropertyUtil.propertyAsInt(
            config.properties(), COMMIT_NUM_STATUS_CHECKS, COMMIT_NUM_STATUS_CHECKS_DEFAULT);
    long minWaitMs =
        PropertyUtil.propertyAsLong(
            config.properties(),
            COMMIT_STATUS_CHECKS_MIN_WAIT_MS,
            COMMIT_STATUS_CHECKS_MIN_WAIT_MS_DEFAULT);
    long maxWaitMs =
        PropertyUtil.propertyAsLong(
            config.properties(),
            COMMIT_STATUS_CHECKS_MAX_WAIT_MS,
            COMMIT_STATUS_CHECKS_MAX_WAIT_MS_DEFAULT);
    long totalRetryMs =
        PropertyUtil.propertyAsLong(
            config.properties(),
            COMMIT_STATUS_CHECKS_TOTAL_WAIT_MS,
            COMMIT_STATUS_CHECKS_TOTAL_WAIT_MS_DEFAULT);

    AtomicReference<CommitStatus> status = new AtomicReference<>(CommitStatus.UNKNOWN);

    Tasks.foreach(newMetadataLocation)
        .retry(maxAttempts)
        .suppressFailureWhenFinished()
        .exponentialBackoff(minWaitMs, maxWaitMs, totalRetryMs, 2.0)
        .onFailure(
            (location, checkException) ->
                LOG.error("Cannot check if commit to {} exists.", tableName(), checkException))
        .run(
            location -> {
              TableMetadata metadata = refresh();
              String currentMetadataFileLocation = metadata.metadataFileLocation();
              boolean commitSuccess =
                  currentMetadataFileLocation.equals(newMetadataLocation)
                      || metadata.previousFiles().stream()
                          .anyMatch(log -> log.file().equals(newMetadataLocation));
              if (commitSuccess) {
                LOG.info(
                    "Commit status check: Commit to {} of {} succeeded",
                    tableName(),
                    newMetadataLocation);
                status.set(CommitStatus.SUCCESS);
              } else {
                LOG.warn(
                    "Commit status check: Commit to {} of {} unknown, new metadata location is not current "
                        + "or in history",
                    tableName(),
                    newMetadataLocation);
              }
            });

    if (status.get() == CommitStatus.UNKNOWN) {
      LOG.error(
          "Cannot determine commit state to {}. Failed during checking {} times. "
              + "Treating commit state as unknown.",
          tableName(),
          maxAttempts);
    }
    return status.get();
  }

  /**
   * 计算新元数据文件的完整路径：版本号 5 位补零 + UUID + 压缩扩展名。
   *
   * @param meta 表元数据
   * @param newVersion 新版本号
   * @return 新元数据文件路径
   */
  private String newTableMetadataFilePath(TableMetadata meta, int newVersion) {
    String codecName =
        meta.property(
            TableProperties.METADATA_COMPRESSION, TableProperties.METADATA_COMPRESSION_DEFAULT);
    String fileExtension = TableMetadataParser.getFileExtension(codecName);
    return metadataFileLocation(
        meta, String.format("%05d-%s%s", newVersion, UUID.randomUUID(), fileExtension));
  }

  /**
   * 从元数据文件路径解析版本号。
   *
   * <p>逻辑：取文件名起始到第一个 '-' 之间的数字部分；若无 '-' 或非数字则返回 -1， 表示该路径不属于本 catalog 管理的元数据文件（例如文件系统表的元数据）。
   *
   * @param metadataLocation 表元数据文件路径
   * @return 版本号；无法解析时返回 -1
   */
  private static int parseVersion(String metadataLocation) {
    int versionStart = metadataLocation.lastIndexOf('/') + 1; // if '/' isn't found, this will be 0
    int versionEnd = metadataLocation.indexOf('-', versionStart);
    if (versionEnd < 0) {
      // found filesystem table's metadata
      return -1;
    }

    try {
      return Integer.valueOf(metadataLocation.substring(versionStart, versionEnd));
    } catch (NumberFormatException e) {
      LOG.warn("Unable to parse version from metadata location: {}", metadataLocation, e);
      return -1;
    }
  }

  /**
   * 在提交成功后，按配置删除被移出历史日志的元数据文件。
   *
   * <p>逻辑：仅当 {@link TableProperties#METADATA_DELETE_AFTER_COMMIT_ENABLED} 为 true 时执行； 计算
   * base.previousFiles 与 metadata.previousFiles 的差集（即被新元数据日志裁剪掉的旧文件）， 逐个删除，删除失败仅告警不影响主流程。
   *
   * @param base 提交前的元数据
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
      // TableMetadata#addPreviousFile builds up the metadata log and uses
      // TableProperties.METADATA_PREVIOUS_VERSIONS_MAX to determine how many files should stay in
      // the log, thus we don't include metadata.previousFiles() for deletion - everything else can
      // be removed
      removedPreviousMetadataFiles.removeAll(metadata.previousFiles());
      Tasks.foreach(removedPreviousMetadataFiles)
          .noRetry()
          .suppressFailureWhenFinished()
          .onFailure(
              (previousMetadataFile, exc) ->
                  LOG.warn(
                      "Delete failed for previous metadata file: {}", previousMetadataFile, exc))
          .run(previousMetadataFile -> io().deleteFile(previousMetadataFile.file()));
    }
  }
}
