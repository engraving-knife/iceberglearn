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
 * Iceberg 表的 changelog（行变更日志）Spark 视图。
 *
 * <p>所属模块：iceberg-spark（Spark v3.5 集成模块），source 子包。
 *
 * <p>职责：把 Iceberg 表包装为只支持批量读的 Spark {@link Table}，暴露其 changelog schema （在原 schema 上附加
 * _change_type/_change_ordinal/_commit_snapshot 元数据列）， 通过 {@link
 * SparkScanBuilder#buildChangelogScan} 构造 changelog 扫描。
 *
 * <p>设计意图：懒加载 SparkSession、changelog schema 与 Spark schema 避免重复计算； 元数据列通过 SupportsMetadataColumns
 * 暴露（spec_id、partition、file_path、row_position、is_deleted）。
 *
 * <p>上下游关系：被 Spark catalog 在表名后追加 ".changes" 时返回；产出 SparkScan 供读取。
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

  /** 构造 changelog 表，指定是否在每次构建扫描时急切刷新表元数据。 */
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

  /**
   * 构造扫描 builder。
   *
   * <p>逻辑：若 refreshEagerly 则先刷新表；返回匿名 SparkScanBuilder，build() 直接构造 changelog 扫描。
   */
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

  /** 返回 changelog 表暴露的元数据列：spec_id、partition、file_path、row_position、is_deleted。 */
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
