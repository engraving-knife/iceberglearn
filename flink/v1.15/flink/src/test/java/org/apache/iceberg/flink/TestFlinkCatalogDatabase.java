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

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.flink.table.catalog.exceptions.DatabaseNotEmptyException;
import org.apache.flink.types.Row;
import org.apache.iceberg.Schema;
import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.types.Types;
import org.assertj.core.api.Assertions;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

/**
 * 文件级说明：测试 TestFlinkCatalogDatabase 的功能。
 *
 * <p>所属模块：iceberg-flink（flink v1.15）。职责：验证 TestFlinkCatalogDatabase 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 Flink TableEnvironment + JUnit，通过构造测试数据、执行 SQL/Table API 操作、 断言结果来覆盖正常路径与边界情况。
 */
public class TestFlinkCatalogDatabase extends FlinkCatalogTestBase {

  /** 辅助方法：TestFlinkCatalogDatabase，Flink Catalog Database。 */
  public TestFlinkCatalogDatabase(String catalogName, Namespace baseNamespace) {
    super(catalogName, baseNamespace);
  }

  /** 辅助方法：clean，clean。 */
  @After
  @Override
  public void clean() {
    sql("DROP TABLE IF EXISTS %s.tl", flinkDatabase);
    sql("DROP DATABASE IF EXISTS %s", flinkDatabase);
    super.clean();
  }

  /**
   * 测试场景：Create Namespace。
   *
   * <p>验证该方法在 Create Namespace 条件下的行为是否符合预期。
   */
  @Test
  public void testCreateNamespace() {
    Assert.assertFalse(
        "Database should not already exist",
        validationNamespaceCatalog.namespaceExists(icebergNamespace));

    sql("CREATE DATABASE %s", flinkDatabase);

    Assert.assertTrue(
        "Database should exist", validationNamespaceCatalog.namespaceExists(icebergNamespace));

    sql("CREATE DATABASE IF NOT EXISTS %s", flinkDatabase);
    Assert.assertTrue(
        "Database should still exist",
        validationNamespaceCatalog.namespaceExists(icebergNamespace));

    sql("DROP DATABASE IF EXISTS %s", flinkDatabase);
    Assert.assertFalse(
        "Database should be dropped", validationNamespaceCatalog.namespaceExists(icebergNamespace));

    sql("CREATE DATABASE IF NOT EXISTS %s", flinkDatabase);
    Assert.assertTrue(
        "Database should be created", validationNamespaceCatalog.namespaceExists(icebergNamespace));
  }

  /**
   * 测试场景：Drop Empty Database。
   *
   * <p>验证该方法在 Drop Empty Database 条件下的行为是否符合预期。
   */
  @Test
  public void testDropEmptyDatabase() {
    Assert.assertFalse(
        "Namespace should not already exist",
        validationNamespaceCatalog.namespaceExists(icebergNamespace));

    sql("CREATE DATABASE %s", flinkDatabase);

    Assert.assertTrue(
        "Namespace should exist", validationNamespaceCatalog.namespaceExists(icebergNamespace));

    sql("DROP DATABASE %s", flinkDatabase);

    Assert.assertFalse(
        "Namespace should have been dropped",
        validationNamespaceCatalog.namespaceExists(icebergNamespace));
  }

  /**
   * 测试场景：Drop Non Empty Namespace。
   *
   * <p>验证该方法在 Drop Non Empty Namespace 条件下的行为是否符合预期。
   */
  @Test
  public void testDropNonEmptyNamespace() {
    Assume.assumeFalse(
        "Hadoop catalog throws IOException: Directory is not empty.", isHadoopCatalog);

    Assert.assertFalse(
        "Namespace should not already exist",
        validationNamespaceCatalog.namespaceExists(icebergNamespace));

    sql("CREATE DATABASE %s", flinkDatabase);

    validationCatalog.createTable(
        TableIdentifier.of(icebergNamespace, "tl"),
        new Schema(Types.NestedField.optional(0, "id", Types.LongType.get())));

    Assert.assertTrue(
        "Namespace should exist", validationNamespaceCatalog.namespaceExists(icebergNamespace));
    Assert.assertTrue(
        "Table should exist",
        validationCatalog.tableExists(TableIdentifier.of(icebergNamespace, "tl")));

    Assertions.assertThatThrownBy(() -> sql("DROP DATABASE %s", flinkDatabase))
        .cause()
        .isInstanceOf(DatabaseNotEmptyException.class)
        .hasMessage(
            String.format("Database %s in catalog %s is not empty.", DATABASE, catalogName));

    sql("DROP TABLE %s.tl", flinkDatabase);
  }

  /**
   * 测试场景：List Tables。
   *
   * <p>验证该方法在 List Tables 条件下的行为是否符合预期。
   */
  @Test
  public void testListTables() {
    Assert.assertFalse(
        "Namespace should not already exist",
        validationNamespaceCatalog.namespaceExists(icebergNamespace));

    sql("CREATE DATABASE %s", flinkDatabase);
    sql("USE CATALOG %s", catalogName);
    sql("USE %s", DATABASE);

    Assert.assertTrue(
        "Namespace should exist", validationNamespaceCatalog.namespaceExists(icebergNamespace));

    Assert.assertEquals("Should not list any tables", 0, sql("SHOW TABLES").size());

    validationCatalog.createTable(
        TableIdentifier.of(icebergNamespace, "tl"),
        new Schema(Types.NestedField.optional(0, "id", Types.LongType.get())));

    List<Row> tables = sql("SHOW TABLES");
    Assert.assertEquals("Only 1 table", 1, tables.size());
    Assert.assertEquals("Table name should match", "tl", tables.get(0).getField(0));
  }

  /**
   * 测试场景：List Namespace。
   *
   * <p>验证该方法在 List Namespace 条件下的行为是否符合预期。
   */
  @Test
  public void testListNamespace() {
    Assert.assertFalse(
        "Namespace should not already exist",
        validationNamespaceCatalog.namespaceExists(icebergNamespace));

    sql("CREATE DATABASE %s", flinkDatabase);
    sql("USE CATALOG %s", catalogName);

    Assert.assertTrue(
        "Namespace should exist", validationNamespaceCatalog.namespaceExists(icebergNamespace));

    List<Row> databases = sql("SHOW DATABASES");

    if (isHadoopCatalog) {
      Assert.assertEquals("Should have 1 database", 1, databases.size());
      Assert.assertEquals("Should have db database", "db", databases.get(0).getField(0));

      if (!baseNamespace.isEmpty()) {
        // test namespace not belongs to this catalog
        validationNamespaceCatalog.createNamespace(
            Namespace.of(baseNamespace.level(0), "UNKNOWN_NAMESPACE"));
        databases = sql("SHOW DATABASES");
        Assert.assertEquals("Should have 1 database", 1, databases.size());
        Assert.assertEquals(
            "Should have db and default database", "db", databases.get(0).getField(0));
      }
    } else {
      // If there are multiple classes extends FlinkTestBase, TestHiveMetastore may loose the
      // creation for default
      // database. See HiveMetaStore.HMSHandler.init.
      Assert.assertTrue(
          "Should have db database",
          databases.stream().anyMatch(d -> Objects.equals(d.getField(0), "db")));
    }
  }

  /**
   * 测试场景：Create Namespace With Metadata。
   *
   * <p>验证该方法在 Create Namespace With Metadata 条件下的行为是否符合预期。
   */
  @Test
  public void testCreateNamespaceWithMetadata() {
    Assume.assumeFalse("HadoopCatalog does not support namespace metadata", isHadoopCatalog);

    Assert.assertFalse(
        "Namespace should not already exist",
        validationNamespaceCatalog.namespaceExists(icebergNamespace));

    sql("CREATE DATABASE %s WITH ('prop'='value')", flinkDatabase);

    Assert.assertTrue(
        "Namespace should exist", validationNamespaceCatalog.namespaceExists(icebergNamespace));

    Map<String, String> nsMetadata =
        validationNamespaceCatalog.loadNamespaceMetadata(icebergNamespace);

    Assert.assertEquals(
        "Namespace should have expected prop value", "value", nsMetadata.get("prop"));
  }

  /**
   * 测试场景：Create Namespace With Comment。
   *
   * <p>验证该方法在 Create Namespace With Comment 条件下的行为是否符合预期。
   */
  @Test
  public void testCreateNamespaceWithComment() {
    Assume.assumeFalse("HadoopCatalog does not support namespace metadata", isHadoopCatalog);

    Assert.assertFalse(
        "Namespace should not already exist",
        validationNamespaceCatalog.namespaceExists(icebergNamespace));

    sql("CREATE DATABASE %s COMMENT 'namespace doc'", flinkDatabase);

    Assert.assertTrue(
        "Namespace should exist", validationNamespaceCatalog.namespaceExists(icebergNamespace));

    Map<String, String> nsMetadata =
        validationNamespaceCatalog.loadNamespaceMetadata(icebergNamespace);

    Assert.assertEquals(
        "Namespace should have expected comment", "namespace doc", nsMetadata.get("comment"));
  }

  /**
   * 测试场景：Create Namespace With Location。
   *
   * <p>验证该方法在 Create Namespace With Location 条件下的行为是否符合预期。
   */
  @Test
  public void testCreateNamespaceWithLocation() throws Exception {
    Assume.assumeFalse("HadoopCatalog does not support namespace metadata", isHadoopCatalog);

    Assert.assertFalse(
        "Namespace should not already exist",
        validationNamespaceCatalog.namespaceExists(icebergNamespace));

    File location = TEMPORARY_FOLDER.newFile();
    Assert.assertTrue(location.delete());

    sql("CREATE DATABASE %s WITH ('location'='%s')", flinkDatabase, location);

    Assert.assertTrue(
        "Namespace should exist", validationNamespaceCatalog.namespaceExists(icebergNamespace));

    Map<String, String> nsMetadata =
        validationNamespaceCatalog.loadNamespaceMetadata(icebergNamespace);

    Assert.assertEquals(
        "Namespace should have expected location",
        "file:" + location.getPath(),
        nsMetadata.get("location"));
  }

  /**
   * 测试场景：Set Properties。
   *
   * <p>验证该方法在 Set Properties 条件下的行为是否符合预期。
   */
  @Test
  public void testSetProperties() {
    Assume.assumeFalse("HadoopCatalog does not support namespace metadata", isHadoopCatalog);

    Assert.assertFalse(
        "Namespace should not already exist",
        validationNamespaceCatalog.namespaceExists(icebergNamespace));

    sql("CREATE DATABASE %s", flinkDatabase);

    Assert.assertTrue(
        "Namespace should exist", validationNamespaceCatalog.namespaceExists(icebergNamespace));

    Map<String, String> defaultMetadata =
        validationNamespaceCatalog.loadNamespaceMetadata(icebergNamespace);
    Assert.assertFalse(
        "Default metadata should not have custom property", defaultMetadata.containsKey("prop"));

    sql("ALTER DATABASE %s SET ('prop'='value')", flinkDatabase);

    Map<String, String> nsMetadata =
        validationNamespaceCatalog.loadNamespaceMetadata(icebergNamespace);

    Assert.assertEquals(
        "Namespace should have expected prop value", "value", nsMetadata.get("prop"));
  }

  /**
   * 测试场景：Hadoop Not Support Meta。
   *
   * <p>验证该方法在 Hadoop Not Support Meta 条件下的行为是否符合预期。
   */
  @Test
  public void testHadoopNotSupportMeta() {
    Assume.assumeTrue("HadoopCatalog does not support namespace metadata", isHadoopCatalog);

    Assert.assertFalse(
        "Namespace should not already exist",
        validationNamespaceCatalog.namespaceExists(icebergNamespace));

    Assertions.assertThatThrownBy(
            () -> sql("CREATE DATABASE %s WITH ('prop'='value')", flinkDatabase))
        .cause()
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessage(
            String.format(
                "Cannot create namespace %s: metadata is not supported", icebergNamespace));
  }
}
