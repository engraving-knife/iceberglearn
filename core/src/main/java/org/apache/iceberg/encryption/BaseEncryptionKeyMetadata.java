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
import org.apache.iceberg.util.ByteBuffers;

/**
 * 文件级说明：{@link EncryptionKeyMetadata} 的基础实现，直接持有原始字节缓冲。
 *
 * <p>所属模块：iceberg-core（加密包），实现 iceberg-api 的 {@link EncryptionKeyMetadata} 接口，
 * 作为“不解释字节含义”的通用密钥元数据载体。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>持有密钥元数据的 {@link ByteBuffer}，提供 {@link #buffer()} 与 {@link #copy()}。
 *   <li>提供静态工厂方法，把 {@link ByteBuffer} 或 byte[] 包装为实例，null 时返回 {@link
 *       EncryptionKeyMetadata#empty()}。
 * </ul>
 *
 * <p>设计意图：本类对字节内容不做任何结构化解释（不区分 DEK/AAD 等），适用于密钥元数据由 外部（如 envelope 加密实现）自行解析的场景；与之相对，{@link
 * KeyMetadata} 则是带 Avro schema 的结构化实现。null 归一化为 empty，简化调用方判空。
 *
 * <p>上下游关系：被 {@link EncryptedFiles} 工厂、{@link EncryptionKeyMetadatas} 工具类用于
 * 构造实例；被加密管理器在包装输入/输出文件时使用。
 */
class BaseEncryptionKeyMetadata implements EncryptionKeyMetadata {

  /**
   * 由 {@link ByteBuffer} 构造密钥元数据，null 归一化为 {@link EncryptionKeyMetadata#empty()}。
   *
   * @param keyMetadata 原始密钥元数据字节，可为 null
   * @return 对应的 {@link EncryptionKeyMetadata} 实例
   */
  public static EncryptionKeyMetadata fromKeyMetadata(ByteBuffer keyMetadata) {
    if (keyMetadata == null) {
      return EncryptionKeyMetadata.empty();
    }
    return new BaseEncryptionKeyMetadata(keyMetadata);
  }

  /**
   * 由 byte[] 构造密钥元数据，null 归一化为 {@link EncryptionKeyMetadata#empty()}。
   *
   * @param keyMetadata 原始密钥元数据字节，可为 null
   * @return 对应的 {@link EncryptionKeyMetadata} 实例
   */
  public static EncryptionKeyMetadata fromByteArray(byte[] keyMetadata) {
    if (keyMetadata == null) {
      return EncryptionKeyMetadata.empty();
    }
    return fromKeyMetadata(ByteBuffer.wrap(keyMetadata));
  }

  private final ByteBuffer keyMetadata;

  private BaseEncryptionKeyMetadata(ByteBuffer keyMetadata) {
    this.keyMetadata = keyMetadata;
  }

  /** 返回原始密钥元数据字节缓冲（只读视图由调用方按需处理）。 */
  @Override
  public ByteBuffer buffer() {
    return keyMetadata;
  }

  /** 复制一份独立的密钥元数据（深拷贝底层字节），避免共享缓冲被修改。 */
  @Override
  public EncryptionKeyMetadata copy() {
    return new BaseEncryptionKeyMetadata(ByteBuffers.copy(keyMetadata));
  }
}
