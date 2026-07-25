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

import org.apache.iceberg.IsolationLevel;
import org.apache.iceberg.MetadataColumns;
import org.apache.iceberg.Table;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.connector.expressions.Expressions;
import org.apache.spark.sql.connector.expressions.NamedReference;
import org.apache.spark.sql.connector.iceberg.write.DeltaWriteBuilder;
import org.apache.spark.sql.connector.iceberg.write.ExtendedLogicalWriteInfo;
import org.apache.spark.sql.connector.iceberg.write.SupportsDelta;
import org.apache.spark.sql.connector.read.Scan;
import org.apache.spark.sql.connector.read.ScanBuilder;
import org.apache.spark.sql.connector.write.LogicalWriteInfo;
import org.apache.spark.sql.connector.write.RowLevelOperation;
import org.apache.spark.sql.connector.write.RowLevelOperationInfo;
import org.apache.spark.sql.util.CaseInsensitiveStringMap;

/**
 * Iceberg 表在 Spark DataSource V2 中的实现。
 *
 * <p>所属模块：iceberg-spark v3.3。 类型：类 SparkPositionDeltaOperation。
 *
 * <p>上下游：被 SparkCatalog 创建，依赖 Iceberg Table API 与底层扫描/写入组件。
 */
class SparkPositionDeltaOperation implements RowLevelOperation, SupportsDelta {

  private final SparkSession spark;
  private final Table table;
  private final String branch;
  private final Command command;
  private final IsolationLevel isolationLevel;

  // lazy vars
  private ScanBuilder lazyScanBuilder;
  private Scan configuredScan;
  private DeltaWriteBuilder lazyWriteBuilder;

  SparkPositionDeltaOperation(
      SparkSession spark,
      Table table,
      String branch,
      RowLevelOperationInfo info,
      IsolationLevel isolationLevel) {
    this.spark = spark;
    this.table = table;
    this.branch = branch;
    this.command = info.command();
    this.isolationLevel = isolationLevel;
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  @Override
  public Command command() {
    return command;
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @param options 参数
   * @return 结果对象
   */
  @Override
  public ScanBuilder newScanBuilder(CaseInsensitiveStringMap options) {
    if (lazyScanBuilder == null) {
      this.lazyScanBuilder =
          new SparkScanBuilder(spark, table, branch, options) {
            /**
             * 构造并返回目标对象。
             *
             * @return 结果对象
             */
            @Override
            public Scan build() {
              Scan scan = super.buildMergeOnReadScan();
              SparkPositionDeltaOperation.this.configuredScan = scan;
              return scan;
            }
          };
    }

    return lazyScanBuilder;
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @param info 参数
   * @return 结果对象
   */
  @Override
  public DeltaWriteBuilder newWriteBuilder(LogicalWriteInfo info) {
    if (lazyWriteBuilder == null) {
      Preconditions.checkArgument(
          info instanceof ExtendedLogicalWriteInfo, "info must be ExtendedLogicalWriteInfo");
      // don't validate the scan is not null as if the condition evaluates to false,
      // the optimizer replaces the original scan relation with a local relation
      lazyWriteBuilder =
          new SparkPositionDeltaWriteBuilder(
              spark,
              table,
              branch,
              command,
              configuredScan,
              isolationLevel,
              (ExtendedLogicalWriteInfo) info);
    }

    return lazyWriteBuilder;
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  @Override
  public NamedReference[] requiredMetadataAttributes() {
    NamedReference specId = Expressions.column(MetadataColumns.SPEC_ID.name());
    NamedReference partition = Expressions.column(MetadataColumns.PARTITION_COLUMN_NAME);
    return new NamedReference[] {specId, partition};
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  @Override
  public NamedReference[] rowId() {
    NamedReference file = Expressions.column(MetadataColumns.FILE_PATH.name());
    NamedReference pos = Expressions.column(MetadataColumns.ROW_POSITION.name());
    return new NamedReference[] {file, pos};
  }
}
