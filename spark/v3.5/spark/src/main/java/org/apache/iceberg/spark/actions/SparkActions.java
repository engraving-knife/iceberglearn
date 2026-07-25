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
 * Spark 版 {@link ActionsProvider} 实现。
 *
 * <p>所属模块：iceberg-spark（actions 子包）。本类是用户在 Spark 中使用 Iceberg 表维护动作
 * 的主入口，通过工厂方法实例化各类动作（快照、迁移、重写数据文件、删除孤儿文件、重写清单、 过期快照、重写位置删除等）。
 *
 * <p>职责：持有 SparkSession，按需创建并返回对应动作实例，本身不执行具体逻辑。
 *
 * <p>设计意图：作为统一门面（Facade），屏蔽各动作的构造细节，提供一致的获取方式（{@link #get} 或 {@link #get(SparkSession)}），便于上层调用与扩展。
 *
 * <p>上下游关系：实现 Iceberg api 的 ActionsProvider；被 Spark 存储过程与用户代码调用。
 */
public class SparkActions implements ActionsProvider {

  private final SparkSession spark;

  private SparkActions(SparkSession spark) {
    this.spark = spark;
  }

  /** 以指定 SparkSession 创建 SparkActions。 */
  public static SparkActions get(SparkSession spark) {
    return new SparkActions(spark);
  }

  /** 以当前活跃 SparkSession 创建 SparkActions。 */
  public static SparkActions get() {
    return new SparkActions(SparkSession.active());
  }

  /** 将外部表快照为 Iceberg 表：解析标识后构造 {@link SnapshotTableSparkAction}。 */
  @Override
  public SnapshotTableSparkAction snapshotTable(String tableIdent) {
    String ctx = "snapshot source";
    CatalogPlugin defaultCatalog = spark.sessionState().catalogManager().currentCatalog();
    CatalogAndIdentifier catalogAndIdent =
        Spark3Util.catalogAndIdentifier(ctx, spark, tableIdent, defaultCatalog);
    return new SnapshotTableSparkAction(
        spark, catalogAndIdent.catalog(), catalogAndIdent.identifier());
  }

  /** 将外部表原地迁移为 Iceberg 表：解析标识后构造 {@link MigrateTableSparkAction}。 */
  @Override
  public MigrateTableSparkAction migrateTable(String tableIdent) {
    String ctx = "migrate target";
    CatalogPlugin defaultCatalog = spark.sessionState().catalogManager().currentCatalog();
    CatalogAndIdentifier catalogAndIdent =
        Spark3Util.catalogAndIdentifier(ctx, spark, tableIdent, defaultCatalog);
    return new MigrateTableSparkAction(
        spark, catalogAndIdent.catalog(), catalogAndIdent.identifier());
  }

  /** 重写指定表的数据文件（合并小文件/重排），返回 {@link RewriteDataFilesSparkAction}。 */
  @Override
  public RewriteDataFilesSparkAction rewriteDataFiles(Table table) {
    return new RewriteDataFilesSparkAction(spark, table);
  }

  /** 删除指定表的孤儿文件，返回 {@link DeleteOrphanFilesSparkAction}。 */
  @Override
  public DeleteOrphanFilesSparkAction deleteOrphanFiles(Table table) {
    return new DeleteOrphanFilesSparkAction(spark, table);
  }

  /** 重写指定表的清单，返回 {@link RewriteManifestsSparkAction}。 */
  @Override
  public RewriteManifestsSparkAction rewriteManifests(Table table) {
    return new RewriteManifestsSparkAction(spark, table);
  }

  /** 过期指定表的旧快照，返回 {@link ExpireSnapshotsSparkAction}。 */
  @Override
  public ExpireSnapshotsSparkAction expireSnapshots(Table table) {
    return new ExpireSnapshotsSparkAction(spark, table);
  }

  /** 删除给定元数据位置可达的文件，返回 {@link DeleteReachableFilesSparkAction}。 */
  @Override
  public DeleteReachableFilesSparkAction deleteReachableFiles(String metadataLocation) {
    return new DeleteReachableFilesSparkAction(spark, metadataLocation);
  }

  /** 重写指定表的位置删除文件，返回 {@link RewritePositionDeleteFilesSparkAction}。 */
  @Override
  public RewritePositionDeleteFilesSparkAction rewritePositionDeletes(Table table) {
    return new RewritePositionDeleteFilesSparkAction(spark, table);
  }
}
