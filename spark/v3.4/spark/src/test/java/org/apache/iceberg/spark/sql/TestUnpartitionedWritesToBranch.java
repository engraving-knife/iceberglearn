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

import java.util.Map;
import org.apache.iceberg.Table;
import org.apache.iceberg.exceptions.ValidationException;
import org.assertj.core.api.Assertions;
import org.junit.Test;

/**
 * 文件级说明：测试 TestUnpartitionedWritesToBranch 相关功能。
 *
 * <p>所属模块：iceberg-spark（spark v3.4）。职责：验证 Iceberg 表在 Spark 引擎下 非分区写到分支 相关行为，覆盖正常路径与边界场景。
 *
 * <p>测试策略：基于 SparkSession + JUnit，通过构造测试数据、执行 SQL/DataFrame 操作并断言结果， 覆盖正常路径与边界情况。
 */
public class TestUnpartitionedWritesToBranch extends UnpartitionedWritesTestBase {

  private static final String BRANCH = "test";

  /** 测试非分区写到分支。 */
  public TestUnpartitionedWritesToBranch(
      String catalogName, String implementation, Map<String, String> config) {
    super(catalogName, implementation, config);
  }

  /** 创建表。 */
  @Override
  public void createTables() {
    super.createTables();
    Table table = validationCatalog.loadTable(tableIdent);
    table.manageSnapshots().createBranch(BRANCH, table.currentSnapshot().snapshotId()).commit();
    sql("REFRESH TABLE " + tableName);
  }

  /** 提交target。 */
  @Override
  protected String commitTarget() {
    return String.format("%s.branch_%s", tableName, BRANCH);
  }

  /** 辅助方法：selectTarget。 */
  @Override
  protected String selectTarget() {
    return String.format("%s VERSION AS OF '%s'", tableName, BRANCH);
  }

  /** 测试插入不存在的已存在的分支fails场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testInsertIntoNonExistingBranchFails() {
    Assertions.assertThatThrownBy(
            () -> sql("INSERT INTO %s.branch_not_exist VALUES (4, 'd'), (5, 'e')", tableName))
        .isInstanceOf(ValidationException.class)
        .hasMessage("Cannot use branch (does not exist): not_exist");
  }
}
