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
import org.apache.iceberg.actions.ImmutableMigrateTable;
import org.apache.iceberg.actions.MigrateTable;
import org.apache.iceberg.exceptions.AlreadyExistsException;
import org.apache.iceberg.exceptions.NoSuchTableException;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.spark.JobGroupInfo;
import org.apache.iceberg.spark.SparkSessionCatalog;
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
import scala.Some;
import scala.collection.JavaConverters;

/**
 * 所属模块：iceberg-spark v3.4
 *
 * <p>职责：迁移表的 Spark 动作，将已有的非 Iceberg 表（如 Parquet/ORC）就地迁移为 Iceberg 表。
 *
 * <p>设计意图：通过列举原表文件并以 Iceberg 元数据注册，实现无数据拷贝的就地迁移。
 *
 * <p>上下游关系：由 SparkActions 创建；继承 BaseTableCreationSparkAction。
 */
public class MigrateTableSparkAction extends BaseTableCreationSparkAction<MigrateTableSparkAction>
    implements MigrateTable {

  private static final Logger LOG = LoggerFactory.getLogger(MigrateTableSparkAction.class);
  private static final String BACKUP_SUFFIX = "_BACKUP_";

  private final StagingTableCatalog destCatalog;
  private final Identifier destTableIdent;

  private Identifier backupIdent;
  private boolean dropBackup = false;

  MigrateTableSparkAction(
      SparkSession spark, CatalogPlugin sourceCatalog, Identifier sourceTableIdent) {
    super(spark, sourceCatalog, sourceTableIdent);
    this.destCatalog = checkDestinationCatalog(sourceCatalog);
    this.destTableIdent = sourceTableIdent;
    String backupName = sourceTableIdent.name() + BACKUP_SUFFIX;
    this.backupIdent = Identifier.of(sourceTableIdent.namespace(), backupName);
  }
  /** 执行 self 相关操作。 */
  @Override
  protected MigrateTableSparkAction self() {
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
  /** 执行 tableProperties 相关操作。 */
  @Override
  public MigrateTableSparkAction tableProperties(Map<String, String> properties) {
    setProperties(properties);
    return this;
  }
  /** 执行 tableProperty 相关操作。 */
  @Override
  public MigrateTableSparkAction tableProperty(String property, String value) {
    setProperty(property, value);
    return this;
  }
  /** 执行 dropBackup 相关操作。 */
  @Override
  public MigrateTableSparkAction dropBackup() {
    this.dropBackup = true;
    return this;
  }
  /** 执行 backupTableName 相关操作。 */
  @Override
  public MigrateTableSparkAction backupTableName(String tableName) {
    this.backupIdent = Identifier.of(sourceTableIdent().namespace(), tableName);
    return this;
  }
  /** 执行动作并返回结果。 */
  @Override
  public MigrateTable.Result execute() {
    String desc = String.format("Migrating table %s", destTableIdent().toString());
    JobGroupInfo info = newJobGroupInfo("MIGRATE-TABLE", desc);
    return withJobGroupInfo(info, this::doExecute);
  }
  /** 执行 doExecute 相关操作。 */
  private MigrateTable.Result doExecute() {
    LOG.info("Starting the migration of {} to Iceberg", sourceTableIdent());

    // move the source table to a new name, halting all modifications and allowing us to stage
    // the creation of a new Iceberg table in its place
    renameAndBackupSourceTable();

    StagedSparkTable stagedTable = null;
    Table icebergTable;
    boolean threw = true;
    try {
      LOG.info("Staging a new Iceberg table {}", destTableIdent());
      stagedTable = stageDestTable();
      icebergTable = stagedTable.table();

      LOG.info("Ensuring {} has a valid name mapping", destTableIdent());
      ensureNameMappingPresent(icebergTable);

      Some<String> backupNamespace = Some.apply(backupIdent.namespace()[0]);
      TableIdentifier v1BackupIdent = new TableIdentifier(backupIdent.name(), backupNamespace);
      String stagingLocation = getMetadataLocation(icebergTable);
      LOG.info("Generating Iceberg metadata for {} in {}", destTableIdent(), stagingLocation);
      SparkTableUtil.importSparkTable(spark(), v1BackupIdent, icebergTable, stagingLocation);

      LOG.info("Committing staged changes to {}", destTableIdent());
      stagedTable.commitStagedChanges();
      threw = false;
    } finally {
      if (threw) {
        LOG.error(
            "Failed to perform the migration, aborting table creation and restoring the original table");

        restoreSourceTable();

        if (stagedTable != null) {
          try {
            stagedTable.abortStagedChanges();
          } catch (Exception abortException) {
            LOG.error("Cannot abort staged changes", abortException);
          }
        }
      } else if (dropBackup) {
        dropBackupTable();
      }
    }

    Snapshot snapshot = icebergTable.currentSnapshot();
    long migratedDataFilesCount =
        Long.parseLong(snapshot.summary().get(SnapshotSummary.TOTAL_DATA_FILES_PROP));
    LOG.info(
        "Successfully loaded Iceberg metadata for {} files to {}",
        migratedDataFilesCount,
        destTableIdent());
    return ImmutableMigrateTable.Result.builder()
        .migratedDataFilesCount(migratedDataFilesCount)
        .build();
  }
  /** 执行 destTableProps 相关操作。 */
  @Override
  protected Map<String, String> destTableProps() {
    Map<String, String> properties = Maps.newHashMap();

    // copy over relevant source table props
    properties.putAll(JavaConverters.mapAsJavaMapConverter(v1SourceTable().properties()).asJava());
    EXCLUDED_PROPERTIES.forEach(properties::remove);

    // set default and user-provided props
    properties.put(TableCatalog.PROP_PROVIDER, "iceberg");
    properties.putAll(additionalProperties());

    // make sure we mark this table as migrated
    properties.put("migrated", "true");

    // inherit the source table location
    properties.putIfAbsent(LOCATION, sourceTableLocation());

    return properties;
  }
  /** 执行 checkSourceCatalog 相关操作。 */
  @Override
  protected TableCatalog checkSourceCatalog(CatalogPlugin catalog) {
    // currently the import code relies on being able to look up the table in the session catalog
    Preconditions.checkArgument(
        catalog instanceof SparkSessionCatalog,
        "Cannot migrate a table from a non-Iceberg Spark Session Catalog. Found %s of class %s as the source catalog.",
        catalog.name(),
        catalog.getClass().getName());

    return (TableCatalog) catalog;
  }
  /** 执行 renameAndBackupSourceTable 相关操作。 */
  private void renameAndBackupSourceTable() {
    try {
      LOG.info("Renaming {} as {} for backup", sourceTableIdent(), backupIdent);
      destCatalog().renameTable(sourceTableIdent(), backupIdent);

    } catch (org.apache.spark.sql.catalyst.analysis.NoSuchTableException e) {
      throw new NoSuchTableException("Cannot find source table %s", sourceTableIdent());

    } catch (org.apache.spark.sql.catalyst.analysis.TableAlreadyExistsException e) {
      throw new AlreadyExistsException(
          "Cannot rename %s as %s for backup. The backup table already exists.",
          sourceTableIdent(), backupIdent);
    }
  }
  /** 执行 restoreSourceTable 相关操作。 */
  private void restoreSourceTable() {
    try {
      LOG.info("Restoring {} from {}", sourceTableIdent(), backupIdent);
      destCatalog().renameTable(backupIdent, sourceTableIdent());

    } catch (org.apache.spark.sql.catalyst.analysis.NoSuchTableException e) {
      LOG.error(
          "Cannot restore the original table, the backup table {} cannot be found", backupIdent, e);

    } catch (org.apache.spark.sql.catalyst.analysis.TableAlreadyExistsException e) {
      LOG.error(
          "Cannot restore the original table, a table with the original name exists. "
              + "Use the backup table {} to restore the original table manually.",
          backupIdent,
          e);
    }
  }
  /** 执行 dropBackupTable 相关操作。 */
  private void dropBackupTable() {
    try {
      destCatalog().dropTable(backupIdent);
    } catch (Exception e) {
      LOG.error(
          "Cannot drop the backup table {}, after the migration is completed.", backupIdent, e);
    }
  }
}
