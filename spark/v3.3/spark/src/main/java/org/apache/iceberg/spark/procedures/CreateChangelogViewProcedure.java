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
import org.apache.spark.sql.Row;
import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.catalyst.encoders.RowEncoder;
import org.apache.spark.sql.connector.catalog.Identifier;
import org.apache.spark.sql.connector.catalog.TableCatalog;
import org.apache.spark.sql.connector.iceberg.catalog.ProcedureParameter;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.Metadata;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;
import org.apache.spark.unsafe.types.UTF8String;

/**
 * Iceberg 存储过程，通过 Spark SQL CALL 调用，封装为可通过 SQL CALL 调用的存储过程。
 *
 * <p>所属模块：iceberg-spark v3.3。 类型：类 CreateChangelogViewProcedure。
 *
 * <p>上下游：由 SparkSessionProcedures 注册，被 Spark SQL CALL 语句调用。
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

  /**
   * Enable or disable the remove carry-over rows.
   *
   * @deprecated since 1.4.0, will be removed in 1.5.0; The procedure will always remove carry-over
   *     rows. Please query {@link SparkChangelogTable} instead for the use cases doesn't remove
   *     carry-over rows.
   */
  @Deprecated
  private static final ProcedureParameter REMOVE_CARRYOVERS_PARAM =
      ProcedureParameter.optional("remove_carryovers", DataTypes.BooleanType);

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
        REMOVE_CARRYOVERS_PARAM,
        IDENTIFIER_COLUMNS_PARAM,
        NET_CHANGES,
      };

  private static final StructType OUTPUT_TYPE =
      new StructType(
          new StructField[] {
            new StructField("changelog_view", DataTypes.StringType, false, Metadata.empty())
          });

  /** 构造并返回目标对象。 */
  public static SparkProcedures.ProcedureBuilder builder() {
    return new BaseProcedure.Builder<CreateChangelogViewProcedure>() {
      /** 执行该方法的具体逻辑。 */
      @Override
      protected CreateChangelogViewProcedure doBuild() {
        /** 创建并返回新实例。 */
        return new CreateChangelogViewProcedure(tableCatalog());
      }
    };
  }

  /** 构造 CreateChangelogViewProcedure 实例。 */
  private CreateChangelogViewProcedure(TableCatalog tableCatalog) {
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
    ProcedureInput input = new ProcedureInput(spark(), tableCatalog(), PARAMETERS, args);

    Identifier tableIdent = input.ident(TABLE_PARAM);

    // load insert and deletes from the changelog table
    Identifier changelogTableIdent = changelogTableIdent(tableIdent);
    Dataset<Row> df = loadRows(changelogTableIdent, options(input));

    boolean netChanges = input.asBoolean(NET_CHANGES, false);

    if (shouldComputeUpdateImages(input)) {
      Preconditions.checkArgument(!netChanges, "Not support net changes with update images");
      df = computeUpdateImages(identifierColumns(input, tableIdent), df);
    } else if (shouldRemoveCarryoverRows(input)) {
      df = removeCarryoverRows(df, netChanges);
    }

    String viewName = viewName(input, tableIdent.name());

    df.createOrReplaceTempView(viewName);

    return toOutputRows(viewName);
  }

  /** 执行该方法的具体逻辑。 */
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

  /** 执行该方法的具体逻辑。 */
  private boolean shouldComputeUpdateImages(ProcedureInput input) {
    // If the identifier columns are set, we compute pre/post update images by default.
    boolean defaultValue = input.isProvided(IDENTIFIER_COLUMNS_PARAM);
    return input.asBoolean(COMPUTE_UPDATES_PARAM, defaultValue);
  }

  /** 执行该方法的具体逻辑。 */
  private boolean shouldRemoveCarryoverRows(ProcedureInput input) {
    return input.asBoolean(REMOVE_CARRYOVERS_PARAM, true);
  }

  /** 移除元素或项。 */
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

  /** 执行该方法的具体逻辑。 */
  private String[] identifierColumns(ProcedureInput input, Identifier tableIdent) {
    if (input.isProvided(IDENTIFIER_COLUMNS_PARAM)) {
      return input.asStringArray(IDENTIFIER_COLUMNS_PARAM);
    } else {
      Table table = loadSparkTable(tableIdent).table();
      return table.schema().identifierFieldNames().toArray(new String[0]);
    }
  }

  /** 执行该方法的具体逻辑。 */
  private Identifier changelogTableIdent(Identifier tableIdent) {
    List<String> namespace = Lists.newArrayList();
    namespace.addAll(Arrays.asList(tableIdent.namespace()));
    namespace.add(tableIdent.name());
    return Identifier.of(namespace.toArray(new String[0]), SparkChangelogTable.TABLE_NAME);
  }

  /** 执行该方法的具体逻辑。 */
  private Map<String, String> options(ProcedureInput input) {
    return input.asStringMap(OPTIONS_PARAM, ImmutableMap.of());
  }

  /** 执行该方法的具体逻辑。 */
  private String viewName(ProcedureInput input, String tableName) {
    String defaultValue = String.format("`%s_changes`", tableName);
    return input.asString(CHANGELOG_VIEW_PARAM, defaultValue);
  }

  /** 执行核心逻辑。 */
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
            RowEncoder.apply(schema));
  }

  /** 执行核心逻辑。 */
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
            RowEncoder.apply(schema));
  }

  /** 执行该方法的具体逻辑。 */
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

  /** 转换为outputrows。 */
  private InternalRow[] toOutputRows(String viewName) {
    InternalRow row = newInternalRow(UTF8String.fromString(viewName));
    return new InternalRow[] {row};
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  @Override
  public String description() {
    return "CreateChangelogViewProcedure";
  }
}
