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
 * 懒求值检查 Avro schema 中是否设置了任意字段 id，发现第一个带 id 的字段即返回 true。
 *
 * <p>所属模块：iceberg-core（avro 包，Avro schema 字段 id 探测工具）。
 *
 * <p>职责：遍历 Avro schema，判断是否存在任何带 field id 的节点（字段、map key/value、list 元素）， 用于决定该 Avro schema 是否可视为带
 * Iceberg id 的 schema。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>与 {@link MissingIds} 互为反向：本类判断“是否有 id”，后者判断“是否缺 id”。
 *   <li>注意：返回 true 仅证明 schema 至少有一个 id，并非所有节点都有 id 的充分条件， 因此不能仅凭此判断可安全调用 {@link
 *       AvroSchemaUtil#toIceberg(Schema)}。
 *   <li>继承 {@link AvroCustomOrderSchemaVisitor}，利用惰性供应器避免不必要的子树求值。
 * </ul>
 *
 * <p>上下游关系：被 {@link AvroSchemaUtil#hasIds(Schema)} 调用，用于读写器判断 schema 来源。
 */
class HasIds extends AvroCustomOrderSchemaVisitor<Boolean, Boolean> {
  /**
   * 判断 record 是否含 id：任一字段子树含 id 即为 true。
   *
   * @param record Avro record schema
   * @param names 字段名列表
   * @param fields 各字段子树求值结果
   * @return 是否含 id
   */
  @Override
  public Boolean record(Schema record, List<String> names, Iterable<Boolean> fields) {
    return Iterables.any(fields, Boolean.TRUE::equals);
  }

  /**
   * 判断字段是否含 id：本字段有 id 或其子树含 id 即为 true。
   *
   * @param field Avro 字段
   * @param fieldResult 字段子树求值结果供应器
   * @return 是否含 id
   */
  @Override
  public Boolean field(Schema.Field field, Supplier<Boolean> fieldResult) {
    // see if field id is present, if not, try to find it in the sub tree
    return AvroSchemaUtil.hasFieldId(field) || fieldResult.get();
  }

  /**
   * 判断 map 是否含 id：map 带 key/value id 或其值子树含 id 即为 true。
   *
   * @param map Avro map schema
   * @param value 值子树求值结果供应器
   * @return 是否含 id
   */
  @Override
  public Boolean map(Schema map, Supplier<Boolean> value) {
    return AvroSchemaUtil.hasProperty(map, AvroSchemaUtil.KEY_ID_PROP)
        || AvroSchemaUtil.hasProperty(map, AvroSchemaUtil.VALUE_ID_PROP)
        || value.get();
  }

  /**
   * 判断 array 是否含 id：array 带元素 id 或其元素子树含 id 即为 true。
   *
   * @param array Avro array schema
   * @param element 元素子树求值结果供应器
   * @return 是否含 id
   */
  @Override
  public Boolean array(Schema array, Supplier<Boolean> element) {
    return AvroSchemaUtil.hasProperty(array, AvroSchemaUtil.ELEMENT_ID_PROP) || element.get();
  }

  /**
   * 判断 union 是否含 id：任一分支含 id 即为 true。
   *
   * @param union Avro union schema
   * @param options 各分支求值结果
   * @return 是否含 id
   */
  @Override
  public Boolean union(Schema union, Iterable<Boolean> options) {
    return Iterables.any(options, Boolean.TRUE::equals);
  }

  /**
   * 原始类型节点不带 id，恒返回 false。
   *
   * @param primitive Avro 原始类型 schema
   * @return 固定返回 false
   */
  @Override
  public Boolean primitive(Schema primitive) {
    return false;
  }
}
