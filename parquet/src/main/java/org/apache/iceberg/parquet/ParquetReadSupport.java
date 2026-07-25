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
import java.util.Set;
import org.apache.hadoop.conf.Configuration;
import org.apache.iceberg.Schema;
import org.apache.iceberg.avro.AvroSchemaUtil;
import org.apache.iceberg.mapping.NameMapping;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.apache.parquet.avro.AvroReadSupport;
import org.apache.parquet.hadoop.api.InitContext;
import org.apache.parquet.hadoop.api.ReadSupport;
import org.apache.parquet.io.api.RecordMaterializer;
import org.apache.parquet.schema.MessageType;

/**
 * 文件级说明：Parquet {@link ReadSupport} 实现，基于 Iceberg {@link Schema} 字段 ID 做列投影。
 *
 * <p>所属模块：iceberg-parquet（读取侧 schema 投影，包装底层 ReadSupport 以支持 Iceberg 字段 ID）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>在 init 阶段按 expectedSchema 对文件 schema 做列裁剪（prune），生成投影 schema。
 *   <li>处理无 ID 的旧文件：通过 NameMapping 补 ID 或按名称回退匹配。
 *   <li>设置 Avro 兼容选项与 Avro 投影 schema，委托底层 ReadSupport 完成实际读取。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>装饰器模式：包装 AvroReadSupport 等底层 ReadSupport，在其之上叠加 Iceberg 字段 ID 投影逻辑，保持与 parquet-mr 的兼容性。
 *   <li>三路投影：有 ID 走 pruneColumns，无 ID 有 NameMapping 走 applyNameMapping， 无 ID 无 NameMapping 走
 *       pruneColumnsFallback 按名称匹配。
 * </ul>
 *
 * <p>上下游关系：被 {@link Parquet.ParquetReadBuilder} 构造，由 parquet-mr ParquetReader 使用； 依赖 {@link
 * ParquetSchemaUtil} 与 {@link AvroSchemaUtil}。
 *
 * @param <T> Java 类型
 */
class ParquetReadSupport<T> extends ReadSupport<T> {
  private final Schema expectedSchema;
  private final ReadSupport<T> wrapped;
  private final boolean callInit;
  private final NameMapping nameMapping;

  /**
   * 构造 ParquetReadSupport。
   *
   * @param expectedSchema 期望读取的 Iceberg schema
   * @param readSupport 被包装的底层 ReadSupport
   * @param callInit 是否调用底层 init
   * @param nameMapping 字段名到 ID 的映射（用于无 ID 的旧文件）
   */
  ParquetReadSupport(
      Schema expectedSchema,
      ReadSupport<T> readSupport,
      boolean callInit,
      NameMapping nameMapping) {
    this.expectedSchema = expectedSchema;
    this.wrapped = readSupport;
    this.callInit = callInit;
    this.nameMapping = nameMapping;
  }

  /**
   * 初始化读取上下文，按 expectedSchema 做列投影。
   *
   * <p>逻辑：若文件 schema 有 ID，直接 pruneColumns；若 无 ID 但有 NameMapping， 先 applyNameMapping 再 prune；否则
   * pruneColumnsFallback 按名称匹配。 设置 Avro 兼容选项与 Avro 投影 schema，可选调用底层 init， 返回包含投影 schema 的
   * ReadContext。
   *
   * @param configuration Hadoop 配置
   * @param keyValueMetaData 文件 key-value 元数据
   * @param fileSchema 文件 schema
   * @return 包含投影 schema 的 ReadContext
   */
  @Override
  @SuppressWarnings("deprecation")
  public ReadContext init(
      Configuration configuration, Map<String, String> keyValueMetaData, MessageType fileSchema) {
    // 列选择通过 ReadContext 的 message type 按完整路径匹配文件列，故投影必须使用文件 schema 的路径
    MessageType projection;
    if (ParquetSchemaUtil.hasIds(fileSchema)) {
      projection = ParquetSchemaUtil.pruneColumns(fileSchema, expectedSchema);
    } else if (nameMapping != null) {
      MessageType typeWithIds = ParquetSchemaUtil.applyNameMapping(fileSchema, nameMapping);
      projection = ParquetSchemaUtil.pruneColumns(typeWithIds, expectedSchema);
    } else {
      projection = ParquetSchemaUtil.pruneColumnsFallback(fileSchema, expectedSchema);
    }

    // 覆盖已知向后兼容选项
    configuration.set("parquet.strict.typing", "false");
    configuration.set("parquet.avro.add-list-element-records", "false");
    configuration.set("parquet.avro.write-old-list-structure", "false");

    // 设置 Avro 投影 schema（底层 reader 可能为 Avro）
    AvroReadSupport.setRequestedProjection(
        configuration, AvroSchemaUtil.convert(expectedSchema, projection.getName()));
    org.apache.avro.Schema avroReadSchema =
        AvroSchemaUtil.buildAvroProjection(
            AvroSchemaUtil.convert(ParquetSchemaUtil.convert(projection), projection.getName()),
            expectedSchema,
            ImmutableMap.of());
    AvroReadSupport.setAvroReadSchema(configuration, ParquetAvro.parquetAvroSchema(avroReadSchema));

    // 让底层 ReadSupport 设置元数据，但始终使用正确的投影 schema
    ReadContext context = null;
    if (callInit) {
      try {
        context = wrapped.init(configuration, keyValueMetaData, projection);
      } catch (UnsupportedOperationException e) {
        // 回退到 InitContext 版本
        context =
            wrapped.init(
                new InitContext(configuration, makeMultimap(keyValueMetaData), projection));
      }
    }

    return new ReadContext(
        projection, context != null ? context.getReadSupportMetadata() : ImmutableMap.of());
  }

  /**
   * 准备读取，把投影 schema 交给底层 ReadSupport 构造 RecordMaterializer。
   *
   * <p>逻辑：把 expectedSchema 转为 Parquet schema（保留文件 schema 名称）， 委托底层 prepareForRead 构造
   * RecordMaterializer。
   *
   * @param configuration Hadoop 配置
   * @param fileMetadata 文件元数据
   * @param fileMessageType 文件 schema
   * @param readContext 读取上下文
   * @return RecordMaterializer
   */
  @Override
  public RecordMaterializer<T> prepareForRead(
      Configuration configuration,
      Map<String, String> fileMetadata,
      MessageType fileMessageType,
      ReadContext readContext) {
    // 该 type 在 init 中基于文件 schema 创建，传给底层 ReadSupport 的 schema 需匹配
    // expectedSchema 的名称。此处把 expectedSchema 转为 Parquet 而非重命名文件 schema。
    // 注意：列重排时此方式会失效。
    MessageType readSchema = ParquetSchemaUtil.convert(expectedSchema, fileMessageType.getName());
    return wrapped.prepareForRead(configuration, fileMetadata, readSchema, readContext);
  }

  /**
   * 把 key-value 映射转为 key-&gt;Set&lt;value&gt; 的 multimap，供 InitContext 使用。
   *
   * @param map key-value 映射
   * @return multimap
   */
  private Map<String, Set<String>> makeMultimap(Map<String, String> map) {
    ImmutableMap.Builder<String, Set<String>> builder = ImmutableMap.builder();
    for (Map.Entry<String, String> entry : map.entrySet()) {
      builder.put(entry.getKey(), Sets.newHashSet(entry.getValue()));
    }
    return builder.build();
  }
}
