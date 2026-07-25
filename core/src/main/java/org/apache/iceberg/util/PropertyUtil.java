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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;

/**
 * 属性工具类，提供从 {@link Map} 中按 key 读取并转换为 Boolean/Double/Int/Long/String 等类型的便捷方法， 以及按前缀/谓词过滤属性、应用
 * schema 变更等操作。
 *
 * <p>所属模块：iceberg-core。
 *
 * <p>职责：封装 Iceberg 各模块频繁需要的"从 String 属性 Map 中安全取值并类型转换"逻辑， 统一默认值处理与 null 语义。
 *
 * <p>设计意图：Iceberg 的表/catalog 属性全部以 String 存储，读取时需要类型转换和默认值兜底。 本类把这些重复模式集中为静态方法，Nullable 版本与带默认值版本区分
 * null 语义。
 *
 * <p>上下游关系：被 core 内几乎所有读取属性的类使用（如 LockManagers、Catalog 初始化、 表属性解析等）；仅依赖 relocated guava。
 */
public class PropertyUtil {

  private PropertyUtil() {}

  /**
   * 读取 Boolean 属性，缺失时返回默认值。
   *
   * @param properties 属性映射
   * @param property 属性键
   * @param defaultValue 缺失时的默认值
   * @return 属性对应的布尔值
   */
  public static boolean propertyAsBoolean(
      Map<String, String> properties, String property, boolean defaultValue) {
    String value = properties.get(property);
    if (value != null) {
      return Boolean.parseBoolean(value);
    }
    return defaultValue;
  }

  /**
   * 读取 Boolean 属性，缺失时返回 null（区别于默认值版本）。
   *
   * @param properties 属性映射
   * @param property 属性键
   * @return 属性对应的布尔值，缺失返回 null
   */
  public static Boolean propertyAsNullableBoolean(Map<String, String> properties, String property) {
    String value = properties.get(property);
    if (value != null) {
      return Boolean.parseBoolean(value);
    }
    return null;
  }

  /**
   * 读取 double 属性，缺失时返回默认值。
   *
   * @param properties 属性映射
   * @param property 属性键
   * @param defaultValue 缺失时的默认值
   * @return 属性对应的 double 值
   */
  public static double propertyAsDouble(
      Map<String, String> properties, String property, double defaultValue) {
    String value = properties.get(property);
    if (value != null) {
      return Double.parseDouble(value);
    }
    return defaultValue;
  }

  /**
   * 读取 int 属性，缺失时返回默认值。
   *
   * @param properties 属性映射
   * @param property 属性键
   * @param defaultValue 缺失时的默认值
   * @return 属性对应的 int 值
   */
  public static int propertyAsInt(
      Map<String, String> properties, String property, int defaultValue) {
    String value = properties.get(property);
    if (value != null) {
      return Integer.parseInt(value);
    }
    return defaultValue;
  }

  /**
   * 读取 int 属性，缺失时返回 null。
   *
   * @param properties 属性映射
   * @param property 属性键
   * @return 属性对应的 Integer 值，缺失返回 null
   */
  public static Integer propertyAsNullableInt(Map<String, String> properties, String property) {
    String value = properties.get(property);
    if (value != null) {
      return Integer.parseInt(value);
    }
    return null;
  }

  /**
   * 读取 long 属性，缺失时返回默认值。
   *
   * @param properties 属性映射
   * @param property 属性键
   * @param defaultValue 缺失时的默认值
   * @return 属性对应的 long 值
   */
  public static long propertyAsLong(
      Map<String, String> properties, String property, long defaultValue) {
    String value = properties.get(property);
    if (value != null) {
      return Long.parseLong(value);
    }
    return defaultValue;
  }

  /**
   * 读取 long 属性，缺失时返回 null。
   *
   * @param properties 属性映射
   * @param property 属性键
   * @return 属性对应的 Long 值，缺失返回 null
   */
  public static Long propertyAsNullableLong(Map<String, String> properties, String property) {
    String value = properties.get(property);
    if (value != null) {
      return Long.parseLong(value);
    }
    return null;
  }

  /**
   * 读取 String 属性，缺失时返回默认值。
   *
   * @param properties 属性映射
   * @param property 属性键
   * @param defaultValue 缺失时的默认值
   * @return 属性对应的字符串值
   */
  public static String propertyAsString(
      Map<String, String> properties, String property, String defaultValue) {
    String value = properties.get(property);
    if (value != null) {
      return value;
    }
    return defaultValue;
  }

  /**
   * 返回键以指定前缀开头的子集映射，并从结果键中去除前缀。
   *
   * <p>匹配区分大小写。
   *
   * @param properties 输入映射
   * @param prefix 前缀，不可为 null
   * @return 键以 prefix 开头且已去除前缀的子集映射
   */
  public static Map<String, String> propertiesWithPrefix(
      Map<String, String> properties, String prefix) {
    if (properties == null || properties.isEmpty()) {
      return Collections.emptyMap();
    }

    Preconditions.checkArgument(prefix != null, "Invalid prefix: null");

    return properties.entrySet().stream()
        .filter(e -> e.getKey().startsWith(prefix))
        .collect(Collectors.toMap(e -> e.getKey().replaceFirst(prefix, ""), Map.Entry::getValue));
  }

  /**
   * 按键谓词过滤属性映射。
   *
   * @param properties 输入映射
   * @param keyPredicate 键过滤谓词，不可为 null
   * @return 键满足谓词的子集映射
   */
  public static Map<String, String> filterProperties(
      Map<String, String> properties, Predicate<String> keyPredicate) {
    if (properties == null || properties.isEmpty()) {
      return Collections.emptyMap();
    }

    Preconditions.checkArgument(keyPredicate != null, "Invalid key pattern: null");

    return properties.entrySet().stream()
        .filter(e -> keyPredicate.test(e.getKey()))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  /**
   * 在 schema 变更（列重命名/删除）后更新表属性中与列相关的键。
   *
   * <p>逻辑：遍历属性键，对以指定列属性前缀开头的键，提取列别名；若列已重命名则更新键中的列名， 若列已删除则丢弃该属性，否则原样保留。非列属性原样拷贝。
   *
   * @param properties 原始属性映射
   * @param deletedColumns 已删除列名列表
   * @param renamedColumns 列名映射（旧名 -> 新名）
   * @param columnProperties 列属性前缀集合
   * @return 更新后的属性映射
   */
  public static Map<String, String> applySchemaChanges(
      Map<String, String> properties,
      List<String> deletedColumns,
      Map<String, String> renamedColumns,
      Set<String> columnProperties) {
    if (properties.keySet().stream()
        .noneMatch(key -> columnProperties.stream().anyMatch(key::startsWith))) {
      return properties;
    } else {
      Map<String, String> updatedProperties = Maps.newHashMap();
      properties
          .keySet()
          .forEach(
              key -> {
                String prefix =
                    columnProperties.stream().filter(key::startsWith).findFirst().orElse(null);

                if (prefix != null) {
                  String columnAlias = key.replaceFirst(prefix, "");
                  if (renamedColumns.get(columnAlias) != null) {
                    // The name has changed.
                    String newKey = prefix + renamedColumns.get(columnAlias);
                    updatedProperties.put(newKey, properties.get(key));
                  } else if (!deletedColumns.contains(columnAlias)) {
                    // Copy over the original.
                    updatedProperties.put(key, properties.get(key));
                  }
                  // Implicit drop if deleted.
                } else {
                  updatedProperties.put(key, properties.get(key));
                }
              });

      return updatedProperties;
    }
  }
}
