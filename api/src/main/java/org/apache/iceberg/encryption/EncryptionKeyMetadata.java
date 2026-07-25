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
 * 文件级说明：加密密钥元数据（不透明字节包装）。
 *
 * <p>所属模块：iceberg-api（核心 API 抽象层）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>以 {@link ByteBuffer} 形式承载文件加密密钥的元数据（如经 KMS 包装后的密钥、 密钥 ID、IV 等实现自定义信息），用于在加密/解密流程中传递。
 *   <li>提供 {@link #empty()} 空元数据占位与 {@link #copy()} 拷贝能力。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>语义化 typedef：相对于直接传递 ByteBuffer，本接口以类型显式表达“这是密钥元数据”， 避免参数语义混淆。
 *   <li>不透明性：API 层不规定 buffer 的内部格式，由具体加密实现（如 envelope 加密） 自行解释，从而支持多种加密方案。
 *   <li>空对象：{@link #EMPTY} 单例表示无密钥元数据（如明文文件），避免 null 检查。
 * </ul>
 *
 * <p>上下游关系：被 {@link EncryptedInputFile}、{@link EncryptedOutputFile} 携带； 由 {@link EncryptionManager}
 * 实现侧生成与解析。
 */
public interface EncryptionKeyMetadata {

  /** 空密钥元数据单例：buffer 返回 null，copy 返回自身。 */
  EncryptionKeyMetadata EMPTY =
      new EncryptionKeyMetadata() {
        @Override
        public ByteBuffer buffer() {
          return null;
        }

        @Override
        public EncryptionKeyMetadata copy() {
          return this;
        }
      };

  /**
   * 返回空密钥元数据单例。
   *
   * @return 空 {@link EncryptionKeyMetadata}
   */
  static EncryptionKeyMetadata empty() {
    return EMPTY;
  }

  /**
   * 返回表示文件加密密钥元数据的不透明字节缓冲。
   *
   * @return 密钥元数据字节缓冲；空元数据返回 null
   */
  ByteBuffer buffer();

  /**
   * 返回本密钥元数据的副本（独立缓冲）。
   *
   * <p>设计要点：避免多次消费同一缓冲导致 position/limit 互相干扰，调用方拿到独立副本 可自由读取。
   *
   * @return 元数据副本
   */
  EncryptionKeyMetadata copy();
}
