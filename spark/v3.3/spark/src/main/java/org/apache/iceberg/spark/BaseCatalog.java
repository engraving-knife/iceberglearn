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

import org.apache.iceberg.spark.functions.SparkFunctions;
import org.apache.iceberg.spark.procedures.SparkProcedures;
import org.apache.iceberg.spark.procedures.SparkProcedures.ProcedureBuilder;
import org.apache.iceberg.spark.source.HasIcebergCatalog;
import org.apache.spark.sql.catalyst.analysis.NoSuchFunctionException;
import org.apache.spark.sql.catalyst.analysis.NoSuchNamespaceException;
import org.apache.spark.sql.catalyst.analysis.NoSuchProcedureException;
import org.apache.spark.sql.connector.catalog.FunctionCatalog;
import org.apache.spark.sql.connector.catalog.Identifier;
import org.apache.spark.sql.connector.catalog.StagingTableCatalog;
import org.apache.spark.sql.connector.catalog.SupportsNamespaces;
import org.apache.spark.sql.connector.catalog.functions.UnboundFunction;
import org.apache.spark.sql.connector.iceberg.catalog.Procedure;
import org.apache.spark.sql.connector.iceberg.catalog.ProcedureCatalog;

/**
 * Iceberg Spark 集成相关组件，实现 Spark 目录服务以加载和管理 Iceberg 表。
 *
 * <p>所属模块：iceberg-spark v3.3。 类型：类 BaseCatalog。
 *
 * <p>设计意图：模板方法模式，抽取公共流程供子类复用。
 */
abstract class BaseCatalog
    implements StagingTableCatalog,
        ProcedureCatalog,
        SupportsNamespaces,
        HasIcebergCatalog,
        FunctionCatalog {

  /**
   * 执行该方法的具体逻辑。
   *
   * @param ident 参数
   * @return 结果对象
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

    /** 执行该方法的具体逻辑。 */
    throw new NoSuchProcedureException(ident);
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @param namespace 参数
   * @return 结果对象
   */
  @Override
  public Identifier[] listFunctions(String[] namespace) throws NoSuchNamespaceException {
    if (namespace.length == 0 || isSystemNamespace(namespace)) {
      return SparkFunctions.list().stream()
          .map(name -> Identifier.of(namespace, name))
          .toArray(Identifier[]::new);
    } else if (namespaceExists(namespace)) {
      return new Identifier[0];
    }

    /** 执行该方法的具体逻辑。 */
    throw new NoSuchNamespaceException(namespace);
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @param ident 参数
   * @return 结果对象
   */
  @Override
  public UnboundFunction loadFunction(Identifier ident) throws NoSuchFunctionException {
    String[] namespace = ident.namespace();
    String name = ident.name();

    // Allow for empty namespace, as Spark's storage partitioned joins look up
    // the corresponding functions to generate transforms for partitioning
    // with an empty namespace, such as `bucket`.
    // Otherwise, use `system` namespace.
    if (namespace.length == 0 || isSystemNamespace(namespace)) {
      UnboundFunction func = SparkFunctions.load(name);
      if (func != null) {
        return func;
      }
    }

    /** 执行该方法的具体逻辑。 */
    throw new NoSuchFunctionException(ident);
  }

  /** 判断是否systemnamespace。 */
  private static boolean isSystemNamespace(String[] namespace) {
    return namespace.length == 1 && namespace[0].equalsIgnoreCase("system");
  }
}
