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
 * Iceberg 存储过程，通过 Spark SQL CALL 调用，封装为可通过 SQL CALL 调用的存储过程。
 *
 * <p>所属模块：iceberg-spark v3.2。 类型：类 RewriteManifestsProcedure。
 *
 * <p>设计意图：Catalyst 规则，通过 transformation 介入计划处理。
 *
 * <p>上下游：由 SparkSessionProcedures 注册，被 Spark SQL CALL 语句调用。
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

  /** 构造并返回目标对象。 */
  public static ProcedureBuilder builder() {
    return new BaseProcedure.Builder<RewriteManifestsProcedure>() {
      /** 执行该方法的具体逻辑。 */
      @Override
      protected RewriteManifestsProcedure doBuild() {
        /** 重写计划或文件。 */
        return new RewriteManifestsProcedure(tableCatalog());
      }
    };
  }

  /** 构造 RewriteManifestsProcedure 实例。 */
  private RewriteManifestsProcedure(TableCatalog tableCatalog) {
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

  /** 转换为outputrows。 */
  private InternalRow[] toOutputRows(RewriteManifests.Result result) {
    int rewrittenManifestsCount = Iterables.size(result.rewrittenManifests());
    int addedManifestsCount = Iterables.size(result.addedManifests());
    InternalRow row = newInternalRow(rewrittenManifestsCount, addedManifestsCount);
    return new InternalRow[] {row};
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  @Override
  public String description() {
    return "RewriteManifestsProcedure";
  }
}
