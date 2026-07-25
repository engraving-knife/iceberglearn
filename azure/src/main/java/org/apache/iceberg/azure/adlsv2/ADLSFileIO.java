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
package org.apache.iceberg.azure.adlsv2;

import com.azure.core.http.HttpClient;
import com.azure.core.util.Context;
import com.azure.storage.file.datalake.DataLakeFileClient;
import com.azure.storage.file.datalake.DataLakeFileSystemClient;
import com.azure.storage.file.datalake.DataLakeFileSystemClientBuilder;
import com.azure.storage.file.datalake.models.DataLakeStorageException;
import com.azure.storage.file.datalake.models.ListPathsOptions;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.iceberg.azure.AzureProperties;
import org.apache.iceberg.common.DynConstructors;
import org.apache.iceberg.io.BulkDeletionFailureException;
import org.apache.iceberg.io.DelegateFileIO;
import org.apache.iceberg.io.FileInfo;
import org.apache.iceberg.io.InputFile;
import org.apache.iceberg.io.OutputFile;
import org.apache.iceberg.metrics.MetricsContext;
import org.apache.iceberg.relocated.com.google.common.annotations.VisibleForTesting;
import org.apache.iceberg.util.SerializableMap;
import org.apache.iceberg.util.Tasks;
import org.apache.iceberg.util.ThreadPools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于 Azure Data Lake Storage Gen2 的 FileIO 实现。
 *
 * <p>所属模块：iceberg-azure（Azure 存储后端 FileIO 实现，是 Iceberg 对接 Azure 存储 的核心入口类）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>实现 {@link DelegateFileIO} 接口，提供文件的创建、读取、删除、列举等操作。
 *   <li>管理 ADLS 客户端的生命周期：根据 URI 解析 storage account / container / path， 构建 {@link
 *       DataLakeFileSystemClient} 和 {@link DataLakeFileClient}。
 *   <li>支持批量删除（并行）、前缀列举、目录删除等批量操作。
 *   <li>集成 Hadoop 指标上下文（可选），记录 IO 指标。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>无参构造 + {@code initialize} 两段式初始化：满足 Iceberg 通过反射动态加载 FileIO 的需求（引擎先实例化再注入配置）。
 *   <li>共享 {@link HttpClient}：所有 ADLS 请求复用同一个 HTTP 客户端实例，避免重复 创建连接池，提升连接复用率。
 *   <li>批量删除用线程池并行而非 Azure Batch API：因为 Batch API 不支持所有认证方式 （如 user delegation SAS），为保证兼容性采用逐个并行删除。
 *   <li>指标懒加载：尝试反射加载 HadoopMetricsContext，若 Hadoop 不在 classpath 则 回退到 null metrics，保证 azure
 *       模块可独立使用。
 * </ul>
 *
 * <p>上下游关系：
 *
 * <ul>
 *   <li>上游：由 Iceberg core 的 {@code FileIO} 工厂根据 catalog 配置动态加载。
 *   <li>下游：创建 {@link ADLSInputFile} / {@link ADLSOutputFile}，内部使用 {@link AzureProperties}
 *       管理认证、{@link ADLSLocation} 解析路径。
 * </ul>
 */
public class ADLSFileIO implements DelegateFileIO {

  private static final Logger LOG = LoggerFactory.getLogger(ADLSFileIO.class);
  private static final String DEFAULT_METRICS_IMPL =
      "org.apache.iceberg.hadoop.HadoopMetricsContext";

  private static final HttpClient HTTP = HttpClient.createDefault();

  private AzureProperties azureProperties;
  private MetricsContext metrics = MetricsContext.nullMetrics();
  private SerializableMap<String, String> properties;

  /**
   * 无参构造方法，用于动态加载 FileIO。
   *
   * <p>所有字段将在后续调用 {@link #initialize(Map)} 时初始化。
   */
  public ADLSFileIO() {}

  /**
   * 测试用构造方法，直接注入 {@link AzureProperties}。
   *
   * @param azureProperties 预构造的 Azure 配置
   */
  @VisibleForTesting
  ADLSFileIO(AzureProperties azureProperties) {
    this.azureProperties = azureProperties;
  }

  /**
   * 创建输入文件（读取侧）。
   *
   * @param path 文件完整 URI
   * @return {@link ADLSInputFile} 实例
   */
  @Override
  public InputFile newInputFile(String path) {
    return new ADLSInputFile(path, fileClient(path), azureProperties, metrics);
  }

  /**
   * 创建输入文件（读取侧），并指定已知文件长度。
   *
   * @param path 文件完整 URI
   * @param length 文件长度（字节）
   * @return {@link ADLSInputFile} 实例
   */
  @Override
  public InputFile newInputFile(String path, long length) {
    return new ADLSInputFile(path, length, fileClient(path), azureProperties, metrics);
  }

  /**
   * 创建输出文件（写入侧）。
   *
   * @param path 文件完整 URI
   * @return {@link ADLSOutputFile} 实例
   */
  @Override
  public OutputFile newOutputFile(String path) {
    return new ADLSOutputFile(path, fileClient(path), azureProperties, metrics);
  }

  /**
   * 删除指定路径的文件。
   *
   * <p>删除失败时仅记录警告日志而非抛出异常，与其他 FileIO 实现的行为一致 （删除非 Iceberg 核心必需操作）。
   *
   * @param path 文件完整 URI
   */
  @Override
  public void deleteFile(String path) {
    // There is no specific contract about whether delete should fail
    // and other FileIO providers ignore failure.  Log the failure for
    // now as it is not a required operation for Iceberg.
    try {
      fileClient(path).delete();
    } catch (DataLakeStorageException e) {
      LOG.warn("Failed to delete path: {}", path, e);
    }
  }

  /** 返回初始化时传入的配置属性（只读视图）。 */
  @Override
  public Map<String, String> properties() {
    return properties.immutableMap();
  }

  /**
   * 根据路径创建或获取 {@link DataLakeFileSystemClient}。
   *
   * @param path ADLS 文件 URI
   * @return 文件系统客户端
   */
  public DataLakeFileSystemClient client(String path) {
    ADLSLocation location = new ADLSLocation(path);
    return client(location);
  }

  /**
   * 根据 {@link ADLSLocation} 构建 {@link DataLakeFileSystemClient}。
   *
   * <p>逻辑：创建 {@link DataLakeFileSystemClientBuilder}，设置共享 HTTP 客户端； 若 location 指定了 container 则设为
   * fileSystemName；通过 {@link AzureProperties#applyClientConfiguration} 注入认证与端点配置； 最终构建客户端。
   *
   * @param location 已解析的 ADLS 位置
   * @return 文件系统客户端
   */
  @VisibleForTesting
  DataLakeFileSystemClient client(ADLSLocation location) {
    DataLakeFileSystemClientBuilder clientBuilder =
        new DataLakeFileSystemClientBuilder().httpClient(HTTP);

    location.container().ifPresent(clientBuilder::fileSystemName);
    azureProperties.applyClientConfiguration(location.storageAccount(), clientBuilder);

    return clientBuilder.buildClient();
  }

  /**
   * 根据路径获取 {@link DataLakeFileClient}（文件级客户端）。
   *
   * @param path ADLS 文件 URI
   * @return 文件客户端
   */
  private DataLakeFileClient fileClient(String path) {
    ADLSLocation location = new ADLSLocation(path);
    return client(location).getFileClient(location.path());
  }

  /**
   * 初始化 FileIO，加载配置并构建 {@link AzureProperties}。
   *
   * <p>逻辑：将配置包装为 {@link SerializableMap} 以支持序列化分发；构造 {@link AzureProperties}；尝试加载 Hadoop 指标上下文。
   *
   * @param props Iceberg 配置属性
   */
  @Override
  public void initialize(Map<String, String> props) {
    this.properties = SerializableMap.copyOf(props);
    this.azureProperties = new AzureProperties(properties);
    initMetrics(properties);
  }

  /**
   * 尝试加载 Hadoop 指标上下文。
   *
   * <p>逻辑：通过反射尝试实例化 {@code HadoopMetricsContext}，若 Hadoop 不在 classpath 或加载失败，则回退到 {@link
   * MetricsContext#nullMetrics()} 并记录警告。
   *
   * @param props 配置属性（传递给指标上下文初始化）
   */
  @SuppressWarnings("CatchBlockLogException")
  private void initMetrics(Map<String, String> props) {
    // Report Hadoop metrics if Hadoop is available
    try {
      DynConstructors.Ctor<MetricsContext> ctor =
          DynConstructors.builder(MetricsContext.class)
              .hiddenImpl(DEFAULT_METRICS_IMPL, String.class)
              .buildChecked();
      MetricsContext context = ctor.newInstance("adls");
      context.initialize(props);
      this.metrics = context;
    } catch (NoClassDefFoundError | NoSuchMethodException | ClassCastException e) {
      LOG.warn(
          "Unable to load metrics class: '{}', falling back to null metrics", DEFAULT_METRICS_IMPL);
    }
  }

  /**
   * 批量删除文件。
   *
   * <p>逻辑：使用 {@link ThreadPools#getWorkerPool()} 线程池并行执行 {@link #deleteFile}，不重试。统计失败数量，若有失败则抛出
   * {@link BulkDeletionFailureException}。
   *
   * <p>注意：Azure 批量操作 API 在某些认证方式下不可用（如 user delegation SAS）， 因此不使用 Batch API 而采用逐个并行删除。
   *
   * @param pathsToDelete 待删除文件路径集合
   * @throws BulkDeletionFailureException 部分文件删除失败
   */
  @Override
  public void deleteFiles(Iterable<String> pathsToDelete) throws BulkDeletionFailureException {
    // Azure batch operations are not supported in all cases, e.g. with a user
    // delegation SAS token, so avoid using it for now

    AtomicInteger failureCount = new AtomicInteger();
    Tasks.foreach(pathsToDelete)
        .executeWith(ThreadPools.getWorkerPool())
        .noRetry()
        .suppressFailureWhenFinished()
        .onFailure(
            (file, exc) -> {
              failureCount.incrementAndGet();
              LOG.warn("Failed to delete file {}", file, exc);
            })
        .run(this::deleteFile);

    if (failureCount.get() > 0) {
      throw new BulkDeletionFailureException(failureCount.get());
    }
  }

  /**
   * 列举指定前缀下的所有文件（递归）。
   *
   * <p>逻辑：解析前缀 URI，构建 {@link ListPathsOptions}（设置路径前缀和递归标志）， 调用 {@link
   * DataLakeFileSystemClient#listPaths} 获取路径列表；过滤掉目录项， 映射为 {@link FileInfo}（含路径名、大小、创建时间）。返回懒加载迭代器。
   *
   * <p>若 ADLS 返回 404（路径不存在），则返回空迭代器，与其他 FileIO 实现行为一致。
   *
   * @param prefix 文件路径前缀 URI
   * @return 文件信息迭代器
   */
  @Override
  public Iterable<FileInfo> listPrefix(String prefix) {
    ADLSLocation location = new ADLSLocation(prefix);

    ListPathsOptions options = new ListPathsOptions();
    options.setPath(location.path());
    options.setRecursive(true);

    return () -> {
      try {
        return client(location).listPaths(options, null).stream()
            .filter(pathItem -> !pathItem.isDirectory())
            .map(
                pathItem ->
                    new FileInfo(
                        pathItem.getName(),
                        pathItem.getContentLength(),
                        pathItem.getCreationTime().toInstant().toEpochMilli()))
            .iterator();
      } catch (DataLakeStorageException e) {
        // other FileIO implementations return an empty iterator if nothing
        // is found, so mimic that behavior here
        if (e.getStatusCode() != 404) {
          throw e;
        }
        return Collections.emptyIterator();
      }
    };
  }

  /**
   * 删除指定前缀下的整个目录（递归删除）。
   *
   * <p>逻辑：解析前缀 URI，调用 {@link DataLakeFileSystemClient#deleteDirectoryWithResponse} 递归删除目录。 若 ADLS
   * 返回 404（目录不存在），则静默跳过，与其他 FileIO 实现行为一致。
   *
   * @param prefix 目录路径前缀 URI
   */
  @Override
  public void deletePrefix(String prefix) {
    ADLSLocation location = new ADLSLocation(prefix);
    try {
      client(location)
          .deleteDirectoryWithResponse(location.path(), true, null, null, Context.NONE)
          .getValue();
    } catch (DataLakeStorageException e) {
      // other FileIO implementations skip the delete if nothing is found,
      // so mimic that behavior here
      if (e.getStatusCode() != 404) {
        throw e;
      }
    }
  }
}
