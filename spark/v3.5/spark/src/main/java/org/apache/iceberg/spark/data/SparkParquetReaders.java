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
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.apache.iceberg.MetadataColumns;
import org.apache.iceberg.Schema;
import org.apache.iceberg.parquet.ParquetSchemaUtil;
import org.apache.iceberg.parquet.ParquetUtil;
import org.apache.iceberg.parquet.ParquetValueReader;
import org.apache.iceberg.parquet.ParquetValueReaders;
import org.apache.iceberg.parquet.ParquetValueReaders.FloatAsDoubleReader;
import org.apache.iceberg.parquet.ParquetValueReaders.IntAsLongReader;
import org.apache.iceberg.parquet.ParquetValueReaders.PrimitiveReader;
import org.apache.iceberg.parquet.ParquetValueReaders.RepeatedKeyValueReader;
import org.apache.iceberg.parquet.ParquetValueReaders.RepeatedReader;
import org.apache.iceberg.parquet.ParquetValueReaders.ReusableEntry;
import org.apache.iceberg.parquet.ParquetValueReaders.StructReader;
import org.apache.iceberg.parquet.ParquetValueReaders.UnboxedReader;
import org.apache.iceberg.parquet.TypeWithSchemaVisitor;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.types.Type.TypeID;
import org.apache.iceberg.types.Types;
import org.apache.iceberg.util.UUIDUtil;
import org.apache.parquet.column.ColumnDescriptor;
import org.apache.parquet.io.api.Binary;
import org.apache.parquet.schema.GroupType;
import org.apache.parquet.schema.LogicalTypeAnnotation.DecimalLogicalTypeAnnotation;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.PrimitiveType;
import org.apache.parquet.schema.Type;
import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.catalyst.expressions.GenericInternalRow;
import org.apache.spark.sql.catalyst.util.ArrayBasedMapData;
import org.apache.spark.sql.catalyst.util.ArrayData;
import org.apache.spark.sql.catalyst.util.GenericArrayData;
import org.apache.spark.sql.catalyst.util.MapData;
import org.apache.spark.sql.types.DataType;
import org.apache.spark.sql.types.Decimal;
import org.apache.spark.unsafe.types.CalendarInterval;
import org.apache.spark.unsafe.types.UTF8String;

/**
 * 将 Parquet 列式数据读取为 Spark {@link InternalRow} 的读取器构造工具。
 *
 * <p>所属模块：iceberg-spark（data 子包，负责 Iceberg 表 Parquet 数据向 Spark 内部行的转换）。
 *
 * <p>职责：根据 Iceberg 期望 schema 与 Parquet 文件 schema，按字段 ID 匹配并构建 类型化的 {@link
 * ParquetValueReader}，支持常量列注入、字段重排、类型提升与元数据列。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>通过 {@link TypeWithSchemaVisitor} 按 schema 结构遍历，为每种类型生成对应读取器， 实现读取逻辑与 Spark 类型解耦。
 *   <li>当 Parquet 文件含字段 ID 时按 ID 精确匹配并按期望 schema 顺序重排字段； 无 ID 时回退到按位置匹配的 {@link
 *       FallbackReadBuilder}。
 *   <li>支持把隐藏分区等常量列通过 idToConstant 注入，避免重复读文件； 支持 ROW_POSITION、IS_DELETED 等元数据列。
 *   <li>各类专用 reader（Decimal、Timestamp、String、UUID 等）针对 Parquet 存储格式做优化转换。
 * </ul>
 *
 * <p>上下游关系：被 Iceberg 的 Parquet 读取流水线在 Spark 端调用，产出供 Spark 执行消费的行读取器。
 */
public class SparkParquetReaders {
  private SparkParquetReaders() {}

  /**
   * 构建行读取器，不注入常量列。
   *
   * @param expectedSchema Iceberg 期望读取的 schema
   * @param fileSchema Parquet 文件 schema
   * @return 行读取器
   */
  public static ParquetValueReader<InternalRow> buildReader(
      Schema expectedSchema, MessageType fileSchema) {
    return buildReader(expectedSchema, fileSchema, ImmutableMap.of());
  }

  /**
   * 构建行读取器，并注入字段 ID 到常量值的映射。
   *
   * <p>逻辑：若 Parquet schema 含字段 ID，使用 {@link ReadBuilder} 按 ID 匹配； 否则使用 {@link FallbackReadBuilder}
   * 按位置回退匹配。
   *
   * @param expectedSchema Iceberg 期望读取的 schema
   * @param fileSchema Parquet 文件 schema
   * @param idToConstant 字段 ID 到常量值的映射，用于注入隐藏分区等常量列
   * @return 行读取器
   */
  @SuppressWarnings("unchecked")
  public static ParquetValueReader<InternalRow> buildReader(
      Schema expectedSchema, MessageType fileSchema, Map<Integer, ?> idToConstant) {
    if (ParquetSchemaUtil.hasIds(fileSchema)) {
      return (ParquetValueReader<InternalRow>)
          TypeWithSchemaVisitor.visit(
              expectedSchema.asStruct(), fileSchema, new ReadBuilder(fileSchema, idToConstant));
    } else {
      return (ParquetValueReader<InternalRow>)
          TypeWithSchemaVisitor.visit(
              expectedSchema.asStruct(),
              fileSchema,
              new FallbackReadBuilder(fileSchema, idToConstant));
    }
  }

  /**
   * 回退读取器构建器：当 Parquet 文件 schema 不含字段 ID 时，按字段位置而非 ID 匹配。
   *
   * <p>设计意图：旧版或非 Iceberg 写入的 Parquet 文件没有字段 ID，无法按 ID 精确匹配， 故按文件字段顺序构建读取器，忽略期望 schema 的字段顺序差异。
   */
  private static class FallbackReadBuilder extends ReadBuilder {
    FallbackReadBuilder(MessageType type, Map<Integer, ?> idToConstant) {
      super(type, idToConstant);
    }
    /** 执行 message 相关操作。 */
    @Override
    public ParquetValueReader<?> message(
        Types.StructType expected, MessageType message, List<ParquetValueReader<?>> fieldReaders) {
      // the top level matches by ID, but the remaining IDs are missing
      return super.struct(expected, message, fieldReaders);
    }
    /** 执行 struct 相关操作。 */
    @Override
    public ParquetValueReader<?> struct(
        Types.StructType ignored, GroupType struct, List<ParquetValueReader<?>> fieldReaders) {
      // the expected struct is ignored because nested fields are never found when the
      List<ParquetValueReader<?>> newFields =
          Lists.newArrayListWithExpectedSize(fieldReaders.size());
      List<Type> types = Lists.newArrayListWithExpectedSize(fieldReaders.size());
      List<Type> fields = struct.getFields();
      for (int i = 0; i < fields.size(); i += 1) {
        Type fieldType = fields.get(i);
        int fieldD = type().getMaxDefinitionLevel(path(fieldType.getName())) - 1;
        newFields.add(ParquetValueReaders.option(fieldType, fieldD, fieldReaders.get(i)));
        types.add(fieldType);
      }

      return new InternalRowReader(types, newFields);
    }
  }

  /** 按 schema 结构遍历构建 Parquet 值读取器的访问器，依据字段 ID 匹配并处理常量列与元数据列。 */
  private static class ReadBuilder extends TypeWithSchemaVisitor<ParquetValueReader<?>> {
    private final MessageType type;
    private final Map<Integer, ?> idToConstant;

    ReadBuilder(MessageType type, Map<Integer, ?> idToConstant) {
      this.type = type;
      this.idToConstant = idToConstant;
    }
    /** 执行 message 相关操作。 */
    @Override
    public ParquetValueReader<?> message(
        Types.StructType expected, MessageType message, List<ParquetValueReader<?>> fieldReaders) {
      return struct(expected, message.asGroupType(), fieldReaders);
    }

    /**
     * 构建结构体（行）读取器，按期望 schema 的字段顺序重排并处理常量列与元数据列。
     *
     * <p>逻辑：
     *
     * <ol>
     *   <li>遍历 Parquet 结构体字段，按字段 ID 建立读取器、类型、最大定义级别的映射。
     *   <li>按期望 schema 字段顺序重排：常量列注入常量读取器；ROW_POSITION 注入位置读取器； IS_DELETED 注入常量 false；其余按 ID
     *       取对应读取器，缺失则用 nulls 读取器。
     *   <li>返回按期望顺序构造的 {@link InternalRowReader}。
     * </ol>
     *
     * @param expected Iceberg 期望结构体类型
     * @param struct Parquet 结构体类型
     * @param fieldReaders 各字段读取器
     * @return 行读取器
     */
    @Override
    public ParquetValueReader<?> struct(
        Types.StructType expected, GroupType struct, List<ParquetValueReader<?>> fieldReaders) {
      // match the expected struct's order
      Map<Integer, ParquetValueReader<?>> readersById = Maps.newHashMap();
      Map<Integer, Type> typesById = Maps.newHashMap();
      Map<Integer, Integer> maxDefinitionLevelsById = Maps.newHashMap();
      List<Type> fields = struct.getFields();
      for (int i = 0; i < fields.size(); i += 1) {
        Type fieldType = fields.get(i);
        int fieldD = type.getMaxDefinitionLevel(path(fieldType.getName())) - 1;
        if (fieldType.getId() != null) {
          int id = fieldType.getId().intValue();
          readersById.put(id, ParquetValueReaders.option(fieldType, fieldD, fieldReaders.get(i)));
          typesById.put(id, fieldType);
          if (idToConstant.containsKey(id)) {
            maxDefinitionLevelsById.put(id, fieldD);
          }
        }
      }

      List<Types.NestedField> expectedFields =
          expected != null ? expected.fields() : ImmutableList.of();
      List<ParquetValueReader<?>> reorderedFields =
          Lists.newArrayListWithExpectedSize(expectedFields.size());
      List<Type> types = Lists.newArrayListWithExpectedSize(expectedFields.size());
      // Defaulting to parent max definition level
      int defaultMaxDefinitionLevel = type.getMaxDefinitionLevel(currentPath());
      for (Types.NestedField field : expectedFields) {
        int id = field.fieldId();
        if (idToConstant.containsKey(id)) {
          // containsKey is used because the constant may be null
          int fieldMaxDefinitionLevel =
              maxDefinitionLevelsById.getOrDefault(id, defaultMaxDefinitionLevel);
          reorderedFields.add(
              ParquetValueReaders.constant(idToConstant.get(id), fieldMaxDefinitionLevel));
          types.add(null);
        } else if (id == MetadataColumns.ROW_POSITION.fieldId()) {
          reorderedFields.add(ParquetValueReaders.position());
          types.add(null);
        } else if (id == MetadataColumns.IS_DELETED.fieldId()) {
          reorderedFields.add(ParquetValueReaders.constant(false));
          types.add(null);
        } else {
          ParquetValueReader<?> reader = readersById.get(id);
          if (reader != null) {
            reorderedFields.add(reader);
            types.add(typesById.get(id));
          } else {
            reorderedFields.add(ParquetValueReaders.nulls());
            types.add(null);
          }
        }
      }

      return new InternalRowReader(types, reorderedFields);
    }
    /** 执行 list 相关操作。 */
    @Override
    public ParquetValueReader<?> list(
        Types.ListType expectedList, GroupType array, ParquetValueReader<?> elementReader) {
      String[] repeatedPath = currentPath();

      int repeatedD = type.getMaxDefinitionLevel(repeatedPath) - 1;
      int repeatedR = type.getMaxRepetitionLevel(repeatedPath) - 1;

      Type elementType = ParquetSchemaUtil.determineListElementType(array);
      int elementD = type.getMaxDefinitionLevel(path(elementType.getName())) - 1;

      return new ArrayReader<>(
          repeatedD, repeatedR, ParquetValueReaders.option(elementType, elementD, elementReader));
    }
    /** 执行 map 相关操作。 */
    @Override
    public ParquetValueReader<?> map(
        Types.MapType expectedMap,
        GroupType map,
        ParquetValueReader<?> keyReader,
        ParquetValueReader<?> valueReader) {
      GroupType repeatedKeyValue = map.getFields().get(0).asGroupType();
      String[] repeatedPath = currentPath();

      int repeatedD = type.getMaxDefinitionLevel(repeatedPath) - 1;
      int repeatedR = type.getMaxRepetitionLevel(repeatedPath) - 1;

      Type keyType = repeatedKeyValue.getType(0);
      int keyD = type.getMaxDefinitionLevel(path(keyType.getName())) - 1;
      Type valueType = repeatedKeyValue.getType(1);
      int valueD = type.getMaxDefinitionLevel(path(valueType.getName())) - 1;

      return new MapReader<>(
          repeatedD,
          repeatedR,
          ParquetValueReaders.option(keyType, keyD, keyReader),
          ParquetValueReaders.option(valueType, valueD, valueReader));
    }

    /**
     * 为 Parquet 基本类型构造对应的值读取器。
     *
     * <p>逻辑：
     *
     * <ol>
     *   <li>若 Parquet 类型带 logical type，按逻辑类型分派：UTF8/JSON/ENUM 走字符串读取器； 整型按期望类型决定是否提升为
     *       long；时间戳按毫秒/微秒走不同读取器； Decimal 按底层存储（BINARY/INT64/INT32）走对应 Decimal 读取器。
     *   <li>无 logical type 时按 Parquet 基本类型分派：UUID 走 UUID 读取器； INT32 可提升为 long，FLOAT 可提升为
     *       double；INT96 按时间戳读取以兼容 Impala/Spark 旧格式。
     * </ol>
     *
     * @param expected Iceberg 期望基本类型，可为 null
     * @param primitive Parquet 基本类型
     * @return 对应的值读取器
     * @throws UnsupportedOperationException 当遇到不支持的类型时抛出
     */
    @Override
    @SuppressWarnings("checkstyle:CyclomaticComplexity")
    public ParquetValueReader<?> primitive(
        org.apache.iceberg.types.Type.PrimitiveType expected, PrimitiveType primitive) {
      ColumnDescriptor desc = type.getColumnDescription(currentPath());

      if (primitive.getOriginalType() != null) {
        switch (primitive.getOriginalType()) {
          case ENUM:
          case JSON:
          case UTF8:
            return new StringReader(desc);
          case INT_8:
          case INT_16:
          case INT_32:
            if (expected != null && expected.typeId() == Types.LongType.get().typeId()) {
              return new IntAsLongReader(desc);
            } else {
              return new UnboxedReader(desc);
            }
          case DATE:
          case INT_64:
          case TIMESTAMP_MICROS:
            return new UnboxedReader<>(desc);
          case TIMESTAMP_MILLIS:
            return new TimestampMillisReader(desc);
          case DECIMAL:
            DecimalLogicalTypeAnnotation decimal =
                (DecimalLogicalTypeAnnotation) primitive.getLogicalTypeAnnotation();
            switch (primitive.getPrimitiveTypeName()) {
              case BINARY:
              case FIXED_LEN_BYTE_ARRAY:
                return new BinaryDecimalReader(desc, decimal.getScale());
              case INT64:
                return new LongDecimalReader(desc, decimal.getPrecision(), decimal.getScale());
              case INT32:
                return new IntegerDecimalReader(desc, decimal.getPrecision(), decimal.getScale());
              default:
                throw new UnsupportedOperationException(
                    "Unsupported base type for decimal: " + primitive.getPrimitiveTypeName());
            }
          case BSON:
            return new ParquetValueReaders.ByteArrayReader(desc);
          default:
            throw new UnsupportedOperationException(
                "Unsupported logical type: " + primitive.getOriginalType());
        }
      }

      switch (primitive.getPrimitiveTypeName()) {
        case FIXED_LEN_BYTE_ARRAY:
        case BINARY:
          if (expected != null && expected.typeId() == TypeID.UUID) {
            return new UUIDReader(desc);
          }
          return new ParquetValueReaders.ByteArrayReader(desc);
        case INT32:
          if (expected != null && expected.typeId() == TypeID.LONG) {
            return new IntAsLongReader(desc);
          } else {
            return new UnboxedReader<>(desc);
          }
        case FLOAT:
          if (expected != null && expected.typeId() == TypeID.DOUBLE) {
            return new FloatAsDoubleReader(desc);
          } else {
            return new UnboxedReader<>(desc);
          }
        case BOOLEAN:
        case INT64:
        case DOUBLE:
          return new UnboxedReader<>(desc);
        case INT96:
          // Impala & Spark used to write timestamps as INT96 without a logical type. For backwards
          // compatibility we try to read INT96 as timestamps.
          return new TimestampInt96Reader(desc);
        default:
          throw new UnsupportedOperationException("Unsupported type: " + primitive);
      }
    }
    /** 执行 type 相关操作。 */
    protected MessageType type() {
      return type;
    }
  }

  /** 读取 Parquet BINARY/FIXED_LEN_BYTE_ARRAY 存储的 Decimal 并转为 Spark {@link Decimal}。 */
  private static class BinaryDecimalReader extends PrimitiveReader<Decimal> {
    private final int scale;

    BinaryDecimalReader(ColumnDescriptor desc, int scale) {
      super(desc);
      this.scale = scale;
    }
    /** 读取数据。 */
    @Override
    public Decimal read(Decimal ignored) {
      Binary binary = column.nextBinary();
      return Decimal.fromDecimal(new BigDecimal(new BigInteger(binary.getBytes()), scale));
    }
  }

  /** 读取 Parquet INT32 存储的 Decimal 并转为 Spark {@link Decimal}。 */
  private static class IntegerDecimalReader extends PrimitiveReader<Decimal> {
    private final int precision;
    private final int scale;

    IntegerDecimalReader(ColumnDescriptor desc, int precision, int scale) {
      super(desc);
      this.precision = precision;
      this.scale = scale;
    }
    /** 读取数据。 */
    @Override
    public Decimal read(Decimal ignored) {
      return Decimal.apply(column.nextInteger(), precision, scale);
    }
  }

  /** 读取 Parquet INT64 存储的 Decimal 并转为 Spark {@link Decimal}。 */
  private static class LongDecimalReader extends PrimitiveReader<Decimal> {
    private final int precision;
    private final int scale;

    LongDecimalReader(ColumnDescriptor desc, int precision, int scale) {
      super(desc);
      this.precision = precision;
      this.scale = scale;
    }
    /** 读取数据。 */
    @Override
    public Decimal read(Decimal ignored) {
      return Decimal.apply(column.nextLong(), precision, scale);
    }
  }

  /** 读取 Parquet 毫秒级时间戳并转为微秒级 long。 */
  private static class TimestampMillisReader extends UnboxedReader<Long> {
    TimestampMillisReader(ColumnDescriptor desc) {
      super(desc);
    }
    /** 读取数据。 */
    @Override
    public Long read(Long ignored) {
      return readLong();
    }
    /** 执行 readLong 相关操作。 */
    @Override
    public long readLong() {
      return 1000 * column.nextLong();
    }
  }

  /** 读取 Parquet INT96 时间戳（Impala/Spark 旧格式）并转为微秒级 long。 */
  private static class TimestampInt96Reader extends UnboxedReader<Long> {

    TimestampInt96Reader(ColumnDescriptor desc) {
      super(desc);
    }
    /** 读取数据。 */
    @Override
    public Long read(Long ignored) {
      return readLong();
    }
    /** 执行 readLong 相关操作。 */
    @Override
    public long readLong() {
      final ByteBuffer byteBuffer =
          column.nextBinary().toByteBuffer().order(ByteOrder.LITTLE_ENDIAN);
      return ParquetUtil.extractTimestampInt96(byteBuffer);
    }
  }

  /** 读取 Parquet 字符串并转为 Spark {@link UTF8String}。 */
  private static class StringReader extends PrimitiveReader<UTF8String> {
    StringReader(ColumnDescriptor desc) {
      super(desc);
    }
    /** 读取数据。 */
    @Override
    public UTF8String read(UTF8String ignored) {
      Binary binary = column.nextBinary();
      ByteBuffer buffer = binary.toByteBuffer();
      if (buffer.hasArray()) {
        return UTF8String.fromBytes(
            buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining());
      } else {
        return UTF8String.fromBytes(binary.getBytes());
      }
    }
  }

  /** 读取 Parquet UUID（固定长度字节数组）并转为字符串形式的 {@link UTF8String}。 */
  private static class UUIDReader extends PrimitiveReader<UTF8String> {
    UUIDReader(ColumnDescriptor desc) {
      super(desc);
    }
    /** 读取数据。 */
    @Override
    @SuppressWarnings("ByteBufferBackingArray")
    public UTF8String read(UTF8String ignored) {
      return UTF8String.fromString(UUIDUtil.convert(column.nextBinary().toByteBuffer()).toString());
    }
  }

  /** 读取 Parquet 重复字段为 Spark {@link ArrayData}，复用内部数组缓冲以减少分配。 */
  private static class ArrayReader<E> extends RepeatedReader<ArrayData, ReusableArrayData, E> {
    private int readPos = 0;
    private int writePos = 0;

    ArrayReader(int definitionLevel, int repetitionLevel, ParquetValueReader<E> reader) {
      super(definitionLevel, repetitionLevel, reader);
    }
    /** 创建 ListData 实例。 */
    @Override
    @SuppressWarnings("unchecked")
    protected ReusableArrayData newListData(ArrayData reuse) {
      this.readPos = 0;
      this.writePos = 0;

      if (reuse instanceof ReusableArrayData) {
        return (ReusableArrayData) reuse;
      } else {
        return new ReusableArrayData();
      }
    }
    /** 返回 Element 属性。 */
    @Override
    @SuppressWarnings("unchecked")
    protected E getElement(ReusableArrayData list) {
      E value = null;
      if (readPos < list.capacity()) {
        value = (E) list.values[readPos];
      }

      readPos += 1;

      return value;
    }
    /** 执行 addElement 相关操作。 */
    @Override
    protected void addElement(ReusableArrayData reused, E element) {
      if (writePos >= reused.capacity()) {
        reused.grow();
      }

      reused.values[writePos] = element;

      writePos += 1;
    }
    /** 执行 buildList 相关操作。 */
    @Override
    protected ArrayData buildList(ReusableArrayData list) {
      list.setNumElements(writePos);
      return list;
    }
  }

  private static class MapReader<K, V>
      extends RepeatedKeyValueReader<MapData, ReusableMapData, K, V> {
    private int readPos = 0;
    private int writePos = 0;

    private final ReusableEntry<K, V> entry = new ReusableEntry<>();
    private final ReusableEntry<K, V> nullEntry = new ReusableEntry<>();

    MapReader(
        int definitionLevel,
        int repetitionLevel,
        ParquetValueReader<K> keyReader,
        ParquetValueReader<V> valueReader) {
      super(definitionLevel, repetitionLevel, keyReader, valueReader);
    }
    /** 创建 MapData 实例。 */
    @Override
    @SuppressWarnings("unchecked")
    protected ReusableMapData newMapData(MapData reuse) {
      this.readPos = 0;
      this.writePos = 0;

      if (reuse instanceof ReusableMapData) {
        return (ReusableMapData) reuse;
      } else {
        return new ReusableMapData();
      }
    }
    /** 返回 Pair 属性。 */
    @Override
    @SuppressWarnings("unchecked")
    protected Map.Entry<K, V> getPair(ReusableMapData map) {
      Map.Entry<K, V> kv = nullEntry;
      if (readPos < map.capacity()) {
        entry.set((K) map.keys.values[readPos], (V) map.values.values[readPos]);
        kv = entry;
      }

      readPos += 1;

      return kv;
    }
    /** 执行 addPair 相关操作。 */
    @Override
    protected void addPair(ReusableMapData map, K key, V value) {
      if (writePos >= map.capacity()) {
        map.grow();
      }

      map.keys.values[writePos] = key;
      map.values.values[writePos] = value;

      writePos += 1;
    }
    /** 执行 buildMap 相关操作。 */
    @Override
    protected MapData buildMap(ReusableMapData map) {
      map.setNumElements(writePos);
      return map;
    }
  }

  private static class InternalRowReader extends StructReader<InternalRow, GenericInternalRow> {
    private final int numFields;

    InternalRowReader(List<Type> types, List<ParquetValueReader<?>> readers) {
      super(types, readers);
      this.numFields = readers.size();
    }
    /** 创建 StructData 实例。 */
    @Override
    protected GenericInternalRow newStructData(InternalRow reuse) {
      if (reuse instanceof GenericInternalRow) {
        return (GenericInternalRow) reuse;
      } else {
        return new GenericInternalRow(numFields);
      }
    }
    /** 返回 Field 属性。 */
    @Override
    protected Object getField(GenericInternalRow intermediate, int pos) {
      return intermediate.genericGet(pos);
    }
    /** 执行 buildStruct 相关操作。 */
    @Override
    protected InternalRow buildStruct(GenericInternalRow struct) {
      return struct;
    }
    /** 执行 set 相关操作。 */
    @Override
    protected void set(GenericInternalRow row, int pos, Object value) {
      row.update(pos, value);
    }
    /** 设置 Null 属性。 */
    @Override
    protected void setNull(GenericInternalRow row, int pos) {
      row.setNullAt(pos);
    }
    /** 设置 Boolean 属性。 */
    @Override
    protected void setBoolean(GenericInternalRow row, int pos, boolean value) {
      row.setBoolean(pos, value);
    }
    /** 设置 Integer 属性。 */
    @Override
    protected void setInteger(GenericInternalRow row, int pos, int value) {
      row.setInt(pos, value);
    }
    /** 设置 Long 属性。 */
    @Override
    protected void setLong(GenericInternalRow row, int pos, long value) {
      row.setLong(pos, value);
    }
    /** 设置 Float 属性。 */
    @Override
    protected void setFloat(GenericInternalRow row, int pos, float value) {
      row.setFloat(pos, value);
    }
    /** 设置 Double 属性。 */
    @Override
    protected void setDouble(GenericInternalRow row, int pos, double value) {
      row.setDouble(pos, value);
    }
  }

  private static class ReusableMapData extends MapData {
    private final ReusableArrayData keys;
    private final ReusableArrayData values;
    private int numElements;

    private ReusableMapData() {
      this.keys = new ReusableArrayData();
      this.values = new ReusableArrayData();
    }
    /** 执行 grow 相关操作。 */
    private void grow() {
      keys.grow();
      values.grow();
    }
    /** 执行 capacity 相关操作。 */
    private int capacity() {
      return keys.capacity();
    }
    /** 设置 NumElements 属性。 */
    public void setNumElements(int numElements) {
      this.numElements = numElements;
      keys.setNumElements(numElements);
      values.setNumElements(numElements);
    }
    /** 执行 numElements 相关操作。 */
    @Override
    public int numElements() {
      return numElements;
    }
    /** 返回副本。 */
    @Override
    public MapData copy() {
      return new ArrayBasedMapData(keyArray().copy(), valueArray().copy());
    }
    /** 执行 keyArray 相关操作。 */
    @Override
    public ReusableArrayData keyArray() {
      return keys;
    }
    /** 执行 valueArray 相关操作。 */
    @Override
    public ReusableArrayData valueArray() {
      return values;
    }
  }

  private static class ReusableArrayData extends ArrayData {
    private static final Object[] EMPTY = new Object[0];

    private Object[] values = EMPTY;
    private int numElements = 0;
    /** 执行 grow 相关操作。 */
    private void grow() {
      if (values.length == 0) {
        this.values = new Object[20];
      } else {
        Object[] old = values;
        this.values = new Object[old.length << 2];
        // copy the old array in case it has values that can be reused
        System.arraycopy(old, 0, values, 0, old.length);
      }
    }
    /** 执行 capacity 相关操作。 */
    private int capacity() {
      return values.length;
    }
    /** 设置 NumElements 属性。 */
    public void setNumElements(int numElements) {
      this.numElements = numElements;
    }
    /** 返回值。 */
    @Override
    public Object get(int ordinal, DataType dataType) {
      return values[ordinal];
    }
    /** 执行 numElements 相关操作。 */
    @Override
    public int numElements() {
      return numElements;
    }
    /** 返回副本。 */
    @Override
    public ArrayData copy() {
      return new GenericArrayData(array());
    }
    /** 执行 array 相关操作。 */
    @Override
    public Object[] array() {
      return Arrays.copyOfRange(values, 0, numElements);
    }
    /** 设置 NullAt 属性。 */
    @Override
    public void setNullAt(int i) {
      values[i] = null;
    }
    /** 执行 update 相关操作。 */
    @Override
    public void update(int ordinal, Object value) {
      values[ordinal] = value;
    }
    /** 判断是否 NullAt。 */
    @Override
    public boolean isNullAt(int ordinal) {
      return null == values[ordinal];
    }
    /** 返回 Boolean 属性。 */
    @Override
    public boolean getBoolean(int ordinal) {
      return (boolean) values[ordinal];
    }
    /** 返回 Byte 属性。 */
    @Override
    public byte getByte(int ordinal) {
      return (byte) values[ordinal];
    }
    /** 返回 Short 属性。 */
    @Override
    public short getShort(int ordinal) {
      return (short) values[ordinal];
    }
    /** 返回 Int 属性。 */
    @Override
    public int getInt(int ordinal) {
      return (int) values[ordinal];
    }
    /** 返回 Long 属性。 */
    @Override
    public long getLong(int ordinal) {
      return (long) values[ordinal];
    }
    /** 返回 Float 属性。 */
    @Override
    public float getFloat(int ordinal) {
      return (float) values[ordinal];
    }
    /** 返回 Double 属性。 */
    @Override
    public double getDouble(int ordinal) {
      return (double) values[ordinal];
    }
    /** 返回 Decimal 属性。 */
    @Override
    public Decimal getDecimal(int ordinal, int precision, int scale) {
      return (Decimal) values[ordinal];
    }
    /** 返回 UTF8String 属性。 */
    @Override
    public UTF8String getUTF8String(int ordinal) {
      return (UTF8String) values[ordinal];
    }
    /** 返回 Binary 属性。 */
    @Override
    public byte[] getBinary(int ordinal) {
      return (byte[]) values[ordinal];
    }
    /** 返回 Interval 属性。 */
    @Override
    public CalendarInterval getInterval(int ordinal) {
      return (CalendarInterval) values[ordinal];
    }
    /** 返回 Struct 属性。 */
    @Override
    public InternalRow getStruct(int ordinal, int numFields) {
      return (InternalRow) values[ordinal];
    }
    /** 返回 Array 属性。 */
    @Override
    public ArrayData getArray(int ordinal) {
      return (ArrayData) values[ordinal];
    }
    /** 返回 Map 属性。 */
    @Override
    public MapData getMap(int ordinal) {
      return (MapData) values[ordinal];
    }
  }
}
