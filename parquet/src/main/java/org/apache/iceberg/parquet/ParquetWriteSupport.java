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
package org.apache.iceberg.parquet;

import java.util.Map;
import org.apache.hadoop.conf.Configuration;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.parquet.hadoop.api.WriteSupport;
import org.apache.parquet.io.api.RecordConsumer;
import org.apache.parquet.schema.MessageType;

/**
 * 文件级说明：Parquet {@link WriteSupport} 装饰器，叠加 Iceberg 的 schema 与 key-value 元数据。
 *
 * <p>所属模块：iceberg-parquet（写入侧，包装底层 WriteSupport 以注入 Iceberg schema 与元数据）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>在 init 时把 Iceberg schema（MessageType）与 key-value 元数据合并到 WriteContext。
 *   <li>委托底层 WriteSupport 完成 prepareForRead/write/finalizeWrite。
 *   <li>在 getName 时返回 "Iceberg/" 前缀以标识来源。
 * </ul>
 *
 * <p>设计意图：装饰器模式，在不修改底层 WriteSupport（如 AvroWriteSupport）的前提下， 注入 Iceberg 特有的 schema 与元数据（如
 * iceberg.schema）。
 *
 * <p>上下游关系：被 {@link Parquet.ParquetWriteBuilder} 构造，由 parquet-mr ParquetWriter 使用。
 *
 * @param <T> 数据类型
 */
class ParquetWriteSupport<T> extends WriteSupport<T> {
  private final MessageType type;
  private final Map<String, String> keyValueMetadata;
  private final WriteSupport<T> wrapped;

  /**
   * 构造 WriteSupport 装饰器。
   *
   * @param type Parquet schema
   * @param keyValueMetadata 要写入 footer 的 key-value 元数据
   * @param writeSupport 被包装的底层 WriteSupport
   */
  ParquetWriteSupport(
      MessageType type, Map<String, String> keyValueMetadata, WriteSupport<T> writeSupport) {
    this.type = type;
    this.keyValueMetadata = keyValueMetadata;
    this.wrapped = writeSupport;
  }

  /**
   * 初始化写入上下文，合并 Iceberg 元数据与底层 WriteSupport 的元数据。
   *
   * <p>逻辑：先调用底层 init 获取其 WriteContext，再把 Iceberg 的 key-value 元数据与 底层 extraMetaData 合并，返回使用 Iceberg
   * schema 的 WriteContext。
   *
   * @param configuration Hadoop 配置
   * @return 合并后的 WriteContext
   */
  @Override
  public WriteContext init(Configuration configuration) {
    WriteContext wrappedContext = wrapped.init(configuration);
    Map<String, String> metadata =
        ImmutableMap.<String, String>builder()
            .putAll(keyValueMetadata)
            .putAll(wrappedContext.getExtraMetaData())
            .buildOrThrow();
    return new WriteContext(type, metadata);
  }

  /** 返回 "Iceberg/" + 底层名称，标识写入来源。 */
  @Override
  public String getName() {
    return "Iceberg/" + wrapped.getName();
  }

  /** 委托底层 WriteSupport 设置 RecordConsumer。 */
  @Override
  public void prepareForWrite(RecordConsumer recordConsumer) {
    wrapped.prepareForWrite(recordConsumer);
  }

  /** 委托底层 WriteSupport 写入一条记录。 */
  @Override
  public void write(T t) {
    wrapped.write(t);
  }

  /** 委托底层 WriteSupport 返回 FinalizedWriteContext。 */
  @Override
  public FinalizedWriteContext finalizeWrite() {
    return wrapped.finalizeWrite();
  }
}
