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
import org.apache.spark.sql.catalyst.analysis.NoSuchFunctionException;
import org.apache.spark.sql.catalyst.analysis.NoSuchNamespaceException;
import org.apache.spark.sql.connector.catalog.FunctionCatalog;
import org.apache.spark.sql.connector.catalog.Identifier;
import org.apache.spark.sql.connector.catalog.functions.UnboundFunction;

/**
 * 所属模块：iceberg-spark v3.5
 *
 * <p>职责：支持函数注册的目录混入接口，声明目录可向 Spark 暴露 Iceberg 标量函数。
 *
 * <p>设计意图：以接口隔离函数能力，使目录可选地提供函数目录功能。
 *
 * <p>上下游关系：由 SparkCatalog / SparkSessionCatalog 实现；由 SparkFunctionCatalog 使用。
 */
interface SupportsFunctions extends FunctionCatalog {
  /** 判断是否 FunctionNamespace。 */
  default boolean isFunctionNamespace(String[] namespace) {
    return namespace.length == 0;
  }
  /** 判断是否 ExistingNamespace。 */
  default boolean isExistingNamespace(String[] namespace) {
    return namespace.length == 0;
  }

  default Identifier[] listFunctions(String[] namespace) throws NoSuchNamespaceException {
    if (isFunctionNamespace(namespace)) {
      return SparkFunctions.list().stream()
          .map(name -> Identifier.of(namespace, name))
          .toArray(Identifier[]::new);
    } else if (isExistingNamespace(namespace)) {
      return new Identifier[0];
    }

    throw new NoSuchNamespaceException(namespace);
  }

  default UnboundFunction loadFunction(Identifier ident) throws NoSuchFunctionException {
    String[] namespace = ident.namespace();
    String name = ident.name();

    if (isFunctionNamespace(namespace)) {
      UnboundFunction func = SparkFunctions.load(name);
      if (func != null) {
        return func;
      }
    }

    throw new NoSuchFunctionException(ident);
  }
}
