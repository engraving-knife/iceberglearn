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
 * 所有 Iceberg Spark 存储过程的抽象基类。
 *
 * <p>所属模块：iceberg-spark（Iceberg 与 Spark 3.5 的集成层，procedures 子包负责 将 Iceberg 的表管理操作封装为 Spark 存储过程，可通过
 * CALL 语句调用）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>提供存储过程的公共基础设施：SparkSession 获取、表加载、标识符解析、 Spark 缓存刷新、过滤表达式转换等。
 *   <li>提供 modifyIcebergTable / withIcebergTable 模板方法，封装"加载表 -> 执行操作 -> 刷新缓存 -> 清理资源"的标准流程。
 *   <li>提供按需创建的线程池管理（executorService），支持存储过程的并行操作。
 * </ul>
 *
 * <p>设计意图：将所有存储过程共享的横切逻辑（表加载、缓存刷新、资源清理、标识符解析） 抽取到抽象基类，具体过程只需实现 call() 方法。使用 Builder 模式构建过程实例， 统一了
 * TableCatalog 的注入方式。线程池在存储过程执行完毕后自动关闭。
 *
 * <p>上下游关系：被所有具体 Procedure（FastForwardBranchProcedure、RegisterTableProcedure、
 * RewritePositionDeleteFilesProcedure 等）继承；通过 SparkProcedures 注册到 Spark catalog； 依赖
 * Spark3Util、SparkActions、SparkTable 等 Spark 集成组件。
 */
abstract class BaseProcedure implements Procedure {
  protected static final DataType STRING_MAP =
      DataTypes.createMapType(DataTypes.StringType, DataTypes.StringType);
  protected static final DataType STRING_ARRAY = DataTypes.createArrayType(DataTypes.StringType);

  private final SparkSession spark;
  private final TableCatalog tableCatalog;

  private SparkActions actions;
  private ExecutorService executorService = null;

  protected BaseProcedure(TableCatalog tableCatalog) {
    this.spark = SparkSession.active();
    this.tableCatalog = tableCatalog;
  }
  /** 执行 spark 相关操作。 */
  protected SparkSession spark() {
    return this.spark;
  }
  /** 执行 actions 相关操作。 */
  protected SparkActions actions() {
    if (actions == null) {
      this.actions = SparkActions.get(spark);
    }
    return actions;
  }
  /** 执行 tableCatalog 相关操作。 */
  protected TableCatalog tableCatalog() {
    return this.tableCatalog;
  }

  /**
   * 加载 Iceberg 表并执行修改操作，执行后刷新 Spark 缓存。
   *
   * <p>逻辑：调用 execute 加载表并应用 func，refreshSparkCache=true 使修改后的表元数据 在 Spark 缓存中失效重建。finally
   * 中关闭本过程创建的线程池。
   *
   * @param ident 表标识符
   * @param func 对 Iceberg 表执行的修改函数
   * @param <T> 返回类型
   * @return 函数执行结果
   */
  protected <T> T modifyIcebergTable(Identifier ident, Function<org.apache.iceberg.Table, T> func) {
    try {
      return execute(ident, true, func);
    } finally {
      closeService();
    }
  }

  /**
   * 加载 Iceberg 表并执行只读操作（不刷新 Spark 缓存）。
   *
   * @param ident 表标识符
   * @param func 对 Iceberg 表执行的只读函数
   * @param <T> 返回类型
   * @return 函数执行结果
   */
  protected <T> T withIcebergTable(Identifier ident, Function<org.apache.iceberg.Table, T> func) {
    try {
      return execute(ident, false, func);
    } finally {
      closeService();
    }
  }

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
  /** 转换为 Identifier。 */
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
  /** 转换为 CatalogAndIdentifier。 */
  protected CatalogAndIdentifier toCatalogAndIdentifier(
      String identifierAsString, String argName, CatalogPlugin catalog) {
    Preconditions.checkArgument(
        identifierAsString != null && !identifierAsString.isEmpty(),
        "Cannot handle an empty identifier for argument %s",
        argName);

    return Spark3Util.catalogAndIdentifier(
        "identifier for arg " + argName, spark, identifierAsString, catalog);
  }

  /**
   * 通过 catalog 加载 SparkTable，校验目标表确实是 Iceberg 表。
   *
   * @param ident 表标识符
   * @return 加载的 SparkTable
   * @throws RuntimeException 表不存在或非 Iceberg 表时抛出
   */
  protected SparkTable loadSparkTable(Identifier ident) {
    try {
      Table table = tableCatalog.loadTable(ident);
      ValidationException.check(
          table instanceof SparkTable, "%s is not %s", ident, SparkTable.class.getName());
      return (SparkTable) table;
    } catch (NoSuchTableException e) {
      String errMsg =
          String.format("Couldn't load table '%s' in catalog '%s'", ident, tableCatalog.name());
      throw new RuntimeException(errMsg, e);
    }
  }
  /** 执行 loadRows 相关操作。 */
  protected Dataset<Row> loadRows(Identifier tableIdent, Map<String, String> options) {
    String tableName = Spark3Util.quotedFullIdentifier(tableCatalog().name(), tableIdent);
    return spark().read().options(options).table(tableName);
  }
  /** 执行 refreshSparkCache 相关操作。 */
  protected void refreshSparkCache(Identifier ident, Table table) {
    CacheManager cacheManager = spark.sharedState().cacheManager();
    DataSourceV2Relation relation =
        DataSourceV2Relation.create(table, Option.apply(tableCatalog), Option.apply(ident));
    cacheManager.recacheByPlan(spark, relation);
  }

  /**
   * 将 SQL where 子句解析并转换为 Iceberg 过滤表达式。
   *
   * <p>逻辑：通过 SparkExpressionConverter 收集并解析 Spark 表达式， 再转换为 Iceberg 的 Expression。解析失败时抛出
   * IllegalArgumentException。
   *
   * @param ident 表标识符（用于构造完整表名供 Spark 解析）
   * @param where SQL where 子句字符串
   * @return Iceberg 过滤表达式
   * @throws IllegalArgumentException where 子句无法解析时抛出
   */
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
  /** 创建 InternalRow 实例。 */
  protected InternalRow newInternalRow(Object... values) {
    return new GenericInternalRow(values);
  }

  protected abstract static class Builder<T extends BaseProcedure> implements ProcedureBuilder {
    private TableCatalog tableCatalog;
    /** 返回带 TableCatalog 设置的副本。 */
    @Override
    public Builder<T> withTableCatalog(TableCatalog newTableCatalog) {
      this.tableCatalog = newTableCatalog;
      return this;
    }
    /** 构建目标对象。 */
    @Override
    public T build() {
      return doBuild();
    }
    /** 执行 doBuild 相关操作。 */
    protected abstract T doBuild();

    TableCatalog tableCatalog() {
      return tableCatalog;
    }
  }

  /**
   * Closes this procedure's executor service if a new one was created with {@link
   * #executorService(int, String)}. Does not block for any remaining tasks.
   */
  protected void closeService() {
    if (executorService != null) {
      executorService.shutdown();
    }
  }

  /**
   * Starts a new executor service which can be used by this procedure in its work. The pool will be
   * automatically shut down if {@link #withIcebergTable(Identifier, Function)} or {@link
   * #modifyIcebergTable(Identifier, Function)} are called. If these methods are not used then the
   * service can be shut down with {@link #closeService()} or left to be closed when this class is
   * finalized.
   *
   * @param threadPoolSize number of threads in the service
   * @param nameFormat name prefix for threads created in this service
   * @return the new executor service owned by this procedure
   */
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
