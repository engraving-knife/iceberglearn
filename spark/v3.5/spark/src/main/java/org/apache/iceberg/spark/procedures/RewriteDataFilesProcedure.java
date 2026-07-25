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

import java.util.List;
import java.util.Map;
import org.apache.iceberg.Schema;
import org.apache.iceberg.SortOrder;
import org.apache.iceberg.Table;
import org.apache.iceberg.actions.RewriteDataFiles;
import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.expressions.NamedReference;
import org.apache.iceberg.expressions.Zorder;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.spark.ExtendedParser;
import org.apache.iceberg.spark.procedures.SparkProcedures.ProcedureBuilder;
import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.connector.catalog.Identifier;
import org.apache.spark.sql.connector.catalog.TableCatalog;
import org.apache.spark.sql.connector.iceberg.catalog.ProcedureParameter;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.Metadata;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;
import scala.runtime.BoxedUnit;

/**
 * 重写表数据文件的存储过程。
 *
 * <p>所属模块：iceberg-spark（procedures 子包）。通过 {@code CALL system.rewrite_data_files} 触发数据文件重写（binpack
 * 合并小文件或 sort/zorder 重排），以优化读取性能。
 *
 * <p>职责：解析表标识，按 strategy/sort_order/options/where 配置 RewriteDataFiles 动作并执行， 返回重写/新增/失败文件数与重写字节数。
 *
 * <p>设计意图：将重写策略选择（binpack/sort/zorder）与过滤、选项配置统一在此过程封装； sort_order 支持 Zorder 与恒等排序，但不允许混用。
 *
 * <p>上下游关系：由 {@link SparkProcedures} 注册；底层调用 {@link
 * org.apache.iceberg.spark.actions.SparkActions#rewriteDataFiles(Table)}。
 */
class RewriteDataFilesProcedure extends BaseProcedure {

  private static final ProcedureParameter[] PARAMETERS =
      new ProcedureParameter[] {
        ProcedureParameter.required("table", DataTypes.StringType),
        ProcedureParameter.optional("strategy", DataTypes.StringType),
        ProcedureParameter.optional("sort_order", DataTypes.StringType),
        ProcedureParameter.optional("options", STRING_MAP),
        ProcedureParameter.optional("where", DataTypes.StringType)
      };

  // counts are not nullable since the action result is never null
  private static final StructType OUTPUT_TYPE =
      new StructType(
          new StructField[] {
            new StructField(
                "rewritten_data_files_count", DataTypes.IntegerType, false, Metadata.empty()),
            new StructField(
                "added_data_files_count", DataTypes.IntegerType, false, Metadata.empty()),
            new StructField("rewritten_bytes_count", DataTypes.LongType, false, Metadata.empty()),
            new StructField(
                "failed_data_files_count", DataTypes.IntegerType, false, Metadata.empty())
          });

  /** 返回本过程构建器。 */
  public static ProcedureBuilder builder() {
    return new Builder<RewriteDataFilesProcedure>() {
      /** 执行 doBuild 相关操作。 */
      @Override
      protected RewriteDataFilesProcedure doBuild() {
        return new RewriteDataFilesProcedure(tableCatalog());
      }
    };
  }

  /** 以所属 Catalog 构造。 */
  private RewriteDataFilesProcedure(TableCatalog tableCatalog) {
    super(tableCatalog);
  }

  /** 返回参数定义（table、strategy、sort_order、options、where）。 */
  @Override
  public ProcedureParameter[] parameters() {
    return PARAMETERS;
  }

  /** 返回输出结构（重写/新增/失败文件数、重写字节数）。 */
  @Override
  public StructType outputType() {
    return OUTPUT_TYPE;
  }

  /**
   * 执行数据文件重写。
   *
   * <p>逻辑：解析表标识，创建 RewriteDataFiles 动作；按需应用策略/排序、选项、过滤；执行后 输出统计行。
   */
  @Override
  public InternalRow[] call(InternalRow args) {
    Identifier tableIdent = toIdentifier(args.getString(0), PARAMETERS[0].name());

    return modifyIcebergTable(
        tableIdent,
        table -> {
          RewriteDataFiles action = actions().rewriteDataFiles(table);

          String strategy = args.isNullAt(1) ? null : args.getString(1);
          String sortOrderString = args.isNullAt(2) ? null : args.getString(2);

          if (strategy != null || sortOrderString != null) {
            action = checkAndApplyStrategy(action, strategy, sortOrderString, table.schema());
          }

          if (!args.isNullAt(3)) {
            action = checkAndApplyOptions(args, action);
          }

          String where = args.isNullAt(4) ? null : args.getString(4);

          action = checkAndApplyFilter(action, where, tableIdent);

          RewriteDataFiles.Result result = action.execute();

          return toOutputRows(result);
        });
  }

  /** 若提供 where 则解析为过滤表达式并应用到动作。 */
  private RewriteDataFiles checkAndApplyFilter(
      RewriteDataFiles action, String where, Identifier ident) {
    if (where != null) {
      Expression expression = filterExpression(ident, where);
      return action.filter(expression);
    }
    return action;
  }

  /** 从实参 map 读取选项并应用到动作。 */
  private RewriteDataFiles checkAndApplyOptions(InternalRow args, RewriteDataFiles action) {
    Map<String, String> options = Maps.newHashMap();
    args.getMap(3)
        .foreach(
            DataTypes.StringType,
            DataTypes.StringType,
            (k, v) -> {
              options.put(k.toString(), v.toString());
              return BoxedUnit.UNIT;
            });
    return action.options(options);
  }

  /**
   * 校验并应用重写策略。
   *
   * <p>逻辑：解析 sort_order，分离 Zorder 与恒等排序项（二者不可混用）；strategy 为 null 或 sort 时按 zorder/sort/默认 sort
   * 处理；binpack 时调用 binPack（带 sort_order 则报错）；其他策略抛异常。
   */
  private RewriteDataFiles checkAndApplyStrategy(
      RewriteDataFiles action, String strategy, String sortOrderString, Schema schema) {
    List<Zorder> zOrderTerms = Lists.newArrayList();
    List<ExtendedParser.RawOrderField> sortOrderFields = Lists.newArrayList();
    if (sortOrderString != null) {
      ExtendedParser.parseSortOrder(spark(), sortOrderString)
          .forEach(
              field -> {
                if (field.term() instanceof Zorder) {
                  zOrderTerms.add((Zorder) field.term());
                } else {
                  sortOrderFields.add(field);
                }
              });

      if (!zOrderTerms.isEmpty() && !sortOrderFields.isEmpty()) {
        // TODO: we need to allow this in future when SparkAction has handling for this.
        throw new IllegalArgumentException(
            "Cannot mix identity sort columns and a Zorder sort expression: " + sortOrderString);
      }
    }

    // caller of this function ensures that between strategy and sortOrder, at least one of them is
    // not null.
    if (strategy == null || strategy.equalsIgnoreCase("sort")) {
      if (!zOrderTerms.isEmpty()) {
        String[] columnNames =
            zOrderTerms.stream()
                .flatMap(zOrder -> zOrder.refs().stream().map(NamedReference::name))
                .toArray(String[]::new);
        return action.zOrder(columnNames);
      } else if (!sortOrderFields.isEmpty()) {
        return action.sort(buildSortOrder(sortOrderFields, schema));
      } else {
        return action.sort();
      }
    }
    if (strategy.equalsIgnoreCase("binpack")) {
      RewriteDataFiles rewriteDataFiles = action.binPack();
      if (sortOrderString != null) {
        // calling below method to throw the error as user has set both binpack strategy and sort
        // order
        return rewriteDataFiles.sort(buildSortOrder(sortOrderFields, schema));
      }
      return rewriteDataFiles;
    } else {
      throw new IllegalArgumentException(
          "unsupported strategy: " + strategy + ". Only binpack or sort is supported");
    }
  }

  /** 由原始排序字段列表与 schema 构建 Iceberg {@link SortOrder}。 */
  private SortOrder buildSortOrder(
      List<ExtendedParser.RawOrderField> rawOrderFields, Schema schema) {
    SortOrder.Builder builder = SortOrder.builderFor(schema);
    rawOrderFields.forEach(
        rawField -> builder.sortBy(rawField.term(), rawField.direction(), rawField.nullOrder()));
    return builder.build();
  }

  /** 由动作结果构造输出行。 */
  private InternalRow[] toOutputRows(RewriteDataFiles.Result result) {
    int rewrittenDataFilesCount = result.rewrittenDataFilesCount();
    long rewrittenBytesCount = result.rewrittenBytesCount();
    int addedDataFilesCount = result.addedDataFilesCount();
    int failedDataFilesCount = result.failedDataFilesCount();

    InternalRow row =
        newInternalRow(
            rewrittenDataFilesCount,
            addedDataFilesCount,
            rewrittenBytesCount,
            failedDataFilesCount);
    return new InternalRow[] {row};
  }
  /** 返回描述。 */
  @Override
  public String description() {
    return "RewriteDataFilesProcedure";
  }
}
