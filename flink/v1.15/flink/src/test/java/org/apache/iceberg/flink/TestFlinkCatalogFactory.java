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
package org.apache.iceberg.flink;

import java.util.Map;
import org.apache.hadoop.conf.Configuration;
import org.apache.iceberg.AssertHelpers;
import org.apache.iceberg.CatalogProperties;
import org.apache.iceberg.catalog.Catalog;
import org.apache.iceberg.hadoop.HadoopCatalog;
import org.apache.iceberg.hive.HiveCatalog;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;

/**
 * 文件级说明：测试 TestFlinkCatalogFactory 的功能。
 *
 * <p>所属模块：iceberg-flink（flink v1.15）。职责：验证 TestFlinkCatalogFactory 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 Flink TableEnvironment + JUnit，通过构造测试数据、执行 SQL/Table API 操作、 断言结果来覆盖正常路径与边界情况。
 */
public class TestFlinkCatalogFactory {

  private Map<String, String> props;

  /** 辅助方法：before，before。 */
  @Before
  public void before() {
    props = Maps.newHashMap();
    props.put("type", "iceberg");
    props.put(CatalogProperties.WAREHOUSE_LOCATION, "/tmp/location");
  }

  /**
   * 测试场景：Create Catalog Hive。
   *
   * <p>验证该方法在 Create Catalog Hive 条件下的行为是否符合预期。
   */
  @Test
  public void testCreateCatalogHive() {
    String catalogName = "hiveCatalog";
    props.put(
        FlinkCatalogFactory.ICEBERG_CATALOG_TYPE, FlinkCatalogFactory.ICEBERG_CATALOG_TYPE_HIVE);

    Catalog catalog =
        FlinkCatalogFactory.createCatalogLoader(catalogName, props, new Configuration())
            .loadCatalog();

    Assertions.assertThat(catalog).isNotNull().isInstanceOf(HiveCatalog.class);
  }

  /**
   * 测试场景：Create Catalog Hadoop。
   *
   * <p>验证该方法在 Create Catalog Hadoop 条件下的行为是否符合预期。
   */
  @Test
  public void testCreateCatalogHadoop() {
    String catalogName = "hadoopCatalog";
    props.put(
        FlinkCatalogFactory.ICEBERG_CATALOG_TYPE, FlinkCatalogFactory.ICEBERG_CATALOG_TYPE_HADOOP);

    Catalog catalog =
        FlinkCatalogFactory.createCatalogLoader(catalogName, props, new Configuration())
            .loadCatalog();

    Assertions.assertThat(catalog).isNotNull().isInstanceOf(HadoopCatalog.class);
  }

  /**
   * 测试场景：Create Catalog Custom。
   *
   * <p>验证该方法在 Create Catalog Custom 条件下的行为是否符合预期。
   */
  @Test
  public void testCreateCatalogCustom() {
    String catalogName = "customCatalog";
    props.put(CatalogProperties.CATALOG_IMPL, CustomHadoopCatalog.class.getName());

    Catalog catalog =
        FlinkCatalogFactory.createCatalogLoader(catalogName, props, new Configuration())
            .loadCatalog();

    Assertions.assertThat(catalog).isNotNull().isInstanceOf(CustomHadoopCatalog.class);
  }

  /**
   * 测试场景：Create Catalog Custom With Hive Catalog Type Set。
   *
   * <p>验证该方法在 Create Catalog Custom With Hive Catalog Type Set 条件下的行为是否符合预期。
   */
  @Test
  public void testCreateCatalogCustomWithHiveCatalogTypeSet() {
    String catalogName = "customCatalog";
    props.put(CatalogProperties.CATALOG_IMPL, CustomHadoopCatalog.class.getName());
    props.put(
        FlinkCatalogFactory.ICEBERG_CATALOG_TYPE, FlinkCatalogFactory.ICEBERG_CATALOG_TYPE_HIVE);

    AssertHelpers.assertThrows(
        "Should throw when both catalog-type and catalog-impl are set",
        IllegalArgumentException.class,
        "both catalog-type and catalog-impl are set",
        () -> FlinkCatalogFactory.createCatalogLoader(catalogName, props, new Configuration()));
  }

  /**
   * 测试场景：Load Catalog Unknown。
   *
   * <p>验证该方法在 Load Catalog Unknown 条件下的行为是否符合预期。
   */
  @Test
  public void testLoadCatalogUnknown() {
    String catalogName = "unknownCatalog";
    props.put(FlinkCatalogFactory.ICEBERG_CATALOG_TYPE, "fooType");

    AssertHelpers.assertThrows(
        "Should throw when an unregistered / unknown catalog is set as the catalog factor's`type` setting",
        UnsupportedOperationException.class,
        "Unknown catalog-type",
        () -> FlinkCatalogFactory.createCatalogLoader(catalogName, props, new Configuration()));
  }

  public static class CustomHadoopCatalog extends HadoopCatalog {

    /** 辅助方法：CustomHadoopCatalog，Custom Hadoop Catalog。 */
    public CustomHadoopCatalog() {}

    /** 辅助方法：CustomHadoopCatalog，Custom Hadoop Catalog。 */
    public CustomHadoopCatalog(Configuration conf, String warehouseLocation) {
      setConf(conf);
      initialize(
          "custom", ImmutableMap.of(CatalogProperties.WAREHOUSE_LOCATION, warehouseLocation));
    }
  }
}
