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

import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.connector.catalog.Identifier;
import org.apache.spark.sql.connector.catalog.TableCatalog;
import org.apache.spark.sql.connector.iceberg.catalog.ProcedureParameter;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.Metadata;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;
import org.apache.spark.unsafe.types.UTF8String;

/**
 * 所属模块：iceberg-spark v3.4
 *
 * <p>职责：快进分支的存储过程，将目标分支快进到源分支的最新快照。
 *
 * <p>设计意图：通过 Iceberg fast-forward 操作更新分支指针，要求源为祖先。
 *
 * <p>上下游关系：由 SparkProcedures 注册；由 CALL 语句经 CallExec 调用。
 */
public class FastForwardBranchProcedure extends BaseProcedure {

  private static final ProcedureParameter[] PARAMETERS =
      new ProcedureParameter[] {
        ProcedureParameter.required("table", DataTypes.StringType),
        ProcedureParameter.required("branch", DataTypes.StringType),
        ProcedureParameter.required("to", DataTypes.StringType)
      };

  private static final StructType OUTPUT_TYPE =
      new StructType(
          new StructField[] {
            new StructField("branch_updated", DataTypes.StringType, false, Metadata.empty()),
            new StructField("previous_ref", DataTypes.LongType, true, Metadata.empty()),
            new StructField("updated_ref", DataTypes.LongType, false, Metadata.empty())
          });
  /** 执行 builder 相关操作。 */
  public static SparkProcedures.ProcedureBuilder builder() {
    return new Builder<FastForwardBranchProcedure>() {
      /** 执行 doBuild 相关操作。 */
      @Override
      protected FastForwardBranchProcedure doBuild() {
        return new FastForwardBranchProcedure(tableCatalog());
      }
    };
  }

  private FastForwardBranchProcedure(TableCatalog tableCatalog) {
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
  /** 执行过程并返回结果行。 */
  @Override
  public InternalRow[] call(InternalRow args) {
    Identifier tableIdent = toIdentifier(args.getString(0), PARAMETERS[0].name());
    String source = args.getString(1);
    String target = args.getString(2);

    return modifyIcebergTable(
        tableIdent,
        table -> {
          long currentRef = table.currentSnapshot().snapshotId();
          table.manageSnapshots().fastForwardBranch(source, target).commit();
          long updatedRef = table.currentSnapshot().snapshotId();

          InternalRow outputRow =
              newInternalRow(UTF8String.fromString(source), currentRef, updatedRef);
          return new InternalRow[] {outputRow};
        });
  }
  /** 返回描述。 */
  @Override
  public String description() {
    return "FastForwardBranchProcedure";
  }
}
