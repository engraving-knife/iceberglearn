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
 * 将表回滚到指定时间点的存储过程。
 *
 * <p>所属模块：iceberg-spark（procedures 子包）。通过 {@code CALL system.rollback_to_timestamp}
 * 调用，把表回滚到给定时间戳对应的快照。
 *
 * <p>注意：本过程会使所有引用该表的 Spark 缓存计划失效。
 *
 * <p>设计意图：作为 Iceberg 表管理能力在 Spark 中的过程化暴露，封装 manageSnapshots().rollbackToTime。
 *
 * <p>上下游关系：由 {@link SparkProcedures} 注册；底层调用 {@link
 * org.apache.iceberg.ManageSnapshots#rollbackToTime(long)}。
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

  /** 返回本过程的构建器。 */
  public static ProcedureBuilder builder() {
    return new BaseProcedure.Builder<RollbackToTimestampProcedure>() {
      /** 执行 doBuild 相关操作。 */
      @Override
      protected RollbackToTimestampProcedure doBuild() {
        return new RollbackToTimestampProcedure(tableCatalog());
      }
    };
  }

  /** 以所属 Catalog 构造。 */
  private RollbackToTimestampProcedure(TableCatalog catalog) {
    super(catalog);
  }

  /** 返回参数定义（table、timestamp）。 */
  @Override
  public ProcedureParameter[] parameters() {
    return PARAMETERS;
  }

  /** 返回输出结构（前一快照 ID、当前快照 ID）。 */
  @Override
  public StructType outputType() {
    return OUTPUT_TYPE;
  }

  /**
   * 执行回滚。
   *
   * <p>逻辑：解析表标识，将微秒时间戳转为毫秒（有损），记录回滚前快照，调用 rollbackToTime 提交，再取回滚后当前快照，输出前后快照 ID。
   */
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

  /** 返回过程描述。 */
  @Override
  public String description() {
    return "RollbackToTimestampProcedure";
  }
}
