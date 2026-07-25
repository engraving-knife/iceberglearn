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

import java.util.Map;
import org.apache.iceberg.Snapshot;
import org.apache.iceberg.SnapshotSummary;
import org.apache.iceberg.Table;
import org.apache.iceberg.TableProperties;
import org.apache.iceberg.actions.ImmutableSnapshotTable;
import org.apache.iceberg.actions.SnapshotTable;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.spark.JobGroupInfo;
import org.apache.iceberg.spark.Spark3Util;
import org.apache.iceberg.spark.Spark3Util.CatalogAndIdentifier;
import org.apache.iceberg.spark.SparkTableUtil;
import org.apache.iceberg.spark.source.StagedSparkTable;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.catalyst.TableIdentifier;
import org.apache.spark.sql.connector.catalog.CatalogPlugin;
import org.apache.spark.sql.connector.catalog.Identifier;
import org.apache.spark.sql.connector.catalog.StagingTableCatalog;
import org.apache.spark.sql.connector.catalog.TableCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.collection.JavaConverters;

/**
 * 基于 Spark 的"快照表"创建 action。
 *
 * <p>所属模块：iceberg-spark（Spark v3.5 集成模块），actions 子包。
 *
 * <p>职责：基于已存在的 Spark 源表（如 Hive 表）创建一个新的 Iceberg 表，新表使用独立的数据与 元数据目录，可与源表互不影响地共存。本质上是对源表做一次 Iceberg
 * 元数据导入。
 *
 * <p>设计意图：通过 StagingTableCatalog 的两阶段提交（stage -> commitStagedChanges）保证原子性， 失败时调用
 * abortStagedChanges 回滚；强制源表位于 spark_catalog，依赖 {@link SparkTableUtil#importSparkTable}
 * 完成文件级元数据导入。新表标记 gc.enabled=false， 避免误删源表仍引用的文件。
 *
 * <p>上下游关系：继承 {@link BaseTableCreationSparkAction}，实现 {@link SnapshotTable}； 被 Spark 过程 {@code
 * snapshot} 调用；依赖 Spark Catalog API 与 Iceberg core 的 StagedSparkTable。
 */
public class SnapshotTableSparkAction extends BaseTableCreationSparkAction<SnapshotTableSparkAction>
    implements SnapshotTable {

  private static final Logger LOG = LoggerFactory.getLogger(SnapshotTableSparkAction.class);

  private StagingTableCatalog destCatalog;
  private Identifier destTableIdent;
  private String destTableLocation = null;

  /** 构造快照表 action，需指定 SparkSession、源 catalog 与源表标识符。 */
  SnapshotTableSparkAction(
      SparkSession spark, CatalogPlugin sourceCatalog, Identifier sourceTableIdent) {
    super(spark, sourceCatalog, sourceTableIdent);
  }

  /** CRTP 钩子，返回 this。 */
  @Override
  protected SnapshotTableSparkAction self() {
    return this;
  }
  /** 执行 destCatalog 相关操作。 */
  @Override
  protected StagingTableCatalog destCatalog() {
    return destCatalog;
  }
  /** 执行 destTableIdent 相关操作。 */
  @Override
  protected Identifier destTableIdent() {
    return destTableIdent;
  }

  /** 指定目标表的标识符字符串（catalog.database.table），解析后设置目标 catalog 与标识符。 */
  @Override
  public SnapshotTableSparkAction as(String ident) {
    String ctx = "snapshot destination";
    CatalogPlugin defaultCatalog = spark().sessionState().catalogManager().currentCatalog();
    CatalogAndIdentifier catalogAndIdent =
        Spark3Util.catalogAndIdentifier(ctx, spark(), ident, defaultCatalog);
    this.destCatalog = checkDestinationCatalog(catalogAndIdent.catalog());
    this.destTableIdent = catalogAndIdent.identifier();
    return this;
  }

  /** 批量设置目标表属性。 */
  @Override
  public SnapshotTableSparkAction tableProperties(Map<String, String> properties) {
    setProperties(properties);
    return this;
  }

  /** 设置单个目标表属性。 */
  @Override
  public SnapshotTableSparkAction tableProperty(String property, String value) {
    setProperty(property, value);
    return this;
  }

  /** 在 SNAPSHOT-TABLE 作业组下执行快照建表。 */
  @Override
  public SnapshotTable.Result execute() {
    String desc = String.format("Snapshotting table %s as %s", sourceTableIdent(), destTableIdent);
    JobGroupInfo info = newJobGroupInfo("SNAPSHOT-TABLE", desc);
    return withJobGroupInfo(info, this::doExecute);
  }

  /**
   * 实际执行快照建表流程。
   *
   * <p>逻辑：校验目标 catalog/标识符非空；stage 目标 Iceberg 表；确保 name mapping 存在； 通过
   * SparkTableUtil.importSparkTable 把源表文件导入为 Iceberg 元数据； commitStagedChanges 提交；任何异常都触发
   * abortStagedChanges 回滚。 最后从快照摘要读取导入的数据文件数。
   *
   * @return 含导入数据文件数的结果
   */
  private SnapshotTable.Result doExecute() {
    Preconditions.checkArgument(
        destCatalog() != null && destTableIdent() != null,
        "The destination catalog and identifier cannot be null. "
            + "Make sure to configure the action with a valid destination table identifier via the `as` method.");

    LOG.info(
        "Staging a new Iceberg table {} as a snapshot of {}", destTableIdent(), sourceTableIdent());
    StagedSparkTable stagedTable = stageDestTable();
    Table icebergTable = stagedTable.table();

    // TODO: Check the dest table location does not overlap with the source table location

    boolean threw = true;
    try {
      LOG.info("Ensuring {} has a valid name mapping", destTableIdent());
      ensureNameMappingPresent(icebergTable);

      TableIdentifier v1TableIdent = v1SourceTable().identifier();
      String stagingLocation = getMetadataLocation(icebergTable);
      LOG.info("Generating Iceberg metadata for {} in {}", destTableIdent(), stagingLocation);
      SparkTableUtil.importSparkTable(spark(), v1TableIdent, icebergTable, stagingLocation);

      LOG.info("Committing staged changes to {}", destTableIdent());
      stagedTable.commitStagedChanges();
      threw = false;
    } finally {
      if (threw) {
        LOG.error("Error when populating the staged table with metadata, aborting changes");

        try {
          stagedTable.abortStagedChanges();
        } catch (Exception abortException) {
          LOG.error("Cannot abort staged changes", abortException);
        }
      }
    }

    Snapshot snapshot = icebergTable.currentSnapshot();
    long importedDataFilesCount =
        Long.parseLong(snapshot.summary().get(SnapshotSummary.TOTAL_DATA_FILES_PROP));
    LOG.info(
        "Successfully loaded Iceberg metadata for {} files to {}",
        importedDataFilesCount,
        destTableIdent());
    return ImmutableSnapshotTable.Result.builder()
        .importedDataFilesCount(importedDataFilesCount)
        .build();
  }

  /**
   * 构造目标表属性。
   *
   * <p>逻辑：拷贝源表相关属性并移除被排除项与所有 location 类属性；设置 provider=iceberg、 用户自定义属性；标记 gc.enabled=false 与
   * snapshot=true 防止误删；可选设置目标表 location。
   */
  @Override
  protected Map<String, String> destTableProps() {
    Map<String, String> properties = Maps.newHashMap();

    // copy over relevant source table props
    properties.putAll(JavaConverters.mapAsJavaMapConverter(v1SourceTable().properties()).asJava());
    EXCLUDED_PROPERTIES.forEach(properties::remove);

    // remove any possible location properties from origin properties
    properties.remove(LOCATION);
    properties.remove(TableProperties.WRITE_METADATA_LOCATION);
    properties.remove(TableProperties.WRITE_FOLDER_STORAGE_LOCATION);
    properties.remove(TableProperties.OBJECT_STORE_PATH);
    properties.remove(TableProperties.WRITE_DATA_LOCATION);

    // set default and user-provided props
    properties.put(TableCatalog.PROP_PROVIDER, "iceberg");
    properties.putAll(additionalProperties());

    // make sure we mark this table as a snapshot table
    properties.put(TableProperties.GC_ENABLED, "false");
    properties.put("snapshot", "true");

    // set the destination table location if provided
    if (destTableLocation != null) {
      properties.put(LOCATION, destTableLocation);
    }

    return properties;
  }

  /** 校验源 catalog 必须是 spark_catalog 且为 TableCatalog，否则抛出非法参数异常。 */
  @Override
  protected TableCatalog checkSourceCatalog(CatalogPlugin catalog) {
    // currently the import code relies on being able to look up the table in the session catalog
    Preconditions.checkArgument(
        catalog.name().equalsIgnoreCase("spark_catalog"),
        "Cannot snapshot a table that isn't in the session catalog (i.e. spark_catalog). "
            + "Found source catalog: %s.",
        catalog.name());

    Preconditions.checkArgument(
        catalog instanceof TableCatalog,
        "Cannot snapshot as catalog %s of class %s in not a table catalog",
        catalog.name(),
        catalog.getClass().getName());

    return (TableCatalog) catalog;
  }

  /** 指定目标表 location，校验不能与源表 location 相同，避免文件混放。 */
  @Override
  public SnapshotTableSparkAction tableLocation(String location) {
    Preconditions.checkArgument(
        !sourceTableLocation().equals(location),
        "The snapshot table location cannot be same as the source table location. "
            + "This would mix snapshot table files with original table files.");
    this.destTableLocation = location;
    return this;
  }
}
