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
 * Iceberg 存储过程，通过 Spark SQL CALL 调用，封装为可通过 SQL CALL 调用的存储过程。
 *
 * <p>所属模块：iceberg-spark v3.3。 类型：类 SnapshotTableProcedure。
 *
 * <p>上下游：由 SparkSessionProcedures 注册，被 Spark SQL CALL 语句调用。
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

  /** 构造 SnapshotTableProcedure 实例。 */
  private SnapshotTableProcedure(TableCatalog tableCatalog) {
    super(tableCatalog);
  }

  /** 构造并返回目标对象。 */
  public static SparkProcedures.ProcedureBuilder builder() {
    return new BaseProcedure.Builder<SnapshotTableProcedure>() {
      /** 执行该方法的具体逻辑。 */
      @Override
      protected SnapshotTableProcedure doBuild() {
        /** 执行该方法的具体逻辑。 */
        return new SnapshotTableProcedure(tableCatalog());
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

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  @Override
  public String description() {
    return "SnapshotTableProcedure";
  }
}
