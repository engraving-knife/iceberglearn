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
import org.apache.iceberg.Table;
import org.apache.iceberg.actions.DeleteOrphanFiles;
import org.apache.iceberg.actions.DeleteOrphanFiles.PrefixMismatchMode;
import org.apache.iceberg.io.SupportsBulkOperations;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Iterables;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.spark.actions.DeleteOrphanFilesSparkAction;
import org.apache.iceberg.spark.actions.SparkActions;
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
 * Spark 存储过程：清理表中的孤儿文件（orphan files）。
 *
 * <p>所属模块：iceberg-spark（Spark v3.5 集成模块），procedures 子包。对应 {@code system.remove_orphan_files(table
 * => '...', older_than => ..., ...)}。
 *
 * <p>职责：委托 {@link DeleteOrphanFilesSparkAction} 找出并删除不被任何快照/元数据引用的文件， 支持按时间阈值、自定义
 * location、dry_run、并发删除数、文件列表视图对比、URI scheme/authority 等价映射、 前缀不匹配模式等选项。返回孤儿文件路径列表。
 *
 * <p>设计意图：作为薄包装层把过程参数转换为 action 链式调用；older_than 在非测试环境下强制 >= 24 小时， 防止误删正在写入的文件；支持 bulk IO
 * 时忽略并发删除参数。
 *
 * <p>上下游关系：由 {@link SparkProcedures} 注册；依赖 {@link SparkActions#deleteOrphanFiles}。
 *
 * @see SparkActions#deleteOrphanFiles(Table)
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

  /** 返回构造本过程的 builder。 */
  public static ProcedureBuilder builder() {
    return new BaseProcedure.Builder<RemoveOrphanFilesProcedure>() {
      /** 执行 doBuild 相关操作。 */
      @Override
      protected RemoveOrphanFilesProcedure doBuild() {
        return new RemoveOrphanFilesProcedure(tableCatalog());
      }
    };
  }

  /** 构造过程，绑定所在 catalog。 */
  private RemoveOrphanFilesProcedure(TableCatalog catalog) {
    super(catalog);
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
   * 执行孤儿文件清理。
   *
   * <p>逻辑：解析表标识符与各可选参数；构造 DeleteOrphanFilesSparkAction； 按参数配置 older_than（非测试校验 >=
   * 24h）、location、dry_run（注入空删除函数）、 max_concurrent_deletes（bulk IO 时告警忽略）、file_list_view
   * 对比、equal_schemes/authorities、 prefix_mismatch_mode；执行并返回孤儿文件路径行数组。
   *
   * @param args 输入参数行
   * @return 孤儿文件路径列表
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

  /** 把结果中的孤儿文件路径可迭代对象转为 InternalRow 数组。 */
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

  /** 校验 older_than 与当前时间间隔不小于 24 小时，防止误删正在写入的文件。 */
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
  /** 返回描述。 */
  @Override
  public String description() {
    return "RemoveOrphanFilesProcedure";
  }
}
