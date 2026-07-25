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
package org.apache.iceberg.mr;

import static org.apache.iceberg.types.Types.NestedField.required;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Optional;
import java.util.Properties;
import org.apache.hadoop.conf.Configuration;
import org.apache.iceberg.CatalogProperties;
import org.apache.iceberg.CatalogUtil;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.PartitionSpecParser;
import org.apache.iceberg.Schema;
import org.apache.iceberg.SchemaParser;
import org.apache.iceberg.Table;
import org.apache.iceberg.catalog.Catalog;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.exceptions.NoSuchTableException;
import org.apache.iceberg.hadoop.HadoopCatalog;
import org.apache.iceberg.hadoop.HadoopTables;
import org.apache.iceberg.hive.HiveCatalog;
import org.apache.iceberg.types.Types;
import org.assertj.core.api.Assertions;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * 文件级说明：测试 TestCatalogs 的功能。
 *
 * <p>所属模块：iceberg-mr。职责：验证 TestCatalogs 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 JUnit 框架，通过构造输入、调用方法、断言结果来覆盖功能点。
 */
public class TestCatalogs {

  private static final Schema SCHEMA = new Schema(required(1, "foo", Types.StringType.get()));
  private static final PartitionSpec SPEC =
      PartitionSpec.builderFor(SCHEMA).identity("foo").build();

  private Configuration conf;

  @Rule public TemporaryFolder temp = new TemporaryFolder();

  /** 辅助方法：before。 */
  @Before
  public void before() {
    conf = new Configuration();
  }

  /**
   * 测试场景：Load Table From Location。
   *
   * <p>验证该方法在 Load Table From Location 条件下的行为是否符合预期。
   */
  @Test
  public void testLoadTableFromLocation() throws IOException {
    conf.set(CatalogUtil.ICEBERG_CATALOG_TYPE, Catalogs.LOCATION);

    Assertions.assertThatThrownBy(() -> Catalogs.loadTable(conf))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Table location not set");

    HadoopTables tables = new HadoopTables();
    Table hadoopTable = tables.create(SCHEMA, temp.newFolder("hadoop_tables").toString());

    conf.set(InputFormatConfig.TABLE_LOCATION, hadoopTable.location());

    Assert.assertEquals(hadoopTable.location(), Catalogs.loadTable(conf).location());
  }

  /**
   * 测试场景：Load Table From Catalog。
   *
   * <p>验证该方法在 Load Table From Catalog 条件下的行为是否符合预期。
   */
  @Test
  public void testLoadTableFromCatalog() throws IOException {
    String defaultCatalogName = "default";
    String warehouseLocation = temp.newFolder("hadoop", "warehouse").toString();
    setCustomCatalogProperties(defaultCatalogName, warehouseLocation);

    Assertions.assertThatThrownBy(() -> Catalogs.loadTable(conf))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Table identifier not set");

    HadoopCatalog catalog = new CustomHadoopCatalog(conf, warehouseLocation);
    Table hadoopCatalogTable = catalog.createTable(TableIdentifier.of("table"), SCHEMA);

    conf.set(InputFormatConfig.TABLE_IDENTIFIER, "table");

    Assert.assertEquals(hadoopCatalogTable.location(), Catalogs.loadTable(conf).location());
  }

  /**
   * 测试场景：Create Drop Table To Location。
   *
   * <p>验证该方法在 Create Drop Table To Location 条件下的行为是否符合预期。
   */
  @Test
  public void testCreateDropTableToLocation() throws IOException {
    Properties missingSchema = new Properties();
    missingSchema.put("location", temp.newFolder("hadoop_tables").toString());

    Assertions.assertThatThrownBy(() -> Catalogs.createTable(conf, missingSchema))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("Table schema not set");

    conf.set(CatalogUtil.ICEBERG_CATALOG_TYPE, Catalogs.LOCATION);
    Properties missingLocation = new Properties();
    missingLocation.put(InputFormatConfig.TABLE_SCHEMA, SchemaParser.toJson(SCHEMA));

    Assertions.assertThatThrownBy(() -> Catalogs.createTable(conf, missingLocation))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("Table location not set");

    Properties properties = new Properties();
    properties.put("location", temp.getRoot() + "/hadoop_tables");
    properties.put(InputFormatConfig.TABLE_SCHEMA, SchemaParser.toJson(SCHEMA));
    properties.put(InputFormatConfig.PARTITION_SPEC, PartitionSpecParser.toJson(SPEC));
    properties.put("dummy", "test");

    Catalogs.createTable(conf, properties);

    HadoopTables tables = new HadoopTables();
    Table table = tables.load(properties.getProperty("location"));

    Assert.assertEquals(properties.getProperty("location"), table.location());
    Assert.assertEquals(SchemaParser.toJson(SCHEMA), SchemaParser.toJson(table.schema()));
    Assert.assertEquals(PartitionSpecParser.toJson(SPEC), PartitionSpecParser.toJson(table.spec()));
    assertThat(table.properties()).containsEntry("dummy", "test");

    Assertions.assertThatThrownBy(() -> Catalogs.dropTable(conf, new Properties()))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("Table location not set");

    Properties dropProperties = new Properties();
    dropProperties.put("location", temp.getRoot() + "/hadoop_tables");
    Catalogs.dropTable(conf, dropProperties);

    Assertions.assertThatThrownBy(() -> Catalogs.loadTable(conf, dropProperties))
        .isInstanceOf(NoSuchTableException.class)
        .hasMessage("Table does not exist at location: " + properties.getProperty("location"));
  }

  /**
   * 测试场景：Create Drop Table To Catalog。
   *
   * <p>验证该方法在 Create Drop Table To Catalog 条件下的行为是否符合预期。
   */
  @Test
  public void testCreateDropTableToCatalog() throws IOException {
    TableIdentifier identifier = TableIdentifier.of("test", "table");
    String defaultCatalogName = "default";
    String warehouseLocation = temp.newFolder("hadoop", "warehouse").toString();

    setCustomCatalogProperties(defaultCatalogName, warehouseLocation);

    Properties missingSchema = new Properties();
    missingSchema.put("name", identifier.toString());
    missingSchema.put(InputFormatConfig.CATALOG_NAME, defaultCatalogName);

    Assertions.assertThatThrownBy(() -> Catalogs.createTable(conf, missingSchema))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("Table schema not set");

    Properties missingIdentifier = new Properties();
    missingIdentifier.put(InputFormatConfig.TABLE_SCHEMA, SchemaParser.toJson(SCHEMA));
    missingIdentifier.put(InputFormatConfig.CATALOG_NAME, defaultCatalogName);
    Assertions.assertThatThrownBy(() -> Catalogs.createTable(conf, missingIdentifier))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("Table identifier not set");

    Properties properties = new Properties();
    properties.put("name", identifier.toString());
    properties.put(InputFormatConfig.TABLE_SCHEMA, SchemaParser.toJson(SCHEMA));
    properties.put(InputFormatConfig.PARTITION_SPEC, PartitionSpecParser.toJson(SPEC));
    properties.put("dummy", "test");
    properties.put(InputFormatConfig.CATALOG_NAME, defaultCatalogName);

    Catalogs.createTable(conf, properties);

    HadoopCatalog catalog = new CustomHadoopCatalog(conf, warehouseLocation);
    Table table = catalog.loadTable(identifier);

    Assert.assertEquals(SchemaParser.toJson(SCHEMA), SchemaParser.toJson(table.schema()));
    Assert.assertEquals(PartitionSpecParser.toJson(SPEC), PartitionSpecParser.toJson(table.spec()));
    assertThat(table.properties()).containsEntry("dummy", "test");

    Assertions.assertThatThrownBy(() -> Catalogs.dropTable(conf, new Properties()))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("Table identifier not set");

    Properties dropProperties = new Properties();
    dropProperties.put("name", identifier.toString());
    dropProperties.put(InputFormatConfig.CATALOG_NAME, defaultCatalogName);
    Catalogs.dropTable(conf, dropProperties);

    Assertions.assertThatThrownBy(() -> Catalogs.loadTable(conf, dropProperties))
        .isInstanceOf(NoSuchTableException.class)
        .hasMessage("Table does not exist: test.table");
  }

  /**
   * 测试场景：Load Catalog Default。
   *
   * <p>验证该方法在 Load Catalog Default 条件下的行为是否符合预期。
   */
  @Test
  public void testLoadCatalogDefault() {
    String catalogName = "barCatalog";
    Optional<Catalog> defaultCatalog = Catalogs.loadCatalog(conf, catalogName);
    Assert.assertTrue(defaultCatalog.isPresent());
    Assertions.assertThat(defaultCatalog.get()).isInstanceOf(HiveCatalog.class);
    Properties properties = new Properties();
    properties.put(InputFormatConfig.CATALOG_NAME, catalogName);
    Assert.assertTrue(Catalogs.hiveCatalog(conf, properties));
  }

  /**
   * 测试场景：Load Catalog Hive。
   *
   * <p>验证该方法在 Load Catalog Hive 条件下的行为是否符合预期。
   */
  @Test
  public void testLoadCatalogHive() {
    String catalogName = "barCatalog";
    conf.set(
        InputFormatConfig.catalogPropertyConfigKey(catalogName, CatalogUtil.ICEBERG_CATALOG_TYPE),
        CatalogUtil.ICEBERG_CATALOG_TYPE_HIVE);
    Optional<Catalog> hiveCatalog = Catalogs.loadCatalog(conf, catalogName);
    Assert.assertTrue(hiveCatalog.isPresent());
    Assertions.assertThat(hiveCatalog.get()).isInstanceOf(HiveCatalog.class);
    Properties properties = new Properties();
    properties.put(InputFormatConfig.CATALOG_NAME, catalogName);
    Assert.assertTrue(Catalogs.hiveCatalog(conf, properties));
  }

  /**
   * 测试场景：Load Catalog Hadoop。
   *
   * <p>验证该方法在 Load Catalog Hadoop 条件下的行为是否符合预期。
   */
  @Test
  public void testLoadCatalogHadoop() {
    String catalogName = "barCatalog";
    conf.set(
        InputFormatConfig.catalogPropertyConfigKey(catalogName, CatalogUtil.ICEBERG_CATALOG_TYPE),
        CatalogUtil.ICEBERG_CATALOG_TYPE_HADOOP);
    conf.set(
        InputFormatConfig.catalogPropertyConfigKey(
            catalogName, CatalogProperties.WAREHOUSE_LOCATION),
        "/tmp/mylocation");
    Optional<Catalog> hadoopCatalog = Catalogs.loadCatalog(conf, catalogName);
    Assert.assertTrue(hadoopCatalog.isPresent());
    Assertions.assertThat(hadoopCatalog.get()).isInstanceOf(HadoopCatalog.class);
    Assert.assertEquals(
        "HadoopCatalog{name=barCatalog, location=/tmp/mylocation}", hadoopCatalog.get().toString());
    Properties properties = new Properties();
    properties.put(InputFormatConfig.CATALOG_NAME, catalogName);
    Assert.assertFalse(Catalogs.hiveCatalog(conf, properties));
  }

  /**
   * 测试场景：Load Catalog Custom。
   *
   * <p>验证该方法在 Load Catalog Custom 条件下的行为是否符合预期。
   */
  @Test
  public void testLoadCatalogCustom() {
    String catalogName = "barCatalog";
    conf.set(
        InputFormatConfig.catalogPropertyConfigKey(catalogName, CatalogProperties.CATALOG_IMPL),
        CustomHadoopCatalog.class.getName());
    conf.set(
        InputFormatConfig.catalogPropertyConfigKey(
            catalogName, CatalogProperties.WAREHOUSE_LOCATION),
        "/tmp/mylocation");
    Optional<Catalog> customHadoopCatalog = Catalogs.loadCatalog(conf, catalogName);
    Assert.assertTrue(customHadoopCatalog.isPresent());
    Assertions.assertThat(customHadoopCatalog.get()).isInstanceOf(CustomHadoopCatalog.class);
    Properties properties = new Properties();
    properties.put(InputFormatConfig.CATALOG_NAME, catalogName);
    Assert.assertFalse(Catalogs.hiveCatalog(conf, properties));
  }

  /**
   * 测试场景：Load Catalog Location。
   *
   * <p>验证该方法在 Load Catalog Location 条件下的行为是否符合预期。
   */
  @Test
  public void testLoadCatalogLocation() {
    Assert.assertFalse(Catalogs.loadCatalog(conf, Catalogs.ICEBERG_HADOOP_TABLE_NAME).isPresent());
  }

  /**
   * 测试场景：Load Catalog Unknown。
   *
   * <p>验证该方法在 Load Catalog Unknown 条件下的行为是否符合预期。
   */
  @Test
  public void testLoadCatalogUnknown() {
    String catalogName = "barCatalog";
    conf.set(
        InputFormatConfig.catalogPropertyConfigKey(catalogName, CatalogUtil.ICEBERG_CATALOG_TYPE),
        "fooType");

    Assertions.assertThatThrownBy(() -> Catalogs.loadCatalog(conf, catalogName))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessage("Unknown catalog type: fooType");
  }

  public static class CustomHadoopCatalog extends HadoopCatalog {

    /** 辅助方法：CustomHadoopCatalog。 */
    public CustomHadoopCatalog() {}

    /** 辅助方法：CustomHadoopCatalog。 */
    public CustomHadoopCatalog(Configuration conf, String warehouseLocation) {
      super(conf, warehouseLocation);
    }
  }

  /** 辅助方法：setCustomCatalogProperties。 */
  private void setCustomCatalogProperties(String catalogName, String warehouseLocation) {
    conf.set(
        InputFormatConfig.catalogPropertyConfigKey(
            catalogName, CatalogProperties.WAREHOUSE_LOCATION),
        warehouseLocation);
    conf.set(
        InputFormatConfig.catalogPropertyConfigKey(catalogName, CatalogProperties.CATALOG_IMPL),
        CustomHadoopCatalog.class.getName());
    conf.set(InputFormatConfig.CATALOG_NAME, catalogName);
  }
}
