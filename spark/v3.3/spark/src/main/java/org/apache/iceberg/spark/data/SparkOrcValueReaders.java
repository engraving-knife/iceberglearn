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
 * Iceberg 与 Spark 数据格式之间的读写转换组件的读取器，负责从底层读取数据并转换为 Spark 内部格式。
 *
 * <p>所属模块：iceberg-spark v3.3。 类型：类 SparkOrcValueReaders。
 *
 * <p>上下游：被 SparkScan/SparkWrite 调用，依赖 Iceberg 文件格式读取/写入 API。
 */
public class SparkOrcValueReaders {
  /** 构造 SparkOrcValueReaders 实例。 */
  private SparkOrcValueReaders() {}

  /** 执行该方法的具体逻辑。 */
  public static OrcValueReader<UTF8String> utf8String() {
    return StringReader.INSTANCE;
  }

  /** 执行该方法的具体逻辑。 */
  public static OrcValueReader<UTF8String> uuids() {
    return UUIDReader.INSTANCE;
  }

  /** 执行该方法的具体逻辑。 */
  public static OrcValueReader<Long> timestampTzs() {
    return TimestampTzReader.INSTANCE;
  }

  /** 执行该方法的具体逻辑。 */
  public static OrcValueReader<Decimal> decimals(int precision, int scale) {
    if (precision <= Decimal.MAX_LONG_DIGITS()) {
      return new SparkOrcValueReaders.Decimal18Reader(precision, scale);
    } else if (precision <= 38) {
      return new SparkOrcValueReaders.Decimal38Reader(precision, scale);
    } else {
      throw new IllegalArgumentException("Invalid precision: " + precision);
    }
  }

  /** 执行该方法的具体逻辑。 */
  static OrcValueReader<?> struct(
      List<OrcValueReader<?>> readers, Types.StructType struct, Map<Integer, ?> idToConstant) {
    /** 执行该方法的具体逻辑。 */
    return new StructReader(readers, struct, idToConstant);
  }

  /** 执行该方法的具体逻辑。 */
  static OrcValueReader<?> array(OrcValueReader<?> elementReader) {
    /** 执行该方法的具体逻辑。 */
    return new ArrayReader(elementReader);
  }

  /** 执行该方法的具体逻辑。 */
  static OrcValueReader<?> map(OrcValueReader<?> keyReader, OrcValueReader<?> valueReader) {
    /** 执行该方法的具体逻辑。 */
    return new MapReader(keyReader, valueReader);
  }

  /**
   * Iceberg 与 Spark 数据格式之间的读写转换组件的读取器，负责从底层读取数据并转换为 Spark 内部格式。
   *
   * <p>所属模块：iceberg-spark v3.3。 类型：类 ArrayReader。
   *
   * <p>上下游：被 SparkScan/SparkWrite 调用，依赖 Iceberg 文件格式读取/写入 API。
   */
  private static class ArrayReader implements OrcValueReader<ArrayData> {
    private final OrcValueReader<?> elementReader;

    /** 构造 ArrayReader 实例。 */
    private ArrayReader(OrcValueReader<?> elementReader) {
      this.elementReader = elementReader;
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @param vector 参数
     * @param row 参数
     * @return 结果对象
     */
    @Override
    public ArrayData nonNullRead(ColumnVector vector, int row) {
      ListColumnVector listVector = (ListColumnVector) vector;
      int offset = (int) listVector.offsets[row];
      int length = (int) listVector.lengths[row];
      List<Object> elements = Lists.newArrayListWithExpectedSize(length);
      for (int c = 0; c < length; ++c) {
        elements.add(elementReader.read(listVector.child, offset + c));
      }
      /** 执行该方法的具体逻辑。 */
      return new GenericArrayData(elements.toArray());
    }

    /** 设置batchcontext。 */
    @Override
    public void setBatchContext(long batchOffsetInFile) {
      elementReader.setBatchContext(batchOffsetInFile);
    }
  }

  /**
   * Iceberg 与 Spark 数据格式之间的读写转换组件的读取器，负责从底层读取数据并转换为 Spark 内部格式。
   *
   * <p>所属模块：iceberg-spark v3.3。 类型：类 MapReader。
   *
   * <p>上下游：被 SparkScan/SparkWrite 调用，依赖 Iceberg 文件格式读取/写入 API。
   */
  private static class MapReader implements OrcValueReader<MapData> {
    private final OrcValueReader<?> keyReader;
    private final OrcValueReader<?> valueReader;

    /** 构造 MapReader 实例。 */
    private MapReader(OrcValueReader<?> keyReader, OrcValueReader<?> valueReader) {
      this.keyReader = keyReader;
      this.valueReader = valueReader;
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @param vector 参数
     * @param row 参数
     * @return 结果对象
     */
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

      /** 执行该方法的具体逻辑。 */
      return new ArrayBasedMapData(
          new GenericArrayData(keys.toArray()), new GenericArrayData(values.toArray()));
    }

    /** 设置batchcontext。 */
    @Override
    public void setBatchContext(long batchOffsetInFile) {
      keyReader.setBatchContext(batchOffsetInFile);
      valueReader.setBatchContext(batchOffsetInFile);
    }
  }

  /**
   * Iceberg 与 Spark 数据格式之间的读写转换组件的读取器，负责从底层读取数据并转换为 Spark 内部格式。
   *
   * <p>所属模块：iceberg-spark v3.3。 类型：类 StructReader。
   *
   * <p>上下游：被 SparkScan/SparkWrite 调用，依赖 Iceberg 文件格式读取/写入 API。
   */
  static class StructReader extends OrcValueReaders.StructReader<InternalRow> {
    private final int numFields;

    /** 构造 StructReader 实例。 */
    protected StructReader(
        List<OrcValueReader<?>> readers, Types.StructType struct, Map<Integer, ?> idToConstant) {
      super(readers, struct, idToConstant);
      this.numFields = struct.fields().size();
    }

    /** 创建并返回新实例。 */
    @Override
    protected InternalRow create() {
      /** 执行该方法的具体逻辑。 */
      return new GenericInternalRow(numFields);
    }

    /** 执行该方法的具体逻辑。 */
    @Override
    protected void set(InternalRow struct, int pos, Object value) {
      if (value != null) {
        struct.update(pos, value);
      } else {
        struct.setNullAt(pos);
      }
    }
  }

  /**
   * Iceberg 与 Spark 数据格式之间的读写转换组件的读取器，负责从底层读取数据并转换为 Spark 内部格式。
   *
   * <p>所属模块：iceberg-spark v3.3。 类型：类 StringReader。
   *
   * <p>上下游：被 SparkScan/SparkWrite 调用，依赖 Iceberg 文件格式读取/写入 API。
   */
  private static class StringReader implements OrcValueReader<UTF8String> {
    private static final StringReader INSTANCE = new StringReader();

    /** 构造 StringReader 实例。 */
    private StringReader() {}

    /**
     * 执行该方法的具体逻辑。
     *
     * @param vector 参数
     * @param row 参数
     * @return 结果对象
     */
    @Override
    public UTF8String nonNullRead(ColumnVector vector, int row) {
      BytesColumnVector bytesVector = (BytesColumnVector) vector;
      return UTF8String.fromBytes(
          bytesVector.vector[row], bytesVector.start[row], bytesVector.length[row]);
    }
  }

  /**
   * Iceberg 与 Spark 数据格式之间的读写转换组件的读取器，负责从底层读取数据并转换为 Spark 内部格式。
   *
   * <p>所属模块：iceberg-spark v3.3。 类型：类 UUIDReader。
   *
   * <p>上下游：被 SparkScan/SparkWrite 调用，依赖 Iceberg 文件格式读取/写入 API。
   */
  private static class UUIDReader implements OrcValueReader<UTF8String> {
    private static final UUIDReader INSTANCE = new UUIDReader();

    /** 构造 UUIDReader 实例。 */
    private UUIDReader() {}

    /**
     * 执行该方法的具体逻辑。
     *
     * @param vector 参数
     * @param row 参数
     * @return 结果对象
     */
    @Override
    public UTF8String nonNullRead(ColumnVector vector, int row) {
      BytesColumnVector bytesVector = (BytesColumnVector) vector;
      ByteBuffer buffer =
          ByteBuffer.wrap(bytesVector.vector[row], bytesVector.start[row], bytesVector.length[row]);
      return UTF8String.fromString(UUIDUtil.convert(buffer).toString());
    }
  }

  /**
   * Iceberg 与 Spark 数据格式之间的读写转换组件的读取器，负责从底层读取数据并转换为 Spark 内部格式。
   *
   * <p>所属模块：iceberg-spark v3.3。 类型：类 TimestampTzReader。
   *
   * <p>上下游：被 SparkScan/SparkWrite 调用，依赖 Iceberg 文件格式读取/写入 API。
   */
  private static class TimestampTzReader implements OrcValueReader<Long> {
    private static final TimestampTzReader INSTANCE = new TimestampTzReader();

    /** 构造 TimestampTzReader 实例。 */
    private TimestampTzReader() {}

    /**
     * 执行该方法的具体逻辑。
     *
     * @param vector 参数
     * @param row 参数
     * @return 结果对象
     */
    @Override
    public Long nonNullRead(ColumnVector vector, int row) {
      TimestampColumnVector tcv = (TimestampColumnVector) vector;
      return Math.floorDiv(tcv.time[row], 1_000) * 1_000_000 + Math.floorDiv(tcv.nanos[row], 1000);
    }
  }

  /**
   * Iceberg 与 Spark 数据格式之间的读写转换组件的读取器，负责从底层读取数据并转换为 Spark 内部格式。
   *
   * <p>所属模块：iceberg-spark v3.3。 类型：类 Decimal18Reader。
   *
   * <p>上下游：被 SparkScan/SparkWrite 调用，依赖 Iceberg 文件格式读取/写入 API。
   */
  private static class Decimal18Reader implements OrcValueReader<Decimal> {
    private final int precision;
    private final int scale;

    Decimal18Reader(int precision, int scale) {
      this.precision = precision;
      this.scale = scale;
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @param vector 参数
     * @param row 参数
     * @return 结果对象
     */
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

      /** 执行该方法的具体逻辑。 */
      return new Decimal().set(value.serialize64(scale), precision, scale);
    }
  }

  /**
   * Iceberg 与 Spark 数据格式之间的读写转换组件的读取器，负责从底层读取数据并转换为 Spark 内部格式。
   *
   * <p>所属模块：iceberg-spark v3.3。 类型：类 Decimal38Reader。
   *
   * <p>上下游：被 SparkScan/SparkWrite 调用，依赖 Iceberg 文件格式读取/写入 API。
   */
  private static class Decimal38Reader implements OrcValueReader<Decimal> {
    private final int precision;
    private final int scale;

    Decimal38Reader(int precision, int scale) {
      this.precision = precision;
      this.scale = scale;
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @param vector 参数
     * @param row 参数
     * @return 结果对象
     */
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

      /** 执行该方法的具体逻辑。 */
      return new Decimal().set(new scala.math.BigDecimal(value), precision, scale);
    }
  }
}
