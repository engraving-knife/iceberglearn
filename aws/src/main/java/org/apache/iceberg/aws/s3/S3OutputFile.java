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

import java.io.IOException;
import java.io.UncheckedIOException;
import org.apache.iceberg.encryption.NativeFileCryptoParameters;
import org.apache.iceberg.encryption.NativelyEncryptedFile;
import org.apache.iceberg.exceptions.AlreadyExistsException;
import org.apache.iceberg.io.InputFile;
import org.apache.iceberg.io.OutputFile;
import org.apache.iceberg.io.PositionOutputStream;
import org.apache.iceberg.metrics.MetricsContext;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * 文件级说明：基于 S3 的只写输出文件实现。
 *
 * <p>所属模块：iceberg-aws（Iceberg 与 AWS 服务集成的入口模块，位于 api/core 之上）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>实现 Iceberg {@link OutputFile}，把 S3 对象包装为可写入的输出文件。
 *   <li>实现 {@link NativelyEncryptedFile}，承载原生文件级加密参数（如 SSE-C 客户密钥）。
 *   <li>支持 create（已存在则抛异常）与 createOrOverwrite（覆盖式）两种创建语义。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>S3 无原生“不存在则创建”语义，create 通过先 exists 探测再写入模拟该语义， 避免覆盖既有数据。
 *   <li>写入流委托给 {@link S3OutputStream}，本类管理元数据与生命周期。
 *   <li>工厂方法模式：通过静态 fromLocation 构造实例，使用 {@link S3URI} 解析路径。
 * </ul>
 *
 * <p>上下游关系：由 {@link org.apache.iceberg.aws.s3.S3FileIO} 工厂方法构造； 被 Iceberg 写入流程用于写出数据文件、元数据文件、清单文件等。
 */
public class S3OutputFile extends BaseS3File implements OutputFile, NativelyEncryptedFile {
  private NativeFileCryptoParameters nativeEncryptionParameters;

  /**
   * 通过 location 字符串构造 S3OutputFile。
   *
   * @param location S3 路径
   * @param client S3 客户端
   * @param s3FileIOProperties S3 FileIO 配置
   * @param metrics 度量上下文
   * @return S3OutputFile 实例
   */
  public static S3OutputFile fromLocation(
      String location,
      S3Client client,
      S3FileIOProperties s3FileIOProperties,
      MetricsContext metrics) {
    return new S3OutputFile(
        client,
        new S3URI(location, s3FileIOProperties.bucketToAccessPointMapping()),
        s3FileIOProperties,
        metrics);
  }

  /** 包级构造器，由 fromLocation 调用。 */
  S3OutputFile(
      S3Client client, S3URI uri, S3FileIOProperties s3FileIOProperties, MetricsContext metrics) {
    super(client, uri, s3FileIOProperties, metrics);
  }

  /**
   * 当目标对象不存在时创建输出流，否则抛 {@link AlreadyExistsException}。
   *
   * <p>逻辑：先调用 {@link #exists()} 探测，存在则抛异常，否则委托 {@link #createOrOverwrite}。
   *
   * @return output stream
   * @throws AlreadyExistsException 当对象已存在时
   */
  @Override
  public PositionOutputStream create() {
    if (!exists()) {
      return createOrOverwrite();
    } else {
      throw new AlreadyExistsException("Location already exists: %s", uri());
    }
  }

  /**
   * 创建可覆盖既有对象的输出流。
   *
   * <p>逻辑：构造 {@link S3OutputStream}，构造期 IOException 包装为 UncheckedIOException。
   *
   * @return 输出流
   */
  @Override
  public PositionOutputStream createOrOverwrite() {
    try {
      return new S3OutputStream(client(), uri(), s3FileIOProperties(), metrics());
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to create output stream for location: " + uri(), e);
    }
  }

  /** 把本输出文件转为对应输入文件，复用 client/uri/properties，length 留空待获取。 */
  @Override
  public InputFile toInputFile() {
    return new S3InputFile(client(), uri(), null, s3FileIOProperties(), metrics());
  }

  /** 返回原生文件加密参数（如 SSE-C 客户密钥）。 */
  @Override
  public NativeFileCryptoParameters nativeCryptoParameters() {
    return nativeEncryptionParameters;
  }

  /** 设置原生文件加密参数。 */
  @Override
  public void setNativeCryptoParameters(NativeFileCryptoParameters nativeCryptoParameters) {
    this.nativeEncryptionParameters = nativeCryptoParameters;
  }
}
