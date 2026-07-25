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
import org.apache.iceberg.Schema;
import org.apache.iceberg.Table;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.spark.SparkSchemaUtil;
import org.apache.iceberg.spark.SparkUtil;
import org.apache.iceberg.spark.SparkWriteConf;
import org.apache.iceberg.types.TypeUtil;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.connector.read.Scan;
import org.apache.spark.sql.connector.write.DeltaWrite;
import org.apache.spark.sql.connector.write.DeltaWriteBuilder;
import org.apache.spark.sql.connector.write.LogicalWriteInfo;
import org.apache.spark.sql.connector.write.RowLevelOperation.Command;
import org.apache.spark.sql.types.StructType;

/**
 * Position Delta 写入的构建器。
 *
 * <p>所属模块：iceberg-spark（source 子包）。实现 {@link DeltaWriteBuilder}，为 Spark 行级
 * 操作（UPDATE/DELETE/MERGE）基于 position delta 的写入构建 {@link SparkPositionDeltaWrite}。
 *
 * <p>职责：校验行 ID schema、元数据 schema 与分区变换合法性，构造数据 schema 后创建写入实例。
 *
 * <p>设计意图：行级操作需要按文件路径+行位置定位受影响行，本构建器在 build 前严格校验 必需的 rowId/metadata schema，确保 position delta
 * 写入语义正确。
 *
 * <p>上下游关系：由 Spark 行级操作计划调用；产出 {@link SparkPositionDeltaWrite}。
 */
class SparkPositionDeltaWriteBuilder implements DeltaWriteBuilder {

  private static final Schema EXPECTED_ROW_ID_SCHEMA =
      new Schema(MetadataColumns.FILE_PATH, MetadataColumns.ROW_POSITION);

  private final SparkSession spark;
  private final Table table;
  private final Command command;
  private final SparkBatchQueryScan scan;
  private final IsolationLevel isolationLevel;
  private final SparkWriteConf writeConf;
  private final LogicalWriteInfo info;
  private final boolean checkNullability;
  private final boolean checkOrdering;

  /** 构建器构造，初始化写配置与可空性/排序校验开关。 */
  SparkPositionDeltaWriteBuilder(
      SparkSession spark,
      Table table,
      String branch,
      Command command,
      Scan scan,
      IsolationLevel isolationLevel,
      LogicalWriteInfo info) {
    this.spark = spark;
    this.table = table;
    this.command = command;
    this.scan = (SparkBatchQueryScan) scan;
    this.isolationLevel = isolationLevel;
    this.writeConf = new SparkWriteConf(spark, table, branch, info.options());
    this.info = info;
    this.checkNullability = writeConf.checkNullability();
    this.checkOrdering = writeConf.checkOrdering();
  }

  /** 校验 schema 与分区变换后构造 {@link SparkPositionDeltaWrite}。 */
  @Override
  public DeltaWrite build() {
    Schema dataSchema = dataSchema();

    validateRowIdSchema();
    validateMetadataSchema();
    SparkUtil.validatePartitionTransforms(table.spec());

    return new SparkPositionDeltaWrite(
        spark, table, command, scan, isolationLevel, writeConf, info, dataSchema);
  }

  /** 计算数据 schema：info.schema 为空返回 null，否则转换并校验。 */
  private Schema dataSchema() {
    if (info.schema() == null || info.schema().isEmpty()) {
      return null;
    } else {
      Schema dataSchema = SparkSchemaUtil.convert(table.schema(), info.schema());
      validateSchema("data", table.schema(), dataSchema);
      return dataSchema;
    }
  }

  /** 校验行 ID schema 与期望（FILE_PATH+ROW_POSITION）一致。 */
  private void validateRowIdSchema() {
    Preconditions.checkArgument(info.rowIdSchema().isPresent(), "Row ID schema must be set");
    StructType rowIdSparkType = info.rowIdSchema().get();
    Schema rowIdSchema = SparkSchemaUtil.convert(EXPECTED_ROW_ID_SCHEMA, rowIdSparkType);
    validateSchema("row ID", EXPECTED_ROW_ID_SCHEMA, rowIdSchema);
  }

  /** 校验元数据 schema 与期望（SPEC_ID+分区列）一致。 */
  private void validateMetadataSchema() {
    Preconditions.checkArgument(info.metadataSchema().isPresent(), "Metadata schema must be set");
    Schema expectedMetadataSchema =
        new Schema(
            MetadataColumns.SPEC_ID,
            MetadataColumns.metadataColumn(table, MetadataColumns.PARTITION_COLUMN_NAME));
    StructType metadataSparkType = info.metadataSchema().get();
    Schema metadataSchema = SparkSchemaUtil.convert(expectedMetadataSchema, metadataSparkType);
    validateSchema("metadata", expectedMetadataSchema, metadataSchema);
  }

  /** 按可空性/排序开关校验期望与实际 schema 兼容。 */
  private void validateSchema(String context, Schema expected, Schema actual) {
    TypeUtil.validateSchema(context, expected, actual, checkNullability, checkOrdering);
  }
}
