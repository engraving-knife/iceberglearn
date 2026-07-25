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

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.iceberg.Table;
import org.apache.iceberg.TableProperties;
import org.apache.iceberg.exceptions.ValidationException;
import org.apache.iceberg.spark.SparkSQLProperties;
import org.assertj.core.api.Assertions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * 文件级说明：测试 TestPartitionedWritesToWapBranch 相关功能。
 *
 * <p>所属模块：iceberg-spark（spark v3.5）。职责：验证 Iceberg 表在 Spark 引擎下 分区写到wap分支 相关行为，覆盖正常路径与边界场景。
 *
 * <p>测试策略：基于 SparkSession + JUnit，通过构造测试数据、执行 SQL/DataFrame 操作并断言结果， 覆盖正常路径与边界情况。
 */
public class TestPartitionedWritesToWapBranch extends PartitionedWritesTestBase {

  private static final String BRANCH = "test";

  /** 测试分区写到wap分支。 */
  public TestPartitionedWritesToWapBranch(
      String catalogName, String implementation, Map<String, String> config) {
    super(catalogName, implementation, config);
  }

  /** 创建表。 */
  @Before
  @Override
  public void createTables() {
    spark.conf().set(SparkSQLProperties.WAP_BRANCH, BRANCH);
    sql(
        "CREATE TABLE %s (id bigint, data string) USING iceberg PARTITIONED BY (truncate(id, 3)) OPTIONS (%s = 'true')",
        tableName, TableProperties.WRITE_AUDIT_PUBLISH_ENABLED);
    sql("INSERT INTO %s VALUES (1, 'a'), (2, 'b'), (3, 'c')", tableName);
  }

  /** 移除表。 */
  @After
  @Override
  public void removeTables() {
    super.removeTables();
    spark.conf().unset(SparkSQLProperties.WAP_BRANCH);
    spark.conf().unset(SparkSQLProperties.WAP_ID);
  }

  /** 提交target。 */
  @Override
  protected String commitTarget() {
    return tableName;
  }

  /** 辅助方法：selectTarget。 */
  @Override
  protected String selectTarget() {
    return String.format("%s VERSION AS OF '%s'", tableName, BRANCH);
  }

  /** 测试分支与wap分支cannotboth被集合用于写场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testBranchAndWapBranchCannotBothBeSetForWrite() {
    Table table = validationCatalog.loadTable(tableIdent);
    table.manageSnapshots().createBranch("test2", table.refs().get(BRANCH).snapshotId()).commit();
    sql("REFRESH TABLE " + tableName);
    Assertions.assertThatThrownBy(
            () -> sql("INSERT INTO %s.branch_test2 VALUES (4, 'd')", tableName))
        .isInstanceOf(ValidationException.class)
        .hasMessage(
            "Cannot write to both branch and WAP branch, but got branch [test2] and WAP branch [%s]",
            BRANCH);
  }

  /** 测试wapid与wap分支cannotboth被集合用于写场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testWapIdAndWapBranchCannotBothBeSetForWrite() {
    String wapId = UUID.randomUUID().toString();
    spark.conf().set(SparkSQLProperties.WAP_ID, wapId);
    Assertions.assertThatThrownBy(() -> sql("INSERT INTO %s VALUES (4, 'd')", tableName))
        .isInstanceOf(ValidationException.class)
        .hasMessage(
            "Cannot set both WAP ID and branch, but got ID [%s] and branch [%s]", wapId, BRANCH);
  }

  /** 断言分区元数据。 */
  @Override
  protected void assertPartitionMetadata(
      String tableName, List<Object[]> expected, String... selectPartitionColumns) {
    // Cannot read from the .partitions table newly written data into the WAP branch. See
    // https://github.com/apache/iceberg/issues/7297 for more details.
  }
}
