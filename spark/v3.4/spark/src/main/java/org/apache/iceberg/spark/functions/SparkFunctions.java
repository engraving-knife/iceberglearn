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
 * 所属模块：iceberg-spark v3.4
 *
 * <p>职责：Iceberg Spark 函数注册表，按名提供各内置标量函数的未绑定实例。
 *
 * <p>设计意图：以映射表集中管理函数名到函数实例，供函数目录按需查找。
 *
 * <p>上下游关系：由 SparkFunctionCatalog 调用。
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
  public static List<String> list() {
    return FUNCTION_NAMES;
  }
  /** 执行 load 相关操作。 */
  public static UnboundFunction load(String name) {
    // function resolution is case-insensitive to match the existing Spark behavior for functions
    return FUNCTIONS.get(name.toLowerCase(Locale.ROOT));
  }
  /** 执行 loadFunctionByClass 相关操作。 */
  public static UnboundFunction loadFunctionByClass(Class<?> functionClass) {
    Class<?> declaringClass = functionClass.getDeclaringClass();
    if (declaringClass == null) {
      return null;
    }

    return CLASS_TO_FUNCTIONS.get(declaringClass);
  }
}
