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
import org.apache.iceberg.spark.procedures.SparkProcedures.ProcedureBuilder;
import org.apache.iceberg.util.DateTimeUtil;
import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.connector.catalog.Identifier;
import org.apache.spark.sql.connector.catalog.TableCatalog;
import org.apache.spark.sql.connector.iceberg.catalog.ProcedureParameter;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.Metadata;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;

/**
 * 所属模块：iceberg-spark v3.4
 *
 * <p>职责：回滚到指定时间戳的存储过程，将表回滚到不晚于给定时间的最近快照。
 *
 * <p>设计意图：通过 Iceberg rollback-to-timestamp 操作重置当前快照指针。
 *
 * <p>上下游关系：由 SparkProcedures 注册；由 CALL 语句经 CallExec 调用。
 */
class RollbackToTimestampProcedure extends BaseProcedure {

  private static final ProcedureParameter[] PARAMETERS =
      new ProcedureParameter[] {
        ProcedureParameter.required("table", DataTypes.StringType),
        ProcedureParameter.required("timestamp", DataTypes.TimestampType)
      };

  private static final StructType OUTPUT_TYPE =
      new StructType(
          new StructField[] {
            new StructField("previous_snapshot_id", DataTypes.LongType, false, Metadata.empty()),
            new StructField("current_snapshot_id", DataTypes.LongType, false, Metadata.empty())
          });
  /** 执行 builder 相关操作。 */
  public static ProcedureBuilder builder() {
    return new BaseProcedure.Builder<RollbackToTimestampProcedure>() {
      /** 执行 doBuild 相关操作。 */
      @Override
      protected RollbackToTimestampProcedure doBuild() {
        return new RollbackToTimestampProcedure(tableCatalog());
      }
    };
  }

  private RollbackToTimestampProcedure(TableCatalog catalog) {
    super(catalog);
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
    Identifier tableIdent = toIdentifier(args.getString(0), PARAMETERS[0].name());
    // timestamps in Spark have microsecond precision so this conversion is lossy
    long timestampMillis = DateTimeUtil.microsToMillis(args.getLong(1));

    return modifyIcebergTable(
        tableIdent,
        table -> {
          Snapshot previousSnapshot = table.currentSnapshot();

          table.manageSnapshots().rollbackToTime(timestampMillis).commit();

          Snapshot currentSnapshot = table.currentSnapshot();

          InternalRow outputRow =
              newInternalRow(previousSnapshot.snapshotId(), currentSnapshot.snapshotId());
          return new InternalRow[] {outputRow};
        });
  }
  /** 返回描述。 */
  @Override
  public String description() {
    return "RollbackToTimestampProcedure";
  }
}
