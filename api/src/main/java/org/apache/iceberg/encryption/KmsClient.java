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

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.Map;

/**
 * 文件级说明：密钥管理服务（KMS）客户端的最小接口。
 *
 * <p>所属模块：iceberg-api（核心 API 抽象层）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>定义与外部 KMS 交互的最小契约：包装密钥（wrapKey）、解包密钥（unwrapKey）、 可选地在 KMS 服务端生成密钥（generateKey）。
 *   <li>提供客户端初始化（{@link #initialize(Map)}）与能力探测 （{@link #supportsKeyGeneration()}）。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>envelope encryption 的核心抽象：数据文件用本地生成的数据加密密钥（DEK）加密， DEK 再由 KMS 中的主密钥（KEK）包装后随文件存储；解密时通过 KMS
 *       解包 DEK。 本接口只暴露 wrap/unwrap，把具体 KMS 协议留给实现。
 *   <li>实现 {@link Serializable}：客户端实例需在 Spark 等引擎中序列化分发到执行节点。
 *   <li>可选密钥生成：部分 KMS 支持服务端生成密钥，{@link #supportsKeyGeneration()} 返回 true 时 Iceberg 优先调用 {@link
 *       #generateKey(String)}，否则本地用 SecureRandom 生成后再 wrap。
 * </ul>
 *
 * <p>上下游关系：被 iceberg-core 的 {@code EncryptionManager} 实现（如 envelope 加密） 调用；具体实现对接 AWS KMS、GCP
 * KMS、Vault 等。
 *
 * @deprecated 将在 v2.0.0 移除，由 {@code KeyManagementClient} 接口替代。
 */
@Deprecated
public interface KmsClient extends Serializable {

  /**
   * 使用 KMS 中由 wrappingKeyId 引用的主密钥包装（加密）一个秘密密钥。
   *
   * <p>包装指用主密钥加密秘密密钥，并可附加 KMS 特定元数据，使后续 unwrap 调用能解密。
   *
   * @param key 待包装的秘密密钥
   * @param wrappingKeyId KMS 中包装密钥的 ID
   * @return 包装后的密钥材料（字符串形式）
   */
  String wrapKey(ByteBuffer key, String wrappingKeyId);

  /**
   * 是否支持在 KMS 服务端生成秘密密钥。
   *
   * <p>返回 true 时，Iceberg 会调用 {@link #generateKey(String)} 让 KMS 生成并包装密钥； 返回 false 时，Iceberg 在本地用
   * SecureRandom 生成密钥，再调用 {@link #wrapKey(ByteBuffer, String)} 包装。
   *
   * @return 支持 KMS 端密钥生成返回 true，否则 false
   */
  default boolean supportsKeyGeneration() {
    return false;
  }

  /**
   * 在 KMS 服务端生成新秘密密钥，并用 wrappingKeyId 引用的主密钥包装。
   *
   * <p>仅当 {@link #supportsKeyGeneration()} 返回 true 时才会被调用。默认实现抛出 {@link
   * UnsupportedOperationException}，由支持服务端生成的实现覆盖。
   *
   * @param wrappingKeyId KMS 中包装密钥的 ID
   * @return 包含原始密钥与包装密钥的 {@link KeyGenerationResult}
   */
  default KeyGenerationResult generateKey(String wrappingKeyId) {
    throw new UnsupportedOperationException("Key generation is not supported in this KmsClient");
  }

  /**
   * 使用 KMS 中由 wrappingKeyId 引用的主密钥解包（解密）一个秘密密钥。
   *
   * @param wrappedKey 包装后的密钥材料（由 {@link #wrapKey(ByteBuffer, String)} 返回， 含加密密钥与可选 KMS 元数据）
   * @param wrappingKeyId KMS 中包装密钥的 ID
   * @return 原始密钥字节
   */
  ByteBuffer unwrapKey(String wrappedKey, String wrappingKeyId);

  /**
   * 使用给定属性初始化 KMS 客户端。
   *
   * @param properties KMS 客户端属性
   */
  void initialize(Map<String, String> properties);

  /**
   * KMS 密钥生成结果：保存生成的原始秘密密钥与其包装形式。
   *
   * <p>设计意图：在服务端生成密钥时，原始密钥需要返回给 Iceberg 用于加密数据， 包装密钥需要持久化到文件元数据以便后续解包，故用本类成对返回。
   */
  class KeyGenerationResult {
    private final ByteBuffer key;
    private final String wrappedKey;

    /**
     * 构造密钥生成结果。
     *
     * @param key 原始秘密密钥
     * @param wrappedKey 包装后的密钥材料
     */
    public KeyGenerationResult(ByteBuffer key, String wrappedKey) {
      this.key = key;
      this.wrappedKey = wrappedKey;
    }

    /**
     * 返回原始秘密密钥。
     *
     * @return 原始密钥字节缓冲
     */
    public ByteBuffer key() {
      return key;
    }

    /**
     * 返回包装后的密钥材料。
     *
     * @return 包装密钥字符串
     */
    public String wrappedKey() {
      return wrappedKey;
    }
  }
}
