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
 * Iceberg 存储过程，通过 Spark SQL CALL 调用，封装为可通过 SQL CALL 调用的存储过程。
 *
 * <p>所属模块：iceberg-spark v3.3。 类型：类 RewritePositionDeleteFilesProcedure。
 *
 * <p>设计意图：Catalyst 规则，通过 transformation 介入计划处理。
 *
 * <p>上下游：由 SparkSessionProcedures 注册，被 Spark SQL CALL 语句调用。
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

  /** 构造并返回目标对象。 */
  public static SparkProcedures.ProcedureBuilder builder() {
    return new Builder<RewritePositionDeleteFilesProcedure>() {
      /** 执行该方法的具体逻辑。 */
      @Override
      protected RewritePositionDeleteFilesProcedure doBuild() {
        /** 重写计划或文件。 */
        return new RewritePositionDeleteFilesProcedure(tableCatalog());
      }
    };
  }

  /** 构造 RewritePositionDeleteFilesProcedure 实例。 */
  private RewritePositionDeleteFilesProcedure(TableCatalog tableCatalog) {
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

  /** 转换为outputrow。 */
  private InternalRow toOutputRow(Result result) {
    return newInternalRow(
        result.rewrittenDeleteFilesCount(),
        result.addedDeleteFilesCount(),
        result.rewrittenBytesCount(),
        result.addedBytesCount());
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  @Override
  public String description() {
    return "RewritePositionDeleteFilesProcedure";
  }
}
