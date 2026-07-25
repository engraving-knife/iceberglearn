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
import org.apache.iceberg.relocated.com.google.common.base.Joiner;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;

/**
 * 外部 schema 名称到 Iceberg 字段 ID 的映射集合。
 *
 * <p>所属模块：iceberg-core（mapping 子包），是名称映射体系的顶层入口。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>持有一棵 {@link MappedFields} 映射树，表达字段名与 ID 的对应关系。
 *   <li>提供按 ID 或按名称路径查找 {@link MappedField} 的能力。
 *   <li>懒加载并缓存按 ID、按名称索引的两个映射表，加速重复查找。
 * </ul>
 *
 * <p>设计意图：{@code fieldsById}/{@code fieldsByName} 标记为 transient，序列化时不保存，
 * 反序列化后按需重建，避免缓存与源数据不一致；查找时将多级名称以点号拼接为键，支持嵌套路径定位。
 *
 * <p>上下游关系：由 {@link NameMappingParser} 从 JSON 解析生成，被读写流程用于在外部 schema 名称与 Iceberg 内部字段 ID 之间转换。
 */
public class NameMapping implements Serializable {
  private static final Joiner DOT = Joiner.on('.');

  /**
   * 由可变参数 {@link MappedField} 创建名称映射。
   *
   * @param fields 映射字段
   * @return 名称映射实例
   */
  public static NameMapping of(MappedField... fields) {
    return new NameMapping(MappedFields.of(ImmutableList.copyOf(fields)));
  }

  /**
   * 由字段列表创建名称映射。
   *
   * @param fields 映射字段列表
   * @return 名称映射实例
   */
  public static NameMapping of(List<MappedField> fields) {
    return new NameMapping(MappedFields.of(fields));
  }

  /**
   * 由 {@link MappedFields} 创建名称映射。
   *
   * @param fields 映射字段集
   * @return 名称映射实例
   */
  public static NameMapping of(MappedFields fields) {
    return new NameMapping(fields);
  }

  private final MappedFields mapping;
  private transient Map<Integer, MappedField> fieldsById;
  private transient Map<String, MappedField> fieldsByName;

  /**
   * 包级构造器，初始化映射树并预建 ID/名称索引。
   *
   * @param mapping 映射字段集
   */
  NameMapping(MappedFields mapping) {
    this.mapping = mapping;
    lazyFieldsById();
    lazyFieldsByName();
  }

  /**
   * 按字段 ID 查找映射字段。
   *
   * @param id 字段 ID
   * @return 对应的映射字段，不存在返回 null
   */
  public MappedField find(int id) {
    return lazyFieldsById().get(id);
  }

  /**
   * 按名称路径（可变参数）查找映射字段，各级以点号拼接为键。
   *
   * @param names 名称路径
   * @return 对应的映射字段，不存在返回 null
   */
  public MappedField find(String... names) {
    return lazyFieldsByName().get(DOT.join(names));
  }

  /**
   * 按名称路径（列表）查找映射字段，各级以点号拼接为键。
   *
   * @param names 名称路径列表
   * @return 对应的映射字段，不存在返回 null
   */
  public MappedField find(List<String> names) {
    return lazyFieldsByName().get(DOT.join(names));
  }

  /** 返回 底层映射字段集。 */
  public MappedFields asMappedFields() {
    return mapping;
  }

  /**
   * 懒加载按 ID 索引的映射表，通过 {@link MappingUtil#indexById} 构建。
   *
   * @return ID 到映射字段的映射
   */
  private Map<Integer, MappedField> lazyFieldsById() {
    if (fieldsById == null) {
      this.fieldsById = MappingUtil.indexById(mapping);
    }
    return fieldsById;
  }

  /**
   * 懒加载按名称索引的映射表，通过 {@link MappingUtil#indexByName} 构建。
   *
   * @return 名称到映射字段的映射
   */
  private Map<String, MappedField> lazyFieldsByName() {
    if (fieldsByName == null) {
      this.fieldsByName = MappingUtil.indexByName(mapping);
    }
    return fieldsByName;
  }

  /** 返回多行格式的映射字段字符串表示。 */
  @Override
  public String toString() {
    if (mapping.fields().isEmpty()) {
      return "[]";
    } else {
      return "[\n  " + Joiner.on("\n  ").join(mapping.fields()) + "\n]";
    }
  }
}
