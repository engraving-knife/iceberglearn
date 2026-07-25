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
 * 文件级说明：原生加密文件接口。
 *
 * <p>所属模块：iceberg-core（加密包），应用于 {@link org.apache.iceberg.io.OutputFile} 与 {@link
 * org.apache.iceberg.io.InputFile} 实现，使其能够接收加密参数。
 *
 * <p>职责：作为密钥管理模块与原生支持加密的文件格式（Parquet/ORC）读写器之间的参数传递通道， 通过 get/set 传递 {@link
 * NativeFileCryptoParameters}（加密密钥等）。
 *
 * <p>设计意图：Parquet/ORC 等格式自带加密能力，加密由格式读写器内部完成而非 Iceberg 流式包装。 本接口让 Iceberg
 * 的加密管理器能把密钥等参数注入到这些文件实现中，从而复用格式原生加密。
 *
 * <p>上下游关系：由加密管理器在包装原生加密文件时调用 {@link #setNativeCryptoParameters} 注入参数； 格式读写器在读写时通过 {@link
 * #nativeCryptoParameters} 读取参数。
 */
public interface NativelyEncryptedFile {
  /** 返回当前文件的原生加密参数，可能为 null（未设置）。 */
  NativeFileCryptoParameters nativeCryptoParameters();

  /** 设置当前文件的原生加密参数。 */
  void setNativeCryptoParameters(NativeFileCryptoParameters nativeCryptoParameters);
}
