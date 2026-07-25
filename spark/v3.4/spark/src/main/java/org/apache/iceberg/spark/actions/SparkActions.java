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
package org.apache.iceberg.spark.actions;

import org.apache.iceberg.Table;
import org.apache.iceberg.actions.ActionsProvider;
import org.apache.iceberg.spark.Spark3Util;
import org.apache.iceberg.spark.Spark3Util.CatalogAndIdentifier;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.connector.catalog.CatalogPlugin;

/**
 * 所属模块：iceberg-spark v3.4
 *
 * <p>职责：Spark 动作入口，提供创建各类 Iceberg 维护动作（expire/rewrite/delete 等）的工厂方法。
 *
 * <p>设计意图：采用工厂模式，按动作类型创建对应 SparkAction 实例。
 *
 * <p>上下游关系：由 SparkCatalog / Spark3Util / 用户代码调用；产出各 SparkAction。
 */
public class SparkActions implements ActionsProvider {

  private final SparkSession spark;

  private SparkActions(SparkSession spark) {
    this.spark = spark;
  }
  /** 返回值。 */
  public static SparkActions get(SparkSession spark) {
    return new SparkActions(spark);
  }
  /** 返回值。 */
  public static SparkActions get() {
    return new SparkActions(SparkSession.active());
  }
  /** 执行 snapshotTable 相关操作。 */
  @Override
  public SnapshotTableSparkAction snapshotTable(String tableIdent) {
    String ctx = "snapshot source";
    CatalogPlugin defaultCatalog = spark.sessionState().catalogManager().currentCatalog();
    CatalogAndIdentifier catalogAndIdent =
        Spark3Util.catalogAndIdentifier(ctx, spark, tableIdent, defaultCatalog);
    return new SnapshotTableSparkAction(
        spark, catalogAndIdent.catalog(), catalogAndIdent.identifier());
  }
  /** 执行 migrateTable 相关操作。 */
  @Override
  public MigrateTableSparkAction migrateTable(String tableIdent) {
    String ctx = "migrate target";
    CatalogPlugin defaultCatalog = spark.sessionState().catalogManager().currentCatalog();
    CatalogAndIdentifier catalogAndIdent =
        Spark3Util.catalogAndIdentifier(ctx, spark, tableIdent, defaultCatalog);
    return new MigrateTableSparkAction(
        spark, catalogAndIdent.catalog(), catalogAndIdent.identifier());
  }
  /** 执行 rewriteDataFiles 相关操作。 */
  @Override
  public RewriteDataFilesSparkAction rewriteDataFiles(Table table) {
    return new RewriteDataFilesSparkAction(spark, table);
  }
  /** 执行 deleteOrphanFiles 相关操作。 */
  @Override
  public DeleteOrphanFilesSparkAction deleteOrphanFiles(Table table) {
    return new DeleteOrphanFilesSparkAction(spark, table);
  }
  /** 执行 rewriteManifests 相关操作。 */
  @Override
  public RewriteManifestsSparkAction rewriteManifests(Table table) {
    return new RewriteManifestsSparkAction(spark, table);
  }
  /** 执行 expireSnapshots 相关操作。 */
  @Override
  public ExpireSnapshotsSparkAction expireSnapshots(Table table) {
    return new ExpireSnapshotsSparkAction(spark, table);
  }
  /** 执行 deleteReachableFiles 相关操作。 */
  @Override
  public DeleteReachableFilesSparkAction deleteReachableFiles(String metadataLocation) {
    return new DeleteReachableFilesSparkAction(spark, metadataLocation);
  }
  /** 执行 rewritePositionDeletes 相关操作。 */
  @Override
  public RewritePositionDeleteFilesSparkAction rewritePositionDeletes(Table table) {
    return new RewritePositionDeleteFilesSparkAction(spark, table);
  }
}
