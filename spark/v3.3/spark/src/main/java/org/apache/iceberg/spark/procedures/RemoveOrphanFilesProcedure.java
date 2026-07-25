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
import java.util.concurrent.TimeUnit;
import org.apache.iceberg.actions.DeleteOrphanFiles;
import org.apache.iceberg.actions.DeleteOrphanFiles.PrefixMismatchMode;
import org.apache.iceberg.io.SupportsBulkOperations;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Iterables;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.spark.actions.DeleteOrphanFilesSparkAction;
import org.apache.iceberg.spark.procedures.SparkProcedures.ProcedureBuilder;
import org.apache.iceberg.util.DateTimeUtil;
import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.connector.catalog.Identifier;
import org.apache.spark.sql.connector.catalog.TableCatalog;
import org.apache.spark.sql.connector.iceberg.catalog.ProcedureParameter;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.Metadata;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;
import org.apache.spark.unsafe.types.UTF8String;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.runtime.BoxedUnit;

/**
 * Iceberg 存储过程，通过 Spark SQL CALL 调用，封装为可通过 SQL CALL 调用的存储过程。
 *
 * <p>所属模块：iceberg-spark v3.3。 类型：类 RemoveOrphanFilesProcedure。
 *
 * <p>上下游：由 SparkSessionProcedures 注册，被 Spark SQL CALL 语句调用。
 */
public class RemoveOrphanFilesProcedure extends BaseProcedure {
  private static final Logger LOG = LoggerFactory.getLogger(RemoveOrphanFilesProcedure.class);

  private static final ProcedureParameter[] PARAMETERS =
      new ProcedureParameter[] {
        ProcedureParameter.required("table", DataTypes.StringType),
        ProcedureParameter.optional("older_than", DataTypes.TimestampType),
        ProcedureParameter.optional("location", DataTypes.StringType),
        ProcedureParameter.optional("dry_run", DataTypes.BooleanType),
        ProcedureParameter.optional("max_concurrent_deletes", DataTypes.IntegerType),
        ProcedureParameter.optional("file_list_view", DataTypes.StringType),
        ProcedureParameter.optional("equal_schemes", STRING_MAP),
        ProcedureParameter.optional("equal_authorities", STRING_MAP),
        ProcedureParameter.optional("prefix_mismatch_mode", DataTypes.StringType),
      };

  private static final StructType OUTPUT_TYPE =
      new StructType(
          new StructField[] {
            new StructField("orphan_file_location", DataTypes.StringType, false, Metadata.empty())
          });

  /** 构造并返回目标对象。 */
  public static ProcedureBuilder builder() {
    return new BaseProcedure.Builder<RemoveOrphanFilesProcedure>() {
      /** 执行该方法的具体逻辑。 */
      @Override
      protected RemoveOrphanFilesProcedure doBuild() {
        /** 移除元素或项。 */
        return new RemoveOrphanFilesProcedure(tableCatalog());
      }
    };
  }

  /** 构造 RemoveOrphanFilesProcedure 实例。 */
  private RemoveOrphanFilesProcedure(TableCatalog catalog) {
    super(catalog);
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
  @SuppressWarnings("checkstyle:CyclomaticComplexity")
  public InternalRow[] call(InternalRow args) {
    Identifier tableIdent = toIdentifier(args.getString(0), PARAMETERS[0].name());
    Long olderThanMillis = args.isNullAt(1) ? null : DateTimeUtil.microsToMillis(args.getLong(1));
    String location = args.isNullAt(2) ? null : args.getString(2);
    boolean dryRun = args.isNullAt(3) ? false : args.getBoolean(3);
    Integer maxConcurrentDeletes = args.isNullAt(4) ? null : args.getInt(4);
    String fileListView = args.isNullAt(5) ? null : args.getString(5);

    Preconditions.checkArgument(
        maxConcurrentDeletes == null || maxConcurrentDeletes > 0,
        "max_concurrent_deletes should have value > 0, value: %s",
        maxConcurrentDeletes);

    Map<String, String> equalSchemes = Maps.newHashMap();
    if (!args.isNullAt(6)) {
      args.getMap(6)
          .foreach(
              DataTypes.StringType,
              DataTypes.StringType,
              (k, v) -> {
                equalSchemes.put(k.toString(), v.toString());
                return BoxedUnit.UNIT;
              });
    }

    Map<String, String> equalAuthorities = Maps.newHashMap();
    if (!args.isNullAt(7)) {
      args.getMap(7)
          .foreach(
              DataTypes.StringType,
              DataTypes.StringType,
              (k, v) -> {
                equalSchemes.put(k.toString(), v.toString());
                return BoxedUnit.UNIT;
              });
    }

    PrefixMismatchMode prefixMismatchMode =
        args.isNullAt(8) ? null : PrefixMismatchMode.fromString(args.getString(8));

    return withIcebergTable(
        tableIdent,
        table -> {
          DeleteOrphanFilesSparkAction action = actions().deleteOrphanFiles(table);

          if (olderThanMillis != null) {
            boolean isTesting = Boolean.parseBoolean(spark().conf().get("spark.testing", "false"));
            if (!isTesting) {
              validateInterval(olderThanMillis);
            }
            action.olderThan(olderThanMillis);
          }

          if (location != null) {
            action.location(location);
          }

          if (dryRun) {
            action.deleteWith(file -> {});
          }

          if (maxConcurrentDeletes != null) {
            if (table.io() instanceof SupportsBulkOperations) {
              LOG.warn(
                  "max_concurrent_deletes only works with FileIOs that do not support bulk deletes. This"
                      + "table is currently using {} which supports bulk deletes so the parameter will be ignored. "
                      + "See that IO's documentation to learn how to adjust parallelism for that particular "
                      + "IO's bulk delete.",
                  table.io().getClass().getName());
            } else {

              action.executeDeleteWith(executorService(maxConcurrentDeletes, "remove-orphans"));
            }
          }

          if (fileListView != null) {
            action.compareToFileList(spark().table(fileListView));
          }

          action.equalSchemes(equalSchemes);
          action.equalAuthorities(equalAuthorities);

          if (prefixMismatchMode != null) {
            action.prefixMismatchMode(prefixMismatchMode);
          }

          DeleteOrphanFiles.Result result = action.execute();

          return toOutputRows(result);
        });
  }

  /** 转换为outputrows。 */
  private InternalRow[] toOutputRows(DeleteOrphanFiles.Result result) {
    Iterable<String> orphanFileLocations = result.orphanFileLocations();

    int orphanFileLocationsCount = Iterables.size(orphanFileLocations);
    InternalRow[] rows = new InternalRow[orphanFileLocationsCount];

    int index = 0;
    for (String fileLocation : orphanFileLocations) {
      rows[index] = newInternalRow(UTF8String.fromString(fileLocation));
      index++;
    }

    return rows;
  }

  /** 校验前置条件或参数。 */
  private void validateInterval(long olderThanMillis) {
    long intervalMillis = System.currentTimeMillis() - olderThanMillis;
    if (intervalMillis < TimeUnit.DAYS.toMillis(1)) {
      throw new IllegalArgumentException(
          "Cannot remove orphan files with an interval less than 24 hours. Executing this "
              + "procedure with a short interval may corrupt the table if other operations are happening "
              + "at the same time. If you are absolutely confident that no concurrent operations will be "
              + "affected by removing orphan files with such a short interval, you can use the Action API "
              + "to remove orphan files with an arbitrary interval.");
    }
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  @Override
  public String description() {
    return "RemoveOrphanFilesProcedure";
  }
}
