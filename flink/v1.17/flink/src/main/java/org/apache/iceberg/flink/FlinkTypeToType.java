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
package org.apache.iceberg.flink;

import java.util.List;
import java.util.stream.Collectors;
import org.apache.flink.table.types.logical.ArrayType;
import org.apache.flink.table.types.logical.BigIntType;
import org.apache.flink.table.types.logical.BinaryType;
import org.apache.flink.table.types.logical.BooleanType;
import org.apache.flink.table.types.logical.CharType;
import org.apache.flink.table.types.logical.DateType;
import org.apache.flink.table.types.logical.DecimalType;
import org.apache.flink.table.types.logical.DoubleType;
import org.apache.flink.table.types.logical.FloatType;
import org.apache.flink.table.types.logical.IntType;
import org.apache.flink.table.types.logical.LocalZonedTimestampType;
import org.apache.flink.table.types.logical.MapType;
import org.apache.flink.table.types.logical.MultisetType;
import org.apache.flink.table.types.logical.RowType;
import org.apache.flink.table.types.logical.SmallIntType;
import org.apache.flink.table.types.logical.TimeType;
import org.apache.flink.table.types.logical.TimestampType;
import org.apache.flink.table.types.logical.TinyIntType;
import org.apache.flink.table.types.logical.VarBinaryType;
import org.apache.flink.table.types.logical.VarCharType;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;

/**
 * 文件级说明：将 Flink 逻辑类型转换为 Iceberg 类型的访问器。
 *
 * <p>所属模块：iceberg-flink v1.17（Iceberg 与 Flink v1.17 集成模块根包）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>遍历 Flink 的 {@link org.apache.flink.table.types.logical.LogicalType} 树， 返回对应的 Iceberg {@link
 *       Type}。
 *   <li>为嵌套类型（list/map/struct）自动分配递增的字段 ID。
 *   <li>根 struct 的字段使用 0..N-1 作为 ID，与 Iceberg 表 schema 字段 ID 约定一致。
 * </ul>
 *
 * <p>设计意图：Flink 与 Iceberg 的类型系统存在差异（如 Flink 的 CharType 对应 Iceberg
 * StringType，TinyIntType/SmallIntType 都对应 Iceberg IntegerType）， 本访问器集中处理这些差异，避免散落在各处的转换逻辑。
 *
 * <p>上下游关系：上游为 {@link FlinkSchemaUtil}，下游为 Iceberg 的 {@link Types} 类型工厂。
 */
class FlinkTypeToType extends FlinkTypeVisitor<Type> {

  private final RowType root;
  private int nextId;

  /** 不带根 RowType 的构造，常用于不需要根 ID 特殊分配的场景。 */
  FlinkTypeToType() {
    this.root = null;
  }

  /**
   * 带根 RowType 的构造。
   *
   * <p>逻辑：根 struct 字段使用 0..fieldCount-1 作为 ID， 嵌套字段从 fieldCount 起递增分配。
   */
  FlinkTypeToType(RowType root) {
    this.root = root;
    // the root struct's fields use the first ids
    this.nextId = root.getFieldCount();
  }

  /** 分配并返回下一个递增的字段 ID。 */
  private int getNextId() {
    int next = nextId;
    nextId += 1;
    return next;
  }

  @Override
  public Type visit(CharType charType) {
    return Types.StringType.get();
  }

  @Override
  public Type visit(VarCharType varCharType) {
    return Types.StringType.get();
  }

  @Override
  public Type visit(BooleanType booleanType) {
    return Types.BooleanType.get();
  }

  @Override
  public Type visit(BinaryType binaryType) {
    return Types.FixedType.ofLength(binaryType.getLength());
  }

  @Override
  public Type visit(VarBinaryType varBinaryType) {
    return Types.BinaryType.get();
  }

  @Override
  public Type visit(DecimalType decimalType) {
    return Types.DecimalType.of(decimalType.getPrecision(), decimalType.getScale());
  }

  @Override
  public Type visit(TinyIntType tinyIntType) {
    return Types.IntegerType.get();
  }

  @Override
  public Type visit(SmallIntType smallIntType) {
    return Types.IntegerType.get();
  }

  @Override
  public Type visit(IntType intType) {
    return Types.IntegerType.get();
  }

  @Override
  public Type visit(BigIntType bigIntType) {
    return Types.LongType.get();
  }

  @Override
  public Type visit(FloatType floatType) {
    return Types.FloatType.get();
  }

  @Override
  public Type visit(DoubleType doubleType) {
    return Types.DoubleType.get();
  }

  @Override
  public Type visit(DateType dateType) {
    return Types.DateType.get();
  }

  @Override
  public Type visit(TimeType timeType) {
    return Types.TimeType.get();
  }

  @Override
  public Type visit(TimestampType timestampType) {
    return Types.TimestampType.withoutZone();
  }

  @Override
  public Type visit(LocalZonedTimestampType localZonedTimestampType) {
    return Types.TimestampType.withZone();
  }

  @Override
  public Type visit(ArrayType arrayType) {
    Type elementType = arrayType.getElementType().accept(this);
    if (arrayType.getElementType().isNullable()) {
      return Types.ListType.ofOptional(getNextId(), elementType);
    } else {
      return Types.ListType.ofRequired(getNextId(), elementType);
    }
  }

  @Override
  public Type visit(MultisetType multisetType) {
    Type elementType = multisetType.getElementType().accept(this);
    return Types.MapType.ofRequired(getNextId(), getNextId(), elementType, Types.IntegerType.get());
  }

  @Override
  public Type visit(MapType mapType) {
    // keys in map are not allowed to be null.
    Type keyType = mapType.getKeyType().accept(this);
    Type valueType = mapType.getValueType().accept(this);
    if (mapType.getValueType().isNullable()) {
      return Types.MapType.ofOptional(getNextId(), getNextId(), keyType, valueType);
    } else {
      return Types.MapType.ofRequired(getNextId(), getNextId(), keyType, valueType);
    }
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  public Type visit(RowType rowType) {
    List<Types.NestedField> newFields = Lists.newArrayListWithExpectedSize(rowType.getFieldCount());
    boolean isRoot = root == rowType;

    List<Type> types =
        rowType.getFields().stream()
            .map(f -> f.getType().accept(this))
            .collect(Collectors.toList());

    for (int i = 0; i < rowType.getFieldCount(); i++) {
      int id = isRoot ? i : getNextId();

      RowType.RowField field = rowType.getFields().get(i);
      String name = field.getName();
      String comment = field.getDescription().orElse(null);

      if (field.getType().isNullable()) {
        newFields.add(Types.NestedField.optional(id, name, types.get(i), comment));
      } else {
        newFields.add(Types.NestedField.required(id, name, types.get(i), comment));
      }
    }

    return Types.StructType.of(newFields);
  }
}
