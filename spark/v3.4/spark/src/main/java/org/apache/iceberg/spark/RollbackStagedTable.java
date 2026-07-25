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
 * 所属模块：iceberg-spark v3.4
 *
 * <p>职责：可回滚的暂存表实现，在 CREATE TABLE AS SELECT 等场景中暂存表并在失败时回滚元数据。
 *
 * <p>设计意图：包装 StagedSparkTable，捕获创建过程以支持失败回滚，保证原子性。
 *
 * <p>上下游关系：由 SparkCatalog 在分阶段建表流程中使用。
 */
public class RollbackStagedTable
    implements StagedTable, SupportsRead, SupportsWrite, SupportsDelete {
  private final TableCatalog catalog;
  private final Identifier ident;
  private final Table table;

  public RollbackStagedTable(TableCatalog catalog, Identifier ident, Table table) {
    this.catalog = catalog;
    this.ident = ident;
    this.table = table;
  }
  /** 执行 commitStagedChanges 相关操作。 */
  @Override
  public void commitStagedChanges() {
    // the changes have already been committed to the table at the end of the write
  }
  /** 执行 abortStagedChanges 相关操作。 */
  @Override
  public void abortStagedChanges() {
    // roll back changes by dropping the table
    catalog.dropTable(ident);
  }
  /** 返回名称。 */
  @Override
  public String name() {
    return table.name();
  }
  /** 返回 Schema。 */
  @Override
  public StructType schema() {
    return table.schema();
  }
  /** 返回分区信息。 */
  @Override
  public Transform[] partitioning() {
    return table.partitioning();
  }
  /** 返回属性。 */
  @Override
  public Map<String, String> properties() {
    return table.properties();
  }
  /** 返回能力集。 */
  @Override
  public Set<TableCapability> capabilities() {
    return table.capabilities();
  }
  /** 执行 deleteWhere 相关操作。 */
  @Override
  public void deleteWhere(Filter[] filters) {
    call(SupportsDelete.class, t -> t.deleteWhere(filters));
  }
  /** 创建 ScanBuilder 实例。 */
  @Override
  public ScanBuilder newScanBuilder(CaseInsensitiveStringMap options) {
    return callReturning(SupportsRead.class, t -> t.newScanBuilder(options));
  }
  /** 创建 WriteBuilder 实例。 */
  @Override
  public WriteBuilder newWriteBuilder(LogicalWriteInfo info) {
    return callReturning(SupportsWrite.class, t -> t.newWriteBuilder(info));
  }

  private <T> void call(Class<? extends T> requiredClass, Consumer<T> task) {
    callReturning(
        requiredClass,
        inst -> {
          task.accept(inst);
          return null;
        });
  }

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
