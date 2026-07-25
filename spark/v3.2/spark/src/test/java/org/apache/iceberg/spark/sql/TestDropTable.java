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
package org.apache.iceberg.spark.sql;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.iceberg.AssertHelpers;
import org.apache.iceberg.MetadataTableType;
import org.apache.iceberg.exceptions.ValidationException;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.Streams;
import org.apache.iceberg.spark.SparkCatalogTestBase;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * 文件级说明：测试 TestDropTable 相关功能。
 *
 * <p>所属模块：iceberg-spark（spark v3.2）。职责：验证 Iceberg 表在 Spark 引擎下 删除表 相关行为，覆盖正常路径与边界场景。
 *
 * <p>测试策略：基于 SparkSession + JUnit，通过构造测试数据、执行 SQL/DataFrame 操作并断言结果， 覆盖正常路径与边界情况。
 */
public class TestDropTable extends SparkCatalogTestBase {

  /** 测试删除表。 */
  public TestDropTable(String catalogName, String implementation, Map<String, String> config) {
    super(catalogName, implementation, config);
  }

  /** 创建表。 */
  @Before
  public void createTable() {
    sql("CREATE TABLE %s (id INT, name STRING) USING iceberg", tableName);
    sql("INSERT INTO %s VALUES (1, 'test')", tableName);
  }

  /** 移除表。 */
  @After
  public void removeTable() throws IOException {
    sql("DROP TABLE IF EXISTS %s", tableName);
  }

  /** 测试删除表场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testDropTable() throws IOException {
    dropTableInternal();
  }

  /** 测试删除表gcdisabled场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testDropTableGCDisabled() throws IOException {
    sql("ALTER TABLE %s SET TBLPROPERTIES (gc.enabled = false)", tableName);
    dropTableInternal();
  }

  /** 删除表internal。 */
  private void dropTableInternal() throws IOException {
    assertEquals(
        "Should have expected rows",
        ImmutableList.of(row(1, "test")),
        sql("SELECT * FROM %s", tableName));

    List<String> manifestAndFiles = manifestsAndFiles();
    Assert.assertEquals(
        "There should be 2 files for manifests and files", 2, manifestAndFiles.size());
    Assert.assertTrue("All files should be existed", checkFilesExist(manifestAndFiles, true));

    sql("DROP TABLE %s", tableName);
    Assert.assertFalse("Table should not exist", validationCatalog.tableExists(tableIdent));

    if (catalogName.equals("testhadoop")) {
      // HadoopCatalog drop table without purge will delete the base table location.
      Assert.assertTrue("All files should be deleted", checkFilesExist(manifestAndFiles, false));
    } else {
      Assert.assertTrue("All files should not be deleted", checkFilesExist(manifestAndFiles, true));
    }
  }

  /** 测试purge表场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testPurgeTable() throws IOException {
    assertEquals(
        "Should have expected rows",
        ImmutableList.of(row(1, "test")),
        sql("SELECT * FROM %s", tableName));

    List<String> manifestAndFiles = manifestsAndFiles();
    Assert.assertEquals(
        "There should be 2 files for manifests and files", 2, manifestAndFiles.size());
    Assert.assertTrue("All files should exist", checkFilesExist(manifestAndFiles, true));

    sql("DROP TABLE %s PURGE", tableName);
    Assert.assertFalse("Table should not exist", validationCatalog.tableExists(tableIdent));
    Assert.assertTrue("All files should be deleted", checkFilesExist(manifestAndFiles, false));
  }

  /** 测试purge表gcdisabled场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testPurgeTableGCDisabled() throws IOException {
    sql("ALTER TABLE %s SET TBLPROPERTIES (gc.enabled = false)", tableName);

    assertEquals(
        "Should have expected rows",
        ImmutableList.of(row(1, "test")),
        sql("SELECT * FROM %s", tableName));

    List<String> manifestAndFiles = manifestsAndFiles();
    Assert.assertEquals(
        "There totally should have 2 files for manifests and files", 2, manifestAndFiles.size());
    Assert.assertTrue("All files should be existed", checkFilesExist(manifestAndFiles, true));

    AssertHelpers.assertThrows(
        "Purge table is not allowed when GC is disabled",
        ValidationException.class,
        "Cannot purge table: GC is disabled (deleting files may corrupt other tables",
        () -> sql("DROP TABLE %s PURGE", tableName));

    Assert.assertTrue("Table should not been dropped", validationCatalog.tableExists(tableIdent));
    Assert.assertTrue("All files should not be deleted", checkFilesExist(manifestAndFiles, true));
  }

  /** 清单与文件。 */
  private List<String> manifestsAndFiles() {
    List<Object[]> files = sql("SELECT file_path FROM %s.%s", tableName, MetadataTableType.FILES);
    List<Object[]> manifests =
        sql("SELECT path FROM %s.%s", tableName, MetadataTableType.MANIFESTS);
    return Streams.concat(files.stream(), manifests.stream())
        .map(row -> (String) row[0])
        .collect(Collectors.toList());
  }

  /** 检查文件存在。 */
  private boolean checkFilesExist(List<String> files, boolean shouldExist) throws IOException {
    boolean mask = !shouldExist;
    if (files.isEmpty()) {
      return mask;
    }

    FileSystem fs = new Path(files.get(0)).getFileSystem(hiveConf);
    return files.stream()
        .allMatch(
            file -> {
              try {
                return fs.exists(new Path(file)) ^ mask;
              } catch (IOException e) {
                throw new RuntimeException(e);
              }
            });
  }
}
