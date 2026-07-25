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

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.apache.avro.io.Decoder;
import org.apache.avro.util.Utf8;
import org.apache.iceberg.avro.ValueReader;
import org.apache.iceberg.avro.ValueReaders;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.types.Types;
import org.apache.iceberg.util.UUIDUtil;
import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.catalyst.expressions.GenericInternalRow;
import org.apache.spark.sql.catalyst.util.ArrayBasedMapData;
import org.apache.spark.sql.catalyst.util.ArrayData;
import org.apache.spark.sql.catalyst.util.GenericArrayData;
import org.apache.spark.sql.types.Decimal;
import org.apache.spark.unsafe.types.UTF8String;

/**
 * Iceberg 与 Spark 数据格式之间的读写转换组件的读取器，负责从底层读取数据并转换为 Spark 内部格式。
 *
 * <p>所属模块：iceberg-spark v3.3。 类型：类 SparkValueReaders。
 *
 * <p>上下游：被 SparkScan/SparkWrite 调用，依赖 Iceberg 文件格式读取/写入 API。
 */
public class SparkValueReaders {

  /** 构造 SparkValueReaders 实例。 */
  private SparkValueReaders() {}

  /** 执行该方法的具体逻辑。 */
  static ValueReader<UTF8String> strings() {
    return StringReader.INSTANCE;
  }

  /** 执行该方法的具体逻辑。 */
  static ValueReader<UTF8String> enums(List<String> symbols) {
    /** 执行该方法的具体逻辑。 */
    return new EnumReader(symbols);
  }

  /** 执行该方法的具体逻辑。 */
  static ValueReader<UTF8String> uuids() {
    return UUIDReader.INSTANCE;
  }

  /** 执行该方法的具体逻辑。 */
  static ValueReader<Decimal> decimal(ValueReader<byte[]> unscaledReader, int scale) {
    /** 执行该方法的具体逻辑。 */
    return new DecimalReader(unscaledReader, scale);
  }

  /** 执行该方法的具体逻辑。 */
  static ValueReader<ArrayData> array(ValueReader<?> elementReader) {
    /** 执行该方法的具体逻辑。 */
    return new ArrayReader(elementReader);
  }

  /** 执行该方法的具体逻辑。 */
  static ValueReader<ArrayBasedMapData> arrayMap(
      ValueReader<?> keyReader, ValueReader<?> valueReader) {
    /** 执行该方法的具体逻辑。 */
    return new ArrayMapReader(keyReader, valueReader);
  }

  /** 执行该方法的具体逻辑。 */
  static ValueReader<ArrayBasedMapData> map(ValueReader<?> keyReader, ValueReader<?> valueReader) {
    /** 执行该方法的具体逻辑。 */
    return new MapReader(keyReader, valueReader);
  }

  /** 执行该方法的具体逻辑。 */
  static ValueReader<InternalRow> struct(
      List<ValueReader<?>> readers, Types.StructType struct, Map<Integer, ?> idToConstant) {
    /** 执行该方法的具体逻辑。 */
    return new StructReader(readers, struct, idToConstant);
  }

  /**
   * Iceberg 与 Spark 数据格式之间的读写转换组件的读取器，负责从底层读取数据并转换为 Spark 内部格式。
   *
   * <p>所属模块：iceberg-spark v3.3。 类型：类 StringReader。
   *
   * <p>上下游：被 SparkScan/SparkWrite 调用，依赖 Iceberg 文件格式读取/写入 API。
   */
  private static class StringReader implements ValueReader<UTF8String> {
    private static final StringReader INSTANCE = new StringReader();

    /** 构造 StringReader 实例。 */
    private StringReader() {}

    /**
     * 读取数据。
     *
     * @param decoder 参数
     * @param reuse 参数
     * @return 结果对象
     */
    @Override
    public UTF8String read(Decoder decoder, Object reuse) throws IOException {
      // use the decoder's readString(Utf8) method because it may be a resolving decoder
      Utf8 utf8 = null;
      if (reuse instanceof UTF8String) {
        utf8 = new Utf8(((UTF8String) reuse).getBytes());
      }

      Utf8 string = decoder.readString(utf8);
      return UTF8String.fromBytes(string.getBytes(), 0, string.getByteLength());
    }
  }

  /**
   * Iceberg 与 Spark 数据格式之间的读写转换组件的读取器，负责从底层读取数据并转换为 Spark 内部格式。
   *
   * <p>所属模块：iceberg-spark v3.3。 类型：类 EnumReader。
   *
   * <p>上下游：被 SparkScan/SparkWrite 调用，依赖 Iceberg 文件格式读取/写入 API。
   */
  private static class EnumReader implements ValueReader<UTF8String> {
    private final UTF8String[] symbols;

    /** 构造 EnumReader 实例。 */
    private EnumReader(List<String> symbols) {
      this.symbols = new UTF8String[symbols.size()];
      for (int i = 0; i < this.symbols.length; i += 1) {
        this.symbols[i] = UTF8String.fromBytes(symbols.get(i).getBytes(StandardCharsets.UTF_8));
      }
    }

    /**
     * 读取数据。
     *
     * @param decoder 参数
     * @param ignore 参数
     * @return 结果对象
     */
    @Override
    public UTF8String read(Decoder decoder, Object ignore) throws IOException {
      int index = decoder.readEnum();
      return symbols[index];
    }
  }

  /**
   * Iceberg 与 Spark 数据格式之间的读写转换组件的读取器，负责从底层读取数据并转换为 Spark 内部格式。
   *
   * <p>所属模块：iceberg-spark v3.3。 类型：类 UUIDReader。
   *
   * <p>上下游：被 SparkScan/SparkWrite 调用，依赖 Iceberg 文件格式读取/写入 API。
   */
  private static class UUIDReader implements ValueReader<UTF8String> {
    private static final ThreadLocal<ByteBuffer> BUFFER =
        ThreadLocal.withInitial(
            () -> {
              ByteBuffer buffer = ByteBuffer.allocate(16);
              buffer.order(ByteOrder.BIG_ENDIAN);
              return buffer;
            });

    private static final UUIDReader INSTANCE = new UUIDReader();

    /** 构造 UUIDReader 实例。 */
    private UUIDReader() {}

    /**
     * 读取数据。
     *
     * @param decoder 参数
     * @param reuse 参数
     * @return 结果对象
     */
    @Override
    @SuppressWarnings("ByteBufferBackingArray")
    public UTF8String read(Decoder decoder, Object reuse) throws IOException {
      ByteBuffer buffer = BUFFER.get();
      buffer.rewind();

      decoder.readFixed(buffer.array(), 0, 16);

      return UTF8String.fromString(UUIDUtil.convert(buffer).toString());
    }
  }

  /**
   * Iceberg 与 Spark 数据格式之间的读写转换组件的读取器，负责从底层读取数据并转换为 Spark 内部格式。
   *
   * <p>所属模块：iceberg-spark v3.3。 类型：类 DecimalReader。
   *
   * <p>上下游：被 SparkScan/SparkWrite 调用，依赖 Iceberg 文件格式读取/写入 API。
   */
  private static class DecimalReader implements ValueReader<Decimal> {
    private final ValueReader<byte[]> bytesReader;
    private final int scale;

    /** 构造 DecimalReader 实例。 */
    private DecimalReader(ValueReader<byte[]> bytesReader, int scale) {
      this.bytesReader = bytesReader;
      this.scale = scale;
    }

    /**
     * 读取数据。
     *
     * @param decoder 参数
     * @param reuse 参数
     * @return 结果对象
     */
    @Override
    public Decimal read(Decoder decoder, Object reuse) throws IOException {
      byte[] bytes = bytesReader.read(decoder, null);
      return Decimal.apply(new BigDecimal(new BigInteger(bytes), scale));
    }
  }

  /**
   * Iceberg 与 Spark 数据格式之间的读写转换组件的读取器，负责从底层读取数据并转换为 Spark 内部格式。
   *
   * <p>所属模块：iceberg-spark v3.3。 类型：类 ArrayReader。
   *
   * <p>上下游：被 SparkScan/SparkWrite 调用，依赖 Iceberg 文件格式读取/写入 API。
   */
  private static class ArrayReader implements ValueReader<ArrayData> {
    private final ValueReader<?> elementReader;
    private final List<Object> reusedList = Lists.newArrayList();

    /** 构造 ArrayReader 实例。 */
    private ArrayReader(ValueReader<?> elementReader) {
      this.elementReader = elementReader;
    }

    /**
     * 读取数据。
     *
     * @param decoder 参数
     * @param reuse 参数
     * @return 结果对象
     */
    @Override
    public GenericArrayData read(Decoder decoder, Object reuse) throws IOException {
      reusedList.clear();
      long chunkLength = decoder.readArrayStart();

      while (chunkLength > 0) {
        for (int i = 0; i < chunkLength; i += 1) {
          reusedList.add(elementReader.read(decoder, null));
        }

        chunkLength = decoder.arrayNext();
      }

      // this will convert the list to an array so it is okay to reuse the list
      /** 执行该方法的具体逻辑。 */
      return new GenericArrayData(reusedList.toArray());
    }
  }

  /**
   * Iceberg 与 Spark 数据格式之间的读写转换组件的读取器，负责从底层读取数据并转换为 Spark 内部格式。
   *
   * <p>所属模块：iceberg-spark v3.3。 类型：类 ArrayMapReader。
   *
   * <p>上下游：被 SparkScan/SparkWrite 调用，依赖 Iceberg 文件格式读取/写入 API。
   */
  private static class ArrayMapReader implements ValueReader<ArrayBasedMapData> {
    private final ValueReader<?> keyReader;
    private final ValueReader<?> valueReader;

    private final List<Object> reusedKeyList = Lists.newArrayList();
    private final List<Object> reusedValueList = Lists.newArrayList();

    /** 构造 ArrayMapReader 实例。 */
    private ArrayMapReader(ValueReader<?> keyReader, ValueReader<?> valueReader) {
      this.keyReader = keyReader;
      this.valueReader = valueReader;
    }

    /**
     * 读取数据。
     *
     * @param decoder 参数
     * @param reuse 参数
     * @return 结果对象
     */
    @Override
    public ArrayBasedMapData read(Decoder decoder, Object reuse) throws IOException {
      reusedKeyList.clear();
      reusedValueList.clear();

      long chunkLength = decoder.readArrayStart();

      while (chunkLength > 0) {
        for (int i = 0; i < chunkLength; i += 1) {
          reusedKeyList.add(keyReader.read(decoder, null));
          reusedValueList.add(valueReader.read(decoder, null));
        }

        chunkLength = decoder.arrayNext();
      }

      /** 执行该方法的具体逻辑。 */
      return new ArrayBasedMapData(
          new GenericArrayData(reusedKeyList.toArray()),
          new GenericArrayData(reusedValueList.toArray()));
    }
  }

  /**
   * Iceberg 与 Spark 数据格式之间的读写转换组件的读取器，负责从底层读取数据并转换为 Spark 内部格式。
   *
   * <p>所属模块：iceberg-spark v3.3。 类型：类 MapReader。
   *
   * <p>上下游：被 SparkScan/SparkWrite 调用，依赖 Iceberg 文件格式读取/写入 API。
   */
  private static class MapReader implements ValueReader<ArrayBasedMapData> {
    private final ValueReader<?> keyReader;
    private final ValueReader<?> valueReader;

    private final List<Object> reusedKeyList = Lists.newArrayList();
    private final List<Object> reusedValueList = Lists.newArrayList();

    /** 构造 MapReader 实例。 */
    private MapReader(ValueReader<?> keyReader, ValueReader<?> valueReader) {
      this.keyReader = keyReader;
      this.valueReader = valueReader;
    }

    /**
     * 读取数据。
     *
     * @param decoder 参数
     * @param reuse 参数
     * @return 结果对象
     */
    @Override
    public ArrayBasedMapData read(Decoder decoder, Object reuse) throws IOException {
      reusedKeyList.clear();
      reusedValueList.clear();

      long chunkLength = decoder.readMapStart();

      while (chunkLength > 0) {
        for (int i = 0; i < chunkLength; i += 1) {
          reusedKeyList.add(keyReader.read(decoder, null));
          reusedValueList.add(valueReader.read(decoder, null));
        }

        chunkLength = decoder.mapNext();
      }

      /** 执行该方法的具体逻辑。 */
      return new ArrayBasedMapData(
          new GenericArrayData(reusedKeyList.toArray()),
          new GenericArrayData(reusedValueList.toArray()));
    }
  }

  /**
   * Iceberg 与 Spark 数据格式之间的读写转换组件的读取器，负责从底层读取数据并转换为 Spark 内部格式。
   *
   * <p>所属模块：iceberg-spark v3.3。 类型：类 StructReader。
   *
   * <p>上下游：被 SparkScan/SparkWrite 调用，依赖 Iceberg 文件格式读取/写入 API。
   */
  static class StructReader extends ValueReaders.StructReader<InternalRow> {
    private final int numFields;

    /** 构造 StructReader 实例。 */
    protected StructReader(
        List<ValueReader<?>> readers, Types.StructType struct, Map<Integer, ?> idToConstant) {
      super(readers, struct, idToConstant);
      this.numFields = readers.size();
    }

    /** 执行该方法的具体逻辑。 */
    @Override
    protected InternalRow reuseOrCreate(Object reuse) {
      if (reuse instanceof GenericInternalRow
          && ((GenericInternalRow) reuse).numFields() == numFields) {
        return (InternalRow) reuse;
      }
      /** 执行该方法的具体逻辑。 */
      return new GenericInternalRow(numFields);
    }

    /** 执行该方法的具体逻辑。 */
    @Override
    protected Object get(InternalRow struct, int pos) {
      return null;
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
}
