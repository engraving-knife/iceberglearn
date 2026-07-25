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
package org.apache.iceberg.spark.functions;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.spark.sql.connector.catalog.functions.UnboundFunction;

/**
 * Iceberg Spark 内置函数注册表。
 *
 * <p>所属模块：iceberg-spark（Spark v3.5 集成模块），functions 子包。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>维护 Iceberg 暴露给 Spark SQL 的函数映射（iceberg_version、years、months、days、hours、bucket、truncate）。
 *   <li>提供按名称（大小写不敏感）加载未绑定函数 {@link UnboundFunction} 的能力。
 *   <li>提供按函数实现类反查函数（用于 Spark codegen 场景）。
 * </ul>
 *
 * <p>设计意图：用不可变 Map 静态注册所有函数，保证线程安全；函数名小写化以匹配 Spark 大小写不敏感行为； 同时维护 Class -> Function 映射以支持 codegen
 * 时通过声明类反查。函数通过 system 命名空间访问， 命名空间解析由 BaseCatalog 处理，故 list 只返回名称列表。
 *
 * <p>上下游关系：被 Iceberg Spark catalog 调用以加载函数；上游是各 *Function 实现，下游是 Spark SQL 调用方。
 */
public class SparkFunctions {

  private SparkFunctions() {}

  private static final Map<String, UnboundFunction> FUNCTIONS =
      ImmutableMap.of(
          "iceberg_version", new IcebergVersionFunction(),
          "years", new YearsFunction(),
          "months", new MonthsFunction(),
          "days", new DaysFunction(),
          "hours", new HoursFunction(),
          "bucket", new BucketFunction(),
          "truncate", new TruncateFunction());

  private static final Map<Class<?>, UnboundFunction> CLASS_TO_FUNCTIONS =
      ImmutableMap.of(
          YearsFunction.class, new YearsFunction(),
          MonthsFunction.class, new MonthsFunction(),
          DaysFunction.class, new DaysFunction(),
          HoursFunction.class, new HoursFunction(),
          BucketFunction.class, new BucketFunction(),
          TruncateFunction.class, new TruncateFunction());

  private static final List<String> FUNCTION_NAMES = ImmutableList.copyOf(FUNCTIONS.keySet());

  // Functions that are added to all Iceberg catalogs should be accessed with the `system`
  // namespace. They can also be accessed with no namespace at all if qualified with the
  // catalog name, e.g. my_hadoop_catalog.iceberg_version().
  // As namespace resolution is handled by those rules in BaseCatalog, a list of names
  // alone is returned.
  /** 返回所有已注册函数名列表（不可变）。 */
  public static List<String> list() {
    return FUNCTION_NAMES;
  }

  /** 按名称加载函数（大小写不敏感），未注册返回 null。 */
  public static UnboundFunction load(String name) {
    // function resolution is case-insensitive to match the existing Spark behavior for functions
    return FUNCTIONS.get(name.toLowerCase(Locale.ROOT));
  }

  /**
   * 按函数实现类反查未绑定函数。
   *
   * <p>逻辑：取 functionClass 的声明类（外部类），在 CLASS_TO_FUNCTIONS 中查找。 用于 Spark codegen 场景通过内部实现类反查到外层
   * UnboundFunction。
   *
   * @param functionClass 函数实现类（通常是 BoundFunction 内部类）
   * @return 对应的 UnboundFunction，无声明类时返回 null
   */
  public static UnboundFunction loadFunctionByClass(Class<?> functionClass) {
    Class<?> declaringClass = functionClass.getDeclaringClass();
    if (declaringClass == null) {
      return null;
    }

    return CLASS_TO_FUNCTIONS.get(declaringClass);
  }
}
