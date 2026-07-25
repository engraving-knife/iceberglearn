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
 * 文件级说明：Flink {@link RowData} 与 Iceberg 数据类型之间的转换工具类。
 *
 * <p>所属模块：iceberg-flink（数据写入子包 data），提供 RowData 相关的静态工具方法。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>将 Iceberg 常量值转换为 Flink 对应类型（如 BigDecimal → DecimalData）。
 *   <li>提供 RowData 的克隆方法，支持带额外列（position deletes）的行复制。
 * </ul>
 *
 * <p>设计意图：Flink 内部使用紧凑二进制类型（StringData、DecimalData、TimestampData 等）， Iceberg 使用标准 Java
 * 类型。本类封装两者之间的转换，使 Iceberg 的常量可直接用于 Flink 计算。
 *
 * <p>上下游关系：被 Iceberg Flink sink/source 中需要转换常量或复制行的场景调用。
 */
public class RowDataUtil {

  private RowDataUtil() {}

  /**
   * 将 Iceberg 类型的常量值转换为 Flink 对应类型。
   *
   * <p>逻辑：按 Iceberg type ID 分支：
   *
   * <ul>
   *   <li>DECIMAL → {@link DecimalData}
   *   <li>STRING → {@link StringData}（支持 Avro Utf8 输入）
   *   <li>FIXED/BINARY → byte[]
   *   <li>TIME → int（毫秒，Iceberg 存微秒故除以 1000）
   *   <li>TIMESTAMP → {@link TimestampData}
   * </ul>
   *
   * @param type Iceberg 类型
   * @param value 常量值（Iceberg 标准类型）
   * @return 转换后的 Flink 类型值，若输入为 null 则返回 null
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
   * 克隆 RowData，类似 {@link RowDataSerializer#copyRowData(RowData, RowData)} 私有方法。
   *
   * <p>设计意图：跳过 rowType 与 from 的 arity 检查，因为 from RowData 可能包含额外的 position deletes 列。使用 {@link
   * RowDataSerializer#copy(RowData, RowData)} 会因 arity 不匹配而失败。
   *
   * <p>逻辑：若 reuse 是 GenericRowData 则复用，否则新建；按 rowType 的字段数逐字段复制， 使用各字段的 TypeSerializer 做深拷贝。
   *
   * @param from 源 RowData
   * @param reuse 可复用的 RowData（可为 null 或非 GenericRowData）
   * @param rowType 行类型
   * @param fieldSerializers 各字段的 TypeSerializer
   * @return 克隆后的 GenericRowData
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
