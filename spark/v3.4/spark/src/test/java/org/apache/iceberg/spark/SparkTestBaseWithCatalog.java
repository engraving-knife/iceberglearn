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
package org.apache.iceberg.spark;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.iceberg.CatalogProperties;
import org.apache.iceberg.PlanningMode;
import org.apache.iceberg.TableProperties;
import org.apache.iceberg.catalog.Catalog;
import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.catalog.SupportsNamespaces;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.hadoop.HadoopCatalog;
import org.apache.iceberg.util.PropertyUtil;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

/**
 * 文件级说明：测试 SparkTestBaseWithCatalog 相关功能。
 *
 * <p>所属模块：iceberg-spark（spark v3.4）。职责：验证 Iceberg 表在 Spark 引擎下 Spark测试基类带目录 相关行为，覆盖正常路径与边界场景。
 *
 * <p>测试策略：基于 SparkSession + JUnit，通过构造测试数据、执行 SQL/DataFrame 操作并断言结果， 覆盖正常路径与边界情况。
 */
public abstract class SparkTestBaseWithCatalog extends SparkTestBase {
  protected static File warehouse = null;

  /** 创建warehouse。 */
  @BeforeClass
  public static void createWarehouse() throws IOException {
    SparkTestBaseWithCatalog.warehouse = File.createTempFile("warehouse", null);
    Assert.assertTrue(warehouse.delete());
  }

  /** 删除warehouse。 */
  @AfterClass
  public static void dropWarehouse() throws IOException {
    if (warehouse != null && warehouse.exists()) {
      Path warehousePath = new Path(warehouse.getAbsolutePath());
      FileSystem fs = warehousePath.getFileSystem(hiveConf);
      Assert.assertTrue("Failed to delete " + warehousePath, fs.delete(warehousePath, true));
    }
  }

  @Rule public TemporaryFolder temp = new TemporaryFolder();

  protected final String catalogName;
  protected final Map<String, String> catalogConfig;
  protected final Catalog validationCatalog;
  protected final SupportsNamespaces validationNamespaceCatalog;
  protected final TableIdentifier tableIdent = TableIdentifier.of(Namespace.of("default"), "table");
  protected final String tableName;

  /** Spark测试基类带目录。 */
  public SparkTestBaseWithCatalog() {
    this(SparkCatalogConfig.HADOOP);
  }

  /** Spark测试基类带目录。 */
  public SparkTestBaseWithCatalog(SparkCatalogConfig config) {
    this(config.catalogName(), config.implementation(), config.properties());
  }

  /** Spark测试基类带目录。 */
  public SparkTestBaseWithCatalog(
      String catalogName, String implementation, Map<String, String> config) {
    this.catalogName = catalogName;
    this.catalogConfig = config;
    this.validationCatalog =
        catalogName.equals("testhadoop")
            ? new HadoopCatalog(spark.sessionState().newHadoopConf(), "file:" + warehouse)
            : catalog;
    this.validationNamespaceCatalog = (SupportsNamespaces) validationCatalog;

    spark.conf().set("spark.sql.catalog." + catalogName, implementation);
    config.forEach(
        (key, value) -> spark.conf().set("spark.sql.catalog." + catalogName + "." + key, value));

    if (config.get("type").equalsIgnoreCase("hadoop")) {
      spark.conf().set("spark.sql.catalog." + catalogName + ".warehouse", "file:" + warehouse);
    }

    this.tableName =
        (catalogName.equals("spark_catalog") ? "" : catalogName + ".") + "default.table";

    sql("CREATE NAMESPACE IF NOT EXISTS default");
  }

  /** 表name。 */
  protected String tableName(String name) {
    return (catalogName.equals("spark_catalog") ? "" : catalogName + ".") + "default." + name;
  }

  /** 提交target。 */
  protected String commitTarget() {
    return tableName;
  }

  /** 辅助方法：selectTarget。 */
  protected String selectTarget() {
    return tableName;
  }

  /** caching目录enabled。 */
  protected boolean cachingCatalogEnabled() {
    return PropertyUtil.propertyAsBoolean(
        catalogConfig, CatalogProperties.CACHE_ENABLED, CatalogProperties.CACHE_ENABLED_DEFAULT);
  }

  /** configure规划模式。 */
  protected void configurePlanningMode(PlanningMode planningMode) {
    configurePlanningMode(tableName, planningMode);
  }

  /** configure规划模式。 */
  protected void configurePlanningMode(String table, PlanningMode planningMode) {
    sql(
        "ALTER TABLE %s SET TBLPROPERTIES ('%s' '%s', '%s' '%s')",
        table,
        TableProperties.DATA_PLANNING_MODE,
        planningMode.modeName(),
        TableProperties.DELETE_PLANNING_MODE,
        planningMode.modeName());
  }
}
