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
package org.apache.iceberg.data.parquet;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.iceberg.MetadataColumns;
import org.apache.iceberg.Schema;
import org.apache.iceberg.parquet.ParquetSchemaUtil;
import org.apache.iceberg.parquet.ParquetValueReader;
import org.apache.iceberg.parquet.ParquetValueReaders;
import org.apache.iceberg.parquet.TypeWithSchemaVisitor;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.types.Types;
import org.apache.parquet.column.ColumnDescriptor;
import org.apache.parquet.schema.GroupType;
import org.apache.parquet.schema.LogicalTypeAnnotation.DecimalLogicalTypeAnnotation;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.PrimitiveType;
import org.apache.parquet.schema.Type;

/**
 * 文件级说明：Parquet 读取器构建基类（抽象）。
 *
 * <p>所属模块：iceberg-parquet（基于 Parquet 列式格式的读写实现模块，向上为 iceberg-api/iceberg-core 及各引擎提供 Parquet
 * 文件访问能力；本类位于 org.apache.iceberg.data.parquet 子包，面向"内存数据模型"读取场景）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>根据期望的 Iceberg {@link Schema} 与文件实际的 Parquet {@link MessageType}， 通过 {@link
 *       TypeWithSchemaVisitor} 联合遍历，构造一棵 {@link ParquetValueReader} 读取器树。
 *   <li>提供对 Iceberg 类型与 Parquet 原始/逻辑类型的映射，并处理各种类型转换 （INT32→LONG、FLOAT→DOUBLE、INT96 时间戳、DECIMAL
 *       缩放、Date/Time/Timestamp 等）。
 *   <li>对缺少字段 ID 的旧文件提供"回退"匹配策略（按字段名顺序对齐）。
 *   <li>注入常量列（idToConstant）、行号列（ROW_POSITION）、删除标记列（IS_DELETED）等元数据列。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>模板方法：将"struct 读取器"的实例化下沉到子类（{@link #createStructReader}）， 使不同内存模型（GenericRecord、Spark
 *       InternalRow 等）可复用同一套类型映射逻辑。
 *   <li>双 Builder：{@link ReadBuilder} 处理"带字段 ID"的 Iceberg 文件，按 ID 重排字段并 严格匹配；{@link
 *       FallbackReadBuilder} 处理历史遗留的"无 ID" Parquet 文件， 退化到按位置/名称匹配，保证兼容性。
 *   <li>定义级别（definition level）处理：通过 option(...) 包装处理可空字段与嵌套结构中的 null 语义，正确还原 Parquet D/R 编码。
 * </ul>
 *
 * <p>上下游关系：依赖 ParquetSchemaUtil（schema 工具）、TypeWithSchemaVisitor（联合遍历）、
 * ParquetValueReaders（具体读取器实现）；被 {@link GenericParquetReaders} 等子类继承， 最终被 ParquetReader 等读取入口调用。
 *
 * @param <T> 读取后返回的记录类型
 */
public abstract class BaseParquetReaders<T> {
  protected BaseParquetReaders() {}

  /**
   * 构造 Parquet 读取器（不带常量列）。
   *
   * @param expectedSchema 期望读取的 Iceberg schema
   * @param fileSchema Parquet 文件实际 schema
   * @return 与期望 schema 对齐的 {@link ParquetValueReader}
   */
  protected ParquetValueReader<T> createReader(Schema expectedSchema, MessageType fileSchema) {
    return createReader(expectedSchema, fileSchema, ImmutableMap.of());
  }

  /**
   * 构造 Parquet 读取器（支持注入常量列）。
   *
   * <p>逻辑：通过 {@link ParquetSchemaUtil#hasIds} 判断文件 schema 是否带字段 ID。 若带 ID，使用 {@link ReadBuilder} 按
   * ID 精确匹配并重排字段顺序； 否则使用 {@link FallbackReadBuilder} 退化为按字段顺序位置匹配，兼容历史文件。 最终以 {@link
   * TypeWithSchemaVisitor#visit} 联合遍历构造读取器树。
   *
   * @param expectedSchema 期望读取的 Iceberg schema
   * @param fileSchema Parquet 文件实际 schema
   * @param idToConstant 字段 ID 到常量值的映射，用于注入投影常量列；可能包含 null 值
   * @return 与期望 schema 对齐的 {@link ParquetValueReader}
   */
  @SuppressWarnings("unchecked")
  protected ParquetValueReader<T> createReader(
      Schema expectedSchema, MessageType fileSchema, Map<Integer, ?> idToConstant) {
    if (ParquetSchemaUtil.hasIds(fileSchema)) {
      return (ParquetValueReader<T>)
          TypeWithSchemaVisitor.visit(
              expectedSchema.asStruct(), fileSchema, new ReadBuilder(fileSchema, idToConstant));
    } else {
      return (ParquetValueReader<T>)
          TypeWithSchemaVisitor.visit(
              expectedSchema.asStruct(),
              fileSchema,
              new FallbackReadBuilder(fileSchema, idToConstant));
    }
  }

  /**
   * 由子类实现：根据字段类型列表与字段读取器列表构造 struct 读取器。
   *
   * @param types 各字段对应的 Parquet 类型（常量列/缺失字段为 null）
   * @param fieldReaders 各字段对应的读取器
   * @param structType 期望的 Iceberg struct 类型
   * @return 子类特定的 struct 读取器
   */
  protected abstract ParquetValueReader<T> createStructReader(
      List<Type> types, List<ParquetValueReader<?>> fieldReaders, Types.StructType structType);

  /**
   * 回退构建器：用于处理"无字段 ID"的历史 Parquet 文件。
   *
   * <p>设计意图：当文件 schema 不带 Iceberg 字段 ID 时，无法按 ID 匹配字段，于是退化到 按字段在文件中的顺序位置与期望 schema 对齐。本类覆写
   * message/struct，使顶层也走 struct 的"按位置对齐"逻辑，从而兼容 Hive 等老版本写入的 Parquet 文件。
   */
  private class FallbackReadBuilder extends ReadBuilder {
    private FallbackReadBuilder(MessageType type, Map<Integer, ?> idToConstant) {
      super(type, idToConstant);
    }

    @Override
    public ParquetValueReader<?> message(
        Types.StructType expected, MessageType message, List<ParquetValueReader<?>> fieldReaders) {
      // 顶层按 ID 匹配不可用，回退为按 struct 顺序处理
      return super.struct(expected, message, fieldReaders);
    }

    /**
     * 按文件字段顺序构建 struct 读取器（无 ID 匹配）。
     *
     * <p>逻辑：忽略期望 struct 的字段顺序，直接按文件 struct 中字段的下标顺序收集非 null 读取器，并通过 {@link
     * ParquetValueReaders#option} 包装以处理可空语义。
     *
     * @param expected 期望 struct（仅用于类型信息，字段顺序被忽略）
     * @param struct 文件中的 Parquet struct 类型
     * @param fieldReaders 由 visit 顺序生成的字段读取器，可能含 null
     * @return struct 读取器
     */
    @Override
    public ParquetValueReader<?> struct(
        Types.StructType expected, GroupType struct, List<ParquetValueReader<?>> fieldReaders) {
      // 期望 struct 被忽略，因为无 ID 时嵌套字段无法按名称找到
      List<ParquetValueReader<?>> newFields =
          Lists.newArrayListWithExpectedSize(fieldReaders.size());
      List<Type> types = Lists.newArrayListWithExpectedSize(fieldReaders.size());
      List<Type> fields = struct.getFields();
      for (int i = 0; i < fields.size(); i += 1) {
        ParquetValueReader<?> fieldReader = fieldReaders.get(i);
        if (fieldReader != null) {
          Type fieldType = fields.get(i);
          int fieldD = type().getMaxDefinitionLevel(path(fieldType.getName())) - 1;
          newFields.add(ParquetValueReaders.option(fieldType, fieldD, fieldReader));
          types.add(fieldType);
        }
      }

      return createStructReader(types, newFields, expected);
    }
  }

  /**
   * 主构建器：用于处理"带字段 ID"的 Iceberg Parquet 文件。
   *
   * <p>设计意图：继承 {@link TypeWithSchemaVisitor}，在联合遍历 Iceberg schema 与 Parquet schema 时，按字段 ID
   * 精确匹配文件字段，并按期望 schema 的字段顺序重排读取器， 处理常量列、元数据列、缺失字段等场景。
   */
  private class ReadBuilder extends TypeWithSchemaVisitor<ParquetValueReader<?>> {
    private final MessageType type;
    private final Map<Integer, ?> idToConstant;

    private ReadBuilder(MessageType type, Map<Integer, ?> idToConstant) {
      this.type = type;
      this.idToConstant = idToConstant;
    }

    /** 顶层 message 节点：直接委托给 struct 处理（message 在 Parquet 中相当于根 struct）。 */
    @Override
    public ParquetValueReader<?> message(
        Types.StructType expected, MessageType message, List<ParquetValueReader<?>> fieldReaders) {
      return struct(expected, message.asGroupType(), fieldReaders);
    }

    /**
     * 按 Iceberg 期望 schema 字段顺序构建 struct 读取器。
     *
     * <p>逻辑：
     *
     * <ol>
     *   <li>第一遍遍历文件 struct 字段，按字段 ID 收集 readersById/typesById， 并计算每个字段的最大定义级别（用于常量列定位 null 行）。
     *   <li>第二遍按期望 schema 字段顺序逐个处理：若该 ID 在常量映射中，则注入常量读取器； 若是行号列/删除标记列，注入对应元数据读取器；否则从 readersById
     *       取出对应读取器， 取不到则填充 nulls() 读取器（字段缺失全为 null）。
     * </ol>
     *
     * @param expected 期望的 Iceberg struct 类型
     * @param struct 文件中的 Parquet struct 类型
     * @param fieldReaders 与文件 struct 字段顺序对齐的子读取器列表
     * @return 按期望 schema 顺序排列的 struct 读取器
     */
    @Override
    public ParquetValueReader<?> struct(
        Types.StructType expected, GroupType struct, List<ParquetValueReader<?>> fieldReaders) {
      // 按期望 struct 的字段顺序重排
      Map<Integer, ParquetValueReader<?>> readersById = Maps.newHashMap();
      Map<Integer, Type> typesById = Maps.newHashMap();
      Map<Integer, Integer> maxDefinitionLevelsById = Maps.newHashMap();
      List<Type> fields = struct.getFields();
      for (int i = 0; i < fields.size(); i += 1) {
        ParquetValueReader<?> fieldReader = fieldReaders.get(i);
        if (fieldReader != null) {
          Type fieldType = fields.get(i);
          int fieldD = type.getMaxDefinitionLevel(path(fieldType.getName())) - 1;
          int id = fieldType.getId().intValue();
          readersById.put(id, ParquetValueReaders.option(fieldType, fieldD, fieldReader));
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

      return createStructReader(types, reorderedFields, expected);
    }

    /**
     * 构建 list 读取器。
     *
     * <p>逻辑：计算重复层级（repeated D/R）和元素的定义级别，通过 {@link ParquetValueReaders.ListReader} 处理 Parquet LIST
     * 编码， 并用 option(...) 包装元素读取器以处理可空元素。
     *
     * @param expectedList 期望的 Iceberg list 类型
     * @param array Parquet LIST group 类型
     * @param elementReader 元素读取器
     * @return list 读取器，若期望类型为 null 则返回 null
     */
    @Override
    public ParquetValueReader<?> list(
        Types.ListType expectedList, GroupType array, ParquetValueReader<?> elementReader) {
      if (expectedList == null) {
        return null;
      }

      String[] repeatedPath = currentPath();

      int repeatedD = type.getMaxDefinitionLevel(repeatedPath) - 1;
      int repeatedR = type.getMaxRepetitionLevel(repeatedPath) - 1;

      Type elementType = ParquetSchemaUtil.determineListElementType(array);
      int elementD = type.getMaxDefinitionLevel(path(elementType.getName())) - 1;

      return new ParquetValueReaders.ListReader<>(
          repeatedD, repeatedR, ParquetValueReaders.option(elementType, elementD, elementReader));
    }

    /**
     * 构建 map 读取器。
     *
     * <p>逻辑：从 Parquet MAP group 中取出 key-value 重复组，分别计算 key/value 的定义级别， 通过 {@link
     * ParquetValueReaders.MapReader} 处理 Parquet MAP 编码。
     *
     * @param expectedMap 期望的 Iceberg map 类型
     * @param map Parquet MAP group 类型
     * @param keyReader key 读取器
     * @param valueReader value 读取器
     * @return map 读取器，若期望类型为 null 则返回 null
     */
    @Override
    public ParquetValueReader<?> map(
        Types.MapType expectedMap,
        GroupType map,
        ParquetValueReader<?> keyReader,
        ParquetValueReader<?> valueReader) {
      if (expectedMap == null) {
        return null;
      }

      GroupType repeatedKeyValue = map.getFields().get(0).asGroupType();
      String[] repeatedPath = currentPath();

      int repeatedD = type.getMaxDefinitionLevel(repeatedPath) - 1;
      int repeatedR = type.getMaxRepetitionLevel(repeatedPath) - 1;

      Type keyType = repeatedKeyValue.getType(0);
      int keyD = type.getMaxDefinitionLevel(path(keyType.getName())) - 1;
      Type valueType = repeatedKeyValue.getType(1);
      int valueD = type.getMaxDefinitionLevel(path(valueType.getName())) - 1;

      return new ParquetValueReaders.MapReader<>(
          repeatedD,
          repeatedR,
          ParquetValueReaders.option(keyType, keyD, keyReader),
          ParquetValueReaders.option(valueType, valueD, valueReader));
    }

    /**
     * 构建原始类型读取器：根据 Parquet 逻辑类型与原始类型分派到具体 Reader。
     *
     * <p>逻辑：
     *
     * <ul>
     *   <li>若 Parquet 类型带逻辑类型标注（UTF8/INT_32/DATE/DECIMAL 等），按逻辑类型选择 对应 Reader，并处理 Iceberg 与 Parquet
     *       的类型差异（如 INT32→LONG、TIMESTAMP 时区）。
     *   <li>否则按原始类型名（BINARY/INT32/FLOAT/INT96 等）选择 Reader，处理无逻辑标注的旧文件。
     *   <li>DECIMAL 按底层存储类型（BINARY/INT64/INT32）选择不同的 Decimal Reader 并传入 scale。
     *   <li>INT96 为 Impala/Spark 旧版时间戳编码，特殊处理为时间戳读取。
     * </ul>
     *
     * @param expected 期望的 Iceberg 原始类型
     * @param primitive Parquet 原始类型
     * @return 对应的原始类型读取器，若期望类型为 null 则返回 null
     * @throws UnsupportedOperationException 若类型不受支持
     */
    @Override
    @SuppressWarnings("checkstyle:CyclomaticComplexity")
    public ParquetValueReader<?> primitive(
        org.apache.iceberg.types.Type.PrimitiveType expected, PrimitiveType primitive) {
      if (expected == null) {
        return null;
      }

      ColumnDescriptor desc = type.getColumnDescription(currentPath());

      if (primitive.getOriginalType() != null) {
        switch (primitive.getOriginalType()) {
          case ENUM:
          case JSON:
          case UTF8:
            return new ParquetValueReaders.StringReader(desc);
          case INT_8:
          case INT_16:
          case INT_32:
            if (expected.typeId() == org.apache.iceberg.types.Type.TypeID.LONG) {
              return new ParquetValueReaders.IntAsLongReader(desc);
            } else {
              return new ParquetValueReaders.UnboxedReader<>(desc);
            }
          case INT_64:
            return new ParquetValueReaders.UnboxedReader<>(desc);
          case DATE:
            return new DateReader(desc);
          case TIMESTAMP_MICROS:
            Types.TimestampType tsMicrosType = (Types.TimestampType) expected;
            if (tsMicrosType.shouldAdjustToUTC()) {
              return new TimestamptzReader(desc);
            } else {
              return new TimestampReader(desc);
            }
          case TIMESTAMP_MILLIS:
            Types.TimestampType tsMillisType = (Types.TimestampType) expected;
            if (tsMillisType.shouldAdjustToUTC()) {
              return new TimestamptzMillisReader(desc);
            } else {
              return new TimestampMillisReader(desc);
            }
          case TIME_MICROS:
            return new TimeReader(desc);
          case TIME_MILLIS:
            return new TimeMillisReader(desc);
          case DECIMAL:
            DecimalLogicalTypeAnnotation decimal =
                (DecimalLogicalTypeAnnotation) primitive.getLogicalTypeAnnotation();
            switch (primitive.getPrimitiveTypeName()) {
              case BINARY:
              case FIXED_LEN_BYTE_ARRAY:
                return new ParquetValueReaders.BinaryAsDecimalReader(desc, decimal.getScale());
              case INT64:
                return new ParquetValueReaders.LongAsDecimalReader(desc, decimal.getScale());
              case INT32:
                return new ParquetValueReaders.IntegerAsDecimalReader(desc, decimal.getScale());
              default:
                throw new UnsupportedOperationException(
                    "Unsupported base type for decimal: " + primitive.getPrimitiveTypeName());
            }
          case BSON:
            return new ParquetValueReaders.BytesReader(desc);
          default:
            throw new UnsupportedOperationException(
                "Unsupported logical type: " + primitive.getOriginalType());
        }
      }

      switch (primitive.getPrimitiveTypeName()) {
        case FIXED_LEN_BYTE_ARRAY:
          return new FixedReader(desc);
        case BINARY:
          if (expected != null
              && expected.typeId() == org.apache.iceberg.types.Type.TypeID.STRING) {
            return new ParquetValueReaders.StringReader(desc);
          } else {
            return new ParquetValueReaders.BytesReader(desc);
          }
        case INT32:
          if (expected != null && expected.typeId() == org.apache.iceberg.types.Type.TypeID.LONG) {
            return new ParquetValueReaders.IntAsLongReader(desc);
          } else {
            return new ParquetValueReaders.UnboxedReader<>(desc);
          }
        case FLOAT:
          if (expected != null
              && expected.typeId() == org.apache.iceberg.types.Type.TypeID.DOUBLE) {
            return new ParquetValueReaders.FloatAsDoubleReader(desc);
          } else {
            return new ParquetValueReaders.UnboxedReader<>(desc);
          }
        case BOOLEAN:
        case INT64:
        case DOUBLE:
          return new ParquetValueReaders.UnboxedReader<>(desc);
        case INT96:
          // Impala 和 Spark 旧版将时间戳写为 INT96（无逻辑类型）。为向后兼容，
          // 这里将 INT96 作为时间戳读取。
          return new TimestampInt96Reader(desc);
        default:
          throw new UnsupportedOperationException("Unsupported type: " + primitive);
      }
    }

    MessageType type() {
      return type;
    }
  }

  private static final OffsetDateTime EPOCH = Instant.ofEpochSecond(0).atOffset(ZoneOffset.UTC);
  private static final LocalDate EPOCH_DAY = EPOCH.toLocalDate();

  /** Date 读取器：将 Parquet 自纪元以来的天数转为 {@link LocalDate}。 */
  private static class DateReader extends ParquetValueReaders.PrimitiveReader<LocalDate> {
    private DateReader(ColumnDescriptor desc) {
      super(desc);
    }

    @Override
    public LocalDate read(LocalDate reuse) {
      return EPOCH_DAY.plusDays(column.nextInteger());
    }
  }

  /** Timestamp 读取器（微秒，不带时区）：将微秒值转为 {@link LocalDateTime}。 */
  private static class TimestampReader extends ParquetValueReaders.PrimitiveReader<LocalDateTime> {
    private TimestampReader(ColumnDescriptor desc) {
      super(desc);
    }

    @Override
    public LocalDateTime read(LocalDateTime reuse) {
      return EPOCH.plus(column.nextLong(), ChronoUnit.MICROS).toLocalDateTime();
    }
  }

  /** Timestamp 读取器（毫秒，不带时区）：将毫秒值转为微秒后构造 {@link LocalDateTime}。 */
  private static class TimestampMillisReader
      extends ParquetValueReaders.PrimitiveReader<LocalDateTime> {
    private TimestampMillisReader(ColumnDescriptor desc) {
      super(desc);
    }

    @Override
    public LocalDateTime read(LocalDateTime reuse) {
      return EPOCH.plus(column.nextLong() * 1000, ChronoUnit.MICROS).toLocalDateTime();
    }
  }

  /**
   * INT96 时间戳读取器：解析 Impala/Spark 旧版 INT96 编码（Julian Day + nanos）。
   *
   * <p>设计要点：INT96 由 8 字节纳秒（一天内的时间）+ 4 字节 Julian Day 组成， 小端序存储。通过 Julian Day 与 Unix Epoch 的偏移量转换为
   * {@link OffsetDateTime}。
   */
  private static class TimestampInt96Reader
      extends ParquetValueReaders.PrimitiveReader<OffsetDateTime> {
    private static final long UNIX_EPOCH_JULIAN = 2_440_588L;

    private TimestampInt96Reader(ColumnDescriptor desc) {
      super(desc);
    }

    @Override
    public OffsetDateTime read(OffsetDateTime reuse) {
      final ByteBuffer byteBuffer =
          column.nextBinary().toByteBuffer().order(ByteOrder.LITTLE_ENDIAN);
      final long timeOfDayNanos = byteBuffer.getLong();
      final int julianDay = byteBuffer.getInt();

      return Instant.ofEpochMilli(TimeUnit.DAYS.toMillis(julianDay - UNIX_EPOCH_JULIAN))
          .plusNanos(timeOfDayNanos)
          .atOffset(ZoneOffset.UTC);
    }
  }

  /** Timestamp 读取器（微秒，带时区 UTC）：将微秒值转为 {@link OffsetDateTime}。 */
  private static class TimestamptzReader
      extends ParquetValueReaders.PrimitiveReader<OffsetDateTime> {
    private TimestamptzReader(ColumnDescriptor desc) {
      super(desc);
    }

    @Override
    public OffsetDateTime read(OffsetDateTime reuse) {
      return EPOCH.plus(column.nextLong(), ChronoUnit.MICROS);
    }
  }

  /** Timestamp 读取器（毫秒，带时区 UTC）：将毫秒值转为微秒后构造 {@link OffsetDateTime}。 */
  private static class TimestamptzMillisReader
      extends ParquetValueReaders.PrimitiveReader<OffsetDateTime> {
    private TimestamptzMillisReader(ColumnDescriptor desc) {
      super(desc);
    }

    @Override
    public OffsetDateTime read(OffsetDateTime reuse) {
      return EPOCH.plus(column.nextLong() * 1000, ChronoUnit.MICROS);
    }
  }

  /** Time 读取器（毫秒）：将毫秒值转为 {@link LocalTime}。 */
  private static class TimeMillisReader extends ParquetValueReaders.PrimitiveReader<LocalTime> {
    private TimeMillisReader(ColumnDescriptor desc) {
      super(desc);
    }

    @Override
    public LocalTime read(LocalTime reuse) {
      return LocalTime.ofNanoOfDay(column.nextLong() * 1000000L);
    }
  }

  /** Time 读取器（微秒）：将微秒值转为 {@link LocalTime}。 */
  private static class TimeReader extends ParquetValueReaders.PrimitiveReader<LocalTime> {
    private TimeReader(ColumnDescriptor desc) {
      super(desc);
    }

    @Override
    public LocalTime read(LocalTime reuse) {
      return LocalTime.ofNanoOfDay(column.nextLong() * 1000L);
    }
  }

  /**
   * FixedLenByteArray 读取器：读取定长字节数组。
   *
   * <p>设计要点：若调用方传入可复用 byte[]，则通过 duplicate() 写入避免分配； 否则直接 getBytes() 返回新数组。
   */
  private static class FixedReader extends ParquetValueReaders.PrimitiveReader<byte[]> {
    private FixedReader(ColumnDescriptor desc) {
      super(desc);
    }

    @Override
    public byte[] read(byte[] reuse) {
      if (reuse != null) {
        column.nextBinary().toByteBuffer().duplicate().get(reuse);
        return reuse;
      } else {
        return column.nextBinary().getBytes();
      }
    }
  }
}
