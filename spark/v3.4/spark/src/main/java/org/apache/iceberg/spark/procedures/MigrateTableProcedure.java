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
import org.apache.iceberg.actions.MigrateTable;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.spark.actions.MigrateTableSparkAction;
import org.apache.iceberg.spark.actions.SparkActions;
import org.apache.iceberg.spark.procedures.SparkProcedures.ProcedureBuilder;
import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.connector.catalog.TableCatalog;
import org.apache.spark.sql.connector.iceberg.catalog.ProcedureParameter;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.Metadata;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;
import scala.runtime.BoxedUnit;

/**
 * 所属模块：iceberg-spark v3.4
 *
 * <p>职责：迁移表的存储过程，将外部表就地迁移为 Iceberg 表。
 *
 * <p>设计意图：委托 MigrateTableSparkAction 执行无拷贝迁移。
 *
 * <p>上下游关系：由 SparkProcedures 注册；由 CALL 语句经 CallExec 调用。
 */
class MigrateTableProcedure extends BaseProcedure {
  private static final ProcedureParameter[] PARAMETERS =
      new ProcedureParameter[] {
        ProcedureParameter.required("table", DataTypes.StringType),
        ProcedureParameter.optional("properties", STRING_MAP),
        ProcedureParameter.optional("drop_backup", DataTypes.BooleanType),
        ProcedureParameter.optional("backup_table_name", DataTypes.StringType)
      };

  private static final StructType OUTPUT_TYPE =
      new StructType(
          new StructField[] {
            new StructField("migrated_files_count", DataTypes.LongType, false, Metadata.empty())
          });

  private MigrateTableProcedure(TableCatalog tableCatalog) {
    super(tableCatalog);
  }
  /** 执行 builder 相关操作。 */
  public static ProcedureBuilder builder() {
    return new BaseProcedure.Builder<MigrateTableProcedure>() {
      /** 执行 doBuild 相关操作。 */
      @Override
      protected MigrateTableProcedure doBuild() {
        return new MigrateTableProcedure(tableCatalog());
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
  /** 执行过程并返回结果行。 */
  @Override
  public InternalRow[] call(InternalRow args) {
    String tableName = args.getString(0);
    Preconditions.checkArgument(
        tableName != null && !tableName.isEmpty(),
        "Cannot handle an empty identifier for argument table");

    Map<String, String> properties = Maps.newHashMap();
    if (!args.isNullAt(1)) {
      args.getMap(1)
          .foreach(
              DataTypes.StringType,
              DataTypes.StringType,
              (k, v) -> {
                properties.put(k.toString(), v.toString());
                return BoxedUnit.UNIT;
              });
    }

    boolean dropBackup = args.isNullAt(2) ? false : args.getBoolean(2);
    String backupTableName = args.isNullAt(3) ? null : args.getString(3);

    MigrateTableSparkAction migrateTableSparkAction =
        SparkActions.get().migrateTable(tableName).tableProperties(properties);

    if (dropBackup) {
      migrateTableSparkAction = migrateTableSparkAction.dropBackup();
    }

    if (backupTableName != null) {
      migrateTableSparkAction = migrateTableSparkAction.backupTableName(backupTableName);
    }

    MigrateTable.Result result = migrateTableSparkAction.execute();
    return new InternalRow[] {newInternalRow(result.migratedDataFilesCount())};
  }
  /** 返回描述。 */
  @Override
  public String description() {
    return "MigrateTableProcedure";
  }
}
