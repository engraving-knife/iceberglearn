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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.iceberg.Schema;
import org.apache.iceberg.Table;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.hadoop.HadoopFileIO;
import org.apache.iceberg.hadoop.HadoopTables;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.types.Types;
import org.assertj.core.api.Assertions;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * 文件级说明：测试 TestCatalogTableLoader 的功能。
 *
 * <p>所属模块：iceberg-flink（flink v1.16）。职责：验证 TestCatalogTableLoader 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 Flink TableEnvironment + JUnit，通过构造测试数据、执行 SQL/Table API 操作、 断言结果来覆盖正常路径与边界情况。
 */
public class TestCatalogTableLoader extends FlinkTestBase {

  private static File warehouse = null;
  private static final TableIdentifier IDENTIFIER = TableIdentifier.of("default", "my_table");
  private static final Schema SCHEMA =
      new Schema(Types.NestedField.required(1, "f1", Types.StringType.get()));

  /** 辅助方法：createWarehouse，create Warehouse。 */
  @BeforeClass
  public static void createWarehouse() throws IOException {
    warehouse = File.createTempFile("warehouse", null);
    Assert.assertTrue(warehouse.delete());
    hiveConf.set("my_key", "my_value");
  }

  /** 辅助方法：dropWarehouse，drop Warehouse。 */
  @AfterClass
  public static void dropWarehouse() throws IOException {
    if (warehouse != null && warehouse.exists()) {
      Path warehousePath = new Path(warehouse.getAbsolutePath());
      FileSystem fs = warehousePath.getFileSystem(hiveConf);
      Assert.assertTrue("Failed to delete " + warehousePath, fs.delete(warehousePath, true));
    }
  }

  /**
   * 测试场景：Hadoop Table Loader。
   *
   * <p>验证该方法在 Hadoop Table Loader 条件下的行为是否符合预期。
   */
  @Test
  public void testHadoopTableLoader() throws IOException, ClassNotFoundException {
    String location = "file:" + warehouse + "/my_table";
    new HadoopTables(hiveConf).create(SCHEMA, location);
    validateTableLoader(TableLoader.fromHadoopTable(location, hiveConf));
  }

  /**
   * 测试场景：Hive Catalog Table Loader。
   *
   * <p>验证该方法在 Hive Catalog Table Loader 条件下的行为是否符合预期。
   */
  @Test
  public void testHiveCatalogTableLoader() throws IOException, ClassNotFoundException {
    CatalogLoader loader = CatalogLoader.hive("my_catalog", hiveConf, Maps.newHashMap());
    javaSerdes(loader).loadCatalog().createTable(IDENTIFIER, SCHEMA);

    CatalogLoader catalogLoader = CatalogLoader.hive("my_catalog", hiveConf, Maps.newHashMap());
    validateTableLoader(TableLoader.fromCatalog(catalogLoader, IDENTIFIER));
  }

  /** 辅助方法：validateTableLoader，validate Table Loader。 */
  private static void validateTableLoader(TableLoader loader)
      throws IOException, ClassNotFoundException {
    TableLoader copied = javaSerdes(loader);
    copied.open();
    try {
      validateHadoopConf(copied.loadTable());
    } finally {
      copied.close();
    }
  }

  /** 辅助方法：validateHadoopConf，validate Hadoop Conf。 */
  private static void validateHadoopConf(Table table) {
    FileIO io = table.io();
    Assertions.assertThat(io)
        .as("FileIO should be a HadoopFileIO")
        .isInstanceOf(HadoopFileIO.class);
    HadoopFileIO hadoopIO = (HadoopFileIO) io;
    Assert.assertEquals("my_value", hadoopIO.conf().get("my_key"));
  }

  /** 辅助方法：javaSerdes，java Serdes。 */
  @SuppressWarnings("unchecked")
  private static <T> T javaSerdes(T object) throws IOException, ClassNotFoundException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
      out.writeObject(object);
    }

    try (ObjectInputStream in =
        new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      return (T) in.readObject();
    }
  }
}
