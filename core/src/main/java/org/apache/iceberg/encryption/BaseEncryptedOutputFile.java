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

import org.apache.iceberg.io.OutputFile;

/**
 * 文件级说明：{@link EncryptedOutputFile} 的基础实现。
 *
 * <p>所属模块：iceberg-core（加密包），实现 iceberg-api 的 {@link EncryptedOutputFile} 接口， 把一个写入原始字节的 {@link
 * OutputFile} 与其密钥元数据简单捆绑在一起。
 *
 * <p>职责：仅作数据持有者，持有加密输出文件引用与 {@link EncryptionKeyMetadata}，不执行任何加解密。
 *
 * <p>设计意图：作为最朴素的不可变包装实现，由工厂 {@link EncryptedFiles#encryptedOutput} 创建， 供 {@link
 * org.apache.iceberg.encryption.EncryptionManager} 在加密写入时关联密钥元数据。
 */
class BaseEncryptedOutputFile implements EncryptedOutputFile {

  private final OutputFile encryptingOutputFile;
  private final EncryptionKeyMetadata keyMetadata;

  /**
   * 构造加密输出文件包装。
   *
   * @param encryptingOutputFile 写入原始字节的底层输出文件
   * @param keyMetadata 该文件对应的密钥元数据
   */
  BaseEncryptedOutputFile(OutputFile encryptingOutputFile, EncryptionKeyMetadata keyMetadata) {
    this.encryptingOutputFile = encryptingOutputFile;
    this.keyMetadata = keyMetadata;
  }

  /** 返回写入原始字节的底层输出文件。 */
  @Override
  public OutputFile encryptingOutputFile() {
    return encryptingOutputFile;
  }

  /** 返回该文件的密钥元数据。 */
  @Override
  public EncryptionKeyMetadata keyMetadata() {
    return keyMetadata;
  }
}
