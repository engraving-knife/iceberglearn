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
import org.junit.Before;

/**
 * 文件级说明：测试 TestPartitionedWritesToBranch 相关功能。
 *
 * <p>所属模块：iceberg-spark（spark v3.4）。职责：验证 Iceberg 表在 Spark 引擎下 分区写到分支 相关行为，覆盖正常路径与边界场景。
 *
 * <p>测试策略：基于 SparkSession + JUnit，通过构造测试数据、执行 SQL/DataFrame 操作并断言结果， 覆盖正常路径与边界情况。
 */
public class TestPartitionedWritesToBranch extends PartitionedWritesTestBase {

  private static final String BRANCH = "test";

  /** 测试分区写到分支。 */
  public TestPartitionedWritesToBranch(
      String catalogName, String implementation, Map<String, String> config) {
    super(catalogName, implementation, config);
  }

  /** 创建表。 */
  @Before
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
}
