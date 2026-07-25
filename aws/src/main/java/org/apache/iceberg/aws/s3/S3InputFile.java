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

import org.apache.iceberg.encryption.NativeFileCryptoParameters;
import org.apache.iceberg.encryption.NativelyEncryptedFile;
import org.apache.iceberg.io.InputFile;
import org.apache.iceberg.io.SeekableInputStream;
import org.apache.iceberg.metrics.MetricsContext;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * 文件级说明：基于 S3 的只读输入文件实现。
 *
 * <p>所属模块：iceberg-aws（Iceberg 与 AWS 服务集成的入口模块，位于 api/core 之上）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>实现 Iceberg {@link InputFile}，把 S3 对象包装为可随机读的输入文件。
 *   <li>实现 {@link NativelyEncryptedFile}，承载原生文件级解密参数（如 SSE-C 客户密钥）。
 *   <li>缓存对象长度，避免重复 HeadObject 调用。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>延迟取长度：长度仅在首次 getLength 时通过 HeadObject 获取并缓存，减少元数据请求。
 *   <li>工厂方法模式：通过静态 fromLocation 构造实例，内部使用 {@link S3URI} 解析路径并应用 Access Point 映射，屏蔽 S3 路径多样性。
 *   <li>读流委托给 {@link S3InputStream}，本类仅管理元数据与生命周期。
 * </ul>
 *
 * <p>上下游关系：由 {@link org.apache.iceberg.aws.s3.S3FileIO} 工厂方法构造； 被 Iceberg 读写流程用于读取数据文件、元数据文件、清单文件等。
 */
public class S3InputFile extends BaseS3File implements InputFile, NativelyEncryptedFile {
  private NativeFileCryptoParameters nativeDecryptionParameters;
  private Long length;

  /**
   * 通过 location 字符串构造 S3InputFile，长度未知（首次访问时获取）。
   *
   * @param location S3 路径
   * @param client S3 客户端
   * @param s3FileIOProperties S3 FileIO 配置
   * @param metrics 度量上下文
   * @return S3InputFile 实例
   */
  public static S3InputFile fromLocation(
      String location,
      S3Client client,
      S3FileIOProperties s3FileIOProperties,
      MetricsContext metrics) {
    return new S3InputFile(
        client,
        new S3URI(location, s3FileIOProperties.bucketToAccessPointMapping()),
        null,
        s3FileIOProperties,
        metrics);
  }

  /**
   * 通过 location 字符串与已知长度构造 S3InputFile，避免后续 HeadObject 调用。
   *
   * @param location S3 路径
   * @param length 已知对象长度，&lt;=0 视为未知
   * @param client S3 客户端
   * @param s3FileIOProperties S3 FileIO 配置
   * @param metrics 度量上下文
   * @return S3InputFile 实例
   */
  public static S3InputFile fromLocation(
      String location,
      long length,
      S3Client client,
      S3FileIOProperties s3FileIOProperties,
      MetricsContext metrics) {
    return new S3InputFile(
        client,
        new S3URI(location, s3FileIOProperties.bucketToAccessPointMapping()),
        length > 0 ? length : null,
        s3FileIOProperties,
        metrics);
  }

  /** 包级构造器，由 fromLocation 调用，length 可为 null 表示未知。 */
  S3InputFile(
      S3Client client,
      S3URI uri,
      Long length,
      S3FileIOProperties s3FileIOProperties,
      MetricsContext metrics) {
    super(client, uri, s3FileIOProperties, metrics);
    this.length = length;
  }

  /**
   * 返回对象内容长度。首次调用时通过 HeadObject 获取并缓存。
   *
   * <p>注意：若文件在缓存后被删除，本方法返回的长度可能已过期。
   *
   * @return content length
   */
  @Override
  public long getLength() {
    if (length == null) {
      this.length = getObjectMetadata().contentLength();
    }

    return length;
  }

  /** 创建并返回一个支持随机定位读的 {@link SeekableInputStream}（基于 {@link S3InputStream}）。 */
  @Override
  public SeekableInputStream newStream() {
    return new S3InputStream(client(), uri(), s3FileIOProperties(), metrics());
  }

  /** 返回原生文件解密参数（如 SSE-C 客户密钥）。 */
  @Override
  public NativeFileCryptoParameters nativeCryptoParameters() {
    return nativeDecryptionParameters;
  }

  /** 设置原生文件解密参数。 */
  @Override
  public void setNativeCryptoParameters(NativeFileCryptoParameters nativeCryptoParameters) {
    this.nativeDecryptionParameters = nativeCryptoParameters;
  }
}
