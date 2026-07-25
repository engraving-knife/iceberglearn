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
 * 基于 Spark 执行的 Iceberg 表维护动作，执行快照过期、文件清理、数据压缩等表维护操作。
 *
 * <p>所属模块：iceberg-spark v3.3。 类型：类 SparkActions。
 *
 * <p>上下游：由 SparkActions 创建，委托 Spark 作业执行实际数据处理。
 */
public class SparkActions implements ActionsProvider {

  private final SparkSession spark;

  /** 构造 SparkActions 实例。 */
  private SparkActions(SparkSession spark) {
    this.spark = spark;
  }

  /** 执行该方法的具体逻辑。 */
  public static SparkActions get(SparkSession spark) {
    /** 执行该方法的具体逻辑。 */
    return new SparkActions(spark);
  }

  /** 执行该方法的具体逻辑。 */
  public static SparkActions get() {
    /** 执行该方法的具体逻辑。 */
    return new SparkActions(SparkSession.active());
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @param tableIdent 参数
   * @return 结果对象
   */
  @Override
  public SnapshotTableSparkAction snapshotTable(String tableIdent) {
    String ctx = "snapshot source";
    CatalogPlugin defaultCatalog = spark.sessionState().catalogManager().currentCatalog();
    CatalogAndIdentifier catalogAndIdent =
        Spark3Util.catalogAndIdentifier(ctx, spark, tableIdent, defaultCatalog);
    /** 执行该方法的具体逻辑。 */
    return new SnapshotTableSparkAction(
        spark, catalogAndIdent.catalog(), catalogAndIdent.identifier());
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @param tableIdent 参数
   * @return 结果对象
   */
  @Override
  public MigrateTableSparkAction migrateTable(String tableIdent) {
    String ctx = "migrate target";
    CatalogPlugin defaultCatalog = spark.sessionState().catalogManager().currentCatalog();
    CatalogAndIdentifier catalogAndIdent =
        Spark3Util.catalogAndIdentifier(ctx, spark, tableIdent, defaultCatalog);
    /** 执行该方法的具体逻辑。 */
    return new MigrateTableSparkAction(
        spark, catalogAndIdent.catalog(), catalogAndIdent.identifier());
  }

  /**
   * 重写计划或文件。
   *
   * @param table 参数
   * @return 结果对象
   */
  @Override
  public RewriteDataFilesSparkAction rewriteDataFiles(Table table) {
    /** 重写计划或文件。 */
    return new RewriteDataFilesSparkAction(spark, table);
  }

  /**
   * 删除数据或文件。
   *
   * @param table 参数
   * @return 结果对象
   */
  @Override
  public DeleteOrphanFilesSparkAction deleteOrphanFiles(Table table) {
    /** 删除数据或文件。 */
    return new DeleteOrphanFilesSparkAction(spark, table);
  }

  /**
   * 重写计划或文件。
   *
   * @param table 参数
   * @return 结果对象
   */
  @Override
  public RewriteManifestsSparkAction rewriteManifests(Table table) {
    /** 重写计划或文件。 */
    return new RewriteManifestsSparkAction(spark, table);
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @param table 参数
   * @return 结果对象
   */
  @Override
  public ExpireSnapshotsSparkAction expireSnapshots(Table table) {
    /** 执行该方法的具体逻辑。 */
    return new ExpireSnapshotsSparkAction(spark, table);
  }

  /**
   * 删除数据或文件。
   *
   * @param metadataLocation 参数
   * @return 结果对象
   */
  @Override
  public DeleteReachableFilesSparkAction deleteReachableFiles(String metadataLocation) {
    /** 删除数据或文件。 */
    return new DeleteReachableFilesSparkAction(spark, metadataLocation);
  }

  /**
   * 重写计划或文件。
   *
   * @param table 参数
   * @return 结果对象
   */
  @Override
  public RewritePositionDeleteFilesSparkAction rewritePositionDeletes(Table table) {
    /** 重写计划或文件。 */
    return new RewritePositionDeleteFilesSparkAction(spark, table);
  }
}
