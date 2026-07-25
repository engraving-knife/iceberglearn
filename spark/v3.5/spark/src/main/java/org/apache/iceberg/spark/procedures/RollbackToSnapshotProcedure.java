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
import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.connector.catalog.Identifier;
import org.apache.spark.sql.connector.catalog.TableCatalog;
import org.apache.spark.sql.connector.iceberg.catalog.ProcedureParameter;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.Metadata;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;

/**
 * Spark 存储过程：把表回滚到指定快照 ID。
 *
 * <p>所属模块：iceberg-spark（Spark v3.5 集成模块），procedures 子包。对应 {@code system.rollback_to_snapshot(table
 * => '...', snapshot_id => ...)}。
 *
 * <p>职责：调用 {@link org.apache.iceberg.ManageSnapshots#rollbackTo(long)} 把表当前快照回滚到 指定快照。返回回滚前快照 ID
 * 与回滚后快照 ID。
 *
 * <p>设计意图：通过 {@link BaseProcedure#modifyIcebergTable} 在修改上下文中执行，自动失效引用该表的 Spark 缓存计划。
 *
 * <p>上下游关系：由 {@link SparkProcedures} 注册；依赖 Iceberg core 的 ManageSnapshots。
 *
 * @see org.apache.iceberg.ManageSnapshots#rollbackTo(long)
 */
class RollbackToSnapshotProcedure extends BaseProcedure {

  private static final ProcedureParameter[] PARAMETERS =
      new ProcedureParameter[] {
        ProcedureParameter.required("table", DataTypes.StringType),
        ProcedureParameter.required("snapshot_id", DataTypes.LongType)
      };

  private static final StructType OUTPUT_TYPE =
      new StructType(
          new StructField[] {
            new StructField("previous_snapshot_id", DataTypes.LongType, false, Metadata.empty()),
            new StructField("current_snapshot_id", DataTypes.LongType, false, Metadata.empty())
          });

  /** 返回构造本过程的 builder。 */
  public static ProcedureBuilder builder() {
    return new BaseProcedure.Builder<RollbackToSnapshotProcedure>() {
      /** 执行 doBuild 相关操作。 */
      @Override
      public RollbackToSnapshotProcedure doBuild() {
        return new RollbackToSnapshotProcedure(tableCatalog());
      }
    };
  }

  /** 构造过程，绑定所在 catalog。 */
  private RollbackToSnapshotProcedure(TableCatalog tableCatalog) {
    super(tableCatalog);
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

  /**
   * 执行回滚。
   *
   * <p>逻辑：解析表标识符与 snapshot_id；记录回滚前当前快照；调用 {@code
   * manageSnapshots().rollbackTo(snapshotId).commit()}；返回前后快照 ID 行。
   *
   * @param args 输入参数行
   * @return 含前后快照 ID 的单行结果
   */
  @Override
  public InternalRow[] call(InternalRow args) {
    Identifier tableIdent = toIdentifier(args.getString(0), PARAMETERS[0].name());
    long snapshotId = args.getLong(1);

    return modifyIcebergTable(
        tableIdent,
        table -> {
          Snapshot previousSnapshot = table.currentSnapshot();

          table.manageSnapshots().rollbackTo(snapshotId).commit();

          InternalRow outputRow = newInternalRow(previousSnapshot.snapshotId(), snapshotId);
          return new InternalRow[] {outputRow};
        });
  }
  /** 返回描述。 */
  @Override
  public String description() {
    return "RollbackToSnapshotProcedure";
  }
}
