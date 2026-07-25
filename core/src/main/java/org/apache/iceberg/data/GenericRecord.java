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
package org.apache.iceberg.data;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.apache.iceberg.Schema;
import org.apache.iceberg.StructLike;
import org.apache.iceberg.relocated.com.google.common.base.Objects;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.types.Types;
import org.apache.iceberg.types.Types.StructType;

/**
 * 通用记录实现：同时实现 {@link Record} 与 {@link StructLike}，是 iceberg-core data 包的默认行数据载体。
 *
 * <p>所属模块：iceberg-core，data 包内的通用数据记录实现。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>以 Object 数组存储字段值，按位置或字段名读写。
 *   <li>缓存结构类型的字段名到位置的映射（NAME_MAP_CACHE），加速按名访问。
 *   <li>支持拷贝（copy）与部分覆盖拷贝（copy with overwrite）。
 * </ul>
 *
 * <p>设计意图：作为 Iceberg 内部测试与通用场景的默认 Record 实现，同时满足 StructLike 接口 以便直接用于列式读写。字段名到位置的映射使用 Caffeine
 * 缓存（weakKeys）以避免对同一 StructType 重复构建。equals/hashCode 基于 values 数组的深比较与哈希。
 *
 * <p>上下游关系：被 {@code GenericReaders}、{@code GenericWriters} 等读写器使用； 被 core 的扫描读取、测试等场景作为默认行对象。
 */
public class GenericRecord implements Record, StructLike {
  private static final LoadingCache<StructType, Map<String, Integer>> NAME_MAP_CACHE =
      Caffeine.newBuilder()
          .weakKeys()
          .build(
              struct -> {
                Map<String, Integer> idToPos = Maps.newHashMap();
                List<Types.NestedField> fields = struct.fields();
                for (int i = 0; i < fields.size(); i += 1) {
                  idToPos.put(fields.get(i).name(), i);
                }
                return idToPos;
              });

  /**
   * 按 Iceberg schema 创建通用记录。
   *
   * @param schema Iceberg schema
   * @return 新的 GenericRecord 实例
   */
  public static GenericRecord create(Schema schema) {
    return new GenericRecord(schema.asStruct());
  }

  /**
   * 按结构类型创建通用记录。
   *
   * @param struct 结构类型
   * @return 新的 GenericRecord 实例
   */
  public static GenericRecord create(StructType struct) {
    return new GenericRecord(struct);
  }

  private final StructType struct;
  private final int size;
  private final Object[] values;
  private final Map<String, Integer> nameToPos;

  private GenericRecord(StructType struct) {
    this.struct = struct;
    this.size = struct.fields().size();
    this.values = new Object[size];
    this.nameToPos = NAME_MAP_CACHE.get(struct);
  }

  private GenericRecord(GenericRecord toCopy) {
    this.struct = toCopy.struct;
    this.size = toCopy.size;
    this.values = Arrays.copyOf(toCopy.values, toCopy.values.length);
    this.nameToPos = toCopy.nameToPos;
  }

  private GenericRecord(GenericRecord toCopy, Map<String, Object> overwrite) {
    this.struct = toCopy.struct;
    this.size = toCopy.size;
    this.values = Arrays.copyOf(toCopy.values, toCopy.values.length);
    this.nameToPos = toCopy.nameToPos;
    for (Map.Entry<String, Object> entry : overwrite.entrySet()) {
      setField(entry.getKey(), entry.getValue());
    }
  }

  @Override
  public StructType struct() {
    return struct;
  }

  /**
   * 按字段名读取值。
   *
   * @param name 字段名
   * @return 字段值，若字段不存在返回 null
   */
  @Override
  public Object getField(String name) {
    Integer pos = nameToPos.get(name);
    if (pos != null) {
      return values[pos];
    }

    return null;
  }

  /**
   * 按字段名设置值。
   *
   * @param name 字段名
   * @param value 字段值
   * @throws IllegalArgumentException 若字段名不存在
   */
  @Override
  public void setField(String name, Object value) {
    Integer pos = nameToPos.get(name);
    Preconditions.checkArgument(pos != null, "Cannot set unknown field named: %s", name);
    values[pos] = value;
  }

  @Override
  public int size() {
    return size;
  }

  @Override
  public Object get(int pos) {
    return values[pos];
  }

  /**
   * 按位置读取值并校验类型。
   *
   * @param pos 字段位置
   * @param javaClass 期望的 Java 类型
   * @param <T> 值的 Java 类型
   * @return 字段值
   * @throws IllegalStateException 若值不是期望类型的实例
   */
  @Override
  public <T> T get(int pos, Class<T> javaClass) {
    Object value = get(pos);
    if (value == null || javaClass.isInstance(value)) {
      return javaClass.cast(value);
    } else {
      throw new IllegalStateException("Not an instance of " + javaClass.getName() + ": " + value);
    }
  }

  @Override
  public <T> void set(int pos, T value) {
    values[pos] = value;
  }

  /**
   * 深拷贝当前记录（复制 values 数组）。
   *
   * @return 拷贝后的 GenericRecord
   */
  @Override
  public GenericRecord copy() {
    return new GenericRecord(this);
  }

  /**
   * 深拷贝当前记录并覆盖指定字段值。
   *
   * @param overwriteValues 需覆盖的字段名到值的映射
   * @return 拷贝并覆盖后的 GenericRecord
   */
  @Override
  public GenericRecord copy(Map<String, Object> overwriteValues) {
    return new GenericRecord(this, overwriteValues);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("Record(");
    for (int i = 0; i < values.length; i += 1) {
      if (i != 0) {
        sb.append(", ");
      }
      sb.append(values[i]);
    }
    sb.append(")");
    return sb.toString();
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    } else if (!(other instanceof GenericRecord)) {
      return false;
    }

    GenericRecord that = (GenericRecord) other;
    return Arrays.deepEquals(this.values, that.values);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(values);
  }
}
