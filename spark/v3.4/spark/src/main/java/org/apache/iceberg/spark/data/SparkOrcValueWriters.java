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
 * 所属模块：iceberg-spark v3.4
 *
 * <p>职责：ORC 值写入器工厂集合，提供各 Spark 类型的 ORC 列写入器实例。
 *
 * <p>设计意图：以工厂方法集中创建写入器，复用公共逻辑。
 *
 * <p>上下游关系：由 SparkOrcWriter 使用。
 */
class SparkOrcValueWriters {
  private SparkOrcValueWriters() {}
  /** 执行 strings 相关操作。 */
  static OrcValueWriter<?> strings() {
    return StringWriter.INSTANCE;
  }
  /** 执行 uuids 相关操作。 */
  static OrcValueWriter<?> uuids() {
    return UUIDWriter.INSTANCE;
  }
  /** 执行 timestampTz 相关操作。 */
  static OrcValueWriter<?> timestampTz() {
    return TimestampTzWriter.INSTANCE;
  }
  /** 执行 decimal 相关操作。 */
  static OrcValueWriter<?> decimal(int precision, int scale) {
    if (precision <= 18) {
      return new Decimal18Writer(scale);
    } else {
      return new Decimal38Writer();
    }
  }
  /** 执行 list 相关操作。 */
  static OrcValueWriter<?> list(OrcValueWriter<?> element, List<TypeDescription> orcType) {
    return new ListWriter<>(element, orcType);
  }
  /** 执行 map 相关操作。 */
  static OrcValueWriter<?> map(
      OrcValueWriter<?> keyWriter, OrcValueWriter<?> valueWriter, List<TypeDescription> orcTypes) {
    return new MapWriter<>(keyWriter, valueWriter, orcTypes);
  }

  private static class StringWriter implements OrcValueWriter<UTF8String> {
    private static final StringWriter INSTANCE = new StringWriter();
    /** 执行 nonNullWrite 相关操作。 */
    @Override
    public void nonNullWrite(int rowId, UTF8String data, ColumnVector output) {
      byte[] value = data.getBytes();
      ((BytesColumnVector) output).setRef(rowId, value, 0, value.length);
    }
  }

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

  private static class Decimal38Writer implements OrcValueWriter<Decimal> {
    /** 执行 nonNullWrite 相关操作。 */
    @Override
    public void nonNullWrite(int rowId, Decimal decimal, ColumnVector output) {
      ((DecimalColumnVector) output)
          .vector[rowId].set(HiveDecimal.create(decimal.toJavaBigDecimal()));
    }
  }

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
  /** 执行 growColumnVector 相关操作。 */
  private static void growColumnVector(ColumnVector cv, int requestedSize) {
    if (cv.isNull.length < requestedSize) {
      // Use growth factor of 3 to avoid frequent array allocations
      cv.ensureSize(requestedSize * 3, true);
    }
  }
}
