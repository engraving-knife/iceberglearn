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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.RemoteIterator;
import org.apache.iceberg.exceptions.RuntimeIOException;
import org.apache.iceberg.io.BulkDeletionFailureException;
import org.apache.iceberg.io.DelegateFileIO;
import org.apache.iceberg.io.FileInfo;
import org.apache.iceberg.io.InputFile;
import org.apache.iceberg.io.OutputFile;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.relocated.com.google.common.collect.Streams;
import org.apache.iceberg.util.SerializableMap;
import org.apache.iceberg.util.SerializableSupplier;
import org.apache.iceberg.util.Tasks;
import org.apache.iceberg.util.ThreadPools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 文件级说明：基于 Hadoop {@link FileSystem} 的 {@link DelegateFileIO} 实现。
 *
 * <p>所属模块：iceberg-core 的 hadoop 包。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>实现 {@link DelegateFileIO}，提供基于 Hadoop {@link FileSystem} 的文件读、写、删除、 列举前缀、批量删除等能力。
 *   <li>实现 {@link HadoopConfigurable}，支持把不可序列化的 Hadoop {@link Configuration} 通过 {@link
 *       SerializableSupplier} 安全序列化，便于在分布式任务中分发。
 *   <li>支持并行批量删除文件，失败时按重试次数重试并统计失败数。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>使用 {@link SerializableSupplier} 持有 {@link Configuration}，默认以 {@link
 *       SerializableConfiguration} 包装，保证跨进程序列化。
 *   <li>删除线程池采用类级 {@code volatile} + 双检锁懒加载，进程内共享，避免重复创建。
 *   <li>批量删除通过 {@link Tasks} 工具编排重试与失败抑制。
 * </ul>
 *
 * <p>上下游关系：被 {@link HadoopCatalog}、{@link HadoopTables}、{@link HadoopTableOperations} 等作为表 IO
 * 使用；也可通过 catalog 属性 {@code file-io-impl} 动态加载。
 */
public class HadoopFileIO implements HadoopConfigurable, DelegateFileIO {

  private static final Logger LOG = LoggerFactory.getLogger(HadoopFileIO.class);
  private static final String DELETE_FILE_PARALLELISM = "iceberg.hadoop.delete-file-parallelism";
  private static final String DELETE_FILE_POOL_NAME = "iceberg-hadoopfileio-delete";
  private static final int DELETE_RETRY_ATTEMPTS = 3;
  private static final int DEFAULT_DELETE_CORE_MULTIPLE = 4;
  private static volatile ExecutorService executorService;

  private SerializableSupplier<Configuration> hadoopConf;
  private SerializableMap<String, String> properties = SerializableMap.copyOf(ImmutableMap.of());

  /**
   * 用于动态加载 FileIO 的无参构造方法。
   *
   * <p>设计要点：Hadoop {@link Configuration} 必须通过 {@link #setConf(Configuration)} 注入。
   */
  public HadoopFileIO() {}

  /**
   * 以 Hadoop {@link Configuration} 构造，内部用 {@link SerializableConfiguration} 包装以保证可序列化。
   *
   * @param hadoopConf Hadoop 配置
   */
  public HadoopFileIO(Configuration hadoopConf) {
    this(new SerializableConfiguration(hadoopConf)::get);
  }

  /**
   * 以可序列化的 {@link Configuration} supplier 构造。
   *
   * @param hadoopConf 提供 Hadoop {@link Configuration} 的可序列化 supplier
   */
  public HadoopFileIO(SerializableSupplier<Configuration> hadoopConf) {
    this.hadoopConf = hadoopConf;
  }

  /** 获取当前持有的 Hadoop {@link Configuration}。 */
  public Configuration conf() {
    return hadoopConf.get();
  }

  /**
   * 用给定属性初始化 FileIO（保存为可序列化副本）。
   *
   * @param props FileIO 属性
   */
  @Override
  public void initialize(Map<String, String> props) {
    this.properties = SerializableMap.copyOf(props);
  }

  /**
   * 创建指定路径的 {@link InputFile} 用于读取。
   *
   * @param path 文件路径字符串
   * @return 基于 Hadoop 的 {@link HadoopInputFile}
   */
  @Override
  public InputFile newInputFile(String path) {
    return HadoopInputFile.fromLocation(path, hadoopConf.get());
  }

  /**
   * 创建指定路径与已知长度的 {@link InputFile} 用于读取。
   *
   * @param path 文件路径字符串
   * @param length 已知文件长度
   * @return 基于 Hadoop 的 {@link HadoopInputFile}
   */
  @Override
  public InputFile newInputFile(String path, long length) {
    return HadoopInputFile.fromLocation(path, length, hadoopConf.get());
  }

  /**
   * 创建指定路径的 {@link OutputFile} 用于写入。
   *
   * @param path 文件路径字符串
   * @return 基于 Hadoop 的 {@link HadoopOutputFile}
   */
  @Override
  public OutputFile newOutputFile(String path) {
    return HadoopOutputFile.fromPath(new Path(path), hadoopConf.get());
  }

  /**
   * 删除指定路径的单个文件（非递归）。
   *
   * <p>逻辑：根据路径获取 {@link FileSystem}，调用 {@link FileSystem#delete(Path, boolean)} 且
   * recursive=false；IO 异常包装为 {@link RuntimeIOException}。
   *
   * @param path 待删除文件路径字符串
   */
  @Override
  public void deleteFile(String path) {
    Path toDelete = new Path(path);
    FileSystem fs = Util.getFs(toDelete, hadoopConf.get());
    try {
      fs.delete(toDelete, false /* not recursive */);
    } catch (IOException e) {
      throw new RuntimeIOException(e, "Failed to delete file: %s", path);
    }
  }

  /** 返回 FileIO 的只读属性。 */
  @Override
  public Map<String, String> properties() {
    return properties.immutableMap();
  }

  /**
   * 注入 Hadoop {@link Configuration}，并用 {@link SerializableConfiguration} 包装以保证可序列化。
   *
   * @param conf Hadoop 配置
   */
  @Override
  public void setConf(Configuration conf) {
    this.hadoopConf = new SerializableConfiguration(conf)::get;
  }

  /** 获取当前持有的 Hadoop {@link Configuration}。 */
  @Override
  public Configuration getConf() {
    return hadoopConf.get();
  }

  /**
   * 用调用方提供的函数序列化当前 Hadoop {@link Configuration}。
   *
   * <p>逻辑：把当前 {@link Configuration} 传入函数，用返回的可序列化 supplier 替换内部引用， 使本对象可安全序列化。
   *
   * @param confSerializer 把 {@link Configuration} 转为可序列化 supplier 的函数
   */
  @Override
  public void serializeConfWith(
      Function<Configuration, SerializableSupplier<Configuration>> confSerializer) {
    this.hadoopConf = confSerializer.apply(getConf());
  }

  /**
   * 列举指定前缀下的所有文件信息（递归）。
   *
   * <p>逻辑：获取 {@link FileSystem} 后调用 {@link FileSystem#listFiles(Path, boolean)} 且
   * recursive=true，并通过 {@link AdaptingIterator} 把 {@link RemoteIterator} 适配为 {@link Iterator}，再映射为
   * {@link FileInfo}（路径、长度、修改时间）的迭代器。 IO 异常包装为 {@link UncheckedIOException}。
   *
   * @param prefix 前缀路径字符串
   * @return 文件信息可迭代对象
   */
  @Override
  public Iterable<FileInfo> listPrefix(String prefix) {
    Path prefixToList = new Path(prefix);
    FileSystem fs = Util.getFs(prefixToList, hadoopConf.get());

    return () -> {
      try {
        return Streams.stream(
                new AdaptingIterator<>(fs.listFiles(prefixToList, true /* recursive */)))
            .map(
                fileStatus ->
                    new FileInfo(
                        fileStatus.getPath().toString(),
                        fileStatus.getLen(),
                        fileStatus.getModificationTime()))
            .iterator();
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    };
  }

  /**
   * 递归删除指定前缀路径。
   *
   * <p>逻辑：获取 {@link FileSystem} 后调用 {@link FileSystem#delete(Path, boolean)} 且 recursive=true； IO
   * 异常包装为 {@link UncheckedIOException}。
   *
   * @param prefix 前缀路径字符串
   */
  @Override
  public void deletePrefix(String prefix) {
    Path prefixToDelete = new Path(prefix);
    FileSystem fs = Util.getFs(prefixToDelete, hadoopConf.get());

    try {
      fs.delete(prefixToDelete, true /* recursive */);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * 批量并行删除多个文件，支持重试。
   *
   * <p>逻辑：使用 {@link Tasks} 编排，{@link #executorService()} 提供并行执行， 最多重试 {@link
   * #DELETE_RETRY_ATTEMPTS} 次，遇到 {@link FileNotFoundException} 停止重试， 完成时抑制失败；统计失败数，非零则抛 {@link
   * BulkDeletionFailureException}。
   *
   * @param pathsToDelete 待删除文件路径的可迭代集合
   * @throws BulkDeletionFailureException 当存在删除失败的文件时抛出
   */
  @Override
  public void deleteFiles(Iterable<String> pathsToDelete) throws BulkDeletionFailureException {
    AtomicInteger failureCount = new AtomicInteger(0);
    Tasks.foreach(pathsToDelete)
        .executeWith(executorService())
        .retry(DELETE_RETRY_ATTEMPTS)
        .stopRetryOn(FileNotFoundException.class)
        .suppressFailureWhenFinished()
        .onFailure(
            (f, e) -> {
              LOG.error("Failure during bulk delete on file: {} ", f, e);
              failureCount.incrementAndGet();
            })
        .run(this::deleteFile);

    if (failureCount.get() != 0) {
      throw new BulkDeletionFailureException(failureCount.get());
    }
  }

  /**
   * 计算批量删除使用的线程数。
   *
   * <p>逻辑：默认值为可用处理器数乘 {@link #DEFAULT_DELETE_CORE_MULTIPLE}， 可通过 {@link #DELETE_FILE_PARALLELISM}
   * 配置覆盖。
   *
   * @return 删除线程数
   */
  private int deleteThreads() {
    int defaultValue = Runtime.getRuntime().availableProcessors() * DEFAULT_DELETE_CORE_MULTIPLE;
    return conf().getInt(DELETE_FILE_PARALLELISM, defaultValue);
  }

  /**
   * 获取（按需懒创建）批量删除共享线程池。
   *
   * <p>逻辑：类级 {@code volatile} + 双检锁，确保进程内只创建一次。
   *
   * @return 删除线程池
   */
  private ExecutorService executorService() {
    if (executorService == null) {
      synchronized (HadoopFileIO.class) {
        if (executorService == null) {
          executorService = ThreadPools.newWorkerPool(DELETE_FILE_POOL_NAME, deleteThreads());
        }
      }
    }

    return executorService;
  }

  /**
   * 适配器：把 Hadoop {@link RemoteIterator} 转换为标准 {@link Iterator}。
   *
   * <p>设计要点：{@link RemoteIterator#hasNext()}/{@link RemoteIterator#next()} 抛出 {@link
   * IOException}，本类把它们包装为 {@link UncheckedIOException}，便于在 Stream API 中使用。
   *
   * @param <E> 元素类型
   */
  private static class AdaptingIterator<E> implements Iterator<E>, RemoteIterator<E> {
    private final RemoteIterator<E> delegate;

    AdaptingIterator(RemoteIterator<E> delegate) {
      this.delegate = delegate;
    }

    @Override
    public boolean hasNext() {
      try {
        return delegate.hasNext();
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    @Override
    public E next() {
      try {
        return delegate.next();
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }
}
