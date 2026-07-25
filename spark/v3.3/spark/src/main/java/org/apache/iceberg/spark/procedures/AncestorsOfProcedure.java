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
 * Iceberg 存储过程，通过 Spark SQL CALL 调用，封装为可通过 SQL CALL 调用的存储过程。
 *
 * <p>所属模块：iceberg-spark v3.3。 类型：类 AncestorsOfProcedure。
 *
 * <p>上下游：由 SparkSessionProcedures 注册，被 Spark SQL CALL 语句调用。
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

  /** 构造 AncestorsOfProcedure 实例。 */
  private AncestorsOfProcedure(TableCatalog tableCatalog) {
    super(tableCatalog);
  }

  /** 构造并返回目标对象。 */
  public static SparkProcedures.ProcedureBuilder builder() {
    return new Builder<AncestorsOfProcedure>() {
      /** 执行该方法的具体逻辑。 */
      @Override
      protected AncestorsOfProcedure doBuild() {
        /** 执行该方法的具体逻辑。 */
        return new AncestorsOfProcedure(tableCatalog());
      }
    };
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  @Override
  public ProcedureParameter[] parameters() {
    return PARAMETERS;
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  @Override
  public StructType outputType() {
    return OUTPUT_TYPE;
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @param args 参数
   * @return 结果对象
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

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  @Override
  public String description() {
    return "AncestorsOf";
  }

  /** 转换为outputrow。 */
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
