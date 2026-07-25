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

import java.util.Set;
import org.apache.iceberg.ChangelogUtil;
import org.apache.iceberg.MetadataColumns;
import org.apache.iceberg.Partitioning;
import org.apache.iceberg.Schema;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableSet;
import org.apache.iceberg.spark.SparkSchemaUtil;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.connector.catalog.MetadataColumn;
import org.apache.spark.sql.connector.catalog.SupportsMetadataColumns;
import org.apache.spark.sql.connector.catalog.SupportsRead;
import org.apache.spark.sql.connector.catalog.Table;
import org.apache.spark.sql.connector.catalog.TableCapability;
import org.apache.spark.sql.connector.read.Scan;
import org.apache.spark.sql.connector.read.ScanBuilder;
import org.apache.spark.sql.types.DataType;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructType;
import org.apache.spark.sql.util.CaseInsensitiveStringMap;

/**
 * 所属模块：iceberg-spark v3.4
 *
 * <p>职责：Iceberg 变更日志表的 Spark 包装，将 changelog 元数据表暴露为 Spark 表。
 *
 * <p>设计意图：以 `_changes` 后缀表名访问行级变更，委托 SparkChangelogScan。
 *
 * <p>上下游关系：由 SparkCatalog 在解析 changelog 后缀时创建。
 */
public class SparkChangelogTable implements Table, SupportsRead, SupportsMetadataColumns {

  public static final String TABLE_NAME = "changes";

  private static final Set<TableCapability> CAPABILITIES =
      ImmutableSet.of(TableCapability.BATCH_READ);

  private final org.apache.iceberg.Table icebergTable;
  private final boolean refreshEagerly;

  private SparkSession lazySpark = null;
  private StructType lazyTableSparkType = null;
  private Schema lazyChangelogSchema = null;

  public SparkChangelogTable(org.apache.iceberg.Table icebergTable, boolean refreshEagerly) {
    this.icebergTable = icebergTable;
    this.refreshEagerly = refreshEagerly;
  }
  /** 返回名称。 */
  @Override
  public String name() {
    return icebergTable.name() + "." + TABLE_NAME;
  }
  /** 返回 Schema。 */
  @Override
  public StructType schema() {
    if (lazyTableSparkType == null) {
      this.lazyTableSparkType = SparkSchemaUtil.convert(changelogSchema());
    }

    return lazyTableSparkType;
  }
  /** 返回能力集。 */
  @Override
  public Set<TableCapability> capabilities() {
    return CAPABILITIES;
  }
  /** 创建 ScanBuilder 实例。 */
  @Override
  public ScanBuilder newScanBuilder(CaseInsensitiveStringMap options) {
    if (refreshEagerly) {
      icebergTable.refresh();
    }

    return new SparkScanBuilder(spark(), icebergTable, changelogSchema(), options) {
      /** 构建目标对象。 */
      @Override
      public Scan build() {
        return buildChangelogScan();
      }
    };
  }
  /** 执行 changelogSchema 相关操作。 */
  private Schema changelogSchema() {
    if (lazyChangelogSchema == null) {
      this.lazyChangelogSchema = ChangelogUtil.changelogSchema(icebergTable.schema());
    }

    return lazyChangelogSchema;
  }
  /** 执行 spark 相关操作。 */
  private SparkSession spark() {
    if (lazySpark == null) {
      this.lazySpark = SparkSession.active();
    }

    return lazySpark;
  }
  /** 执行 metadataColumns 相关操作。 */
  @Override
  public MetadataColumn[] metadataColumns() {
    DataType sparkPartitionType = SparkSchemaUtil.convert(Partitioning.partitionType(icebergTable));
    return new MetadataColumn[] {
      new SparkMetadataColumn(MetadataColumns.SPEC_ID.name(), DataTypes.IntegerType, false),
      new SparkMetadataColumn(MetadataColumns.PARTITION_COLUMN_NAME, sparkPartitionType, true),
      new SparkMetadataColumn(MetadataColumns.FILE_PATH.name(), DataTypes.StringType, false),
      new SparkMetadataColumn(MetadataColumns.ROW_POSITION.name(), DataTypes.LongType, false),
      new SparkMetadataColumn(MetadataColumns.IS_DELETED.name(), DataTypes.BooleanType, false)
    };
  }
}
