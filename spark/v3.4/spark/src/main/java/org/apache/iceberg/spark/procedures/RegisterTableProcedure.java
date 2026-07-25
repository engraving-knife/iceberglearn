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
package org.apache.iceberg.spark.procedures;

import org.apache.iceberg.Snapshot;
import org.apache.iceberg.SnapshotSummary;
import org.apache.iceberg.Table;
import org.apache.iceberg.catalog.Catalog;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.spark.Spark3Util;
import org.apache.iceberg.spark.procedures.SparkProcedures.ProcedureBuilder;
import org.apache.iceberg.spark.source.HasIcebergCatalog;
import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.connector.catalog.TableCatalog;
import org.apache.spark.sql.connector.iceberg.catalog.ProcedureParameter;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.Metadata;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;

/**
 * 所属模块：iceberg-spark v3.4
 *
 * <p>职责：注册表的存储过程，通过已有元数据文件注册一个 Iceberg 表到 catalog。
 *
 * <p>设计意图：无需数据拷贝，直接加载已有 metadata.json 注册表。
 *
 * <p>上下游关系：由 SparkProcedures 注册；由 CALL 语句经 CallExec 调用。
 */
class RegisterTableProcedure extends BaseProcedure {
  private static final ProcedureParameter[] PARAMETERS =
      new ProcedureParameter[] {
        ProcedureParameter.required("table", DataTypes.StringType),
        ProcedureParameter.required("metadata_file", DataTypes.StringType)
      };

  private static final StructType OUTPUT_TYPE =
      new StructType(
          new StructField[] {
            new StructField("current_snapshot_id", DataTypes.LongType, true, Metadata.empty()),
            new StructField("total_records_count", DataTypes.LongType, true, Metadata.empty()),
            new StructField("total_data_files_count", DataTypes.LongType, true, Metadata.empty())
          });

  private RegisterTableProcedure(TableCatalog tableCatalog) {
    super(tableCatalog);
  }
  /** 执行 builder 相关操作。 */
  public static ProcedureBuilder builder() {
    return new BaseProcedure.Builder<RegisterTableProcedure>() {
      /** 执行 doBuild 相关操作。 */
      @Override
      protected RegisterTableProcedure doBuild() {
        return new RegisterTableProcedure(tableCatalog());
      }
    };
  }
  /** 返回参数。 */
  @Override
  public ProcedureParameter[] parameters() {
    return PARAMETERS;
  }
  /** 执行 outputType 相关操作。 */
  @Override
  public StructType outputType() {
    return OUTPUT_TYPE;
  }
  /** 执行过程并返回结果行。 */
  @Override
  public InternalRow[] call(InternalRow args) {
    TableIdentifier tableName =
        Spark3Util.identifierToTableIdentifier(toIdentifier(args.getString(0), "table"));
    String metadataFile = args.getString(1);
    Preconditions.checkArgument(
        tableCatalog() instanceof HasIcebergCatalog,
        "Cannot use Register Table in a non-Iceberg catalog");
    Preconditions.checkArgument(
        metadataFile != null && !metadataFile.isEmpty(),
        "Cannot handle an empty argument metadata_file");

    Catalog icebergCatalog = ((HasIcebergCatalog) tableCatalog()).icebergCatalog();
    Table table = icebergCatalog.registerTable(tableName, metadataFile);
    Long currentSnapshotId = null;
    Long totalDataFiles = null;
    Long totalRecords = null;

    Snapshot currentSnapshot = table.currentSnapshot();
    if (currentSnapshot != null) {
      currentSnapshotId = currentSnapshot.snapshotId();
      totalDataFiles =
          Long.parseLong(currentSnapshot.summary().get(SnapshotSummary.TOTAL_DATA_FILES_PROP));
      totalRecords =
          Long.parseLong(currentSnapshot.summary().get(SnapshotSummary.TOTAL_RECORDS_PROP));
    }

    return new InternalRow[] {newInternalRow(currentSnapshotId, totalRecords, totalDataFiles)};
  }
  /** 返回描述。 */
  @Override
  public String description() {
    return "RegisterTableProcedure";
  }
}
