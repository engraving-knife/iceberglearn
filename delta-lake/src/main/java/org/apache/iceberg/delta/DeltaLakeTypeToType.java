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
package org.apache.iceberg.delta;

import io.delta.standalone.types.ArrayType;
import io.delta.standalone.types.BinaryType;
import io.delta.standalone.types.BooleanType;
import io.delta.standalone.types.ByteType;
import io.delta.standalone.types.DataType;
import io.delta.standalone.types.DateType;
import io.delta.standalone.types.DecimalType;
import io.delta.standalone.types.DoubleType;
import io.delta.standalone.types.FloatType;
import io.delta.standalone.types.IntegerType;
import io.delta.standalone.types.LongType;
import io.delta.standalone.types.MapType;
import io.delta.standalone.types.ShortType;
import io.delta.standalone.types.StringType;
import io.delta.standalone.types.StructField;
import io.delta.standalone.types.StructType;
import io.delta.standalone.types.TimestampType;
import java.util.List;
import org.apache.iceberg.exceptions.ValidationException;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;

/**
 * Delta Lake 类型到 Iceberg 类型的转换器（访问者实现）。
 *
 * <p>所属模块：iceberg-delta-lake（Delta Lake 表迁移到 Iceberg 的支持模块）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>继承 {@link DeltaLakeDataTypeVisitor}，在遍历 Delta Lake 类型树时将每个节点 转换为对应的 Iceberg {@link Type}。
 *   <li>为嵌套字段分配自增字段 ID，保证 Iceberg schema 的字段 ID 唯一性。
 *   <li>处理字段可空性（optional/required）、字段注释（comment）等元信息。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>字段 ID 分配策略：根 Struct 的字段使用"序号"作为 ID（与 Iceberg 默认行为一致， 便于与已有表 schema 对齐），而嵌套类型的字段使用自增计数器分配新
 *       ID， 确保全局唯一。这是 Iceberg schema 演进和字段追踪的基础。
 *   <li>引用等价（{@code @SuppressWarnings("ReferenceEquality")}）：根 Struct 的判断使用 {@code root ==
 *       struct}（引用相等而非值相等），因为传入的根 Struct 就是构造时保存的引用。
 *   <li>Timestamp 默认带时区：Delta Lake 的 TimestampType 统一映射为 {@link Types.TimestampType#withZone()}，与
 *       Iceberg 的 timestamp-with-timezone 对应。
 * </ul>
 *
 * <p>上下游关系：被 {@link BaseSnapshotDeltaLakeTableAction#convertDeltaLakeSchema} 调用， 通过 {@link
 * DeltaLakeDataTypeVisitor#visit(DataType, DeltaLakeDataTypeVisitor)} 驱动遍历。
 */
class DeltaLakeTypeToType extends DeltaLakeDataTypeVisitor<Type> {
  private final StructType root;
  private int nextId = 0;

  /**
   * 构造一个无根 Struct 的转换器。
   *
   * <p>不指定根 Struct 时，所有层级的字段 ID 都通过自增分配，根 Struct 不享有"序号即 ID"的特殊待遇。
   */
  DeltaLakeTypeToType() {
    this.root = null;
  }

  /**
   * 构造一个以 {@code root} 为根 Struct 的转换器。
   *
   * <p>设计要点：以根 Struct 的字段数初始化 {@code nextId}，使嵌套字段的 ID 从根字段数之后 开始自增，避免与根字段的序号 ID 冲突。
   *
   * @param root Delta Lake 表的顶层 StructType
   */
  DeltaLakeTypeToType(StructType root) {
    this.root = root;
    this.nextId = root.getFields().length;
  }

  /**
   * 获取并消耗下一个自增字段 ID。
   *
   * @return 当前 {@code nextId} 值，返回后自增
   */
  private int getNextId() {
    int next = nextId;
    nextId += 1;
    return next;
  }

  /**
   * 将 Delta Lake 的 StructType 转换为 Iceberg 的 {@link Types.StructType}。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>遍历各字段，取出已转换的类型结果。
   *   <li>判断当前 Struct 是否为根 Struct（引用相等）：若是，字段 ID 直接使用序号 {@code i}； 否则通过 {@link #getNextId()} 分配新
   *       ID。
   *   <li>从字段 metadata 中提取 {@code comment} 作为字段文档。
   *   <li>根据字段可空性创建 {@link Types.NestedField#optional} 或 {@link Types.NestedField#required}。
   * </ol>
   *
   * @param struct Delta Lake 的 StructType 节点
   * @param types 各字段已转换的 Iceberg 类型列表
   * @return 转换后的 Iceberg {@link Types.StructType}
   */
  @Override
  @SuppressWarnings("ReferenceEquality")
  public Type struct(StructType struct, List<Type> types) {
    StructField[] fields = struct.getFields();
    List<Types.NestedField> newFields = Lists.newArrayListWithExpectedSize(fields.length);
    boolean isRoot = root == struct;
    for (int i = 0; i < fields.length; i += 1) {
      StructField field = fields[i];
      Type type = types.get(i);

      int id;
      if (isRoot) {
        // for new conversions, use ordinals for ids in the root struct
        id = i;
      } else {
        id = getNextId();
      }

      String doc =
          field.getMetadata().contains("comment")
              ? field.getMetadata().get("comment").toString()
              : null;

      if (field.isNullable()) {
        newFields.add(Types.NestedField.optional(id, field.getName(), type, doc));
      } else {
        newFields.add(Types.NestedField.required(id, field.getName(), type, doc));
      }
    }

    return Types.StructType.of(newFields);
  }

  /**
   * 字段回调：直接返回该字段已转换的类型结果。
   *
   * <p>本转换器在 {@link #struct(StructType, List)} 中已处理字段级别的逻辑（ID 分配、可空性等）， 因此这里只需透传类型结果。
   *
   * @param field Delta Lake 的字段定义（本实现未使用）
   * @param typeResult 该字段已转换的 Iceberg 类型
   * @return 传入的 {@code typeResult}
   */
  @Override
  public Type field(StructField field, Type typeResult) {
    return typeResult;
  }

  /**
   * 将 Delta Lake 的 ArrayType 转换为 Iceberg 的 {@link Types.ListType}。
   *
   * <p>根据 {@code array.containsNull()} 决定元素是 optional（可含 null）还是 required（不含 null）， 并为元素分配新的字段 ID。
   *
   * @param array Delta Lake 的 ArrayType 节点
   * @param elementType 已转换的元素 Iceberg 类型
   * @return 转换后的 Iceberg {@link Types.ListType}
   */
  @Override
  public Type array(ArrayType array, Type elementType) {
    if (array.containsNull()) {
      return Types.ListType.ofOptional(getNextId(), elementType);
    } else {
      return Types.ListType.ofRequired(getNextId(), elementType);
    }
  }

  /**
   * 将 Delta Lake 的 MapType 转换为 Iceberg 的 {@link Types.MapType}。
   *
   * <p>为 key 和 value 分别分配新的字段 ID。根据 {@code map.valueContainsNull()} 决定 value 是 optional 还是
   * required（key 在 Iceberg 中始终 required）。
   *
   * @param map Delta Lake 的 MapType 节点
   * @param keyType 已转换的 key Iceberg 类型
   * @param valueType 已转换的 value Iceberg 类型
   * @return 转换后的 Iceberg {@link Types.MapType}
   */
  @Override
  public Type map(MapType map, Type keyType, Type valueType) {
    if (map.valueContainsNull()) {
      return Types.MapType.ofOptional(getNextId(), getNextId(), keyType, valueType);
    } else {
      return Types.MapType.ofRequired(getNextId(), getNextId(), keyType, valueType);
    }
  }

  /**
   * 将 Delta Lake 的原子类型转换为对应的 Iceberg 原子类型。
   *
   * <p>逻辑：按类型分支逐一映射——BooleanType、IntegerType/ShortType/ByteType（统一为 Integer）、
   * LongType、FloatType、DoubleType、StringType、DateType、TimestampType（带时区）、
   * DecimalType（保留精度和小数位）、BinaryType。未覆盖的类型抛出 {@link ValidationException}。
   *
   * @param atomic Delta Lake 的原子类型
   * @return 转换后的 Iceberg 原子类型
   * @throws ValidationException 当遇到不支持的类型时
   */
  @SuppressWarnings("checkstyle:CyclomaticComplexity")
  @Override
  public Type atomic(DataType atomic) {
    if (atomic instanceof BooleanType) {
      return Types.BooleanType.get();

    } else if (atomic instanceof IntegerType
        || atomic instanceof ShortType
        || atomic instanceof ByteType) {
      return Types.IntegerType.get();

    } else if (atomic instanceof LongType) {
      return Types.LongType.get();

    } else if (atomic instanceof FloatType) {
      return Types.FloatType.get();

    } else if (atomic instanceof DoubleType) {
      return Types.DoubleType.get();

    } else if (atomic instanceof StringType) {
      return Types.StringType.get();

    } else if (atomic instanceof DateType) {
      return Types.DateType.get();

    } else if (atomic instanceof TimestampType) {
      return Types.TimestampType.withZone();

    } else if (atomic instanceof DecimalType) {
      return Types.DecimalType.of(
          ((DecimalType) atomic).getPrecision(), ((DecimalType) atomic).getScale());
    } else if (atomic instanceof BinaryType) {
      return Types.BinaryType.get();
    }

    throw new ValidationException("Not a supported type: %s", atomic.getCatalogString());
  }
}
