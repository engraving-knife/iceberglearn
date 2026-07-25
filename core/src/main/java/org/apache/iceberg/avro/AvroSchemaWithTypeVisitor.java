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
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;

/**
 * 带 Iceberg Type 伙伴的 Avro Schema 访问器：遍历 Avro schema 时同步对照 Iceberg {@link Type}，按结构位置一一对应地访问节点。
 *
 * <p>所属模块：iceberg-core（avro 包，Avro 与 Iceberg type 联合遍历的基础工具）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>在遍历 Avro schema 的同时携带对应的 Iceberg {@link Type}，使 visitor 能同时 获取两边的类型信息（典型用途：构造
 *       ValueReader/Writer 时需要两种类型）。
 *   <li>通过字段 id 把 Avro 字段与 Iceberg {@link Types.NestedField} 对齐，处理字段重命名 与 schema 演进。
 *   <li>对 {@link LogicalMap}（array&lt;key,value&gt; 表示的 map）做特殊处理，区分 真正的 list 与 map-as-array 两种情况。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>与 {@link AvroSchemaVisitor} 的差异在于“携带伙伴类型”：本类保证 Avro schema 与 Iceberg type 结构完全匹配（两者均派生自同一
 *       Iceberg schema），因此可按位置对齐， 无需依赖名称匹配。
 *   <li>union 分支中的 NULL 节点对应 Iceberg 的 null 类型，访问时传 null 作为伙伴类型。
 * </ul>
 *
 * <p>上下游关系：被 {@link GenericAvroReader}、{@link GenericAvroWriter} 等用于在 Avro schema 与 Iceberg type
 * 之间建立字段映射；上游由读写器构造时触发。
 *
 * @param <T> 访问结果类型
 */
public abstract class AvroSchemaWithTypeVisitor<T> {
  /**
   * 从 Iceberg schema 与 Avro schema 联合开始遍历。
   *
   * <p>实现：把 Iceberg schema 转为 {@link Types.StructType} 后委托给 {@link #visit(Type, Schema,
   * AvroSchemaWithTypeVisitor)}。
   *
   * @param iSchema Iceberg schema
   * @param schema Avro schema
   * @param visitor 访问器
   * @param <T> 结果类型
   * @return 访问结果
   */
  public static <T> T visit(
      org.apache.iceberg.Schema iSchema, Schema schema, AvroSchemaWithTypeVisitor<T> visitor) {
    return visit(iSchema.asStruct(), schema, visitor);
  }

  /**
   * 以 Iceberg type 作为伙伴类型遍历 Avro schema，按 Avro 节点类型分派。
   *
   * <p>逻辑：
   *
   * <ul>
   *   <li>RECORD：委托 {@link #visitRecord}（按字段 id 对齐 Avro 字段与 Iceberg 字段）。
   *   <li>UNION：委托 {@link #visitUnion}。
   *   <li>ARRAY：委托 {@link #visitArray}（区分 LogicalMap 与普通 list）。
   *   <li>MAP：把 Iceberg {@link Types.MapType} 的 valueType 作为伙伴递归值类型。
   *   <li>其他：调用 {@link #primitive(Type.PrimitiveType, Schema)}。
   * </ul>
   *
   * @param iType Iceberg 伙伴类型，可为 null（对应 Avro union 的 NULL 分支）
   * @param schema Avro schema
   * @param visitor 访问器
   * @param <T> 结果类型
   * @return 访问结果
   */
  public static <T> T visit(Type iType, Schema schema, AvroSchemaWithTypeVisitor<T> visitor) {
    switch (schema.getType()) {
      case RECORD:
        return visitRecord(iType != null ? iType.asStructType() : null, schema, visitor);

      case UNION:
        return visitUnion(iType, schema, visitor);

      case ARRAY:
        return visitArray(iType, schema, visitor);

      case MAP:
        Types.MapType map = iType != null ? iType.asMapType() : null;
        return visitor.map(
            map,
            schema,
            visit(map != null ? map.valueType() : null, schema.getValueType(), visitor));

      default:
        return visitor.primitive(iType != null ? iType.asPrimitiveType() : null, schema);
    }
  }

  /**
   * 访问 record 节点，按字段 id 把 Avro 字段与 Iceberg {@link Types.NestedField} 对齐后递归。
   *
   * <p>逻辑：先将 record 全名压入 {@link #recordLevels} 以检测递归 record（Iceberg 不支持递归 类型，故遇到即报错）；再遍历 Avro 字段，通过
   * {@link AvroSchemaUtil#getFieldId} 取字段 id， 从 Iceberg struct 中查找同 id 字段作为伙伴类型递归访问；最后弹出记录名并回调
   * visitor。
   *
   * @param struct Iceberg struct 类型，可为 null
   * @param record Avro record schema
   * @param visitor 访问器
   * @param <T> 结果类型
   * @return record 访问结果
   */
  private static <T> T visitRecord(
      Types.StructType struct, Schema record, AvroSchemaWithTypeVisitor<T> visitor) {
    // check to make sure this hasn't been visited before
    String name = record.getFullName();
    Preconditions.checkState(
        !visitor.recordLevels.contains(name), "Cannot process recursive Avro record %s", name);

    visitor.recordLevels.push(name);

    List<Schema.Field> fields = record.getFields();
    List<String> names = Lists.newArrayListWithExpectedSize(fields.size());
    List<T> results = Lists.newArrayListWithExpectedSize(fields.size());
    for (Schema.Field field : fields) {
      int fieldId = AvroSchemaUtil.getFieldId(field);
      Types.NestedField iField = struct != null ? struct.field(fieldId) : null;
      names.add(field.name());
      results.add(visit(iField != null ? iField.type() : null, field.schema(), visitor));
    }

    visitor.recordLevels.pop();

    return visitor.record(struct, record, names, results);
  }

  /**
   * 访问 union 节点，对每个分支递归访问。
   *
   * <p>逻辑：遍历 union 各分支，NULL 分支传 null 作为伙伴类型，其余分支沿用当前 Iceberg 类型 作为伙伴；最后回调 visitor 的 union 方法。
   *
   * @param type Iceberg 伙伴类型
   * @param union Avro union schema
   * @param visitor 访问器
   * @param <T> 结果类型
   * @return union 访问结果
   */
  private static <T> T visitUnion(Type type, Schema union, AvroSchemaWithTypeVisitor<T> visitor) {
    List<Schema> types = union.getTypes();
    List<T> options = Lists.newArrayListWithExpectedSize(types.size());
    for (Schema branch : types) {
      if (branch.getType() == Schema.Type.NULL) {
        options.add(visit((Type) null, branch, visitor));
      } else {
        options.add(visit(type, branch, visitor));
      }
    }
    return visitor.union(type, union, options);
  }

  /**
   * 访问 array 节点，区分 {@link LogicalMap}（array 表示的 map）与普通 list 两种情况。
   *
   * <p>逻辑：若 Avro array 带有 LogicalMap 逻辑类型或伙伴类型本身是 map，则按 key-value 对访问 （要求元素 schema 为 key/value 二字段
   * record）；否则按普通 list 访问元素类型。
   *
   * @param type Iceberg 伙伴类型
   * @param array Avro array schema
   * @param visitor 访问器
   * @param <T> 结果类型
   * @return array 或 map 访问结果
   */
  private static <T> T visitArray(Type type, Schema array, AvroSchemaWithTypeVisitor<T> visitor) {
    if (array.getLogicalType() instanceof LogicalMap || (type != null && type.isMapType())) {
      Preconditions.checkState(
          AvroSchemaUtil.isKeyValueSchema(array.getElementType()),
          "Cannot visit invalid logical map type: %s",
          array);
      Types.MapType map = type != null ? type.asMapType() : null;
      List<Schema.Field> keyValueFields = array.getElementType().getFields();
      return visitor.map(
          map,
          array,
          visit(map != null ? map.keyType() : null, keyValueFields.get(0).schema(), visitor),
          visit(map != null ? map.valueType() : null, keyValueFields.get(1).schema(), visitor));

    } else {
      Types.ListType list = type != null ? type.asListType() : null;
      return visitor.array(
          list,
          array,
          visit(list != null ? list.elementType() : null, array.getElementType(), visitor));
    }
  }

  /** 正在访问的 record 全名栈，用于检测并禁止递归 Avro record。 */
  private Deque<String> recordLevels = Lists.newLinkedList();

  /**
   * 访问 record 节点（带 Iceberg {@link Types.StructType} 伙伴），默认返回 null。
   *
   * @param iStruct Iceberg struct 类型
   * @param record Avro record schema
   * @param names 字段名列表
   * @param fields 各字段访问结果
   * @return 访问结果
   */
  public T record(Types.StructType iStruct, Schema record, List<String> names, List<T> fields) {
    return null;
  }

  /**
   * 访问 union 节点（带 Iceberg {@link Type} 伙伴），默认返回 null。
   *
   * @param iType Iceberg 类型
   * @param union Avro union schema
   * @param options 各分支访问结果
   * @return 访问结果
   */
  public T union(Type iType, Schema union, List<T> options) {
    return null;
  }

  /**
   * 访问 array 节点（带 Iceberg {@link Types.ListType} 伙伴），默认返回 null。
   *
   * @param iList Iceberg list 类型
   * @param array Avro array schema
   * @param element 元素类型访问结果
   * @return 访问结果
   */
  public T array(Types.ListType iList, Schema array, T element) {
    return null;
  }

  /**
   * 访问 map 节点（带 Iceberg {@link Types.MapType} 伙伴，含 key 与 value），默认返回 null。
   *
   * @param iMap Iceberg map 类型
   * @param map Avro map schema
   * @param key 键类型访问结果
   * @param value 值类型访问结果
   * @return 访问结果
   */
  public T map(Types.MapType iMap, Schema map, T key, T value) {
    return null;
  }

  /**
   * 访问 map 节点（带 Iceberg {@link Types.MapType} 伙伴，仅 value），默认返回 null。
   *
   * @param iMap Iceberg map 类型
   * @param map Avro map schema
   * @param value 值类型访问结果
   * @return 访问结果
   */
  public T map(Types.MapType iMap, Schema map, T value) {
    return null;
  }

  /**
   * 访问原始类型节点（带 Iceberg {@link Type.PrimitiveType} 伙伴），默认返回 null。
   *
   * @param iPrimitive Iceberg 原始类型
   * @param primitive Avro 原始类型 schema
   * @return 访问结果
   */
  public T primitive(Type.PrimitiveType iPrimitive, Schema primitive) {
    return null;
  }
}
