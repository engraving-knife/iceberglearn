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
 * Spark 内部数据类型到 Parquet 的写入器构建工厂。
 *
 * <p>所属模块：iceberg-spark（Iceberg 与 Spark 3.5 的集成层，data 子包负责 Spark 数据 格式与 Parquet 之间的写入转换）。
 *
 * <p>职责：根据 Spark StructType 和 Parquet MessageType 构建对应的 {@link ParquetValueWriter} 树，使 Spark
 * InternalRow/ArrayData/MapData 能正确写入 Parquet 文件。涵盖所有 Spark 原始类型、字符串、UUID、Decimal、数组和映射。
 *
 * <p>设计意图：利用 {@link ParquetWithSparkSchemaVisitor} 的访问者模式遍历 Spark 与 Parquet 的类型对应结构，通过 WriteBuilder
 * 在每个节点构建对应的写入器。 Decimal 写入根据 Parquet 物理类型（INT32/INT64/BINARY）选择不同的写入器。 使用 ThreadLocal 缓冲区避免
 * UUID/Decimal 写入时的频繁分配。
 *
 * <p>上下游关系：被 Iceberg Spark 数据源在写入 Parquet 文件时调用； 依赖 Iceberg parquet 模块的 ParquetValueWriter 框架。
 */
public class SparkParquetWriters {
  private SparkParquetWriters() {}

  /**
   * 构建 Spark StructType 对应的 Parquet 写入器。
   *
   * <p>逻辑：通过 ParquetWithSparkSchemaVisitor 遍历 Spark schema 与 Parquet MessageType 的结构对应关系，使用
   * WriteBuilder 在每个类型节点构建写入器。
   *
   * @param dfSchema Spark 结构类型
   * @param type Parquet 消息类型
   * @param <T> 写入值的类型
   * @return Parquet 值写入器
   */
  @SuppressWarnings("unchecked")
  public static <T> ParquetValueWriter<T> buildWriter(StructType dfSchema, MessageType type) {
    return (ParquetValueWriter<T>)
        ParquetWithSparkSchemaVisitor.visit(dfSchema, type, new WriteBuilder(type));
  }

  /** 写入器构建访问者：在 Spark 与 Parquet 类型遍历过程中为每个节点构建对应的写入器。 */
  private static class WriteBuilder extends ParquetWithSparkSchemaVisitor<ParquetValueWriter<?>> {
    private final MessageType type;

    WriteBuilder(MessageType type) {
      this.type = type;
    }
    /** 执行 message 相关操作。 */
    @Override
    public ParquetValueWriter<?> message(
        StructType sStruct, MessageType message, List<ParquetValueWriter<?>> fieldWriters) {
      return struct(sStruct, message.asGroupType(), fieldWriters);
    }
    /** 执行 struct 相关操作。 */
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

      return new InternalRowWriter(writers, sparkTypes);
    }
    /** 执行 list 相关操作。 */
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
          sArray.elementType());
    }
    /** 执行 map 相关操作。 */
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
          sMap.keyType(),
          sMap.valueType());
    }
    /** 创建 Option 实例。 */
    private ParquetValueWriter<?> newOption(Type fieldType, ParquetValueWriter<?> writer) {
      int maxD = type.getMaxDefinitionLevel(path(fieldType.getName()));
      return ParquetValueWriters.option(fieldType, maxD, writer);
    }

    /**
     * 为原始类型构建对应的 Parquet 写入器。
     *
     * <p>逻辑：先根据 Parquet 的 OriginalType（ENUM/JSON/UTF8/DATE/INT_8/INT_16/INT_32/
     * INT_64/TIME_MICROS/TIMESTAMP_MICROS/DECIMAL/BSON）分派；无注解时再根据
     * PrimitiveTypeName（BINARY/BOOLEAN/INT32/INT64/FLOAT/DOUBLE）分派。 Decimal 根据 Parquet 物理类型选择
     * Integer/Long/Fixed 写入器。
     *
     * @param sType Spark 数据类型
     * @param primitive Parquet 原始类型
     * @return 对应的写入器
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
  /** 执行 ints 相关操作。 */
  private static PrimitiveWriter<?> ints(DataType type, ColumnDescriptor desc) {
    if (type instanceof ByteType) {
      return ParquetValueWriters.tinyints(desc);
    } else if (type instanceof ShortType) {
      return ParquetValueWriters.shorts(desc);
    }
    return ParquetValueWriters.ints(desc);
  }
  /** 执行 utf8Strings 相关操作。 */
  private static PrimitiveWriter<UTF8String> utf8Strings(ColumnDescriptor desc) {
    return new UTF8StringWriter(desc);
  }
  /** 执行 uuids 相关操作。 */
  private static PrimitiveWriter<UTF8String> uuids(ColumnDescriptor desc) {
    return new UUIDWriter(desc);
  }
  /** 执行 decimalAsInteger 相关操作。 */
  private static PrimitiveWriter<Decimal> decimalAsInteger(
      ColumnDescriptor desc, int precision, int scale) {
    return new IntegerDecimalWriter(desc, precision, scale);
  }
  /** 执行 decimalAsLong 相关操作。 */
  private static PrimitiveWriter<Decimal> decimalAsLong(
      ColumnDescriptor desc, int precision, int scale) {
    return new LongDecimalWriter(desc, precision, scale);
  }
  /** 执行 decimalAsFixed 相关操作。 */
  private static PrimitiveWriter<Decimal> decimalAsFixed(
      ColumnDescriptor desc, int precision, int scale) {
    return new FixedDecimalWriter(desc, precision, scale);
  }
  /** 执行 byteArrays 相关操作。 */
  private static PrimitiveWriter<byte[]> byteArrays(ColumnDescriptor desc) {
    return new ByteArrayWriter(desc);
  }

  private static class UTF8StringWriter extends PrimitiveWriter<UTF8String> {
    private UTF8StringWriter(ColumnDescriptor desc) {
      super(desc);
    }
    /** 写入数据。 */
    @Override
    public void write(int repetitionLevel, UTF8String value) {
      column.writeBinary(repetitionLevel, Binary.fromReusedByteArray(value.getBytes()));
    }
  }

  private static class IntegerDecimalWriter extends PrimitiveWriter<Decimal> {
    private final int precision;
    private final int scale;

    private IntegerDecimalWriter(ColumnDescriptor desc, int precision, int scale) {
      super(desc);
      this.precision = precision;
      this.scale = scale;
    }
    /** 写入数据。 */
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

  private static class LongDecimalWriter extends PrimitiveWriter<Decimal> {
    private final int precision;
    private final int scale;

    private LongDecimalWriter(ColumnDescriptor desc, int precision, int scale) {
      super(desc);
      this.precision = precision;
      this.scale = scale;
    }
    /** 写入数据。 */
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

  private static class FixedDecimalWriter extends PrimitiveWriter<Decimal> {
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
    /** 写入数据。 */
    @Override
    public void write(int repetitionLevel, Decimal decimal) {
      byte[] binary =
          DecimalUtil.toReusedFixLengthBytes(
              precision, scale, decimal.toJavaBigDecimal(), bytes.get());
      column.writeBinary(repetitionLevel, Binary.fromReusedByteArray(binary));
    }
  }

  private static class UUIDWriter extends PrimitiveWriter<UTF8String> {
    private static final ThreadLocal<ByteBuffer> BUFFER =
        ThreadLocal.withInitial(
            () -> {
              ByteBuffer buffer = ByteBuffer.allocate(16);
              buffer.order(ByteOrder.BIG_ENDIAN);
              return buffer;
            });

    private UUIDWriter(ColumnDescriptor desc) {
      super(desc);
    }
    /** 写入数据。 */
    @Override
    public void write(int repetitionLevel, UTF8String string) {
      UUID uuid = UUID.fromString(string.toString());
      ByteBuffer buffer = UUIDUtil.convertToByteBuffer(uuid, BUFFER.get());
      column.writeBinary(repetitionLevel, Binary.fromReusedByteBuffer(buffer));
    }
  }

  private static class ByteArrayWriter extends PrimitiveWriter<byte[]> {
    private ByteArrayWriter(ColumnDescriptor desc) {
      super(desc);
    }
    /** 写入数据。 */
    @Override
    public void write(int repetitionLevel, byte[] bytes) {
      column.writeBinary(repetitionLevel, Binary.fromReusedByteArray(bytes));
    }
  }

  private static class ArrayDataWriter<E> extends RepeatedWriter<ArrayData, E> {
    private final DataType elementType;

    private ArrayDataWriter(
        int definitionLevel,
        int repetitionLevel,
        ParquetValueWriter<E> writer,
        DataType elementType) {
      super(definitionLevel, repetitionLevel, writer);
      this.elementType = elementType;
    }
    /** 执行 elements 相关操作。 */
    @Override
    protected Iterator<E> elements(ArrayData list) {
      return new ElementIterator<>(list);
    }

    private class ElementIterator<E> implements Iterator<E> {
      private final int size;
      private final ArrayData list;
      private int index;

      private ElementIterator(ArrayData list) {
        this.list = list;
        size = list.numElements();
        index = 0;
      }
      /** 判断是否有下一个元素。 */
      @Override
      public boolean hasNext() {
        return index != size;
      }
      /** 返回下一个元素。 */
      @Override
      @SuppressWarnings("unchecked")
      public E next() {
        if (index >= size) {
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

  private static class MapDataWriter<K, V> extends RepeatedKeyValueWriter<MapData, K, V> {
    private final DataType keyType;
    private final DataType valueType;

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
    /** 执行 pairs 相关操作。 */
    @Override
    protected Iterator<Map.Entry<K, V>> pairs(MapData map) {
      return new EntryIterator<>(map);
    }

    private class EntryIterator<K, V> implements Iterator<Map.Entry<K, V>> {
      private final int size;
      private final ArrayData keys;
      private final ArrayData values;
      private final ReusableEntry<K, V> entry;
      private int index;

      private EntryIterator(MapData map) {
        size = map.numElements();
        keys = map.keyArray();
        values = map.valueArray();
        entry = new ReusableEntry<>();
        index = 0;
      }
      /** 判断是否有下一个元素。 */
      @Override
      public boolean hasNext() {
        return index != size;
      }
      /** 返回下一个元素。 */
      @Override
      @SuppressWarnings("unchecked")
      public Map.Entry<K, V> next() {
        if (index >= size) {
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

  private static class InternalRowWriter extends ParquetValueWriters.StructWriter<InternalRow> {
    private final DataType[] types;

    private InternalRowWriter(List<ParquetValueWriter<?>> writers, List<DataType> types) {
      super(writers);
      this.types = types.toArray(new DataType[types.size()]);
    }
    /** 返回值。 */
    @Override
    protected Object get(InternalRow struct, int index) {
      return struct.get(index, types[index]);
    }
  }
}
