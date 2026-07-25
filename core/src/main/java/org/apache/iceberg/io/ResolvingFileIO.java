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
package org.apache.iceberg.io;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.hadoop.conf.Configuration;
import org.apache.iceberg.CatalogUtil;
import org.apache.iceberg.exceptions.ValidationException;
import org.apache.iceberg.hadoop.HadoopConfigurable;
import org.apache.iceberg.hadoop.SerializableConfiguration;
import org.apache.iceberg.relocated.com.google.common.annotations.VisibleForTesting;
import org.apache.iceberg.relocated.com.google.common.base.Joiner;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.relocated.com.google.common.collect.Iterators;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.util.SerializableMap;
import org.apache.iceberg.util.SerializableSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 文件级说明：根据 location scheme（如 s3/gs/abfs）自动选择对应 FileIO 实现的代理型 FileIO。
 *
 * <p>所属模块：iceberg-core。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>维护 scheme 到 DelegateFileIO 实现类的映射（s3->S3FileIO、gs->GCSFileIO、abfs->ADLSFileIO 等）。
 *   <li>对每个出现过的 scheme 缓存对应的 DelegateFileIO 实例，按 location 委托调用。
 *   <li>当目标实现类不可加载时，回退到 HadoopFileIO，保证基础可用性。
 *   <li>实现 {@link HadoopConfigurable}，支持 Kryo 序列化后重新注入 Hadoop Configuration。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>统一入口：调用方只需配置 ResolvingFileIO，即可透明访问多种对象存储，无需在 catalog 层 显式选择具体 FileIO 实现。
 *   <li>懒加载与缓存：每个 scheme 的 FileIO 仅在首次访问时通过 CatalogUtil 反射加载并缓存， 避免无谓的初始化开销。
 *   <li>未关闭资源兜底：构造时记录栈轨迹，finalize 时若未关闭则关闭并 WARN 日志，辅助发现资源泄漏。
 * </ul>
 *
 * <p>上下游关系：被 CatalogUtil.loadFileIO 作为默认 FileIO 加载；被各 TaskWriter / 表读写操作 通过 Table.io() 使用；委托给
 * S3FileIO/GCSFileIO/ADLSFileIO/HadoopFileIO 等具体实现。
 */
public class ResolvingFileIO implements HadoopConfigurable, DelegateFileIO {
  private static final Logger LOG = LoggerFactory.getLogger(ResolvingFileIO.class);
  private static final int BATCH_SIZE = 100_000;
  private static final String FALLBACK_IMPL = "org.apache.iceberg.hadoop.HadoopFileIO";
  private static final String S3_FILE_IO_IMPL = "org.apache.iceberg.aws.s3.S3FileIO";
  private static final String GCS_FILE_IO_IMPL = "org.apache.iceberg.gcp.gcs.GCSFileIO";
  private static final String ADLS_FILE_IO_IMPL = "org.apache.iceberg.azure.adlsv2.ADLSFileIO";
  private static final Map<String, String> SCHEME_TO_FILE_IO =
      ImmutableMap.of(
          "s3", S3_FILE_IO_IMPL,
          "s3a", S3_FILE_IO_IMPL,
          "s3n", S3_FILE_IO_IMPL,
          "gs", GCS_FILE_IO_IMPL,
          "abfs", ADLS_FILE_IO_IMPL,
          "abfss", ADLS_FILE_IO_IMPL);

  private final Map<String, DelegateFileIO> ioInstances = Maps.newConcurrentMap();
  private final AtomicBoolean isClosed = new AtomicBoolean(false);
  private final transient StackTraceElement[] createStack;
  private SerializableMap<String, String> properties;
  private SerializableSupplier<Configuration> hadoopConf;

  /**
   * 无参构造方法，供动态加载使用。
   *
   * <p>设计要点：所有字段在 {@link #initialize(Map)} 中初始化；构造时记录调用栈便于资源泄漏诊断。
   */
  public ResolvingFileIO() {
    createStack = Thread.currentThread().getStackTrace();
  }

  /** 委托给 location 对应的 DelegateFileIO 创建 InputFile。 */
  @Override
  public InputFile newInputFile(String location) {
    return io(location).newInputFile(location);
  }

  /** 委托给 location 对应的 DelegateFileIO 创建带已知长度的 InputFile。 */
  @Override
  public InputFile newInputFile(String location, long length) {
    return io(location).newInputFile(location, length);
  }

  /** 委托给 location 对应的 DelegateFileIO 创建 OutputFile。 */
  @Override
  public OutputFile newOutputFile(String location) {
    return io(location).newOutputFile(location);
  }

  /** 委托给 location 对应的 DelegateFileIO 删除文件。 */
  @Override
  public void deleteFile(String location) {
    io(location).deleteFile(location);
  }

  /**
   * 批量删除文件。
   *
   * <p>逻辑：按 BATCH_SIZE（10 万）分批；每批内按 location 对应的 DelegateFileIO 分组，分别委托 各
   * DelegateFileIO.deleteFiles 批量删除，从而利用各对象存储的原生批量删除能力。
   *
   * @param pathsToDelete 待删除文件路径集合
   * @throws BulkDeletionFailureException 批量删除失败时抛出
   */
  @Override
  public void deleteFiles(Iterable<String> pathsToDelete) throws BulkDeletionFailureException {
    Iterators.partition(pathsToDelete.iterator(), BATCH_SIZE)
        .forEachRemaining(
            partitioned -> {
              Map<DelegateFileIO, List<String>> pathByFileIO =
                  partitioned.stream().collect(Collectors.groupingBy(this::io));
              for (Map.Entry<DelegateFileIO, List<String>> entries : pathByFileIO.entrySet()) {
                DelegateFileIO io = entries.getKey();
                List<String> filePaths = entries.getValue();
                io.deleteFiles(filePaths);
              }
            });
  }

  /** 返回配置属性的不可变视图。 */
  @Override
  public Map<String, String> properties() {
    return properties.immutableMap();
  }

  /**
   * 用新配置重新初始化，会先关闭已存在的 FileIO 实例。
   *
   * <p>逻辑：调用 close() 释放旧的 DelegateFileIO 实例；拷贝新配置；重置 isClosed 为 false。
   *
   * @param newProperties 新的配置属性
   */
  @Override
  public void initialize(Map<String, String> newProperties) {
    close(); // close and discard any existing FileIO instances
    this.properties = SerializableMap.copyOf(newProperties);
    isClosed.set(false);
  }

  /**
   * 关闭所有委托的 FileIO 实例。
   *
   * <p>逻辑：CAS 将 isClosed 置 true；取出所有 ioInstances 中的实例；清空 ioInstances； 逐个 close。CAS 保证只关闭一次。
   */
  @Override
  public void close() {
    if (isClosed.compareAndSet(false, true)) {
      List<DelegateFileIO> instances = Lists.newArrayList();

      instances.addAll(ioInstances.values());
      ioInstances.clear();

      for (DelegateFileIO io : instances) {
        io.close();
      }
    }
  }

  /**
   * 用自定义序列化器包装当前 Hadoop Configuration，便于在 Kryo 等序列化框架中传输。
   *
   * @param confSerializer 将 Configuration 转为 SerializableSupplier 的函数
   */
  @Override
  public void serializeConfWith(
      Function<Configuration, SerializableSupplier<Configuration>> confSerializer) {
    this.hadoopConf = confSerializer.apply(hadoopConf.get());
  }

  /** 设置 Hadoop Configuration，包装为 SerializableSupplier。 */
  @Override
  public void setConf(Configuration conf) {
    this.hadoopConf = new SerializableConfiguration(conf)::get;
  }

  /** 返回 Hadoop Configuration。 */
  @Override
  public Configuration getConf() {
    return hadoopConf.get();
  }

  /**
   * 根据 location 的 scheme 获取（必要时创建）对应的 DelegateFileIO 实例。
   *
   * <p>逻辑：
   *
   * <ul>
   *   <li>由 {@link #implFromLocation(String)} 得到目标实现类名；
   *   <li>若 ioInstances 已缓存且为 HadoopConfigurable 但 conf 为 null（Kryo 序列化后丢失）， 则在同步块内重新注入 hadoopConf；
   *   <li>否则用 computeIfAbsent 反射加载：构造 props 副本（关闭 S3FileIO 内部栈追踪）， 调用 CatalogUtil.loadFileIO；失败时若非
   *       fallback 实现则回退到 HadoopFileIO， 仍失败则将后异常作为 suppressed 附加到原异常抛出；
   *   <li>校验结果实现了 DelegateFileIO 接口。
   * </ul>
   *
   * @param location 文件路径
   * @return 对应的 DelegateFileIO 实例
   */
  @VisibleForTesting
  DelegateFileIO io(String location) {
    String impl = implFromLocation(location);
    DelegateFileIO io = ioInstances.get(impl);
    if (io != null) {
      if (io instanceof HadoopConfigurable && ((HadoopConfigurable) io).getConf() == null) {
        synchronized (io) {
          if (((HadoopConfigurable) io).getConf() == null) {
            // re-apply the config in case it's null after Kryo serialization
            ((HadoopConfigurable) io).setConf(hadoopConf.get());
          }
        }
      }

      return io;
    }

    return ioInstances.computeIfAbsent(
        impl,
        key -> {
          Configuration conf = hadoopConf.get();
          FileIO fileIO;

          try {
            Map<String, String> props = Maps.newHashMap(properties);
            // ResolvingFileIO is keeping track of the creation stacktrace, so no need to do the
            // same in S3FileIO.
            props.put("init-creation-stacktrace", "false");
            fileIO = CatalogUtil.loadFileIO(key, props, conf);
          } catch (IllegalArgumentException e) {
            if (key.equals(FALLBACK_IMPL)) {
              // no implementation to fall back to, throw the exception
              throw e;
            } else {
              // couldn't load the normal class, fall back to HadoopFileIO
              LOG.warn(
                  "Failed to load FileIO implementation: {}, falling back to {}",
                  key,
                  FALLBACK_IMPL,
                  e);
              try {
                fileIO = CatalogUtil.loadFileIO(FALLBACK_IMPL, properties, conf);
              } catch (IllegalArgumentException suppressed) {
                LOG.warn(
                    "Failed to load FileIO implementation: {} (fallback)",
                    FALLBACK_IMPL,
                    suppressed);
                // both attempts failed, throw the original exception with the later exception
                // suppressed
                e.addSuppressed(suppressed);
                throw e;
              }
            }
          }

          Preconditions.checkState(
              fileIO instanceof DelegateFileIO,
              "FileIO does not implement DelegateFileIO: " + fileIO.getClass().getName());

          return (DelegateFileIO) fileIO;
        });
  }

  /**
   * 根据 location 的 scheme 查表得到 FileIO 实现类全限定名，未命中则返回 HadoopFileIO。
   *
   * @param location 文件路径
   * @return FileIO 实现类全限定名
   */
  @VisibleForTesting
  String implFromLocation(String location) {
    return SCHEME_TO_FILE_IO.getOrDefault(scheme(location), FALLBACK_IMPL);
  }

  /**
   * 返回 location 对应 FileIO 实现的 Class 对象。
   *
   * @param location 文件路径
   * @return FileIO 实现类
   * @throws ValidationException 类未找到时抛出
   */
  public Class<?> ioClass(String location) {
    String fileIOClassName = implFromLocation(location);
    try {
      return Class.forName(fileIOClassName);
    } catch (ClassNotFoundException e) {
      throw new ValidationException("Class %s not found : %s", fileIOClassName, e.getMessage());
    }
  }

  /**
   * 从 location 中提取 scheme（冒号前的部分）。
   *
   * @param location 文件路径
   * @return scheme 字符串；无冒号返回 null
   */
  private static String scheme(String location) {
    int colonPos = location.indexOf(":");
    if (colonPos > 0) {
      return location.substring(0, colonPos);
    }

    return null;
  }

  /**
   * GC 兜底：若实例未被显式 close 则在此关闭并打印创建栈，辅助发现资源泄漏。
   *
   * <p>设计要点：依赖 finalizer 不可靠，仅作为兜底；正式资源管理应显式 close。
   */
  @SuppressWarnings("checkstyle:NoFinalizer")
  @Override
  protected void finalize() throws Throwable {
    super.finalize();
    if (!isClosed.get()) {
      close();

      if (null != createStack) {
        String trace =
            Joiner.on("\n\t").join(Arrays.copyOfRange(createStack, 1, createStack.length));
        LOG.warn("Unclosed ResolvingFileIO instance created by:\n\t{}", trace);
      }
    }
  }

  /** 委托给 prefix 对应的 DelegateFileIO 列出前缀下所有文件。 */
  @Override
  public Iterable<FileInfo> listPrefix(String prefix) {
    return io(prefix).listPrefix(prefix);
  }

  /** 委托给 prefix 对应的 DelegateFileIO 删除前缀。 */
  @Override
  public void deletePrefix(String prefix) {
    io(prefix).deletePrefix(prefix);
  }
}
