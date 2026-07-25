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

import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.iceberg.Schema;
import org.apache.iceberg.data.GenericRecord;
import org.apache.iceberg.data.Record;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;
import org.apache.iceberg.util.ByteBuffers;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.catalyst.util.DateTimeUtils;

/**
 * 所属模块：iceberg-spark v3.4
 *
 * <p>职责：Spark 值转换器，在 Spark 内部值与 Iceberg 字面量之间转换。
 *
 * <p>设计意图：用于将 Spark 端常量值转为 Iceberg 表达式可识别的字面量。
 *
 * <p>上下游关系：由 SparkFilters / SparkV2Filters 等在转换谓词时调用。
 */
public class SparkValueConverter {

  private SparkValueConverter() {}
  /** 执行类型/值转换。 */
  public static Record convert(Schema schema, Row row) {
    return convert(schema.asStruct(), row);
  }
  /** 执行类型/值转换。 */
  public static Object convert(Type type, Object object) {
    if (object == null) {
      return null;
    }

    switch (type.typeId()) {
      case STRUCT:
        return convert(type.asStructType(), (Row) object);

      case LIST:
        List<Object> convertedList = Lists.newArrayList();
        List<?> list = (List<?>) object;
        for (Object element : list) {
          convertedList.add(convert(type.asListType().elementType(), element));
        }
        return convertedList;

      case MAP:
        Map<Object, Object> convertedMap = Maps.newLinkedHashMap();
        Map<?, ?> map = (Map<?, ?>) object;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
          convertedMap.put(
              convert(type.asMapType().keyType(), entry.getKey()),
              convert(type.asMapType().valueType(), entry.getValue()));
        }
        return convertedMap;

      case DATE:
        // if spark.sql.datetime.java8API.enabled is set to true, java.time.LocalDate
        // for Spark SQL DATE type otherwise java.sql.Date is returned.
        return DateTimeUtils.anyToDays(object);
      case TIMESTAMP:
        Types.TimestampType ts = (Types.TimestampType) type.asPrimitiveType();

        // This if/else can be removed once https://github.com/apache/spark/pull/41238 is in
        if (ts.shouldAdjustToUTC()) {
          // if spark.sql.datetime.java8API.enabled is set to true, java.time.Instant
          // for Spark SQL TIMESTAMP type is returned otherwise java.sql.Timestamp is returned.
          return DateTimeUtils.anyToMicros(object);
        } else {
          LocalDateTime ldt = (LocalDateTime) object;
          return DateTimeUtils.localDateTimeToMicros(ldt);
        }
      case BINARY:
        return ByteBuffer.wrap((byte[]) object);
      case INTEGER:
        return ((Number) object).intValue();
      case BOOLEAN:
      case LONG:
      case FLOAT:
      case DOUBLE:
      case DECIMAL:
      case STRING:
      case FIXED:
        return object;
      default:
        throw new UnsupportedOperationException("Not a supported type: " + type);
    }
  }
  /** 执行类型/值转换。 */
  private static Record convert(Types.StructType struct, Row row) {
    if (row == null) {
      return null;
    }

    Record record = GenericRecord.create(struct);
    List<Types.NestedField> fields = struct.fields();
    for (int i = 0; i < fields.size(); i += 1) {
      Types.NestedField field = fields.get(i);

      Type fieldType = field.type();

      switch (fieldType.typeId()) {
        case STRUCT:
          record.set(i, convert(fieldType.asStructType(), row.getStruct(i)));
          break;
        case LIST:
          record.set(i, convert(fieldType.asListType(), row.getList(i)));
          break;
        case MAP:
          record.set(i, convert(fieldType.asMapType(), row.getJavaMap(i)));
          break;
        default:
          record.set(i, convert(fieldType, row.get(i)));
      }
    }
    return record;
  }
  /** 执行 convertToSpark 相关操作。 */
  public static Object convertToSpark(Type type, Object object) {
    if (object == null) {
      return null;
    }

    switch (type.typeId()) {
      case STRUCT:
      case LIST:
      case MAP:
        return new UnsupportedOperationException("Complex types currently not supported");
      case DATE:
        return DateTimeUtils.daysToLocalDate((int) object);
      case TIMESTAMP:
        Types.TimestampType ts = (Types.TimestampType) type.asPrimitiveType();
        if (ts.shouldAdjustToUTC()) {
          return DateTimeUtils.microsToInstant((long) object);
        } else {
          return DateTimeUtils.microsToLocalDateTime((long) object);
        }
      case BINARY:
        return ByteBuffers.toByteArray((ByteBuffer) object);
      case INTEGER:
      case BOOLEAN:
      case LONG:
      case FLOAT:
      case DOUBLE:
      case DECIMAL:
      case STRING:
      case FIXED:
        return object;
      default:
        throw new UnsupportedOperationException("Not a supported type: " + type);
    }
  }
}
