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

import java.io.Closeable;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.Map;

/**
 * 文件级说明：密钥管理服务（KMS）的最小客户端接口。
 *
 * <p>所属模块：iceberg-core（加密包），是 envelope 加密方案中与外部 KMS 交互的抽象边界。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>包装（wrap）：用 KMS 中由 ID 引用的主密钥加密数据密钥（DEK）。
 *   <li>解包（unwrap）：把已包装的 DEK 还原为明文 DEK。
 *   <li>可选：在 KMS 服务端生成新密钥并直接返回其明文与包装形式。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>实现 {@link Serializable} 与 {@link Closeable}：客户端实例可能被序列化分发到执行节点， 且可能被多个加密管理器共享，需要可关闭以释放资源。
 *   <li>批量化解耦：本接口只定义单密钥操作，批量优化由 {@link org.apache.iceberg.encryption.EncryptionManager}
 *       的批量方法在更上层处理，避免 KMS 接口承担过多职责。
 *   <li>密钥生成能力可选：{@link #supportsKeyGeneration()} 默认 false，不支持时 Iceberg 退化为本地生成密钥再包装。
 * </ul>
 *
 * <p>上下游关系：由具体 KMS 集成模块实现；被 envelope 加密管理器调用完成 DEK 的包装/解包。
 */
interface KeyManagementClient extends Serializable, Closeable {

  /**
   * 用 KMS 中由 {@code wrappingKeyId} 引用的主密钥包装（加密）一个数据密钥。
   *
   * <p>包装指用主密钥加密数据密钥，并可附加 KMS 特定元数据，使后续 {@link #unwrapKey} 能还原。
   *
   * @param key 待包装的数据密钥
   * @param wrappingKeyId 主密钥（包装密钥）在 KMS 中的标识
   * @return 已包装的密钥字节
   */
  ByteBuffer wrapKey(ByteBuffer key, String wrappingKeyId);

  /**
   * 是否支持在 KMS 服务端生成密钥。
   *
   * <p>返回 true 表示实现希望利用 KMS 的密钥生成能力；返回 false 时 Iceberg 会本地生成数据密钥 （用 {@link
   * java.security.SecureRandom}）再调用 {@link #wrapKey} 包装。
   *
   * @return 支持 KMS 端密钥生成返回 true，否则 false
   */
  default boolean supportsKeyGeneration() {
    return false;
  }

  /**
   * 在 KMS 服务端生成新数据密钥，并用 {@code wrappingKeyId} 引用的主密钥包装。
   *
   * <p>仅当 {@link #supportsKeyGeneration()} 返回 true 时才会被调用。
   *
   * @param wrappingKeyId 主密钥（包装密钥）在 KMS 中的标识
   * @return 同时包含明文密钥与已包装密钥的 {@link KeyGenerationResult}
   */
  default KeyGenerationResult generateKey(String wrappingKeyId) {
    throw new UnsupportedOperationException("Key generation is not supported in this KmsClient");
  }

  /**
   * 用 KMS 中由 {@code wrappingKeyId} 引用的主密钥解包（解密）一个已包装的数据密钥。
   *
   * @param wrappedKey 已包装的密钥字节（{@link #wrapKey} 的返回值，含密文与可选 KMS 元数据）
   * @param wrappingKeyId 主密钥（包装密钥）在 KMS 中的标识
   * @return 明文数据密钥字节
   */
  ByteBuffer unwrapKey(ByteBuffer wrappedKey, String wrappingKeyId);

  /**
   * 用给定属性初始化 KMS 客户端。
   *
   * @param properties KMS 客户端配置属性
   */
  void initialize(Map<String, String> properties);

  /**
   * 关闭 KMS 客户端以释放底层资源。
   *
   * <p>当客户端被多个加密管理器共享时，可能在不同的线程中被触发关闭。
   */
  @Override
  default void close() {}

  /** 对于支持密钥生成的 KMS，承载生成结果：明文密钥与其包装形式。 */
  class KeyGenerationResult {
    private final ByteBuffer key;
    private final ByteBuffer wrappedKey;

    KeyGenerationResult(ByteBuffer key, ByteBuffer wrappedKey) {
      this.key = key;
      this.wrappedKey = wrappedKey;
    }

    /** 返回明文数据密钥。 */
    public ByteBuffer key() {
      return key;
    }

    /** 返回已包装的数据密钥。 */
    public ByteBuffer wrappedKey() {
      return wrappedKey;
    }
  }
}
