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
 * Iceberg 与 Spark 数据格式之间的读写转换组件的读取器，负责从底层读取数据并转换为 Spark 内部格式。
 *
 * <p>所属模块：iceberg-spark v3.2。 类型：类 SparkParquetReaders。
 *
 * <p>上下游：被 SparkScan/SparkWrite 调用，依赖 Iceberg 文件格式读取/写入 API。
 */
public class SparkParquetReaders {
  /** 构造 SparkParquetReaders 实例。 */
  private SparkParquetReaders() {}

  /** 构造并返回目标对象。 */
  public static ParquetValueReader<InternalRow> buildReader(
      Schema expectedSchema, MessageType fileSchema) {
    return buildReader(expectedSchema, fileSchema, ImmutableMap.of());
  }

  /** 构造并返回目标对象。 */
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
              /** 执行该方法的具体逻辑。 */
              fileSchema,
              new FallbackReadBuilder(fileSchema, idToConstant));
    }
  }

  /**
   * Iceberg 与 Spark 数据格式之间的读写转换组件的构建器，负责分步骤构造目标对象。
   *
   * <p>所属模块：iceberg-spark v3.2。 类型：类 FallbackReadBuilder。
   *
   * <p>设计意图：建造者模式，分离复杂对象的构造与表示。
   *
   * <p>上下游：被 SparkScan/SparkWrite 调用，依赖 Iceberg 文件格式读取/写入 API。
   */
  private static class FallbackReadBuilder extends ReadBuilder {
    FallbackReadBuilder(MessageType type, Map<Integer, ?> idToConstant) {
      super(type, idToConstant);
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @param expected 参数
     * @param message 参数
     * @param fieldReaders 参数
     * @return 结果对象
     */
    @Override
    public ParquetValueReader<?> message(
        Types.StructType expected, MessageType message, List<ParquetValueReader<?>> fieldReaders) {
      // the top level matches by ID, but the remaining IDs are missing
      return super.struct(expected, message, fieldReaders);
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @param ignored 参数
     * @param struct 参数
     * @param fieldReaders 参数
     * @return 结果对象
     */
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

      /** 执行该方法的具体逻辑。 */
      return new InternalRowReader(types, newFields);
    }
  }

  /**
   * Iceberg 与 Spark 数据格式之间的读写转换组件的构建器，负责分步骤构造目标对象。
   *
   * <p>所属模块：iceberg-spark v3.2。 类型：类 ReadBuilder。
   *
   * <p>设计意图：建造者模式，分离复杂对象的构造与表示。
   *
   * <p>上下游：被 SparkScan/SparkWrite 调用，依赖 Iceberg 文件格式读取/写入 API。
   */
  private static class ReadBuilder extends TypeWithSchemaVisitor<ParquetValueReader<?>> {
    private final MessageType type;
    private final Map<Integer, ?> idToConstant;

    ReadBuilder(MessageType type, Map<Integer, ?> idToConstant) {
      this.type = type;
      this.idToConstant = idToConstant;
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @param expected 参数
     * @param message 参数
     * @param fieldReaders 参数
     * @return 结果对象
     */
    @Override
    public ParquetValueReader<?> message(
        Types.StructType expected, MessageType message, List<ParquetValueReader<?>> fieldReaders) {
      return struct(expected, message.asGroupType(), fieldReaders);
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @param expected 参数
     * @param struct 参数
     * @param fieldReaders 参数
     * @return 结果对象
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

      /** 执行该方法的具体逻辑。 */
      return new InternalRowReader(types, reorderedFields);
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @param expectedList 参数
     * @param array 参数
     * @param elementReader 参数
     * @return 结果对象
     */
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

    /**
     * 执行该方法的具体逻辑。
     *
     * @param expectedMap 参数
     * @param map 参数
     * @param keyReader 参数
     * @param valueReader 参数
     * @return 结果对象
     */
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
     * 执行该方法的具体逻辑。
     *
     * @param expected 参数
     * @param primitive 参数
     * @return 结果对象
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
            /** 执行该方法的具体逻辑。 */
            return new StringReader(desc);
          case INT_8:
          case INT_16:
          case INT_32:
            if (expected != null && expected.typeId() == Types.LongType.get().typeId()) {
              /** 执行该方法的具体逻辑。 */
              return new IntAsLongReader(desc);
            } else {
              /** 执行该方法的具体逻辑。 */
              return new UnboxedReader(desc);
            }
          case DATE:
          case INT_64:
          case TIMESTAMP_MICROS:
            return new UnboxedReader<>(desc);
          case TIMESTAMP_MILLIS:
            /** 执行该方法的具体逻辑。 */
            return new TimestampMillisReader(desc);
          case DECIMAL:
            DecimalLogicalTypeAnnotation decimal =
                (DecimalLogicalTypeAnnotation) primitive.getLogicalTypeAnnotation();
            switch (primitive.getPrimitiveTypeName()) {
              case BINARY:
              case FIXED_LEN_BYTE_ARRAY:
                /** 执行该方法的具体逻辑。 */
                return new BinaryDecimalReader(desc, decimal.getScale());
              case INT64:
                /** 执行该方法的具体逻辑。 */
                return new LongDecimalReader(desc, decimal.getPrecision(), decimal.getScale());
              case INT32:
                /** 执行该方法的具体逻辑。 */
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
            /** 执行该方法的具体逻辑。 */
            return new UUIDReader(desc);
          }
          return new ParquetValueReaders.ByteArrayReader(desc);
        case INT32:
          if (expected != null && expected.typeId() == TypeID.LONG) {
            /** 执行该方法的具体逻辑。 */
            return new IntAsLongReader(desc);
          } else {
            return new UnboxedReader<>(desc);
          }
        case FLOAT:
          if (expected != null && expected.typeId() == TypeID.DOUBLE) {
            /** 执行该方法的具体逻辑。 */
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
          /** 执行该方法的具体逻辑。 */
          return new TimestampInt96Reader(desc);
        default:
          throw new UnsupportedOperationException("Unsupported type: " + primitive);
      }
    }

    /** 执行该方法的具体逻辑。 */
    protected MessageType type() {
      return type;
    }
  }

  /**
   * Iceberg 与 Spark 数据格式之间的读写转换组件的读取器，负责从底层读取数据并转换为 Spark 内部格式。
   *
   * <p>所属模块：iceberg-spark v3.2。 类型：类 BinaryDecimalReader。
   *
   * <p>上下游：被 SparkScan/SparkWrite 调用，依赖 Iceberg 文件格式读取/写入 API。
   */
  private static class BinaryDecimalReader extends PrimitiveReader<Decimal> {
    private final int scale;

    BinaryDecimalReader(ColumnDescriptor desc, int scale) {
      super(desc);
      this.scale = scale;
    }

    /**
     * 读取数据。
     *
     * @param ignored 参数
     * @return 结果对象
     */
    @Override
    public Decimal read(Decimal ignored) {
      Binary binary = column.nextBinary();
      return Decimal.fromDecimal(new BigDecimal(new BigInteger(binary.getBytes()), scale));
    }
  }

  /**
   * Iceberg 与 Spark 数据格式之间的读写转换组件的读取器，负责从底层读取数据并转换为 Spark 内部格式。
   *
   * <p>所属模块：iceberg-spark v3.2。 类型：类 IntegerDecimalReader。
   *
   * <p>上下游：被 SparkScan/SparkWrite 调用，依赖 Iceberg 文件格式读取/写入 API。
   */
  private static class IntegerDecimalReader extends PrimitiveReader<Decimal> {
    private final int precision;
    private final int scale;

    IntegerDecimalReader(ColumnDescriptor desc, int precision, int scale) {
      super(desc);
      this.precision = precision;
      this.scale = scale;
    }

    /**
     * 读取数据。
     *
     * @param ignored 参数
     * @return 结果对象
     */
    @Override
    public Decimal read(Decimal ignored) {
      return Decimal.apply(column.nextInteger(), precision, scale);
    }
  }

  /**
   * Iceberg 与 Spark 数据格式之间的读写转换组件的读取器，负责从底层读取数据并转换为 Spark 内部格式。
   *
   * <p>所属模块：iceberg-spark v3.2。 类型：类 LongDecimalReader。
   *
   * <p>上下游：被 SparkScan/SparkWrite 调用，依赖 Iceberg 文件格式读取/写入 API。
   */
  private static class LongDecimalReader extends PrimitiveReader<Decimal> {
    private final int precision;
    private final int scale;

    LongDecimalReader(ColumnDescriptor desc, int precision, int scale) {
      super(desc);
      this.precision = precision;
      this.scale = scale;
    }

    /**
     * 读取数据。
     *
     * @param ignored 参数
     * @return 结果对象
     */
    @Override
    public Decimal read(Decimal ignored) {
      return Decimal.apply(column.nextLong(), precision, scale);
    }
  }

  /**
   * Iceberg 与 Spark 数据格式之间的读写转换组件的读取器，负责从底层读取数据并转换为 Spark 内部格式。
   *
   * <p>所属模块：iceberg-spark v3.2。 类型：类 TimestampMillisReader。
   *
   * <p>上下游：被 SparkScan/SparkWrite 调用，依赖 Iceberg 文件格式读取/写入 API。
   */
  private static class TimestampMillisReader extends UnboxedReader<Long> {
    TimestampMillisReader(ColumnDescriptor desc) {
      super(desc);
    }

    /**
     * 读取数据。
     *
     * @param ignored 参数
     * @return 结果对象
     */
    @Override
    public Long read(Long ignored) {
      return readLong();
    }

    /**
     * 读取数据。
     *
     * @return 结果对象
     */
    @Override
    public long readLong() {
      return 1000 * column.nextLong();
    }
  }

  /**
   * Iceberg 与 Spark 数据格式之间的读写转换组件的读取器，负责从底层读取数据并转换为 Spark 内部格式。
   *
   * <p>所属模块：iceberg-spark v3.2。 类型：类 TimestampInt96Reader。
   *
   * <p>上下游：被 SparkScan/SparkWrite 调用，依赖 Iceberg 文件格式读取/写入 API。
   */
  private static class TimestampInt96Reader extends UnboxedReader<Long> {

    TimestampInt96Reader(ColumnDescriptor desc) {
      super(desc);
    }

    /**
     * 读取数据。
     *
     * @param ignored 参数
     * @return 结果对象
     */
    @Override
    public Long read(Long ignored) {
      return readLong();
    }

    /**
     * 读取数据。
     *
     * @return 结果对象
     */
    @Override
    public long readLong() {
      final ByteBuffer byteBuffer =
          column.nextBinary().toByteBuffer().order(ByteOrder.LITTLE_ENDIAN);
      return ParquetUtil.extractTimestampInt96(byteBuffer);
    }
  }

  /**
   * Iceberg 与 Spark 数据格式之间的读写转换组件的读取器，负责从底层读取数据并转换为 Spark 内部格式。
   *
   * <p>所属模块：iceberg-spark v3.2。 类型：类 StringReader。
   *
   * <p>上下游：被 SparkScan/SparkWrite 调用，依赖 Iceberg 文件格式读取/写入 API。
   */
  private static class StringReader extends PrimitiveReader<UTF8String> {
    StringReader(ColumnDescriptor desc) {
      super(desc);
    }

    /**
     * 读取数据。
     *
     * @param ignored 参数
     * @return 结果对象
     */
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

  /**
   * Iceberg 与 Spark 数据格式之间的读写转换组件的读取器，负责从底层读取数据并转换为 Spark 内部格式。
   *
   * <p>所属模块：iceberg-spark v3.2。 类型：类 UUIDReader。
   *
   * <p>上下游：被 SparkScan/SparkWrite 调用，依赖 Iceberg 文件格式读取/写入 API。
   */
  private static class UUIDReader extends PrimitiveReader<UTF8String> {
    UUIDReader(ColumnDescriptor desc) {
      super(desc);
    }

    /**
     * 读取数据。
     *
     * @param ignored 参数
     * @return 结果对象
     */
    @Override
    @SuppressWarnings("ByteBufferBackingArray")
    public UTF8String read(UTF8String ignored) {
      return UTF8String.fromString(UUIDUtil.convert(column.nextBinary().toByteBuffer()).toString());
    }
  }

  /**
   * Iceberg 与 Spark 数据格式之间的读写转换组件的读取器，负责从底层读取数据并转换为 Spark 内部格式。
   *
   * <p>所属模块：iceberg-spark v3.2。 类型：类 ArrayReader。
   *
   * <p>上下游：被 SparkScan/SparkWrite 调用，依赖 Iceberg 文件格式读取/写入 API。
   */
  private static class ArrayReader<E> extends RepeatedReader<ArrayData, ReusableArrayData, E> {
    private int readPos = 0;
    private int writePos = 0;

    ArrayReader(int definitionLevel, int repetitionLevel, ParquetValueReader<E> reader) {
      super(definitionLevel, repetitionLevel, reader);
    }

    /** 执行该方法的具体逻辑。 */
    @Override
    @SuppressWarnings("unchecked")
    protected ReusableArrayData newListData(ArrayData reuse) {
      this.readPos = 0;
      this.writePos = 0;

      if (reuse instanceof ReusableArrayData) {
        return (ReusableArrayData) reuse;
      } else {
        /** 执行该方法的具体逻辑。 */
        return new ReusableArrayData();
      }
    }

    /** 返回element。 */
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

    /** 添加元素或项。 */
    @Override
    protected void addElement(ReusableArrayData reused, E element) {
      if (writePos >= reused.capacity()) {
        reused.grow();
      }

      reused.values[writePos] = element;

      writePos += 1;
    }

    /** 构造并返回目标对象。 */
    @Override
    protected ArrayData buildList(ReusableArrayData list) {
      list.setNumElements(writePos);
      return list;
    }
  }

  /**
   * Iceberg 与 Spark 数据格式之间的读写转换组件的读取器，负责从底层读取数据并转换为 Spark 内部格式。
   *
   * <p>所属模块：iceberg-spark v3.2。 类型：类 MapReader。
   *
   * <p>上下游：被 SparkScan/SparkWrite 调用，依赖 Iceberg 文件格式读取/写入 API。
   */
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

    /** 执行该方法的具体逻辑。 */
    @Override
    @SuppressWarnings("unchecked")
    protected ReusableMapData newMapData(MapData reuse) {
      this.readPos = 0;
      this.writePos = 0;

      if (reuse instanceof ReusableMapData) {
        return (ReusableMapData) reuse;
      } else {
        /** 执行该方法的具体逻辑。 */
        return new ReusableMapData();
      }
    }

    /** 返回pair。 */
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

    /** 添加元素或项。 */
    @Override
    protected void addPair(ReusableMapData map, K key, V value) {
      if (writePos >= map.capacity()) {
        map.grow();
      }

      map.keys.values[writePos] = key;
      map.values.values[writePos] = value;

      writePos += 1;
    }

    /** 构造并返回目标对象。 */
    @Override
    protected MapData buildMap(ReusableMapData map) {
      map.setNumElements(writePos);
      return map;
    }
  }

  /**
   * Iceberg 与 Spark 数据格式之间的读写转换组件的读取器，负责从底层读取数据并转换为 Spark 内部格式。
   *
   * <p>所属模块：iceberg-spark v3.2。 类型：类 InternalRowReader。
   *
   * <p>上下游：被 SparkScan/SparkWrite 调用，依赖 Iceberg 文件格式读取/写入 API。
   */
  private static class InternalRowReader extends StructReader<InternalRow, GenericInternalRow> {
    private final int numFields;

    InternalRowReader(List<Type> types, List<ParquetValueReader<?>> readers) {
      super(types, readers);
      this.numFields = readers.size();
    }

    /** 执行该方法的具体逻辑。 */
    @Override
    protected GenericInternalRow newStructData(InternalRow reuse) {
      if (reuse instanceof GenericInternalRow) {
        return (GenericInternalRow) reuse;
      } else {
        /** 执行该方法的具体逻辑。 */
        return new GenericInternalRow(numFields);
      }
    }

    /** 返回field。 */
    @Override
    protected Object getField(GenericInternalRow intermediate, int pos) {
      return intermediate.genericGet(pos);
    }

    /** 构造并返回目标对象。 */
    @Override
    protected InternalRow buildStruct(GenericInternalRow struct) {
      return struct;
    }

    /** 执行该方法的具体逻辑。 */
    @Override
    protected void set(GenericInternalRow row, int pos, Object value) {
      row.update(pos, value);
    }

    /** 设置null。 */
    @Override
    protected void setNull(GenericInternalRow row, int pos) {
      row.setNullAt(pos);
    }

    /** 设置boolean。 */
    @Override
    protected void setBoolean(GenericInternalRow row, int pos, boolean value) {
      row.setBoolean(pos, value);
    }

    /** 设置integer。 */
    @Override
    protected void setInteger(GenericInternalRow row, int pos, int value) {
      row.setInt(pos, value);
    }

    /** 设置long。 */
    @Override
    protected void setLong(GenericInternalRow row, int pos, long value) {
      row.setLong(pos, value);
    }

    /** 设置float。 */
    @Override
    protected void setFloat(GenericInternalRow row, int pos, float value) {
      row.setFloat(pos, value);
    }

    /** 设置double。 */
    @Override
    protected void setDouble(GenericInternalRow row, int pos, double value) {
      row.setDouble(pos, value);
    }
  }

  /**
   * Iceberg 与 Spark 数据格式之间的读写转换组件。
   *
   * <p>所属模块：iceberg-spark v3.2。 类型：类 ReusableMapData。
   *
   * <p>上下游：被 SparkScan/SparkWrite 调用，依赖 Iceberg 文件格式读取/写入 API。
   */
  private static class ReusableMapData extends MapData {
    private final ReusableArrayData keys;
    private final ReusableArrayData values;
    private int numElements;

    /** 构造 ReusableMapData 实例。 */
    private ReusableMapData() {
      this.keys = new ReusableArrayData();
      this.values = new ReusableArrayData();
    }

    /** 执行该方法的具体逻辑。 */
    private void grow() {
      keys.grow();
      values.grow();
    }

    /** 执行该方法的具体逻辑。 */
    private int capacity() {
      return keys.capacity();
    }

    /** 设置numelements。 */
    public void setNumElements(int numElements) {
      this.numElements = numElements;
      keys.setNumElements(numElements);
      values.setNumElements(numElements);
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @return 结果对象
     */
    @Override
    public int numElements() {
      return numElements;
    }

    /**
     * 返回当前对象的副本。
     *
     * @return 结果对象
     */
    @Override
    public MapData copy() {
      /** 执行该方法的具体逻辑。 */
      return new ArrayBasedMapData(keyArray().copy(), valueArray().copy());
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @return 结果对象
     */
    @Override
    public ReusableArrayData keyArray() {
      return keys;
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @return 结果对象
     */
    @Override
    public ReusableArrayData valueArray() {
      return values;
    }
  }

  /**
   * Iceberg 与 Spark 数据格式之间的读写转换组件。
   *
   * <p>所属模块：iceberg-spark v3.2。 类型：类 ReusableArrayData。
   *
   * <p>上下游：被 SparkScan/SparkWrite 调用，依赖 Iceberg 文件格式读取/写入 API。
   */
  private static class ReusableArrayData extends ArrayData {
    private static final Object[] EMPTY = new Object[0];

    private Object[] values = EMPTY;
    private int numElements = 0;

    /** 执行该方法的具体逻辑。 */
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

    /** 执行该方法的具体逻辑。 */
    private int capacity() {
      return values.length;
    }

    /** 设置numelements。 */
    public void setNumElements(int numElements) {
      this.numElements = numElements;
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @param ordinal 参数
     * @param dataType 参数
     * @return 对应结果
     */
    @Override
    public Object get(int ordinal, DataType dataType) {
      return values[ordinal];
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @return 结果对象
     */
    @Override
    public int numElements() {
      return numElements;
    }

    /**
     * 返回当前对象的副本。
     *
     * @return 结果对象
     */
    @Override
    public ArrayData copy() {
      /** 执行该方法的具体逻辑。 */
      return new GenericArrayData(array());
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @return 结果对象
     */
    @Override
    public Object[] array() {
      return Arrays.copyOfRange(values, 0, numElements);
    }

    /** 设置nullat。 */
    @Override
    public void setNullAt(int i) {
      values[i] = null;
    }

    /**
     * 更新数据或状态。
     *
     * @param ordinal 参数
     * @param value 参数
     */
    @Override
    public void update(int ordinal, Object value) {
      values[ordinal] = value;
    }

    /** 判断是否nullat。 */
    @Override
    public boolean isNullAt(int ordinal) {
      return null == values[ordinal];
    }

    /** 返回boolean。 */
    @Override
    public boolean getBoolean(int ordinal) {
      return (boolean) values[ordinal];
    }

    /** 返回byte。 */
    @Override
    public byte getByte(int ordinal) {
      return (byte) values[ordinal];
    }

    /** 返回short。 */
    @Override
    public short getShort(int ordinal) {
      return (short) values[ordinal];
    }

    /** 返回int。 */
    @Override
    public int getInt(int ordinal) {
      return (int) values[ordinal];
    }

    /** 返回long。 */
    @Override
    public long getLong(int ordinal) {
      return (long) values[ordinal];
    }

    /** 返回float。 */
    @Override
    public float getFloat(int ordinal) {
      return (float) values[ordinal];
    }

    /** 返回double。 */
    @Override
    public double getDouble(int ordinal) {
      return (double) values[ordinal];
    }

    /** 返回decimal。 */
    @Override
    public Decimal getDecimal(int ordinal, int precision, int scale) {
      return (Decimal) values[ordinal];
    }

    /** 返回utf8string。 */
    @Override
    public UTF8String getUTF8String(int ordinal) {
      return (UTF8String) values[ordinal];
    }

    /** 返回binary。 */
    @Override
    public byte[] getBinary(int ordinal) {
      return (byte[]) values[ordinal];
    }

    /** 返回interval。 */
    @Override
    public CalendarInterval getInterval(int ordinal) {
      return (CalendarInterval) values[ordinal];
    }

    /** 返回struct。 */
    @Override
    public InternalRow getStruct(int ordinal, int numFields) {
      return (InternalRow) values[ordinal];
    }

    /** 返回array。 */
    @Override
    public ArrayData getArray(int ordinal) {
      return (ArrayData) values[ordinal];
    }

    /** 返回map。 */
    @Override
    public MapData getMap(int ordinal) {
      return (MapData) values[ordinal];
    }
  }
}
