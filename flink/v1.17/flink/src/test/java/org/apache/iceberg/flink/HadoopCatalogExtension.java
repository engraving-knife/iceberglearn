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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.apache.commons.io.FileUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.iceberg.CatalogProperties;
import org.apache.iceberg.catalog.Catalog;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.hadoop.HadoopCatalog;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * 文件级说明：测试 HadoopCatalogExtension 的功能。
 *
 * <p>所属模块：iceberg-flink（flink v1.17）。职责：验证 HadoopCatalogExtension 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 Flink TableEnvironment + JUnit，通过构造测试数据、执行 SQL/Table API 操作、 断言结果来覆盖正常路径与边界情况。
 */
public class HadoopCatalogExtension
    implements BeforeAllCallback, BeforeEachCallback, AfterAllCallback, AfterEachCallback {
  protected final String database;
  protected final String tableName;

  protected Path temporaryFolder;
  protected Catalog catalog;
  protected CatalogLoader catalogLoader;
  protected String warehouse;
  protected TableLoader tableLoader;

  /** 辅助方法：HadoopCatalogExtension，Hadoop Catalog Extension。 */
  public HadoopCatalogExtension(String database, String tableName) {
    this.database = database;
    this.tableName = tableName;
  }

  /** 辅助方法：beforeAll，before All。 */
  @Override
  public void beforeAll(ExtensionContext context) throws Exception {
    this.temporaryFolder = Files.createTempDirectory("junit5_hadoop_catalog-");
  }

  /** 辅助方法：afterAll，after All。 */
  @Override
  public void afterAll(ExtensionContext context) throws Exception {
    FileUtils.deleteDirectory(temporaryFolder.toFile());
  }

  /** 辅助方法：beforeEach，before Each。 */
  @Override
  public void beforeEach(ExtensionContext context) throws Exception {
    Assertions.assertThat(temporaryFolder).exists().isDirectory();
    this.warehouse = "file:" + temporaryFolder + "/" + UUID.randomUUID();
    this.catalogLoader =
        CatalogLoader.hadoop(
            "hadoop",
            new Configuration(),
            ImmutableMap.of(CatalogProperties.WAREHOUSE_LOCATION, warehouse));
    this.catalog = catalogLoader.loadCatalog();
    this.tableLoader =
        TableLoader.fromCatalog(catalogLoader, TableIdentifier.of(database, tableName));
  }

  /** 辅助方法：afterEach，after Each。 */
  @Override
  public void afterEach(ExtensionContext context) throws Exception {
    try {
      catalog.dropTable(TableIdentifier.of(database, tableName));
      ((HadoopCatalog) catalog).close();
      tableLoader.close();
    } catch (Exception e) {
      throw new RuntimeException("Failed to close catalog resource");
    }
  }

  /** 辅助方法：tableLoader，table Loader。 */
  public TableLoader tableLoader() {
    return tableLoader;
  }

  /** 辅助方法：catalog，catalog。 */
  public Catalog catalog() {
    return catalog;
  }

  /** 辅助方法：catalogLoader，catalog Loader。 */
  public CatalogLoader catalogLoader() {
    return catalogLoader;
  }

  /** 辅助方法：warehouse，warehouse。 */
  public String warehouse() {
    return warehouse;
  }
}
