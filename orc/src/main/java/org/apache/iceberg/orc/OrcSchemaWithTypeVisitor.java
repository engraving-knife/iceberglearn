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
package org.apache.iceberg.orc;

import java.util.List;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;
import org.apache.orc.TypeDescription;

/**
 * 同时遍历 Iceberg Type 与 ORC TypeDescription 的配对访问器基类。
 *
 * <p>所属模块：iceberg-orc。与 {@link OrcSchemaVisitor} 不同，本类在遍历时同时携带 Iceberg 类型与 ORC 类型，使子类可同时访问两套类型信息。
 *
 * <p>职责：按 ORC category 遍历，同时把对应的 Iceberg 类型（StructType/ListType/MapType/PrimitiveType） 传递给子类的
 * record/list/map/primitive 方法。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>struct 字段匹配：按 ORC 字段的 Iceberg id 在 Iceberg StructType 中查找对应字段类型， 实现按 id 而非按列名的字段匹配。
 *   <li>支持 Iceberg 类型为 null（投影时 ORC 有列但 Iceberg schema 无对应字段）， 子类需处理 null 分支。
 * </ul>
 *
 * <p>上下游关系：被 {@link org.apache.iceberg.data.orc.GenericOrcReader.ReadBuilder} 和 {@link
 * org.apache.iceberg.data.orc.GenericOrcWriter.WriteBuilder} 继承，用于构建 reader/writer 树。
 */
public abstract class OrcSchemaWithTypeVisitor<T> {
  /** 便捷入口：从 Iceberg Schema 和 ORC schema 开始配对遍历。 */
  public static <T> T visit(
      org.apache.iceberg.Schema iSchema,
      TypeDescription schema,
      OrcSchemaWithTypeVisitor<T> visitor) {
    return visit(iSchema.asStruct(), schema, visitor);
  }

  /**
   * 核心递归遍历：按 ORC category 配对 Iceberg Type 进行访问。
   *
   * <p>逻辑：STRUCT 走 visitRecord（按 id 匹配字段）；LIST 取 Iceberg ListType 的 elementType 与 ORC 子节点配对；MAP 取
   * keyType/valueType 与 ORC 两个子节点配对；其余走 primitive。 Iceberg 类型可能为 null（投影裁剪）。
   */
  public static <T> T visit(
      Type iType, TypeDescription schema, OrcSchemaWithTypeVisitor<T> visitor) {
    switch (schema.getCategory()) {
      case STRUCT:
        return visitRecord(iType != null ? iType.asStructType() : null, schema, visitor);

      case UNION:
        throw new UnsupportedOperationException("Cannot handle " + schema);

      case LIST:
        Types.ListType list = iType != null ? iType.asListType() : null;
        return visitor.list(
            list,
            schema,
            visit(list != null ? list.elementType() : null, schema.getChildren().get(0), visitor));

      case MAP:
        Types.MapType map = iType != null ? iType.asMapType() : null;
        return visitor.map(
            map,
            schema,
            visit(map != null ? map.keyType() : null, schema.getChildren().get(0), visitor),
            visit(map != null ? map.valueType() : null, schema.getChildren().get(1), visitor));

      default:
        return visitor.primitive(iType != null ? iType.asPrimitiveType() : null, schema);
    }
  }

  private static <T> T visitRecord(
      Types.StructType struct, TypeDescription record, OrcSchemaWithTypeVisitor<T> visitor) {
    List<TypeDescription> fields = record.getChildren();
    List<String> names = record.getFieldNames();
    List<T> results = Lists.newArrayListWithExpectedSize(fields.size());
    for (TypeDescription field : fields) {
      int fieldId = ORCSchemaUtil.fieldId(field);
      Types.NestedField iField = struct != null ? struct.field(fieldId) : null;
      results.add(visit(iField != null ? iField.type() : null, field, visitor));
    }
    return visitor.record(struct, record, names, results);
  }

  public T record(
      Types.StructType iStruct, TypeDescription record, List<String> names, List<T> fields) {
    return null;
  }

  public T list(Types.ListType iList, TypeDescription array, T element) {
    return null;
  }

  public T map(Types.MapType iMap, TypeDescription map, T key, T value) {
    return null;
  }

  public T primitive(Type.PrimitiveType iPrimitive, TypeDescription primitive) {
    return null;
  }
}
