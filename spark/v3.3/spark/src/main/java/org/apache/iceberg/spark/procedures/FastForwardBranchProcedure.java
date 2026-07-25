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
 * Iceberg 存储过程，通过 Spark SQL CALL 调用，封装为可通过 SQL CALL 调用的存储过程。
 *
 * <p>所属模块：iceberg-spark v3.3。 类型：类 FastForwardBranchProcedure。
 *
 * <p>上下游：由 SparkSessionProcedures 注册，被 Spark SQL CALL 语句调用。
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

  /** 构造并返回目标对象。 */
  public static SparkProcedures.ProcedureBuilder builder() {
    return new Builder<FastForwardBranchProcedure>() {
      /** 执行该方法的具体逻辑。 */
      @Override
      protected FastForwardBranchProcedure doBuild() {
        /** 执行该方法的具体逻辑。 */
        return new FastForwardBranchProcedure(tableCatalog());
      }
    };
  }

  /** 构造 FastForwardBranchProcedure 实例。 */
  private FastForwardBranchProcedure(TableCatalog tableCatalog) {
    super(tableCatalog);
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

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  @Override
  public String description() {
    return "FastForwardBranchProcedure";
  }
}
