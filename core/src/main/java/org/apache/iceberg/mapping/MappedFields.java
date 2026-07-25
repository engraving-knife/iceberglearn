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
package org.apache.iceberg.mapping;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.iceberg.relocated.com.google.common.base.Joiner;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;

/**
 * 文件级说明：字段名→字段 ID 的映射对象，用于在缺乏字段 ID 的文件上恢复 ID 信息。
 *
 * <p>所属模块：iceberg-core（mapping 子包）。职责：持有字段名到 {@link MappedField} 的映射表，提供按名查找、按 ID 查找、层级遍历等能力。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>NameMapping 的核心数据结构：把字段名路径映射到字段 ID，使无 ID 的文件 （如非 Iceberg 写入的 Avro/Parquet）也能参与 schema 演化。
 *   <li>实现 Serializable，支持序列化缓存。
 *   <li>不可变设计，通过 Builder 构造。
 * </ul>
 *
 * <p>上下游关系：被 {@link MappingUtil} 创建；被 {@link PruneColumns}、{@link AvroSchemaUtil} 等在处理无 ID schema
 * 时使用。
 */
public class MappedFields implements Serializable {

  /**
   * 工厂方法：从字段数组创建 MappedFields。
   *
   * @param fields 映射字段数组
   * @return MappedFields 实例
   */
  public static MappedFields of(MappedField... fields) {
    return new MappedFields(ImmutableList.copyOf(fields));
  }

  public static MappedFields of(List<MappedField> fields) {
    return new MappedFields(fields);
  }

  private final List<MappedField> fields;
  private transient Map<String, Integer> nameToId;
  private transient Map<Integer, MappedField> idToField;

  private MappedFields(List<MappedField> fields) {
    this.fields = ImmutableList.copyOf(fields);
    lazyNameToId();
    lazyIdToField();
  }

  public MappedField field(int id) {
    return lazyIdToField().get(id);
  }

  /**
   * 按字段名查找字段 ID。
   *
   * @param name 字段名
   * @return 字段 ID，未找到返回 null
   */
  public Integer id(String name) {
    return lazyNameToId().get(name);
  }

  /**
   * 返回映射中的字段数量。
   *
   * @return 字段数量
   */
  public int size() {
    return fields.size();
  }

  private static Map<String, Integer> indexIds(List<MappedField> fields) {
    ImmutableMap.Builder<String, Integer> builder = ImmutableMap.builder();
    fields.forEach(
        field ->
            field
                .names()
                .forEach(
                    name -> {
                      Integer id = field.id();
                      if (id != null) {
                        builder.put(name, id);
                      }
                    }));
    return builder.build();
  }

  private static Map<Integer, MappedField> indexFields(List<MappedField> fields) {
    ImmutableMap.Builder<Integer, MappedField> builder = ImmutableMap.builder();
    fields.forEach(
        field -> {
          Integer id = field.id();
          if (id != null) {
            builder.put(id, field);
          }
        });
    return builder.build();
  }

  /**
   * 返回所有映射字段列表。
   *
   * @return 映射字段列表（不可变）
   */
  public List<MappedField> fields() {
    return fields;
  }

  private Map<String, Integer> lazyNameToId() {
    if (nameToId == null) {
      this.nameToId = indexIds(fields);
    }
    return nameToId;
  }

  private Map<Integer, MappedField> lazyIdToField() {
    if (idToField == null) {
      this.idToField = indexFields(fields);
    }
    return idToField;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    } else if (!(other instanceof MappedFields)) {
      return false;
    }

    return fields.equals(((MappedFields) other).fields);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(fields);
  }

  @Override
  public String toString() {
    return "[ " + Joiner.on(", ").join(fields) + " ]";
  }
}
