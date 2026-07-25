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
package org.apache.spark.sql.connector.iceberg.catalog;

import org.apache.spark.sql.catalyst.analysis.NoSuchProcedureException;
import org.apache.spark.sql.connector.catalog.CatalogPlugin;
import org.apache.spark.sql.connector.catalog.Identifier;

/**
 * 所属模块：iceberg-spark v3.5
 *
 * <p>职责：支持存储过程的目录接口，声明目录可按名加载 Procedure。
 *
 * <p>设计意图：以接口隔离过程能力，使目录可选地提供存储过程。
 *
 * <p>上下游关系：由 SparkCatalog 实现；由 ResolveProcedures / CallExec 使用。
 */
public interface ProcedureCatalog extends CatalogPlugin {
  /**
   * Load a {@link Procedure stored procedure} by {@link Identifier identifier}.
   *
   * @param ident a stored procedure identifier
   * @return the stored procedure's metadata
   * @throws NoSuchProcedureException if there is no matching stored procedure
   */
  Procedure loadProcedure(Identifier ident) throws NoSuchProcedureException;
}
