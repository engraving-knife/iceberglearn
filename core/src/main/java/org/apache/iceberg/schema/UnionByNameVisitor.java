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
package org.apache.iceberg.schema;

import java.util.List;
import java.util.stream.IntStream;
import org.apache.iceberg.Schema;
import org.apache.iceberg.UpdateSchema;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;

/**
 * 按名称联合两个 Schema 的访问器（iceberg-core 模式演化层）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>遍历新 Schema 与现有 Schema，按字段名比对差异；
 *   <li>将差异（新增列、可空性变更、类型提升、文档更新）累积到 {@link UpdateSchema} 操作中；
 *   <li>支持大小写敏感/不敏感两种字段名匹配模式。
 * </ul>
 *
 * <p>设计意图：
 *
 * <p>采用访问者模式（{@link SchemaWithPartnerVisitor}），将"比对逻辑"与"变更应用"解耦。 partnerId 为现有 Schema 中对应字段的
 * ID（按名匹配，可能为 null 表示缺失）， 访问结果 Boolean 表示该节点是否为新 Schema 中缺失的字段（需在 partner 侧新增）。
 *
 * <p>上下游关系：被 {@link UpdateSchema#unionByNameWith(Schema)} 调用， 依赖 {@link PartnerAccessors} 提供按名定位
 * partner 字段的能力。
 */
public class UnionByNameVisitor extends SchemaWithPartnerVisitor<Integer, Boolean> {

  private final UpdateSchema api;
  private final Schema partnerSchema;
  private boolean caseSensitive;

  /**
   * 私有构造方法。
   *
   * @param api 用于累积变更的 {@link UpdateSchema} 操作
   * @param partnerSchema 现有 Schema，作为比对基准
   * @param caseSensitive 字段名匹配是否大小写敏感
   */
  private UnionByNameVisitor(UpdateSchema api, Schema partnerSchema, boolean caseSensitive) {
    this.api = api;
    this.partnerSchema = partnerSchema;
    this.caseSensitive = caseSensitive;
  }

  /**
   * 将两个 Schema 联合所需的变更累积到 {@link UpdateSchema} 操作中（大小写敏感）。
   *
   * <p>把 existingSchema 演化为与 newSchema 的联合。
   *
   * @param api 用于累积变更的 {@link UpdateSchema} 操作
   * @param existingSchema 现有 Schema
   * @param newSchema 与现有 Schema 比对的新 Schema
   */
  public static void visit(UpdateSchema api, Schema existingSchema, Schema newSchema) {
    visit(api, existingSchema, newSchema, true);
  }

  /**
   * 将两个 Schema 联合所需的变更累积到 {@link UpdateSchema} 操作中。
   *
   * <p>把 existingSchema 演化为与 newSchema 的联合。
   *
   * @param api 用于累积变更的 {@link UpdateSchema} 操作
   * @param existingSchema 现有 Schema
   * @param caseSensitive 为 false 时忽略字段名大小写
   * @param newSchema 与现有 Schema 比对的新 Schema
   */
  public static void visit(
      UpdateSchema api, Schema existingSchema, Schema newSchema, boolean caseSensitive) {
    visit(
        newSchema,
        -1,
        new UnionByNameVisitor(api, existingSchema, caseSensitive),
        new PartnerIdByNameAccessors(existingSchema, caseSensitive));
  }

  /**
   * 访问 struct 类型节点，按字段名比对并累积新增或更新变更。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>若 partnerId 为 null（现有 Schema 中缺失该 struct），返回 true 表示缺失；
   *   <li>否则遍历新 struct 的每个字段，根据 missingPositions 判断该字段在现有 Schema 中是否缺失：
   *       <ul>
   *         <li>缺失则调用 {@link #addColumn} 新增；
   *         <li>否则按名匹配 partner 字段并调用 {@link #updateColumn} 处理可空性/类型/文档差异。
   *       </ul>
   * </ol>
   *
   * @param struct 新 Schema 中的 struct 类型
   * @param partnerId 现有 Schema 中对应字段的 ID，null 表示缺失
   * @param missingPositions 各位置字段是否在 partner 侧缺失
   * @return 该 struct 是否在 partner 侧缺失
   */
  @Override
  public Boolean struct(
      Types.StructType struct, Integer partnerId, List<Boolean> missingPositions) {
    if (partnerId == null) {
      return true;
    }

    List<Types.NestedField> fields = struct.fields();
    Types.StructType partnerStruct = findFieldType(partnerId).asStructType();
    IntStream.range(0, missingPositions.size())
        .forEach(
            pos -> {
              Boolean isMissing = missingPositions.get(pos);
              Types.NestedField field = fields.get(pos);
              if (isMissing) {
                addColumn(partnerId, field);
              } else {
                Types.NestedField nestedField =
                    caseSensitive
                        ? partnerStruct.field(field.name())
                        : partnerStruct.caseInsensitiveField(field.name());
                updateColumn(field, nestedField);
              }
            });

    return false;
  }

  /**
   * 访问 struct 中的字段节点。
   *
   * @param field 新 Schema 中的字段
   * @param partnerId 现有 Schema 中对应字段的 ID，null 表示缺失
   * @param isFieldMissing 该字段在 partner 侧是否缺失
   * @return 该字段是否在 partner 侧缺失
   */
  @Override
  public Boolean field(Types.NestedField field, Integer partnerId, Boolean isFieldMissing) {
    return partnerId == null;
  }

  /**
   * 访问 list 类型节点，校验元素存在并按需更新元素字段。
   *
   * <p>逻辑：partnerId 为 null 时返回 true 表示缺失；否则校验元素未缺失， 并调用 {@link #updateColumn} 比对 list 元素字段。
   *
   * @param list 新 Schema 中的 list 类型
   * @param partnerId 现有 Schema 中对应字段的 ID
   * @param isElementMissing 元素是否在 partner 侧缺失
   * @return 该 list 是否在 partner 侧缺失
   */
  @Override
  public Boolean list(Types.ListType list, Integer partnerId, Boolean isElementMissing) {
    if (partnerId == null) {
      return true;
    }

    Preconditions.checkState(
        !isElementMissing, "Error traversing schemas: element is missing, but list is present");

    Types.ListType partnerList = findFieldType(partnerId).asListType();
    updateColumn(list.fields().get(0), partnerList.fields().get(0));

    return false;
  }

  /**
   * 访问 map 类型节点，校验 key/value 存在并按需更新对应字段。
   *
   * <p>逻辑：partnerId 为 null 时返回 true 表示缺失；否则校验 key 和 value 均未缺失， 并分别调用 {@link #updateColumn} 比对 map
   * 的 key 字段与 value 字段。
   *
   * @param map 新 Schema 中的 map 类型
   * @param partnerId 现有 Schema 中对应字段的 ID
   * @param isKeyMissing key 是否在 partner 侧缺失
   * @param isValueMissing value 是否在 partner 侧缺失
   * @return 该 map 是否在 partner 侧缺失
   */
  @Override
  public Boolean map(
      Types.MapType map, Integer partnerId, Boolean isKeyMissing, Boolean isValueMissing) {
    if (partnerId == null) {
      return true;
    }

    Preconditions.checkState(
        !isKeyMissing, "Error traversing schemas: key is missing, but map is present");
    Preconditions.checkState(
        !isValueMissing, "Error traversing schemas: value is missing, but map is present");

    Types.MapType partnerMap = findFieldType(partnerId).asMapType();
    updateColumn(map.fields().get(0), partnerMap.fields().get(0));
    updateColumn(map.fields().get(1), partnerMap.fields().get(1));

    return false;
  }

  /**
   * 访问基本类型节点。
   *
   * @param primitive 新 Schema 中的基本类型
   * @param partnerId 现有 Schema 中对应字段的 ID，null 表示缺失
   * @return 该基本类型是否在 partner 侧缺失
   */
  @Override
  public Boolean primitive(Type.PrimitiveType primitive, Integer partnerId) {
    return partnerId == null;
  }

  /**
   * 根据 fieldId 在 partner Schema 中查找对应的类型。
   *
   * @param fieldId 字段 ID，-1 表示根 struct
   * @return partner Schema 中该字段的类型
   */
  private Type findFieldType(int fieldId) {
    if (fieldId == -1) {
      return partnerSchema.asStruct();
    } else {
      return partnerSchema.findField(fieldId).type();
    }
  }

  /**
   * 将新字段添加到指定父字段下。
   *
   * @param parentId 父字段的 ID
   * @param field 待新增的字段定义
   */
  private void addColumn(int parentId, Types.NestedField field) {
    String parentName = partnerSchema.findColumnName(parentId);
    api.addColumn(parentName, field.name(), field.type(), field.doc());
  }

  /**
   * 比对新字段与现有字段，按需触发可空性、类型、文档三类更新。
   *
   * <p>逻辑：
   *
   * <ul>
   *   <li>新字段可选且现有字段必填时，调用 {@link UpdateSchema#makeColumnOptional}；
   *   <li>新字段为基本类型且与现有类型不同时，调用 {@link UpdateSchema#updateColumn}；
   *   <li>新字段文档非空且与现有文档不同时，调用 {@link UpdateSchema#updateColumnDoc}。
   * </ul>
   *
   * @param field 新 Schema 中的字段
   * @param existingField 现有 Schema 中同名字段
   */
  private void updateColumn(Types.NestedField field, Types.NestedField existingField) {
    String fullName = partnerSchema.findColumnName(existingField.fieldId());

    boolean needsOptionalUpdate = field.isOptional() && existingField.isRequired();
    boolean needsTypeUpdate =
        field.type().isPrimitiveType() && !field.type().equals(existingField.type());
    boolean needsDocUpdate = field.doc() != null && !field.doc().equals(existingField.doc());

    if (needsOptionalUpdate) {
      api.makeColumnOptional(fullName);
    }

    if (needsTypeUpdate) {
      api.updateColumn(fullName, field.type().asPrimitiveType());
    }

    if (needsDocUpdate) {
      api.updateColumnDoc(fullName, field.doc());
    }
  }

  /**
   * 基于字段名在 partner Schema 中定位对应字段 ID 的访问器实现（iceberg-core 模式演化层）。
   *
   * <p>职责：实现 {@link PartnerAccessors} 接口，按字段名（可配置大小写敏感） 在 partner Schema 中查找 struct 字段、map
   * key/value、list 元素对应的 ID， 供访问者遍历时判断 partner 侧是否存在对应节点。
   */
  private static class PartnerIdByNameAccessors implements PartnerAccessors<Integer> {
    private final Schema partnerSchema;
    private boolean caseSensitive = true;

    private PartnerIdByNameAccessors(Schema partnerSchema) {
      this.partnerSchema = partnerSchema;
    }

    private PartnerIdByNameAccessors(Schema partnerSchema, boolean caseSensitive) {
      this(partnerSchema);
      this.caseSensitive = caseSensitive;
    }

    /**
     * 在 partner 的 struct 中按名查找对应字段的 ID。
     *
     * @param partnerFieldId partner 侧父 struct 字段的 ID，-1 表示根 struct
     * @param fieldId 新 Schema 中该字段的 ID（未使用）
     * @param name 待匹配的字段名
     * @return partner 中匹配字段的 ID，未匹配返回 null
     */
    @Override
    public Integer fieldPartner(Integer partnerFieldId, int fieldId, String name) {
      Types.StructType struct;
      if (partnerFieldId == -1) {
        struct = partnerSchema.asStruct();
      } else {
        struct = partnerSchema.findField(partnerFieldId).type().asStructType();
      }

      Types.NestedField field =
          caseSensitive ? struct.field(name) : struct.caseInsensitiveField(name);
      if (field != null) {
        return field.fieldId();
      }

      return null;
    }

    /**
     * 获取 partner 中指定 map 字段的 key 字段 ID。
     *
     * @param partnerMapId partner 中 map 字段的 ID
     * @return map key 的字段 ID，map 不存在时返回 null
     */
    @Override
    public Integer mapKeyPartner(Integer partnerMapId) {
      Types.NestedField mapField = partnerSchema.findField(partnerMapId);
      if (mapField != null) {
        return mapField.type().asMapType().fields().get(0).fieldId();
      }

      return null;
    }

    /**
     * 获取 partner 中指定 map 字段的 value 字段 ID。
     *
     * @param partnerMapId partner 中 map 字段的 ID
     * @return map value 的字段 ID，map 不存在时返回 null
     */
    @Override
    public Integer mapValuePartner(Integer partnerMapId) {
      Types.NestedField mapField = partnerSchema.findField(partnerMapId);
      if (mapField != null) {
        return mapField.type().asMapType().fields().get(1).fieldId();
      }

      return null;
    }

    /**
     * 获取 partner 中指定 list 字段的元素字段 ID。
     *
     * @param partnerListId partner 中 list 字段的 ID
     * @return list 元素的字段 ID，list 不存在时返回 null
     */
    @Override
    public Integer listElementPartner(Integer partnerListId) {
      Types.NestedField listField = partnerSchema.findField(partnerListId);
      if (listField != null) {
        return listField.type().asListType().fields().get(0).fieldId();
      }

      return null;
    }
  }
}
