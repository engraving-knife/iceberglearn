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

/**
 * 文件加密支持的算法枚举。
 *
 * <p>所属模块：iceberg-core 的 encryption 包，定义可用于内容文件加密的对称算法种类。 主要被具备原生加密能力的文件格式（Parquet/ORC）相关参数对象使用，见
 * {@link NativeFileCryptoParameters}。
 *
 * <p>设计意图：不同算法在“机密性 / 完整性 / 性能”之间做不同权衡，由调用方按场景选择； GCM 系列同时提供机密性与完整性，CTR 仅提供机密性但吞吐更高。
 */
public enum EncryptionAlgorithm {
  /**
   * AES 计数器模式（CTR）：吞吐高、加密速度快，但仅提供机密性、不保证内容完整性。
   *
   * <p>CTR Cipher 的输入包括：1) 加密密钥；2) 16 字节初始化向量（12 字节 Nonce + 4 字节计数器）；3) 明文数据。
   */
  AES_CTR,
  /**
   * AES 伽罗瓦/计数器模式（GCM）：在 CTR 基础上叠加 Galois 认证，同时保证数据机密性与完整性。
   *
   * <p>GCM Cipher 的输入包括：1) 加密密钥；2) 12 字节初始化向量；3) 附加认证数据（AAD）；4) 明文数据。
   */
  AES_GCM,
  /**
   * GCM 与 CTR 的混合模式，适用于 Parquet 等文件格式：除 page 外的所有模块用 GCM 加密以保证完整性， page 内批量数据用 CTR
   * 加密以提升性能。权衡是攻击者可能篡改 CTR 加密的 page 数据。
   */
  AES_GCM_CTR
}
