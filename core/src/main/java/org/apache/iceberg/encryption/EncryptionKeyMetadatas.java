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

/**
 * 文件级说明：密钥元数据工具类（静态工厂集合）。
 *
 * <p>所属模块：iceberg-core（加密包），提供把 {@link ByteBuffer}/byte[] 包装为 {@link EncryptionKeyMetadata} 的便捷入口。
 *
 * <p>职责：作为 {@link BaseEncryptionKeyMetadata} 工厂方法的对外别名，统一以 {@code of} 命名 暴露给调用方。
 *
 * <p>设计意图：与 {@link EncryptedFiles} 风格一致，提供简洁的静态工厂；内部完全委托 {@link
 * BaseEncryptionKeyMetadata#fromKeyMetadata}/{@link BaseEncryptionKeyMetadata#fromByteArray}， 复用其
 * null 归一化逻辑。
 *
 * <p>上下游关系：被 core 内部需要构造密钥元数据的代码调用。
 */
public class EncryptionKeyMetadatas {

  /**
   * 由 {@link ByteBuffer} 构造 {@link EncryptionKeyMetadata}，null 归一化为 empty。
   *
   * @param keyMetadata 原始密钥元数据字节，可为 null
   * @return 对应的 {@link EncryptionKeyMetadata} 实例
   */
  public static EncryptionKeyMetadata of(ByteBuffer keyMetadata) {
    return BaseEncryptionKeyMetadata.fromKeyMetadata(keyMetadata);
  }

  /**
   * 由 byte[] 构造 {@link EncryptionKeyMetadata}，null 归一化为 empty。
   *
   * @param keyMetadata 原始密钥元数据字节，可为 null
   * @return 对应的 {@link EncryptionKeyMetadata} 实例
   */
  public static EncryptionKeyMetadata of(byte[] keyMetadata) {
    return BaseEncryptionKeyMetadata.fromByteArray(keyMetadata);
  }

  private EncryptionKeyMetadatas() {}
}
