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
package org.apache.iceberg.aws.s3;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.apache.iceberg.aws.AwsClientFactory;
import org.apache.iceberg.aws.S3FileIOAwsClientFactories;
import org.apache.iceberg.common.DynConstructors;
import org.apache.iceberg.io.BulkDeletionFailureException;
import org.apache.iceberg.io.CredentialSupplier;
import org.apache.iceberg.io.DelegateFileIO;
import org.apache.iceberg.io.FileInfo;
import org.apache.iceberg.io.InputFile;
import org.apache.iceberg.io.OutputFile;
import org.apache.iceberg.metrics.MetricsContext;
import org.apache.iceberg.relocated.com.google.common.base.Joiner;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.relocated.com.google.common.collect.Multimaps;
import org.apache.iceberg.relocated.com.google.common.collect.SetMultimap;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.apache.iceberg.relocated.com.google.common.collect.Streams;
import org.apache.iceberg.util.PropertyUtil;
import org.apache.iceberg.util.SerializableMap;
import org.apache.iceberg.util.SerializableSupplier;
import org.apache.iceberg.util.Tasks;
import org.apache.iceberg.util.ThreadPools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.GetObjectTaggingRequest;
import software.amazon.awssdk.services.s3.model.GetObjectTaggingResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectTaggingRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.Tag;
import software.amazon.awssdk.services.s3.model.Tagging;

/**
 * 基于 AWS S3 的 {@link DelegateFileIO} 实现。
 *
 * <p>所属模块：iceberg-aws。职责：为 Iceberg 提供面向 S3 对象存储的文件读写能力，实现 {@link InputFile}/{@link OutputFile}
 * 的输入输出流、批量删除、目录列举与对象标签管理； 同时实现 {@link CredentialSupplier} 暴露 AWS 凭证供下游签名使用。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>位置须遵循 S3 URI 约定（如 {@code s3://bucket/path...}）；s3a、s3n、https scheme 同样视为 S3 路径，其他 scheme 将抛
 *       {@link org.apache.iceberg.exceptions.ValidationException}。
 *   <li>采用 {@code DelegateFileIO} 而非旧版 {@code FileIO}，支持 {@code delete(List)} 批量删除、 {@code
 *       listDirectories}/{@code getFileInfo} 等丰富操作，适配现代对象存储 API。
 *   <li>客户端（{@link S3Client}）通过 {@link AwsClientFactory} 反射加载，支持跨区、access point、 路径风格等多种 S3
 *       兼容部署；删除操作走线程池并发以提升大表过期清理性能。
 * </ul>
 *
 * <p>上下游：向上被 {@code core} 的 {@code TableOperations}/{@code Catalog} 通过 {@code FileIO}
 * 接口调用读写元数据与数据文件；向下依赖 AWS SDK v2 的 S3Client。
 */
public class S3FileIO implements CredentialSupplier, DelegateFileIO {
  private static final Logger LOG = LoggerFactory.getLogger(S3FileIO.class);
  private static final String DEFAULT_METRICS_IMPL =
      "org.apache.iceberg.hadoop.HadoopMetricsContext";
  private static volatile ExecutorService executorService;

  private String credential = null;
  private SerializableSupplier<S3Client> s3;
  private S3FileIOProperties s3FileIOProperties;
  private SerializableMap<String, String> properties = null;
  private transient volatile S3Client client;
  private MetricsContext metrics = MetricsContext.nullMetrics();
  private final AtomicBoolean isResourceClosed = new AtomicBoolean(false);
  private transient StackTraceElement[] createStack;

  /**
   * 无参构造，用于动态加载 FileIO。
   *
   * <p>所有字段在后续调用 {@link #initialize(Map)} 时初始化。
   */
  public S3FileIO() {}

  /**
   * 使用自定义 S3 客户端供应者构造 FileIO。
   *
   * <p>调用 {@link #initialize(Map)} 会覆盖本构造方法设置的 s3 供应者。
   *
   * @param s3 S3 客户端供应者
   */
  public S3FileIO(SerializableSupplier<S3Client> s3) {
    this(s3, new S3FileIOProperties());
  }

  /**
   * 使用自定义 S3 客户端供应者与属性构造 FileIO，并记录创建栈用于资源泄漏排查。
   *
   * <p>调用 {@link #initialize(Map)} 会覆盖本构造方法设置的信息。
   *
   * @param s3 S3 客户端供应者
   * @param s3FileIOProperties S3 FileIO 属性
   */
  public S3FileIO(SerializableSupplier<S3Client> s3, S3FileIOProperties s3FileIOProperties) {
    this.s3 = s3;
    this.s3FileIOProperties = s3FileIOProperties;
    this.createStack = Thread.currentThread().getStackTrace();
  }

  /** 创建指定路径的 S3 输入文件（长度未知，读取时探测）。 */
  @Override
  public InputFile newInputFile(String path) {
    return S3InputFile.fromLocation(path, client(), s3FileIOProperties, metrics);
  }

  /** 创建指定路径与已知长度的 S3 输入文件。 */
  @Override
  public InputFile newInputFile(String path, long length) {
    return S3InputFile.fromLocation(path, length, client(), s3FileIOProperties, metrics);
  }

  /** 创建指定路径的 S3 输出文件。 */
  @Override
  public OutputFile newOutputFile(String path) {
    return S3OutputFile.fromLocation(path, client(), s3FileIOProperties, metrics);
  }

  /**
   * 删除单个文件：若配置了删除标签则先打标签，再根据是否启用删除执行实际删除。
   *
   * <p>逻辑：先按 deleteTags 给对象打删除标签（失败仅告警）；若未启用删除则直接返回； 否则构造 DeleteObjectRequest 调用 S3 删除对象。
   */
  @Override
  public void deleteFile(String path) {
    if (s3FileIOProperties.deleteTags() != null && !s3FileIOProperties.deleteTags().isEmpty()) {
      try {
        tagFileToDelete(path, s3FileIOProperties.deleteTags());
      } catch (S3Exception e) {
        LOG.warn("Failed to add delete tags: {} to {}", s3FileIOProperties.deleteTags(), path, e);
      }
    }

    if (!s3FileIOProperties.isDeleteEnabled()) {
      return;
    }

    S3URI location = new S3URI(path, s3FileIOProperties.bucketToAccessPointMapping());
    DeleteObjectRequest deleteRequest =
        DeleteObjectRequest.builder().bucket(location.bucket()).key(location.key()).build();

    client().deleteObject(deleteRequest);
  }

  @Override
  public Map<String, String> properties() {
    return properties.immutableMap();
  }

  /**
   * 批量删除多个路径。
   *
   * <p>逻辑：先按 deleteTags 给所有对象打删除标签（并发，失败仅告警）；若启用删除，则按 bucket 分组，达到 deleteBatchSize
   * 即提交线程池并发删除，剩余批次最后删除；收集失败项， 若有失败则抛出 {@link BulkDeletionFailureException}。
   *
   * @param paths 待删除路径集合
   */
  @Override
  public void deleteFiles(Iterable<String> paths) throws BulkDeletionFailureException {
    if (s3FileIOProperties.deleteTags() != null && !s3FileIOProperties.deleteTags().isEmpty()) {
      Tasks.foreach(paths)
          .noRetry()
          .executeWith(executorService())
          .suppressFailureWhenFinished()
          .onFailure(
              (path, exc) ->
                  LOG.warn(
                      "Failed to add delete tags: {} to {}",
                      s3FileIOProperties.deleteTags(),
                      path,
                      exc))
          .run(path -> tagFileToDelete(path, s3FileIOProperties.deleteTags()));
    }

    if (s3FileIOProperties.isDeleteEnabled()) {
      SetMultimap<String, String> bucketToObjects =
          Multimaps.newSetMultimap(Maps.newHashMap(), Sets::newHashSet);
      List<Future<List<String>>> deletionTasks = Lists.newArrayList();
      for (String path : paths) {
        S3URI location = new S3URI(path, s3FileIOProperties.bucketToAccessPointMapping());
        String bucket = location.bucket();
        String objectKey = location.key();
        bucketToObjects.get(bucket).add(objectKey);
        if (bucketToObjects.get(bucket).size() == s3FileIOProperties.deleteBatchSize()) {
          Set<String> keys = Sets.newHashSet(bucketToObjects.get(bucket));
          Future<List<String>> deletionTask =
              executorService().submit(() -> deleteBatch(bucket, keys));
          deletionTasks.add(deletionTask);
          bucketToObjects.removeAll(bucket);
        }
      }

      // Delete the remainder
      for (Map.Entry<String, Collection<String>> bucketToObjectsEntry :
          bucketToObjects.asMap().entrySet()) {
        String bucket = bucketToObjectsEntry.getKey();
        Collection<String> keys = bucketToObjectsEntry.getValue();
        Future<List<String>> deletionTask =
            executorService().submit(() -> deleteBatch(bucket, keys));
        deletionTasks.add(deletionTask);
      }

      int totalFailedDeletions = 0;

      for (Future<List<String>> deletionTask : deletionTasks) {
        try {
          List<String> failedDeletions = deletionTask.get();
          failedDeletions.forEach(path -> LOG.warn("Failed to delete object at path {}", path));
          totalFailedDeletions += failedDeletions.size();
        } catch (ExecutionException e) {
          LOG.warn("Caught unexpected exception during batch deletion: ", e.getCause());
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          deletionTasks.stream().filter(task -> !task.isDone()).forEach(task -> task.cancel(true));
          throw new RuntimeException("Interrupted when waiting for deletions to complete", e);
        }
      }

      if (totalFailedDeletions > 0) {
        throw new BulkDeletionFailureException(totalFailedDeletions);
      }
    }
  }

  /** 给指定对象追加删除标签：先读取已有标签，合并 deleteTags 后写回。 */
  private void tagFileToDelete(String path, Set<Tag> deleteTags) throws S3Exception {
    S3URI location = new S3URI(path, s3FileIOProperties.bucketToAccessPointMapping());
    String bucket = location.bucket();
    String objectKey = location.key();
    GetObjectTaggingRequest getObjectTaggingRequest =
        GetObjectTaggingRequest.builder().bucket(bucket).key(objectKey).build();
    GetObjectTaggingResponse getObjectTaggingResponse =
        client().getObjectTagging(getObjectTaggingRequest);
    // Get existing tags, if any and then add the delete tags
    Set<Tag> tags = Sets.newHashSet();
    if (getObjectTaggingResponse.hasTagSet()) {
      tags.addAll(getObjectTaggingResponse.tagSet());
    }

    tags.addAll(deleteTags);
    PutObjectTaggingRequest putObjectTaggingRequest =
        PutObjectTaggingRequest.builder()
            .bucket(bucket)
            .key(objectKey)
            .tagging(Tagging.builder().tagSet(tags).build())
            .build();
    client().putObjectTagging(putObjectTaggingRequest);
  }

  /** 删除单个 bucket 内的一批对象，返回删除失败的 key 路径列表。 */
  private List<String> deleteBatch(String bucket, Collection<String> keysToDelete) {
    List<ObjectIdentifier> objectIds =
        keysToDelete.stream()
            .map(key -> ObjectIdentifier.builder().key(key).build())
            .collect(Collectors.toList());
    DeleteObjectsRequest request =
        DeleteObjectsRequest.builder()
            .bucket(bucket)
            .delete(Delete.builder().objects(objectIds).build())
            .build();
    List<String> failures = Lists.newArrayList();
    try {
      DeleteObjectsResponse response = client().deleteObjects(request);
      if (response.hasErrors()) {
        failures.addAll(
            response.errors().stream()
                .map(error -> String.format("s3://%s/%s", request.bucket(), error.key()))
                .collect(Collectors.toList()));
      }
    } catch (Exception e) {
      LOG.warn("Encountered failure when deleting batch", e);
      failures.addAll(
          request.delete().objects().stream()
              .map(obj -> String.format("s3://%s/%s", request.bucket(), obj.key()))
              .collect(Collectors.toList()));
    }
    return failures;
  }

  /** 列举指定前缀下的所有对象，返回其 location、大小与最后修改时间的惰性迭代器。 */
  @Override
  public Iterable<FileInfo> listPrefix(String prefix) {
    S3URI s3uri = new S3URI(prefix, s3FileIOProperties.bucketToAccessPointMapping());
    ListObjectsV2Request request =
        ListObjectsV2Request.builder().bucket(s3uri.bucket()).prefix(s3uri.key()).build();

    return () ->
        client().listObjectsV2Paginator(request).stream()
            .flatMap(r -> r.contents().stream())
            .map(
                o ->
                    new FileInfo(
                        String.format("%s://%s/%s", s3uri.scheme(), s3uri.bucket(), o.key()),
                        o.size(),
                        o.lastModified().toEpochMilli()))
            .iterator();
  }

  /**
   * 尽力删除指定前缀下所有对象。
   *
   * <p>采用批量删除，失败不重试，但会记录未删除的对象。先列举前缀下所有对象再批量删除。
   *
   * @param prefix 待删除前缀
   */
  @Override
  public void deletePrefix(String prefix) {
    deleteFiles(() -> Streams.stream(listPrefix(prefix)).map(FileInfo::location).iterator());
  }

  /** 返回 S3 客户端，采用双重检查锁懒加载。 */
  public S3Client client() {
    if (client == null) {
      synchronized (this) {
        if (client == null) {
          client = s3.get();
        }
      }
    }
    return client;
  }

  /** 返回删除用的线程池，采用双重检查锁懒加载，全类共享。 */
  private ExecutorService executorService() {
    if (executorService == null) {
      synchronized (S3FileIO.class) {
        if (executorService == null) {
          executorService =
              ThreadPools.newWorkerPool(
                  "iceberg-s3fileio-delete", s3FileIOProperties.deleteThreads());
        }
      }
    }

    return executorService;
  }

  /** 返回 AWS 凭证字符串（可能为 null）。 */
  @Override
  public String getCredential() {
    return credential;
  }

  /**
   * 初始化 FileIO：保存属性，构造 S3FileIOProperties，记录创建栈； 若未外部提供 s3 供应者则通过 {@link
   * S3FileIOAwsClientFactories} 反射加载客户端工厂， 并按需预加载客户端；最后初始化指标。
   */
  @Override
  public void initialize(Map<String, String> props) {
    this.properties = SerializableMap.copyOf(props);
    this.s3FileIOProperties = new S3FileIOProperties(properties);
    this.createStack =
        PropertyUtil.propertyAsBoolean(props, "init-creation-stacktrace", true)
            ? Thread.currentThread().getStackTrace()
            : null;

    // Do not override s3 client if it was provided
    if (s3 == null) {
      Object clientFactory = S3FileIOAwsClientFactories.initialize(props);
      if (clientFactory instanceof S3FileIOAwsClientFactory) {
        this.s3 = ((S3FileIOAwsClientFactory) clientFactory)::s3;
      }
      if (clientFactory instanceof AwsClientFactory) {
        this.s3 = ((AwsClientFactory) clientFactory)::s3;
      }
      if (clientFactory instanceof CredentialSupplier) {
        this.credential = ((CredentialSupplier) clientFactory).getCredential();
      }
      if (s3FileIOProperties.isPreloadClientEnabled()) {
        client();
      }
    }

    initMetrics(properties);
  }

  /** 初始化指标上下文：若 Hadoop 可用则加载 HadoopMetricsContext，否则回退到 null 指标。 */
  @SuppressWarnings("CatchBlockLogException")
  private void initMetrics(Map<String, String> props) {
    // Report Hadoop metrics if Hadoop is available
    try {
      DynConstructors.Ctor<MetricsContext> ctor =
          DynConstructors.builder(MetricsContext.class)
              .hiddenImpl(DEFAULT_METRICS_IMPL, String.class)
              .buildChecked();
      MetricsContext context = ctor.newInstance("s3");
      context.initialize(props);
      this.metrics = context;
    } catch (NoClassDefFoundError | NoSuchMethodException | ClassCastException e) {
      LOG.warn(
          "Unable to load metrics class: '{}', falling back to null metrics", DEFAULT_METRICS_IMPL);
    }
  }

  /** 关闭资源：通过 CAS 保证并发只关闭一次，关闭 S3 客户端。 */
  @Override
  public void close() {
    // handles concurrent calls to close()
    if (isResourceClosed.compareAndSet(false, true)) {
      if (client != null) {
        client.close();
      }
    }
  }

  /** 终结器兜底：若未被显式关闭则在此关闭资源，并告警提示可能存在资源泄漏。 */
  @SuppressWarnings("checkstyle:NoFinalizer")
  @Override
  protected void finalize() throws Throwable {
    super.finalize();
    if (!isResourceClosed.get()) {
      close();

      if (null != createStack) {
        String trace =
            Joiner.on("\n\t").join(Arrays.copyOfRange(createStack, 1, createStack.length));
        LOG.warn("Unclosed S3FileIO instance created by:\n\t{}", trace);
      }
    }
  }
}
