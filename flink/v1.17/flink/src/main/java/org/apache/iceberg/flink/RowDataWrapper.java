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

import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import org.apache.flink.table.data.RowData;
import org.apache.flink.table.data.TimestampData;
import org.apache.flink.table.types.logical.DecimalType;
import org.apache.flink.table.types.logical.LocalZonedTimestampType;
import org.apache.flink.table.types.logical.LogicalType;
import org.apache.flink.table.types.logical.RowType;
import org.apache.flink.table.types.logical.TimestampType;
import org.apache.iceberg.StructLike;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;
import org.apache.iceberg.util.DateTimeUtil;
import org.apache.iceberg.util.UUIDUtil;

/**
 * 将 Flink {@link RowData} 适配为 Iceberg {@link StructLike} 的包装器。
 *
 * <p>所属模块：iceberg-flink，用于让 Iceberg 的表达式求值/比较等逻辑能直接消费 Flink 行数据。
 *
 * <p>职责：按字段类型预建取值器（{@link PositionalGetter}），在 {@link #get(int, Class)} 时 将 Flink 类型值转换为 Iceberg
 * 期望的 Java 类型（如时间毫秒转微秒、时间戳转 micros 等）。
 *
 * <p>设计意图：构造期一次性建立 getter 数组避免运行时反射；对 Iceberg 与 Flink 表示不同的类型 （TIME/TIMESTAMP/DECIMAL/UUID
 * 等）做专门转换，其余直接复用 Flink 的 FieldGetter。
 *
 * <p>上下游关系：被 {@link FlinkSourceFilter} 及需要把 RowData 当 StructLike 使用的场景调用。
 */
public class RowDataWrapper implements StructLike {

  private final LogicalType[] types;
  private final PositionalGetter<?>[] getters;
  private RowData rowData = null;

  /**
   * 构造包装器。
   *
   * <p>逻辑：按字段数为 types/getters 分配数组，逐字段记录 Flink LogicalType 并构建对应的取值器。
   *
   * @param rowType Flink RowType
   * @param struct Iceberg struct 类型
   */
  public RowDataWrapper(RowType rowType, Types.StructType struct) {
    int size = rowType.getFieldCount();

    types = (LogicalType[]) Array.newInstance(LogicalType.class, size);
    getters = (PositionalGetter[]) Array.newInstance(PositionalGetter.class, size);

    for (int i = 0; i < size; i++) {
      types[i] = rowType.getTypeAt(i);
      getters[i] = buildGetter(types[i], struct.fields().get(i).type());
    }
  }

  /**
   * 绑定底层 RowData 并返回自身，便于复用包装器实例。
   *
   * @param data 底层行数据
   * @return 当前对象
   */
  public RowDataWrapper wrap(RowData data) {
    this.rowData = data;
    return this;
  }

  /** 返回字段数量。 */
  @Override
  public int size() {
    return types.length;
  }

  /**
   * 读取指定位置字段的值并转换为期望的 Java 类型。
   *
   * <p>逻辑：若该位置为 null 直接返回 null；若有专用 getter 则调用之；否则回退到 Flink 通用 FieldGetter 取值。结果按 javaClass 强转返回。
   *
   * @param pos 字段位置
   * @param javaClass 期望的 Java 类型
   * @param <T> Java 类型
   * @return 字段值
   */
  @Override
  public <T> T get(int pos, Class<T> javaClass) {
    if (rowData.isNullAt(pos)) {
      return null;
    } else if (getters[pos] != null) {
      return javaClass.cast(getters[pos].get(rowData, pos));
    }

    Object value = RowData.createFieldGetter(types[pos], pos).getFieldOrNull(rowData);
    return javaClass.cast(value);
  }

  /** 不支持设置字段，因为底层 RowData 只读。 */
  @Override
  public <T> void set(int pos, T value) {
    throw new UnsupportedOperationException(
        "Could not set a field in the RowDataWrapper because rowData is read-only");
  }

  /** 按位置从 RowData 取值的策略接口，用于为不同类型定制取值逻辑。 */
  private interface PositionalGetter<T> {
    T get(RowData data, int pos);
  }

  /**
   * 根据 Flink LogicalType（结合 Iceberg Type）构建专用取值器。
   *
   * <p>逻辑：按 typeRoot 分发——TINYINT/SMALLINT 提升为 int；CHAR/VARCHAR 取字符串； BINARY/VARBINARY 区分 UUID
   * 与普通字节；DECIMAL 转 BigDecimal； TIME 将毫秒转微秒；TIMESTAMP/TIMESTAMP_WITH_LOCAL_TIME_ZONE 转为 micros； ROW
   * 递归构造嵌套 RowDataWrapper；其余返回 null（回退到通用 getter）。
   *
   * @param logicalType Flink 逻辑类型
   * @param type Iceberg 类型
   * @return 专用取值器，无专用时返回 null
   */
  private static PositionalGetter<?> buildGetter(LogicalType logicalType, Type type) {
    switch (logicalType.getTypeRoot()) {
      case TINYINT:
        return (row, pos) -> (int) row.getByte(pos);
      case SMALLINT:
        return (row, pos) -> (int) row.getShort(pos);
      case CHAR:
      case VARCHAR:
        return (row, pos) -> row.getString(pos).toString();

      case BINARY:
      case VARBINARY:
        if (Type.TypeID.UUID == type.typeId()) {
          return (row, pos) -> UUIDUtil.convert(row.getBinary(pos));
        } else {
          return (row, pos) -> ByteBuffer.wrap(row.getBinary(pos));
        }

      case DECIMAL:
        DecimalType decimalType = (DecimalType) logicalType;
        return (row, pos) ->
            row.getDecimal(pos, decimalType.getPrecision(), decimalType.getScale()).toBigDecimal();

      case TIME_WITHOUT_TIME_ZONE:
        // Time in RowData is in milliseconds (Integer), while iceberg's time is microseconds
        // (Long).
        return (row, pos) -> ((long) row.getInt(pos)) * 1_000;

      case TIMESTAMP_WITHOUT_TIME_ZONE:
        TimestampType timestampType = (TimestampType) logicalType;
        return (row, pos) -> {
          LocalDateTime localDateTime =
              row.getTimestamp(pos, timestampType.getPrecision()).toLocalDateTime();
          return DateTimeUtil.microsFromTimestamp(localDateTime);
        };

      case TIMESTAMP_WITH_LOCAL_TIME_ZONE:
        LocalZonedTimestampType lzTs = (LocalZonedTimestampType) logicalType;
        return (row, pos) -> {
          TimestampData timestampData = row.getTimestamp(pos, lzTs.getPrecision());
          return timestampData.getMillisecond() * 1000
              + timestampData.getNanoOfMillisecond() / 1000;
        };

      case ROW:
        RowType rowType = (RowType) logicalType;
        Types.StructType structType = (Types.StructType) type;

        RowDataWrapper nestedWrapper = new RowDataWrapper(rowType, structType);
        return (row, pos) -> nestedWrapper.wrap(row.getRow(pos, rowType.getFieldCount()));

      default:
        return null;
    }
  }
}
