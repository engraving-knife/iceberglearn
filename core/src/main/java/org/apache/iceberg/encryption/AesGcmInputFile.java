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
package org.apache.iceberg.encryption;

import org.apache.iceberg.io.InputFile;
import org.apache.iceberg.io.SeekableInputStream;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;

/**
 * 文件级说明：基于 AES-GCM 的解密输入文件包装器。
 *
 * <p>所属模块：iceberg-core（加密包），实现 iceberg-api 的 {@link InputFile} 契约， 在读取底层已加密字节流时透明地解密为明文。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>包装一个返回密文字节的源 {@link InputFile}，对外暴露为返回明文字节的 InputFile。
 *   <li>用数据密钥与文件 AAD 前缀构造 {@link AesGcmInputStream}，按需分块解密。
 *   <li>基于密文长度推算明文长度，供读取端做边界判断。
 * </ul>
 *
 * <p>设计意图：Iceberg 的流式加密方案将明文切成固定大小的块（见 {@link Ciphers}）， 每块独立用 GCM 加密并附带 nonce
 * 与认证标签。本类只负责把“读密文”这件事转成“读明文”， 真正的按块解密在 {@link AesGcmInputStream} 中完成，从而支持随机定位与跳过。
 *
 * <p>上下游关系：由 {@link EncryptedFiles} 或加密管理器在解密路径中创建；读取端通过 {@link #newStream()} 获得 {@link
 * SeekableInputStream}。
 */
public class AesGcmInputFile implements InputFile {
  private final InputFile sourceFile;
  private final byte[] dataKey;
  private final byte[] fileAADPrefix;
  private long plaintextLength;

  /**
   * 构造一个 AES-GCM 解密输入文件。
   *
   * @param sourceFile 返回密文字节的底层输入文件
   * @param dataKey 用于解密的数据密钥（DEK）
   * @param fileAADPrefix 文件级附加认证数据（AAD）前缀，参与每块的 GCM 认证
   */
  public AesGcmInputFile(InputFile sourceFile, byte[] dataKey, byte[] fileAADPrefix) {
    this.sourceFile = sourceFile;
    this.dataKey = dataKey;
    this.fileAADPrefix = fileAADPrefix;
    this.plaintextLength = -1;
  }

  /**
   * 返回明文长度。
   *
   * <p>逻辑：首调用时基于密文长度（假设所有流使用硬编码的明文块大小）通过 {@link AesGcmInputStream#calculatePlaintextLength(long)}
   * 反推明文长度并缓存， 避免每次都重算。
   */
  @Override
  public long getLength() {
    if (plaintextLength == -1) {
      // Presumes all streams use hard-coded plaintext block size.
      plaintextLength = AesGcmInputStream.calculatePlaintextLength(sourceFile.getLength());
    }

    return plaintextLength;
  }

  /**
   * 创建一个返回明文字节的解密输入流。
   *
   * <p>逻辑：校验密文长度不小于最小合法流长度（{@link Ciphers#MIN_STREAM_LENGTH}）， 再用底层流、密文长度、数据密钥与 AAD 前缀构造 {@link
   * AesGcmInputStream}。
   *
   * @return 按需解密的 {@link SeekableInputStream}
   * @throws IllegalStateException 若密文长度小于最小合法流长度
   */
  @Override
  public SeekableInputStream newStream() {
    long ciphertextLength = sourceFile.getLength();
    Preconditions.checkState(
        ciphertextLength >= Ciphers.MIN_STREAM_LENGTH,
        "Invalid encrypted stream: %d is shorter than the minimum possible stream length",
        ciphertextLength);
    return new AesGcmInputStream(sourceFile.newStream(), ciphertextLength, dataKey, fileAADPrefix);
  }

  /** 返回底层文件的位置标识。 */
  @Override
  public String location() {
    return sourceFile.location();
  }

  /** 返回底层文件是否存在。 */
  @Override
  public boolean exists() {
    return sourceFile.exists();
  }
}
