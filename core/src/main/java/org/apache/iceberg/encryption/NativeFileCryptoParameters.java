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
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;

/**
 * 文件级说明：原生加密文件参数（每个内容文件一个）。
 *
 * <p>所属模块：iceberg-core（加密包），承载传递给原生支持加密的文件格式（Parquet/ORC）读写器所需的 加密参数。
 *
 * <p>职责：携带文件加密密钥（{@code fileKey}）与加密算法（{@link EncryptionAlgorithm}）， 供格式读写器在原生加密时使用（后续可扩展列密钥与 AAD
 * 前缀）。
 *
 * <p>设计意图：仅适用于具备原生加密能力的格式（Parquet/ORC），把密钥等信息从 Iceberg 密钥管理模块 传递给格式读写器，使加密逻辑由格式自身实现而非 Iceberg
 * 流式包装。采用 Builder 模式构造， 校验密钥非空。
 *
 * <p>上下游关系：由加密管理器构造，通过 {@link NativelyEncryptedFile#setNativeCryptoParameters} 注入到实现了该接口的
 * InputFile/OutputFile。
 */
public class NativeFileCryptoParameters {
  private ByteBuffer fileKey;
  private EncryptionAlgorithm fileEncryptionAlgorithm;

  private NativeFileCryptoParameters(
      ByteBuffer fileKey, EncryptionAlgorithm fileEncryptionAlgorithm) {
    Preconditions.checkState(fileKey != null, "File encryption key is not supplied");
    this.fileKey = fileKey;
    this.fileEncryptionAlgorithm = fileEncryptionAlgorithm;
  }

  /**
   * 创建构建器。
   *
   * @param fileKey 单文件加密密钥，例如 Parquet 加密中作为“footer key”的 DEK
   * @return 新的 {@link Builder}
   */
  public static Builder create(ByteBuffer fileKey) {
    return new Builder(fileKey);
  }

  /** 构建器：链式设置加密算法并构造 {@link NativeFileCryptoParameters}。 */
  public static class Builder {
    private ByteBuffer fileKey;
    private EncryptionAlgorithm fileEncryptionAlgorithm;

    private Builder(ByteBuffer fileKey) {
      this.fileKey = fileKey;
    }

    /** 设置文件加密算法。 */
    public Builder encryptionAlgorithm(EncryptionAlgorithm encryptionAlgorithm) {
      this.fileEncryptionAlgorithm = encryptionAlgorithm;
      return this;
    }

    /** 构造并返回 {@link NativeFileCryptoParameters} 实例。 */
    public NativeFileCryptoParameters build() {
      return new NativeFileCryptoParameters(fileKey, fileEncryptionAlgorithm);
    }
  }

  /** 返回文件加密密钥。 */
  public ByteBuffer fileKey() {
    return fileKey;
  }

  /** 返回文件加密算法。 */
  public EncryptionAlgorithm encryptionAlgorithm() {
    return fileEncryptionAlgorithm;
  }
}
