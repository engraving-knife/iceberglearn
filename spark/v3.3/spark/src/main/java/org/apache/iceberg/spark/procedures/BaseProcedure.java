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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.Function;
import org.apache.iceberg.exceptions.ValidationException;
import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.util.concurrent.MoreExecutors;
import org.apache.iceberg.relocated.com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.apache.iceberg.spark.Spark3Util;
import org.apache.iceberg.spark.Spark3Util.CatalogAndIdentifier;
import org.apache.iceberg.spark.actions.SparkActions;
import org.apache.iceberg.spark.procedures.SparkProcedures.ProcedureBuilder;
import org.apache.iceberg.spark.source.SparkTable;
import org.apache.spark.sql.AnalysisException;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.catalyst.analysis.NoSuchTableException;
import org.apache.spark.sql.catalyst.expressions.GenericInternalRow;
import org.apache.spark.sql.connector.catalog.CatalogPlugin;
import org.apache.spark.sql.connector.catalog.Identifier;
import org.apache.spark.sql.connector.catalog.Table;
import org.apache.spark.sql.connector.catalog.TableCatalog;
import org.apache.spark.sql.connector.iceberg.catalog.Procedure;
import org.apache.spark.sql.execution.CacheManager;
import org.apache.spark.sql.execution.datasources.SparkExpressionConverter;
import org.apache.spark.sql.execution.datasources.v2.DataSourceV2Relation;
import org.apache.spark.sql.types.DataType;
import org.apache.spark.sql.types.DataTypes;
import scala.Option;

/**
 * Iceberg 存储过程，通过 Spark SQL CALL 调用，封装为可通过 SQL CALL 调用的存储过程。
 *
 * <p>所属模块：iceberg-spark v3.3。 类型：类 BaseProcedure。
 *
 * <p>设计意图：模板方法模式，抽取公共流程供子类复用。
 *
 * <p>上下游：由 SparkSessionProcedures 注册，被 Spark SQL CALL 语句调用。
 */
abstract class BaseProcedure implements Procedure {
  protected static final DataType STRING_MAP =
      DataTypes.createMapType(DataTypes.StringType, DataTypes.StringType);
  protected static final DataType STRING_ARRAY = DataTypes.createArrayType(DataTypes.StringType);

  private final SparkSession spark;
  private final TableCatalog tableCatalog;

  private SparkActions actions;
  private ExecutorService executorService = null;

  /** 构造 BaseProcedure 实例。 */
  protected BaseProcedure(TableCatalog tableCatalog) {
    this.spark = SparkSession.active();
    this.tableCatalog = tableCatalog;
  }

  /** 执行该方法的具体逻辑。 */
  protected SparkSession spark() {
    return this.spark;
  }

  /** 执行该方法的具体逻辑。 */
  protected SparkActions actions() {
    if (actions == null) {
      this.actions = SparkActions.get(spark);
    }
    return actions;
  }

  /** 执行该方法的具体逻辑。 */
  protected TableCatalog tableCatalog() {
    return this.tableCatalog;
  }

  /** 执行该方法的具体逻辑。 */
  protected <T> T modifyIcebergTable(Identifier ident, Function<org.apache.iceberg.Table, T> func) {
    try {
      return execute(ident, true, func);
    } finally {
      closeService();
    }
  }

  /** 返回带新设置的副本。 */
  protected <T> T withIcebergTable(Identifier ident, Function<org.apache.iceberg.Table, T> func) {
    try {
      return execute(ident, false, func);
    } finally {
      closeService();
    }
  }

  /** 执行具体逻辑。 */
  private <T> T execute(
      Identifier ident, boolean refreshSparkCache, Function<org.apache.iceberg.Table, T> func) {
    SparkTable sparkTable = loadSparkTable(ident);
    org.apache.iceberg.Table icebergTable = sparkTable.table();

    T result = func.apply(icebergTable);

    if (refreshSparkCache) {
      refreshSparkCache(ident, sparkTable);
    }

    return result;
  }

  /** 转换为identifier。 */
  protected Identifier toIdentifier(String identifierAsString, String argName) {
    CatalogAndIdentifier catalogAndIdentifier =
        toCatalogAndIdentifier(identifierAsString, argName, tableCatalog);

    Preconditions.checkArgument(
        catalogAndIdentifier.catalog().equals(tableCatalog),
        "Cannot run procedure in catalog '%s': '%s' is a table in catalog '%s'",
        tableCatalog.name(),
        identifierAsString,
        catalogAndIdentifier.catalog().name());

    return catalogAndIdentifier.identifier();
  }

  /** 转换为catalogandidentifier。 */
  protected CatalogAndIdentifier toCatalogAndIdentifier(
      String identifierAsString, String argName, CatalogPlugin catalog) {
    Preconditions.checkArgument(
        identifierAsString != null && !identifierAsString.isEmpty(),
        "Cannot handle an empty identifier for argument %s",
        argName);

    return Spark3Util.catalogAndIdentifier(
        "identifier for arg " + argName, spark, identifierAsString, catalog);
  }

  /** 执行该方法的具体逻辑。 */
  protected SparkTable loadSparkTable(Identifier ident) {
    try {
      Table table = tableCatalog.loadTable(ident);
      ValidationException.check(
          table instanceof SparkTable, "%s is not %s", ident, SparkTable.class.getName());
      return (SparkTable) table;
    } catch (NoSuchTableException e) {
      String errMsg =
          String.format("Couldn't load table '%s' in catalog '%s'", ident, tableCatalog.name());
      /** 执行该方法的具体逻辑。 */
      throw new RuntimeException(errMsg, e);
    }
  }

  /** 执行该方法的具体逻辑。 */
  protected Dataset<Row> loadRows(Identifier tableIdent, Map<String, String> options) {
    String tableName = Spark3Util.quotedFullIdentifier(tableCatalog().name(), tableIdent);
    return spark().read().options(options).table(tableName);
  }

  /** 执行该方法的具体逻辑。 */
  protected void refreshSparkCache(Identifier ident, Table table) {
    CacheManager cacheManager = spark.sharedState().cacheManager();
    DataSourceV2Relation relation =
        DataSourceV2Relation.create(table, Option.apply(tableCatalog), Option.apply(ident));
    cacheManager.recacheByPlan(spark, relation);
  }

  /** 按条件过滤。 */
  protected Expression filterExpression(Identifier ident, String where) {
    try {
      String name = Spark3Util.quotedFullIdentifier(tableCatalog.name(), ident);
      org.apache.spark.sql.catalyst.expressions.Expression expression =
          SparkExpressionConverter.collectResolvedSparkExpression(spark, name, where);
      return SparkExpressionConverter.convertToIcebergExpression(expression);
    } catch (AnalysisException e) {
      throw new IllegalArgumentException("Cannot parse predicates in where option: " + where, e);
    }
  }

  /** 执行该方法的具体逻辑。 */
  protected InternalRow newInternalRow(Object... values) {
    /** 执行该方法的具体逻辑。 */
    return new GenericInternalRow(values);
  }

  /**
   * Iceberg 存储过程，通过 Spark SQL CALL 调用的构建器，负责分步骤构造目标对象。
   *
   * <p>所属模块：iceberg-spark v3.3。 类型：类 Builder。
   *
   * <p>设计意图：建造者模式，分离复杂对象的构造与表示。
   *
   * <p>上下游：由 SparkSessionProcedures 注册，被 Spark SQL CALL 语句调用。
   */
  protected abstract static class Builder<T extends BaseProcedure> implements ProcedureBuilder {
    private TableCatalog tableCatalog;

    /**
     * 返回带新设置的副本。
     *
     * @param newTableCatalog 参数
     * @return 结果对象
     */
    @Override
    public Builder<T> withTableCatalog(TableCatalog newTableCatalog) {
      this.tableCatalog = newTableCatalog;
      return this;
    }

    /**
     * 构造并返回目标对象。
     *
     * @return 结果对象
     */
    @Override
    public T build() {
      return doBuild();
    }

    /** 执行该方法的具体逻辑。 */
    protected abstract T doBuild();

    /** 执行该方法的具体逻辑。 */
    TableCatalog tableCatalog() {
      return tableCatalog;
    }
  }

  /** 释放底层资源。 */
  protected void closeService() {
    if (executorService != null) {
      executorService.shutdown();
    }
  }

  /** 执行该方法的具体逻辑。 */
  protected ExecutorService executorService(int threadPoolSize, String nameFormat) {
    Preconditions.checkArgument(
        executorService == null, "Cannot create a new executor service, one already exists.");
    Preconditions.checkArgument(
        nameFormat != null, "Cannot create a service with null nameFormat arg");
    this.executorService =
        MoreExecutors.getExitingExecutorService(
            (ThreadPoolExecutor)
                Executors.newFixedThreadPool(
                    threadPoolSize,
                    new ThreadFactoryBuilder()
                        .setDaemon(true)
                        .setNameFormat(nameFormat + "-%d")
                        .build()));

    return executorService;
  }
}
