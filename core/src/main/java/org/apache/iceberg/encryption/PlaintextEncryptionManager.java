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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 文件级说明：明文加密管理器（实际不加密）。
 *
 * <p>所属模块：iceberg-core（加密包），实现 iceberg-api 的 {@link EncryptionManager} 接口， 作为“未启用加密”时的占位实现。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>解密：直接返回底层加密输入文件，不做任何解密（视为明文）。
 *   <li>加密：把输出文件包装为 {@link EncryptedOutputFile} 但密钥元数据为 null（即不加密）。
 * </ul>
 *
 * <p>设计意图：当表未配置加密时使用本实现，使读写路径无需特判“是否加密”。若解密时发现文件其实 携带了密钥元数据，记录一条警告日志提示配置可能不一致，但不阻断读取。
 *
 * <p>上下游关系：在未启用加密的表中作为 {@link EncryptionManager} 的默认实现被读写路径使用。
 */
public class PlaintextEncryptionManager implements EncryptionManager {
  private static final Logger LOG = LoggerFactory.getLogger(PlaintextEncryptionManager.class);

  /**
   * “解密”输入文件：直接返回底层文件。
   *
   * <p>逻辑：若输入携带了非空密钥元数据，记录警告（提示当前使用明文管理器但文件可能是加密的）， 然后直接返回 {@link
   * EncryptedInputFile#encryptedInputFile()}。
   *
   * @param encrypted 加密输入文件包装
   * @return 底层输入文件（视为明文）
   */
  @Override
  public InputFile decrypt(EncryptedInputFile encrypted) {
    if (encrypted.keyMetadata().buffer() != null) {
      LOG.warn(
          "File encryption key metadata is present, but currently using PlaintextEncryptionManager.");
    }
    return encrypted.encryptedInputFile();
  }

  /**
   * “加密”输出文件：返回密钥元数据为 null 的包装（即不加密）。
   *
   * @param rawOutput 原始输出文件
   * @return 不加密的 {@link EncryptedOutputFile} 包装
   */
  @Override
  public EncryptedOutputFile encrypt(OutputFile rawOutput) {
    return EncryptedFiles.encryptedOutput(rawOutput, (ByteBuffer) null);
  }
}
