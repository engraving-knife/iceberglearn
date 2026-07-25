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
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.apache.iceberg.FieldMetrics;
import org.apache.iceberg.orc.OrcValueWriter;
import org.apache.iceberg.util.UUIDUtil;
import org.apache.orc.TypeDescription;
import org.apache.orc.storage.common.type.HiveDecimal;
import org.apache.orc.storage.ql.exec.vector.BytesColumnVector;
import org.apache.orc.storage.ql.exec.vector.ColumnVector;
import org.apache.orc.storage.ql.exec.vector.DecimalColumnVector;
import org.apache.orc.storage.ql.exec.vector.ListColumnVector;
import org.apache.orc.storage.ql.exec.vector.MapColumnVector;
import org.apache.orc.storage.ql.exec.vector.TimestampColumnVector;
import org.apache.spark.sql.catalyst.util.ArrayData;
import org.apache.spark.sql.catalyst.util.MapData;
import org.apache.spark.sql.types.Decimal;
import org.apache.spark.unsafe.types.UTF8String;

/**
 * Spark 值 -> ORC 列向量的写入器集合。
 *
 * <p>所属模块：iceberg-spark（Spark v3.5 集成模块），data 子包。负责把 Spark 内部数据类型
 * （UTF8String、Decimal、ArrayData、MapData 等）写入 ORC 的 ColumnVector。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>提供 string/uuid/timestampTz/decimal/list/map 等类型的 {@link OrcValueWriter} 实现。
 *   <li>处理 decimal 精度分流（<=18 用 long，>18 用 HiveDecimal）。
 *   <li>处理 list/map 的子向量扩容与偏移记录。
 * </ul>
 *
 * <p>设计意图：每种类型一个独立的 writer 单例或实例，避免类型分支重复；list/map writer 通过 {@link SparkOrcWriter.FieldGetter} 从
 * Spark ArrayData/MapData 取元素，复用元素 writer 递归写入。 growColumnVector 用 3 倍增长因子减少频繁分配。
 *
 * <p>上下游关系：被 {@link SparkOrcWriter} 调用以构造字段 writer；依赖 ORC ColumnVector API。
 */
class SparkOrcValueWriters {
  private SparkOrcValueWriters() {}

  /** 返回写入 UTF8String 到 BytesColumnVector 的 writer 单例。 */
  static OrcValueWriter<?> strings() {
    return StringWriter.INSTANCE;
  }

  /** 返回写入 UUID（UTF8String 形式）为字节向量的 writer 单例。 */
  static OrcValueWriter<?> uuids() {
    return UUIDWriter.INSTANCE;
  }

  /** 返回写入带时区时间戳（Long 微秒）到 TimestampColumnVector 的 writer 单例。 */
  static OrcValueWriter<?> timestampTz() {
    return TimestampTzWriter.INSTANCE;
  }

  /** 根据精度选择 Decimal18Writer（<=18）或 Decimal38Writer（>18）。 */
  static OrcValueWriter<?> decimal(int precision, int scale) {
    if (precision <= 18) {
      return new Decimal18Writer(scale);
    } else {
      return new Decimal38Writer();
    }
  }

  /** 构造 list writer，元素由 element writer 写入。 */
  static OrcValueWriter<?> list(OrcValueWriter<?> element, List<TypeDescription> orcType) {
    return new ListWriter<>(element, orcType);
  }

  /** 构造 map writer，key/value 分别由对应 writer 写入。 */
  static OrcValueWriter<?> map(
      OrcValueWriter<?> keyWriter, OrcValueWriter<?> valueWriter, List<TypeDescription> orcTypes) {
    return new MapWriter<>(keyWriter, valueWriter, orcTypes);
  }

  /** 把 Spark {@link UTF8String} 写入 ORC BytesColumnVector 的 writer。 */
  private static class StringWriter implements OrcValueWriter<UTF8String> {
    private static final StringWriter INSTANCE = new StringWriter();
    /** 执行 nonNullWrite 相关操作。 */
    @Override
    public void nonNullWrite(int rowId, UTF8String data, ColumnVector output) {
      byte[] value = data.getBytes();
      ((BytesColumnVector) output).setRef(rowId, value, 0, value.length);
    }
  }

  /** 把 Spark UTF8String 形式的 UUID 转为字节后写入 BytesColumnVector 的 writer。 */
  private static class UUIDWriter implements OrcValueWriter<UTF8String> {
    private static final UUIDWriter INSTANCE = new UUIDWriter();
    /** 执行 nonNullWrite 相关操作。 */
    @Override
    public void nonNullWrite(int rowId, UTF8String data, ColumnVector output) {
      // ((BytesColumnVector) output).setRef(..) just stores a reference to the passed byte[], so
      // can't use a ThreadLocal ByteBuffer here like in other places because subsequent writes
      // would then overwrite previous values
      ByteBuffer buffer = UUIDUtil.convertToByteBuffer(UUID.fromString(data.toString()));
      ((BytesColumnVector) output).setRef(rowId, buffer.array(), 0, buffer.array().length);
    }
  }

  /** 把 Long 微秒时间戳拆分为毫秒与纳秒写入 TimestampColumnVector 的 writer。 */
  private static class TimestampTzWriter implements OrcValueWriter<Long> {
    private static final TimestampTzWriter INSTANCE = new TimestampTzWriter();
    /** 执行 nonNullWrite 相关操作。 */
    @Override
    public void nonNullWrite(int rowId, Long micros, ColumnVector output) {
      TimestampColumnVector cv = (TimestampColumnVector) output;
      cv.time[rowId] = Math.floorDiv(micros, 1_000); // millis
      cv.nanos[rowId] = (int) Math.floorMod(micros, 1_000_000) * 1_000; // nanos
    }
  }

  /** 把 Spark Decimal（精度<=18）以 unscaled long + scale 写入 DecimalColumnVector 的 writer。 */
  private static class Decimal18Writer implements OrcValueWriter<Decimal> {
    private final int scale;

    Decimal18Writer(int scale) {
      this.scale = scale;
    }
    /** 执行 nonNullWrite 相关操作。 */
    @Override
    public void nonNullWrite(int rowId, Decimal decimal, ColumnVector output) {
      ((DecimalColumnVector) output)
          .vector[rowId].setFromLongAndScale(decimal.toUnscaledLong(), scale);
    }
  }

  /** 把 Spark Decimal（精度>18）转为 HiveDecimal 写入 DecimalColumnVector 的 writer。 */
  private static class Decimal38Writer implements OrcValueWriter<Decimal> {
    /** 执行 nonNullWrite 相关操作。 */
    @Override
    public void nonNullWrite(int rowId, Decimal decimal, ColumnVector output) {
      ((DecimalColumnVector) output)
          .vector[rowId].set(HiveDecimal.create(decimal.toJavaBigDecimal()));
    }
  }

  /**
   * 把 Spark {@link ArrayData} 写入 ORC ListColumnVector 的 writer。
   *
   * <p>设计意图：记录每个 list 的长度与偏移，必要时扩容子向量，逐元素委托给元素 writer。
   */
  private static class ListWriter<T> implements OrcValueWriter<ArrayData> {
    private final OrcValueWriter<T> writer;
    private final SparkOrcWriter.FieldGetter<T> fieldGetter;

    @SuppressWarnings("unchecked")
    ListWriter(OrcValueWriter<T> writer, List<TypeDescription> orcTypes) {
      if (orcTypes.size() != 1) {
        throw new IllegalArgumentException(
            "Expected one (and same) ORC type for list elements, got: " + orcTypes);
      }
      this.writer = writer;
      this.fieldGetter =
          (SparkOrcWriter.FieldGetter<T>) SparkOrcWriter.createFieldGetter(orcTypes.get(0));
    }
    /** 执行 nonNullWrite 相关操作。 */
    @Override
    public void nonNullWrite(int rowId, ArrayData value, ColumnVector output) {
      ListColumnVector cv = (ListColumnVector) output;
      // record the length and start of the list elements
      cv.lengths[rowId] = value.numElements();
      cv.offsets[rowId] = cv.childCount;
      cv.childCount = (int) (cv.childCount + cv.lengths[rowId]);
      // make sure the child is big enough
      growColumnVector(cv.child, cv.childCount);
      // Add each element
      for (int e = 0; e < cv.lengths[rowId]; ++e) {
        writer.write((int) (e + cv.offsets[rowId]), fieldGetter.getFieldOrNull(value, e), cv.child);
      }
    }
    /** 执行 metrics 相关操作。 */
    @Override
    public Stream<FieldMetrics<?>> metrics() {
      return writer.metrics();
    }
  }

  /**
   * 把 Spark {@link MapData} 写入 ORC MapColumnVector 的 writer。
   *
   * <p>设计意图：记录每个 map 的长度与偏移，扩容 key/value 子向量，逐 entry 委托给 key/value writer。
   */
  private static class MapWriter<K, V> implements OrcValueWriter<MapData> {
    private final OrcValueWriter<K> keyWriter;
    private final OrcValueWriter<V> valueWriter;
    private final SparkOrcWriter.FieldGetter<K> keyFieldGetter;
    private final SparkOrcWriter.FieldGetter<V> valueFieldGetter;

    @SuppressWarnings("unchecked")
    MapWriter(
        OrcValueWriter<K> keyWriter,
        OrcValueWriter<V> valueWriter,
        List<TypeDescription> orcTypes) {
      if (orcTypes.size() != 2) {
        throw new IllegalArgumentException(
            "Expected two ORC type descriptions for a map, got: " + orcTypes);
      }
      this.keyWriter = keyWriter;
      this.valueWriter = valueWriter;
      this.keyFieldGetter =
          (SparkOrcWriter.FieldGetter<K>) SparkOrcWriter.createFieldGetter(orcTypes.get(0));
      this.valueFieldGetter =
          (SparkOrcWriter.FieldGetter<V>) SparkOrcWriter.createFieldGetter(orcTypes.get(1));
    }
    /** 执行 nonNullWrite 相关操作。 */
    @Override
    public void nonNullWrite(int rowId, MapData map, ColumnVector output) {
      ArrayData key = map.keyArray();
      ArrayData value = map.valueArray();
      MapColumnVector cv = (MapColumnVector) output;
      // record the length and start of the list elements
      cv.lengths[rowId] = value.numElements();
      cv.offsets[rowId] = cv.childCount;
      cv.childCount = (int) (cv.childCount + cv.lengths[rowId]);
      // make sure the child is big enough
      growColumnVector(cv.keys, cv.childCount);
      growColumnVector(cv.values, cv.childCount);
      // Add each element
      for (int e = 0; e < cv.lengths[rowId]; ++e) {
        int pos = (int) (e + cv.offsets[rowId]);
        keyWriter.write(pos, keyFieldGetter.getFieldOrNull(key, e), cv.keys);
        valueWriter.write(pos, valueFieldGetter.getFieldOrNull(value, e), cv.values);
      }
    }
    /** 执行 metrics 相关操作。 */
    @Override
    public Stream<FieldMetrics<?>> metrics() {
      return Stream.concat(keyWriter.metrics(), valueWriter.metrics());
    }
  }

  /** 按 3 倍增长因子扩容 ColumnVector，避免频繁分配。 */
  private static void growColumnVector(ColumnVector cv, int requestedSize) {
    if (cv.isNull.length < requestedSize) {
      // Use growth factor of 3 to avoid frequent array allocations
      cv.ensureSize(requestedSize * 3, true);
    }
  }
}
