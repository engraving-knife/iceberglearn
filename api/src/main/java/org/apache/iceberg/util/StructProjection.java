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
package org.apache.iceberg.util;

import java.util.List;
import java.util.Set;
import org.apache.iceberg.Schema;
import org.apache.iceberg.StructLike;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.types.TypeUtil;
import org.apache.iceberg.types.Types;
import org.apache.iceberg.types.Types.ListType;
import org.apache.iceberg.types.Types.MapType;
import org.apache.iceberg.types.Types.StructType;

/**
 * 结构投影（projection）实现：将一个 {@link StructLike} 行按目标 schema 投影为只包含 指定字段的视图，避免拷贝数据。
 *
 * <p>所属模块：iceberg-api。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>按字段 id 建立投影位置映射（positionMap），把目标 schema 中的字段位置映射到原 schema 中的位置。
 *   <li>对嵌套 struct 递归建立子投影；对 list/map 仅在类型完全匹配时透传，不支持部分投影。
 *   <li>实现 {@link StructLike}，通过 {@link #wrap(StructLike)} 绑定底层数据行后即可按投影 schema 读取字段。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>零拷贝视图：不复制数据，仅维护位置映射数组与（嵌套时的）子投影，适用于扫描时按需 读取子集字段，性能优于物化新行。
 *   <li>按字段 id 而非名称匹配：保证 schema 演进（重命名/重排）后投影仍正确。
 *   <li>list/map 的部分结构投影被禁止：因元素/键值是重复结构，无法用单一位置映射表达， 故要求投影前后类型一致，否则抛出 IllegalArgumentException。
 *   <li>{@link #createAllowMissing} 允许目标字段在源中缺失（仅可选字段），位置记为 -1， 读取时返回 null，用于 schema
 *       演进中新增可选字段的兼容读取。
 * </ul>
 *
 * <p>上下游关系：被 core 模块的扫描与读取逻辑用于按投影 schema 读取行数据。
 */
public class StructProjection implements StructLike {
  /**
   * 按字段 id 集合创建投影包装器。
   *
   * <p>本投影不支持 list/map 等重复类型。
   *
   * @param schema 原始行 schema
   * @param ids 需投影的字段 id 集合
   * @return 投影包装器
   */
  public static StructProjection create(Schema schema, Set<Integer> ids) {
    StructType structType = schema.asStruct();
    return new StructProjection(structType, TypeUtil.project(structType, ids));
  }

  /**
   * 按 schema 创建投影包装器。
   *
   * <p>本投影不支持 list/map 等重复类型。
   *
   * @param dataSchema 原始行 schema
   * @param projectedSchema 投影后的目标 schema
   * @return 投影包装器
   */
  public static StructProjection create(Schema dataSchema, Schema projectedSchema) {
    return new StructProjection(dataSchema.asStruct(), projectedSchema.asStruct());
  }

  /**
   * 按 {@link StructType} 创建投影包装器。
   *
   * <p>本投影不支持 list/map 等重复类型。
   *
   * @param structType 原始行结构类型
   * @param projectedStructType 投影后的目标结构类型
   * @return 投影包装器
   */
  public static StructProjection create(StructType structType, StructType projectedStructType) {
    return new StructProjection(structType, projectedStructType);
  }

  /**
   * 按 {@link StructType} 创建投影包装器，允许目标字段在源中缺失。
   *
   * <p>本投影支持缺失字段（仅可选字段，读取时返回 null），不支持 list/map 等重复类型。
   *
   * @param structType 原始行结构类型
   * @param projectedStructType 投影后的目标结构类型
   * @return 投影包装器
   */
  public static StructProjection createAllowMissing(
      StructType structType, StructType projectedStructType) {
    return new StructProjection(structType, projectedStructType, true);
  }

  private final StructType type;
  private final int[] positionMap;
  private final StructProjection[] nestedProjections;
  private StructLike struct;

  private StructProjection(
      StructType type, int[] positionMap, StructProjection[] nestedProjections) {
    this.type = type;
    this.positionMap = positionMap;
    this.nestedProjections = nestedProjections;
  }

  private StructProjection(StructType structType, StructType projection) {
    this(structType, projection, false);
  }

  /**
   * 构造投影：建立目标字段到源字段的位置映射，并为嵌套 struct 递归创建子投影。
   *
   * <p>逻辑：遍历目标 schema 的每个字段，在源 schema 中按字段 id 查找匹配字段，记录其位置到 positionMap；若该字段是 STRUCT 则递归构造子投影；若是
   * MAP/LIST 则校验类型一致（不允许 部分投影）；找不到且 allowMissing 且字段可选时记为 -1，否则抛出异常。
   *
   * @param structType 源结构类型
   * @param projection 目标投影结构类型
   * @param allowMissing 是否允许目标字段在源中缺失
   * @throws IllegalArgumentException 若字段找不到且不允许缺失，或 list/map 类型不一致
   */
  @SuppressWarnings("checkstyle:CyclomaticComplexity")
  private StructProjection(StructType structType, StructType projection, boolean allowMissing) {
    this.type = projection;
    this.positionMap = new int[projection.fields().size()];
    this.nestedProjections = new StructProjection[projection.fields().size()];

    // set up the projection positions and any nested projections that are needed
    List<Types.NestedField> dataFields = structType.fields();
    for (int pos = 0; pos < positionMap.length; pos += 1) {
      Types.NestedField projectedField = projection.fields().get(pos);

      boolean found = false;
      for (int i = 0; !found && i < dataFields.size(); i += 1) {
        Types.NestedField dataField = dataFields.get(i);
        if (projectedField.fieldId() == dataField.fieldId()) {
          found = true;
          positionMap[pos] = i;
          switch (projectedField.type().typeId()) {
            case STRUCT:
              nestedProjections[pos] =
                  new StructProjection(
                      dataField.type().asStructType(), projectedField.type().asStructType());
              break;
            case MAP:
              MapType projectedMap = projectedField.type().asMapType();
              MapType originalMap = dataField.type().asMapType();

              boolean keyProjectable =
                  !projectedMap.keyType().isNestedType()
                      || projectedMap.keyType().equals(originalMap.keyType());
              boolean valueProjectable =
                  !projectedMap.valueType().isNestedType()
                      || projectedMap.valueType().equals(originalMap.valueType());
              Preconditions.checkArgument(
                  keyProjectable && valueProjectable,
                  "Cannot project a partial map key or value struct. Trying to project %s out of %s",
                  projectedField,
                  dataField);

              nestedProjections[pos] = null;
              break;
            case LIST:
              ListType projectedList = projectedField.type().asListType();
              ListType originalList = dataField.type().asListType();

              boolean elementProjectable =
                  !projectedList.elementType().isNestedType()
                      || projectedList.elementType().equals(originalList.elementType());
              Preconditions.checkArgument(
                  elementProjectable,
                  "Cannot project a partial list element struct. Trying to project %s out of %s",
                  projectedField,
                  dataField);

              nestedProjections[pos] = null;
              break;
            default:
              nestedProjections[pos] = null;
          }
        }
      }

      if (!found && projectedField.isOptional() && allowMissing) {
        positionMap[pos] = -1;
        nestedProjections[pos] = null;
      } else if (!found) {
        throw new IllegalArgumentException(
            String.format("Cannot find field %s in %s", projectedField, structType));
      }
    }
  }

  /**
   * 绑定新的底层数据行，返回自身以支持链式调用。
   *
   * @param newStruct 待投影的数据行
   * @return 本投影对象
   */
  public StructProjection wrap(StructLike newStruct) {
    this.struct = newStruct;
    return this;
  }

  /**
   * 为新的数据行创建一个共享映射关系但独立绑定的投影副本。
   *
   * @param newStruct 待投影的数据行
   * @return 新的投影实例，复用本对象的 positionMap 与 nestedProjections
   */
  public StructProjection copyFor(StructLike newStruct) {
    return new StructProjection(type, positionMap, nestedProjections).wrap(newStruct);
  }

  @Override
  public int size() {
    return type.fields().size();
  }

  /**
   * 按投影位置读取字段值。
   *
   * <p>逻辑：若未绑定 struct（或绑定为 null）则返回 null；按 positionMap 映射到源位置； 若该字段有嵌套投影则取出源中的 StructLike
   * 并用子投影包装后返回；若映射位置为 -1 （缺失字段）则返回 null；否则直接从源行读取。
   *
   * @param pos 投影 schema 中的字段位置
   * @param javaClass 期望的 Java 类型
   * @param <T> 返回值类型
   * @return 字段值，可能为 null
   */
  @Override
  public <T> T get(int pos, Class<T> javaClass) {
    // struct can be null if wrap is not called first before the get call
    // or if a null struct is wrapped.
    if (struct == null) {
      return null;
    }

    int structPos = positionMap[pos];
    if (nestedProjections[pos] != null) {
      StructLike nestedStruct = struct.get(structPos, StructLike.class);
      if (nestedStruct == null) {
        return null;
      }

      return javaClass.cast(nestedProjections[pos].wrap(nestedStruct));
    }

    if (structPos != -1) {
      return struct.get(structPos, javaClass);
    } else {
      return null;
    }
  }

  @Override
  public <T> void set(int pos, T value) {
    throw new UnsupportedOperationException("Cannot set fields in a TypeProjection");
  }
}
