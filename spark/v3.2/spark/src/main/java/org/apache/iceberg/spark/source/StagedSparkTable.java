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
 * Iceberg 表在 Spark DataSource V2 中的实现。
 *
 * <p>所属模块：iceberg-spark v3.2。 类型：类 StagedSparkTable。
 *
 * <p>上下游：被 SparkCatalog 创建，依赖 Iceberg Table API 与底层扫描/写入组件。
 */
public class StagedSparkTable extends SparkTable implements StagedTable {
  private final Transaction transaction;

  /** 构造 StagedSparkTable 实例。 */
  public StagedSparkTable(Transaction transaction) {
    super(transaction.table(), false);
    this.transaction = transaction;
  }

  /** 提交事务或写入结果。 */
  @Override
  public void commitStagedChanges() {
    transaction.commitTransaction();
  }

  /** 中止并回滚当前操作。 */
  @Override
  public void abortStagedChanges() {
    // TODO: clean up
  }
}
