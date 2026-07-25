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
package org.apache.iceberg;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import org.apache.avro.Schema;
import org.apache.avro.generic.IndexedRecord;
import org.apache.avro.specific.SpecificData.SchemaConstructable;
import org.apache.iceberg.ManifestFile.PartitionFieldSummary;
import org.apache.iceberg.avro.AvroSchemaUtil;
import org.apache.iceberg.relocated.com.google.common.annotations.VisibleForTesting;
import org.apache.iceberg.relocated.com.google.common.base.MoreObjects;
import org.apache.iceberg.types.Types;
import org.apache.iceberg.util.ByteBuffers;

/**
 * 分区字段摘要的通用实现：记录 manifest 中某分区字段的 containsNull、containsNaN、上下界。
 *
 * <p>所属模块：iceberg-core，实现 {@link PartitionFieldSummary} 接口，同时实现 {@link IndexedRecord}、{@link
 * StructLike}、{@link SchemaConstructable} 以支持 Avro 序列化。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>存储分区字段的统计摘要（是否含 null、是否含 NaN、lower/upper bound 字节数组）。
 *   <li>支持 Avro 投影读取：通过 {@code fromProjectionPos} 将投影字段序映射到完整字段序。
 *   <li>支持 Java 序列化与 Avro 反射实例化。
 * </ul>
 *
 * <p>设计意图：实现多个接口使其既能被 Avro 直接读写，又能作为 StructLike 被Iceberg 工具处理。 {@code fromProjectionPos} 处理
 * manifest 列裁剪场景下的字段位置映射；{@code byte[]} 存储 上下界避免反序列化开销，{@link #lowerBound()}/{@link #upperBound()}
 * 按需包装为 ByteBuffer。
 *
 * <p>上下游关系：由 {@code GenericManifestFile} 持有，被 {@link ManifestEvaluator} 在 分区级过滤时读取；通过 Avro 序列化到
 * manifest 文件。
 */
public class GenericPartitionFieldSummary
    implements PartitionFieldSummary, StructLike, IndexedRecord, SchemaConstructable, Serializable {
  private static final Schema AVRO_SCHEMA = AvroSchemaUtil.convert(PartitionFieldSummary.getType());

  private transient Schema avroSchema; // not final for Java serialization
  private int[] fromProjectionPos;

  // data fields
  private boolean containsNull = false;
  private Boolean containsNaN = null;
  private byte[] lowerBound = null;
  private byte[] upperBound = null;

  /**
   * Avro 反射读取时的构造器：根据投影 schema 建立字段位置映射。
   *
   * <p>逻辑：将投影 schema 的字段与完整 {@link PartitionFieldSummary#getType()} 字段比对， 记录每个投影字段在完整 schema 中的位置到
   * {@code fromProjectionPos}，供后续 get/set 映射。
   *
   * @param avroSchema Avro 投影 schema
   * @throws IllegalArgumentException 若投影字段在完整 schema 中找不到
   */
  public GenericPartitionFieldSummary(Schema avroSchema) {
    this.avroSchema = avroSchema;

    List<Types.NestedField> fields =
        AvroSchemaUtil.convert(avroSchema).asNestedType().asStructType().fields();
    List<Types.NestedField> allFields = PartitionFieldSummary.getType().fields();

    this.fromProjectionPos = new int[fields.size()];
    for (int i = 0; i < fromProjectionPos.length; i += 1) {
      boolean found = false;
      for (int j = 0; j < allFields.size(); j += 1) {
        if (fields.get(i).fieldId() == allFields.get(j).fieldId()) {
          found = true;
          fromProjectionPos[i] = j;
        }
      }

      if (!found) {
        throw new IllegalArgumentException("Cannot find projected field: " + fields.get(i));
      }
    }
  }

  /**
   * 全字段构造器：用于手动创建分区字段摘要。
   *
   * @param containsNull 是否含 null 值
   * @param containsNaN 是否含 NaN 值
   * @param lowerBound 下界（ByteBuffer）
   * @param upperBound 上界（ByteBuffer）
   */
  public GenericPartitionFieldSummary(
      boolean containsNull, boolean containsNaN, ByteBuffer lowerBound, ByteBuffer upperBound) {
    this.avroSchema = AVRO_SCHEMA;
    this.containsNull = containsNull;
    this.containsNaN = containsNaN;
    this.lowerBound = ByteBuffers.toByteArray(lowerBound);
    this.upperBound = ByteBuffers.toByteArray(upperBound);
    this.fromProjectionPos = null;
  }

  /** 仅用于向后兼容性测试的构造器：不含 containsNaN 字段。 */
  // for testing backward compatibility only
  @VisibleForTesting
  GenericPartitionFieldSummary(boolean containsNull, ByteBuffer lowerBound, ByteBuffer upperBound) {
    this.avroSchema = AVRO_SCHEMA;
    this.containsNull = containsNull;
    this.lowerBound = ByteBuffers.toByteArray(lowerBound);
    this.upperBound = ByteBuffers.toByteArray(upperBound);
    this.fromProjectionPos = null;
  }

  /**
   * 拷贝构造器：深拷贝上下界字节数组。
   *
   * @param toCopy 待拷贝的实例
   */
  private GenericPartitionFieldSummary(GenericPartitionFieldSummary toCopy) {
    this.avroSchema = toCopy.avroSchema;
    this.containsNull = toCopy.containsNull;
    this.containsNaN = toCopy.containsNaN;
    this.lowerBound =
        toCopy.lowerBound == null
            ? null
            : Arrays.copyOf(toCopy.lowerBound, toCopy.lowerBound.length);
    this.upperBound =
        toCopy.upperBound == null
            ? null
            : Arrays.copyOf(toCopy.upperBound, toCopy.upperBound.length);
    this.fromProjectionPos = toCopy.fromProjectionPos;
  }

  /** Java 序列化用的无参构造器。 */
  GenericPartitionFieldSummary() {}

  /** @return 该分区字段是否包含 null 值 */
  @Override
  public boolean containsNull() {
    return containsNull;
  }

  /** @return 该分区字段是否包含 NaN 值，可能为 null（旧版本 manifest 无此信息） */
  @Override
  public Boolean containsNaN() {
    return containsNaN;
  }

  /** @return 下界字节数组包装为 {@link ByteBuffer}，无统计时为 null */
  @Override
  public ByteBuffer lowerBound() {
    return lowerBound != null ? ByteBuffer.wrap(lowerBound) : null;
  }

  /** @return 上界字节数组包装为 {@link ByteBuffer}，无统计时为 null */
  @Override
  public ByteBuffer upperBound() {
    return upperBound != null ? ByteBuffer.wrap(upperBound) : null;
  }

  /** @return 完整 schema 的字段数量（{@link StructLike} 接口要求） */
  @Override
  public int size() {
    return PartitionFieldSummary.getType().fields().size();
  }

  /** 按序号读取字段并转型为指定类型。 */
  @Override
  public <T> T get(int pos, Class<T> javaClass) {
    return javaClass.cast(get(pos));
  }

  /**
   * 按序号读取字段值，处理投影位置映射。
   *
   * <p>逻辑：若存在 {@code fromProjectionPos}，先将外部序号映射到完整字段序号， 再按位置返回
   * containsNull/containsNaN/lowerBound/upperBound。
   *
   * @param i 字段序号
   * @return 字段值
   */
  @Override
  public Object get(int i) {
    int pos = i;
    // if the schema was projected, map the incoming ordinal to the expected one
    if (fromProjectionPos != null) {
      pos = fromProjectionPos[i];
    }
    switch (pos) {
      case 0:
        return containsNull;
      case 1:
        return containsNaN;
      case 2:
        return lowerBound();
      case 3:
        return upperBound();
      default:
        throw new UnsupportedOperationException("Unknown field ordinal: " + pos);
    }
  }

  /**
   * 按序号设置字段值，处理投影位置映射；未知序号忽略以兼容新版本字段。
   *
   * @param i 字段序号
   * @param value 字段值
   */
  @Override
  @SuppressWarnings("unchecked")
  public <T> void set(int i, T value) {
    int pos = i;
    // if the schema was projected, map the incoming ordinal to the expected one
    if (fromProjectionPos != null) {
      pos = fromProjectionPos[i];
    }
    switch (pos) {
      case 0:
        this.containsNull = (Boolean) value;
        return;
      case 1:
        this.containsNaN = (Boolean) value;
        return;
      case 2:
        this.lowerBound = ByteBuffers.toByteArray((ByteBuffer) value);
        return;
      case 3:
        this.upperBound = ByteBuffers.toByteArray((ByteBuffer) value);
        return;
      default:
        // ignore the object, it must be from a newer version of the format
    }
  }

  /** 按序号写入字段值，委托给 {@link #set(int, Object)}。 */
  @Override
  public void put(int i, Object v) {
    set(i, v);
  }

  /** 深拷贝当前实例（上下界字节数组一并复制）。 */
  @Override
  public PartitionFieldSummary copy() {
    return new GenericPartitionFieldSummary(this);
  }

  /** @return Avro schema（{@link IndexedRecord} 接口要求） */
  @Override
  public Schema getSchema() {
    return avroSchema;
  }

  /** 返回包含 containsNull/containsNaN/lowerBound/upperBound 的字符串表示。 */
  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("contains_null", containsNull)
        .add("contains_nan", containsNaN)
        .add("lower_bound", lowerBound)
        .add("upper_bound", upperBound)
        .toString();
  }
}
