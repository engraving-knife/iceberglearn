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
import org.apache.iceberg.Table;
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
 * 重写位置删除文件的存储过程。
 *
 * <p>所属模块：iceberg-spark（Iceberg 与 Spark 3.5 的集成层，procedures 子包）。
 *
 * <p>职责：实现 CALL system.rewrite_position_delete_files('table', options, 'where') 存储过程，通过
 * SparkActions.rewritePositionDeletes 将位置删除文件合并重写， 优化读取性能。支持可选的过滤条件（where）和配置选项（options）。
 *
 * <p>设计意图：继承 BaseProcedure 复用表加载、缓存刷新和过滤表达式转换逻辑。 输出包含重写/新增的删除文件数和字节数，便于评估重写效果。
 *
 * <p>上下游关系：通过 SparkProcedures 注册；依赖 SparkActions 的 {@link
 * org.apache.iceberg.actions.RewritePositionDeleteFiles} 实现； 底层使用
 * SparkBinPackPositionDeletesRewriter 执行实际重写。
 *
 * @see org.apache.iceberg.spark.actions.SparkActions#rewritePositionDeletes(Table)
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

  /**
   * 执行位置删除文件重写操作。
   *
   * <p>逻辑：解析输入参数（表名、选项、where 条件），创建 RewritePositionDeleteFiles action 并设置选项和过滤条件，执行后返回重写统计结果。
   *
   * @param args 输入参数行（table, options, where）
   * @return 包含重写/新增删除文件数和字节数的单行输出
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
