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
package org.apache.iceberg.parquet;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData.Fixed;
import org.apache.avro.generic.GenericData.Record;
import org.apache.avro.util.Utf8;
import org.apache.iceberg.avro.AvroSchemaUtil;
import org.apache.iceberg.parquet.ParquetValueReaders.BytesReader;
import org.apache.iceberg.parquet.ParquetValueReaders.FloatAsDoubleReader;
import org.apache.iceberg.parquet.ParquetValueReaders.IntAsLongReader;
import org.apache.iceberg.parquet.ParquetValueReaders.IntegerAsDecimalReader;
import org.apache.iceberg.parquet.ParquetValueReaders.ListReader;
import org.apache.iceberg.parquet.ParquetValueReaders.LongAsDecimalReader;
import org.apache.iceberg.parquet.ParquetValueReaders.MapReader;
import org.apache.iceberg.parquet.ParquetValueReaders.StructReader;
import org.apache.iceberg.parquet.ParquetValueReaders.UnboxedReader;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.types.Type.TypeID;
import org.apache.iceberg.types.Types;
import org.apache.iceberg.util.UUIDUtil;
import org.apache.parquet.column.ColumnDescriptor;
import org.apache.parquet.schema.GroupType;
import org.apache.parquet.schema.LogicalTypeAnnotation.DecimalLogicalTypeAnnotation;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.PrimitiveType;
import org.apache.parquet.schema.Type;

/**
 * 文件级说明：基于 Avro Record 的 Parquet 读取器实现。
 *
 * <p>所属模块：iceberg-parquet（Avro 适配层，位于 org.apache.iceberg.parquet 包）。
 *
 * <p>职责：将 Parquet 列式数据读取为 Avro {@link Record}，处理 Iceberg 类型与 Avro/Parquet
 * 类型的映射（decimal、UUID、string、fixed 等）。
 *
 * <p>设计意图：通过 {@link ReadBuilder}（继承 TypeWithSchemaVisitor）联合遍历 Iceberg schema 与 Parquet
 * schema，为每个字段构造对应 Reader。与 BaseParquetReaders 不同，本类面向 Avro 内存模型，使用 AvroSchemaUtil 将 Iceberg 类型转为
 * Avro schema。
 *
 * <p>上下游关系：被 Parquet 写入/读取流程中需要 Avro 模型的场景调用； 依赖 ParquetValueReaders（基础读取器）、AvroSchemaUtil（类型转换）。
 */
public class ParquetAvroValueReaders {
  private ParquetAvroValueReaders() {}

  /**
   * 构造 Avro Record 的 Parquet 读取器。
   *
   * @param expectedSchema 期望读取的 Iceberg schema
   * @param fileSchema Parquet 文件实际 schema
   * @return 读取结果为 Avro {@link Record} 的读取器
   */
  @SuppressWarnings("unchecked")
  public static ParquetValueReader<Record> buildReader(
      org.apache.iceberg.Schema expectedSchema, MessageType fileSchema) {
    return (ParquetValueReader<Record>)
        TypeWithSchemaVisitor.visit(
            expectedSchema.asStruct(), fileSchema, new ReadBuilder(expectedSchema, fileSchema));
  }

  /**
   * 读取器构建器：联合遍历 Iceberg schema 与 Parquet schema，按 Avro 模型构造读取器树。
   *
   * <p>设计要点：构造时通过 AvroSchemaUtil 将 Iceberg 类型转为 Avro schema， 供 FixedReader 等需要 Avro schema 的读取器使用。
   */
  private static class ReadBuilder extends TypeWithSchemaVisitor<ParquetValueReader<?>> {
    private final org.apache.iceberg.Schema schema;
    private final Map<org.apache.iceberg.types.Type, Schema> avroSchemas;
    private final MessageType type;

    ReadBuilder(org.apache.iceberg.Schema schema, MessageType type) {
      this.schema = schema;
      this.avroSchemas = AvroSchemaUtil.convertTypes(schema.asStruct(), type.getName());
      this.type = type;
    }

    @Override
    public ParquetValueReader<?> message(
        Types.StructType expected, MessageType message, List<ParquetValueReader<?>> fieldReaders) {
      return struct(expected, message.asGroupType(), fieldReaders);
    }

    @Override
    public ParquetValueReader<?> struct(
        Types.StructType expected, GroupType struct, List<ParquetValueReader<?>> fieldReaders) {
      Schema avroSchema = avroSchemas.get(expected);

      // match the expected struct's order
      Map<Integer, ParquetValueReader<?>> readersById = Maps.newHashMap();
      Map<Integer, Type> typesById = Maps.newHashMap();
      List<Type> fields = struct.getFields();
      for (int i = 0; i < fields.size(); i += 1) {
        Type fieldType = fields.get(i);
        int fieldD = type.getMaxDefinitionLevel(path(fieldType.getName())) - 1;
        int id = fieldType.getId().intValue();
        readersById.put(id, ParquetValueReaders.option(fieldType, fieldD, fieldReaders.get(i)));
        typesById.put(id, fieldType);
      }

      List<Types.NestedField> expectedFields =
          expected != null ? expected.fields() : ImmutableList.of();
      List<ParquetValueReader<?>> reorderedFields =
          Lists.newArrayListWithExpectedSize(expectedFields.size());
      List<Type> types = Lists.newArrayListWithExpectedSize(expectedFields.size());
      for (Types.NestedField field : expectedFields) {
        int id = field.fieldId();
        ParquetValueReader<?> reader = readersById.get(id);
        if (reader != null) {
          reorderedFields.add(reader);
          types.add(typesById.get(id));
        } else {
          reorderedFields.add(ParquetValueReaders.nulls());
          types.add(null);
        }
      }

      return new RecordReader(types, reorderedFields, avroSchema);
    }

    @Override
    public ParquetValueReader<?> list(
        Types.ListType expectedList, GroupType array, ParquetValueReader<?> elementReader) {
      String[] repeatedPath = currentPath();

      int repeatedD = type.getMaxDefinitionLevel(repeatedPath) - 1;
      int repeatedR = type.getMaxRepetitionLevel(repeatedPath) - 1;

      Type elementType = ParquetSchemaUtil.determineListElementType(array);
      int elementD = type.getMaxDefinitionLevel(path(elementType.getName())) - 1;

      return new ListReader<>(
          repeatedD, repeatedR, ParquetValueReaders.option(elementType, elementD, elementReader));
    }

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
     * 构建原始类型读取器：按 Parquet 逻辑类型/原始类型分派。
     *
     * <p>逻辑：处理 UTF8（String/Utf8 区分 map key）、DECIMAL（INT32/INT64/BINARY）、 FIXED_LEN_BYTE_ARRAY（需
     * Avro schema）、BINARY（Bytes）、INT32→LONG/FLOAT→DOUBLE 转换等。
     *
     * @param expected 期望的 Iceberg 原始类型
     * @param primitive Parquet 原始类型
     * @return 对应的读取器
     * @throws UnsupportedOperationException 若类型不受支持
     */
    @Override
    public ParquetValueReader<?> primitive(
        org.apache.iceberg.types.Type.PrimitiveType expected, PrimitiveType primitive) {
      ColumnDescriptor desc = type.getColumnDescription(currentPath());

      boolean isMapKey = fieldNames.contains("key");

      if (primitive.getOriginalType() != null) {
        switch (primitive.getOriginalType()) {
          case ENUM:
          case JSON:
          case UTF8:
            if (isMapKey) {
              return new StringReader(desc);
            }
            return new Utf8Reader(desc);
          case DATE:
          case INT_8:
          case INT_16:
          case INT_32:
          case INT_64:
          case TIME_MICROS:
          case TIMESTAMP_MICROS:
            return new UnboxedReader<>(desc);
          case TIME_MILLIS:
            return new TimeMillisReader(desc);
          case TIMESTAMP_MILLIS:
            return new TimestampMillisReader(desc);
          case DECIMAL:
            DecimalLogicalTypeAnnotation decimal =
                (DecimalLogicalTypeAnnotation) primitive.getLogicalTypeAnnotation();
            switch (primitive.getPrimitiveTypeName()) {
              case BINARY:
              case FIXED_LEN_BYTE_ARRAY:
                return new DecimalReader(desc, decimal.getScale());
              case INT64:
                return new LongAsDecimalReader(desc, decimal.getScale());
              case INT32:
                return new IntegerAsDecimalReader(desc, decimal.getScale());
              default:
                throw new UnsupportedOperationException(
                    "Unsupported base type for decimal: " + primitive.getPrimitiveTypeName());
            }
          case BSON:
            return new BytesReader(desc);
          default:
            throw new UnsupportedOperationException(
                "Unsupported logical type: " + primitive.getOriginalType());
        }
      }

      switch (primitive.getPrimitiveTypeName()) {
        case FIXED_LEN_BYTE_ARRAY:
          int fieldId = primitive.getId().intValue();
          Schema avroSchema = AvroSchemaUtil.convert(schema.findType(fieldId));
          return new FixedReader(desc, avroSchema);
        case BINARY:
          return new BytesReader(desc);
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
        default:
          throw new UnsupportedOperationException("Unsupported type: " + primitive);
      }
    }
  }

  /** Decimal 读取器：将 Parquet Binary 转为 {@link BigDecimal}（按 scale 解码）。 */
  static class DecimalReader extends ParquetValueReaders.PrimitiveReader<BigDecimal> {
    private final int scale;

    DecimalReader(ColumnDescriptor desc, int scale) {
      super(desc);
      this.scale = scale;
    }

    @Override
    public BigDecimal read(BigDecimal ignored) {
      return new BigDecimal(new BigInteger(column.nextBinary().getBytesUnsafe()), scale);
    }
  }

  /** String 读取器：将 Parquet Binary 按 UTF-8 解码为 Java String。 */
  static class StringReader extends ParquetValueReaders.PrimitiveReader<String> {
    StringReader(ColumnDescriptor desc) {
      super(desc);
    }

    @Override
    public String read(String ignored) {
      return column.nextBinary().toStringUsingUTF8();
    }
  }

  /**
   * Utf8 读取器：将 Parquet Binary 解码为 Avro {@link Utf8}。
   *
   * <p>设计要点：始终拷贝字节到 Utf8 内部数组，避免复用常量 Binary 的 buffer 导致数据损坏。
   */
  static class Utf8Reader extends ParquetValueReaders.PrimitiveReader<Utf8> {
    Utf8Reader(ColumnDescriptor desc) {
      super(desc);
    }

    @Override
    public Utf8 read(Utf8 reuse) {
      Utf8 utf8;
      if (reuse != null) {
        utf8 = reuse;
      } else {
        utf8 = new Utf8();
      }

      // use a byte buffer because it never results in a copy
      ByteBuffer buffer = column.nextBinary().toByteBuffer();

      // always copy the bytes into the Utf8. for constant binary data backed by an array starting
      // at 0, it is possible to wrap the bytes in a Utf8, but reusing that Utf8 could corrupt the
      // constant binary if its backing buffer is copied to.
      utf8.setByteLength(buffer.remaining());
      buffer.get(utf8.getBytes(), 0, buffer.remaining());

      return utf8;
    }
  }

  /** UUID 读取器：将 Parquet Binary（16 字节）转为 {@link UUID}。 */
  static class UUIDReader extends ParquetValueReaders.PrimitiveReader<UUID> {
    UUIDReader(ColumnDescriptor desc) {
      super(desc);
    }

    @Override
    public UUID read(UUID ignored) {
      return UUIDUtil.convert(column.nextBinary().toByteBuffer());
    }
  }

  /** Fixed 读取器：将 Parquet FIXED_LEN_BYTE_ARRAY 读入 Avro {@link Fixed}。 */
  static class FixedReader extends ParquetValueReaders.PrimitiveReader<Fixed> {
    private final Schema schema;

    FixedReader(ColumnDescriptor desc, Schema schema) {
      super(desc);
      this.schema = schema;
    }

    @Override
    public Fixed read(Fixed reuse) {
      Fixed fixed;
      if (reuse != null) {
        fixed = reuse;
      } else {
        fixed = new Fixed(schema);
      }

      column.nextBinary().toByteBuffer().get(fixed.bytes());

      return fixed;
    }
  }

  /** Time 读取器（毫秒）：将毫秒值转为微秒（×1000）。 */
  public static class TimeMillisReader extends UnboxedReader<Long> {
    TimeMillisReader(ColumnDescriptor desc) {
      super(desc);
    }

    @Override
    public long readLong() {
      return 1000 * column.nextLong();
    }
  }

  /** Timestamp 读取器（毫秒）：将毫秒值转为微秒（×1000）。 */
  public static class TimestampMillisReader extends UnboxedReader<Long> {
    TimestampMillisReader(ColumnDescriptor desc) {
      super(desc);
    }

    @Override
    public long readLong() {
      return 1000 * column.nextLong();
    }
  }

  /** Avro Record 读取器：将各字段读取器汇聚到 Avro {@link Record}。 */
  static class RecordReader extends StructReader<Record, Record> {
    private final Schema schema;

    RecordReader(List<Type> types, List<ParquetValueReader<?>> readers, Schema schema) {
      super(types, readers);
      this.schema = schema;
    }

    @Override
    protected Record newStructData(Record reuse) {
      if (reuse != null) {
        return reuse;
      } else {
        return new Record(schema);
      }
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Object getField(Record intermediate, int pos) {
      return intermediate.get(pos);
    }

    @Override
    protected Record buildStruct(Record struct) {
      return struct;
    }

    @Override
    protected void set(Record struct, int pos, Object value) {
      struct.put(pos, value);
    }
  }
}
