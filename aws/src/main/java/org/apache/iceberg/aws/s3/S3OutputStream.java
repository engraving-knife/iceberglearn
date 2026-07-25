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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.SequenceInputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;
import org.apache.iceberg.io.FileIOMetricsContext;
import org.apache.iceberg.io.PositionOutputStream;
import org.apache.iceberg.metrics.Counter;
import org.apache.iceberg.metrics.MetricsContext;
import org.apache.iceberg.metrics.MetricsContext.Unit;
import org.apache.iceberg.relocated.com.google.common.base.Joiner;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.base.Predicates;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.relocated.com.google.common.io.CountingOutputStream;
import org.apache.iceberg.relocated.com.google.common.util.concurrent.MoreExecutors;
import org.apache.iceberg.relocated.com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.apache.iceberg.util.Tasks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.internal.util.Mimetype;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.ContentStreamProvider;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.Tag;
import software.amazon.awssdk.services.s3.model.Tagging;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.model.UploadPartResponse;
import software.amazon.awssdk.utils.BinaryUtils;

/**
 * 基于 S3 的输出流：通过本地暂存文件 + 分片上传实现大文件写入。
 *
 * <p>所属模块：iceberg-aws（Iceberg 与 AWS 服务集成模块，处于引擎层之下）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>实现 {@link org.apache.iceberg.io.PositionOutputStream}，提供带位置追踪的写入能力。
 *   <li>数据先写入本地暂存文件，达到阈值后切换为 S3 分片上传（Multipart Upload）模式。
 *   <li>小文件直接通过 PutObject 上传，大文件通过分片上传并发上传各部分。
 *   <li>支持写入标签、存储类别、服务端加密、MD5 校验等配置。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>暂存文件策略：S3 不支持追加写入，因此数据先写入本地临时文件，按 multiPartSize 分片。 写满一个分片大小的暂存文件后，异步上传该分片并创建新的暂存文件。
 *   <li>延迟切换分片上传：写入数据量未超过 multiPartThresholdSize 时保持单文件 PutObject 模式， 超过后才初始化 Multipart
 *       Upload，避免小文件也走分片流程的开销。
 *   <li>分片上传并发执行：使用固定线程池异步上传各分片，通过 CompletableFuture 管理上传状态， 上传完成后立即删除暂存文件释放磁盘空间。
 *   <li>MD5 校验：开启后为每个分片和完整文件计算 MD5，随请求发送给 S3 做完整性校验。
 *   <li>通过 finalize 兜底检测未关闭的流，记录创建栈帮助排查资源泄漏。
 * </ul>
 *
 * <p>上下游关系：由 {@link S3OutputFile} 创建，被 Iceberg 的 Parquet/ORC 写入器使用。
 */
class S3OutputStream extends PositionOutputStream {
  private static final Logger LOG = LoggerFactory.getLogger(S3OutputStream.class);
  private static final String digestAlgorithm = "MD5";

  private static volatile ExecutorService executorService;

  private final StackTraceElement[] createStack;
  private final S3Client s3;
  private final S3URI location;
  private final S3FileIOProperties s3FileIOProperties;
  private final Set<Tag> writeTags;

  private CountingOutputStream stream;
  private final List<FileAndDigest> stagingFiles = Lists.newArrayList();
  private final File stagingDirectory;
  private File currentStagingFile;
  private String multipartUploadId;
  private final Map<File, CompletableFuture<CompletedPart>> multiPartMap = Maps.newHashMap();
  private final int multiPartSize;
  private final int multiPartThresholdSize;
  private final boolean isChecksumEnabled;
  private final MessageDigest completeMessageDigest;
  private MessageDigest currentPartMessageDigest;

  private final Counter writeBytes;
  private final Counter writeOperations;

  private long pos = 0;
  private boolean closed = false;

  /**
   * 构造 S3 输出流。
   *
   * <p>逻辑：初始化上传线程池（DCL），设置 S3 客户端、URI、属性、标签， 计算分片大小和阈值，创建 MD5 摘要器（若启用校验），调用 newStream 创建首个暂存文件。
   *
   * @param s3 S3 客户端
   * @param location S3 URI
   * @param s3FileIOProperties S3 FileIO 属性
   * @param metrics 指标上下文
   * @throws IOException 创建暂存文件或摘要器失败
   */
  @SuppressWarnings("StaticAssignmentInConstructor")
  S3OutputStream(
      S3Client s3, S3URI location, S3FileIOProperties s3FileIOProperties, MetricsContext metrics)
      throws IOException {
    if (executorService == null) {
      synchronized (S3OutputStream.class) {
        if (executorService == null) {
          executorService =
              MoreExecutors.getExitingExecutorService(
                  (ThreadPoolExecutor)
                      Executors.newFixedThreadPool(
                          s3FileIOProperties.multipartUploadThreads(),
                          new ThreadFactoryBuilder()
                              .setDaemon(true)
                              .setNameFormat("iceberg-s3fileio-upload-%d")
                              .build()));
        }
      }
    }

    this.s3 = s3;
    this.location = location;
    this.s3FileIOProperties = s3FileIOProperties;
    this.writeTags = s3FileIOProperties.writeTags();

    this.createStack = Thread.currentThread().getStackTrace();

    this.multiPartSize = s3FileIOProperties.multiPartSize();
    this.multiPartThresholdSize =
        (int) (multiPartSize * s3FileIOProperties.multipartThresholdFactor());
    this.stagingDirectory = new File(s3FileIOProperties.stagingDirectory());
    this.isChecksumEnabled = s3FileIOProperties.isChecksumEnabled();
    try {
      this.completeMessageDigest =
          isChecksumEnabled ? MessageDigest.getInstance(digestAlgorithm) : null;
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(
          "Failed to create message digest needed for s3 checksum checks", e);
    }

    this.writeBytes = metrics.counter(FileIOMetricsContext.WRITE_BYTES, Unit.BYTES);
    this.writeOperations = metrics.counter(FileIOMetricsContext.WRITE_OPERATIONS);

    newStream();
  }

  @Override
  public long getPos() {
    return pos;
  }

  @Override
  public void flush() throws IOException {
    stream.flush();
  }

  /**
   * 写入单个字节。
   *
   * <p>逻辑：若当前暂存文件已满（达到 multiPartSize），创建新暂存文件并触发分片上传； 写入字节后，若总写入量超过阈值且尚未进入分片模式，则初始化 Multipart
   * Upload。
   */
  @Override
  public void write(int b) throws IOException {
    if (stream.getCount() >= multiPartSize) {
      newStream();
      uploadParts();
    }

    stream.write(b);
    pos += 1;
    writeBytes.increment();
    writeOperations.increment();

    // switch to multipart upload
    if (multipartUploadId == null && pos >= multiPartThresholdSize) {
      initializeMultiPartUpload();
      uploadParts();
    }
  }

  /**
   * 写入字节数组。
   *
   * <p>逻辑：循环将数据按 multiPartSize 分片写入暂存文件，每写满一个分片创建新暂存文件 并触发上传；写入完成后，若总写入量超过阈值且尚未进入分片模式，则初始化
   * Multipart Upload。
   */
  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    int remaining = len;
    int relativeOffset = off;

    // Write the remainder of the part size to the staging file
    // and continue to write new staging files if the write is
    // larger than the part size.
    while (stream.getCount() + remaining > multiPartSize) {
      int writeSize = multiPartSize - (int) stream.getCount();

      stream.write(b, relativeOffset, writeSize);
      remaining -= writeSize;
      relativeOffset += writeSize;

      newStream();
      uploadParts();
    }

    stream.write(b, relativeOffset, remaining);
    pos += len;
    writeBytes.increment(len);
    writeOperations.increment();

    // switch to multipart upload
    if (multipartUploadId == null && pos >= multiPartThresholdSize) {
      initializeMultiPartUpload();
      uploadParts();
    }
  }

  /**
   * 创建新的暂存文件和对应的输出流。
   *
   * <p>逻辑：关闭旧流，在暂存目录创建临时文件，根据是否启用校验包装 DigestOutputStream（同时计算分片和完整文件 MD5），最终包装为
   * CountingOutputStream。
   */
  private void newStream() throws IOException {
    if (stream != null) {
      stream.close();
    }

    createStagingDirectoryIfNotExists();
    currentStagingFile = File.createTempFile("s3fileio-", ".tmp", stagingDirectory);
    currentStagingFile.deleteOnExit();
    try {
      currentPartMessageDigest =
          isChecksumEnabled ? MessageDigest.getInstance(digestAlgorithm) : null;
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(
          "Failed to create message digest needed for s3 checksum checks.", e);
    }

    stagingFiles.add(new FileAndDigest(currentStagingFile, currentPartMessageDigest));
    OutputStream outputStream = Files.newOutputStream(currentStagingFile.toPath());

    if (isChecksumEnabled) {
      DigestOutputStream digestOutputStream;

      // if switched over to multipart threshold already, no need to update complete message digest
      if (multipartUploadId != null) {
        digestOutputStream =
            new DigestOutputStream(
                new BufferedOutputStream(outputStream), currentPartMessageDigest);
      } else {
        digestOutputStream =
            new DigestOutputStream(
                new DigestOutputStream(
                    new BufferedOutputStream(outputStream), currentPartMessageDigest),
                completeMessageDigest);
      }

      stream = new CountingOutputStream(digestOutputStream);
    } else {
      stream = new CountingOutputStream(new BufferedOutputStream(outputStream));
    }
  }

  /**
   * 关闭输出流并完成上传。
   *
   * <p>逻辑：关闭底层流，调用 completeUploads 完成上传（PutObject 或 CompleteMultipartUpload）， finally 块清理暂存文件。
   *
   * @throws IOException 上传或清理失败
   */
  @Override
  public void close() throws IOException {
    if (closed) {
      return;
    }

    super.close();
    closed = true;

    try {
      stream.close();
      completeUploads();
    } finally {
      cleanUpStagingFiles();
    }
  }

  /**
   * 初始化 S3 Multipart Upload，获取 uploadId。
   *
   * <p>逻辑：构建 CreateMultipartUploadRequest，设置标签、存储类别、加密、权限， 调用 S3 createMultipartUpload 获取 uploadId。
   */
  private void initializeMultiPartUpload() {
    CreateMultipartUploadRequest.Builder requestBuilder =
        CreateMultipartUploadRequest.builder().bucket(location.bucket()).key(location.key());
    if (writeTags != null && !writeTags.isEmpty()) {
      requestBuilder.tagging(Tagging.builder().tagSet(writeTags).build());
    }
    if (s3FileIOProperties.writeStorageClass() != null) {
      requestBuilder.storageClass(s3FileIOProperties.writeStorageClass());
    }

    S3RequestUtil.configureEncryption(s3FileIOProperties, requestBuilder);
    S3RequestUtil.configurePermission(s3FileIOProperties, requestBuilder);

    multipartUploadId = s3.createMultipartUpload(requestBuilder.build()).uploadId();
  }

  /**
   * 异步上传已完成的暂存文件分片。
   *
   * <p>逻辑：
   *
   * <ul>
   *   <li>过滤出尚未上传且非当前正在写入的暂存文件。
   *   <li>为每个文件构建 UploadPartRequest（partNumber 从 1 开始），配置 MD5 和加密。
   *   <li>通过 CompletableFuture.supplyAsync 在线程池中并发上传，完成后删除暂存文件。
   *   <li>将 Future 存入 multiPartMap 供后续 join。
   * </ul>
   */
  @SuppressWarnings("checkstyle:LocalVariableName")
  private void uploadParts() {
    // exit if multipart has not been initiated
    if (multipartUploadId == null) {
      return;
    }

    stagingFiles.stream()
        // do not upload the file currently being written
        .filter(f -> closed || !f.file().equals(currentStagingFile))
        // do not upload any files that have already been processed
        .filter(Predicates.not(f -> multiPartMap.containsKey(f.file())))
        .forEach(
            fileAndDigest -> {
              File f = fileAndDigest.file();
              UploadPartRequest.Builder requestBuilder =
                  UploadPartRequest.builder()
                      .bucket(location.bucket())
                      .key(location.key())
                      .uploadId(multipartUploadId)
                      .partNumber(stagingFiles.indexOf(fileAndDigest) + 1)
                      .contentLength(f.length());

              if (fileAndDigest.hasDigest()) {
                requestBuilder.contentMD5(BinaryUtils.toBase64(fileAndDigest.digest()));
              }

              S3RequestUtil.configureEncryption(s3FileIOProperties, requestBuilder);

              UploadPartRequest uploadRequest = requestBuilder.build();

              CompletableFuture<CompletedPart> future =
                  CompletableFuture.supplyAsync(
                          () -> {
                            UploadPartResponse response =
                                s3.uploadPart(uploadRequest, RequestBody.fromFile(f));
                            return CompletedPart.builder()
                                .eTag(response.eTag())
                                .partNumber(uploadRequest.partNumber())
                                .build();
                          },
                          executorService)
                      .whenComplete(
                          (result, thrown) -> {
                            try {
                              Files.deleteIfExists(f.toPath());
                            } catch (IOException e) {
                              LOG.warn("Failed to delete staging file: {}", f, e);
                            }

                            if (thrown != null) {
                              // Exception observed here will be thrown as part of
                              // CompletionException
                              // when we will join completable futures.
                              LOG.error("Failed to upload part: {}", uploadRequest, thrown);
                            }
                          });

              multiPartMap.put(f, future);
            });
  }

  /**
   * 完成分片上传：等待所有分片上传完成，按 partNumber 排序后调用 CompleteMultipartUpload。
   *
   * <p>逻辑：join 所有 Future，任一失败则取消其余并 abortUpload； 成功则构建 CompleteMultipartUploadRequest
   * 并提交。completeMultipartUpload 失败时也会 abort。
   *
   * @throws CompletionException 分片上传失败
   */
  private void completeMultiPartUpload() {
    Preconditions.checkState(closed, "Complete upload called on open stream: " + location);

    List<CompletedPart> completedParts;
    try {
      completedParts =
          multiPartMap.values().stream()
              .map(CompletableFuture::join)
              .sorted(Comparator.comparing(CompletedPart::partNumber))
              .collect(Collectors.toList());
    } catch (CompletionException ce) {
      // cancel the remaining futures.
      multiPartMap.values().forEach(c -> c.cancel(true));
      abortUpload();
      throw ce;
    }

    CompleteMultipartUploadRequest request =
        CompleteMultipartUploadRequest.builder()
            .bucket(location.bucket())
            .key(location.key())
            .uploadId(multipartUploadId)
            .multipartUpload(CompletedMultipartUpload.builder().parts(completedParts).build())
            .build();

    Tasks.foreach(request)
        .noRetry()
        .onFailure(
            (r, thrown) -> {
              LOG.error("Failed to complete multipart upload request: {}", r, thrown);
              abortUpload();
            })
        .throwFailureWhenFinished()
        .run(s3::completeMultipartUpload);
  }

  /**
   * 中止分片上传并清理暂存文件。
   *
   * <p>逻辑：若存在 multipartUploadId，调用 S3 abortMultipartUpload 中止上传， 然后清理所有暂存文件。
   */
  private void abortUpload() {
    if (multipartUploadId != null) {
      try {
        s3.abortMultipartUpload(
            AbortMultipartUploadRequest.builder()
                .bucket(location.bucket())
                .key(location.key())
                .uploadId(multipartUploadId)
                .build());
      } finally {
        cleanUpStagingFiles();
      }
    }
  }

  /** 删除所有暂存文件，失败仅告警不抛异常。 */
  private void cleanUpStagingFiles() {
    Tasks.foreach(stagingFiles.stream().map(FileAndDigest::file))
        .suppressFailureWhenFinished()
        .onFailure((file, thrown) -> LOG.warn("Failed to delete staging file: {}", file, thrown))
        .run(File::delete);
  }

  /**
   * 根据上传模式完成上传。
   *
   * <p>逻辑：
   *
   * <ul>
   *   <li>未进入分片模式（multipartUploadId == null）：将所有暂存文件串联为 SequenceInputStream， 通过 PutObject
   *       单次上传，设置标签、存储类别、MD5 校验、加密、权限。
   *   <li>已进入分片模式：先 uploadParts 上传剩余分片，再 completeMultiPartUpload 完成。
   * </ul>
   */
  private void completeUploads() {
    if (multipartUploadId == null) {
      long contentLength =
          stagingFiles.stream().map(FileAndDigest::file).mapToLong(File::length).sum();
      ContentStreamProvider contentProvider =
          () ->
              new BufferedInputStream(
                  stagingFiles.stream()
                      .map(FileAndDigest::file)
                      .map(S3OutputStream::uncheckedInputStream)
                      .reduce(SequenceInputStream::new)
                      .orElseGet(() -> new ByteArrayInputStream(new byte[0])));

      PutObjectRequest.Builder requestBuilder =
          PutObjectRequest.builder().bucket(location.bucket()).key(location.key());

      if (writeTags != null && !writeTags.isEmpty()) {
        requestBuilder.tagging(Tagging.builder().tagSet(writeTags).build());
      }

      if (s3FileIOProperties.writeStorageClass() != null) {
        requestBuilder.storageClass(s3FileIOProperties.writeStorageClass());
      }

      if (isChecksumEnabled) {
        requestBuilder.contentMD5(BinaryUtils.toBase64(completeMessageDigest.digest()));
      }

      S3RequestUtil.configureEncryption(s3FileIOProperties, requestBuilder);
      S3RequestUtil.configurePermission(s3FileIOProperties, requestBuilder);

      s3.putObject(
          requestBuilder.build(),
          RequestBody.fromContentProvider(
              contentProvider, contentLength, Mimetype.MIMETYPE_OCTET_STREAM));
    } else {
      uploadParts();
      completeMultiPartUpload();
    }
  }

  private static InputStream uncheckedInputStream(File file) {
    try {
      return Files.newInputStream(file.toPath());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private void createStagingDirectoryIfNotExists() throws IOException, SecurityException {
    if (!stagingDirectory.exists()) {
      LOG.info(
          "Staging directory does not exist, trying to create one: {}",
          stagingDirectory.getAbsolutePath());
      boolean createdStagingDirectory = stagingDirectory.mkdirs();
      if (createdStagingDirectory) {
        LOG.info("Successfully created staging directory: {}", stagingDirectory.getAbsolutePath());
      } else {
        if (stagingDirectory.exists()) {
          LOG.info(
              "Successfully created staging directory by another process: {}",
              stagingDirectory.getAbsolutePath());
        } else {
          throw new IOException(
              "Failed to create staging directory due to some unknown reason: "
                  + stagingDirectory.getAbsolutePath());
        }
      }
    }
  }

  @SuppressWarnings("checkstyle:NoFinalizer")
  @Override
  protected void finalize() throws Throwable {
    super.finalize();
    if (!closed) {
      close(); // releasing resources is more important than printing the warning
      String trace = Joiner.on("\n\t").join(Arrays.copyOfRange(createStack, 1, createStack.length));
      LOG.warn("Unclosed output stream created by:\n\t{}", trace);
    }
  }

  /** 暂存文件及其 MD5 摘要的持有对，用于分片上传时传递文件和校验值。 */
  private static class FileAndDigest {
    private final File file;
    private final MessageDigest digest;

    FileAndDigest(File file, MessageDigest digest) {
      this.file = file;
      this.digest = digest;
    }

    File file() {
      return file;
    }

    byte[] digest() {
      return digest.digest();
    }

    public boolean hasDigest() {
      return digest != null;
    }
  }
}
