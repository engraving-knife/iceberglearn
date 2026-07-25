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
package org.apache.iceberg.spark;

import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import org.apache.spark.sql.connector.catalog.Identifier;
import org.apache.spark.sql.connector.catalog.StagedTable;
import org.apache.spark.sql.connector.catalog.SupportsDelete;
import org.apache.spark.sql.connector.catalog.SupportsRead;
import org.apache.spark.sql.connector.catalog.SupportsWrite;
import org.apache.spark.sql.connector.catalog.Table;
import org.apache.spark.sql.connector.catalog.TableCapability;
import org.apache.spark.sql.connector.catalog.TableCatalog;
import org.apache.spark.sql.connector.expressions.Transform;
import org.apache.spark.sql.connector.read.ScanBuilder;
import org.apache.spark.sql.connector.write.LogicalWriteInfo;
import org.apache.spark.sql.connector.write.WriteBuilder;
import org.apache.spark.sql.sources.Filter;
import org.apache.spark.sql.types.StructType;
import org.apache.spark.sql.util.CaseInsensitiveStringMap;

/**
 * 模拟 Spark 非原子 CTAS/RTAS 行为的 StagedTable 实现。
 *
 * <p>所属模块：iceberg-spark。Catalog 实现 {@link StagingTableCatalog} 后，Spark 期望其能为 任意加载的表产出 StagedTable；但
 * {@link SparkSessionCatalog} 包装了 session catalog，无法 为其加载的非 Iceberg 表产出可用的 StagedTable。本类作为折中方案：实现
 * StagedTable 接口但 不提供原子性，而是用"建表→写入→失败时删表回滚"的非原子方式模拟。
 *
 * <p>职责：将读、写、删除调用透传给真实表；提交时无操作（写时已提交）；中止时删除表以回滚。
 *
 * <p>设计意图：复用 StagedTable 接口语义来承载非原子执行计划，避免 Spark 因 Catalog 无法 产出 StagedTable 而报错。实现
 * SupportsRead/SupportsWrite/SupportsDelete 是安全的，因为 Spark 仅在 {@link #capabilities()} 返回对应能力时才会调用。
 *
 * <p>上下游关系：由 SparkSessionCatalog 等在无法提供真正原子暂存表时返回。
 */
public class RollbackStagedTable
    implements StagedTable, SupportsRead, SupportsWrite, SupportsDelete {
  private final TableCatalog catalog;
  private final Identifier ident;
  private final Table table;

  /** 以所属 Catalog、表标识与真实表构造。 */
  public RollbackStagedTable(TableCatalog catalog, Identifier ident, Table table) {
    this.catalog = catalog;
    this.ident = ident;
    this.table = table;
  }

  /** 提交暂存变更：实际变更在写入结束时已提交，此处无操作。 */
  @Override
  public void commitStagedChanges() {
    // the changes have already been committed to the table at the end of the write
  }

  /** 中止暂存变更：通过删除表实现回滚。 */
  @Override
  public void abortStagedChanges() {
    // roll back changes by dropping the table
    catalog.dropTable(ident);
  }

  /** 返回真实表名。 */
  @Override
  public String name() {
    return table.name();
  }

  /** 返回真实表 schema。 */
  @Override
  public StructType schema() {
    return table.schema();
  }

  /** 返回真实表分区变换。 */
  @Override
  public Transform[] partitioning() {
    return table.partitioning();
  }

  /** 返回真实表属性。 */
  @Override
  public Map<String, String> properties() {
    return table.properties();
  }

  /** 返回真实表能力集合。 */
  @Override
  public Set<TableCapability> capabilities() {
    return table.capabilities();
  }

  /** 透传 deleteWhere 到实现了 SupportsDelete 的真实表。 */
  @Override
  public void deleteWhere(Filter[] filters) {
    call(SupportsDelete.class, t -> t.deleteWhere(filters));
  }

  /** 透传 newScanBuilder 到实现了 SupportsRead 的真实表。 */
  @Override
  public ScanBuilder newScanBuilder(CaseInsensitiveStringMap options) {
    return callReturning(SupportsRead.class, t -> t.newScanBuilder(options));
  }

  /** 透传 newWriteBuilder 到实现了 SupportsWrite 的真实表。 */
  @Override
  public WriteBuilder newWriteBuilder(LogicalWriteInfo info) {
    return callReturning(SupportsWrite.class, t -> t.newWriteBuilder(info));
  }

  /** 在真实表上执行无返回值的调用，委托给 {@link #callReturning}。 */
  private <T> void call(Class<? extends T> requiredClass, Consumer<T> task) {
    callReturning(
        requiredClass,
        inst -> {
          task.accept(inst);
          return null;
        });
  }

  /** 在真实表上执行有返回值的调用：若真实表实现了所需接口则执行，否则抛出 UnsupportedOperationException。 */
  private <T, R> R callReturning(Class<? extends T> requiredClass, Function<T, R> task) {
    if (requiredClass.isInstance(table)) {
      return task.apply(requiredClass.cast(table));
    } else {
      throw new UnsupportedOperationException(
          String.format(
              "Table does not implement %s: %s (%s)",
              requiredClass.getSimpleName(), table.name(), table.getClass().getName()));
    }
  }
}
