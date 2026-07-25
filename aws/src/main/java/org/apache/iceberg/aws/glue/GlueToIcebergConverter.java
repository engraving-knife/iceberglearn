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
package org.apache.iceberg.aws.glue;

import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.catalog.TableIdentifier;
import software.amazon.awssdk.services.glue.model.Database;
import software.amazon.awssdk.services.glue.model.Table;

/**
 * 文件级说明：Glue 元数据对象到 Iceberg 标识符的转换器。
 *
 * <p>所属模块：iceberg-aws（Iceberg 与 AWS 服务集成的入口模块，位于 api/core 之上）。
 *
 * <p>职责：将 AWS Glue SDK 模型（{@link Database}、{@link Table}）转换为 Iceberg 内部使用的 {@link Namespace} 与
 * {@link TableIdentifier}，屏蔽两套命名模型差异。
 *
 * <p>设计意图：工具类模式，私有构造器 + 静态方法，无状态可复用；Glue 中库名即 Iceberg 单层 Namespace，表由 databaseName + tableName
 * 组合唯一确定。
 *
 * <p>上下游关系：被 {@link GlueCatalog} 在列举库/表、加载表元数据等流程中调用。
 */
class GlueToIcebergConverter {

  private GlueToIcebergConverter() {}

  /**
   * 将 Glue {@link Database} 转换为 Iceberg {@link Namespace}。
   *
   * @param database Glue 数据库对象
   * @return 由 Glue 库名构成的 Iceberg Namespace
   */
  static Namespace toNamespace(Database database) {
    return Namespace.of(database.name());
  }

  /**
   * 将 Glue {@link Table} 转换为 Iceberg {@link TableIdentifier}。
   *
   * @param table Glue 表对象
   * @return 由 Glue 库名 + 表名构成的 Iceberg TableIdentifier
   */
  static TableIdentifier toTableId(Table table) {
    return TableIdentifier.of(table.databaseName(), table.name());
  }
}
