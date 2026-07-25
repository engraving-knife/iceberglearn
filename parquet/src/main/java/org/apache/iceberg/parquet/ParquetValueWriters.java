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

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.avro.util.Utf8;
import org.apache.iceberg.DoubleFieldMetrics;
import org.apache.iceberg.FieldMetrics;
import org.apache.iceberg.FloatFieldMetrics;
import org.apache.iceberg.deletes.PositionDelete;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.types.TypeUtil;
import org.apache.iceberg.util.DecimalUtil;
import org.apache.parquet.column.ColumnDescriptor;
import org.apache.parquet.column.ColumnWriteStore;
import org.apache.parquet.io.api.Binary;
import org.apache.parquet.schema.Type;

/**
 * 文件级说明：Parquet 值写入器集合，提供各类型的基础写入器实现。
 *
 * <p>所属模块：iceberg-parquet（写入器基础设施，位于 org.apache.iceberg.parquet 包）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>提供原始类型写入器（UnboxedWriter/BytesWriter/StringWriter 等），封装 {@link ColumnWriter}。
 *   <li>提供组合写入器（OptionWriter/StructWriter/CollectionWriter/MapWriter）处理嵌套结构与 null 语义。
 *   <li>提供 Decimal 写入器（IntegerDecimalWriter/LongDecimalWriter/FixedDecimalWriter）。
 *   <li>提供 metrics 收集（FloatWriter/DoubleWriter 收集 FieldMetrics）。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>组合模式：StructWriter/CollectionWriter/MapWriter 通过组合子写入器实现嵌套结构的递归写入。
 *   <li>Definition/Repetition Level 驱动：OptionWriter/RepeatedWriter 通过 D/R level 正确编码嵌套 null 和重复结构。
 *   <li>工厂方法：booleans/ints/longs/floats/doubles/strings 等提供类型安全的写入器创建。
 * </ul>
 *
 * <p>上下游关系：被 ParquetValueWriter 构建器（如 BaseParquetWriters）使用； 依赖
 * ColumnWriter（底层列写入器）、ColumnWriteStore。
 */
public class ParquetValueWriters {
  private ParquetValueWriters() {}

  /**
   * 按 definition level 包装写入器：OPTIONAL 列用 OptionWriter 包装以处理 null 写入。
   *
   * @param definitionLevel 字段的最大定义级别
   * @param writer 原始写入器
   * @return 包装后的写入器
   */
  public static <T> ParquetValueWriter<T> option(
      Type type, int definitionLevel, ParquetValueWriter<T> writer) {
    if (type.isRepetition(Type.Repetition.OPTIONAL)) {
      return new OptionWriter<>(definitionLevel, writer);
    }

    return writer;
  }

  /** 创建 boolean 列写入器。 */
  public static UnboxedWriter<Boolean> booleans(ColumnDescriptor desc) {
    return new UnboxedWriter<>(desc);
  }

  public static UnboxedWriter<Byte> tinyints(ColumnDescriptor desc) {
    return new ByteWriter(desc);
  }

  public static UnboxedWriter<Short> shorts(ColumnDescriptor desc) {
    return new ShortWriter(desc);
  }

  /** 创建 int 列写入器。 */
  public static UnboxedWriter<Integer> ints(ColumnDescriptor desc) {
    return new UnboxedWriter<>(desc);
  }

  /** 创建 long 列写入器。 */
  public static UnboxedWriter<Long> longs(ColumnDescriptor desc) {
    return new UnboxedWriter<>(desc);
  }

  /** 创建 float 列写入器（含 FieldMetrics 收集）。 */
  public static UnboxedWriter<Float> floats(ColumnDescriptor desc) {
    return new FloatWriter(desc);
  }

  /** 创建 double 列写入器（含 FieldMetrics 收集）。 */
  public static UnboxedWriter<Double> doubles(ColumnDescriptor desc) {
    return new DoubleWriter(desc);
  }

  /** 创建 string 列写入器（CharSequence → Binary）。 */
  public static PrimitiveWriter<CharSequence> strings(ColumnDescriptor desc) {
    return new StringWriter(desc);
  }

  /** 创建 INT32 存储 decimal 的写入器。 */
  public static PrimitiveWriter<BigDecimal> decimalAsInteger(
      ColumnDescriptor desc, int precision, int scale) {
    return new IntegerDecimalWriter(desc, precision, scale);
  }

  /** 创建 INT64 存储 decimal 的写入器。 */
  public static PrimitiveWriter<BigDecimal> decimalAsLong(
      ColumnDescriptor desc, int precision, int scale) {
    return new LongDecimalWriter(desc, precision, scale);
  }

  /** 创建 FIXED_LEN_BYTE_ARRAY 存储 decimal 的写入器。 */
  public static PrimitiveWriter<BigDecimal> decimalAsFixed(
      ColumnDescriptor desc, int precision, int scale) {
    return new FixedDecimalWriter(desc, precision, scale);
  }

  /** 创建 ByteBuffer 列写入器。 */
  public static PrimitiveWriter<ByteBuffer> byteBuffers(ColumnDescriptor desc) {
    return new BytesWriter(desc);
  }

  /** 创建集合（List）写入器。 */
  public static <E> CollectionWriter<E> collections(int dl, int rl, ParquetValueWriter<E> writer) {
    return new CollectionWriter<>(dl, rl, writer);
  }

  /** 创建 Map 写入器。 */
  public static <K, V> MapWriter<K, V> maps(
      int dl, int rl, ParquetValueWriter<K> keyWriter, ParquetValueWriter<V> valueWriter) {
    return new MapWriter<>(dl, rl, keyWriter, valueWriter);
  }

  /** 原始类型写入器基类（抽象）：封装 {@link ColumnWriter}，提供列写入器和列存储的设置。 子类通过 column.writeXxx() 写入具体类型的值。 */
  public abstract static class PrimitiveWriter<T> implements ParquetValueWriter<T> {
    @SuppressWarnings("checkstyle:VisibilityModifier")
    protected final ColumnWriter<T> column;

    private final List<TripleWriter<?>> children;

    protected PrimitiveWriter(ColumnDescriptor desc) {
      this.column = ColumnWriter.newWriter(desc);
      this.children = ImmutableList.of(column);
    }

    @Override
    public void write(int repetitionLevel, T value) {
      column.write(repetitionLevel, value);
    }

    @Override
    public List<TripleWriter<?>> columns() {
      return children;
    }

    @Override
    public void setColumnStore(ColumnWriteStore columnStore) {
      this.column.setColumnStore(columnStore);
    }
  }

  /** 拆箱写入器：提供 writeBoolean/Integer/Long/Float/Double 等原始类型写入方法。 write() 默认抛异常，由具体子类覆写。 */
  private static class UnboxedWriter<T> extends PrimitiveWriter<T> {
    private UnboxedWriter(ColumnDescriptor desc) {
      super(desc);
    }

    public void writeBoolean(int repetitionLevel, boolean value) {
      column.writeBoolean(repetitionLevel, value);
    }

    public void writeInteger(int repetitionLevel, int value) {
      column.writeInteger(repetitionLevel, value);
    }

    public void writeLong(int repetitionLevel, long value) {
      column.writeLong(repetitionLevel, value);
    }

    public void writeFloat(int repetitionLevel, float value) {
      column.writeFloat(repetitionLevel, value);
    }

    public void writeDouble(int repetitionLevel, double value) {
      column.writeDouble(repetitionLevel, value);
    }
  }

  /** Float 写入器：写入 float 值并收集 FieldMetrics（用于 NaN/上下界统计）。 */
  private static class FloatWriter extends UnboxedWriter<Float> {
    private final FloatFieldMetrics.Builder floatFieldMetricsBuilder;

    private FloatWriter(ColumnDescriptor desc) {
      super(desc);
      int id = desc.getPrimitiveType().getId().intValue();
      this.floatFieldMetricsBuilder = new FloatFieldMetrics.Builder(id);
    }

    @Override
    public void write(int repetitionLevel, Float value) {
      writeFloat(repetitionLevel, value);
      floatFieldMetricsBuilder.addValue(value);
    }

    @Override
    public Stream<FieldMetrics<?>> metrics() {
      return Stream.of(floatFieldMetricsBuilder.build());
    }
  }

  /** Double 写入器：写入 double 值并收集 FieldMetrics（用于 NaN/上下界统计）。 */
  private static class DoubleWriter extends UnboxedWriter<Double> {
    private final DoubleFieldMetrics.Builder doubleFieldMetricsBuilder;

    private DoubleWriter(ColumnDescriptor desc) {
      super(desc);
      int id = desc.getPrimitiveType().getId().intValue();
      this.doubleFieldMetricsBuilder = new DoubleFieldMetrics.Builder(id);
    }

    @Override
    public void write(int repetitionLevel, Double value) {
      writeDouble(repetitionLevel, value);
      doubleFieldMetricsBuilder.addValue(value);
    }

    @Override
    public Stream<FieldMetrics<?>> metrics() {
      return Stream.of(doubleFieldMetricsBuilder.build());
    }
  }

  /** Byte 写入器：将 byte 值作为 int 写入（Iceberg INT_8 存储）。 */
  private static class ByteWriter extends UnboxedWriter<Byte> {
    private ByteWriter(ColumnDescriptor desc) {
      super(desc);
    }

    @Override
    public void write(int repetitionLevel, Byte value) {
      writeInteger(repetitionLevel, value.intValue());
    }
  }

  /** Short 写入器：将 short 值作为 int 写入（Iceberg INT_16 存储）。 */
  private static class ShortWriter extends UnboxedWriter<Short> {
    private ShortWriter(ColumnDescriptor desc) {
      super(desc);
    }

    @Override
    public void write(int repetitionLevel, Short value) {
      writeInteger(repetitionLevel, value.intValue());
    }
  }

  /** INT32 Decimal 写入器：将 BigDecimal 的 unscaled value 作为 int 写入。 */
  private static class IntegerDecimalWriter extends PrimitiveWriter<BigDecimal> {
    private final int precision;
    private final int scale;

    private IntegerDecimalWriter(ColumnDescriptor desc, int precision, int scale) {
      super(desc);
      this.precision = precision;
      this.scale = scale;
    }

    @Override
    public void write(int repetitionLevel, BigDecimal decimal) {
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

      column.writeInteger(repetitionLevel, decimal.unscaledValue().intValue());
    }
  }

  /** INT64 Decimal 写入器：将 BigDecimal 的 unscaled value 作为 long 写入。 */
  private static class LongDecimalWriter extends PrimitiveWriter<BigDecimal> {
    private final int precision;
    private final int scale;

    private LongDecimalWriter(ColumnDescriptor desc, int precision, int scale) {
      super(desc);
      this.precision = precision;
      this.scale = scale;
    }

    @Override
    public void write(int repetitionLevel, BigDecimal decimal) {
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

      column.writeLong(repetitionLevel, decimal.unscaledValue().longValue());
    }
  }

  /** FIXED Decimal 写入器：将 BigDecimal 转为定长字节数组写入。 */
  private static class FixedDecimalWriter extends PrimitiveWriter<BigDecimal> {
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
    public void write(int repetitionLevel, BigDecimal decimal) {
      byte[] binary = DecimalUtil.toReusedFixLengthBytes(precision, scale, decimal, bytes.get());
      column.writeBinary(repetitionLevel, Binary.fromReusedByteArray(binary));
    }
  }

  /** Bytes 写入器：将 ByteBuffer 转为 Parquet Binary 写入。 */
  private static class BytesWriter extends PrimitiveWriter<ByteBuffer> {
    private BytesWriter(ColumnDescriptor desc) {
      super(desc);
    }

    @Override
    public void write(int repetitionLevel, ByteBuffer buffer) {
      column.writeBinary(repetitionLevel, Binary.fromReusedByteBuffer(buffer));
    }
  }

  /** String 写入器：将 CharSequence 转为 Parquet Binary（UTF-8）写入。 */
  private static class StringWriter extends PrimitiveWriter<CharSequence> {
    private StringWriter(ColumnDescriptor desc) {
      super(desc);
    }

    @Override
    public void write(int repetitionLevel, CharSequence value) {
      if (value instanceof Utf8) {
        Utf8 utf8 = (Utf8) value;
        column.writeBinary(
            repetitionLevel, Binary.fromReusedByteArray(utf8.getBytes(), 0, utf8.getByteLength()));
      } else {
        column.writeBinary(repetitionLevel, Binary.fromString(value.toString()));
      }
    }
  }

  /**
   * 可选字段写入器：通过 definition level 处理 null 写入。
   *
   * <p>逻辑：write 时若值为 null 则调用 writeNull（D < maxD），否则委托子写入器写入非 null 值。
   */
  static class OptionWriter<T> implements ParquetValueWriter<T> {
    private final int definitionLevel;
    private final ParquetValueWriter<T> writer;
    private final List<TripleWriter<?>> children;
    private long nullValueCount = 0;

    OptionWriter(int definitionLevel, ParquetValueWriter<T> writer) {
      this.definitionLevel = definitionLevel;
      this.writer = writer;
      this.children = writer.columns();
    }

    @Override
    public void write(int repetitionLevel, T value) {
      if (value != null) {
        writer.write(repetitionLevel, value);

      } else {
        nullValueCount++;
        for (TripleWriter<?> column : children) {
          column.writeNull(repetitionLevel, definitionLevel - 1);
        }
      }
    }

    @Override
    public List<TripleWriter<?>> columns() {
      return children;
    }

    @Override
    public void setColumnStore(ColumnWriteStore columnStore) {
      writer.setColumnStore(columnStore);
    }

    @Override
    public Stream<FieldMetrics<?>> metrics() {
      if (writer instanceof PrimitiveWriter) {
        List<FieldMetrics<?>> fieldMetricsFromWriter =
            writer.metrics().collect(Collectors.toList());

        if (fieldMetricsFromWriter.size() == 0) {
          // we are not tracking field metrics for this type ourselves
          return Stream.empty();
        } else if (fieldMetricsFromWriter.size() == 1) {
          FieldMetrics<?> metrics = fieldMetricsFromWriter.get(0);
          return Stream.of(
              new FieldMetrics<>(
                  metrics.id(),
                  metrics.valueCount() + nullValueCount,
                  nullValueCount,
                  metrics.nanValueCount(),
                  metrics.lowerBound(),
                  metrics.upperBound()));
        } else {
          throw new IllegalStateException(
              String.format(
                  "OptionWriter should only expect at most one field metric from a primitive writer."
                      + "Current number of fields: %s, primitive writer type: %s",
                  fieldMetricsFromWriter.size(), writer.getClass().getSimpleName()));
        }
      }

      // skipping updating null stats for non-primitive types since we don't use them today, to
      // avoid unnecessary work
      return writer.metrics();
    }
  }

  /**
   * 重复字段写入器基类（抽象）：处理 Parquet LIST 编码中的重复元素写入。
   *
   * <p>逻辑：write 时遍历集合元素，为每个元素写入一个三元组（含正确的 D/R level）， 空集合写入一个 null 标记三元组。子类实现 elements() 提供元素迭代器。
   */
  public abstract static class RepeatedWriter<L, E> implements ParquetValueWriter<L> {
    private final int definitionLevel;
    private final int repetitionLevel;
    private final ParquetValueWriter<E> writer;
    private final List<TripleWriter<?>> children;

    protected RepeatedWriter(
        int definitionLevel, int repetitionLevel, ParquetValueWriter<E> writer) {
      this.definitionLevel = definitionLevel;
      this.repetitionLevel = repetitionLevel;
      this.writer = writer;
      this.children = writer.columns();
    }

    @Override
    public void write(int parentRepetition, L value) {
      Iterator<E> elements = elements(value);

      if (!elements.hasNext()) {
        // write the empty list to each column
        // TODO: make sure this definition level is correct
        for (TripleWriter<?> column : children) {
          column.writeNull(parentRepetition, definitionLevel - 1);
        }

      } else {
        boolean first = true;
        while (elements.hasNext()) {
          E element = elements.next();

          int rl = repetitionLevel;
          if (first) {
            rl = parentRepetition;
            first = false;
          }

          writer.write(rl, element);
        }
      }
    }

    @Override
    public List<TripleWriter<?>> columns() {
      return children;
    }

    @Override
    public void setColumnStore(ColumnWriteStore columnStore) {
      writer.setColumnStore(columnStore);
    }

    protected abstract Iterator<E> elements(L value);

    @Override
    public Stream<FieldMetrics<?>> metrics() {
      return writer.metrics();
    }
  }

  /** Collection 写入器：将 Java Collection 的元素逐个写入 Parquet LIST。 */
  private static class CollectionWriter<E> extends RepeatedWriter<Collection<E>, E> {
    private CollectionWriter(
        int definitionLevel, int repetitionLevel, ParquetValueWriter<E> writer) {
      super(definitionLevel, repetitionLevel, writer);
    }

    @Override
    protected Iterator<E> elements(Collection<E> list) {
      return list.iterator();
    }
  }

  /**
   * 重复键值对写入器基类（抽象）：处理 Parquet MAP 编码中的 key-value 对写入。
   *
   * <p>逻辑：write 时遍历 Map 的 entry，为每个 key-value 写入两个三元组（含正确的 D/R level）， 空 map 写入一个 null 标记三元组。子类实现
   * pairs() 提供 entry 迭代器。
   */
  public abstract static class RepeatedKeyValueWriter<M, K, V> implements ParquetValueWriter<M> {
    private final int definitionLevel;
    private final int repetitionLevel;
    private final ParquetValueWriter<K> keyWriter;
    private final ParquetValueWriter<V> valueWriter;
    private final List<TripleWriter<?>> children;

    protected RepeatedKeyValueWriter(
        int definitionLevel,
        int repetitionLevel,
        ParquetValueWriter<K> keyWriter,
        ParquetValueWriter<V> valueWriter) {
      this.definitionLevel = definitionLevel;
      this.repetitionLevel = repetitionLevel;
      this.keyWriter = keyWriter;
      this.valueWriter = valueWriter;
      this.children =
          ImmutableList.<TripleWriter<?>>builder()
              .addAll(keyWriter.columns())
              .addAll(valueWriter.columns())
              .build();
    }

    @Override
    public void write(int parentRepetition, M value) {
      Iterator<Map.Entry<K, V>> pairs = pairs(value);

      if (!pairs.hasNext()) {
        // write the empty map to each column
        for (TripleWriter<?> column : children) {
          column.writeNull(parentRepetition, definitionLevel - 1);
        }

      } else {
        boolean first = true;
        while (pairs.hasNext()) {
          Map.Entry<K, V> pair = pairs.next();

          int rl = repetitionLevel;
          if (first) {
            rl = parentRepetition;
            first = false;
          }

          keyWriter.write(rl, pair.getKey());
          valueWriter.write(rl, pair.getValue());
        }
      }
    }

    @Override
    public List<TripleWriter<?>> columns() {
      return children;
    }

    @Override
    public void setColumnStore(ColumnWriteStore columnStore) {
      keyWriter.setColumnStore(columnStore);
      valueWriter.setColumnStore(columnStore);
    }

    protected abstract Iterator<Map.Entry<K, V>> pairs(M value);

    @Override
    public Stream<FieldMetrics<?>> metrics() {
      return Stream.concat(keyWriter.metrics(), valueWriter.metrics());
    }
  }

  /** Map 写入器：将 Java Map 的 key-value 对逐个写入 Parquet MAP。 */
  private static class MapWriter<K, V> extends RepeatedKeyValueWriter<Map<K, V>, K, V> {
    private MapWriter(
        int definitionLevel,
        int repetitionLevel,
        ParquetValueWriter<K> keyWriter,
        ParquetValueWriter<V> valueWriter) {
      super(definitionLevel, repetitionLevel, keyWriter, valueWriter);
    }

    @Override
    protected Iterator<Map.Entry<K, V>> pairs(Map<K, V> map) {
      return map.entrySet().iterator();
    }
  }

  /**
   * Struct 写入器基类（抽象）：将记录的字段逐个通过子写入器写入。
   *
   * <p>设计要点：write 时通过 get() 取出各字段值并委托子写入器写入； 子类实现 get() 以适配具体记录类型。
   */
  public abstract static class StructWriter<S> implements ParquetValueWriter<S> {
    private final ParquetValueWriter<Object>[] writers;
    private final List<TripleWriter<?>> children;

    @SuppressWarnings("unchecked")
    protected StructWriter(List<ParquetValueWriter<?>> writers) {
      this.writers =
          (ParquetValueWriter<Object>[])
              Array.newInstance(ParquetValueWriter.class, writers.size());

      ImmutableList.Builder<TripleWriter<?>> columnsBuilder = ImmutableList.builder();
      for (int i = 0; i < writers.size(); i += 1) {
        ParquetValueWriter<?> writer = writers.get(i);
        this.writers[i] = (ParquetValueWriter<Object>) writer;
        columnsBuilder.addAll(writer.columns());
      }

      this.children = columnsBuilder.build();
    }

    @Override
    public void write(int repetitionLevel, S value) {
      for (int i = 0; i < writers.length; i += 1) {
        Object fieldValue = get(value, i);
        writers[i].write(repetitionLevel, fieldValue);
      }
    }

    @Override
    public List<TripleWriter<?>> columns() {
      return children;
    }

    @Override
    public void setColumnStore(ColumnWriteStore columnStore) {
      for (ParquetValueWriter<?> writer : writers) {
        writer.setColumnStore(columnStore);
      }
    }

    protected abstract Object get(S struct, int index);

    @Override
    public Stream<FieldMetrics<?>> metrics() {
      return Arrays.stream(writers).flatMap(ParquetValueWriter::metrics);
    }
  }

  /** PositionDelete Struct 写入器：专门写入位置删除（pos + row）记录。 */
  public static class PositionDeleteStructWriter<R> extends StructWriter<PositionDelete<R>> {
    private final Function<CharSequence, ?> pathTransformFunc;

    public PositionDeleteStructWriter(
        StructWriter<?> replacedWriter, Function<CharSequence, ?> pathTransformFunc) {
      super(Arrays.asList(replacedWriter.writers));
      this.pathTransformFunc = pathTransformFunc;
    }

    @Override
    protected Object get(PositionDelete<R> delete, int index) {
      switch (index) {
        case 0:
          return pathTransformFunc.apply(delete.path());
        case 1:
          return delete.pos();
        case 2:
          return delete.row();
      }
      throw new IllegalArgumentException("Cannot get value for invalid index: " + index);
    }
  }
}
