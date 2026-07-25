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
 * Iceberg 内置函数在 Spark 中的实现，注册为 Spark SQL 函数。
 *
 * <p>所属模块：iceberg-spark v3.3。 类型：类 SparkFunctions。
 *
 * <p>上下游：由 SparkCatalog 注册为函数，被 Spark SQL 表达式调用。
 */
public class SparkFunctions {

  /** 构造 SparkFunctions 实例。 */
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

  private static final List<String> FUNCTION_NAMES = ImmutableList.copyOf(FUNCTIONS.keySet());

  // Functions that are added to all Iceberg catalogs should be accessed with the `system`
  // namespace. They can also be accessed with no namespace at all if qualified with the
  // catalog name, e.g. my_hadoop_catalog.iceberg_version().
  // As namespace resolution is handled by those rules in BaseCatalog, a list of names
  // alone is returned.
  /** 执行该方法的具体逻辑。 */
  public static List<String> list() {
    return FUNCTION_NAMES;
  }

  /** 执行该方法的具体逻辑。 */
  public static UnboundFunction load(String name) {
    // function resolution is case-insensitive to match the existing Spark behavior for functions
    return FUNCTIONS.get(name.toLowerCase(Locale.ROOT));
  }
}
