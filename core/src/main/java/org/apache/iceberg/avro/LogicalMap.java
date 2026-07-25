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
package org.apache.iceberg.avro;

import org.apache.avro.LogicalType;
import org.apache.avro.Schema;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;

/**
 * 文件级说明：Iceberg 自定义 Avro 逻辑类型 "map"，用于标识"以 array 形式存储的 map"。
 *
 * <p>所属模块：iceberg-core。职责：定义一个名为 "map" 的 Avro {@link LogicalType}， 用于在 Avro schema 中标注"逻辑上是
 * map、物理上是 key-value 数组"的字段，并提供 schema 合法性校验。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>Avro 原生 map 不保证 key 顺序、且不支持 null value，Iceberg 需要顺序与可空 value， 因此把 map 物理上存储为 {@code
 *       array<struct<key,value>>}，再用本逻辑类型做"语义标记"。
 *   <li>采用单例（{@link #INSTANCE}）减少对象创建，逻辑类型本身无状态。
 *   <li>{@link #validate(Schema)} 在 schema 构建期校验底层物理结构必须是 array 且元素为 合法的 key-value 二字段
 *       record，避免运行期才发现 schema 错误。
 * </ul>
 *
 * <p>上下游关系：被 {@link TypeToSchema}（Iceberg Type → Avro Schema 转换器）在生成 map 类型的 Avro schema 时附加；被
 * {@link SchemaToType} 反向识别以还原为 Iceberg {@link org.apache.iceberg.types.Types.MapType}。
 */
public class LogicalMap extends LogicalType {
  static final String NAME = "map";
  private static final LogicalMap INSTANCE = new LogicalMap();

  /**
   * 获取本逻辑类型的全局唯一实例。
   *
   * <p>设计要点：单例模式，逻辑类型无状态可安全共享。
   *
   * @return LogicalMap 单例
   */
  static LogicalMap get() {
    return INSTANCE;
  }

  private LogicalMap() {
    super(NAME);
  }

  /**
   * 校验承载本逻辑类型的 Avro schema 是否符合 Iceberg 的 map 物理布局约定。
   *
   * <p>设计要点：先调用父类校验，再断言 schema 类型为 ARRAY，且元素是合法的 key-value 二字段 record（通过 {@link
   * AvroSchemaUtil#isKeyValueSchema(Schema)} 判定）。
   *
   * @param schema 待校验的 Avro schema
   */
  @Override
  public void validate(Schema schema) {
    super.validate(schema);
    Preconditions.checkArgument(
        schema.getType() == Schema.Type.ARRAY,
        "Invalid type for map, must be an array: %s",
        schema);
    Preconditions.checkArgument(
        AvroSchemaUtil.isKeyValueSchema(schema.getElementType()),
        "Invalid key-value record: %s",
        schema.getElementType());
  }
}
