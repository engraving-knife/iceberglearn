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

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import org.apache.iceberg.MetadataColumns;
import org.apache.iceberg.Table;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.apache.iceberg.spark.ChangelogIterator;
import org.apache.iceberg.spark.source.SparkChangelogTable;
import org.apache.spark.api.java.function.MapPartitionsFunction;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Encoders;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.connector.catalog.Identifier;
import org.apache.spark.sql.connector.catalog.TableCatalog;
import org.apache.spark.sql.connector.iceberg.catalog.ProcedureParameter;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.Metadata;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;
import org.apache.spark.unsafe.types.UTF8String;

/**
 * 为变更行创建临时视图的存储过程。
 *
 * <p>所属模块：iceberg-spark（procedures 子包）。通过 {@code CALL system.create_changelog_view} 基于变更日志表生成 Spark
 * 临时视图，默认移除 carry-over 行；可选计算更新前后镜像。
 *
 * <p>Carry-over 行：copy-on-write 机制下，删除某行需重写整个文件，导致同行的"删除+插入" 出现在变更日志中但并非真实变更，本过程默认将其移除（可通过
 * remove_carryovers=false 保留）。
 *
 * <p>更新前后镜像：由一对相同标识列的删除行与插入行转换而来（UPDATE_BEFORE/UPDATE_AFTER）。 标识列可来自表 schema 的 identifier
 * fields，或作为过程参数传入；可通过 compute_updates=true 启用。
 *
 * <p>设计意图：把变更日志的 carry-over 清理与更新镜像计算封装为可复用视图，简化下游 CDC 消费。
 *
 * <p>上下游关系：由 {@link SparkProcedures} 注册；依赖 {@link ChangelogIterator} 做行级处理， 读取 {@link
 * SparkChangelogTable}。
 */
public class CreateChangelogViewProcedure extends BaseProcedure {

  private static final ProcedureParameter TABLE_PARAM =
      ProcedureParameter.required("table", DataTypes.StringType);
  private static final ProcedureParameter CHANGELOG_VIEW_PARAM =
      ProcedureParameter.optional("changelog_view", DataTypes.StringType);
  private static final ProcedureParameter OPTIONS_PARAM =
      ProcedureParameter.optional("options", STRING_MAP);
  private static final ProcedureParameter COMPUTE_UPDATES_PARAM =
      ProcedureParameter.optional("compute_updates", DataTypes.BooleanType);
  private static final ProcedureParameter IDENTIFIER_COLUMNS_PARAM =
      ProcedureParameter.optional("identifier_columns", STRING_ARRAY);
  private static final ProcedureParameter NET_CHANGES =
      ProcedureParameter.optional("net_changes", DataTypes.BooleanType);

  private static final ProcedureParameter[] PARAMETERS =
      new ProcedureParameter[] {
        TABLE_PARAM,
        CHANGELOG_VIEW_PARAM,
        OPTIONS_PARAM,
        COMPUTE_UPDATES_PARAM,
        IDENTIFIER_COLUMNS_PARAM,
        NET_CHANGES,
      };

  private static final StructType OUTPUT_TYPE =
      new StructType(
          new StructField[] {
            new StructField("changelog_view", DataTypes.StringType, false, Metadata.empty())
          });

  /** 返回本过程构建器。 */
  public static SparkProcedures.ProcedureBuilder builder() {
    return new BaseProcedure.Builder<CreateChangelogViewProcedure>() {
      /** 执行 doBuild 相关操作。 */
      @Override
      protected CreateChangelogViewProcedure doBuild() {
        return new CreateChangelogViewProcedure(tableCatalog());
      }
    };
  }

  /** 以所属 Catalog 构造。 */
  private CreateChangelogViewProcedure(TableCatalog tableCatalog) {
    super(tableCatalog);
  }

  /** 返回参数定义（table、changelog_view、options、compute_updates、identifier_columns、net_changes）。 */
  @Override
  public ProcedureParameter[] parameters() {
    return PARAMETERS;
  }

  /** 返回输出结构（视图名）。 */
  @Override
  public StructType outputType() {
    return OUTPUT_TYPE;
  }

  /**
   * 执行变更视图创建。
   *
   * <p>逻辑：解析表标识，加载变更日志表行；若需计算更新镜像（且非 net changes）则 {@link #computeUpdateImages}，否则 {@link
   * #removeCarryoverRows}；最后创建/替换临时视图并返回视图名。
   */
  @Override
  public InternalRow[] call(InternalRow args) {
    ProcedureInput input = new ProcedureInput(spark(), tableCatalog(), PARAMETERS, args);

    Identifier tableIdent = input.ident(TABLE_PARAM);

    // load insert and deletes from the changelog table
    Identifier changelogTableIdent = changelogTableIdent(tableIdent);
    Dataset<Row> df = loadRows(changelogTableIdent, options(input));

    boolean netChanges = input.asBoolean(NET_CHANGES, false);

    if (shouldComputeUpdateImages(input)) {
      Preconditions.checkArgument(!netChanges, "Not support net changes with update images");
      df = computeUpdateImages(identifierColumns(input, tableIdent), df);
    } else {
      df = removeCarryoverRows(df, netChanges);
    }

    String viewName = viewName(input, tableIdent.name());

    df.createOrReplaceTempView(viewName);

    return toOutputRows(viewName);
  }

  /** 按标识列与变更序号 repartition 后应用 {@link ChangelogIterator#computeUpdates} 计算更新镜像。 */
  private Dataset<Row> computeUpdateImages(String[] identifierColumns, Dataset<Row> df) {
    Preconditions.checkArgument(
        identifierColumns.length > 0,
        "Cannot compute the update images because identifier columns are not set");

    Column[] repartitionSpec = new Column[identifierColumns.length + 1];
    for (int i = 0; i < identifierColumns.length; i++) {
      repartitionSpec[i] = df.col(identifierColumns[i]);
    }

    repartitionSpec[repartitionSpec.length - 1] = df.col(MetadataColumns.CHANGE_ORDINAL.name());

    return applyChangelogIterator(df, repartitionSpec);
  }

  /** 是否计算更新镜像：默认当 identifier_columns 已提供时为 true，可被 compute_updates 覆盖。 */
  private boolean shouldComputeUpdateImages(ProcedureInput input) {
    // If the identifier columns are set, we compute pre/post update images by default.
    boolean defaultValue = input.isProvided(IDENTIFIER_COLUMNS_PARAM);
    return input.asBoolean(COMPUTE_UPDATES_PARAM, defaultValue);
  }

  /** 移除 carry-over 行：net changes 模式额外保留部分元数据列，应用对应清理迭代器。 */
  private Dataset<Row> removeCarryoverRows(Dataset<Row> df, boolean netChanges) {
    Predicate<String> columnsToKeep;
    if (netChanges) {
      Set<String> metadataColumn =
          Sets.newHashSet(
              MetadataColumns.CHANGE_TYPE.name(),
              MetadataColumns.CHANGE_ORDINAL.name(),
              MetadataColumns.COMMIT_SNAPSHOT_ID.name());

      columnsToKeep = column -> !metadataColumn.contains(column);
    } else {
      columnsToKeep = column -> !column.equals(MetadataColumns.CHANGE_TYPE.name());
    }

    Column[] repartitionSpec =
        Arrays.stream(df.columns()).filter(columnsToKeep).map(df::col).toArray(Column[]::new);
    return applyCarryoverRemoveIterator(df, repartitionSpec, netChanges);
  }

  /** 取标识列：优先过程参数，否则取表 schema 的 identifier 字段。 */
  private String[] identifierColumns(ProcedureInput input, Identifier tableIdent) {
    if (input.isProvided(IDENTIFIER_COLUMNS_PARAM)) {
      return input.asStringArray(IDENTIFIER_COLUMNS_PARAM);
    } else {
      Table table = loadSparkTable(tableIdent).table();
      return table.schema().identifierFieldNames().toArray(new String[0]);
    }
  }

  /** 构造变更日志表标识（表名后追加 SparkChangelogTable.TABLE_NAME）。 */
  private Identifier changelogTableIdent(Identifier tableIdent) {
    List<String> namespace = Lists.newArrayList();
    namespace.addAll(Arrays.asList(tableIdent.namespace()));
    namespace.add(tableIdent.name());
    return Identifier.of(namespace.toArray(new String[0]), SparkChangelogTable.TABLE_NAME);
  }

  /** 读取 options 参数，默认空 map。 */
  private Map<String, String> options(ProcedureInput input) {
    return input.asStringMap(OPTIONS_PARAM, ImmutableMap.of());
  }

  /** 生成视图名：默认 {@code `<表名>_changes`}，可被 changelog_view 参数覆盖。 */
  private String viewName(ProcedureInput input, String tableName) {
    String defaultValue = String.format("`%s_changes`", tableName);
    return input.asString(CHANGELOG_VIEW_PARAM, defaultValue);
  }

  /** repartition + 分区内排序后用 computeUpdates 迭代器处理分区。 */
  private Dataset<Row> applyChangelogIterator(Dataset<Row> df, Column[] repartitionSpec) {
    Column[] sortSpec = sortSpec(df, repartitionSpec, false);
    StructType schema = df.schema();
    String[] identifierFields =
        Arrays.stream(repartitionSpec).map(Column::toString).toArray(String[]::new);

    return df.repartition(repartitionSpec)
        .sortWithinPartitions(sortSpec)
        .mapPartitions(
            (MapPartitionsFunction<Row, Row>)
                rowIterator ->
                    ChangelogIterator.computeUpdates(rowIterator, schema, identifierFields),
            Encoders.row(schema));
  }

  /** repartition + 分区内排序后用 removeNetCarryovers 或 removeCarryovers 迭代器处理分区。 */
  private Dataset<Row> applyCarryoverRemoveIterator(
      Dataset<Row> df, Column[] repartitionSpec, boolean netChanges) {
    Column[] sortSpec = sortSpec(df, repartitionSpec, netChanges);
    StructType schema = df.schema();

    return df.repartition(repartitionSpec)
        .sortWithinPartitions(sortSpec)
        .mapPartitions(
            (MapPartitionsFunction<Row, Row>)
                rowIterator ->
                    netChanges
                        ? ChangelogIterator.removeNetCarryovers(rowIterator, schema)
                        : ChangelogIterator.removeCarryovers(rowIterator, schema),
            Encoders.row(schema));
  }

  /** 构造分区内排序规格：repartitionSpec 后追加 change_type（net changes 时含 change_ordinal）。 */
  private static Column[] sortSpec(Dataset<Row> df, Column[] repartitionSpec, boolean netChanges) {
    Column changeType = df.col(MetadataColumns.CHANGE_TYPE.name());
    Column changeOrdinal = df.col(MetadataColumns.CHANGE_ORDINAL.name());
    Column[] extraColumns =
        netChanges ? new Column[] {changeOrdinal, changeType} : new Column[] {changeType};

    Column[] sortSpec = new Column[repartitionSpec.length + extraColumns.length];

    System.arraycopy(repartitionSpec, 0, sortSpec, 0, repartitionSpec.length);
    System.arraycopy(extraColumns, 0, sortSpec, repartitionSpec.length, extraColumns.length);

    return sortSpec;
  }

  /** 构造输出行（视图名）。 */
  private InternalRow[] toOutputRows(String viewName) {
    InternalRow row = newInternalRow(UTF8String.fromString(viewName));
    return new InternalRow[] {row};
  }
  /** 返回描述。 */
  @Override
  public String description() {
    return "CreateChangelogViewProcedure";
  }
}
