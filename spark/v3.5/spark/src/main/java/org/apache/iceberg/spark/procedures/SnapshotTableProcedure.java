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
import org.apache.iceberg.actions.SnapshotTable;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.spark.actions.SparkActions;
import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.connector.catalog.TableCatalog;
import org.apache.spark.sql.connector.iceberg.catalog.ProcedureParameter;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.Metadata;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;
import scala.runtime.BoxedUnit;

/**
 * 快照表存储过程，为已有表创建 Iceberg 快照（不修改原表）。
 *
 * <p>所属模块：iceberg-spark（Iceberg 与 Spark 3.5 的集成层，procedures 子包）。
 *
 * <p>职责：实现 CALL system.snapshot('source_table', 'table', 'location', properties) 存储过程，通过
 * SparkActions.snapshotTable 为源表创建一个新的 Iceberg 表快照， 新表拥有独立的元数据但共享源表的数据文件。
 *
 * <p>设计意图：与 MigrateTable 不同，snapshot 不修改原表而是创建新表， 适用于在不影响原表的情况下试用 Iceberg。支持自定义表位置和属性。
 * 输出返回导入的数据文件数。
 *
 * <p>上下游关系：通过 SparkProcedures 注册；依赖 SparkActions 的 {@link org.apache.iceberg.actions.SnapshotTable}
 * 实现。
 */
class SnapshotTableProcedure extends BaseProcedure {
  private static final ProcedureParameter[] PARAMETERS =
      new ProcedureParameter[] {
        ProcedureParameter.required("source_table", DataTypes.StringType),
        ProcedureParameter.required("table", DataTypes.StringType),
        ProcedureParameter.optional("location", DataTypes.StringType),
        ProcedureParameter.optional("properties", STRING_MAP)
      };

  private static final StructType OUTPUT_TYPE =
      new StructType(
          new StructField[] {
            new StructField("imported_files_count", DataTypes.LongType, false, Metadata.empty())
          });

  private SnapshotTableProcedure(TableCatalog tableCatalog) {
    super(tableCatalog);
  }
  /** 执行 builder 相关操作。 */
  public static SparkProcedures.ProcedureBuilder builder() {
    return new BaseProcedure.Builder<SnapshotTableProcedure>() {
      /** 执行 doBuild 相关操作。 */
      @Override
      protected SnapshotTableProcedure doBuild() {
        return new SnapshotTableProcedure(tableCatalog());
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
   * 执行快照表操作。
   *
   * <p>逻辑：解析源表名、目标表名、可选的位置和属性，创建 SnapshotTable action 并设置参数后执行。校验源表与目标表名不同。
   *
   * @param args 输入参数行（source_table, table, location, properties）
   * @return 包含导入数据文件数的单行输出
   */
  @Override
  public InternalRow[] call(InternalRow args) {
    String source = args.getString(0);
    Preconditions.checkArgument(
        source != null && !source.isEmpty(),
        "Cannot handle an empty identifier for argument source_table");
    String dest = args.getString(1);
    Preconditions.checkArgument(
        dest != null && !dest.isEmpty(), "Cannot handle an empty identifier for argument table");
    String snapshotLocation = args.isNullAt(2) ? null : args.getString(2);

    Map<String, String> properties = Maps.newHashMap();
    if (!args.isNullAt(3)) {
      args.getMap(3)
          .foreach(
              DataTypes.StringType,
              DataTypes.StringType,
              (k, v) -> {
                properties.put(k.toString(), v.toString());
                return BoxedUnit.UNIT;
              });
    }

    Preconditions.checkArgument(
        !source.equals(dest),
        "Cannot create a snapshot with the same name as the source of the snapshot.");
    SnapshotTable action = SparkActions.get().snapshotTable(source).as(dest);

    if (snapshotLocation != null) {
      action.tableLocation(snapshotLocation);
    }

    SnapshotTable.Result result = action.tableProperties(properties).execute();
    return new InternalRow[] {newInternalRow(result.importedDataFilesCount())};
  }
  /** 返回描述。 */
  @Override
  public String description() {
    return "SnapshotTableProcedure";
  }
}
