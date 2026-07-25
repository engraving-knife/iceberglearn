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
import java.util.function.Supplier;
import org.apache.avro.Schema;
import org.apache.iceberg.relocated.com.google.common.collect.Iterables;

/**
 * 检查 Avro schema 中是否存在缺失 id 的节点，发现第一个缺 id 的节点即返回 true。是 {@link HasIds} 的反向检查。
 *
 * <p>所属模块：iceberg-core（avro 包，Avro schema 字段 id 完整性探测工具）。
 *
 * <p>职责：遍历 Avro schema，判断是否存在任何缺失 field id 的节点（字段、map key/value、 list 元素），用于判断 schema 是否为“全部带
 * id”或“全部不带 id”的一致状态。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>调用 {@link AvroSchemaUtil#toIceberg(Schema)} 将 Avro schema 转为 Iceberg schema 时， 要求 Avro
 *       schema 要么所有节点都带 id，要么都不带 id。仅调用 {@link AvroSchemaUtil#hasIds(Schema)} 只能证明“至少有一个
 *       id”，并非充分条件。
 *   <li>本类与 {@link HasIds} 配合：hasIds 为 true 且 missingIds 为 false，才说明所有节点都带 id， 可安全转换为 Iceberg
 *       schema。
 *   <li>原始类型节点恒返回 false，因为 Iceberg 本就不为原始类型节点分配 id。
 * </ul>
 *
 * <p>上下游关系：被 schema 转换逻辑调用，用于在调用 {@link AvroSchemaUtil#toIceberg(Schema)} 前校验 id 一致性。
 */
class MissingIds extends AvroCustomOrderSchemaVisitor<Boolean, Boolean> {
  /**
   * 判断 record 是否缺 id：任一字段子树缺 id 即为 true。
   *
   * @param record Avro record schema
   * @param names 字段名列表
   * @param fields 各字段子树求值结果
   * @return 是否缺 id
   */
  @Override
  public Boolean record(Schema record, List<String> names, Iterable<Boolean> fields) {
    return Iterables.any(fields, Boolean.TRUE::equals);
  }

  /**
   * 判断字段是否缺 id：本字段无 id 或其子树缺 id 即为 true。
   *
   * @param field Avro 字段
   * @param fieldResult 字段子树求值结果供应器
   * @return 是否缺 id
   */
  @Override
  public Boolean field(Schema.Field field, Supplier<Boolean> fieldResult) {
    // either this field is missing ID, or the subtree is missing ID somewhere
    return !AvroSchemaUtil.hasFieldId(field) || fieldResult.get();
  }

  /**
   * 判断 map 是否缺 id：map 缺 key/value id 或其值子树缺 id 即为 true。
   *
   * @param map Avro map schema
   * @param value 值子树求值结果供应器
   * @return 是否缺 id
   */
  @Override
  public Boolean map(Schema map, Supplier<Boolean> value) {
    // either this map node is missing (key/value) ID, or the subtree is missing ID somewhere
    return !AvroSchemaUtil.hasProperty(map, AvroSchemaUtil.KEY_ID_PROP)
        || !AvroSchemaUtil.hasProperty(map, AvroSchemaUtil.VALUE_ID_PROP)
        || value.get();
  }

  /**
   * 判断 array 是否缺 id：array 缺元素 id 或其元素子树缺 id 即为 true。
   *
   * @param array Avro array schema
   * @param element 元素子树求值结果供应器
   * @return 是否缺 id
   */
  @Override
  public Boolean array(Schema array, Supplier<Boolean> element) {
    // either this list node is missing (elem) ID, or the subtree is missing ID somewhere
    return !AvroSchemaUtil.hasProperty(array, AvroSchemaUtil.ELEMENT_ID_PROP) || element.get();
  }

  /**
   * 判断 union 是否缺 id：任一分支缺 id 即为 true。
   *
   * @param union Avro union schema
   * @param options 各分支求值结果
   * @return 是否缺 id
   */
  @Override
  public Boolean union(Schema union, Iterable<Boolean> options) {
    return Iterables.any(options, Boolean.TRUE::equals);
  }

  /**
   * 原始类型节点不分配 id，故恒返回 false（不存在“缺 id”一说）。
   *
   * @param primitive Avro 原始类型 schema
   * @return 固定返回 false
   */
  @Override
  public Boolean primitive(Schema primitive) {
    // primitive node cannot be missing ID as Iceberg do not assign primitive node IDs in the first
    // place
    return false;
  }
}
