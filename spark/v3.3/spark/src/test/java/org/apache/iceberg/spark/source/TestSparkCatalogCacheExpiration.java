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
import org.apache.iceberg.CachingCatalog;
import org.apache.iceberg.CatalogProperties;
import org.apache.iceberg.catalog.Catalog;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.spark.SparkCatalog;
import org.apache.iceberg.spark.SparkSessionCatalog;
import org.apache.iceberg.spark.SparkTestBaseWithCatalog;
import org.apache.spark.sql.connector.catalog.TableCatalog;
import org.assertj.core.api.Assertions;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * 文件级说明：测试 TestSparkCatalogCacheExpiration 相关功能。
 *
 * <p>所属模块：iceberg-spark（spark v3.3）。职责：验证 Iceberg 表在 Spark 引擎下 Spark目录cacheexpiration
 * 相关行为，覆盖正常路径与边界场景。
 *
 * <p>测试策略：基于 SparkSession + JUnit，通过构造测试数据、执行 SQL/DataFrame 操作并断言结果， 覆盖正常路径与边界情况。
 */
public class TestSparkCatalogCacheExpiration extends SparkTestBaseWithCatalog {

  private static final String sessionCatalogName = "spark_catalog";
  private static final String sessionCatalogImpl = SparkSessionCatalog.class.getName();
  private static final Map<String, String> sessionCatalogConfig =
      ImmutableMap.of(
          "type",
          "hadoop",
          "default-namespace",
          "default",
          CatalogProperties.CACHE_ENABLED,
          "true",
          CatalogProperties.CACHE_EXPIRATION_INTERVAL_MS,
          "3000");

  /** 作为SQL配置目录key用于。 */
  private static String asSqlConfCatalogKeyFor(String catalog, String configKey) {
    // configKey is empty when the catalog's class is being defined
    if (configKey.isEmpty()) {
      return String.format("spark.sql.catalog.%s", catalog);
    } else {
      return String.format("spark.sql.catalog.%s.%s", catalog, configKey);
    }
  }

  // Add more catalogs to the spark session, so we only need to start spark one time for multiple
  // different catalog configuration tests.
  @BeforeClass
  public static void beforeClass() {
    // Catalog - expiration_disabled: Catalog with caching on and expiration disabled.
    ImmutableMap.of(
            "",
            "org.apache.iceberg.spark.SparkCatalog",
            "type",
            "hive",
            CatalogProperties.CACHE_ENABLED,
            "true",
            CatalogProperties.CACHE_EXPIRATION_INTERVAL_MS,
            "-1")
        .forEach((k, v) -> spark.conf().set(asSqlConfCatalogKeyFor("expiration_disabled", k), v));

    // Catalog - cache_disabled_implicitly: Catalog that does not cache, as the cache expiration
    // interval is 0.
    ImmutableMap.of(
            "",
            "org.apache.iceberg.spark.SparkCatalog",
            "type",
            "hive",
            CatalogProperties.CACHE_ENABLED,
            "true",
            CatalogProperties.CACHE_EXPIRATION_INTERVAL_MS,
            "0")
        .forEach(
            (k, v) -> spark.conf().set(asSqlConfCatalogKeyFor("cache_disabled_implicitly", k), v));
  }

  /** 测试Spark目录cacheexpiration。 */
  public TestSparkCatalogCacheExpiration() {
    super(sessionCatalogName, sessionCatalogImpl, sessionCatalogConfig);
  }

  /** 测试Spark会话目录带expirationenabled场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testSparkSessionCatalogWithExpirationEnabled() {
    SparkSessionCatalog<?> sparkCatalog = sparkSessionCatalog();
    Assertions.assertThat(sparkCatalog)
        .extracting("icebergCatalog")
        .extracting("cacheEnabled")
        .isEqualTo(true);

    Assertions.assertThat(sparkCatalog)
        .extracting("icebergCatalog")
        .extracting("icebergCatalog")
        .isInstanceOfSatisfying(
            Catalog.class,
            icebergCatalog -> {
              Assertions.assertThat(icebergCatalog)
                  .isExactlyInstanceOf(CachingCatalog.class)
                  .extracting("expirationIntervalMillis")
                  .isEqualTo(3000L);
            });
  }

  /** 测试cacheenabled与expirationdisabled场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testCacheEnabledAndExpirationDisabled() {
    SparkCatalog sparkCatalog = getSparkCatalog("expiration_disabled");
    Assertions.assertThat(sparkCatalog).extracting("cacheEnabled").isEqualTo(true);

    Assertions.assertThat(sparkCatalog)
        .extracting("icebergCatalog")
        .isInstanceOfSatisfying(
            CachingCatalog.class,
            icebergCatalog -> {
              Assertions.assertThat(icebergCatalog)
                  .extracting("expirationIntervalMillis")
                  .isEqualTo(-1L);
            });
  }

  /** 测试 testCacheDisabledImplicitly 场景：验证 CacheDisabledImplicitly 相关操作的行为与结果。 */
  @Test
  public void testCacheDisabledImplicitly() {
    SparkCatalog sparkCatalog = getSparkCatalog("cache_disabled_implicitly");
    Assertions.assertThat(sparkCatalog).extracting("cacheEnabled").isEqualTo(false);

    Assertions.assertThat(sparkCatalog)
        .extracting("icebergCatalog")
        .isInstanceOfSatisfying(
            Catalog.class,
            icebergCatalog ->
                Assertions.assertThat(icebergCatalog).isNotInstanceOf(CachingCatalog.class));
  }

  /** Spark会话目录。 */
  private SparkSessionCatalog<?> sparkSessionCatalog() {
    TableCatalog catalog =
        (TableCatalog) spark.sessionState().catalogManager().catalog("spark_catalog");
    return (SparkSessionCatalog<?>) catalog;
  }

  /** 获取Spark目录。 */
  private SparkCatalog getSparkCatalog(String catalog) {
    return (SparkCatalog) spark.sessionState().catalogManager().catalog(catalog);
  }
}
