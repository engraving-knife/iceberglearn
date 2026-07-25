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

import java.util.Map;
import org.apache.iceberg.actions.RewritePositionDeleteFiles;
import org.apache.iceberg.actions.RewritePositionDeleteFiles.Result;
import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
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
 * <p>职责：重写位置删除文件的存储过程，合并分散的删除文件。
 *
 * <p>设计意图：委托 RewritePositionDeleteFilesSparkAction 执行。
 *
 * <p>上下游关系：由 SparkProcedures 注册；由 CALL 语句经 CallExec 调用。
 */
public class RewritePositionDeleteFilesProcedure extends BaseProcedure {

  private static final ProcedureParameter TABLE_PARAM =
      ProcedureParameter.required("table", DataTypes.StringType);
  private static final ProcedureParameter OPTIONS_PARAM =
      ProcedureParameter.optional("options", STRING_MAP);
  private static final ProcedureParameter WHERE_PARAM =
      ProcedureParameter.optional("where", DataTypes.StringType);

  private static final ProcedureParameter[] PARAMETERS =
      new ProcedureParameter[] {TABLE_PARAM, OPTIONS_PARAM, WHERE_PARAM};

  private static final StructType OUTPUT_TYPE =
      new StructType(
          new StructField[] {
            new StructField(
                "rewritten_delete_files_count", DataTypes.IntegerType, false, Metadata.empty()),
            new StructField(
                "added_delete_files_count", DataTypes.IntegerType, false, Metadata.empty()),
            new StructField("rewritten_bytes_count", DataTypes.LongType, false, Metadata.empty()),
            new StructField("added_bytes_count", DataTypes.LongType, false, Metadata.empty())
          });
  /** 执行 builder 相关操作。 */
  public static SparkProcedures.ProcedureBuilder builder() {
    return new Builder<RewritePositionDeleteFilesProcedure>() {
      /** 执行 doBuild 相关操作。 */
      @Override
      protected RewritePositionDeleteFilesProcedure doBuild() {
        return new RewritePositionDeleteFilesProcedure(tableCatalog());
      }
    };
  }

  private RewritePositionDeleteFilesProcedure(TableCatalog tableCatalog) {
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
    ProcedureInput input = new ProcedureInput(spark(), tableCatalog(), PARAMETERS, args);
    Identifier tableIdent = input.ident(TABLE_PARAM);
    Map<String, String> options = input.asStringMap(OPTIONS_PARAM, ImmutableMap.of());
    String where = input.asString(WHERE_PARAM, null);

    return modifyIcebergTable(
        tableIdent,
        table -> {
          RewritePositionDeleteFiles action =
              actions().rewritePositionDeletes(table).options(options);

          if (where != null) {
            Expression whereExpression = filterExpression(tableIdent, where);
            action = action.filter(whereExpression);
          }

          Result result = action.execute();
          return new InternalRow[] {toOutputRow(result)};
        });
  }
  /** 转换为 OutputRow。 */
  private InternalRow toOutputRow(Result result) {
    return newInternalRow(
        result.rewrittenDeleteFilesCount(),
        result.addedDeleteFilesCount(),
        result.rewrittenBytesCount(),
        result.addedBytesCount());
  }
  /** 返回描述。 */
  @Override
  public String description() {
    return "RewritePositionDeleteFilesProcedure";
  }
}
