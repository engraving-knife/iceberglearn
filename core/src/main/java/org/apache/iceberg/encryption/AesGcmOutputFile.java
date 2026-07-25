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

import org.apache.iceberg.io.InputFile;
import org.apache.iceberg.io.OutputFile;
import org.apache.iceberg.io.PositionOutputStream;

/**
 * 文件级说明：基于 AES-GCM 的加密输出文件包装器。
 *
 * <p>所属模块：iceberg-core（加密包），实现 iceberg-api 的 {@link OutputFile} 契约， 把写入的明文字节透明地加密为密文字节再写入底层文件。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>包装一个接收密文字节的底层 {@link OutputFile}，对外暴露为接收明文字节的 OutputFile。
 *   <li>创建 {@link AesGcmOutputStream} 对明文做分块 GCM 加密后写出。
 *   <li>支持通过 {@link #toInputFile()} 转为对应的解密输入文件视图。
 * </ul>
 *
 * <p>设计意图：与 {@link AesGcmInputFile} 对称，写入侧同样按固定块分块加密， 保证读侧可随机定位解密。
 *
 * <p>上下游关系：由加密管理器在加密写入路径中创建；写入端调用 {@link #create()} 或 {@link #createOrOverwrite()} 获得返回明文写入位置的
 * {@link PositionOutputStream}。
 */
public class AesGcmOutputFile implements OutputFile {
  private final OutputFile targetFile;
  private final byte[] dataKey;
  private final byte[] fileAADPrefix;

  /**
   * 构造一个 AES-GCM 加密输出文件。
   *
   * @param targetFile 接收密文字节的底层输出文件
   * @param dataKey 用于加密的数据密钥（DEK）
   * @param fileAADPrefix 文件级 AAD 前缀，参与每块的 GCM 认证
   */
  public AesGcmOutputFile(OutputFile targetFile, byte[] dataKey, byte[] fileAADPrefix) {
    this.targetFile = targetFile;
    this.dataKey = dataKey;
    this.fileAADPrefix = fileAADPrefix;
  }

  /** 创建新的加密输出流（要求目标文件不存在）。 */
  @Override
  public PositionOutputStream create() {
    return new AesGcmOutputStream(targetFile.create(), dataKey, fileAADPrefix);
  }

  /** 创建或覆盖的加密输出流。 */
  @Override
  public PositionOutputStream createOrOverwrite() {
    return new AesGcmOutputStream(targetFile.createOrOverwrite(), dataKey, fileAADPrefix);
  }

  /** 返回底层文件的位置标识。 */
  @Override
  public String location() {
    return targetFile.location();
  }

  /**
   * 转换为对应的解密输入文件视图。
   *
   * <p>逻辑：复用相同的密钥与 AAD 前缀，把底层文件的输入视图包装为 {@link AesGcmInputFile}，从而对同一密文既能写也能读。
   */
  @Override
  public InputFile toInputFile() {
    return new AesGcmInputFile(targetFile.toInputFile(), dataKey, fileAADPrefix);
  }
}
