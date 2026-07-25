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
 * 以 Flink {@link LogicalType} 为 partner 的 Avro Schema 访问器基类。
 *
 * <p>所属模块：iceberg-flink（Flink 集成层），位于 iceberg-core 的 avro 抽象访问器 {@link
 * AvroWithPartnerByStructureVisitor} 之上。
 *
 * <p>职责：将父类的抽象方法适配到 Flink 的 {@link LogicalType} 类型系统，使 Avro 读写器可基于 Flink 类型结构进行构建。
 *
 * <p>设计意图：采用模板方法模式，把"按结构遍历 partner schema"的通用流程放在父类，子类只需实现 具体类型读写；本类负责 Flink LogicalType 与 Iceberg
 * 类型结构（struct/map/array/string）的判定与拆解。
 *
 * <p>上下游关系：被 Flink Avro 读写器（如 FlinkAvroWriter/FlinkAvroReader 相关实现）继承使用。
 */
public abstract class AvroWithFlinkSchemaVisitor<T>
    extends AvroWithPartnerByStructureVisitor<LogicalType, T> {

  /**
   * 判断给定 Flink 逻辑类型是否属于字符串族（{@link LogicalTypeFamily#CHARACTER_STRING}）。
   *
   * @param logicalType Flink 逻辑类型
   * @return 若为字符串族返回 true
   */
  @Override
  protected boolean isStringType(LogicalType logicalType) {
    return logicalType.getTypeRoot().getFamilies().contains(LogicalTypeFamily.CHARACTER_STRING);
  }

  /**
   * 判断给定 Flink 逻辑类型是否为 {@link MapType}。
   *
   * @param logicalType Flink 逻辑类型
   * @return 若为 MapType 返回 true
   */
  @Override
  protected boolean isMapType(LogicalType logicalType) {
    return logicalType instanceof MapType;
  }

  /**
   * 返回 Flink {@link ArrayType} 的元素类型。
   *
   * @param arrayType 数组类型
   * @return 元素的 LogicalType
   */
  @Override
  protected LogicalType arrayElementType(LogicalType arrayType) {
    Preconditions.checkArgument(
        arrayType instanceof ArrayType, "Invalid array: %s is not an array", arrayType);
    return ((ArrayType) arrayType).getElementType();
  }

  /**
   * 返回 Flink {@link MapType} 的键类型。
   *
   * @param mapType map 类型
   * @return 键的 LogicalType
   */
  @Override
  protected LogicalType mapKeyType(LogicalType mapType) {
    Preconditions.checkArgument(isMapType(mapType), "Invalid map: %s is not a map", mapType);
    return ((MapType) mapType).getKeyType();
  }

  /**
   * 返回 Flink {@link MapType} 的值类型。
   *
   * @param mapType map 类型
   * @return 值的 LogicalType
   */
  @Override
  protected LogicalType mapValueType(LogicalType mapType) {
    Preconditions.checkArgument(isMapType(mapType), "Invalid map: %s is not a map", mapType);
    return ((MapType) mapType).getValueType();
  }

  /**
   * 返回 Flink {@link RowType} 中指定位置字段的名称与类型。
   *
   * @param structType 结构类型（须为 RowType）
   * @param pos 字段位置索引
   * @return 字段名与类型的 {@link Pair}
   */
  @Override
  protected Pair<String, LogicalType> fieldNameAndType(LogicalType structType, int pos) {
    Preconditions.checkArgument(
        structType instanceof RowType, "Invalid struct: %s is not a struct", structType);
    RowType.RowField field = ((RowType) structType).getFields().get(pos);
    return Pair.of(field.getName(), field.getType());
  }

  /**
   * 返回 Flink 表示空类型的 {@link NullType} 实例。
   *
   * @return NullType 实例
   */
  @Override
  protected LogicalType nullType() {
    return new NullType();
  }
}
