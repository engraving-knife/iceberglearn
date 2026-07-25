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
package org.apache.iceberg.spark.source;

import org.apache.iceberg.Transaction;
import org.apache.spark.sql.connector.catalog.StagedTable;

/**
 * 所属模块：iceberg-spark v3.4
 *
 * <p>职责：暂存 Spark 表，支持分阶段建表/写入并在 commit 前不对外可见。
 *
 * <p>设计意图：实现 StagedTable，包装 SparkTable 以提供创建后暂存、提交或回滚的能力。
 *
 * <p>上下游关系：由 SparkCatalog 在 CREATE TABLE AS SELECT 等分阶段流程创建。
 */
public class StagedSparkTable extends SparkTable implements StagedTable {
  private final Transaction transaction;

  public StagedSparkTable(Transaction transaction) {
    super(transaction.table(), false);
    this.transaction = transaction;
  }
  /** 执行 commitStagedChanges 相关操作。 */
  @Override
  public void commitStagedChanges() {
    transaction.commitTransaction();
  }
  /** 执行 abortStagedChanges 相关操作。 */
  @Override
  public void abortStagedChanges() {
    // TODO: clean up
  }
}
