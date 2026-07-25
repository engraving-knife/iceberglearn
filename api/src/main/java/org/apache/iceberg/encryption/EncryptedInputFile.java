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

/**
 * 文件级说明：已加密输入文件的薄包装接口。
 *
 * <p>所属模块：iceberg-api（核心 API 抽象层）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>把一个读取原始加密字节的 {@link InputFile} 与其对应的 {@link EncryptionKeyMetadata} （解密所需密钥元数据）打包在一起。
 *   <li>作为 {@link EncryptionManager#decrypt(EncryptedInputFile)} 的输入，让加密管理器 据此解密文件并返回可读取明文流的
 *       InputFile。
 * </ul>
 *
 * <p>设计意图：使用“标记接口 + 组合”而非直接扩展 InputFile，使加密语义与底层 IO 解耦。 实现可来自任何 IO 后端（HDFS、S3
 * 等），只要附带正确的密钥元数据即可被统一解密处理。
 *
 * <p>上下游关系：由 iceberg-core 的加密实现构造；被 {@link EncryptionManager} 消费。
 */
public interface EncryptedInputFile {

  /**
   * 返回从底层文件系统读取原始加密字节的 {@link InputFile}。
   *
   * @return 读取加密字节的输入文件
   */
  InputFile encryptedInputFile();

  /**
   * 返回用于解密 {@link #encryptedInputFile()} 的加密密钥元数据。
   *
   * @return 密钥元数据
   */
  EncryptionKeyMetadata keyMetadata();
}
