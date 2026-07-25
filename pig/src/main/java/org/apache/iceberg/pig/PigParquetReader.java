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
package org.apache.iceberg.pig;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.iceberg.Schema;
import org.apache.iceberg.parquet.ParquetSchemaUtil;
import org.apache.iceberg.parquet.ParquetValueReader;
import org.apache.iceberg.parquet.ParquetValueReaders;
import org.apache.iceberg.parquet.ParquetValueReaders.BinaryAsDecimalReader;
import org.apache.iceberg.parquet.ParquetValueReaders.FloatAsDoubleReader;
import org.apache.iceberg.parquet.ParquetValueReaders.IntAsLongReader;
import org.apache.iceberg.parquet.ParquetValueReaders.IntegerAsDecimalReader;
import org.apache.iceberg.parquet.ParquetValueReaders.LongAsDecimalReader;
import org.apache.iceberg.parquet.ParquetValueReaders.PrimitiveReader;
import org.apache.iceberg.parquet.ParquetValueReaders.RepeatedKeyValueReader;
import org.apache.iceberg.parquet.ParquetValueReaders.RepeatedReader;
import org.apache.iceberg.parquet.ParquetValueReaders.ReusableEntry;
import org.apache.iceberg.parquet.ParquetValueReaders.StringReader;
import org.apache.iceberg.parquet.ParquetValueReaders.StructReader;
import org.apache.iceberg.parquet.ParquetValueReaders.UnboxedReader;
import org.apache.iceberg.parquet.TypeWithSchemaVisitor;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.types.Type.TypeID;
import org.apache.iceberg.types.Types;
import org.apache.parquet.column.ColumnDescriptor;
import org.apache.parquet.schema.GroupType;
import org.apache.parquet.schema.LogicalTypeAnnotation.DecimalLogicalTypeAnnotation;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.PrimitiveType;
import org.apache.parquet.schema.Type;
import org.apache.pig.backend.executionengine.ExecException;
import org.apache.pig.data.BagFactory;
import org.apache.pig.data.DataBag;
import org.apache.pig.data.DataByteArray;
import org.apache.pig.data.Tuple;
import org.apache.pig.data.TupleFactory;

/**
 * 文件级说明：把 Parquet 文件读取结果适配为 Pig 数据类型（Tuple/DataBag/Map 等）的读取器构造器。
 *
 * <p>所属模块：iceberg-pig（Pig 引擎集成模块；位于 iceberg-parquet 列读取抽象之上，被 {@link
 * IcebergPigInputFormat.IcebergRecordReader#advance} 调用以构造逐列 Parquet 读取器）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>入口 {@link #buildReader}：根据 Parquet 文件 schema 是否含 field id 选择不同的访问器 构造策略（{@link ReadBuilder}
 *       按 id 匹配，{@link FallbackReadBuilder} 按位置回退）。
 *   <li>{@link ReadBuilder}：实现 {@link TypeWithSchemaVisitor}，按 Iceberg schema 与 Parquet type
 *       的对照关系，为 struct/list/map/primitive 各类型构造对应 {@link ParquetValueReader}， 并把分区列值作为常量读取器注入。
 *   <li>各类 XxxReader：把 Parquet 原始值转为 Pig 类型（如 DateReader 输出 yyyy-MM-dd 字符串、 BytesReader 输出
 *       DataByteArray、MapReader/ArrayReader/TupleReader 输出 Pig 容器）。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>双策略：带 id 的文件走字段对齐（支持 schema 演化/列重排/列裁剪/分区列注入）， 不带 id 的文件走位置回退，保证对老文件的兼容。
 *   <li>分区列注入：分区值不在数据文件中，通过 partitionValues 以 fieldId -&gt; 值形式传入， 在 struct 阶段用 {@link
 *       ParquetValueReaders#constant} 替换实际读取，按 expectedSchema 顺序 重排字段，保证输出 Tuple 列序符合 Pig 期望。
 *   <li>类型转换集中：primitive 方法集中处理 Parquet original type / primitive type 到 Pig 友好 reader 的映射，包括
 *       decimal/timestamp/date/int-as-long/float-as-double 等。
 * </ul>
 *
 * <p>上下游关系：被 {@link IcebergPigInputFormat} 调用；下游依赖 {@link TypeWithSchemaVisitor}、 {@link
 * ParquetValueReaders} 及 Parquet schema/列描述。
 */
public class PigParquetReader {
  /** 工具类，禁止实例化。 */
  private PigParquetReader() {}

  /**
   * 构造顶层 Parquet -&gt; Pig Tuple 读取器。
   *
   * <p>逻辑：检查文件 schema 是否含 field id：
   *
   * <ul>
   *   <li>含 id：用 {@link ReadBuilder} 按 id 严格匹配（支持列重排/裁剪/分区注入）。
   *   <li>不含 id：用 {@link FallbackReadBuilder} 按位置回退匹配（兼容老文件）。
   * </ul>
   *
   * @param fileSchema Parquet 文件 MessageType
   * @param expectedSchema Iceberg 期望读取 Schema（投影后）
   * @param partitionValues 分区列值映射 fieldId -&gt; value
   * @return 顶层 Tuple 读取器
   */
  @SuppressWarnings("unchecked")
  public static ParquetValueReader<Tuple> buildReader(
      MessageType fileSchema, Schema expectedSchema, Map<Integer, Object> partitionValues) {

    if (ParquetSchemaUtil.hasIds(fileSchema)) {
      return (ParquetValueReader<Tuple>)
          TypeWithSchemaVisitor.visit(
              expectedSchema.asStruct(), fileSchema, new ReadBuilder(fileSchema, partitionValues));
    } else {
      return (ParquetValueReader<Tuple>)
          TypeWithSchemaVisitor.visit(
              expectedSchema.asStruct(),
              fileSchema,
              new FallbackReadBuilder(fileSchema, partitionValues));
    }
  }

  /**
   * 回退版本的 ReadBuilder：用于 Parquet 文件 schema 不含 field id 的场景。
   *
   * <p>设计意图：顶层按 id 命中后，下层嵌套字段仍可能找不到 id，故 struct 直接按文件字段顺序 构造 TupleReader，不再依赖 expected
   * struct；每个字段按其 maxDefinitionLevel 包装为可选读取器。
   */
  private static class FallbackReadBuilder extends ReadBuilder {
    FallbackReadBuilder(MessageType type, Map<Integer, Object> partitionValues) {
      super(type, partitionValues);
    }

    /** 顶层 message：忽略 expected struct，直接委托 {@link #struct} 按文件字段顺序构造。 */
    @Override
    public ParquetValueReader<?> message(
        Types.StructType expected, MessageType message, List<ParquetValueReader<?>> fieldReaders) {
      // the top level matches by ID, but the remaining IDs are missing
      return super.struct(expected, message, fieldReaders);
    }

    /**
     * struct 构造（回退版）：忽略 expected struct，按文件字段顺序逐字段包装为可选读取器。
     *
     * <p>逻辑：对每个文件字段，按其 maxDefinitionLevel - 1 包装为 option 读取器， 累积后构造 TupleReader。
     *
     * @param ignored 期望 struct（忽略）
     * @param struct Parquet group type
     * @param fieldReaders 子字段读取器列表
     * @return TupleReader
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
        int fieldD = getMessageType().getMaxDefinitionLevel(path(fieldType.getName())) - 1;
        newFields.add(ParquetValueReaders.option(fieldType, fieldD, fieldReaders.get(i)));
        types.add(fieldType);
      }

      return new TupleReader(types, newFields);
    }
  }

  /**
   * 按 field id 严格匹配的 Parquet 读取器构造器，实现 {@link TypeWithSchemaVisitor}。
   *
   * <p>职责：为 message/struct/list/map/primitive 各节点构造对应 {@link ParquetValueReader}，
   * 并处理分区列注入、列顺序对齐、可选字段包装等。
   */
  private static class ReadBuilder extends TypeWithSchemaVisitor<ParquetValueReader<?>> {
    private final MessageType type;
    private final Map<Integer, Object> partitionValues;

    /**
     * @param type Parquet 文件 MessageType
     * @param partitionValues 分区列值映射（fieldId -&gt; value）
     */
    ReadBuilder(MessageType type, Map<Integer, Object> partitionValues) {
      this.type = type;
      this.partitionValues = partitionValues;
    }

    /** 返回当前 Parquet MessageType。 */
    MessageType getMessageType() {
      return this.type;
    }

    /** 顶层 message 节点：委托 {@link #struct} 处理（把 MessageType 当 GroupType）。 */
    @Override
    public ParquetValueReader<?> message(
        Types.StructType expected, MessageType message, List<ParquetValueReader<?>> fieldReaders) {
      return struct(expected, message.asGroupType(), fieldReaders);
    }

    /**
     * struct 构造：按 expected schema 字段顺序重排 reader，并注入分区列常量。
     *
     * <p>逻辑：
     *
     * <ol>
     *   <li>遍历文件 struct 字段，按 id 建立 readersById/typesById/maxDefinitionLevelsById 映射。
     *   <li>遍历 expected schema 字段：若 id 命中 partitionValues，用 constant 读取器注入分区值； 若 id 在 readersById
     *       中，按重排顺序取用；否则用 nulls 读取器填充。
     *   <li>构造 TupleReader，字段顺序对齐 expected schema。
     * </ol>
     *
     * @param expected Iceberg 期望 struct
     * @param struct Parquet group type
     * @param fieldReaders 子字段读取器列表
     * @return TupleReader
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
        int id = fieldType.getId().intValue();
        readersById.put(id, ParquetValueReaders.option(fieldType, fieldD, fieldReaders.get(i)));
        typesById.put(id, fieldType);
        if (partitionValues.containsKey(id)) {
          maxDefinitionLevelsById.put(id, fieldD);
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
        if (partitionValues.containsKey(id)) {
          // the value may be null so containsKey is used to check for a partition value
          int fieldMaxDefinitionLevel =
              maxDefinitionLevelsById.getOrDefault(id, defaultMaxDefinitionLevel);
          reorderedFields.add(
              ParquetValueReaders.constant(partitionValues.get(id), fieldMaxDefinitionLevel));
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

      return new TupleReader(types, reorderedFields);
    }

    /**
     * list 节点构造：基于 Parquet 数组 group type 计算 repeated/element 的 D 与 R 等级， 包装 elementReader 为 {@link
     * ArrayReader}。
     *
     * @param expectedList 期望 Iceberg list 类型
     * @param array Parquet 数组 group type
     * @param elementReader 元素读取器
     * @return ArrayReader
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
     * map 节点构造：从 Parquet repeated key-value group 中计算 repeated/key/value 的 D 与 R 等级， 包装为 {@link
     * MapReader}。
     *
     * @param expectedMap 期望 Iceberg map 类型
     * @param map Parquet map group type
     * @param keyReader key 读取器
     * @param valueReader value 读取器
     * @return MapReader
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
     * primitive 节点构造：按 Parquet original type 与 primitive type 选择对应 Pig 友好的读取器。
     *
     * <p>逻辑：
     *
     * <ul>
     *   <li>有 original type 时：ENUM/JSON/UTF8 -&gt; StringReader；DATE -&gt; DateReader；
     *       INT_(8/16/32) 期望 long 时用 IntAsLongReader 否则 UnboxedReader； TIMESTAMP_MILLIS/MICROS
     *       -&gt; 对应 TimestampReader； DECIMAL 按底层 BINARY/INT32/INT64 选择对应 DecimalReader。
     *   <li>无 original type 时：BINARY/FIXED -&gt; BytesReader；INT32 期望 long 走 IntAsLongReader； FLOAT
     *       期望 double 走 FloatAsDoubleReader；其余基本类型用 UnboxedReader。
     * </ul>
     *
     * @param expected Iceberg 期望 primitive type
     * @param primitive Parquet primitive type
     * @return 对应 primitive 读取器
     */
    @Override
    public ParquetValueReader<?> primitive(
        org.apache.iceberg.types.Type.PrimitiveType expected, PrimitiveType primitive) {
      ColumnDescriptor desc = type.getColumnDescription(currentPath());

      if (primitive.getOriginalType() != null) {
        switch (primitive.getOriginalType()) {
          case ENUM:
          case JSON:
          case UTF8:
            return new StringReader(desc);
          case DATE:
            return new DateReader(desc);
          case INT_8:
          case INT_16:
          case INT_32:
            if (expected != null && expected.typeId() == Types.LongType.get().typeId()) {
              return new IntAsLongReader(desc);
            } else {
              return new UnboxedReader(desc);
            }
          case INT_64:
            return new UnboxedReader<>(desc);
          case TIMESTAMP_MILLIS:
            return new TimestampMillisReader(desc);
          case TIMESTAMP_MICROS:
            return new TimestampMicrosReader(desc);
          case DECIMAL:
            DecimalLogicalTypeAnnotation decimal =
                (DecimalLogicalTypeAnnotation) primitive.getLogicalTypeAnnotation();
            switch (primitive.getPrimitiveTypeName()) {
              case BINARY:
              case FIXED_LEN_BYTE_ARRAY:
                return new BinaryAsDecimalReader(desc, decimal.getScale());
              case INT32:
                return new IntegerAsDecimalReader(desc, decimal.getScale());
              case INT64:
                return new LongAsDecimalReader(desc, decimal.getScale());
              default:
                throw new UnsupportedOperationException(
                    "Unsupported base type for decimal: " + primitive.getPrimitiveTypeName());
            }
          default:
            throw new UnsupportedOperationException(
                "Unsupported type: " + primitive.getOriginalType());
        }
      }

      switch (primitive.getPrimitiveTypeName()) {
        case FIXED_LEN_BYTE_ARRAY:
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

  /** 把 Parquet DATE（自 epoch 起的天数）读取为 yyyy-MM-dd 字符串的 reader。 */
  private static class DateReader extends PrimitiveReader<String> {
    private static final OffsetDateTime EPOCH = Instant.ofEpochSecond(0).atOffset(ZoneOffset.UTC);

    DateReader(ColumnDescriptor desc) {
      super(desc);
    }

    /** 读取下一天数值，加到 epoch 上后格式化为 yyyy-MM-dd 字符串。 */
    @Override
    public String read(String reuse) {
      OffsetDateTime day = EPOCH.plusDays(column.nextInteger());
      return String.format(
          "%04d-%02d-%02d", day.getYear(), day.getMonth().getValue(), day.getDayOfMonth());
    }
  }

  /** 把 Parquet BINARY 读取为 Pig {@link DataByteArray} 的 reader。 */
  private static class BytesReader extends PrimitiveReader<DataByteArray> {
    BytesReader(ColumnDescriptor desc) {
      super(desc);
    }

    /** 读取下一段二进制并包装为 {@link DataByteArray}。 */
    @Override
    public DataByteArray read(DataByteArray reuse) {
      byte[] bytes = column.nextBinary().getBytes();
      return new DataByteArray(bytes);
    }
  }

  /** 把 Parquet TIMESTAMP_MICROS（自 epoch 起的微秒数）读取为 ISO 字符串的 reader。 */
  private static class TimestampMicrosReader extends UnboxedReader<String> {
    private static final OffsetDateTime EPOCH = Instant.ofEpochSecond(0).atOffset(ZoneOffset.UTC);

    TimestampMicrosReader(ColumnDescriptor desc) {
      super(desc);
    }

    /** 读取下一个 long（微秒），加到 epoch 上并 toString 为 ISO 时间。 */
    @Override
    public String read(String ignored) {
      return ChronoUnit.MICROS.addTo(EPOCH, column.nextLong()).toString();
    }
  }

  /** 把 Parquet TIMESTAMP_MILLIS（自 epoch 起的毫秒数）读取为 ISO 字符串的 reader。 */
  private static class TimestampMillisReader extends UnboxedReader<String> {
    private static final OffsetDateTime EPOCH = Instant.ofEpochSecond(0).atOffset(ZoneOffset.UTC);

    TimestampMillisReader(ColumnDescriptor desc) {
      super(desc);
    }

    /** 读取下一个 long（毫秒），加到 epoch 上并 toString 为 ISO 时间。 */
    @Override
    public String read(String ignored) {
      return ChronoUnit.MILLIS.addTo(EPOCH, column.nextLong()).toString();
    }
  }

  /**
   * 把 Parquet repeated key-value 读取为 Pig {@link Map} 的 reader，底层用 LinkedHashMap 保持顺序。
   *
   * @param <K> key 类型
   * @param <V> value 类型
   */
  private static class MapReader<K, V> extends RepeatedKeyValueReader<Map<K, V>, Map<K, V>, K, V> {
    private final ReusableEntry<K, V> nullEntry = new ReusableEntry<>();

    MapReader(
        int definitionLevel,
        int repetitionLevel,
        ParquetValueReader<K> keyReader,
        ParquetValueReader<V> valueReader) {
      super(definitionLevel, repetitionLevel, keyReader, valueReader);
    }

    /** 创建底层存储容器（保持插入顺序）。 */
    @Override
    protected Map<K, V> newMapData(Map<K, V> reuse) {
      return new LinkedHashMap<>();
    }

    /** 返回可复用的 entry 对象。 */
    @Override
    protected Map.Entry<K, V> getPair(Map<K, V> reuse) {
      return nullEntry;
    }

    /** 把一个 key-value 对写入 map。 */
    @Override
    protected void addPair(Map<K, V> map, K key, V value) {
      map.put(key, value);
    }

    /** 直接返回已填充的 map 作为结果。 */
    @Override
    protected Map<K, V> buildMap(Map<K, V> map) {
      return map;
    }
  }

  /**
   * 把 Parquet repeated 元素读取为 Pig {@link DataBag} 的 reader；每个元素包装为单字段 Tuple。
   *
   * @param <T> 元素类型
   */
  private static class ArrayReader<T> extends RepeatedReader<DataBag, DataBag, T> {
    private final BagFactory bagFactory = BagFactory.getInstance();
    private final TupleFactory tupleFactory = TupleFactory.getInstance();

    ArrayReader(int definitionLevel, int repetitionLevel, ParquetValueReader<T> reader) {
      super(definitionLevel, repetitionLevel, reader);
    }

    /** 创建新的默认 DataBag 作为底层容器。 */
    @Override
    protected DataBag newListData(DataBag reuse) {
      return bagFactory.newDefaultBag();
    }

    /** 当前实现不通过此方法取元素，返回 null。 */
    @Override
    protected T getElement(DataBag list) {
      return null;
    }

    /** 把元素包装为单字段 Tuple 后加入 DataBag（Pig bag 元素必须是 Tuple）。 */
    @Override
    protected void addElement(DataBag bag, T element) {
      bag.add(tupleFactory.newTuple(element));
    }

    /** 直接返回已填充的 DataBag 作为结果。 */
    @Override
    protected DataBag buildList(DataBag bag) {
      return bag;
    }
  }

  /**
   * 把 struct 各字段读取结果组装为 Pig {@link Tuple} 的 reader，按 expected schema 顺序填充。
   *
   * <p>设计意图：Pig 行的顶层就是 Tuple；分区列与数据列混合后通过 set 写入指定位置。
   */
  private static class TupleReader extends StructReader<Tuple, Tuple> {
    private static final TupleFactory TF = TupleFactory.getInstance();
    private final int numColumns;

    TupleReader(List<Type> types, List<ParquetValueReader<?>> readers) {
      super(types, readers);
      this.numColumns = readers.size();
    }

    /** 创建指定列数的空 Tuple 作为行容器。 */
    @Override
    protected Tuple newStructData(Tuple reuse) {
      return TF.newTuple(numColumns);
    }

    /** 当前实现不通过此方法读字段，返回 null。 */
    @Override
    protected Object getField(Tuple tuple, int pos) {
      return null;
    }

    /** 直接返回已填充的 Tuple。 */
    @Override
    protected Tuple buildStruct(Tuple tuple) {
      return tuple;
    }

    /** 把某字段值写入 Tuple 指定位置；ExecException 包装为 RuntimeException。 */
    @Override
    protected void set(Tuple tuple, int pos, Object value) {
      try {
        tuple.set(pos, value);
      } catch (ExecException e) {
        throw new RuntimeException(
            String.format("Error setting tuple value for pos: %d, value: %s", pos, value), e);
      }
    }
  }
}
