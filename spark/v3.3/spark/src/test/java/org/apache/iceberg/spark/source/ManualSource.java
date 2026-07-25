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
package org.apache.iceberg.spark.source;

import java.util.Map;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.spark.sql.connector.catalog.Table;
import org.apache.spark.sql.connector.catalog.TableProvider;
import org.apache.spark.sql.connector.expressions.Transform;
import org.apache.spark.sql.sources.DataSourceRegister;
import org.apache.spark.sql.types.StructType;
import org.apache.spark.sql.util.CaseInsensitiveStringMap;

/**
 * 文件级说明：测试 ManualSource 相关功能。
 *
 * <p>所属模块：iceberg-spark（spark v3.3）。职责：验证 Iceberg 表在 Spark 引擎下 manual源 相关行为，覆盖正常路径与边界场景。
 *
 * <p>测试策略：基于 SparkSession + JUnit，通过构造测试数据、执行 SQL/DataFrame 操作并断言结果， 覆盖正常路径与边界情况。
 */
public class ManualSource implements TableProvider, DataSourceRegister {
  public static final String SHORT_NAME = "manual_source";
  public static final String TABLE_NAME = "TABLE_NAME";
  private static final Map<String, Table> tableMap = Maps.newHashMap();

  /** 集合表。 */
  public static void setTable(String name, Table table) {
    Preconditions.checkArgument(
        !tableMap.containsKey(name), "Cannot set " + name + ". It is already set");
    tableMap.put(name, table);
  }

  /** clear表。 */
  public static void clearTables() {
    tableMap.clear();
  }

  /** 辅助方法：shortName。 */
  @Override
  public String shortName() {
    return SHORT_NAME;
  }

  /** infer模式。 */
  @Override
  public StructType inferSchema(CaseInsensitiveStringMap options) {
    return getTable(null, null, options).schema();
  }

  /** 辅助方法：inferPartitioning。 */
  @Override
  public Transform[] inferPartitioning(CaseInsensitiveStringMap options) {
    return getTable(null, null, options).partitioning();
  }

  /** 获取表。 */
  @Override
  public org.apache.spark.sql.connector.catalog.Table getTable(
      StructType schema, Transform[] partitioning, Map<String, String> properties) {
    Preconditions.checkArgument(
        properties.containsKey(TABLE_NAME), "Missing property " + TABLE_NAME);
    String tableName = properties.get(TABLE_NAME);
    Preconditions.checkArgument(tableMap.containsKey(tableName), "Table missing " + tableName);
    return tableMap.get(tableName);
  }

  /** supportsexternal元数据。 */
  @Override
  public boolean supportsExternalMetadata() {
    return false;
  }
}
