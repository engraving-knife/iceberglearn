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
package org.apache.iceberg.hive;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.iceberg.CatalogProperties;
import org.apache.iceberg.CatalogUtil;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 文件级说明：测试 TestLoadHiveCatalog 的功能。
 *
 * <p>所属模块：iceberg-hive-metastore。职责：验证 TestLoadHiveCatalog 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 JUnit 框架，通过构造输入、调用方法、断言结果来覆盖功能点。
 */
public class TestLoadHiveCatalog {

  private static TestHiveMetastore metastore;

  /** 辅助方法：startMetastore。 */
  @BeforeAll
  public static void startMetastore() throws Exception {
    HiveConf hiveConf = new HiveConf(TestLoadHiveCatalog.class);
    metastore = new TestHiveMetastore();
    metastore.start(hiveConf);
  }

  /** 辅助方法：stopMetastore。 */
  @AfterAll
  public static void stopMetastore() throws Exception {
    if (metastore != null) {
      metastore.stop();
      metastore = null;
    }
  }

  /**
   * 测试场景：Custom Cache Keys。
   *
   * <p>验证该方法在 Custom Cache Keys 条件下的行为是否符合预期。
   */
  @Test
  public void testCustomCacheKeys() throws Exception {
    HiveCatalog hiveCatalog1 =
        (HiveCatalog)
            CatalogUtil.loadCatalog(
                HiveCatalog.class.getName(),
                CatalogUtil.ICEBERG_CATALOG_TYPE_HIVE,
                Collections.emptyMap(),
                metastore.hiveConf());
    HiveCatalog hiveCatalog2 =
        (HiveCatalog)
            CatalogUtil.loadCatalog(
                HiveCatalog.class.getName(),
                CatalogUtil.ICEBERG_CATALOG_TYPE_HIVE,
                Collections.emptyMap(),
                metastore.hiveConf());

    CachedClientPool clientPool1 = (CachedClientPool) hiveCatalog1.clientPool();
    CachedClientPool clientPool2 = (CachedClientPool) hiveCatalog2.clientPool();
    assertThat(clientPool2.clientPool()).isSameAs(clientPool1.clientPool());

    Configuration conf1 = new Configuration(metastore.hiveConf());
    Configuration conf2 = new Configuration(metastore.hiveConf());
    conf1.set("any.key", "any.value");
    conf2.set("any.key", "any.value");
    hiveCatalog1 =
        (HiveCatalog)
            CatalogUtil.loadCatalog(
                HiveCatalog.class.getName(),
                CatalogUtil.ICEBERG_CATALOG_TYPE_HIVE,
                ImmutableMap.of(CatalogProperties.CLIENT_POOL_CACHE_KEYS, "conf:any.key"),
                conf1);
    hiveCatalog2 =
        (HiveCatalog)
            CatalogUtil.loadCatalog(
                HiveCatalog.class.getName(),
                CatalogUtil.ICEBERG_CATALOG_TYPE_HIVE,
                ImmutableMap.of(CatalogProperties.CLIENT_POOL_CACHE_KEYS, "conf:any.key"),
                conf2);
    clientPool1 = (CachedClientPool) hiveCatalog1.clientPool();
    clientPool2 = (CachedClientPool) hiveCatalog2.clientPool();
    assertThat(clientPool2.clientPool()).isSameAs(clientPool1.clientPool());

    conf2.set("any.key", "any.value2");
    hiveCatalog2 =
        (HiveCatalog)
            CatalogUtil.loadCatalog(
                HiveCatalog.class.getName(),
                CatalogUtil.ICEBERG_CATALOG_TYPE_HIVE,
                ImmutableMap.of(CatalogProperties.CLIENT_POOL_CACHE_KEYS, "conf:any.key"),
                conf2);
    clientPool2 = (CachedClientPool) hiveCatalog2.clientPool();
    assertThat(clientPool2.clientPool()).isNotSameAs(clientPool1.clientPool());
  }
}
