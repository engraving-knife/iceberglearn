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
package org.apache.iceberg.delta;

import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.spark.Spark3Util;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.connector.catalog.CatalogPlugin;

/**
 * 文件级说明：DeltaLakeToIcebergMigrationSparkIntegration 集成测试。
 *
 * <p>所属模块：iceberg-delta-lake。职责：验证 Deltalake到Iceberg迁移Spark集成 相关功能，覆盖正常路径与边界场景。
 *
 * <p>测试策略：基于 JUnit 框架，在真实集成环境（如云存储、元数据服务、计算引擎集群）下验证端到端行为。 运行前需配置相应的环境变量、凭证与测试资源。
 */
class DeltaLakeToIcebergMigrationSparkIntegration {

  /** 构造方法：DeltaLakeToIcebergMigrationSparkIntegration。 */
  private DeltaLakeToIcebergMigrationSparkIntegration() {}

  /** 辅助方法：快照Deltalake表。 */
  static SnapshotDeltaLakeTable snapshotDeltaLakeTable(
      SparkSession spark, String newTableIdentifier, String deltaTableLocation) {
    Preconditions.checkArgument(
        spark != null, "The SparkSession cannot be null, please provide a valid SparkSession");
    Preconditions.checkArgument(
        newTableIdentifier != null,
        "The table identifier cannot be null, please provide a valid table identifier for the new iceberg table");
    Preconditions.checkArgument(
        deltaTableLocation != null,
        "The delta lake table location cannot be null, please provide a valid location of the delta lake table to be snapshot");

    String ctx = "delta lake snapshot target";
    CatalogPlugin defaultCatalog = spark.sessionState().catalogManager().currentCatalog();
    Spark3Util.CatalogAndIdentifier catalogAndIdent =
        Spark3Util.catalogAndIdentifier(ctx, spark, newTableIdentifier, defaultCatalog);
    return DeltaLakeToIcebergMigrationActionsProvider.defaultActions()
        .snapshotDeltaLakeTable(deltaTableLocation)
        .as(TableIdentifier.parse(catalogAndIdent.identifier().toString()))
        .deltaLakeConfiguration(spark.sessionState().newHadoopConf())
        .icebergCatalog(Spark3Util.loadIcebergCatalog(spark, catalogAndIdent.catalog().name()));
  }
}
