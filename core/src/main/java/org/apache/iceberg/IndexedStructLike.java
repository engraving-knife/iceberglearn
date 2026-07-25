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
package org.apache.iceberg;

import org.apache.avro.generic.IndexedRecord;

/**
 * {@link IndexedRecord} 适配器：将 {@link StructLike} 包装为 Avro 可写入的 IndexedRecord。
 *
 * <p>所属模块：iceberg-core，用于把 Iceberg 内部的 StructLike 数据写入 Avro 文件 （如 manifest 文件）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>持有 Avro schema 与被包装的 StructLike，代理所有 get/set/put 调用。
 *   <li>提供 {@link #wrap(StructLike)} 复用适配器实例，避免频繁创建对象。
 * </ul>
 *
 * <p>设计意图：Avro 的 {@link IndexedRecord} 接口与 Iceberg 的 {@link StructLike} 接口
 * 方法签名相似但独立，本类作为桥接避免数据拷贝；{@code wrap} 方法支持对象复用， 适配写入流式场景。
 *
 * <p>上下游关系：被 {@code ManifestWriter}、Avro 写入工具在序列化 manifest 条目时使用。
 */
class IndexedStructLike implements StructLike, IndexedRecord {
  private final org.apache.avro.Schema avroSchema;
  private StructLike wrapped = null;

  /**
   * 构造适配器，绑定 Avro schema。
   *
   * @param avroSchema Avro schema
   */
  IndexedStructLike(org.apache.avro.Schema avroSchema) {
    this.avroSchema = avroSchema;
  }

  /**
   * 包装一个 StructLike 并返回自身，支持实例复用。
   *
   * @param struct 被包装的数据
   * @return this
   */
  IndexedStructLike wrap(StructLike struct) {
    this.wrapped = struct;
    return this;
  }

  @Override
  public int size() {
    return wrapped.size();
  }

  @Override
  public <T> T get(int pos, Class<T> javaClass) {
    return wrapped.get(pos, javaClass);
  }

  @Override
  public Object get(int pos) {
    return get(pos, Object.class);
  }

  @Override
  public <T> void set(int pos, T value) {
    wrapped.set(pos, value);
  }

  @Override
  public void put(int pos, Object value) {
    set(pos, value);
  }

  @Override
  public org.apache.avro.Schema getSchema() {
    return avroSchema;
  }
}
