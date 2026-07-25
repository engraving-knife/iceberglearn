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
import org.apache.iceberg.io.InputFile;
import org.apache.iceberg.io.OutputFile;
import org.apache.iceberg.relocated.com.google.common.collect.Iterables;

/**
 * 文件级说明：表数据文件加密/解密管理器接口。
 *
 * <p>所属模块：iceberg-api（核心 API 抽象层）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>解密：根据 {@link EncryptedInputFile} 携带的密钥元数据，把读取原始加密字节的 InputFile 转换为返回明文流的 {@link InputFile}。
 *   <li>加密：把写入原始字节的 {@link OutputFile} 包装为写入加密字节的 {@link EncryptedOutputFile}，并产出对应的密钥元数据。
 *   <li>提供单文件与批量（Iterable）两种入口，批量入口允许实现做优化（如预取密钥）。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>实现 {@link Serializable}：实例可能在 Spark 等引擎中被序列化后分发到执行节点， 故要求可序列化。
 *   <li>批量入口默认委托单文件方法：实现侧只需实现单文件版本即可工作；若需批量化 优化（如批量预取密钥、减少 KMS 往返），可覆盖默认方法。
 *   <li>面向接口：具体加密算法（如 envelope encryption、AES-GCM）由实现决定， API 层只描述加密/解密契约。
 * </ul>
 *
 * <p>上下游关系：被 iceberg-core 的数据读写路径调用；实现侧通常结合 {@link KmsClient} 完成 envelope 加密。
 */
public interface EncryptionManager extends Serializable {

  /**
   * 解密单个加密输入文件。
   *
   * <p>逻辑：根据 {@link EncryptedInputFile#encryptedInputFile()} 提供的原始加密字节与 {@link
   * EncryptedInputFile#keyMetadata()} 提供的密钥元数据，定位解密密钥并返回 一个 {@link InputFile}，其输入流读取时返回明文字节。
   *
   * @param encrypted 已加密的输入文件包装
   * @return 返回明文输入流的 {@link InputFile}
   */
  InputFile decrypt(EncryptedInputFile encrypted);

  /**
   * 批量解密多个加密输入文件（同一上下文）。
   *
   * <p>逻辑：默认实现使用 {@link Iterables#transform} 对每个元素调用 {@link
   * #decrypt(EncryptedInputFile)}。实现可覆盖以做批量优化，例如对输入迭代器 做预读（lookahead）并批量拉取密钥，减少 KMS 往返次数。
   *
   * @param encrypted 已加密输入文件集合
   * @return 返回明文输入流的 {@link InputFile} 迭代视图
   */
  default Iterable<InputFile> decrypt(Iterable<EncryptedInputFile> encrypted) {
    return Iterables.transform(encrypted, this::decrypt);
  }

  /**
   * 加密单个输出文件。
   *
   * <p>逻辑：给定一个向底层文件系统写原始字节的 {@link OutputFile}，返回一个 {@link EncryptedOutputFile}，其 {@link
   * EncryptedOutputFile#encryptingOutputFile()} 在写入时自动加密字节，{@link
   * EncryptedOutputFile#keyMetadata()} 指向所用密钥。
   *
   * @param rawOutput 原始输出文件
   * @return 已加密输出文件包装
   */
  EncryptedOutputFile encrypt(OutputFile rawOutput);

  /**
   * 批量加密多个输出文件（同一上下文）。
   *
   * <p>逻辑：默认实现使用 {@link Iterables#transform} 对每个元素调用 {@link
   * #encrypt(OutputFile)}。实现可覆盖以做批量优化，例如预读迭代器并批量获取 密钥，减少 KMS 往返次数。
   *
   * @param rawOutput 原始输出文件集合
   * @return 已加密输出文件迭代视图
   */
  default Iterable<EncryptedOutputFile> encrypt(Iterable<OutputFile> rawOutput) {
    return Iterables.transform(rawOutput, this::encrypt);
  }
}
