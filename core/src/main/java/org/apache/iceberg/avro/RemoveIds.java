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

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.avro.Schema;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;

/**
 * 文件级说明：Avro Schema 访问器，用于"剥离" schema 上 Iceberg 私有的字段/元素 ID 属性， 生成一份"干净"的、与标准 Avro 兼容的 schema（不含
 * field-id 等扩展属性）。
 *
 * <p>所属模块：iceberg-core。职责：实现 {@link AvroSchemaVisitor}，遍历 Avro schema 并重建一份不带 Iceberg ID
 * 属性（FIELD_ID_PROP / KEY_ID_PROP / VALUE_ID_PROP / ELEMENT_ID_PROP）的新 schema，其他自定义属性保留。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>Iceberg 通过在 Avro schema 上挂自定义属性来保留字段 ID，以支持 schema 演化（重命名等）。 但部分下游工具（如某些旧版 Avro 库、Hive
 *       等）不识别这些属性，甚至报错，因此需要在 写出 schema 给这些工具前先剥离 ID。
 *   <li>采用 visitor 模式递归遍历所有节点（record/map/array/union/primitive），保证不遗漏。
 *   <li>重建 schema 时尽量保留原 schema 的其他属性和文档，仅去除 ID 类属性。
 * </ul>
 *
 * <p>上下游关系：被 Iceberg Avro 写出栈在需要"无 ID schema"时调用（例如生成与外部系统 互通的 schema 文件）；输入可以是 Iceberg {@link
 * org.apache.iceberg.Schema} 或裸 Avro {@link Schema}。
 */
public class RemoveIds extends AvroSchemaVisitor<Schema> {
  /**
   * 处理 record 节点：重建字段列表，去除每个字段的 FIELD_ID_PROP 属性。
   *
   * <p>设计要点：通过 {@link #copyField(Schema.Field, Schema)} 复制每个字段并剥离 ID， 然后用 {@link
   * AvroSchemaUtil#copyRecord} 构造新的 record schema（不带 record 级 ID）。
   *
   * @param record 原 record schema
   * @param names 字段名列表（visitor 框架提供）
   * @param types 子节点 visitor 结果（已去 ID 的新字段类型）
   * @return 去 ID 后的新 record schema
   */
  @Override
  public Schema record(Schema record, List<String> names, List<Schema> types) {
    List<Schema.Field> fields = record.getFields();
    int length = fields.size();
    List<Schema.Field> newFields = Lists.newArrayListWithExpectedSize(length);
    // 逐字段复制并去除 FIELD_ID_PROP，保留其他属性
    for (int i = 0; i < length; i += 1) {
      newFields.add(copyField(fields.get(i), types.get(i)));
    }
    return AvroSchemaUtil.copyRecord(record, newFields, null);
  }

  /**
   * 处理 map 节点：重建 map schema，去除 KEY_ID_PROP 与 VALUE_ID_PROP。
   *
   * <p>设计要点：先创建只含值类型的新 map，再把原 map 上除两个 ID 属性外的其他自定义属性 逐个拷贝回来。
   *
   * @param map 原 map schema
   * @param valueType 子节点 visitor 结果（已去 ID 的值类型）
   * @return 去 ID 后的新 map schema
   */
  @Override
  public Schema map(Schema map, Schema valueType) {
    Schema result = Schema.createMap(valueType);
    // 拷贝原 map 的自定义属性，但跳过 Iceberg 的 KEY_ID_PROP / VALUE_ID_PROP
    for (Map.Entry<String, Object> prop : map.getObjectProps().entrySet()) {
      String key = prop.getKey();
      if (!key.equals(AvroSchemaUtil.KEY_ID_PROP) && !key.equals(AvroSchemaUtil.VALUE_ID_PROP)) {
        result.addProp(key, prop.getValue());
      }
    }
    return result;
  }

  /**
   * 处理 array 节点：重建 array schema，去除 ELEMENT_ID_PROP。
   *
   * <p>设计要点：先创建只含元素类型的新 array，再拷贝原 array 上除 ELEMENT_ID_PROP 外的 其他自定义属性。
   *
   * @param array 原 array schema
   * @param element 子节点 visitor 结果（已去 ID 的元素类型）
   * @return 去 ID 后的新 array schema
   */
  @Override
  public Schema array(Schema array, Schema element) {
    Schema result = Schema.createArray(element);
    // 拷贝原 array 的自定义属性，但跳过 Iceberg 的 ELEMENT_ID_PROP
    for (Map.Entry<String, Object> prop : array.getObjectProps().entrySet()) {
      String key = prop.getKey();
      if (!key.equals(AvroSchemaUtil.ELEMENT_ID_PROP)) {
        result.addProp(key, prop.getValue());
      }
    }
    return result;
  }

  /**
   * 处理 primitive 节点：直接以原类型创建一个不带任何属性的全新 primitive schema。
   *
   * <p>设计要点：primitive 节点上不会挂 Iceberg ID，但仍重建一次以剥离可能存在的 logicalType 之外的其他属性，保持输出"纯净"。
   *
   * @param primitive 原 primitive schema
   * @return 新建的 primitive schema
   */
  @Override
  public Schema primitive(Schema primitive) {
    return Schema.create(primitive.getType());
  }

  /**
   * 处理 union 节点：基于子选项重建 union。
   *
   * <p>设计要点：union 节点本身不挂 Iceberg ID，直接用子 visitor 返回的选项列表重建 union。
   *
   * @param union 原 union schema
   * @param options 子节点 visitor 结果列表
   * @return 重建后的 union schema
   */
  @Override
  public Schema union(Schema union, List<Schema> options) {
    return Schema.createUnion(options);
  }

  /**
   * 复制单个 Avro 字段，并去除其 FIELD_ID_PROP 属性。
   *
   * <p>设计要点：用 Avro 公开的 5 参构造器重建字段（保留 name/schema/doc/default/order）， 再把原字段除 FIELD_ID_PROP
   * 外的自定义属性逐个拷贝回来。
   *
   * @param field 原字段
   * @param newSchema 已去 ID 的字段类型 schema
   * @return 去 ID 后的新字段对象
   */
  private static Schema.Field copyField(Schema.Field field, Schema newSchema) {
    Schema.Field copy =
        new Schema.Field(field.name(), newSchema, field.doc(), field.defaultVal(), field.order());
    // 拷贝字段的自定义属性，但跳过 FIELD_ID_PROP
    for (Map.Entry<String, Object> prop : field.getObjectProps().entrySet()) {
      String key = prop.getKey();
      if (!Objects.equals(key, AvroSchemaUtil.FIELD_ID_PROP)) {
        copy.addProp(key, prop.getValue());
      }
    }
    return copy;
  }

  /**
   * 入口方法：从 Iceberg {@link org.apache.iceberg.Schema} 出发生成去 ID 的 Avro schema。
   *
   * <p>设计要点：先把 Iceberg Schema 转为 Avro schema（asStruct + convert），再走 visitor。
   *
   * @param schema Iceberg schema
   * @return 不含 Iceberg ID 属性的 Avro schema
   */
  static Schema removeIds(org.apache.iceberg.Schema schema) {
    return AvroSchemaVisitor.visit(
        AvroSchemaUtil.convert(schema.asStruct(), "table"), new RemoveIds());
  }

  /**
   * 入口方法：直接对已存在的 Avro {@link Schema} 做 ID 剥离。
   *
   * <p>设计要点：以传入 schema 为根，构造 visitor 并递归访问。
   *
   * @param schema 原始 Avro schema
   * @return 不含 Iceberg ID 属性的新 Avro schema
   */
  public static Schema removeIds(Schema schema) {
    return AvroSchemaVisitor.visit(schema, new RemoveIds());
  }
}
