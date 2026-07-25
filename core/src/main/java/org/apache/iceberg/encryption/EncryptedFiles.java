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

import java.nio.ByteBuffer;
import org.apache.iceberg.io.InputFile;
import org.apache.iceberg.io.OutputFile;

/**
 * 文件级说明：加密输入/输出文件的工厂类。
 *
 * <p>所属模块：iceberg-core（加密包），提供创建 {@link EncryptedInputFile} 与 {@link EncryptedOutputFile} 实例的统一入口。
 *
 * <p>职责：把原始 {@link InputFile}/{@link OutputFile} 与密钥元数据（支持 {@link EncryptionKeyMetadata}、{@link
 * ByteBuffer}、byte[] 三种形态）组合为加密文件包装。
 *
 * <p>设计意图：集中构造入口，屏蔽 {@link BaseEncryptedInputFile}/{@link BaseEncryptedOutputFile}
 * 实现细节；通过重载让调用方用最自然的密钥元数据形态构造，byte[]/ByteBuffer 形态统一委托给 {@link BaseEncryptionKeyMetadata} 完成归一化（含
 * null 转 empty）。
 *
 * <p>上下游关系：被加密管理器与读写路径调用，构造出的包装交给 {@link org.apache.iceberg.encryption.EncryptionManager} 做实际加解密。
 */
public class EncryptedFiles {

  /**
   * 由 {@link InputFile} 与 {@link EncryptionKeyMetadata} 构造加密输入文件。
   *
   * @param encryptedInputFile 返回密文字节的输入文件
   * @param keyMetadata 密钥元数据
   * @return 加密输入文件包装
   */
  public static EncryptedInputFile encryptedInput(
      InputFile encryptedInputFile, EncryptionKeyMetadata keyMetadata) {
    return new BaseEncryptedInputFile(encryptedInputFile, keyMetadata);
  }

  /**
   * 由 {@link InputFile} 与 {@link ByteBuffer} 形态的密钥元数据构造加密输入文件。
   *
   * <p>逻辑：委托 {@link BaseEncryptionKeyMetadata#fromKeyMetadata(ByteBuffer)} 归一化后再构造。
   *
   * @param encryptedInputFile 返回密文字节的输入文件
   * @param keyMetadata 密钥元数据字节，可为 null
   * @return 加密输入文件包装
   */
  public static EncryptedInputFile encryptedInput(
      InputFile encryptedInputFile, ByteBuffer keyMetadata) {
    return encryptedInput(
        encryptedInputFile, BaseEncryptionKeyMetadata.fromKeyMetadata(keyMetadata));
  }

  /**
   * 由 {@link InputFile} 与 byte[] 形态的密钥元数据构造加密输入文件。
   *
   * <p>逻辑：委托 {@link BaseEncryptionKeyMetadata#fromByteArray(byte[])} 归一化后再构造。
   *
   * @param encryptedInputFile 返回密文字节的输入文件
   * @param keyMetadata 密钥元数据字节，可为 null
   * @return 加密输入文件包装
   */
  public static EncryptedInputFile encryptedInput(
      InputFile encryptedInputFile, byte[] keyMetadata) {
    return encryptedInput(encryptedInputFile, BaseEncryptionKeyMetadata.fromByteArray(keyMetadata));
  }

  /**
   * 由 {@link OutputFile} 与 {@link EncryptionKeyMetadata} 构造加密输出文件。
   *
   * @param encryptingOutputFile 写入原始字节的输出文件
   * @param keyMetadata 密钥元数据
   * @return 加密输出文件包装
   */
  public static EncryptedOutputFile encryptedOutput(
      OutputFile encryptingOutputFile, EncryptionKeyMetadata keyMetadata) {
    return new BaseEncryptedOutputFile(encryptingOutputFile, keyMetadata);
  }

  /**
   * 由 {@link OutputFile} 与 {@link ByteBuffer} 形态的密钥元数据构造加密输出文件。
   *
   * <p>逻辑：委托 {@link BaseEncryptionKeyMetadata#fromKeyMetadata(ByteBuffer)} 归一化后再构造。
   *
   * @param encryptingOutputFile 写入原始字节的输出文件
   * @param keyMetadata 密钥元数据字节，可为 null
   * @return 加密输出文件包装
   */
  public static EncryptedOutputFile encryptedOutput(
      OutputFile encryptingOutputFile, ByteBuffer keyMetadata) {
    return encryptedOutput(
        encryptingOutputFile, BaseEncryptionKeyMetadata.fromKeyMetadata(keyMetadata));
  }

  /**
   * 由 {@link OutputFile} 与 byte[] 形态的密钥元数据构造加密输出文件。
   *
   * <p>逻辑：委托 {@link BaseEncryptionKeyMetadata#fromByteArray(byte[])} 归一化后再构造。
   *
   * @param encryptedOutputFile 写入原始字节的输出文件
   * @param keyMetadata 密钥元数据字节，可为 null
   * @return 加密输出文件包装
   */
  public static EncryptedOutputFile encryptedOutput(
      OutputFile encryptedOutputFile, byte[] keyMetadata) {
    return encryptedOutput(
        encryptedOutputFile, BaseEncryptionKeyMetadata.fromByteArray(keyMetadata));
  }

  private EncryptedFiles() {}
}
