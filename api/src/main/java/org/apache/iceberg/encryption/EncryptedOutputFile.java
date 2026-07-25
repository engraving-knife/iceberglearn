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
 * 文件级说明：已加密输出文件的薄包装接口。
 *
 * <p>所属模块：iceberg-api（核心 API 抽象层）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>把一个向底层文件系统写入加密字节的 {@link OutputFile} 与其使用的 {@link EncryptionKeyMetadata}（加密密钥元数据）打包在一起。
 *   <li>作为 {@link EncryptionManager#encrypt(OutputFile)} 的返回值，供写端在写流过程中 实时加密，并记录密钥元数据以便后续解密。
 * </ul>
 *
 * <p>设计意图：与 {@link EncryptedInputFile} 对称。通过组合而非继承把加密能力叠加在 任意 OutputFile 之上，保持 IO 层与加密层解耦。
 *
 * <p>上下游关系：由 {@link EncryptionManager} 在加密写流程中产出；被 iceberg-core 的 数据写入路径消费。
 */
public interface EncryptedOutputFile {

  /**
   * 返回一个 {@link OutputFile}，其输出流在写入时会自动加密字节。
   *
   * @return 加密写入的输出文件
   */
  OutputFile encryptingOutputFile();

  /**
   * 返回用于加密 {@link #encryptingOutputFile()} 的加密密钥元数据。
   *
   * @return 密钥元数据
   */
  EncryptionKeyMetadata keyMetadata();
}
