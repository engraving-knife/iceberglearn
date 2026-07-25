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
package org.apache.iceberg.gcp.gcs;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.OAuth2Credentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.apache.iceberg.common.DynConstructors;
import org.apache.iceberg.gcp.GCPProperties;
import org.apache.iceberg.io.BulkDeletionFailureException;
import org.apache.iceberg.io.DelegateFileIO;
import org.apache.iceberg.io.FileInfo;
import org.apache.iceberg.io.InputFile;
import org.apache.iceberg.io.OutputFile;
import org.apache.iceberg.metrics.MetricsContext;
import org.apache.iceberg.relocated.com.google.common.collect.Iterators;
import org.apache.iceberg.relocated.com.google.common.collect.Streams;
import org.apache.iceberg.util.SerializableMap;
import org.apache.iceberg.util.SerializableSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于 Google Cloud Storage (GCS) 的 FileIO 实现。
 *
 * <p>所属模块：iceberg-gcp（GCP 集成模块，处于 Iceberg 存储访问层，是 Iceberg 与 GCS 对象存储之间的核心桥梁）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>实现 {@link DelegateFileIO} 接口，提供基于 GCS 的文件读写、删除、列举等操作。
 *   <li>管理 GCS {@link Storage} 客户端的懒初始化与生命周期。
 *   <li>支持批量删除（按 configurable batch size 分批），支持按前缀列举和删除。
 *   <li>可选集成 Hadoop 指标上下文（HadoopMetricsContext）以收集 IO 指标。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>位置格式遵循 {@code gs://<bucket>/<blob_path>}，与 {@link BlobId#fromGsUtilUri(String)} 约定一致。
 *   <li>Storage 客户端使用 {@link SerializableSupplier} 延迟创建并双重检查锁保证线程安全， 同时标记 {@code transient
 *       volatile} 避免序列化传输。
 *   <li>无参构造器用于动态加载（SPI 风格），实际配置通过 {@link #initialize(Map)} 注入。
 *   <li>deleteFile 失败仅记录日志不抛异常，与其他 FileIO 实现保持一致。
 * </ul>
 *
 * <p>上下游关系：被 iceberg-core 的 catalog / commit 层调用以读写元数据和数据文件； 依赖 GCP Storage SDK 和 {@link
 * GCPProperties}。
 *
 * <p>位置格式参见 <a href="https://cloud.google.com/storage/docs/folders#overview">Cloud Storage
 * Overview</a>
 */
public class GCSFileIO implements DelegateFileIO {
  private static final Logger LOG = LoggerFactory.getLogger(GCSFileIO.class);
  private static final String DEFAULT_METRICS_IMPL =
      "org.apache.iceberg.hadoop.HadoopMetricsContext";

  private SerializableSupplier<Storage> storageSupplier;
  private GCPProperties gcpProperties;
  private transient volatile Storage storage;
  private MetricsContext metrics = MetricsContext.nullMetrics();
  private final AtomicBoolean isResourceClosed = new AtomicBoolean(false);
  private SerializableMap<String, String> properties = null;

  /**
   * 无参构造器，用于动态加载 FileIO。
   *
   * <p>所有字段在后续调用 {@link #initialize(Map)} 时初始化。
   */
  public GCSFileIO() {}

  /**
   * 使用自定义 Storage 供应器和 GCP 属性构造 FileIO。
   *
   * <p>注意：调用 {@link #initialize(Map)} 会覆盖此构造器设置的信息。
   *
   * @param storageSupplier Storage 客户端的序列化供应器
   * @param gcpProperties GCP 配置属性
   */
  public GCSFileIO(SerializableSupplier<Storage> storageSupplier, GCPProperties gcpProperties) {
    this.storageSupplier = storageSupplier;
    this.gcpProperties = gcpProperties;
  }

  /**
   * 创建指定路径的输入文件（读取用）。
   *
   * @param path GCS 路径，形如 gs://bucket/object
   * @return {@link GCSInputFile} 实例
   */
  @Override
  public InputFile newInputFile(String path) {
    return GCSInputFile.fromLocation(path, client(), gcpProperties, metrics);
  }

  /**
   * 创建指定路径的输入文件（读取用），并附带已知文件长度。
   *
   * @param path GCS 路径
   * @param length 文件长度（字节），若 <= 0 则忽略
   * @return {@link GCSInputFile} 实例
   */
  @Override
  public InputFile newInputFile(String path, long length) {
    return GCSInputFile.fromLocation(path, length, client(), gcpProperties, metrics);
  }

  /**
   * 创建指定路径的输出文件（写入用）。
   *
   * @param path GCS 路径
   * @return {@link GCSOutputFile} 实例
   */
  @Override
  public OutputFile newOutputFile(String path) {
    return GCSOutputFile.fromLocation(path, client(), gcpProperties, metrics);
  }

  /**
   * 删除指定路径的 GCS 对象。
   *
   * <p>逻辑：调用 {@link Storage#delete(BlobId)} 删除对象；若删除失败（返回 false）仅记录 日志告警，不抛异常，与其他 FileIO
   * 实现保持一致——删除非 Iceberg 必须成功的操作。
   *
   * @param path GCS 路径
   */
  @Override
  public void deleteFile(String path) {
    // There is no specific contract about whether delete should fail
    // and other FileIO providers ignore failure.  Log the failure for
    // now as it is not a required operation for Iceberg.
    if (!client().delete(BlobId.fromGsUtilUri(path))) {
      LOG.warn("Failed to delete path: {}", path);
    }
  }

  /** 返回初始化时传入的不可变属性 Map。 */
  @Override
  public Map<String, String> properties() {
    return properties.immutableMap();
  }

  /**
   * 获取 GCS Storage 客户端（双重检查锁懒初始化）。
   *
   * <p>逻辑：若 {@code storage} 为 null，进入同步块再次检查后调用 {@code storageSupplier.get()} 创建客户端。使用 volatile +
   * synchronized 保证 多线程下只创建一次。
   *
   * @return GCS Storage 客户端实例
   */
  public Storage client() {
    if (storage == null) {
      synchronized (this) {
        if (storage == null) {
          storage = storageSupplier.get();
        }
      }
    }
    return storage;
  }

  /**
   * 初始化 FileIO：解析配置属性并构造 Storage 供应器。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>将 props 复制为 {@link SerializableMap} 并保存。
   *   <li>基于 props 构造 {@link GCPProperties}。
   *   <li>构造 {@link SerializableSupplier}：在其中根据 GCPProperties 配置 {@link
   *       StorageOptions.Builder}（projectId、clientLibToken、serviceHost、 OAuth2 凭据），最终 build 出
   *       Storage 服务实例。该供应器在 {@link #client()} 首次调用时才执行。
   *   <li>调用 {@link #initMetrics(Map)} 初始化指标上下文。
   * </ol>
   *
   * @param props 配置属性键值对
   */
  @Override
  public void initialize(Map<String, String> props) {
    this.properties = SerializableMap.copyOf(props);
    this.gcpProperties = new GCPProperties(properties);

    this.storageSupplier =
        () -> {
          StorageOptions.Builder builder = StorageOptions.newBuilder();

          gcpProperties.projectId().ifPresent(builder::setProjectId);
          gcpProperties.clientLibToken().ifPresent(builder::setClientLibToken);
          gcpProperties.serviceHost().ifPresent(builder::setHost);

          gcpProperties
              .oauth2Token()
              .ifPresent(
                  token -> {
                    AccessToken accessToken =
                        new AccessToken(token, gcpProperties.oauth2TokenExpiresAt().orElse(null));
                    builder.setCredentials(OAuth2Credentials.create(accessToken));
                  });

          return builder.build().getService();
        };

    initMetrics(properties);
  }

  /**
   * 初始化指标上下文：尝试加载 HadoopMetricsContext，失败则回退到 null metrics。
   *
   * <p>逻辑：通过 {@link DynConstructors} 反射加载 {@code
   * org.apache.iceberg.hadoop.HadoopMetricsContext}（Iceberg 常见部署环境含 Hadoop 依赖），构造实例并 initialize；若
   * Hadoop 不在 classpath（NoClassDefFoundError）或 反射查找失败，则记录告警并保持默认的 nullMetrics。
   *
   * <p>设计意图：iceberg-gcp 不强依赖 Hadoop，但若运行环境有 Hadoop 则利用其指标体系 （如 FS 名称、用户名等）收集更丰富的 IO 统计。
   *
   * @param props 配置属性
   */
  @SuppressWarnings("CatchBlockLogException")
  private void initMetrics(Map<String, String> props) {
    // Report Hadoop metrics if Hadoop is available
    try {
      DynConstructors.Ctor<MetricsContext> ctor =
          DynConstructors.builder(MetricsContext.class)
              .hiddenImpl(DEFAULT_METRICS_IMPL, String.class)
              .buildChecked();
      MetricsContext context = ctor.newInstance("gcs");
      context.initialize(props);
      this.metrics = context;
    } catch (NoClassDefFoundError | NoSuchMethodException | ClassCastException e) {
      LOG.warn(
          "Unable to load metrics class: '{}', falling back to null metrics", DEFAULT_METRICS_IMPL);
    }
  }

  /**
   * 释放资源：关闭/清理 Storage 客户端引用。
   *
   * <p>逻辑：使用 {@link AtomicBoolean#compareAndSet} 保证并发场景下只执行一次清理； GCS Storage 本身不可 close，因此仅置 null
   * 释放引用，让 GC 回收。
   */
  @Override
  public void close() {
    // handles concurrent calls to close()
    if (isResourceClosed.compareAndSet(false, true)) {
      if (storage != null) {
        // GCS Storage does not appear to be closable, so release the reference
        storage = null;
      }
    }
  }

  /**
   * 列举指定前缀下的所有 GCS 对象。
   *
   * <p>逻辑：将 prefix 解析为 {@link GCSLocation}，调用 {@link Storage#list(String,
   * Storage.BlobListOption...)} 按 bucket 和前缀列举； 将每个 {@link Blob} 转换为 {@link
   * FileInfo}（location、size、createTime）。 返回懒迭代器，支持流式遍历大量对象。
   *
   * @param prefix GCS 前缀路径，形如 gs://bucket/prefix/
   * @return FileInfo 可迭代对象
   */
  @Override
  public Iterable<FileInfo> listPrefix(String prefix) {
    GCSLocation location = new GCSLocation(prefix);
    return () ->
        client()
            .list(location.bucket(), Storage.BlobListOption.prefix(location.prefix()))
            .streamAll()
            .map(
                blob ->
                    new FileInfo(
                        String.format("gs://%s/%s", blob.getBucket(), blob.getName()),
                        blob.getSize(),
                        createTimeMillis(blob)))
            .iterator();
  }

  /**
   * 从 Blob 元数据中提取创建时间（毫秒时间戳）。
   *
   * @param blob GCS Blob 元数据
   * @return 创建时间的 epoch 毫秒值，若无创建时间则返回 0
   */
  private long createTimeMillis(Blob blob) {
    if (blob.getCreateTimeOffsetDateTime() == null) {
      return 0;
    }
    return blob.getCreateTimeOffsetDateTime().toInstant().toEpochMilli();
  }

  /**
   * 删除指定前缀下的所有 GCS 对象。
   *
   * <p>逻辑：先通过 {@link #listPrefix(String)} 列举前缀下所有对象，再将各 location 转为 {@link BlobId} 并委托 {@link
   * #internalDeleteFiles(Stream)} 分批删除。
   *
   * @param prefix GCS 前缀路径
   */
  @Override
  public void deletePrefix(String prefix) {
    internalDeleteFiles(
        Streams.stream(listPrefix(prefix))
            .map(fileInfo -> BlobId.fromGsUtilUri(fileInfo.location())));
  }

  /**
   * 批量删除多个文件。
   *
   * <p>逻辑：将各路径转为 {@link BlobId}，委托 {@link #internalDeleteFiles(Stream)} 按 {@link
   * GCPProperties#deleteBatchSize()} 分批调用 GCS 批量删除接口。
   *
   * @param pathsToDelete 待删除文件路径集合
   * @throws BulkDeletionFailureException 若批量删除失败
   */
  @Override
  public void deleteFiles(Iterable<String> pathsToDelete) throws BulkDeletionFailureException {
    internalDeleteFiles(Streams.stream(pathsToDelete).map(BlobId::fromGsUtilUri));
  }

  /**
   * 内部批量删除实现：按配置的批次大小分批删除。
   *
   * <p>逻辑：使用 {@link Iterators#partition} 将 BlobId 流按 {@link GCPProperties#deleteBatchSize()}
   * 分批，逐批调用 {@link Storage#delete(Iterable)} 执行批量删除，避免单次请求过大。
   *
   * @param blobIdsToDelete 待删除的 BlobId 流
   */
  private void internalDeleteFiles(Stream<BlobId> blobIdsToDelete) {
    Streams.stream(Iterators.partition(blobIdsToDelete.iterator(), gcpProperties.deleteBatchSize()))
        .forEach(batch -> client().delete(batch));
  }
}
