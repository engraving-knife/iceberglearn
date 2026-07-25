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
 * Iceberg 与 Spark 数据格式之间的读写转换组件。
 *
 * <p>所属模块：iceberg-spark v3.2。 类型：类 AvroWithSparkSchemaVisitor。
 *
 * <p>设计意图：访问者模式，按类型分派处理逻辑。
 *
 * <p>上下游：被 SparkScan/SparkWrite 调用，依赖 Iceberg 文件格式读取/写入 API。
 */
public abstract class AvroWithSparkSchemaVisitor<T>
    extends AvroWithPartnerByStructureVisitor<DataType, T> {

  /** 判断是否stringtype。 */
  @Override
  protected boolean isStringType(DataType dataType) {
    return dataType instanceof StringType;
  }

  /** 判断是否maptype。 */
  @Override
  protected boolean isMapType(DataType dataType) {
    return dataType instanceof MapType;
  }

  /** 执行该方法的具体逻辑。 */
  @Override
  protected DataType arrayElementType(DataType arrayType) {
    Preconditions.checkArgument(
        arrayType instanceof ArrayType, "Invalid array: %s is not an array", arrayType);
    return ((ArrayType) arrayType).elementType();
  }

  /** 执行该方法的具体逻辑。 */
  @Override
  protected DataType mapKeyType(DataType mapType) {
    Preconditions.checkArgument(isMapType(mapType), "Invalid map: %s is not a map", mapType);
    return ((MapType) mapType).keyType();
  }

  /** 执行该方法的具体逻辑。 */
  @Override
  protected DataType mapValueType(DataType mapType) {
    Preconditions.checkArgument(isMapType(mapType), "Invalid map: %s is not a map", mapType);
    return ((MapType) mapType).valueType();
  }

  /** 执行该方法的具体逻辑。 */
  @Override
  protected Pair<String, DataType> fieldNameAndType(DataType structType, int pos) {
    Preconditions.checkArgument(
        structType instanceof StructType, "Invalid struct: %s is not a struct", structType);
    StructField field = ((StructType) structType).apply(pos);
    return Pair.of(field.name(), field.dataType());
  }

  /** 执行该方法的具体逻辑。 */
  @Override
  protected DataType nullType() {
    return DataTypes.NullType;
  }
}
