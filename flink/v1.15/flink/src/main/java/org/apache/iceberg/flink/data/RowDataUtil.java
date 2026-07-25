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

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import org.apache.avro.generic.GenericData;
import org.apache.avro.util.Utf8;
import org.apache.flink.api.common.typeutils.TypeSerializer;
import org.apache.flink.table.data.DecimalData;
import org.apache.flink.table.data.GenericRowData;
import org.apache.flink.table.data.RowData;
import org.apache.flink.table.data.StringData;
import org.apache.flink.table.data.TimestampData;
import org.apache.flink.table.runtime.typeutils.RowDataSerializer;
import org.apache.flink.table.types.logical.RowType;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;
import org.apache.iceberg.util.ByteBuffers;
import org.apache.iceberg.util.DateTimeUtil;

/**
 * Flink RowData 相关的工具类。
 *
 * <p>所属模块：iceberg-flink v1.15。职责：提供常量值类型转换与行克隆工具方法， 把 Iceberg 内部 Java 对象转换为 Flink RowData 兼容类型。
 *
 * <p>设计意图：工具类 + 静态方法；上下游：被 Flink 读取/写入算子调用。
 */
public class RowDataUtil {

  private RowDataUtil() {}

  /**
   * 把 Iceberg 常量值转换为 Flink RowData 兼容类型。
   *
   * <p>逻辑：按 Iceberg 类型 ID 处理 decimal/string/fixed/binary/time/timestamp 等类型转换。
   */
  public static Object convertConstant(Type type, Object value) {
    if (value == null) {
      return null;
    }

    switch (type.typeId()) {
      case DECIMAL: // DecimalData
        Types.DecimalType decimal = (Types.DecimalType) type;
        return DecimalData.fromBigDecimal((BigDecimal) value, decimal.precision(), decimal.scale());
      case STRING: // StringData
        if (value instanceof Utf8) {
          Utf8 utf8 = (Utf8) value;
          return StringData.fromBytes(utf8.getBytes(), 0, utf8.getByteLength());
        }
        return StringData.fromString(value.toString());
      case FIXED: // byte[]
        if (value instanceof byte[]) {
          return value;
        } else if (value instanceof GenericData.Fixed) {
          return ((GenericData.Fixed) value).bytes();
        }
        return ByteBuffers.toByteArray((ByteBuffer) value);
      case BINARY: // byte[]
        return ByteBuffers.toByteArray((ByteBuffer) value);
      case TIME: // int mills instead of long
        return (int) ((Long) value / 1000);
      case TIMESTAMP: // TimestampData
        return TimestampData.fromLocalDateTime(DateTimeUtil.timestampFromMicros((Long) value));
      default:
    }
    return value;
  }

  /**
   * 克隆一行数据，跳过 arity 检查。
   *
   * <p>逻辑：类似 {@link RowDataSerializer#copyRowData(RowData, RowData)}， 但 from RowData 可能含额外的
   * position deletes 列，使用 {@link RowDataSerializer#copy(RowData, RowData)} 会因 arity 检查失败。
   */
  public static RowData clone(
      RowData from, RowData reuse, RowType rowType, TypeSerializer[] fieldSerializers) {
    GenericRowData ret;
    if (reuse instanceof GenericRowData) {
      ret = (GenericRowData) reuse;
    } else {
      ret = new GenericRowData(from.getArity());
    }
    ret.setRowKind(from.getRowKind());
    for (int i = 0; i < rowType.getFieldCount(); i++) {
      if (!from.isNullAt(i)) {
        RowData.FieldGetter getter = RowData.createFieldGetter(rowType.getTypeAt(i), i);
        ret.setField(i, fieldSerializers[i].copy(getter.getFieldOrNull(from)));
      } else {
        ret.setField(i, null);
      }
    }
    return ret;
  }
}
