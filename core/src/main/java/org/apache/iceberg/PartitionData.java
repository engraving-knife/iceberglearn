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
import java.util.Objects;
import java.util.stream.Stream;
import org.apache.avro.Schema;
import org.apache.avro.generic.IndexedRecord;
import org.apache.avro.specific.SpecificData;
import org.apache.avro.util.Utf8;
import org.apache.iceberg.avro.AvroSchemaUtil;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.hash.Hasher;
import org.apache.iceberg.relocated.com.google.common.hash.Hashing;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;

/**
 * 分区值数据结构：以紧凑 Object 数组承载一个分区元组的所有字段值。
 *
 * <p>所属模块：iceberg-core（核心实现层），是 Iceberg 分区值的核心承载类。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>实现 {@link StructLike} 与 Avro {@link IndexedRecord}，可被 manifest 读写器按位置读写。
 *   <li>实现 {@link SpecificData.SchemaConstructable}，支持 Avro 反射按 schema 构造实例。
 *   <li>提供 {@link #copy()} 深拷贝、{@link #clear()} 清空、{@link #copyData} 工具方法。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>分区字段只能是基本类型，因此用 Object[] 足够；Utf8/ByteBuffer 在 set 时转为可序列化形式。
 *   <li>schema 懒加载：反序列化后按需从 stringSchema 重建 Avro Schema。
 *   <li>equals/hashCode 同时考虑 partitionType 与 data，保证不同 spec 的相同值不冲突。
 * </ul>
 *
 * <p>上下游关系：被 {@link BaseFile}、{@link GenericManifestFile} 等承载分区值； 被 manifest 读写器按 Avro 反射使用。
 */
public class PartitionData
    implements IndexedRecord, StructLike, SpecificData.SchemaConstructable, Serializable {

  /** 工具方法：把 Iceberg 分区 struct 类型转换为 PartitionData 类对应的 Avro schema。 */
  static Schema partitionDataSchema(Types.StructType partitionType) {
    return AvroSchemaUtil.convert(partitionType, PartitionData.class.getName());
  }

  private final Types.StructType partitionType;
  private final int size;
  private final Object[] data;
  private final String stringSchema;
  private transient Schema schema;

  /**
   * Avro 反射读取 manifest 时使用：基于 Avro schema 构造实例。
   *
   * @param schema Avro schema（对应分区 struct）
   */
  PartitionData(Schema schema) {
    this.partitionType = AvroSchemaUtil.convert(schema).asNestedType().asStructType();
    this.size = partitionType.fields().size();
    this.data = new Object[size];
    this.stringSchema = schema.toString();
    this.schema = schema;
  }

  /**
   * 公共构造方法：按分区 struct 创建空数据数组。
   *
   * <p>逻辑：校验所有字段必须是基本类型（分区不允许嵌套），初始化数据数组与 schema。
   *
   * @param partitionType 分区 struct 类型
   */
  public PartitionData(Types.StructType partitionType) {
    for (Types.NestedField field : partitionType.fields()) {
      Preconditions.checkArgument(
          field.type().isPrimitiveType(),
          "Partitions cannot contain nested types: %s",
          field.type());
    }

    this.partitionType = partitionType;
    this.size = partitionType.fields().size();
    this.data = new Object[size];
    this.schema = partitionDataSchema(partitionType);
    this.stringSchema = schema.toString();
  }

  /** 拷贝构造方法：深拷贝数据（含 byte[]、String）。 */
  private PartitionData(PartitionData toCopy) {
    this.partitionType = toCopy.partitionType;
    this.size = toCopy.size;
    this.data = copyData(toCopy.partitionType, toCopy.data);
    this.stringSchema = toCopy.stringSchema;
    this.schema = toCopy.schema;
  }

  /** 返回分区 struct 类型。 */
  public Types.StructType getPartitionType() {
    return partitionType;
  }

  /** 返回 Avro schema（懒加载：反序列化后从 stringSchema 重建）。 */
  @Override
  public Schema getSchema() {
    if (schema == null) {
      this.schema = new Schema.Parser().parse(stringSchema);
    }
    return schema;
  }

  /** 返回指定位置字段的 Iceberg 类型。 */
  public Type getType(int pos) {
    return partitionType.fields().get(pos).type();
  }

  /** 清空所有字段值为 null。 */
  public void clear() {
    Arrays.fill(data, null);
  }

  /** 返回字段数。 */
  @Override
  public int size() {
    return size;
  }

  /** 按位置与 Java 类型读取字段值，类型不匹配抛异常。 */
  @Override
  public <T> T get(int pos, Class<T> javaClass) {
    Object value = get(pos);
    if (value == null || javaClass.isInstance(value)) {
      return javaClass.cast(value);
    }

    throw new IllegalArgumentException(
        String.format(
            "Wrong class, expected %s, but was %s, for object: %s",
            javaClass.getName(), value.getClass().getName(), value));
  }

  /**
   * 按位置读取字段值。
   *
   * <p>逻辑：超界返回 null；byte[] 包装为 ByteBuffer 返回（便于读取侧统一处理二进制）。
   */
  @Override
  public Object get(int pos) {
    if (pos >= data.length) {
      return null;
    }

    if (data[pos] instanceof byte[]) {
      return ByteBuffer.wrap((byte[]) data[pos]);
    }

    return data[pos];
  }

  /**
   * 按位置写入字段值。
   *
   * <p>逻辑：Utf8 转 String、ByteBuffer 转 byte[] 以保证可序列化；其余直接存。
   */
  @Override
  public <T> void set(int pos, T value) {
    if (value instanceof Utf8) {
      // Utf8 is not Serializable
      data[pos] = value.toString();
    } else if (value instanceof ByteBuffer) {
      // ByteBuffer is not Serializable
      ByteBuffer buffer = (ByteBuffer) value;
      byte[] bytes = new byte[buffer.remaining()];
      buffer.duplicate().get(bytes);
      data[pos] = bytes;
    } else {
      data[pos] = value;
    }
  }

  /** Avro {@link IndexedRecord#put} 实现：委托 {@link #set}。 */
  @Override
  public void put(int i, Object v) {
    set(i, v);
  }

  /** 返回形如 PartitionData{field=value,...} 的字符串。 */
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("PartitionData{");
    for (int i = 0; i < data.length; i += 1) {
      if (i > 0) {
        sb.append(", ");
      }
      sb.append(partitionType.fields().get(i).name()).append("=").append(data[i]);
    }
    sb.append("}");
    return sb.toString();
  }

  /** 深拷贝本 PartitionData。 */
  public PartitionData copy() {
    return new PartitionData(this);
  }

  /** 相等性按 partitionType 与 data 数组同时比较。 */
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    } else if (!(o instanceof PartitionData)) {
      return false;
    }

    PartitionData that = (PartitionData) o;
    return partitionType.equals(that.partitionType) && Arrays.equals(data, that.data);
  }

  /** hashCode 基于 partitionType 与 data 的哈希。 */
  @Override
  public int hashCode() {
    Hasher hasher = Hashing.goodFastHash(32).newHasher();
    Stream.of(data).map(Objects::hashCode).forEach(hasher::putInt);
    partitionType.fields().stream().map(Objects::hashCode).forEach(hasher::putInt);
    return hasher.hash().hashCode();
  }

  /**
   * 工具方法：按字段类型深拷贝分区数据数组。
   *
   * <p>逻辑：null 直接保留；BINARY/FIXED 拷贝 byte[]；STRING 转为 String；其余基本类型直接引用。 嵌套类型抛
   * IllegalArgumentException（分区不支持）。
   *
   * @param type 分区 struct 类型
   * @param data 源数据数组
   * @return 拷贝后的数据数组
   */
  public static Object[] copyData(Types.StructType type, Object[] data) {
    List<Types.NestedField> fields = type.fields();
    Object[] copy = new Object[data.length];
    for (int i = 0; i < data.length; i += 1) {
      if (data[i] == null) {
        copy[i] = null;
      } else {
        Types.NestedField field = fields.get(i);
        switch (field.type().typeId()) {
          case STRUCT:
          case LIST:
          case MAP:
            throw new IllegalArgumentException("Unsupported type in partition data: " + type);
          case BINARY:
          case FIXED:
            byte[] buffer = (byte[]) data[i];
            copy[i] = Arrays.copyOf(buffer, buffer.length);
            break;
          case STRING:
            copy[i] = data[i].toString();
            break;
          default:
            // no need to copy the object
            copy[i] = data[i];
        }
      }
    }

    return copy;
  }
}
