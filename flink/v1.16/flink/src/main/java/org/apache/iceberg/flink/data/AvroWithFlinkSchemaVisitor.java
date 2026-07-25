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
package org.apache.iceberg.flink.data;

import org.apache.flink.table.types.logical.ArrayType;
import org.apache.flink.table.types.logical.LogicalType;
import org.apache.flink.table.types.logical.LogicalTypeFamily;
import org.apache.flink.table.types.logical.MapType;
import org.apache.flink.table.types.logical.NullType;
import org.apache.flink.table.types.logical.RowType;
import org.apache.iceberg.avro.AvroWithPartnerByStructureVisitor;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.util.Pair;

/**
 * 以 Flink {@link LogicalType} 为 partner 的 Avro schema 访问者抽象基类。
 *
 * <p>所属模块：iceberg-flink v1.15。职责：在 Avro 与 Flink schema 互转时， 提供按结构访问 LogicalType 的方法（数组元素、map
 * 键值、struct 字段等）。
 *
 * <p>设计意图：模板方法模式——继承 Iceberg 的 {@link AvroWithPartnerByStructureVisitor}， 为 Flink LogicalType
 * 提供具体的类型判断与字段访问实现。
 */
public abstract class AvroWithFlinkSchemaVisitor<T>
    extends AvroWithPartnerByStructureVisitor<LogicalType, T> {

  /** 判断是否为字符串类型。 */
  @Override
  protected boolean isStringType(LogicalType logicalType) {
    return logicalType.getTypeRoot().getFamilies().contains(LogicalTypeFamily.CHARACTER_STRING);
  }

  /** 判断是否为 Map 类型。 */
  @Override
  protected boolean isMapType(LogicalType logicalType) {
    return logicalType instanceof MapType;
  }

  /** 返回 ArrayType 的元素类型。 */
  @Override
  protected LogicalType arrayElementType(LogicalType arrayType) {
    Preconditions.checkArgument(
        arrayType instanceof ArrayType, "Invalid array: %s is not an array", arrayType);
    return ((ArrayType) arrayType).getElementType();
  }

  /** 返回 MapType 的键类型。 */
  @Override
  protected LogicalType mapKeyType(LogicalType mapType) {
    Preconditions.checkArgument(isMapType(mapType), "Invalid map: %s is not a map", mapType);
    return ((MapType) mapType).getKeyType();
  }

  /** 返回 MapType 的值类型。 */
  @Override
  protected LogicalType mapValueType(LogicalType mapType) {
    Preconditions.checkArgument(isMapType(mapType), "Invalid map: %s is not a map", mapType);
    return ((MapType) mapType).getValueType();
  }

  /** 返回 RowType 中指定位置字段的名称与类型。 */
  @Override
  protected Pair<String, LogicalType> fieldNameAndType(LogicalType structType, int pos) {
    Preconditions.checkArgument(
        structType instanceof RowType, "Invalid struct: %s is not a struct", structType);
    RowType.RowField field = ((RowType) structType).getFields().get(pos);
    return Pair.of(field.getName(), field.getType());
  }

  /** 返回 Flink 的 NullType 实例。 */
  @Override
  protected LogicalType nullType() {
    return new NullType();
  }
}
