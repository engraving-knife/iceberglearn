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

import org.apache.iceberg.spark.procedures.SparkProcedures;
import org.apache.iceberg.spark.procedures.SparkProcedures.ProcedureBuilder;
import org.apache.iceberg.spark.source.HasIcebergCatalog;
import org.apache.spark.sql.catalyst.analysis.NoSuchProcedureException;
import org.apache.spark.sql.connector.catalog.Identifier;
import org.apache.spark.sql.connector.catalog.StagingTableCatalog;
import org.apache.spark.sql.connector.catalog.SupportsNamespaces;
import org.apache.spark.sql.connector.iceberg.catalog.Procedure;
import org.apache.spark.sql.connector.iceberg.catalog.ProcedureCatalog;

/**
 * Iceberg Spark Catalog 抽象基类。
 *
 * <p>所属模块：iceberg-spark。本类为各 Iceberg Spark Catalog（如 {@link SparkCatalog}、 {@link
 * SparkSessionCatalog}）提供公共能力：实现 {@link ProcedureCatalog} 加载系统存储过程、 实现 {@link SupportsNamespaces}
 * 命名空间支持、{@link SupportsFunctions} 函数命名空间判定， 并继承 {@link HasIcebergCatalog}。
 *
 * <p>设计意图：将存储过程加载、函数命名空间判定等与具体 Catalog 实现无关的逻辑上提，子类 只需关注表与命名空间的存储细节。系统存储过程统一挂在 system 命名空间下。
 *
 * <p>上下游关系：被 SparkCatalog/SparkSessionCatalog 继承；通过 {@link SparkProcedures} 构造过程实例。
 */
abstract class BaseCatalog
    implements StagingTableCatalog,
        ProcedureCatalog,
        SupportsNamespaces,
        HasIcebergCatalog,
        SupportsFunctions {

  /**
   * 加载系统存储过程。
   *
   * <p>逻辑：仅当标识位于 system 命名空间（大小写不敏感）时，按名称构造对应过程构建器并绑定本 Catalog； 否则抛出 {@link
   * NoSuchProcedureException}。
   */
  @Override
  public Procedure loadProcedure(Identifier ident) throws NoSuchProcedureException {
    String[] namespace = ident.namespace();
    String name = ident.name();

    // namespace resolution is case insensitive until we have a way to configure case sensitivity in
    // catalogs
    if (isSystemNamespace(namespace)) {
      ProcedureBuilder builder = SparkProcedures.newBuilder(name);
      if (builder != null) {
        return builder.withTableCatalog(this).build();
      }
    }

    throw new NoSuchProcedureException(ident);
  }

  /** 判断给定命名空间是否可用于 Iceberg 函数：允许空命名空间（存储分区连接会以空命名空间查找 bucket 等变换函数）或 system 命名空间。 */
  @Override
  public boolean isFunctionNamespace(String[] namespace) {
    // Allow for empty namespace, as Spark's storage partitioned joins look up
    // the corresponding functions to generate transforms for partitioning
    // with an empty namespace, such as `bucket`.
    // Otherwise, use `system` namespace.
    return namespace.length == 0 || isSystemNamespace(namespace);
  }

  /** 判断命名空间是否存在，委托给 {@link #namespaceExists}。 */
  @Override
  public boolean isExistingNamespace(String[] namespace) {
    return namespaceExists(namespace);
  }

  /** 判断是否为 system 命名空间（单段且大小写不敏感）。 */
  private static boolean isSystemNamespace(String[] namespace) {
    return namespace.length == 1 && namespace[0].equalsIgnoreCase("system");
  }
}
