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
package org.apache.iceberg.delta;

import java.io.File;
import java.io.IOException;
import java.util.List;
import org.apache.hadoop.conf.Configuration;
import org.apache.iceberg.BaseMetastoreCatalog;
import org.apache.iceberg.TableOperations;
import org.apache.iceberg.catalog.Catalog;
import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.catalog.TableIdentifier;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 文件级说明：测试 TestBaseSnapshotDeltaLakeTableAction 的功能。
 *
 * <p>所属模块：iceberg-delta-lake。职责：验证 TestBaseSnapshotDeltaLakeTableAction 在各类场景下的行为是否符合预期，
 * 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 JUnit 框架，通过构造输入、调用方法、断言结果来覆盖功能点。
 */
public class TestBaseSnapshotDeltaLakeTableAction {
  @TempDir private File sourceFolder;
  @TempDir private File destFolder;
  private String sourceTableLocation;
  private final Configuration testHadoopConf = new Configuration();
  private String newTableLocation;
  private final Catalog testCatalog = new TestCatalog();

  /** 辅助方法：before。 */
  @BeforeEach
  public void before() throws IOException {
    sourceTableLocation = sourceFolder.toURI().toString();
    newTableLocation = destFolder.toURI().toString();
  }

  /**
   * 测试场景：Required Table Identifier。
   *
   * <p>验证该方法在 Required Table Identifier 条件下的行为是否符合预期。
   */
  @Test
  public void testRequiredTableIdentifier() {
    SnapshotDeltaLakeTable testAction =
        new BaseSnapshotDeltaLakeTableAction(sourceTableLocation)
            .icebergCatalog(testCatalog)
            .deltaLakeConfiguration(testHadoopConf)
            .tableLocation(newTableLocation);
    Assertions.assertThatThrownBy(testAction::execute)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Iceberg catalog and identifier cannot be null. Make sure to configure the action with a valid Iceberg catalog and identifier.");
  }

  /**
   * 测试场景：Required Iceberg Catalog。
   *
   * <p>验证该方法在 Required Iceberg Catalog 条件下的行为是否符合预期。
   */
  @Test
  public void testRequiredIcebergCatalog() {
    SnapshotDeltaLakeTable testAction =
        new BaseSnapshotDeltaLakeTableAction(sourceTableLocation)
            .as(TableIdentifier.of("test", "test"))
            .deltaLakeConfiguration(testHadoopConf)
            .tableLocation(newTableLocation);
    Assertions.assertThatThrownBy(testAction::execute)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Iceberg catalog and identifier cannot be null. Make sure to configure the action with a valid Iceberg catalog and identifier.");
  }

  /**
   * 测试场景：Required Delta Lake Configuration。
   *
   * <p>验证该方法在 Required Delta Lake Configuration 条件下的行为是否符合预期。
   */
  @Test
  public void testRequiredDeltaLakeConfiguration() {
    SnapshotDeltaLakeTable testAction =
        new BaseSnapshotDeltaLakeTableAction(sourceTableLocation)
            .as(TableIdentifier.of("test", "test"))
            .icebergCatalog(testCatalog)
            .tableLocation(newTableLocation);
    Assertions.assertThatThrownBy(testAction::execute)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Make sure to configure the action with a valid deltaLakeConfiguration");
  }

  /**
   * 测试场景：Delta Table Not Exist。
   *
   * <p>验证该方法在 Delta Table Not Exist 条件下的行为是否符合预期。
   */
  @Test
  public void testDeltaTableNotExist() {
    SnapshotDeltaLakeTable testAction =
        new BaseSnapshotDeltaLakeTableAction(sourceTableLocation)
            .as(TableIdentifier.of("test", "test"))
            .deltaLakeConfiguration(testHadoopConf)
            .icebergCatalog(testCatalog)
            .tableLocation(newTableLocation);
    Assertions.assertThatThrownBy(testAction::execute)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Delta Lake table does not exist at the given location: %s", sourceTableLocation);
  }

  private static class TestCatalog extends BaseMetastoreCatalog {
    TestCatalog() {}

    /** 辅助方法：newTableOps。 */
    @Override
    protected TableOperations newTableOps(TableIdentifier tableIdentifier) {
      return null;
    }

    /** 辅助方法：defaultWarehouseLocation。 */
    @Override
    protected String defaultWarehouseLocation(TableIdentifier tableIdentifier) {
      return null;
    }

    /** 辅助方法：listTables。 */
    @Override
    public List<TableIdentifier> listTables(Namespace namespace) {
      return null;
    }

    /** 辅助方法：dropTable。 */
    @Override
    public boolean dropTable(TableIdentifier identifier, boolean purge) {
      return false;
    }

    /** 辅助方法：renameTable。 */
    @Override
    public void renameTable(TableIdentifier from, TableIdentifier to) {}
  }
}
