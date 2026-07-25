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
 * 文件级说明：将 Flink {@link RowData} 写入 Parquet 格式的写入器工厂。
 *
 * <p>所属模块：iceberg-flink（数据写入子包 data），负责 Flink 行数据与 Parquet 列式编码之间的适配。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>通过 {@link ParquetWithFlinkSchemaVisitor} 同时遍历 Flink {@link LogicalType} 与 Parquet {@link
 *       MessageType}，构建 {@link ParquetValueWriter} 树。
 *   <li>提供针对 Flink 类型（StringData、DecimalData、TimestampData、ArrayData、MapData、RowData）的 具体 Parquet
 *       列写入器实现。
 * </ul>
 *
 * <p>设计意图：Parquet 是列式格式，需要计算 definition/repetition level 来处理可选字段和嵌套结构。 本类通过 Visitor 模式递归构建 writer
 * 树，每个 writer 负责一个列的编码。 DecimalWriter 使用 ThreadLocal 字节缓冲区避免频繁分配。
 *
 * <p>上下游关系：被 Iceberg Parquet 写入流程调用；委托 {@link ParquetWithFlinkSchemaVisitor} 构建 writer 树。
 */
public class FlinkParquetWriters {
  private FlinkParquetWriters() {}

  /**
   * 构建 Parquet 写入器。
   *
   * <p>逻辑：通过 {@link ParquetWithFlinkSchemaVisitor} 同时遍历 Flink {@link LogicalType} 与 Parquet {@link
   * MessageType}，由 {@link WriteBuilder} 递归生成 {@link ParquetValueWriter} 树。
   *
   * @param schema Flink 逻辑类型
   * @param type Parquet message 类型
   * @param <T> 写入数据类型
   * @return Parquet 值写入器
   */
  @SuppressWarnings("unchecked")
  public static <T> ParquetValueWriter<T> buildWriter(LogicalType schema, MessageType type) {
    return (ParquetValueWriter<T>)
        ParquetWithFlinkSchemaVisitor.visit(schema, type, new WriteBuilder(type));
  }

  /**
   * Schema 访问器：将 Parquet 类型节点映射为对应的 Flink {@link ParquetValueWriter}。
   *
   * <p>设计意图：通过 Visitor 模式递归遍历 Parquet schema，在每种类型节点上构建 writer。 对于 struct/list/map，计算
   * definition/repetition level 以正确处理可选字段和嵌套结构。 每个 primitive 字段都会包装为 option writer 以处理 null 值。
   */
  private static class WriteBuilder extends ParquetWithFlinkSchemaVisitor<ParquetValueWriter<?>> {
    private final MessageType type;

    WriteBuilder(MessageType type) {
      this.type = type;
    }

    /** 构建 Parquet message（顶层 struct）的 writer。 */
    @Override
    public ParquetValueWriter<?> message(
        RowType sStruct, MessageType message, List<ParquetValueWriter<?>> fields) {
      return struct(sStruct, message.asGroupType(), fields);
    }

    /**
     * 构建 struct 类型的 writer，每个字段包装为 option writer 以处理 null。
     *
     * <p>逻辑：遍历 Parquet struct 和 Flink RowType 的字段，为每个字段创建 option writer， 收集对应的 Flink 类型，最终创建
     * RowDataWriter。
     */
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

    /**
     * 构建 list 类型的 writer。
     *
     * <p>逻辑：从 Parquet array schema 中取出 repeated group，计算其 definition/repetition level， 将元素 writer
     * 包装为 option 后创建 ArrayDataWriter。
     */
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

    /**
     * 构建 map 类型的 writer。
     *
     * <p>逻辑：从 Parquet map schema 中取出 repeated key-value group，计算 definition/repetition level， 将
     * key/value writer 各自包装为 option 后创建 MapDataWriter。
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
          repeatedD,
          repeatedR,
          newOption(repeatedKeyValue.getType(0), keyWriter),
          newOption(repeatedKeyValue.getType(1), valueWriter),
          sMap.getKeyType(),
          sMap.getValueType());
    }

    /**
     * 将 writer 包装为 option writer，计算该字段的 max definition level。
     *
     * @param fieldType Parquet 字段类型
     * @param writer 内部 writer
     * @return option writer
     */
    private ParquetValueWriter<?> newOption(Type fieldType, ParquetValueWriter<?> writer) {
      int maxD = type.getMaxDefinitionLevel(path(fieldType.getName()));
      return ParquetValueWriters.option(fieldType, maxD, writer);
    }

    /**
     * 构建基础类型的 Parquet writer。
     *
     * <p>逻辑：先检查 Parquet originalType（UTF8/DATE/INT_64/TIME_MICROS/TIMESTAMP_MICROS/DECIMAL/BSON），
     * 选择对应的 writer；无 originalType 时按 Parquet primitive 类型选择。DECIMAL 根据底层存储类型
     * （INT32/INT64/BINARY）选择不同的 writer。
     *
     * @param fType Flink 逻辑类型
     * @param primitive Parquet primitive 类型
     * @return 基础类型 writer
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

  /** 根据 Flink 类型选择 INT 写入器（TINYINT/SMALLINT/INT）。 */
  private static ParquetValueWriters.PrimitiveWriter<?> ints(
      LogicalType type, ColumnDescriptor desc) {
    if (type instanceof TinyIntType) {
      return ParquetValueWriters.tinyints(desc);
    } else if (type instanceof SmallIntType) {
      return ParquetValueWriters.shorts(desc);
    }
    return ParquetValueWriters.ints(desc);
  }

  /** 创建 StringData 写入器。 */
  private static ParquetValueWriters.PrimitiveWriter<StringData> strings(ColumnDescriptor desc) {
    return new StringDataWriter(desc);
  }

  /** 创建 TIME（微秒）写入器。 */
  private static ParquetValueWriters.PrimitiveWriter<Integer> timeMicros(ColumnDescriptor desc) {
    return new TimeMicrosWriter(desc);
  }

  /** 创建以 INT32 存储的 Decimal 写入器（precision ≤ 9）。 */
  private static ParquetValueWriters.PrimitiveWriter<DecimalData> decimalAsInteger(
      ColumnDescriptor desc, int precision, int scale) {
    Preconditions.checkArgument(
        precision <= 9,
        "Cannot write decimal value as integer with precision larger than 9,"
            + " wrong precision %s",
        precision);
    return new IntegerDecimalWriter(desc, precision, scale);
  }

  /** 创建以 INT64 存储的 Decimal 写入器（precision ≤ 18）。 */
  private static ParquetValueWriters.PrimitiveWriter<DecimalData> decimalAsLong(
      ColumnDescriptor desc, int precision, int scale) {
    Preconditions.checkArgument(
        precision <= 18,
        "Cannot write decimal value as long with precision larger than 18, "
            + " wrong precision %s",
        precision);
    return new LongDecimalWriter(desc, precision, scale);
  }

  /** 创建以固定长度字节数组存储的 Decimal 写入器。 */
  private static ParquetValueWriters.PrimitiveWriter<DecimalData> decimalAsFixed(
      ColumnDescriptor desc, int precision, int scale) {
    return new FixedDecimalWriter(desc, precision, scale);
  }

  /** 创建 TimestampData 写入器（微秒精度）。 */
  private static ParquetValueWriters.PrimitiveWriter<TimestampData> timestamps(
      ColumnDescriptor desc) {
    return new TimestampDataWriter(desc);
  }

  /** 创建字节数组写入器。 */
  private static ParquetValueWriters.PrimitiveWriter<byte[]> byteArrays(ColumnDescriptor desc) {
    return new ByteArrayWriter(desc);
  }

  /** StringData 写入器：将 StringData 转为 Parquet Binary。 */
  private static class StringDataWriter extends ParquetValueWriters.PrimitiveWriter<StringData> {
    private StringDataWriter(ColumnDescriptor desc) {
      super(desc);
    }

    @Override
    public void write(int repetitionLevel, StringData value) {
      column.writeBinary(repetitionLevel, Binary.fromReusedByteArray(value.toBytes()));
    }
  }

  /** TIME 写入器：将毫秒值转为微秒写入。 */
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

  /** 以 INT32 存储 Decimal 的写入器，校验 scale 和 precision。 */
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

  /** 以 INT64 存储 Decimal 的写入器，校验 scale 和 precision。 */
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

  /**
   * 以固定长度字节数组存储 Decimal 的写入器。
   *
   * <p>设计要点：使用 ThreadLocal 字节缓冲区避免每次写入分配。
   */
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

  /** TimestampData 写入器：将毫秒+纳秒合成为微秒写入。 */
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

  /** 字节数组写入器：将 byte[] 转为 Parquet Binary。 */
  private static class ByteArrayWriter extends ParquetValueWriters.PrimitiveWriter<byte[]> {
    private ByteArrayWriter(ColumnDescriptor desc) {
      super(desc);
    }

    @Override
    public void write(int repetitionLevel, byte[] bytes) {
      column.writeBinary(repetitionLevel, Binary.fromReusedByteArray(bytes));
    }
  }

  /**
   * ArrayData 写入器：遍历数组元素逐个写入。
   *
   * <p>设计要点：使用 ElementIterator 逐元素访问，通过预创建的 ElementGetter 避免逐元素类型判断。
   */
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

  /**
   * MapData 写入器：遍历 map 的 key/value 对逐对写入。
   *
   * <p>设计要点：使用 EntryIterator 和 ReusableEntry 避免创建临时 Entry 对象。
   */
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

  /**
   * RowData 写入器：逐字段写入 struct。
   *
   * <p>设计要点：预创建 FieldGetter 数组避免逐字段类型判断。
   */
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
