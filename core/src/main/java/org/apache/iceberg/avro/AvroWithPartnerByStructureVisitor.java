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

import java.util.Deque;
import java.util.List;
import org.apache.avro.Schema;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.util.Pair;

/**
 * 带伙伴类型（partner type）的 Avro schema 访问器，按结构位置一一对应地访问节点。
 *
 * <p>所属模块：iceberg-core（avro 包，Avro 与外部类型系统联合遍历的基础工具）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>在遍历 Avro schema 的同时携带伙伴类型 P，使 visitor 能同时获取两边类型信息 （典型用途：构造 ValueReader/Writer 时需要 Iceberg 与
 *       Avro 两侧类型）。
 *   <li>依赖结构完全匹配：Avro schema 与伙伴类型均派生自同一 Iceberg schema，因此可按位置 （而非名称/id）对齐字段，并通过 {@link
 *       #fieldNameAndType} 校验字段名兼容性。
 *   <li>对 {@link LogicalMap}（array&lt;key,value&gt; 表示的 map）做特殊处理，区分真正的 list 与 map-as-array 两种情况。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>与 {@link AvroSchemaWithTypeVisitor} 的差异在于伙伴类型 P 是泛型抽象，由子类决定 （如 Iceberg {@link
 *       org.apache.iceberg.types.Type} 或其他类型系统），增强复用性。
 *   <li>union 仅支持 option schema（null + 单一非空分支），非 option union 直接报错。
 *   <li>通过 recordLevels 栈禁止递归 Avro record（Iceberg 不支持递归类型）。
 * </ul>
 *
 * <p>上下游关系：被 {@link GenericAvroReader}、{@link GenericAvroWriter} 等用于在 Avro schema
 * 与伙伴类型之间建立字段映射；上游由读写器构造时触发。
 *
 * @param <P> 伙伴类型
 * @param <T> 访问结果类型
 */
public abstract class AvroWithPartnerByStructureVisitor<P, T> {

  /**
   * 以伙伴类型 P 作为对照遍历 Avro schema，按 Avro 节点类型分派。
   *
   * <p>逻辑：
   *
   * <ul>
   *   <li>RECORD：委托 {@code visitRecord}（按位置对齐 Avro 字段与伙伴字段）。
   *   <li>UNION：委托 {@code visitUnion}。
   *   <li>ARRAY：委托 {@code visitArray}（区分 LogicalMap 与普通 list）。
   *   <li>MAP：要求 key 为 string 类型，把 value 类型作为伙伴递归值类型。
   *   <li>其他：调用 {@link #primitive(Object, Schema)}。
   * </ul>
   *
   * @param partner 伙伴类型
   * @param schema Avro schema
   * @param visitor 访问器
   * @param <P> 伙伴类型
   * @param <T> 结果类型
   * @return 访问结果
   */
  public static <P, T> T visit(
      P partner, Schema schema, AvroWithPartnerByStructureVisitor<P, T> visitor) {
    switch (schema.getType()) {
      case RECORD:
        return visitRecord(partner, schema, visitor);

      case UNION:
        return visitUnion(partner, schema, visitor);

      case ARRAY:
        return visitArray(partner, schema, visitor);

      case MAP:
        P keyType = visitor.mapKeyType(partner);
        Preconditions.checkArgument(
            visitor.isStringType(keyType), "Invalid map: %s is not a string", keyType);
        return visitor.map(
            partner, schema, visit(visitor.mapValueType(partner), schema.getValueType(), visitor));

      default:
        return visitor.primitive(partner, schema);
    }
  }

  // ---------------------------------- Static helpers ---------------------------------------------

  /**
   * 访问 record 节点，按位置对齐 Avro 字段与伙伴字段后递归。
   *
   * <p>逻辑：先将 record 全名压入 {@code recordLevels} 以检测递归 record；再按位置遍历 Avro 字段， 通过 {@link
   * #fieldNameAndType} 取伙伴字段名与类型，并用 {@link AvroSchemaUtil#makeCompatibleName} 校验字段名兼容性；最后弹出记录名并回调
   * visitor。
   *
   * @param struct 伙伴 struct 类型
   * @param record Avro record schema
   * @param visitor 访问器
   * @param <P> 伙伴类型
   * @param <T> 结果类型
   * @return record 访问结果
   */
  private static <P, T> T visitRecord(
      P struct, Schema record, AvroWithPartnerByStructureVisitor<P, T> visitor) {
    // check to make sure this hasn't been visited before
    String name = record.getFullName();
    Preconditions.checkState(
        !visitor.recordLevels.contains(name), "Cannot process recursive Avro record %s", name);
    List<Schema.Field> fields = record.getFields();

    visitor.recordLevels.push(name);

    List<String> names = Lists.newArrayListWithExpectedSize(fields.size());
    List<T> results = Lists.newArrayListWithExpectedSize(fields.size());
    for (int i = 0; i < fields.size(); i += 1) {
      Pair<String, P> nameAndType = visitor.fieldNameAndType(struct, i);
      String fieldName = nameAndType.first();
      Schema.Field field = fields.get(i);
      Preconditions.checkArgument(
          AvroSchemaUtil.makeCompatibleName(fieldName).equals(field.name()),
          "Structs do not match: field %s != %s",
          fieldName,
          field.name());
      results.add(visit(nameAndType.second(), field.schema(), visitor));
      names.add(fieldName);
    }

    visitor.recordLevels.pop();

    return visitor.record(struct, record, names, results);
  }

  /**
   * 访问 union 节点，要求为 option schema（null + 单一非空分支），对各分支递归访问。
   *
   * <p>逻辑：校验 union 为 option schema；遍历各分支，NULL 分支传 {@link #nullType()} 作为伙伴， 其余分支沿用当前伙伴类型；最后回调
   * visitor 的 union 方法。
   *
   * @param type 伙伴类型
   * @param union Avro union schema
   * @param visitor 访问器
   * @param <P> 伙伴类型
   * @param <T> 结果类型
   * @return union 访问结果
   */
  private static <P, T> T visitUnion(
      P type, Schema union, AvroWithPartnerByStructureVisitor<P, T> visitor) {
    List<Schema> types = union.getTypes();
    Preconditions.checkArgument(
        AvroSchemaUtil.isOptionSchema(union), "Cannot visit non-option union: %s", union);
    List<T> options = Lists.newArrayListWithExpectedSize(types.size());
    for (Schema branch : types) {
      if (branch.getType() == Schema.Type.NULL) {
        options.add(visit(visitor.nullType(), branch, visitor));
      } else {
        options.add(visit(type, branch, visitor));
      }
    }
    return visitor.union(type, union, options);
  }

  /**
   * 访问 array 节点，区分 {@link LogicalMap}（array 表示的 map）与普通 list 两种情况。
   *
   * <p>逻辑：若 Avro array 带 LogicalMap 逻辑类型或伙伴类型本身是 map，则按 key-value 对访问 （要求元素 schema 为 key/value 二字段
   * record）；否则按普通 list 访问元素类型。
   *
   * @param type 伙伴类型
   * @param array Avro array schema
   * @param visitor 访问器
   * @param <P> 伙伴类型
   * @param <T> 结果类型
   * @return array 或 map 访问结果
   */
  private static <P, T> T visitArray(
      P type, Schema array, AvroWithPartnerByStructureVisitor<P, T> visitor) {
    if (array.getLogicalType() instanceof LogicalMap || visitor.isMapType(type)) {
      Preconditions.checkState(
          AvroSchemaUtil.isKeyValueSchema(array.getElementType()),
          "Cannot visit invalid logical map type: %s",
          array);
      List<Schema.Field> keyValueFields = array.getElementType().getFields();
      return visitor.map(
          type,
          array,
          visit(visitor.mapKeyType(type), keyValueFields.get(0).schema(), visitor),
          visit(visitor.mapValueType(type), keyValueFields.get(1).schema(), visitor));

    } else {
      return visitor.array(
          type, array, visit(visitor.arrayElementType(type), array.getElementType(), visitor));
    }
  }

  /** 正在访问的 record 全名栈，仅用于检测并禁止递归 Avro record。 */
  private Deque<String> recordLevels = Lists.newLinkedList();

  // ---------------------------------- Partner type methods
  // ---------------------------------------------

  /** 判断伙伴类型是否为 map 类型。 */
  protected abstract boolean isMapType(P type);

  /** 判断伙伴类型是否为 string 类型（map 的 key 必须为 string）。 */
  protected abstract boolean isStringType(P type);

  /** 从伙伴 list 类型中取出元素类型。 */
  protected abstract P arrayElementType(P arrayType);

  /** 从伙伴 map 类型中取出键类型。 */
  protected abstract P mapKeyType(P mapType);

  /** 从伙伴 map 类型中取出值类型。 */
  protected abstract P mapValueType(P mapType);

  /** 按位置从伙伴 struct 中取出字段名与字段类型。 */
  protected abstract Pair<String, P> fieldNameAndType(P structType, int pos);

  /** 返回伙伴类型系统中表示 null 的类型（对应 Avro union 的 NULL 分支）。 */
  protected abstract P nullType();

  // ---------------------------------- Type visitors ---------------------------------------------

  /**
   * 访问 record 节点（带伙伴 struct），默认返回 null。
   *
   * @param struct 伙伴 struct 类型
   * @param record Avro record schema
   * @param names 字段名列表
   * @param fields 各字段访问结果
   * @return 访问结果
   */
  public T record(P struct, Schema record, List<String> names, List<T> fields) {
    return null;
  }

  /**
   * 访问 union 节点（带伙伴类型），默认返回 null。
   *
   * @param type 伙伴类型
   * @param union Avro union schema
   * @param options 各分支访问结果
   * @return 访问结果
   */
  public T union(P type, Schema union, List<T> options) {
    return null;
  }

  /**
   * 访问 array 节点（带伙伴 list，仅元素），默认返回 null。
   *
   * @param sArray 伙伴 list 类型
   * @param array Avro array schema
   * @param element 元素类型访问结果
   * @return 访问结果
   */
  public T array(P sArray, Schema array, T element) {
    return null;
  }

  /**
   * 访问 map 节点（带伙伴 map，含 key 与 value），默认返回 null。
   *
   * @param sMap 伙伴 map 类型
   * @param map Avro map schema
   * @param key 键类型访问结果
   * @param value 值类型访问结果
   * @return 访问结果
   */
  public T map(P sMap, Schema map, T key, T value) {
    return null;
  }

  /**
   * 访问 map 节点（带伙伴 map，仅 value），默认返回 null。
   *
   * @param sMap 伙伴 map 类型
   * @param map Avro map schema
   * @param value 值类型访问结果
   * @return 访问结果
   */
  public T map(P sMap, Schema map, T value) {
    return null;
  }

  /**
   * 访问原始类型节点（带伙伴类型），默认返回 null。
   *
   * @param type 伙伴类型
   * @param primitive Avro 原始类型 schema
   * @return 访问结果
   */
  public T primitive(P type, Schema primitive) {
    return null;
  }
}
