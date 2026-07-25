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
package org.apache.iceberg.spark;

import java.util.List;
import org.apache.iceberg.MetadataColumns;
import org.apache.iceberg.Schema;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.TypeUtil;
import org.apache.iceberg.types.Types;
import org.apache.spark.sql.types.ArrayType$;
import org.apache.spark.sql.types.BinaryType$;
import org.apache.spark.sql.types.BooleanType$;
import org.apache.spark.sql.types.DataType;
import org.apache.spark.sql.types.DateType$;
import org.apache.spark.sql.types.DecimalType$;
import org.apache.spark.sql.types.DoubleType$;
import org.apache.spark.sql.types.FloatType$;
import org.apache.spark.sql.types.IntegerType$;
import org.apache.spark.sql.types.LongType$;
import org.apache.spark.sql.types.MapType$;
import org.apache.spark.sql.types.Metadata;
import org.apache.spark.sql.types.MetadataBuilder;
import org.apache.spark.sql.types.StringType$;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType$;
import org.apache.spark.sql.types.TimestampNTZType$;
import org.apache.spark.sql.types.TimestampType$;

/**
 * Iceberg 类型 -> Spark {@link DataType} 的访问者转换器。
 *
 * <p>所属模块：iceberg-spark（Spark v3.5 集成模块），spark 顶级包。
 *
 * <p>职责：遍历 Iceberg Schema，把每种类型（struct/list/map/primitive）转换为对应的 Spark 类型， 并为元数据列在 StructField
 * metadata 中打上 {@link #METADATA_COL_ATTR_KEY} 标记。
 *
 * <p>设计意图：继承 {@link TypeUtil.SchemaVisitor} 复用遍历骨架，仅在各节点方法做类型映射； timestamp 按 shouldAdjustToUTC
 * 区分带时区（TimestampType）与无时区（TimestampNTZType）； UUID 映射为 StringType、FIXED 映射为 BinaryType（与 BINARY
 * 同），TIME 不支持。
 *
 * <p>上下游关系：被 {@link SparkSchemaUtil} 等用于 schema 转换；依赖 iceberg-core 的 TypeUtil。
 */
class TypeToSparkType extends TypeUtil.SchemaVisitor<DataType> {
  TypeToSparkType() {}

  public static final String METADATA_COL_ATTR_KEY = "__metadata_col";
  /** 返回 Schema。 */
  @Override
  public DataType schema(Schema schema, DataType structType) {
    return structType;
  }

  /**
   * 转换 struct 为 Spark StructType。
   *
   * <p>逻辑：逐字段构造 StructField，附带 fieldId 元数据与可选 doc 注释， 元数据列额外打上 __metadata_col 标记。
   */
  @Override
  public DataType struct(Types.StructType struct, List<DataType> fieldResults) {
    List<Types.NestedField> fields = struct.fields();

    List<StructField> sparkFields = Lists.newArrayListWithExpectedSize(fieldResults.size());
    for (int i = 0; i < fields.size(); i += 1) {
      Types.NestedField field = fields.get(i);
      DataType type = fieldResults.get(i);
      Metadata metadata = fieldMetadata(field.fieldId());
      StructField sparkField = StructField.apply(field.name(), type, field.isOptional(), metadata);
      if (field.doc() != null) {
        sparkField = sparkField.withComment(field.doc());
      }
      sparkFields.add(sparkField);
    }

    return StructType$.MODULE$.apply(sparkFields);
  }
  /** 执行 field 相关操作。 */
  @Override
  public DataType field(Types.NestedField field, DataType fieldResult) {
    return fieldResult;
  }

  /** 转换 list 为 Spark ArrayType，元素可空性与 Iceberg 一致。 */
  @Override
  public DataType list(Types.ListType list, DataType elementResult) {
    return ArrayType$.MODULE$.apply(elementResult, list.isElementOptional());
  }

  /** 转换 map 为 Spark MapType，value 可空性与 Iceberg 一致。 */
  @Override
  public DataType map(Types.MapType map, DataType keyResult, DataType valueResult) {
    return MapType$.MODULE$.apply(keyResult, valueResult, map.isValueOptional());
  }

  /**
   * 转换 primitive 为 Spark 类型。
   *
   * <p>逻辑：按 typeId 分支映射；TIMESTAMP 按 shouldAdjustToUTC 区分带/无时区； UUID -> StringType，FIXED/BINARY ->
   * BinaryType，TIME 抛出不支持，DECIMAL 保留精度与标度。
   */
  @Override
  public DataType primitive(Type.PrimitiveType primitive) {
    switch (primitive.typeId()) {
      case BOOLEAN:
        return BooleanType$.MODULE$;
      case INTEGER:
        return IntegerType$.MODULE$;
      case LONG:
        return LongType$.MODULE$;
      case FLOAT:
        return FloatType$.MODULE$;
      case DOUBLE:
        return DoubleType$.MODULE$;
      case DATE:
        return DateType$.MODULE$;
      case TIME:
        throw new UnsupportedOperationException("Spark does not support time fields");
      case TIMESTAMP:
        Types.TimestampType ts = (Types.TimestampType) primitive;
        if (ts.shouldAdjustToUTC()) {
          return TimestampType$.MODULE$;
        } else {
          return TimestampNTZType$.MODULE$;
        }
      case STRING:
        return StringType$.MODULE$;
      case UUID:
        // use String
        return StringType$.MODULE$;
      case FIXED:
        return BinaryType$.MODULE$;
      case BINARY:
        return BinaryType$.MODULE$;
      case DECIMAL:
        Types.DecimalType decimal = (Types.DecimalType) primitive;
        return DecimalType$.MODULE$.apply(decimal.precision(), decimal.scale());
      default:
        throw new UnsupportedOperationException(
            "Cannot convert unknown type to Spark: " + primitive);
    }
  }

  /** 为元数据列构造带 __metadata_col=true 标记的 Metadata，普通列返回空 Metadata。 */
  private Metadata fieldMetadata(int fieldId) {
    if (MetadataColumns.metadataFieldIds().contains(fieldId)) {
      return new MetadataBuilder().putBoolean(METADATA_COL_ATTR_KEY, true).build();
    }

    return Metadata.empty();
  }
}
