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
package org.apache.iceberg.util;

import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import java.io.Serializable;
import org.apache.avro.Schema;
import org.apache.avro.generic.IndexedRecord;
import org.apache.avro.reflect.ReflectData;
import org.apache.avro.specific.SpecificData;
import org.apache.iceberg.relocated.com.google.common.base.Objects;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;

/**
 * 二元组工具类，表示一个有序的键值对 (first, second)，同时实现 Avro {@link IndexedRecord} 以便 在 Avro 序列化场景中复用。
 *
 * <p>所属模块：iceberg-core。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>提供不可变二元组的创建与访问（{@link #of(Object, Object)}、{@link #first()}、{@link #second()}）。
 *   <li>实现 Avro {@link IndexedRecord} 与 {@link SpecificData.SchemaConstructable}，使 Pair 可作为 Avro
 *       记录读写，schema 按两个元素的运行时类型动态生成并缓存。
 *   <li>实现 {@link Serializable}，支持在分布式引擎中序列化传输。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>Schema 缓存：使用 Caffeine {@link LoadingCache} 按 (firstClass, secondClass) 缓存 Avro Schema，
 *       避免每次序列化都反射生成，降低开销。
 *   <li>Avro 兼容：通过 {@link #put(int, Object)} 与 {@link #get(int)} 暴露索引访问，供 Avro 反序列化 回填字段；私有构造器
 *       {@code Pair(Schema)} 专供 Avro 反射创建使用。
 * </ul>
 *
 * <p>上下游关系：被 core 内多处用作轻量返回值容器（如分区 spec ID 与 partition tuple 的配对）， 也被
 * ManifestFileUtil、PartitionUtil 等使用；依赖 Avro 与 relocated guava。
 */
public class Pair<X, Y> implements IndexedRecord, SpecificData.SchemaConstructable, Serializable {
  /**
   * 创建一个包含两个元素的 Pair。
   *
   * @param <X> 第一个元素类型
   * @param <Y> 第二个元素类型
   * @param first 第一个元素
   * @param second 第二个元素
   * @return 新建的 Pair 实例
   */
  public static <X, Y> Pair<X, Y> of(X first, Y second) {
    return new Pair<>(first, second);
  }

  private static final LoadingCache<Pair<Class<?>, Class<?>>, Schema> SCHEMA_CACHE =
      Caffeine.newBuilder()
          .build(
              new CacheLoader<Pair<Class<?>, Class<?>>, Schema>() {
                @Override
                @SuppressWarnings("deprecation")
                public Schema load(Pair<Class<?>, Class<?>> key) {
                  Schema xSchema = ReflectData.get().getSchema(key.first);
                  Schema ySchema = ReflectData.get().getSchema(key.second);
                  return Schema.createRecord(
                      "pair",
                      null,
                      null,
                      false,
                      Lists.newArrayList(
                          new Schema.Field("x", xSchema, null, (Object) null),
                          new Schema.Field("y", ySchema, null, (Object) null)));
                }
              });

  private Schema schema = null;
  private X first;
  private Y second;

  /** Avro 反射创建 Pair 时使用的构造器，仅设置 schema 不设值。 */
  private Pair(Schema schema) {
    this.schema = schema;
  }

  private Pair(X first, Y second) {
    this.first = first;
    this.second = second;
  }

  /**
   * Avro 索引写接口：按下标设置元素（0=first，1=second），供 Avro 反序列化回填。
   *
   * @param i 字段下标，必须为 0 或 1
   * @param v 待写入的值
   * @throws IllegalArgumentException 若下标非 0/1
   */
  @Override
  @SuppressWarnings("unchecked")
  public void put(int i, Object v) {
    if (i == 0) {
      this.first = (X) v;
      return;
    } else if (i == 1) {
      this.second = (Y) v;
      return;
    }
    throw new IllegalArgumentException("Cannot set value " + i + " (not 0 or 1): " + v);
  }

  /**
   * Avro 索引读接口：按下标获取元素（0=first，1=second）。
   *
   * @param i 字段下标，必须为 0 或 1
   * @return 对应位置的元素
   * @throws IllegalArgumentException 若下标非 0/1
   */
  @Override
  public Object get(int i) {
    if (i == 0) {
      return first;
    } else if (i == 1) {
      return second;
    }
    throw new IllegalArgumentException("Cannot get value " + i + " (not 0 or 1)");
  }

  /**
   * 返回本 Pair 的 Avro Schema；首次调用时按 first/second 的运行时类型生成并缓存。
   *
   * @return Avro Schema
   */
  @Override
  public Schema getSchema() {
    if (schema == null) {
      this.schema = SCHEMA_CACHE.get(Pair.of(first.getClass(), second.getClass()));
    }
    return schema;
  }

  /** 返回 二元组的第一个元素。 */
  public X first() {
    return first;
  }

  /** 返回 二元组的第二个元素。 */
  public Y second() {
    return second;
  }

  @Override
  public String toString() {
    return "(" + String.valueOf(first) + ", " + String.valueOf(second) + ")";
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(first, second);
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    } else if (!(other instanceof Pair)) {
      return false;
    }
    Pair<?, ?> otherPair = (Pair<?, ?>) other;
    return Objects.equal(first, otherPair.first) && Objects.equal(second, otherPair.second);
  }
}
