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
package org.apache.iceberg.spark.data;

import org.apache.iceberg.avro.AvroWithPartnerByStructureVisitor;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.util.Pair;
import org.apache.spark.sql.types.ArrayType;
import org.apache.spark.sql.types.DataType;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.MapType;
import org.apache.spark.sql.types.StringType;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;

/**
 * 以 Spark {@link DataType} 为「伙伴类型」的 Avro 结构化访问器基类。
 *
 * <p>所属模块：iceberg-spark（data 子包，负责 Iceberg 与 Spark 数据类型之间的读写转换）。
 *
 * <p>职责：在按结构遍历 Avro schema 与 Iceberg/Spark 类型时，提供对 Spark {@link DataType}
 * 的类型判定与拆解能力（是否字符串/Map、数组元素类型、Map 键值类型、 结构体字段名与类型、null 类型）。
 *
 * <p>设计意图：继承 {@link AvroWithPartnerByStructureVisitor}，将「如何识别与拆解伙伴类型」 这一与引擎相关的细节下放给子类实现，使 Avro
 * 读写逻辑与具体引擎解耦。 本类即 Spark 引擎针对 DataType 的实现，被 Avro 读取器复用以对齐 Spark 结构。
 *
 * @param <T> 访问器产生的读者/写者类型
 */
public abstract class AvroWithSparkSchemaVisitor<T>
    extends AvroWithPartnerByStructureVisitor<DataType, T> {

  /** 判断给定 Spark 类型是否为字符串类型。 */
  @Override
  protected boolean isStringType(DataType dataType) {
    return dataType instanceof StringType;
  }

  /** 判断给定 Spark 类型是否为 Map 类型。 */
  @Override
  protected boolean isMapType(DataType dataType) {
    return dataType instanceof MapType;
  }

  /**
   * 获取数组类型的元素类型。
   *
   * @throws IllegalArgumentException 当传入类型非 {@link ArrayType} 时抛出
   */
  @Override
  protected DataType arrayElementType(DataType arrayType) {
    Preconditions.checkArgument(
        arrayType instanceof ArrayType, "Invalid array: %s is not an array", arrayType);
    return ((ArrayType) arrayType).elementType();
  }

  /**
   * 获取 Map 类型的键类型。
   *
   * @throws IllegalArgumentException 当传入类型非 Map 时抛出
   */
  @Override
  protected DataType mapKeyType(DataType mapType) {
    Preconditions.checkArgument(isMapType(mapType), "Invalid map: %s is not a map", mapType);
    return ((MapType) mapType).keyType();
  }

  /**
   * 获取 Map 类型的值类型。
   *
   * @throws IllegalArgumentException 当传入类型非 Map 时抛出
   */
  @Override
  protected DataType mapValueType(DataType mapType) {
    Preconditions.checkArgument(isMapType(mapType), "Invalid map: %s is not a map", mapType);
    return ((MapType) mapType).valueType();
  }

  /**
   * 返回结构体指定位置字段的名称与类型。
   *
   * @param structType 结构体类型
   * @param pos 字段位置序号
   * @return 字段名与字段类型的 Pair
   * @throws IllegalArgumentException 当传入类型非 {@link StructType} 时抛出
   */
  @Override
  protected Pair<String, DataType> fieldNameAndType(DataType structType, int pos) {
    Preconditions.checkArgument(
        structType instanceof StructType, "Invalid struct: %s is not a struct", structType);
    StructField field = ((StructType) structType).apply(pos);
    return Pair.of(field.name(), field.dataType());
  }

  /** 返回 Spark 的 Null 类型。 */
  @Override
  protected DataType nullType() {
    return DataTypes.NullType;
  }
}
