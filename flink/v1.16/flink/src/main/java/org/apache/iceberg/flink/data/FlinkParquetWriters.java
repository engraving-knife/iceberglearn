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

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.apache.flink.table.data.ArrayData;
import org.apache.flink.table.data.DecimalData;
import org.apache.flink.table.data.MapData;
import org.apache.flink.table.data.RowData;
import org.apache.flink.table.data.StringData;
import org.apache.flink.table.data.TimestampData;
import org.apache.flink.table.types.logical.ArrayType;
import org.apache.flink.table.types.logical.LogicalType;
import org.apache.flink.table.types.logical.MapType;
import org.apache.flink.table.types.logical.RowType;
import org.apache.flink.table.types.logical.RowType.RowField;
import org.apache.flink.table.types.logical.SmallIntType;
import org.apache.flink.table.types.logical.TinyIntType;
import org.apache.iceberg.parquet.ParquetValueReaders;
import org.apache.iceberg.parquet.ParquetValueWriter;
import org.apache.iceberg.parquet.ParquetValueWriters;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.types.TypeUtil;
import org.apache.iceberg.util.DecimalUtil;
import org.apache.parquet.column.ColumnDescriptor;
import org.apache.parquet.io.api.Binary;
import org.apache.parquet.schema.GroupType;
import org.apache.parquet.schema.LogicalTypeAnnotation.DecimalLogicalTypeAnnotation;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.PrimitiveType;
import org.apache.parquet.schema.Type;

/**
 * Flink 专用的 Parquet 写入器工厂与内部实现集合。
 *
 * <p>所属模块：iceberg-flink v1.15。职责：按 Flink LogicalType 与 Parquet MessageType 构造 {@link
 * ParquetValueWriter}，把 Flink {@link RowData} 写入 Parquet 列式存储。
 *
 * <p>设计意图：访问者模式 + 工厂方法，通过 {@link ParquetWithFlinkSchemaVisitor} 按类型构造写入器； 上下游：被 Flink Parquet
 * 写入算子调用。
 */
public class FlinkParquetWriters {
  private FlinkParquetWriters() {}

  /** 构造写入器入口，通过 ParquetWithFlinkSchemaVisitor 构造。 */
  @SuppressWarnings("unchecked")
  public static <T> ParquetValueWriter<T> buildWriter(LogicalType schema, MessageType type) {
    return (ParquetValueWriter<T>)
        ParquetWithFlinkSchemaVisitor.visit(schema, type, new WriteBuilder(type));
  }

  /** Parquet schema 访问者实现，按 Flink 类型构造 ParquetValueWriter。 */
  private static class WriteBuilder extends ParquetWithFlinkSchemaVisitor<ParquetValueWriter<?>> {
    private final MessageType type;

    WriteBuilder(MessageType type) {
      this.type = type;
    }

    /** 构造 message（顶层 struct）的写入器。 */
    @Override
    public ParquetValueWriter<?> message(
        RowType sStruct, MessageType message, List<ParquetValueWriter<?>> fields) {
      return struct(sStruct, message.asGroupType(), fields);
    }

    /** 构造 struct 写入器，包装可选字段并构造 RowDataWriter。 */
    @Override
    public ParquetValueWriter<?> struct(
        RowType sStruct, GroupType struct, List<ParquetValueWriter<?>> fieldWriters) {
      List<Type> fields = struct.getFields();
      List<RowField> flinkFields = sStruct.getFields();
      List<ParquetValueWriter<?>> writers = Lists.newArrayListWithExpectedSize(fieldWriters.size());
      List<LogicalType> flinkTypes = Lists.newArrayList();
      for (int i = 0; i < fields.size(); i += 1) {
        writers.add(newOption(struct.getType(i), fieldWriters.get(i)));
        flinkTypes.add(flinkFields.get(i).getType());
      }

      return new RowDataWriter(writers, flinkTypes);
    }

    /** 构造 list 写入器，处理 Parquet repeated group。 */
    @Override
    public ParquetValueWriter<?> list(
        ArrayType sArray, GroupType array, ParquetValueWriter<?> elementWriter) {
      GroupType repeated = array.getFields().get(0).asGroupType();
      String[] repeatedPath = currentPath();

      int repeatedD = type.getMaxDefinitionLevel(repeatedPath);
      int repeatedR = type.getMaxRepetitionLevel(repeatedPath);

      return new ArrayDataWriter<>(
          repeatedD,
          repeatedR,
          newOption(repeated.getType(0), elementWriter),
          sArray.getElementType());
    }

    /** 构造 map 写入器，处理 Parquet repeated key-value group。 */
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
          repeatedD,
          repeatedR,
          newOption(repeatedKeyValue.getType(0), keyWriter),
          newOption(repeatedKeyValue.getType(1), valueWriter),
          sMap.getKeyType(),
          sMap.getValueType());
    }

    /** 包装写入器为可选字段写入器。 */
    private ParquetValueWriter<?> newOption(Type fieldType, ParquetValueWriter<?> writer) {
      int maxD = type.getMaxDefinitionLevel(path(fieldType.getName()));
      return ParquetValueWriters.option(fieldType, maxD, writer);
    }

    /**
     * 构造基本类型的写入器。
     *
     * <p>逻辑：先按 Parquet originalType 处理 ENUM/UTF8/INT/DECIMAL/TIME/TIMESTAMP 等； 再按 Flink 类型根区分
     * tinyint/smallint/int 等。
     */
    @Override
    public ParquetValueWriter<?> primitive(LogicalType fType, PrimitiveType primitive) {
      ColumnDescriptor desc = type.getColumnDescription(currentPath());

      if (primitive.getOriginalType() != null) {
        switch (primitive.getOriginalType()) {
          case ENUM:
          case JSON:
          case UTF8:
            return strings(desc);
          case DATE:
          case INT_8:
          case INT_16:
          case INT_32:
            return ints(fType, desc);
          case INT_64:
            return ParquetValueWriters.longs(desc);
          case TIME_MICROS:
            return timeMicros(desc);
          case TIMESTAMP_MICROS:
            return timestamps(desc);
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
          return byteArrays(desc);
        case BOOLEAN:
          return ParquetValueWriters.booleans(desc);
        case INT32:
          return ints(fType, desc);
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

  private static ParquetValueWriters.PrimitiveWriter<?> ints(
      LogicalType type, ColumnDescriptor desc) {
    if (type instanceof TinyIntType) {
      return ParquetValueWriters.tinyints(desc);
    } else if (type instanceof SmallIntType) {
      return ParquetValueWriters.shorts(desc);
    }
    return ParquetValueWriters.ints(desc);
  }

  private static ParquetValueWriters.PrimitiveWriter<StringData> strings(ColumnDescriptor desc) {
    return new StringDataWriter(desc);
  }

  private static ParquetValueWriters.PrimitiveWriter<Integer> timeMicros(ColumnDescriptor desc) {
    return new TimeMicrosWriter(desc);
  }

  private static ParquetValueWriters.PrimitiveWriter<DecimalData> decimalAsInteger(
      ColumnDescriptor desc, int precision, int scale) {
    Preconditions.checkArgument(
        precision <= 9,
        "Cannot write decimal value as integer with precision larger than 9,"
            + " wrong precision %s",
        precision);
    return new IntegerDecimalWriter(desc, precision, scale);
  }

  private static ParquetValueWriters.PrimitiveWriter<DecimalData> decimalAsLong(
      ColumnDescriptor desc, int precision, int scale) {
    Preconditions.checkArgument(
        precision <= 18,
        "Cannot write decimal value as long with precision larger than 18, "
            + " wrong precision %s",
        precision);
    return new LongDecimalWriter(desc, precision, scale);
  }

  private static ParquetValueWriters.PrimitiveWriter<DecimalData> decimalAsFixed(
      ColumnDescriptor desc, int precision, int scale) {
    return new FixedDecimalWriter(desc, precision, scale);
  }

  private static ParquetValueWriters.PrimitiveWriter<TimestampData> timestamps(
      ColumnDescriptor desc) {
    return new TimestampDataWriter(desc);
  }

  private static ParquetValueWriters.PrimitiveWriter<byte[]> byteArrays(ColumnDescriptor desc) {
    return new ByteArrayWriter(desc);
  }

  /** 字符串写入器：StringData 转 Binary。 */
  private static class StringDataWriter extends ParquetValueWriters.PrimitiveWriter<StringData> {
    private StringDataWriter(ColumnDescriptor desc) {
      super(desc);
    }

    @Override
    public void write(int repetitionLevel, StringData value) {
      column.writeBinary(repetitionLevel, Binary.fromReusedByteArray(value.toBytes()));
    }
  }

  /** 时间写入器：毫秒转微秒。 */
  private static class TimeMicrosWriter extends ParquetValueWriters.PrimitiveWriter<Integer> {
    private TimeMicrosWriter(ColumnDescriptor desc) {
      super(desc);
    }

    @Override
    public void write(int repetitionLevel, Integer value) {
      long micros = value.longValue() * 1000;
      column.writeLong(repetitionLevel, micros);
    }
  }

  /** 精度 <=9 的 Decimal 写入器，存为 int。 */
  private static class IntegerDecimalWriter
      extends ParquetValueWriters.PrimitiveWriter<DecimalData> {
    private final int precision;
    private final int scale;

    private IntegerDecimalWriter(ColumnDescriptor desc, int precision, int scale) {
      super(desc);
      this.precision = precision;
      this.scale = scale;
    }

    @Override
    public void write(int repetitionLevel, DecimalData decimal) {
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

  /** 精度 10-18 的 Decimal 写入器，存为 long。 */
  private static class LongDecimalWriter extends ParquetValueWriters.PrimitiveWriter<DecimalData> {
    private final int precision;
    private final int scale;

    private LongDecimalWriter(ColumnDescriptor desc, int precision, int scale) {
      super(desc);
      this.precision = precision;
      this.scale = scale;
    }

    @Override
    public void write(int repetitionLevel, DecimalData decimal) {
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

  /** 定长 Decimal 写入器，存为 Binary。 */
  private static class FixedDecimalWriter extends ParquetValueWriters.PrimitiveWriter<DecimalData> {
    private final int precision;
    private final int scale;
    private final ThreadLocal<byte[]> bytes;

    private FixedDecimalWriter(ColumnDescriptor desc, int precision, int scale) {
      super(desc);
      this.precision = precision;
      this.scale = scale;
      this.bytes =
          ThreadLocal.withInitial(() -> new byte[TypeUtil.decimalRequiredBytes(precision)]);
    }

    @Override
    public void write(int repetitionLevel, DecimalData decimal) {
      byte[] binary =
          DecimalUtil.toReusedFixLengthBytes(precision, scale, decimal.toBigDecimal(), bytes.get());
      column.writeBinary(repetitionLevel, Binary.fromReusedByteArray(binary));
    }
  }

  /** 时间戳写入器，TimestampData 转微秒。 */
  private static class TimestampDataWriter
      extends ParquetValueWriters.PrimitiveWriter<TimestampData> {
    private TimestampDataWriter(ColumnDescriptor desc) {
      super(desc);
    }

    @Override
    public void write(int repetitionLevel, TimestampData value) {
      column.writeLong(
          repetitionLevel, value.getMillisecond() * 1000 + value.getNanoOfMillisecond() / 1000);
    }
  }

  /** 字节数组写入器。 */
  private static class ByteArrayWriter extends ParquetValueWriters.PrimitiveWriter<byte[]> {
    private ByteArrayWriter(ColumnDescriptor desc) {
      super(desc);
    }

    @Override
    public void write(int repetitionLevel, byte[] bytes) {
      column.writeBinary(repetitionLevel, Binary.fromReusedByteArray(bytes));
    }
  }

  /** 数组写入器，通过迭代元素写入 repeated group。 */
  private static class ArrayDataWriter<E> extends ParquetValueWriters.RepeatedWriter<ArrayData, E> {
    private final LogicalType elementType;

    private ArrayDataWriter(
        int definitionLevel,
        int repetitionLevel,
        ParquetValueWriter<E> writer,
        LogicalType elementType) {
      super(definitionLevel, repetitionLevel, writer);
      this.elementType = elementType;
    }

    @Override
    protected Iterator<E> elements(ArrayData list) {
      return new ElementIterator<>(list);
    }

    private class ElementIterator<E> implements Iterator<E> {
      private final int size;
      private final ArrayData list;
      private final ArrayData.ElementGetter getter;
      private int index;

      private ElementIterator(ArrayData list) {
        this.list = list;
        size = list.size();
        getter = ArrayData.createElementGetter(elementType);
        index = 0;
      }

      @Override
      public boolean hasNext() {
        return index != size;
      }

      @Override
      @SuppressWarnings("unchecked")
      public E next() {
        if (index >= size) {
          throw new NoSuchElementException();
        }

        E element = (E) getter.getElementOrNull(list, index);
        index += 1;

        return element;
      }
    }
  }

  /** map 写入器，通过迭代 entry 写入 repeated key-value group。 */
  private static class MapDataWriter<K, V>
      extends ParquetValueWriters.RepeatedKeyValueWriter<MapData, K, V> {
    private final LogicalType keyType;
    private final LogicalType valueType;

    private MapDataWriter(
        int definitionLevel,
        int repetitionLevel,
        ParquetValueWriter<K> keyWriter,
        ParquetValueWriter<V> valueWriter,
        LogicalType keyType,
        LogicalType valueType) {
      super(definitionLevel, repetitionLevel, keyWriter, valueWriter);
      this.keyType = keyType;
      this.valueType = valueType;
    }

    @Override
    protected Iterator<Map.Entry<K, V>> pairs(MapData map) {
      return new EntryIterator<>(map);
    }

    private class EntryIterator<K, V> implements Iterator<Map.Entry<K, V>> {
      private final int size;
      private final ArrayData keys;
      private final ArrayData values;
      private final ParquetValueReaders.ReusableEntry<K, V> entry;
      private final ArrayData.ElementGetter keyGetter;
      private final ArrayData.ElementGetter valueGetter;
      private int index;

      private EntryIterator(MapData map) {
        size = map.size();
        keys = map.keyArray();
        values = map.valueArray();
        entry = new ParquetValueReaders.ReusableEntry<>();
        keyGetter = ArrayData.createElementGetter(keyType);
        valueGetter = ArrayData.createElementGetter(valueType);
        index = 0;
      }

      @Override
      public boolean hasNext() {
        return index != size;
      }

      @Override
      @SuppressWarnings("unchecked")
      public Map.Entry<K, V> next() {
        if (index >= size) {
          throw new NoSuchElementException();
        }

        entry.set(
            (K) keyGetter.getElementOrNull(keys, index),
            (V) valueGetter.getElementOrNull(values, index));
        index += 1;

        return entry;
      }
    }
  }

  /** RowData 写入器，通过 FieldGetter 按字段索引取值后委托子写入器。 */
  private static class RowDataWriter extends ParquetValueWriters.StructWriter<RowData> {
    private final RowData.FieldGetter[] fieldGetter;

    RowDataWriter(List<ParquetValueWriter<?>> writers, List<LogicalType> types) {
      super(writers);
      fieldGetter = new RowData.FieldGetter[types.size()];
      for (int i = 0; i < types.size(); i += 1) {
        fieldGetter[i] = RowData.createFieldGetter(types.get(i), i);
      }
    }

    @Override
    protected Object get(RowData struct, int index) {
      return fieldGetter[index].getFieldOrNull(struct);
    }
  }
}
