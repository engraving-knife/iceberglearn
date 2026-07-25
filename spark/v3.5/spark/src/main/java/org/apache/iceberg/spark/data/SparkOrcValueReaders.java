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

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import org.apache.iceberg.orc.OrcValueReader;
import org.apache.iceberg.orc.OrcValueReaders;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.types.Types;
import org.apache.iceberg.util.UUIDUtil;
import org.apache.orc.storage.ql.exec.vector.BytesColumnVector;
import org.apache.orc.storage.ql.exec.vector.ColumnVector;
import org.apache.orc.storage.ql.exec.vector.DecimalColumnVector;
import org.apache.orc.storage.ql.exec.vector.ListColumnVector;
import org.apache.orc.storage.ql.exec.vector.MapColumnVector;
import org.apache.orc.storage.ql.exec.vector.TimestampColumnVector;
import org.apache.orc.storage.serde2.io.HiveDecimalWritable;
import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.catalyst.expressions.GenericInternalRow;
import org.apache.spark.sql.catalyst.util.ArrayBasedMapData;
import org.apache.spark.sql.catalyst.util.ArrayData;
import org.apache.spark.sql.catalyst.util.GenericArrayData;
import org.apache.spark.sql.catalyst.util.MapData;
import org.apache.spark.sql.types.Decimal;
import org.apache.spark.unsafe.types.UTF8String;

/**
 * Spark 专用的 ORC 值读取器工厂与内部读取器实现集合。
 *
 * <p>所属模块：iceberg-spark（Iceberg 与 Spark 3.5 的集成层，data 子包负责 ORC 格式 数据到 Spark 内部类型的读取转换）。
 *
 * <p>职责：为 ORC 格式的各种数据类型（字符串、UUID、时间戳、Decimal、struct、array、map） 提供 {@link OrcValueReader} 实现，将 ORC 的
 * ColumnVector 数据转换为 Spark 的 InternalRow/ArrayData/MapData/UTF8String/Decimal 等内部表示。
 *
 * <p>设计意图：将 ORC 读取器按类型拆分为独立的内部类（StringReader、UUIDReader、
 * TimestampTzReader、Decimal18Reader、Decimal38Reader、StructReader、ArrayReader、MapReader），
 * 每个类只负责一种类型的读取逻辑。工厂方法对外提供统一入口。Decimal 根据 precision 分为 18 位（long 存储）和 38 位（BigDecimal 存储）两种读取器。
 *
 * <p>上下游关系：被 {@link VectorizedSparkOrcReaders} 和非向量化 ORC 读取路径调用； 依赖 Iceberg ORC 模块的 OrcValueReader
 * 接口和 Hive ORC 的 ColumnVector。
 */
public class SparkOrcValueReaders {
  private SparkOrcValueReaders() {}

  /**
   * 创建 UTF8 字符串读取器（单例）。
   *
   * @return 将 ORC BytesColumnVector 转换为 Spark UTF8String 的读取器
   */
  public static OrcValueReader<UTF8String> utf8String() {
    return StringReader.INSTANCE;
  }

  /**
   * 创建 UUID 读取器（单例），将 ORC 中的 16 字节二进制转换为 UUID 字符串。
   *
   * @return UUID 读取器
   */
  public static OrcValueReader<UTF8String> uuids() {
    return UUIDReader.INSTANCE;
  }

  /**
   * 创建带时区的时间戳读取器（单例），将 ORC TimestampColumnVector 转换为微秒级 Long。
   *
   * @return 时间戳读取器
   */
  public static OrcValueReader<Long> timestampTzs() {
    return TimestampTzReader.INSTANCE;
  }

  /**
   * 根据 precision 选择合适的 Decimal 读取器。
   *
   * <p>逻辑：precision 不超过 Spark Decimal 的 MAX_LONG_DIGITS（18）时使用 Decimal18Reader（基于 long 存储），否则不超过 38
   * 时使用 Decimal38Reader （基于 BigDecimal 存储），超过 38 则报错。
   *
   * @param precision Decimal 精度
   * @param scale Decimal 标度
   * @return Decimal 读取器
   * @throws IllegalArgumentException precision 超过 38 时抛出
   */
  public static OrcValueReader<Decimal> decimals(int precision, int scale) {
    if (precision <= Decimal.MAX_LONG_DIGITS()) {
      return new SparkOrcValueReaders.Decimal18Reader(precision, scale);
    } else if (precision <= 38) {
      return new SparkOrcValueReaders.Decimal38Reader(precision, scale);
    } else {
      throw new IllegalArgumentException("Invalid precision: " + precision);
    }
  }
  /** 执行 struct 相关操作。 */
  static OrcValueReader<?> struct(
      List<OrcValueReader<?>> readers, Types.StructType struct, Map<Integer, ?> idToConstant) {
    return new StructReader(readers, struct, idToConstant);
  }
  /** 执行 array 相关操作。 */
  static OrcValueReader<?> array(OrcValueReader<?> elementReader) {
    return new ArrayReader(elementReader);
  }
  /** 执行 map 相关操作。 */
  static OrcValueReader<?> map(OrcValueReader<?> keyReader, OrcValueReader<?> valueReader) {
    return new MapReader(keyReader, valueReader);
  }

  private static class ArrayReader implements OrcValueReader<ArrayData> {
    private final OrcValueReader<?> elementReader;

    private ArrayReader(OrcValueReader<?> elementReader) {
      this.elementReader = elementReader;
    }
    /** 执行 nonNullRead 相关操作。 */
    @Override
    public ArrayData nonNullRead(ColumnVector vector, int row) {
      ListColumnVector listVector = (ListColumnVector) vector;
      int offset = (int) listVector.offsets[row];
      int length = (int) listVector.lengths[row];
      List<Object> elements = Lists.newArrayListWithExpectedSize(length);
      for (int c = 0; c < length; ++c) {
        elements.add(elementReader.read(listVector.child, offset + c));
      }
      return new GenericArrayData(elements.toArray());
    }
    /** 设置 BatchContext 属性。 */
    @Override
    public void setBatchContext(long batchOffsetInFile) {
      elementReader.setBatchContext(batchOffsetInFile);
    }
  }

  private static class MapReader implements OrcValueReader<MapData> {
    private final OrcValueReader<?> keyReader;
    private final OrcValueReader<?> valueReader;

    private MapReader(OrcValueReader<?> keyReader, OrcValueReader<?> valueReader) {
      this.keyReader = keyReader;
      this.valueReader = valueReader;
    }
    /** 执行 nonNullRead 相关操作。 */
    @Override
    public MapData nonNullRead(ColumnVector vector, int row) {
      MapColumnVector mapVector = (MapColumnVector) vector;
      int offset = (int) mapVector.offsets[row];
      long length = mapVector.lengths[row];
      List<Object> keys = Lists.newArrayListWithExpectedSize((int) length);
      List<Object> values = Lists.newArrayListWithExpectedSize((int) length);
      for (int c = 0; c < length; c++) {
        keys.add(keyReader.read(mapVector.keys, offset + c));
        values.add(valueReader.read(mapVector.values, offset + c));
      }

      return new ArrayBasedMapData(
          new GenericArrayData(keys.toArray()), new GenericArrayData(values.toArray()));
    }
    /** 设置 BatchContext 属性。 */
    @Override
    public void setBatchContext(long batchOffsetInFile) {
      keyReader.setBatchContext(batchOffsetInFile);
      valueReader.setBatchContext(batchOffsetInFile);
    }
  }

  static class StructReader extends OrcValueReaders.StructReader<InternalRow> {
    private final int numFields;

    protected StructReader(
        List<OrcValueReader<?>> readers, Types.StructType struct, Map<Integer, ?> idToConstant) {
      super(readers, struct, idToConstant);
      this.numFields = struct.fields().size();
    }
    /** 创建实例。 */
    @Override
    protected InternalRow create() {
      return new GenericInternalRow(numFields);
    }
    /** 执行 set 相关操作。 */
    @Override
    protected void set(InternalRow struct, int pos, Object value) {
      if (value != null) {
        struct.update(pos, value);
      } else {
        struct.setNullAt(pos);
      }
    }
  }

  private static class StringReader implements OrcValueReader<UTF8String> {
    private static final StringReader INSTANCE = new StringReader();

    private StringReader() {}
    /** 执行 nonNullRead 相关操作。 */
    @Override
    public UTF8String nonNullRead(ColumnVector vector, int row) {
      BytesColumnVector bytesVector = (BytesColumnVector) vector;
      return UTF8String.fromBytes(
          bytesVector.vector[row], bytesVector.start[row], bytesVector.length[row]);
    }
  }

  private static class UUIDReader implements OrcValueReader<UTF8String> {
    private static final UUIDReader INSTANCE = new UUIDReader();

    private UUIDReader() {}
    /** 执行 nonNullRead 相关操作。 */
    @Override
    public UTF8String nonNullRead(ColumnVector vector, int row) {
      BytesColumnVector bytesVector = (BytesColumnVector) vector;
      ByteBuffer buffer =
          ByteBuffer.wrap(bytesVector.vector[row], bytesVector.start[row], bytesVector.length[row]);
      return UTF8String.fromString(UUIDUtil.convert(buffer).toString());
    }
  }

  private static class TimestampTzReader implements OrcValueReader<Long> {
    private static final TimestampTzReader INSTANCE = new TimestampTzReader();

    private TimestampTzReader() {}
    /** 执行 nonNullRead 相关操作。 */
    @Override
    public Long nonNullRead(ColumnVector vector, int row) {
      TimestampColumnVector tcv = (TimestampColumnVector) vector;
      return Math.floorDiv(tcv.time[row], 1_000) * 1_000_000 + Math.floorDiv(tcv.nanos[row], 1000);
    }
  }

  private static class Decimal18Reader implements OrcValueReader<Decimal> {
    private final int precision;
    private final int scale;

    Decimal18Reader(int precision, int scale) {
      this.precision = precision;
      this.scale = scale;
    }
    /** 执行 nonNullRead 相关操作。 */
    @Override
    public Decimal nonNullRead(ColumnVector vector, int row) {
      HiveDecimalWritable value = ((DecimalColumnVector) vector).vector[row];

      // The scale of decimal read from hive ORC file may be not equals to the expected scale. For
      // data type
      // decimal(10,3) and the value 10.100, the hive ORC writer will remove its trailing zero and
      // store it
      // as 101*10^(-1), its scale will adjust from 3 to 1. So here we could not assert that
      // value.scale() == scale.
      // we also need to convert the hive orc decimal to a decimal with expected precision and
      // scale.
      Preconditions.checkArgument(
          value.precision() <= precision,
          "Cannot read value as decimal(%s,%s), too large: %s",
          precision,
          scale,
          value);

      return new Decimal().set(value.serialize64(scale), precision, scale);
    }
  }

  private static class Decimal38Reader implements OrcValueReader<Decimal> {
    private final int precision;
    private final int scale;

    Decimal38Reader(int precision, int scale) {
      this.precision = precision;
      this.scale = scale;
    }
    /** 执行 nonNullRead 相关操作。 */
    @Override
    public Decimal nonNullRead(ColumnVector vector, int row) {
      BigDecimal value =
          ((DecimalColumnVector) vector).vector[row].getHiveDecimal().bigDecimalValue();

      Preconditions.checkArgument(
          value.precision() <= precision,
          "Cannot read value as decimal(%s,%s), too large: %s",
          precision,
          scale,
          value);

      return new Decimal().set(new scala.math.BigDecimal(value), precision, scale);
    }
  }
}
