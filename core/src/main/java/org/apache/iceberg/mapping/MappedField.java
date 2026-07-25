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
import java.util.Objects;
import java.util.Set;
import org.apache.iceberg.relocated.com.google.common.base.Joiner;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableSet;

/**
 * 字段 ID 与一组外部名称之间的不可变映射。
 *
 * <p>所属模块：iceberg-core（mapping 子包），是 {@link NameMapping} 的基本构成单元。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>保存一个字段 ID 与其对应的一个或多个外部名称（别名）。
 *   <li>可选地持有嵌套 {@link MappedFields}，以表达结构体/嵌套字段的映射。
 * </ul>
 *
 * <p>设计意图：不可变对象，名称集合存为 {@link ImmutableSet}；通过静态工厂方法创建实例， 支持按单名/多名/带嵌套映射等组合构造，便于序列化与缓存。
 *
 * <p>上下游关系：由 {@link NameMapping} 聚合，被 {@link NameMappingParser} 序列化/反序列化， 供读写流程在外部名称与 Iceberg 字段 ID
 * 间转换。
 */
public class MappedField implements Serializable {

  /**
   * 创建单名映射字段，无嵌套映射。
   *
   * @param id 字段 ID
   * @param name 字段名称
   * @return 映射字段实例
   */
  public static MappedField of(Integer id, String name) {
    return new MappedField(id, ImmutableSet.of(name), null);
  }

  /**
   * 创建多名映射字段，无嵌套映射。
   *
   * @param id 字段 ID
   * @param names 字段名称集合（别名）
   * @return 映射字段实例
   */
  public static MappedField of(Integer id, Iterable<String> names) {
    return new MappedField(id, names, null);
  }

  /**
   * 创建单名映射字段并带嵌套映射。
   *
   * @param id 字段 ID
   * @param name 字段名称
   * @param nestedMapping 嵌套字段映射
   * @return 映射字段实例
   */
  public static MappedField of(Integer id, String name, MappedFields nestedMapping) {
    return new MappedField(id, ImmutableSet.of(name), nestedMapping);
  }

  /**
   * 创建多名映射字段并带嵌套映射。
   *
   * @param id 字段 ID
   * @param names 字段名称集合（别名）
   * @param nestedMapping 嵌套字段映射
   * @return 映射字段实例
   */
  public static MappedField of(Integer id, Iterable<String> names, MappedFields nestedMapping) {
    return new MappedField(id, names, nestedMapping);
  }

  private final Set<String> names;
  private Integer id;
  private MappedFields nestedMapping;

  /**
   * 私有构造器，将名称集合拷贝为不可变集合。
   *
   * @param id 字段 ID
   * @param names 字段名称集合
   * @param nested 嵌套字段映射，可为 null
   */
  private MappedField(Integer id, Iterable<String> names, MappedFields nested) {
    this.id = id;
    this.names = ImmutableSet.copyOf(names);
    this.nestedMapping = nested;
  }

  /** 返回 字段 ID。 */
  public Integer id() {
    return id;
  }

  /** 返回 字段名称集合（不可变）。 */
  public Set<String> names() {
    return names;
  }

  /** 返回 嵌套字段映射，无嵌套时为 null。 */
  public MappedFields nestedMapping() {
    return nestedMapping;
  }

  /** 按名称集合、ID、嵌套映射判断相等。 */
  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    } else if (!(other instanceof MappedField)) {
      return false;
    }

    MappedField that = (MappedField) other;
    return names.equals(that.names)
        && Objects.equals(id, that.id)
        && Objects.equals(nestedMapping, that.nestedMapping);
  }

  /** 按名称集合、ID、嵌套映射计算哈希。 */
  @Override
  public int hashCode() {
    return Objects.hash(names, id, nestedMapping);
  }

  /** 返回形如 ([name1, name2] -> id[, nestedMapping]) 的字符串表示。 */
  @Override
  public String toString() {
    return "(["
        + Joiner.on(", ").join(names)
        + "] -> "
        + (id != null ? id : "?")
        + (nestedMapping != null ? ", " + nestedMapping + ")" : ")");
  }
}
