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

import org.apache.iceberg.actions.RewriteManifests;
import org.apache.iceberg.relocated.com.google.common.collect.Iterables;
import org.apache.iceberg.spark.actions.RewriteManifestsSparkAction;
import org.apache.iceberg.spark.actions.SparkActions;
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
 * Spark 存储过程：重写表的清单文件（manifest），合并或拆分清单以优化后续计划性能。
 *
 * <p>所属模块：iceberg-spark（procedures 子包，通过 Spark CALL 语句暴露清单重写能力）。
 *
 * <p>职责：接收表名与可选 use_caching 参数，委托 {@link RewriteManifestsSparkAction} 执行清单重写， 返回重写与新增的清单数量。
 *
 * <p>设计意图：随表演化清单可能变得过多或过大，重写可将其整理为更合理的尺寸分布； 通过 modifyIcebergTable 在修改表的同时失效引用该表的缓存 Spark 计划。
 *
 * <p>上下游关系：由 {@link SparkProcedures} 注册，调用 {@link SparkActions#rewriteManifests}。
 */
class RewriteManifestsProcedure extends BaseProcedure {

  private static final ProcedureParameter[] PARAMETERS =
      new ProcedureParameter[] {
        ProcedureParameter.required("table", DataTypes.StringType),
        ProcedureParameter.optional("use_caching", DataTypes.BooleanType)
      };

  // counts are not nullable since the action result is never null
  private static final StructType OUTPUT_TYPE =
      new StructType(
          new StructField[] {
            new StructField(
                "rewritten_manifests_count", DataTypes.IntegerType, false, Metadata.empty()),
            new StructField("added_manifests_count", DataTypes.IntegerType, false, Metadata.empty())
          });
  /** 执行 builder 相关操作。 */
  public static ProcedureBuilder builder() {
    return new BaseProcedure.Builder<RewriteManifestsProcedure>() {
      /** 执行 doBuild 相关操作。 */
      @Override
      protected RewriteManifestsProcedure doBuild() {
        return new RewriteManifestsProcedure(tableCatalog());
      }
    };
  }

  private RewriteManifestsProcedure(TableCatalog tableCatalog) {
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
   * 执行清单重写。
   *
   * <p>逻辑：解析表名与 use_caching，构造重写动作并按需设置缓存选项，执行后将重写与新增清单数转为输出行。
   *
   * @param args 调用参数行
   * @return 含 rewritten_manifests_count 与 added_manifests_count 的单行结果
   */
  @Override
  public InternalRow[] call(InternalRow args) {
    Identifier tableIdent = toIdentifier(args.getString(0), PARAMETERS[0].name());
    Boolean useCaching = args.isNullAt(1) ? null : args.getBoolean(1);

    return modifyIcebergTable(
        tableIdent,
        table -> {
          RewriteManifestsSparkAction action = actions().rewriteManifests(table);

          if (useCaching != null) {
            action.option(RewriteManifestsSparkAction.USE_CACHING, useCaching.toString());
          }

          RewriteManifests.Result result = action.execute();

          return toOutputRows(result);
        });
  }
  /** 转换为 OutputRows。 */
  private InternalRow[] toOutputRows(RewriteManifests.Result result) {
    int rewrittenManifestsCount = Iterables.size(result.rewrittenManifests());
    int addedManifestsCount = Iterables.size(result.addedManifests());
    InternalRow row = newInternalRow(rewrittenManifestsCount, addedManifestsCount);
    return new InternalRow[] {row};
  }
  /** 返回描述。 */
  @Override
  public String description() {
    return "RewriteManifestsProcedure";
  }
}
