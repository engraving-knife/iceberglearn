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
 * Iceberg Spark 集成相关组件，负责类型或表达式转换。
 *
 * <p>所属模块：iceberg-spark v3.3。 类型：类 SparkValueConverter。
 *
 * <p>设计意图：适配器模式，桥接两套 API。
 */
public class SparkValueConverter {

  /** 构造 SparkValueConverter 实例。 */
  private SparkValueConverter() {}

  /** 把输入转换为另一种表示。 */
  public static Record convert(Schema schema, Row row) {
    return convert(schema.asStruct(), row);
  }

  /** 把输入转换为另一种表示。 */
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
        // if spark.sql.datetime.java8API.enabled is set to true, java.time.Instant
        // for Spark SQL TIMESTAMP type is returned otherwise java.sql.Timestamp is returned.
        return DateTimeUtils.anyToMicros(object);
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

  /** 把输入转换为另一种表示。 */
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

  /** 把输入转换为另一种表示。 */
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
