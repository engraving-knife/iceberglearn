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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.apache.iceberg.parquet.ParquetValueReaders.ReusableEntry;
import org.apache.iceberg.parquet.ParquetValueWriter;
import org.apache.iceberg.parquet.ParquetValueWriters;
import org.apache.iceberg.parquet.ParquetValueWriters.PrimitiveWriter;
import org.apache.iceberg.parquet.ParquetValueWriters.RepeatedKeyValueWriter;
import org.apache.iceberg.parquet.ParquetValueWriters.RepeatedWriter;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.types.TypeUtil;
import org.apache.iceberg.util.DecimalUtil;
import org.apache.iceberg.util.UUIDUtil;
import org.apache.parquet.column.ColumnDescriptor;
import org.apache.parquet.io.api.Binary;
import org.apache.parquet.schema.GroupType;
import org.apache.parquet.schema.LogicalTypeAnnotation;
import org.apache.parquet.schema.LogicalTypeAnnotation.DecimalLogicalTypeAnnotation;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.PrimitiveType;
import org.apache.parquet.schema.Type;
import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.catalyst.util.ArrayData;
import org.apache.spark.sql.catalyst.util.MapData;
import org.apache.spark.sql.types.ArrayType;
import org.apache.spark.sql.types.ByteType;
import org.apache.spark.sql.types.DataType;
import org.apache.spark.sql.types.Decimal;
import org.apache.spark.sql.types.MapType;
import org.apache.spark.sql.types.ShortType;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;
import org.apache.spark.unsafe.types.UTF8String;

/**
 * Iceberg 与 Spark 数据格式之间的读写转换组件的写入器，负责把 Spark 内部数据写入 Iceberg 底层存储。
 *
 * <p>所属模块：iceberg-spark v3.2。 类型：类 SparkParquetWriters。
 *
 * <p>上下游：被 SparkScan/SparkWrite 调用，依赖 Iceberg 文件格式读取/写入 API。
 */
public class SparkParquetWriters {
  /** 构造 SparkParquetWriters 实例。 */
  private SparkParquetWriters() {}

  /**
   * 构造并返回目标对象。
   *
   * @param dfSchema 参数
   * @param type 参数
   * @return 结果对象
   */
  @SuppressWarnings("unchecked")
  public static <T> ParquetValueWriter<T> buildWriter(StructType dfSchema, MessageType type) {
    return (ParquetValueWriter<T>)
        ParquetWithSparkSchemaVisitor.visit(dfSchema, type, new WriteBuilder(type));
  }

  /**
   * Iceberg 与 Spark 数据格式之间的读写转换组件的构建器，负责分步骤构造目标对象。
   *
   * <p>所属模块：iceberg-spark v3.2。 类型：类 WriteBuilder。
   *
   * <p>设计意图：建造者模式，分离复杂对象的构造与表示。
   *
   * <p>上下游：被 SparkScan/SparkWrite 调用，依赖 Iceberg 文件格式读取/写入 API。
   */
  private static class WriteBuilder extends ParquetWithSparkSchemaVisitor<ParquetValueWriter<?>> {
    private final MessageType type;

    WriteBuilder(MessageType type) {
      this.type = type;
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @param sStruct 参数
     * @param message 参数
     * @param fieldWriters 参数
     * @return 结果对象
     */
    @Override
    public ParquetValueWriter<?> message(
        StructType sStruct, MessageType message, List<ParquetValueWriter<?>> fieldWriters) {
      return struct(sStruct, message.asGroupType(), fieldWriters);
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @param sStruct 参数
     * @param struct 参数
     * @param fieldWriters 参数
     * @return 结果对象
     */
    @Override
    public ParquetValueWriter<?> struct(
        StructType sStruct, GroupType struct, List<ParquetValueWriter<?>> fieldWriters) {
      List<Type> fields = struct.getFields();
      StructField[] sparkFields = sStruct.fields();
      List<ParquetValueWriter<?>> writers = Lists.newArrayListWithExpectedSize(fieldWriters.size());
      List<DataType> sparkTypes = Lists.newArrayList();
      for (int i = 0; i < fields.size(); i += 1) {
        writers.add(newOption(struct.getType(i), fieldWriters.get(i)));
        sparkTypes.add(sparkFields[i].dataType());
      }

      /** 执行该方法的具体逻辑。 */
      return new InternalRowWriter(writers, sparkTypes);
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @param sArray 参数
     * @param array 参数
     * @param elementWriter 参数
     * @return 结果对象
     */
    @Override
    public ParquetValueWriter<?> list(
        ArrayType sArray, GroupType array, ParquetValueWriter<?> elementWriter) {
      GroupType repeated = array.getFields().get(0).asGroupType();
      String[] repeatedPath = currentPath();

      int repeatedD = type.getMaxDefinitionLevel(repeatedPath);
      int repeatedR = type.getMaxRepetitionLevel(repeatedPath);

      return new ArrayDataWriter<>(
          /** 执行该方法的具体逻辑。 */
          repeatedD,
          repeatedR,
          newOption(repeated.getType(0), elementWriter),
          sArray.elementType());
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @param sMap 参数
     * @param map 参数
     * @param keyWriter 参数
     * @param valueWriter 参数
     * @return 结果对象
     */
    @Override
    public ParquetValueWriter<?> map(
        MapType sMap,
        GroupType map,
        ParquetValueWriter<?> keyWriter,
        ParquetValueWriter<?> valueWriter) {
      GroupType repeatedKeyValue = map.getFields().get(0).asGroupType();
      String[] repeatedPath = currentPath();

      int repeatedD = type.getMaxDefinitionLevel(repeatedPath);
      int repeatedR = type.getMaxRepetitionLevel(repeatedPath);

      return new MapDataWriter<>(
          /** 执行该方法的具体逻辑。 */
          repeatedD,
          repeatedR,
          newOption(repeatedKeyValue.getType(0), keyWriter),
          newOption(repeatedKeyValue.getType(1), valueWriter),
          sMap.keyType(),
          sMap.valueType());
    }

    /** 执行该方法的具体逻辑。 */
    private ParquetValueWriter<?> newOption(Type fieldType, ParquetValueWriter<?> writer) {
      int maxD = type.getMaxDefinitionLevel(path(fieldType.getName()));
      return ParquetValueWriters.option(fieldType, maxD, writer);
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @param sType 参数
     * @param primitive 参数
     * @return 结果对象
     */
    @Override
    public ParquetValueWriter<?> primitive(DataType sType, PrimitiveType primitive) {
      ColumnDescriptor desc = type.getColumnDescription(currentPath());

      if (primitive.getOriginalType() != null) {
        switch (primitive.getOriginalType()) {
          case ENUM:
          case JSON:
          case UTF8:
            return utf8Strings(desc);
          case DATE:
          case INT_8:
          case INT_16:
          case INT_32:
            return ints(sType, desc);
          case INT_64:
          case TIME_MICROS:
          case TIMESTAMP_MICROS:
            return ParquetValueWriters.longs(desc);
          case DECIMAL:
            DecimalLogicalTypeAnnotation decimal =
                (DecimalLogicalTypeAnnotation) primitive.getLogicalTypeAnnotation();
            switch (primitive.getPrimitiveTypeName()) {
              case INT32:
                return decimalAsInteger(desc, decimal.getPrecision(), decimal.getScale());
              case INT64:
                return decimalAsLong(desc, decimal.getPrecision(), decimal.getScale());
              case BINARY:
              case FIXED_LEN_BYTE_ARRAY:
                return decimalAsFixed(desc, decimal.getPrecision(), decimal.getScale());
              default:
                throw new UnsupportedOperationException(
                    "Unsupported base type for decimal: " + primitive.getPrimitiveTypeName());
            }
          case BSON:
            return byteArrays(desc);
          default:
            throw new UnsupportedOperationException(
                "Unsupported logical type: " + primitive.getOriginalType());
        }
      }

      switch (primitive.getPrimitiveTypeName()) {
        case FIXED_LEN_BYTE_ARRAY:
        case BINARY:
          if (LogicalTypeAnnotation.uuidType().equals(primitive.getLogicalTypeAnnotation())) {
            return uuids(desc);
          }
          return byteArrays(desc);
        case BOOLEAN:
          return ParquetValueWriters.booleans(desc);
        case INT32:
          return ints(sType, desc);
        case INT64:
          return ParquetValueWriters.longs(desc);
        case FLOAT:
          return ParquetValueWriters.floats(desc);
        case DOUBLE:
          return ParquetValueWriters.doubles(desc);
        default:
          throw new UnsupportedOperationException("Unsupported type: " + primitive);
      }
    }
  }

  /** 执行该方法的具体逻辑。 */
  private static PrimitiveWriter<?> ints(DataType type, ColumnDescriptor desc) {
    if (type instanceof ByteType) {
      return ParquetValueWriters.tinyints(desc);
    } else if (type instanceof ShortType) {
      return ParquetValueWriters.shorts(desc);
    }
    return ParquetValueWriters.ints(desc);
  }

  /** 执行该方法的具体逻辑。 */
  private static PrimitiveWriter<UTF8String> utf8Strings(ColumnDescriptor desc) {
    /** 执行该方法的具体逻辑。 */
    return new UTF8StringWriter(desc);
  }

  /** 执行该方法的具体逻辑。 */
  private static PrimitiveWriter<UTF8String> uuids(ColumnDescriptor desc) {
    /** 执行该方法的具体逻辑。 */
    return new UUIDWriter(desc);
  }

  /** 执行该方法的具体逻辑。 */
  private static PrimitiveWriter<Decimal> decimalAsInteger(
      ColumnDescriptor desc, int precision, int scale) {
    /** 执行该方法的具体逻辑。 */
    return new IntegerDecimalWriter(desc, precision, scale);
  }

  /** 执行该方法的具体逻辑。 */
  private static PrimitiveWriter<Decimal> decimalAsLong(
      ColumnDescriptor desc, int precision, int scale) {
    /** 执行该方法的具体逻辑。 */
    return new LongDecimalWriter(desc, precision, scale);
  }

  /** 执行该方法的具体逻辑。 */
  private static PrimitiveWriter<Decimal> decimalAsFixed(
      ColumnDescriptor desc, int precision, int scale) {
    /** 执行该方法的具体逻辑。 */
    return new FixedDecimalWriter(desc, precision, scale);
  }

  /** 执行该方法的具体逻辑。 */
  private static PrimitiveWriter<byte[]> byteArrays(ColumnDescriptor desc) {
    /** 执行该方法的具体逻辑。 */
    return new ByteArrayWriter(desc);
  }

  /**
   * Iceberg 与 Spark 数据格式之间的读写转换组件的写入器，负责把 Spark 内部数据写入 Iceberg 底层存储。
   *
   * <p>所属模块：iceberg-spark v3.2。 类型：类 UTF8StringWriter。
   *
   * <p>上下游：被 SparkScan/SparkWrite 调用，依赖 Iceberg 文件格式读取/写入 API。
   */
  private static class UTF8StringWriter extends PrimitiveWriter<UTF8String> {
    /** 构造 UTF8StringWriter 实例。 */
    private UTF8StringWriter(ColumnDescriptor desc) {
      super(desc);
    }

    /**
     * 写入数据。
     *
     * @param repetitionLevel 参数
     * @param value 参数
     */
    @Override
    public void write(int repetitionLevel, UTF8String value) {
      column.writeBinary(repetitionLevel, Binary.fromReusedByteArray(value.getBytes()));
    }
  }

  /**
   * Iceberg 与 Spark 数据格式之间的读写转换组件的写入器，负责把 Spark 内部数据写入 Iceberg 底层存储。
   *
   * <p>所属模块：iceberg-spark v3.2。 类型：类 IntegerDecimalWriter。
   *
   * <p>上下游：被 SparkScan/SparkWrite 调用，依赖 Iceberg 文件格式读取/写入 API。
   */
  private static class IntegerDecimalWriter extends PrimitiveWriter<Decimal> {
    private final int precision;
    private final int scale;

    /** 构造 IntegerDecimalWriter 实例。 */
    private IntegerDecimalWriter(ColumnDescriptor desc, int precision, int scale) {
      super(desc);
      this.precision = precision;
      this.scale = scale;
    }

    /**
     * 写入数据。
     *
     * @param repetitionLevel 参数
     * @param decimal 参数
     */
    @Override
    public void write(int repetitionLevel, Decimal decimal) {
      Preconditions.checkArgument(
          decimal.scale() == scale,
          "Cannot write value as decimal(%s,%s), wrong scale: %s",
          precision,
          scale,
          decimal);
      Preconditions.checkArgument(
          decimal.precision() <= precision,
          "Cannot write value as decimal(%s,%s), too large: %s",
          precision,
          scale,
          decimal);

      column.writeInteger(repetitionLevel, (int) decimal.toUnscaledLong());
    }
  }

  /**
   * Iceberg 与 Spark 数据格式之间的读写转换组件的写入器，负责把 Spark 内部数据写入 Iceberg 底层存储。
   *
   * <p>所属模块：iceberg-spark v3.2。 类型：类 LongDecimalWriter。
   *
   * <p>上下游：被 SparkScan/SparkWrite 调用，依赖 Iceberg 文件格式读取/写入 API。
   */
  private static class LongDecimalWriter extends PrimitiveWriter<Decimal> {
    private final int precision;
    private final int scale;

    /** 构造 LongDecimalWriter 实例。 */
    private LongDecimalWriter(ColumnDescriptor desc, int precision, int scale) {
      super(desc);
      this.precision = precision;
      this.scale = scale;
    }

    /**
     * 写入数据。
     *
     * @param repetitionLevel 参数
     * @param decimal 参数
     */
    @Override
    public void write(int repetitionLevel, Decimal decimal) {
      Preconditions.checkArgument(
          decimal.scale() == scale,
          "Cannot write value as decimal(%s,%s), wrong scale: %s",
          precision,
          scale,
          decimal);
      Preconditions.checkArgument(
          decimal.precision() <= precision,
          "Cannot write value as decimal(%s,%s), too large: %s",
          precision,
          scale,
          decimal);

      column.writeLong(repetitionLevel, decimal.toUnscaledLong());
    }
  }

  /**
   * Iceberg 与 Spark 数据格式之间的读写转换组件的写入器，负责把 Spark 内部数据写入 Iceberg 底层存储。
   *
   * <p>所属模块：iceberg-spark v3.2。 类型：类 FixedDecimalWriter。
   *
   * <p>上下游：被 SparkScan/SparkWrite 调用，依赖 Iceberg 文件格式读取/写入 API。
   */
  private static class FixedDecimalWriter extends PrimitiveWriter<Decimal> {
    private final int precision;
    private final int scale;
    private final ThreadLocal<byte[]> bytes;

    /** 构造 FixedDecimalWriter 实例。 */
    private FixedDecimalWriter(ColumnDescriptor desc, int precision, int scale) {
      super(desc);
      this.precision = precision;
      this.scale = scale;
      this.bytes =
          ThreadLocal.withInitial(() -> new byte[TypeUtil.decimalRequiredBytes(precision)]);
    }

    /**
     * 写入数据。
     *
     * @param repetitionLevel 参数
     * @param decimal 参数
     */
    @Override
    public void write(int repetitionLevel, Decimal decimal) {
      byte[] binary =
          DecimalUtil.toReusedFixLengthBytes(
              precision, scale, decimal.toJavaBigDecimal(), bytes.get());
      column.writeBinary(repetitionLevel, Binary.fromReusedByteArray(binary));
    }
  }

  /**
   * Iceberg 与 Spark 数据格式之间的读写转换组件的写入器，负责把 Spark 内部数据写入 Iceberg 底层存储。
   *
   * <p>所属模块：iceberg-spark v3.2。 类型：类 UUIDWriter。
   *
   * <p>上下游：被 SparkScan/SparkWrite 调用，依赖 Iceberg 文件格式读取/写入 API。
   */
  private static class UUIDWriter extends PrimitiveWriter<UTF8String> {
    private static final ThreadLocal<ByteBuffer> BUFFER =
        ThreadLocal.withInitial(
            () -> {
              ByteBuffer buffer = ByteBuffer.allocate(16);
              buffer.order(ByteOrder.BIG_ENDIAN);
              return buffer;
            });

    /** 构造 UUIDWriter 实例。 */
    private UUIDWriter(ColumnDescriptor desc) {
      super(desc);
    }

    /**
     * 写入数据。
     *
     * @param repetitionLevel 参数
     * @param string 参数
     */
    @Override
    public void write(int repetitionLevel, UTF8String string) {
      UUID uuid = UUID.fromString(string.toString());
      ByteBuffer buffer = UUIDUtil.convertToByteBuffer(uuid, BUFFER.get());
      column.writeBinary(repetitionLevel, Binary.fromReusedByteBuffer(buffer));
    }
  }

  /**
   * Iceberg 与 Spark 数据格式之间的读写转换组件的写入器，负责把 Spark 内部数据写入 Iceberg 底层存储。
   *
   * <p>所属模块：iceberg-spark v3.2。 类型：类 ByteArrayWriter。
   *
   * <p>上下游：被 SparkScan/SparkWrite 调用，依赖 Iceberg 文件格式读取/写入 API。
   */
  private static class ByteArrayWriter extends PrimitiveWriter<byte[]> {
    /** 构造 ByteArrayWriter 实例。 */
    private ByteArrayWriter(ColumnDescriptor desc) {
      super(desc);
    }

    /**
     * 写入数据。
     *
     * @param repetitionLevel 参数
     * @param bytes 参数
     */
    @Override
    public void write(int repetitionLevel, byte[] bytes) {
      column.writeBinary(repetitionLevel, Binary.fromReusedByteArray(bytes));
    }
  }

  /**
   * Iceberg 与 Spark 数据格式之间的读写转换组件的写入器，负责把 Spark 内部数据写入 Iceberg 底层存储。
   *
   * <p>所属模块：iceberg-spark v3.2。 类型：类 ArrayDataWriter。
   *
   * <p>上下游：被 SparkScan/SparkWrite 调用，依赖 Iceberg 文件格式读取/写入 API。
   */
  private static class ArrayDataWriter<E> extends RepeatedWriter<ArrayData, E> {
    private final DataType elementType;

    /** 构造 ArrayDataWriter 实例。 */
    private ArrayDataWriter(
        int definitionLevel,
        int repetitionLevel,
        ParquetValueWriter<E> writer,
        DataType elementType) {
      super(definitionLevel, repetitionLevel, writer);
      this.elementType = elementType;
    }

    /** 执行该方法的具体逻辑。 */
    @Override
    protected Iterator<E> elements(ArrayData list) {
      return new ElementIterator<>(list);
    }

    /**
     * Iceberg 与 Spark 数据格式之间的读写转换组件的迭代器，按行或按批产出数据。
     *
     * <p>所属模块：iceberg-spark v3.2。 类型：类 ElementIterator。
     *
     * <p>设计意图：迭代器模式，统一遍历接口。
     *
     * <p>上下游：被 SparkScan/SparkWrite 调用，依赖 Iceberg 文件格式读取/写入 API。
     */
    private class ElementIterator<E> implements Iterator<E> {
      private final int size;
      private final ArrayData list;
      private int index;

      /** 构造 ElementIterator 实例。 */
      private ElementIterator(ArrayData list) {
        this.list = list;
        size = list.numElements();
        index = 0;
      }

      /** 判断是否包含next。 */
      @Override
      public boolean hasNext() {
        return index != size;
      }

      /**
       * 执行该方法的具体逻辑。
       *
       * @return 对应结果
       */
      @Override
      @SuppressWarnings("unchecked")
      public E next() {
        if (index >= size) {
          /** 执行该方法的具体逻辑。 */
          throw new NoSuchElementException();
        }

        E element;
        if (list.isNullAt(index)) {
          element = null;
        } else {
          element = (E) list.get(index, elementType);
        }

        index += 1;

        return element;
      }
    }
  }

  /**
   * Iceberg 与 Spark 数据格式之间的读写转换组件的写入器，负责把 Spark 内部数据写入 Iceberg 底层存储。
   *
   * <p>所属模块：iceberg-spark v3.2。 类型：类 MapDataWriter。
   *
   * <p>上下游：被 SparkScan/SparkWrite 调用，依赖 Iceberg 文件格式读取/写入 API。
   */
  private static class MapDataWriter<K, V> extends RepeatedKeyValueWriter<MapData, K, V> {
    private final DataType keyType;
    private final DataType valueType;

    /** 构造 MapDataWriter 实例。 */
    private MapDataWriter(
        int definitionLevel,
        int repetitionLevel,
        ParquetValueWriter<K> keyWriter,
        ParquetValueWriter<V> valueWriter,
        DataType keyType,
        DataType valueType) {
      super(definitionLevel, repetitionLevel, keyWriter, valueWriter);
      this.keyType = keyType;
      this.valueType = valueType;
    }

    /** 执行该方法的具体逻辑。 */
    @Override
    protected Iterator<Map.Entry<K, V>> pairs(MapData map) {
      return new EntryIterator<>(map);
    }

    /**
     * Iceberg 与 Spark 数据格式之间的读写转换组件的迭代器，按行或按批产出数据。
     *
     * <p>所属模块：iceberg-spark v3.2。 类型：类 EntryIterator。
     *
     * <p>设计意图：迭代器模式，统一遍历接口。
     *
     * <p>上下游：被 SparkScan/SparkWrite 调用，依赖 Iceberg 文件格式读取/写入 API。
     */
    private class EntryIterator<K, V> implements Iterator<Map.Entry<K, V>> {
      private final int size;
      private final ArrayData keys;
      private final ArrayData values;
      private final ReusableEntry<K, V> entry;
      private int index;

      /** 构造 EntryIterator 实例。 */
      private EntryIterator(MapData map) {
        size = map.numElements();
        keys = map.keyArray();
        values = map.valueArray();
        entry = new ReusableEntry<>();
        index = 0;
      }

      /** 判断是否包含next。 */
      @Override
      public boolean hasNext() {
        return index != size;
      }

      /**
       * 执行该方法的具体逻辑。
       *
       * @return 对应结果
       */
      @Override
      @SuppressWarnings("unchecked")
      public Map.Entry<K, V> next() {
        if (index >= size) {
          /** 执行该方法的具体逻辑。 */
          throw new NoSuchElementException();
        }

        if (values.isNullAt(index)) {
          entry.set((K) keys.get(index, keyType), null);
        } else {
          entry.set((K) keys.get(index, keyType), (V) values.get(index, valueType));
        }

        index += 1;

        return entry;
      }
    }
  }

  /**
   * Iceberg 与 Spark 数据格式之间的读写转换组件的写入器，负责把 Spark 内部数据写入 Iceberg 底层存储。
   *
   * <p>所属模块：iceberg-spark v3.2。 类型：类 InternalRowWriter。
   *
   * <p>上下游：被 SparkScan/SparkWrite 调用，依赖 Iceberg 文件格式读取/写入 API。
   */
  private static class InternalRowWriter extends ParquetValueWriters.StructWriter<InternalRow> {
    private final DataType[] types;

    /** 构造 InternalRowWriter 实例。 */
    private InternalRowWriter(List<ParquetValueWriter<?>> writers, List<DataType> types) {
      super(writers);
      this.types = types.toArray(new DataType[types.size()]);
    }

    /** 执行该方法的具体逻辑。 */
    @Override
    protected Object get(InternalRow struct, int index) {
      return struct.get(index, types[index]);
    }
  }
}
