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
 * 所属模块：iceberg-spark v3.4
 *
 * <p>职责：Iceberg Spark 表目录（TableCatalog）的抽象基类，提供目录初始化、命名空间与表操作以及存储过程加载的公共骨架。
 *
 * <p>设计意图：采用模板方法模式，将公共的目录代理、过程加载与函数命名空间逻辑沉淀在基类，子类只需关注具体 Catalog 代理实现。
 *
 * <p>上下游关系：被 SparkCatalog / SparkSessionCatalog 继承；依赖底层 Iceberg Catalog 与 SparkProcedures。
 */
abstract class BaseCatalog
    implements StagingTableCatalog,
        ProcedureCatalog,
        SupportsNamespaces,
        HasIcebergCatalog,
        SupportsFunctions {

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
  /** 判断是否 FunctionNamespace。 */
  @Override
  public boolean isFunctionNamespace(String[] namespace) {
    // Allow for empty namespace, as Spark's storage partitioned joins look up
    // the corresponding functions to generate transforms for partitioning
    // with an empty namespace, such as `bucket`.
    // Otherwise, use `system` namespace.
    return namespace.length == 0 || isSystemNamespace(namespace);
  }
  /** 判断是否 ExistingNamespace。 */
  @Override
  public boolean isExistingNamespace(String[] namespace) {
    return namespaceExists(namespace);
  }
  /** 判断是否 SystemNamespace。 */
  private static boolean isSystemNamespace(String[] namespace) {
    return namespace.length == 1 && namespace[0].equalsIgnoreCase("system");
  }
}
