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

import java.util.List;
import org.apache.iceberg.Table;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.spark.source.SparkTable;
import org.apache.iceberg.util.SnapshotUtil;
import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.connector.catalog.Identifier;
import org.apache.spark.sql.connector.catalog.TableCatalog;
import org.apache.spark.sql.connector.iceberg.catalog.ProcedureParameter;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.Metadata;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;

/**
 * Spark 存储过程：查询指定快照的所有祖先快照（含其本身）的 ID 与时间戳。
 *
 * <p>所属模块：iceberg-spark（procedures 子包，通过 Spark CALL 语句暴露表维护与查询能力）。
 *
 * <p>职责：接收表名与可选快照 ID，返回从该快照到表根的所有祖先快照列表， 每条记录包含快照 ID 与提交时间戳。未指定快照 ID 时默认使用当前快照。
 *
 * <p>设计意图：以存储过程形式暴露 Iceberg 快照谱系查询能力，结果以行集合返回便于在 SQL 中使用。 继承 {@link BaseProcedure} 复用参数解析与表加载逻辑。
 *
 * <p>上下游关系：由 {@link SparkProcedures} 注册，经 ResolveProcedures 规则绑定后调用， 内部使用 {@link
 * SnapshotUtil#ancestorIdsBetween} 计算祖先。
 */
public class AncestorsOfProcedure extends BaseProcedure {

  private static final ProcedureParameter TABLE_PARAM =
      ProcedureParameter.required("table", DataTypes.StringType);
  private static final ProcedureParameter SNAPSHOT_ID_PARAM =
      ProcedureParameter.optional("snapshot_id", DataTypes.LongType);

  private static final ProcedureParameter[] PARAMETERS =
      new ProcedureParameter[] {TABLE_PARAM, SNAPSHOT_ID_PARAM};

  private static final StructType OUTPUT_TYPE =
      new StructType(
          new StructField[] {
            new StructField("snapshot_id", DataTypes.LongType, true, Metadata.empty()),
            new StructField("timestamp", DataTypes.LongType, true, Metadata.empty())
          });

  private AncestorsOfProcedure(TableCatalog tableCatalog) {
    super(tableCatalog);
  }
  /** 执行 builder 相关操作。 */
  public static SparkProcedures.ProcedureBuilder builder() {
    return new Builder<AncestorsOfProcedure>() {
      /** 执行 doBuild 相关操作。 */
      @Override
      protected AncestorsOfProcedure doBuild() {
        return new AncestorsOfProcedure(tableCatalog());
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

  /**
   * 执行祖先快照查询。
   *
   * <p>逻辑：解析表名与可选快照 ID，加载表；快照 ID 为空时取当前快照； 通过 {@link SnapshotUtil#ancestorIdsBetween} 收集祖先快照
   * ID，转换为输出行。
   *
   * @param args 调用参数行
   * @return 祖先快照信息行数组（snapshot_id, timestamp）
   */
  @Override
  public InternalRow[] call(InternalRow args) {
    ProcedureInput input = new ProcedureInput(spark(), tableCatalog(), PARAMETERS, args);

    Identifier tableIdent = input.ident(TABLE_PARAM);
    Long toSnapshotId = input.asLong(SNAPSHOT_ID_PARAM, null);

    SparkTable sparkTable = loadSparkTable(tableIdent);
    Table icebergTable = sparkTable.table();

    if (toSnapshotId == null) {
      toSnapshotId =
          icebergTable.currentSnapshot() != null ? icebergTable.currentSnapshot().snapshotId() : -1;
    }

    List<Long> snapshotIds =
        Lists.newArrayList(
            SnapshotUtil.ancestorIdsBetween(toSnapshotId, null, icebergTable::snapshot));

    return toOutputRow(icebergTable, snapshotIds);
  }
  /** 返回描述。 */
  @Override
  public String description() {
    return "AncestorsOf";
  }
  /** 转换为 OutputRow。 */
  private InternalRow[] toOutputRow(Table table, List<Long> snapshotIds) {
    if (snapshotIds.isEmpty()) {
      return new InternalRow[0];
    }

    InternalRow[] internalRows = new InternalRow[snapshotIds.size()];
    for (int i = 0; i < snapshotIds.size(); i++) {
      Long snapshotId = snapshotIds.get(i);
      internalRows[i] = newInternalRow(snapshotId, table.snapshot(snapshotId).timestampMillis());
    }

    return internalRows;
  }
}
