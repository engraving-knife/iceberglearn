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

import static java.util.Collections.emptyIterator;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.parquet.column.ColumnDescriptor;
import org.apache.parquet.column.page.PageReadStore;
import org.apache.parquet.io.api.Binary;
import org.apache.parquet.schema.Type;

/**
 * 文件级说明：Parquet 值读取器集合，提供各类型的基础读取器实现。
 *
 * <p>所属模块：iceberg-parquet（读取器基础设施，位于 org.apache.iceberg.parquet 包）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>提供原始类型读取器（UnboxedReader/StringReader/BytesReader 等），封装 Parquet 列迭代器。
 *   <li>提供组合读取器（OptionReader/StructReader/ListReader/MapReader）处理嵌套结构与 null 语义。
 *   <li>提供工具读取器（NullReader/ConstantReader/PositionReader）处理缺失列、常量列、行号列。
 *   <li>提供类型转换读取器（IntAsLong/FloatAsDouble/IntegerAsDecimal/LongAsDecimal/BinaryAsDecimal）。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>组合模式：StructReader/ListReader/MapReader 通过组合子读取器实现嵌套结构的递归读取。
 *   <li>Definition Level 驱动：OptionReader/RepeatedReader 通过 DL 判断 null/空集合/非空集合。
 *   <li>容器复用：ListReader/MapReader 支持复用容器对象以减少 GC 压力。
 * </ul>
 *
 * <p>上下游关系：被 BaseParquetReaders/ParquetAvroValueReaders 等构建器使用； 依赖
 * ColumnIterator/PageIterator（底层列迭代器）。
 */
public class ParquetValueReaders {
  private ParquetValueReaders() {}

  /**
   * 按 repetition 类型包装读取器：OPTIONAL 列用 OptionReader 包装以处理 null 语义。
   *
   * @param type Parquet 列类型
   * @param definitionLevel 字段的最大定义级别
   * @param reader 原始读取器
   * @return 包装后的读取器（REQUIRED 列直接返回原读取器）
   */
  public static <T> ParquetValueReader<T> option(
      Type type, int definitionLevel, ParquetValueReader<T> reader) {
    if (type.isRepetition(Type.Repetition.OPTIONAL)) {
      return new OptionReader<>(definitionLevel, reader);
    }
    return reader;
  }

  /** 返回 NullReader：所有行都返回 null（用于缺失列）。 */
  @SuppressWarnings("unchecked")
  public static <T> ParquetValueReader<T> nulls() {
    return (ParquetValueReader<T>) NullReader.INSTANCE;
  }

  /** 返回 ConstantReader：所有行都返回指定常量值。 */
  public static <C> ParquetValueReader<C> constant(C value) {
    return new ConstantReader<>(value);
  }

  /** 返回 ConstantReader（带定义级别，用于嵌套结构中的常量列）。 */
  public static <C> ParquetValueReader<C> constant(C value, int definitionLevel) {
    return new ConstantReader<>(value, definitionLevel);
  }

  /** 返回 PositionReader：读取行号（ROW_POSITION 元数据列）。 */
  public static ParquetValueReader<Long> position() {
    return new PositionReader();
  }

  /** Null 读取器：用于缺失列，所有行返回 null，不读取任何 Parquet 数据。 */
  private static class NullReader<T> implements ParquetValueReader<T> {
    private static final NullReader<Void> INSTANCE = new NullReader<>();
    private static final ImmutableList<TripleIterator<?>> COLUMNS = ImmutableList.of();
    private static final TripleIterator<?> NULL_COLUMN =
        new TripleIterator<Object>() {
          @Override
          public int currentDefinitionLevel() {
            return 0;
          }

          @Override
          public int currentRepetitionLevel() {
            return 0;
          }

          @Override
          public <N> N nextNull() {
            return null;
          }

          @Override
          public boolean hasNext() {
            return false;
          }

          @Override
          public Object next() {
            return null;
          }
        };

    private NullReader() {}

    @Override
    public T read(T reuse) {
      return null;
    }

    @Override
    public TripleIterator<?> column() {
      return NULL_COLUMN;
    }

    @Override
    public List<TripleIterator<?>> columns() {
      return COLUMNS;
    }

    @Override
    public void setPageSource(PageReadStore pageStore, long rowPosition) {}
  }

  /** 常量读取器：所有行返回指定常量值，用于投影中的常量列。 */
  static class ConstantReader<C> implements ParquetValueReader<C> {
    private final C constantValue;
    private final TripleIterator<?> column;
    private final List<TripleIterator<?>> children;

    ConstantReader(C constantValue) {
      this.constantValue = constantValue;
      this.column = NullReader.NULL_COLUMN;
      this.children = NullReader.COLUMNS;
    }

    ConstantReader(C constantValue, int definitionLevel) {
      this.constantValue = constantValue;
      this.column =
          new TripleIterator<Object>() {
            @Override
            public int currentDefinitionLevel() {
              return definitionLevel;
            }

            @Override
            public int currentRepetitionLevel() {
              return 0;
            }

            @Override
            public <N> N nextNull() {
              return null;
            }

            @Override
            public boolean hasNext() {
              return false;
            }

            @Override
            public Object next() {
              return null;
            }
          };

      this.children = ImmutableList.of(column);
    }

    @Override
    public C read(C reuse) {
      return constantValue;
    }

    @Override
    public TripleIterator<?> column() {
      return column;
    }

    @Override
    public List<TripleIterator<?>> columns() {
      return children;
    }

    @Override
    public void setPageSource(PageReadStore pageStore, long rowPosition) {}
  }

  /** 行号读取器：返回当前行的全局行号（用于 ROW_POSITION 元数据列）。 */
  static class PositionReader implements ParquetValueReader<Long> {
    private long rowOffset = -1;
    private long rowGroupStart;

    @Override
    public Long read(Long reuse) {
      rowOffset = rowOffset + 1;
      return rowGroupStart + rowOffset;
    }

    @Override
    public TripleIterator<?> column() {
      return NullReader.NULL_COLUMN;
    }

    @Override
    public List<TripleIterator<?>> columns() {
      return NullReader.COLUMNS;
    }

    @Override
    public void setPageSource(PageReadStore pageStore, long rowPosition) {
      this.rowGroupStart = rowPosition;
      this.rowOffset = -1;
    }
  }

  /** 原始类型读取器基类（抽象）：封装 {@link ColumnIterator}，提供页面源设置和列迭代器访问。 子类通过 column.nextXxx() 读取具体类型的值。 */
  public abstract static class PrimitiveReader<T> implements ParquetValueReader<T> {
    private final ColumnDescriptor desc;

    @SuppressWarnings("checkstyle:VisibilityModifier")
    protected final ColumnIterator<?> column;

    private final List<TripleIterator<?>> children;

    protected PrimitiveReader(ColumnDescriptor desc) {
      this.desc = desc;
      this.column = ColumnIterator.newIterator(desc, "");
      this.children = ImmutableList.of(column);
    }

    @Override
    public void setPageSource(PageReadStore pageStore, long rowPosition) {
      column.setPageSource(pageStore.getPageReader(desc));
    }

    @Override
    public TripleIterator<?> column() {
      return column;
    }

    @Override
    public List<TripleIterator<?>> columns() {
      return children;
    }
  }

  /** 拆箱读取器：提供 readBoolean/Integer/Long/Float/Double/Binary 等原始类型读取方法。 read() 方法默认抛异常，由具体子类覆写。 */
  public static class UnboxedReader<T> extends PrimitiveReader<T> {
    public UnboxedReader(ColumnDescriptor desc) {
      super(desc);
    }

    @Override
    @SuppressWarnings("unchecked")
    public T read(T ignored) {
      return (T) column.next();
    }

    public boolean readBoolean() {
      return column.nextBoolean();
    }

    public int readInteger() {
      return column.nextInteger();
    }

    public long readLong() {
      return column.nextLong();
    }

    public float readFloat() {
      return column.nextFloat();
    }

    public double readDouble() {
      return column.nextDouble();
    }

    public Binary readBinary() {
      return column.nextBinary();
    }
  }

  /** String 读取器：将 Parquet Binary 按 UTF-8 解码为 String。 */
  public static class StringReader extends PrimitiveReader<String> {
    public StringReader(ColumnDescriptor desc) {
      super(desc);
    }

    @Override
    public String read(String reuse) {
      return column.nextBinary().toStringUsingUTF8();
    }
  }

  /** INT32→Long 读取器：将 Parquet INT32 读为 Java long（Iceberg LONG 类型读 INT32 列时使用）。 */
  public static class IntAsLongReader extends UnboxedReader<Long> {
    public IntAsLongReader(ColumnDescriptor desc) {
      super(desc);
    }

    @Override
    public Long read(Long ignored) {
      return readLong();
    }

    @Override
    public long readLong() {
      return super.readInteger();
    }
  }

  /** FLOAT→Double 读取器：将 Parquet FLOAT 读为 Java double（Iceberg DOUBLE 类型读 FLOAT 列时使用）。 */
  public static class FloatAsDoubleReader extends UnboxedReader<Double> {
    public FloatAsDoubleReader(ColumnDescriptor desc) {
      super(desc);
    }

    @Override
    public Double read(Double ignored) {
      return readDouble();
    }

    @Override
    public double readDouble() {
      return super.readFloat();
    }
  }

  /** INT32→Decimal 读取器：将 Parquet INT32 读为 BigDecimal（按 scale 解码）。 */
  public static class IntegerAsDecimalReader extends PrimitiveReader<BigDecimal> {
    private final int scale;

    public IntegerAsDecimalReader(ColumnDescriptor desc, int scale) {
      super(desc);
      this.scale = scale;
    }

    @Override
    public BigDecimal read(BigDecimal ignored) {
      return new BigDecimal(BigInteger.valueOf(column.nextInteger()), scale);
    }
  }

  /** INT64→Decimal 读取器：将 Parquet INT64 读为 BigDecimal（按 scale 解码）。 */
  public static class LongAsDecimalReader extends PrimitiveReader<BigDecimal> {
    private final int scale;

    public LongAsDecimalReader(ColumnDescriptor desc, int scale) {
      super(desc);
      this.scale = scale;
    }

    @Override
    public BigDecimal read(BigDecimal ignored) {
      return new BigDecimal(BigInteger.valueOf(column.nextLong()), scale);
    }
  }

  /** BINARY→Decimal 读取器：将 Parquet Binary 读为 BigDecimal（按 scale 解码）。 */
  public static class BinaryAsDecimalReader extends PrimitiveReader<BigDecimal> {
    private int scale;

    public BinaryAsDecimalReader(ColumnDescriptor desc, int scale) {
      super(desc);
      this.scale = scale;
    }

    @Override
    public BigDecimal read(BigDecimal reuse) {
      byte[] bytes = column.nextBinary().getBytesUnsafe();
      return new BigDecimal(new BigInteger(bytes), scale);
    }
  }

  /** Bytes 读取器：将 Parquet Binary 读为 ByteBuffer，支持复用已有 buffer。 */
  public static class BytesReader extends PrimitiveReader<ByteBuffer> {
    public BytesReader(ColumnDescriptor desc) {
      super(desc);
    }

    @Override
    public ByteBuffer read(ByteBuffer reuse) {
      Binary binary = column.nextBinary();
      ByteBuffer data = binary.toByteBuffer();
      if (reuse != null && reuse.hasArray() && reuse.capacity() >= data.remaining()) {
        data.get(reuse.array(), reuse.arrayOffset(), data.remaining());
        reuse.position(0);
        reuse.limit(binary.length());
        return reuse;
      } else {
        byte[] array = new byte[data.remaining()];
        data.get(array, 0, data.remaining());
        return ByteBuffer.wrap(array);
      }
    }
  }

  /** byte[] 读取器：将 Parquet Binary 读为 byte 数组。 */
  public static class ByteArrayReader extends ParquetValueReaders.PrimitiveReader<byte[]> {
    public ByteArrayReader(ColumnDescriptor desc) {
      super(desc);
    }

    @Override
    public byte[] read(byte[] ignored) {
      return column.nextBinary().getBytes();
    }
  }

  /**
   * 可选字段读取器：通过 definition level 判断字段是否为 null。
   *
   * <p>逻辑：read 时若 currentDefinitionLevel > definitionLevel 表示非 null，委托子读取器读取； 否则返回 null 并消费 null
   * 三元组。
   */
  private static class OptionReader<T> implements ParquetValueReader<T> {
    private final int definitionLevel;
    private final ParquetValueReader<T> reader;
    private final TripleIterator<?> column;
    private final List<TripleIterator<?>> children;

    OptionReader(int definitionLevel, ParquetValueReader<T> reader) {
      this.definitionLevel = definitionLevel;
      this.reader = reader;
      this.column = reader.column();
      this.children = reader.columns();
    }

    @Override
    public void setPageSource(PageReadStore pageStore, long rowPosition) {
      reader.setPageSource(pageStore, rowPosition);
    }

    @Override
    public TripleIterator<?> column() {
      return column;
    }

    @Override
    public T read(T reuse) {
      if (column.currentDefinitionLevel() > definitionLevel) {
        return reader.read(reuse);
      }

      for (TripleIterator<?> child : children) {
        child.nextNull();
      }

      return null;
    }

    @Override
    public List<TripleIterator<?>> columns() {
      return children;
    }
  }

  /**
   * 重复字段读取器基类（抽象）：处理 Parquet LIST 编码中的重复元素。
   *
   * <p>逻辑：read 时通过 do-while 循环读取元素，DL 判断空/非空列表，RL 判断列表结束。 子类实现
   * newListData/getElement/addElement/buildList 以适配具体集合类型。
   */
  public abstract static class RepeatedReader<T, I, E> implements ParquetValueReader<T> {
    private final int definitionLevel;
    private final int repetitionLevel;
    private final ParquetValueReader<E> reader;
    private final TripleIterator<?> column;
    private final List<TripleIterator<?>> children;

    protected RepeatedReader(
        int definitionLevel, int repetitionLevel, ParquetValueReader<E> reader) {
      this.definitionLevel = definitionLevel;
      this.repetitionLevel = repetitionLevel;
      this.reader = reader;
      this.column = reader.column();
      this.children = reader.columns();
    }

    @Override
    public void setPageSource(PageReadStore pageStore, long rowPosition) {
      reader.setPageSource(pageStore, rowPosition);
    }

    @Override
    public TripleIterator<?> column() {
      return column;
    }

    @Override
    public T read(T reuse) {
      I intermediate = newListData(reuse);

      do {
        if (column.currentDefinitionLevel() > definitionLevel) {
          addElement(intermediate, reader.read(getElement(intermediate)));
        } else {
          // consume the empty list triple
          for (TripleIterator<?> child : children) {
            child.nextNull();
          }
          // if the current definition level is equal to the definition level of this repeated type,
          // then the result is an empty list and the repetition level will always be <= rl.
          break;
        }
      } while (column.currentRepetitionLevel() > repetitionLevel);

      return buildList(intermediate);
    }

    @Override
    public List<TripleIterator<?>> columns() {
      return children;
    }

    protected abstract I newListData(T reuse);

    protected abstract E getElement(I list);

    protected abstract void addElement(I list, E element);

    protected abstract T buildList(I list);
  }

  /** List 读取器：将 Parquet LIST 读取为 Java List，支持复用列表容器。 */
  public static class ListReader<E> extends RepeatedReader<List<E>, List<E>, E> {
    private List<E> lastList = null;
    private Iterator<E> elements = null;

    public ListReader(int definitionLevel, int repetitionLevel, ParquetValueReader<E> reader) {
      super(definitionLevel, repetitionLevel, reader);
    }

    @Override
    protected List<E> newListData(List<E> reuse) {
      List<E> list;
      if (lastList != null) {
        lastList.clear();
        list = lastList;
      } else {
        list = Lists.newArrayList();
      }

      if (reuse != null) {
        this.lastList = reuse;
        this.elements = reuse.iterator();
      } else {
        this.lastList = null;
        this.elements = emptyIterator();
      }

      return list;
    }

    @Override
    protected E getElement(List<E> reuse) {
      if (elements.hasNext()) {
        return elements.next();
      }

      return null;
    }

    @Override
    protected void addElement(List<E> list, E element) {
      list.add(element);
    }

    @Override
    protected List<E> buildList(List<E> list) {
      return list;
    }
  }

  /**
   * 重复键值对读取器基类（抽象）：处理 Parquet MAP 编码中的重复 key-value 对。
   *
   * <p>逻辑：read 时通过 do-while 循环读取 key-value 对，DL 判断空/非空 map，RL 判断 map 结束。 子类实现
   * newMapData/getPair/addPair/buildMap 以适配具体 map 类型。
   */
  public abstract static class RepeatedKeyValueReader<M, I, K, V> implements ParquetValueReader<M> {
    private final int definitionLevel;
    private final int repetitionLevel;
    private final ParquetValueReader<K> keyReader;
    private final ParquetValueReader<V> valueReader;
    private final TripleIterator<?> column;
    private final List<TripleIterator<?>> children;

    protected RepeatedKeyValueReader(
        int definitionLevel,
        int repetitionLevel,
        ParquetValueReader<K> keyReader,
        ParquetValueReader<V> valueReader) {
      this.definitionLevel = definitionLevel;
      this.repetitionLevel = repetitionLevel;
      this.keyReader = keyReader;
      this.valueReader = valueReader;
      this.column = keyReader.column();
      this.children =
          ImmutableList.<TripleIterator<?>>builder()
              .addAll(keyReader.columns())
              .addAll(valueReader.columns())
              .build();
    }

    @Override
    public void setPageSource(PageReadStore pageStore, long rowPosition) {
      keyReader.setPageSource(pageStore, rowPosition);
      valueReader.setPageSource(pageStore, rowPosition);
    }

    @Override
    public TripleIterator<?> column() {
      return column;
    }

    @Override
    public M read(M reuse) {
      I intermediate = newMapData(reuse);

      do {
        if (column.currentDefinitionLevel() > definitionLevel) {
          Map.Entry<K, V> pair = getPair(intermediate);
          addPair(intermediate, keyReader.read(pair.getKey()), valueReader.read(pair.getValue()));
        } else {
          // consume the empty map triple
          for (TripleIterator<?> child : children) {
            child.nextNull();
          }
          // if the current definition level is equal to the definition level of this repeated type,
          // then the result is an empty list and the repetition level will always be <= rl.
          break;
        }
      } while (column.currentRepetitionLevel() > repetitionLevel);

      return buildMap(intermediate);
    }

    @Override
    public List<TripleIterator<?>> columns() {
      return children;
    }

    protected abstract I newMapData(M reuse);

    protected abstract Map.Entry<K, V> getPair(I map);

    protected abstract void addPair(I map, K key, V value);

    protected abstract M buildMap(I map);
  }

  /** Map 读取器：将 Parquet MAP 读取为 Java Map，支持复用 map 容器。 */
  public static class MapReader<K, V> extends RepeatedKeyValueReader<Map<K, V>, Map<K, V>, K, V> {
    private final ReusableEntry<K, V> nullEntry = new ReusableEntry<>();
    private Map<K, V> lastMap = null;
    private Iterator<Map.Entry<K, V>> pairs = null;

    public MapReader(
        int definitionLevel,
        int repetitionLevel,
        ParquetValueReader<K> keyReader,
        ParquetValueReader<V> valueReader) {
      super(definitionLevel, repetitionLevel, keyReader, valueReader);
    }

    @Override
    protected Map<K, V> newMapData(Map<K, V> reuse) {
      Map<K, V> map;
      if (lastMap != null) {
        lastMap.clear();
        map = lastMap;
      } else {
        map = Maps.newLinkedHashMap();
      }

      if (reuse != null) {
        this.lastMap = reuse;
        this.pairs = reuse.entrySet().iterator();
      } else {
        this.lastMap = null;
        this.pairs = emptyIterator();
      }

      return map;
    }

    @Override
    protected Map.Entry<K, V> getPair(Map<K, V> map) {
      if (pairs.hasNext()) {
        return pairs.next();
      } else {
        return nullEntry;
      }
    }

    @Override
    protected void addPair(Map<K, V> map, K key, V value) {
      map.put(key, value);
    }

    @Override
    protected Map<K, V> buildMap(Map<K, V> map) {
      return map;
    }
  }

  /** 可复用的 Map.Entry 实现：避免在 MapReader 中频繁创建 Entry 对象。 */
  public static class ReusableEntry<K, V> implements Map.Entry<K, V> {
    private K key = null;
    private V value = null;

    public void set(K newKey, V newValue) {
      this.key = newKey;
      this.value = newValue;
    }

    @Override
    public K getKey() {
      return key;
    }

    @Override
    public V getValue() {
      return value;
    }

    @Override
    public V setValue(V newValue) {
      V lastValue = this.value;
      this.value = newValue;
      return lastValue;
    }
  }

  /**
   * Struct 读取器基类（抽象）：将各字段读取器组合为一行记录。
   *
   * <p>设计意图：
   *
   * <ul>
   *   <li>模板方法：子类实现 newStructData/getField/buildStruct/set 以适配具体记录类型。
   *   <li>Setter 内部类：按字段位置分发值写入，支持 null 和各原始类型。
   *   <li>列复用：setPageSource 将页面源传递给所有子读取器。
   * </ul>
   */
  public abstract static class StructReader<T, I> implements ParquetValueReader<T> {
    private interface Setter<R> {
      void set(R record, int pos, Object reuse);
    }

    private final ParquetValueReader<?>[] readers;
    private final TripleIterator<?> column;
    private final List<TripleIterator<?>> children;

    @SuppressWarnings("unchecked")
    protected StructReader(List<Type> types, List<ParquetValueReader<?>> readers) {
      this.readers =
          (ParquetValueReader<?>[]) Array.newInstance(ParquetValueReader.class, readers.size());
      TripleIterator<?>[] columns =
          (TripleIterator<?>[]) Array.newInstance(TripleIterator.class, readers.size());
      Setter<I>[] setters = (Setter<I>[]) Array.newInstance(Setter.class, readers.size());

      ImmutableList.Builder<TripleIterator<?>> columnsBuilder = ImmutableList.builder();
      for (int i = 0; i < readers.size(); i += 1) {
        ParquetValueReader<?> reader = readers.get(i);
        this.readers[i] = readers.get(i);
        columns[i] = reader.column();
        setters[i] = newSetter(reader, types.get(i));
        columnsBuilder.addAll(reader.columns());
      }

      this.children = columnsBuilder.build();
      this.column = firstNonNullColumn(children);
    }

    @Override
    public final void setPageSource(PageReadStore pageStore, long rowPosition) {
      for (ParquetValueReader<?> reader : readers) {
        reader.setPageSource(pageStore, rowPosition);
      }
    }

    @Override
    public final TripleIterator<?> column() {
      return column;
    }

    @Override
    public final T read(T reuse) {
      I intermediate = newStructData(reuse);

      for (int i = 0; i < readers.length; i += 1) {
        set(intermediate, i, readers[i].read(get(intermediate, i)));
        // setters[i].set(intermediate, i, get(intermediate, i));
      }

      return buildStruct(intermediate);
    }

    @Override
    public List<TripleIterator<?>> columns() {
      return children;
    }

    @SuppressWarnings("unchecked")
    private <E> Setter<I> newSetter(ParquetValueReader<E> reader, Type type) {
      if (reader instanceof UnboxedReader && type.isPrimitive()) {
        UnboxedReader<?> unboxed = (UnboxedReader<?>) reader;
        switch (type.asPrimitiveType().getPrimitiveTypeName()) {
          case BOOLEAN:
            return (record, pos, ignored) -> setBoolean(record, pos, unboxed.readBoolean());
          case INT32:
            return (record, pos, ignored) -> setInteger(record, pos, unboxed.readInteger());
          case INT64:
            return (record, pos, ignored) -> setLong(record, pos, unboxed.readLong());
          case FLOAT:
            return (record, pos, ignored) -> setFloat(record, pos, unboxed.readFloat());
          case DOUBLE:
            return (record, pos, ignored) -> setDouble(record, pos, unboxed.readDouble());
          case INT96:
          case FIXED_LEN_BYTE_ARRAY:
          case BINARY:
            return (record, pos, ignored) -> set(record, pos, unboxed.readBinary());
          default:
            throw new UnsupportedOperationException("Unsupported type: " + type);
        }
      }

      // TODO: Add support for options to avoid the null check
      return (record, pos, reuse) -> {
        Object obj = reader.read((E) reuse);
        if (obj != null) {
          set(record, pos, obj);
        } else {
          setNull(record, pos);
        }
      };
    }

    @SuppressWarnings("unchecked")
    private <E> E get(I intermediate, int pos) {
      return (E) getField(intermediate, pos);
    }

    protected abstract I newStructData(T reuse);

    protected abstract Object getField(I intermediate, int pos);

    protected abstract T buildStruct(I struct);

    /**
     * Used to set a struct value by position.
     *
     * <p>To avoid boxing, override {@link #setInteger(Object, int, int)} and similar methods.
     *
     * @param struct a struct object created by {@link #newStructData(Object)}
     * @param pos the position in the struct to set
     * @param value the value to set
     */
    protected abstract void set(I struct, int pos, Object value);

    protected void setNull(I struct, int pos) {
      set(struct, pos, null);
    }

    protected void setBoolean(I struct, int pos, boolean value) {
      set(struct, pos, value);
    }

    protected void setInteger(I struct, int pos, int value) {
      set(struct, pos, value);
    }

    protected void setLong(I struct, int pos, long value) {
      set(struct, pos, value);
    }

    protected void setFloat(I struct, int pos, float value) {
      set(struct, pos, value);
    }

    protected void setDouble(I struct, int pos, double value) {
      set(struct, pos, value);
    }

    /**
     * Find a non-null column or return NULL_COLUMN if one is not available.
     *
     * @param columns a collection of triple iterator columns
     * @return the first non-null column in columns
     */
    private TripleIterator<?> firstNonNullColumn(List<TripleIterator<?>> columns) {
      for (TripleIterator<?> col : columns) {
        if (col != NullReader.NULL_COLUMN) {
          return col;
        }
      }
      return NullReader.NULL_COLUMN;
    }
  }
}
