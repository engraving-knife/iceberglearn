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
package org.apache.iceberg.hive;

import static org.apache.iceberg.TableProperties.GC_ENABLED;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hive.common.StatsSetupConst;
import org.apache.hadoop.hive.metastore.IMetaStoreClient;
import org.apache.hadoop.hive.metastore.TableType;
import org.apache.hadoop.hive.metastore.api.InvalidObjectException;
import org.apache.hadoop.hive.metastore.api.NoSuchObjectException;
import org.apache.hadoop.hive.metastore.api.SerDeInfo;
import org.apache.hadoop.hive.metastore.api.StorageDescriptor;
import org.apache.hadoop.hive.metastore.api.Table;
import org.apache.hadoop.hive.metastore.api.hive_metastoreConstants;
import org.apache.iceberg.BaseMetastoreTableOperations;
import org.apache.iceberg.ClientPool;
import org.apache.iceberg.PartitionSpecParser;
import org.apache.iceberg.SchemaParser;
import org.apache.iceberg.Snapshot;
import org.apache.iceberg.SnapshotSummary;
import org.apache.iceberg.SortOrderParser;
import org.apache.iceberg.TableMetadata;
import org.apache.iceberg.TableProperties;
import org.apache.iceberg.exceptions.AlreadyExistsException;
import org.apache.iceberg.exceptions.CommitFailedException;
import org.apache.iceberg.exceptions.CommitStateUnknownException;
import org.apache.iceberg.exceptions.NoSuchIcebergTableException;
import org.apache.iceberg.exceptions.NoSuchTableException;
import org.apache.iceberg.exceptions.ValidationException;
import org.apache.iceberg.hadoop.ConfigProperties;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.relocated.com.google.common.annotations.VisibleForTesting;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.BiMap;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableBiMap;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.util.JsonUtil;
import org.apache.thrift.TException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于 Hive Metastore 的 Iceberg 表操作实现。
 *
 * <p>所属模块：iceberg-hive-metastore（该模块的核心类，位于 Iceberg 表元数据读写层）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>继承 {@link BaseMetastoreTableOperations}，实现 Iceberg 表元数据在 Hive Metastore 中的
 *       持久化与刷新（refresh/commit）。
 *   <li>将 Iceberg 的 Schema、PartitionSpec、SortOrder、Snapshot 统计等信息写入 HMS 表属性， 供 Hive 引擎读取。
 *   <li>在提交时通过 {@link HiveLock} 做并发控制，保证 metadata location 的原子更新。
 *   <li>管理 HMS 表的 StorageDescriptor（InputFormat/OutputFormat/SerDe）。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>metadata location 提交协议：Iceberg 通过在 HMS 表属性中记录 metadata_location 实现 原子提交，提交前先写新元数据文件，再 CAS
 *       式更新 HMS 表属性。
 *   <li>锁机制可选：通过 hiveLockEnabled 决定使用 {@link MetastoreLock}（HMS 锁+心跳）还是 {@link NoLock}（依赖 HMS
 *       alter_table 的条件更新，HIVE-26882 方案）。
 *   <li>HMS 属性大小限制：部分 Hive 元数据（schema/snapshot summary）序列化后写入 HMS 表属性， 受 HMS 参数大小上限约束，超过则不暴露并告警。
 *   <li>属性翻译：gc.enabled 与 external.table.purge 等含义相同但名称不同的属性需双向同步。
 * </ul>
 *
 * <p>上下游关系：由 {@link HiveCatalog#newTableOps} 创建；委托 {@link CachedClientPool} 执行 HMS Thrift 调用，委托
 * {@link HiveSchemaUtil} 做 Schema 转换，委托 {@link MetastoreUtil} 做 alter_table；被 Spark/Flink 等引擎通过
 * Catalog 间接使用。
 */
public class HiveTableOperations extends BaseMetastoreTableOperations {
  private static final Logger LOG = LoggerFactory.getLogger(HiveTableOperations.class);

  private static final String HIVE_ICEBERG_METADATA_REFRESH_MAX_RETRIES =
      "iceberg.hive.metadata-refresh-max-retries";
  // the max size is based on HMS backend database. For Hive versions below 2.3, the max table
  // parameter size is 4000
  // characters, see https://issues.apache.org/jira/browse/HIVE-12274
  // set to 0 to not expose Iceberg metadata in HMS Table properties.
  private static final String HIVE_TABLE_PROPERTY_MAX_SIZE = "iceberg.hive.table-property-max-size";
  private static final String NO_LOCK_EXPECTED_KEY = "expected_parameter_key";
  private static final String NO_LOCK_EXPECTED_VALUE = "expected_parameter_value";
  private static final long HIVE_TABLE_PROPERTY_MAX_SIZE_DEFAULT = 32672;
  private static final int HIVE_ICEBERG_METADATA_REFRESH_MAX_RETRIES_DEFAULT = 2;
  private static final BiMap<String, String> ICEBERG_TO_HMS_TRANSLATION =
      ImmutableBiMap.of(
          // gc.enabled in Iceberg and external.table.purge in Hive are meant to do the same things
          // but with different names
          GC_ENABLED, "external.table.purge");

  /**
   * 将 HMS 属性名翻译为对应的 Iceberg 属性名。
   *
   * <p>设计意图：部分属性在 Iceberg 和 Hive 中含义相同但名称不同（如 gc.enabled 对应 external.table.purge），需双向同步以避免行为不一致。例如
   * Hive 用户设置了 external.table.purge=true，而 Iceberg 侧 gc.enabled=false，会导致 DROP TABLE 时数据文件 删除行为矛盾。
   *
   * @param hmsProp HMS 属性名
   * @return 等价的 Iceberg 属性名；无翻译映射时原样返回
   */
  public static String translateToIcebergProp(String hmsProp) {
    return ICEBERG_TO_HMS_TRANSLATION.inverse().getOrDefault(hmsProp, hmsProp);
  }

  private final String fullName;
  private final String catalogName;
  private final String database;
  private final String tableName;
  private final Configuration conf;
  private final long maxHiveTablePropertySize;
  private final int metadataRefreshMaxRetries;
  private final FileIO fileIO;
  private final ClientPool<IMetaStoreClient, TException> metaClients;

  /**
   * 构造 Hive 表操作实例。
   *
   * <p>逻辑：保存配置、客户端池、FileIO 与表标识信息，并从配置读取元数据刷新重试次数和 HMS 表属性大小上限。
   *
   * @param conf Hadoop 配置
   * @param metaClients HMS 客户端池
   * @param fileIO 文件 IO（用于读写元数据文件）
   * @param catalogName Catalog 名称
   * @param database database 名
   * @param table 表名
   */
  protected HiveTableOperations(
      Configuration conf,
      ClientPool metaClients,
      FileIO fileIO,
      String catalogName,
      String database,
      String table) {
    this.conf = conf;
    this.metaClients = metaClients;
    this.fileIO = fileIO;
    this.fullName = catalogName + "." + database + "." + table;
    this.catalogName = catalogName;
    this.database = database;
    this.tableName = table;
    this.metadataRefreshMaxRetries =
        conf.getInt(
            HIVE_ICEBERG_METADATA_REFRESH_MAX_RETRIES,
            HIVE_ICEBERG_METADATA_REFRESH_MAX_RETRIES_DEFAULT);
    this.maxHiveTablePropertySize =
        conf.getLong(HIVE_TABLE_PROPERTY_MAX_SIZE, HIVE_TABLE_PROPERTY_MAX_SIZE_DEFAULT);
  }

  /** 返回表的全限定名（catalog.database.table），用于日志与错误信息。 */
  @Override
  protected String tableName() {
    return fullName;
  }

  /** 返回该表使用的 {@link FileIO}。 */
  @Override
  public FileIO io() {
    return fileIO;
  }

  /**
   * 从 Hive Metastore 刷新表元数据。
   *
   * <p>逻辑：通过 HMS getTable 获取表对象，校验其为 Iceberg 表，提取 metadata_location 属性；
   * 表不存在且当前无元数据时视为新表（不抛异常），表不存在但已有元数据时抛 NoSuchTableException； 最后委托父类 {@code
   * refreshFromMetadataLocation} 加载元数据文件（含重试）。
   *
   * @throws NoSuchTableException 表不存在且此前已加载过元数据
   */
  @Override
  protected void doRefresh() {
    String metadataLocation = null;
    try {
      Table table = metaClients.run(client -> client.getTable(database, tableName));
      validateTableIsIceberg(table, fullName);

      metadataLocation = table.getParameters().get(METADATA_LOCATION_PROP);

    } catch (NoSuchObjectException e) {
      if (currentMetadataLocation() != null) {
        throw new NoSuchTableException("No such table: %s.%s", database, tableName);
      }

    } catch (TException e) {
      String errMsg =
          String.format("Failed to get table info from metastore %s.%s", database, tableName);
      throw new RuntimeException(errMsg, e);

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Interrupted during refresh", e);
    }

    refreshFromMetadataLocation(metadataLocation, metadataRefreshMaxRetries);
  }

  /**
   * 将新的表元数据提交到 Hive Metastore。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>写入新元数据文件，判断是否为建表（base==null）。
   *   <li>获取锁对象（{@link MetastoreLock} 或 {@link NoLock}）并加锁。
   *   <li>加载 HMS 表：存在则更新，不存在则新建。
   *   <li>校验 base metadata location 与 HMS 表当前 metadata location 一致（乐观并发控制）。
   *   <li>设置 HMS 表属性（metadata location、schema、snapshot 统计等）。
   *   <li>锁保活后调用 {@link #persistTable} 持久化。
   *   <li>处理各类异常：锁心跳失败→CommitStateUnknown；AlreadyExists→AlreadyExistsException；
   *       并发修改→CommitFailedException；HIVE_LOCKS 表缺失→提示使用非嵌入 Metastore； 其他异常→检查提交状态后决定是否重抛。
   *   <li>finally 中按提交状态清理未提交的元数据文件并释放锁。
   * </ol>
   *
   * @param base 提交前的表元数据（建表时为 null）
   * @param metadata 提交后的新表元数据
   * @throws CommitFailedException 并发提交冲突
   * @throws CommitStateUnknownException 锁心跳失败导致提交状态不确定
   * @throws AlreadyExistsException 建表时表已存在
   */
  @SuppressWarnings("checkstyle:CyclomaticComplexity")
  @Override
  protected void doCommit(TableMetadata base, TableMetadata metadata) {
    boolean newTable = base == null;
    String newMetadataLocation = writeNewMetadataIfRequired(newTable, metadata);
    boolean hiveEngineEnabled = hiveEngineEnabled(metadata, conf);
    boolean keepHiveStats = conf.getBoolean(ConfigProperties.KEEP_HIVE_STATS, false);

    CommitStatus commitStatus = CommitStatus.FAILURE;
    boolean updateHiveTable = false;

    HiveLock lock = lockObject(metadata);
    try {
      lock.lock();

      Table tbl = loadHmsTable();

      if (tbl != null) {
        // If we try to create the table but the metadata location is already set, then we had a
        // concurrent commit
        if (newTable
            && tbl.getParameters().get(BaseMetastoreTableOperations.METADATA_LOCATION_PROP)
                != null) {
          throw new AlreadyExistsException("Table already exists: %s.%s", database, tableName);
        }

        updateHiveTable = true;
        LOG.debug("Committing existing table: {}", fullName);
      } else {
        tbl = newHmsTable(metadata);
        LOG.debug("Committing new table: {}", fullName);
      }

      tbl.setSd(storageDescriptor(metadata, hiveEngineEnabled)); // set to pickup any schema changes

      String metadataLocation = tbl.getParameters().get(METADATA_LOCATION_PROP);
      String baseMetadataLocation = base != null ? base.metadataFileLocation() : null;
      if (!Objects.equals(baseMetadataLocation, metadataLocation)) {
        throw new CommitFailedException(
            "Base metadata location '%s' is not same as the current table metadata location '%s' for %s.%s",
            baseMetadataLocation, metadataLocation, database, tableName);
      }

      // get Iceberg props that have been removed
      Set<String> removedProps = Collections.emptySet();
      if (base != null) {
        removedProps =
            base.properties().keySet().stream()
                .filter(key -> !metadata.properties().containsKey(key))
                .collect(Collectors.toSet());
      }

      Map<String, String> summary =
          Optional.ofNullable(metadata.currentSnapshot())
              .map(Snapshot::summary)
              .orElseGet(ImmutableMap::of);
      setHmsTableParameters(
          newMetadataLocation, tbl, metadata, removedProps, hiveEngineEnabled, summary);

      if (!keepHiveStats) {
        tbl.getParameters().remove(StatsSetupConst.COLUMN_STATS_ACCURATE);
      }

      lock.ensureActive();

      try {
        persistTable(
            tbl, updateHiveTable, hiveLockEnabled(metadata, conf) ? null : baseMetadataLocation);
        lock.ensureActive();

        commitStatus = CommitStatus.SUCCESS;
      } catch (LockException le) {
        commitStatus = CommitStatus.UNKNOWN;
        throw new CommitStateUnknownException(
            "Failed to heartbeat for hive lock while "
                + "committing changes. This can lead to a concurrent commit attempt be able to overwrite this commit. "
                + "Please check the commit history. If you are running into this issue, try reducing "
                + "iceberg.hive.lock-heartbeat-interval-ms.",
            le);
      } catch (org.apache.hadoop.hive.metastore.api.AlreadyExistsException e) {
        throw new AlreadyExistsException(e, "Table already exists: %s.%s", database, tableName);

      } catch (InvalidObjectException e) {
        throw new ValidationException(e, "Invalid Hive object for %s.%s", database, tableName);

      } catch (CommitFailedException | CommitStateUnknownException e) {
        throw e;

      } catch (Throwable e) {
        if (e.getMessage()
            .contains(
                "The table has been modified. The parameter value for key '"
                    + HiveTableOperations.METADATA_LOCATION_PROP
                    + "' is")) {
          throw new CommitFailedException(
              e, "The table %s.%s has been modified concurrently", database, tableName);
        }

        if (e.getMessage() != null
            && e.getMessage().contains("Table/View 'HIVE_LOCKS' does not exist")) {
          throw new RuntimeException(
              "Failed to acquire locks from metastore because the underlying metastore "
                  + "table 'HIVE_LOCKS' does not exist. This can occur when using an embedded metastore which does not "
                  + "support transactions. To fix this use an alternative metastore.",
              e);
        }

        LOG.error(
            "Cannot tell if commit to {}.{} succeeded, attempting to reconnect and check.",
            database,
            tableName,
            e);
        commitStatus = checkCommitStatus(newMetadataLocation, metadata);
        switch (commitStatus) {
          case SUCCESS:
            break;
          case FAILURE:
            throw e;
          case UNKNOWN:
            throw new CommitStateUnknownException(e);
        }
      }
    } catch (TException e) {
      throw new RuntimeException(
          String.format("Metastore operation failed for %s.%s", database, tableName), e);

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Interrupted during commit", e);

    } catch (LockException e) {
      throw new CommitFailedException(e);

    } finally {
      cleanupMetadataAndUnlock(commitStatus, newMetadataLocation, lock);
    }

    LOG.info(
        "Committed to table {} with the new metadata location {}", fullName, newMetadataLocation);
  }

  /**
   * 将 HMS 表对象持久化到 Metastore（建表或更新表）。
   *
   * <p>逻辑：更新表时调用 {@link MetastoreUtil#alterTable}（可携带期望 metadata location 做 条件更新，用于无锁方案）；建表时调用
   * {@code createTable}。
   *
   * @param hmsTable HMS 表对象
   * @param updateHiveTable true 表示更新已有表，false 表示新建表
   * @param expectedMetadataLocation 期望的旧 metadata location（无锁方案的条件检查），可为 null
   */
  @VisibleForTesting
  void persistTable(Table hmsTable, boolean updateHiveTable, String expectedMetadataLocation)
      throws TException, InterruptedException {
    if (updateHiveTable) {
      metaClients.run(
          client -> {
            MetastoreUtil.alterTable(
                client,
                database,
                tableName,
                hmsTable,
                expectedMetadataLocation != null
                    ? ImmutableMap.of(
                        NO_LOCK_EXPECTED_KEY,
                        METADATA_LOCATION_PROP,
                        NO_LOCK_EXPECTED_VALUE,
                        expectedMetadataLocation)
                    : ImmutableMap.of());
            return null;
          });
    } else {
      metaClients.run(
          client -> {
            client.createTable(hmsTable);
            return null;
          });
    }
  }

  /**
   * 从 HMS 加载表对象，表不存在时返回 null（而非抛异常）。
   *
   * @return HMS 表对象，或 null
   */
  @VisibleForTesting
  Table loadHmsTable() throws TException, InterruptedException {
    try {
      return metaClients.run(client -> client.getTable(database, tableName));
    } catch (NoSuchObjectException nte) {
      LOG.trace("Table not found {}", fullName, nte);
      return null;
    }
  }

  /**
   * 创建一个新的 HMS {@link Table} 对象（用于建表）。
   *
   * <p>逻辑：设置表名、database、owner（优先取表属性，回退到当前 Hadoop 用户）、时间戳、 表类型为 EXTERNAL_TABLE，并设置 EXTERNAL=TRUE
   * 参数。
   *
   * @param metadata Iceberg 表元数据
   * @return 构造好的 HMS Table 对象
   */
  private Table newHmsTable(TableMetadata metadata) {
    Preconditions.checkNotNull(metadata, "'metadata' parameter can't be null");
    final long currentTimeMillis = System.currentTimeMillis();

    Table newTable =
        new Table(
            tableName,
            database,
            metadata.property(HiveCatalog.HMS_TABLE_OWNER, HiveHadoopUtil.currentUser()),
            (int) currentTimeMillis / 1000,
            (int) currentTimeMillis / 1000,
            Integer.MAX_VALUE,
            null,
            Collections.emptyList(),
            Maps.newHashMap(),
            null,
            null,
            TableType.EXTERNAL_TABLE.toString());

    newTable
        .getParameters()
        .put("EXTERNAL", "TRUE"); // using the external table type also requires this
    return newTable;
  }

  /**
   * 将 Iceberg 元数据写入 HMS 表属性。
   *
   * <p>逻辑：
   *
   * <ul>
   *   <li>将 Iceberg 表属性推入 HMS（属性名按需翻译，如 gc.enabled→external.table.purge）。
   *   <li>设置 table_type=ICEBERG、metadata_location、previous_metadata_location。
   *   <li>按 hiveEngineEnabled 设置/移除 storage_handler 属性。
   *   <li>设置基本统计（NUM_FILES/ROW_COUNT/TOTAL_SIZE）。
   *   <li>调用 setSnapshotStats/setSchema/setPartitionSpec/setSortOrder 写入快照、Schema、 分区规格、排序信息。
   * </ul>
   *
   * @param newMetadataLocation 新的元数据文件路径
   * @param tbl HMS 表对象
   * @param metadata Iceberg 表元数据
   * @param obsoleteProps 已移除的 Iceberg 属性键集合（需从 HMS 同步移除）
   * @param hiveEngineEnabled 是否启用 Hive 引擎访问
   * @param summary 当前快照的统计摘要
   */
  private void setHmsTableParameters(
      String newMetadataLocation,
      Table tbl,
      TableMetadata metadata,
      Set<String> obsoleteProps,
      boolean hiveEngineEnabled,
      Map<String, String> summary) {
    Map<String, String> parameters =
        Optional.ofNullable(tbl.getParameters()).orElseGet(Maps::newHashMap);

    // push all Iceberg table properties into HMS
    metadata.properties().entrySet().stream()
        .filter(entry -> !entry.getKey().equalsIgnoreCase(HiveCatalog.HMS_TABLE_OWNER))
        .forEach(
            entry -> {
              String key = entry.getKey();
              // translate key names between Iceberg and HMS where needed
              String hmsKey = ICEBERG_TO_HMS_TRANSLATION.getOrDefault(key, key);
              parameters.put(hmsKey, entry.getValue());
            });
    if (metadata.uuid() != null) {
      parameters.put(TableProperties.UUID, metadata.uuid());
    }

    // remove any props from HMS that are no longer present in Iceberg table props
    obsoleteProps.forEach(parameters::remove);

    parameters.put(TABLE_TYPE_PROP, ICEBERG_TABLE_TYPE_VALUE.toUpperCase(Locale.ENGLISH));
    parameters.put(METADATA_LOCATION_PROP, newMetadataLocation);

    if (currentMetadataLocation() != null && !currentMetadataLocation().isEmpty()) {
      parameters.put(PREVIOUS_METADATA_LOCATION_PROP, currentMetadataLocation());
    }

    // If needed set the 'storage_handler' property to enable query from Hive
    if (hiveEngineEnabled) {
      parameters.put(
          hive_metastoreConstants.META_TABLE_STORAGE,
          "org.apache.iceberg.mr.hive.HiveIcebergStorageHandler");
    } else {
      parameters.remove(hive_metastoreConstants.META_TABLE_STORAGE);
    }

    // Set the basic statistics
    if (summary.get(SnapshotSummary.TOTAL_DATA_FILES_PROP) != null) {
      parameters.put(StatsSetupConst.NUM_FILES, summary.get(SnapshotSummary.TOTAL_DATA_FILES_PROP));
    }
    if (summary.get(SnapshotSummary.TOTAL_RECORDS_PROP) != null) {
      parameters.put(StatsSetupConst.ROW_COUNT, summary.get(SnapshotSummary.TOTAL_RECORDS_PROP));
    }
    if (summary.get(SnapshotSummary.TOTAL_FILE_SIZE_PROP) != null) {
      parameters.put(StatsSetupConst.TOTAL_SIZE, summary.get(SnapshotSummary.TOTAL_FILE_SIZE_PROP));
    }

    setSnapshotStats(metadata, parameters);
    setSchema(metadata, parameters);
    setPartitionSpec(metadata, parameters);
    setSortOrder(metadata, parameters);

    tbl.setParameters(parameters);
  }

  /**
   * 将当前快照统计信息写入 HMS 表属性。
   *
   * <p>逻辑：先清除旧的快照属性，若启用 HMS 属性暴露且存在当前快照，则写入快照 id、时间戳、 摘要；始终写入快照总数。
   *
   * @param metadata Iceberg 表元数据
   * @param parameters HMS 表属性 Map
   */
  @VisibleForTesting
  void setSnapshotStats(TableMetadata metadata, Map<String, String> parameters) {
    parameters.remove(TableProperties.CURRENT_SNAPSHOT_ID);
    parameters.remove(TableProperties.CURRENT_SNAPSHOT_TIMESTAMP);
    parameters.remove(TableProperties.CURRENT_SNAPSHOT_SUMMARY);

    Snapshot currentSnapshot = metadata.currentSnapshot();
    if (exposeInHmsProperties() && currentSnapshot != null) {
      parameters.put(
          TableProperties.CURRENT_SNAPSHOT_ID, String.valueOf(currentSnapshot.snapshotId()));
      parameters.put(
          TableProperties.CURRENT_SNAPSHOT_TIMESTAMP,
          String.valueOf(currentSnapshot.timestampMillis()));
      setSnapshotSummary(parameters, currentSnapshot);
    }

    parameters.put(TableProperties.SNAPSHOT_COUNT, String.valueOf(metadata.snapshots().size()));
  }

  /**
   * 将快照摘要序列化为 JSON 并写入 HMS 表属性。
   *
   * <p>逻辑：将快照 summary 序列化为 JSON，若长度不超过 maxHiveTablePropertySize 则写入， 否则跳过并告警。
   *
   * @param parameters HMS 表属性 Map
   * @param currentSnapshot 当前快照
   */
  @VisibleForTesting
  void setSnapshotSummary(Map<String, String> parameters, Snapshot currentSnapshot) {
    try {
      String summary = JsonUtil.mapper().writeValueAsString(currentSnapshot.summary());
      if (summary.length() <= maxHiveTablePropertySize) {
        parameters.put(TableProperties.CURRENT_SNAPSHOT_SUMMARY, summary);
      } else {
        LOG.warn(
            "Not exposing the current snapshot({}) summary in HMS since it exceeds {} characters",
            currentSnapshot.snapshotId(),
            maxHiveTablePropertySize);
      }
    } catch (JsonProcessingException e) {
      LOG.warn(
          "Failed to convert current snapshot({}) summary to a json string",
          currentSnapshot.snapshotId(),
          e);
    }
  }

  /**
   * 将 Iceberg Schema 序列化后写入 HMS 表属性（受大小限制）。
   *
   * @param metadata Iceberg 表元数据
   * @param parameters HMS 表属性 Map
   */
  @VisibleForTesting
  void setSchema(TableMetadata metadata, Map<String, String> parameters) {
    parameters.remove(TableProperties.CURRENT_SCHEMA);
    if (exposeInHmsProperties() && metadata.schema() != null) {
      String schema = SchemaParser.toJson(metadata.schema());
      setField(parameters, TableProperties.CURRENT_SCHEMA, schema);
    }
  }

  /**
   * 将默认分区规格序列化后写入 HMS 表属性（仅分区表）。
   *
   * @param metadata Iceberg 表元数据
   * @param parameters HMS 表属性 Map
   */
  @VisibleForTesting
  void setPartitionSpec(TableMetadata metadata, Map<String, String> parameters) {
    parameters.remove(TableProperties.DEFAULT_PARTITION_SPEC);
    if (exposeInHmsProperties() && metadata.spec() != null && metadata.spec().isPartitioned()) {
      String spec = PartitionSpecParser.toJson(metadata.spec());
      setField(parameters, TableProperties.DEFAULT_PARTITION_SPEC, spec);
    }
  }

  /**
   * 将默认排序规格序列化后写入 HMS 表属性（仅有序排序时）。
   *
   * @param metadata Iceberg 表元数据
   * @param parameters HMS 表属性 Map
   */
  @VisibleForTesting
  void setSortOrder(TableMetadata metadata, Map<String, String> parameters) {
    parameters.remove(TableProperties.DEFAULT_SORT_ORDER);
    if (exposeInHmsProperties()
        && metadata.sortOrder() != null
        && metadata.sortOrder().isSorted()) {
      String sortOrder = SortOrderParser.toJson(metadata.sortOrder());
      setField(parameters, TableProperties.DEFAULT_SORT_ORDER, sortOrder);
    }
  }

  /**
   * 将值写入 HMS 表属性，若超过大小上限则跳过并告警。
   *
   * @param parameters HMS 表属性 Map
   * @param key 属性键
   * @param value 属性值
   */
  private void setField(Map<String, String> parameters, String key, String value) {
    if (value.length() <= maxHiveTablePropertySize) {
      parameters.put(key, value);
    } else {
      LOG.warn(
          "Not exposing {} in HMS since it exceeds {} characters", key, maxHiveTablePropertySize);
    }
  }

  /**
   * 判断是否将 Iceberg 元数据暴露到 HMS 表属性。
   *
   * <p>设计要点：maxHiveTablePropertySize 设为 0 时关闭暴露，避免 HMS 参数过大。
   *
   * @return true 表示应暴露
   */
  private boolean exposeInHmsProperties() {
    return maxHiveTablePropertySize > 0;
  }

  /**
   * 构造 HMS 表的 {@link StorageDescriptor}。
   *
   * <p>逻辑：设置列（由 {@link HiveSchemaUtil} 转换）、location、SerDeInfo； 启用 Hive 引擎时使用 Iceberg 专用
   * InputFormat/OutputFormat/SerDe， 否则使用 Hadoop 默认格式。
   *
   * @param metadata Iceberg 表元数据
   * @param hiveEngineEnabled 是否启用 Hive 引擎访问
   * @return 构造好的 StorageDescriptor
   */
  private StorageDescriptor storageDescriptor(TableMetadata metadata, boolean hiveEngineEnabled) {

    final StorageDescriptor storageDescriptor = new StorageDescriptor();
    storageDescriptor.setCols(HiveSchemaUtil.convert(metadata.schema()));
    storageDescriptor.setLocation(metadata.location());
    SerDeInfo serDeInfo = new SerDeInfo();
    serDeInfo.setParameters(Maps.newHashMap());
    if (hiveEngineEnabled) {
      storageDescriptor.setInputFormat("org.apache.iceberg.mr.hive.HiveIcebergInputFormat");
      storageDescriptor.setOutputFormat("org.apache.iceberg.mr.hive.HiveIcebergOutputFormat");
      serDeInfo.setSerializationLib("org.apache.iceberg.mr.hive.HiveIcebergSerDe");
    } else {
      storageDescriptor.setOutputFormat("org.apache.hadoop.mapred.FileOutputFormat");
      storageDescriptor.setInputFormat("org.apache.hadoop.mapred.FileInputFormat");
      serDeInfo.setSerializationLib("org.apache.hadoop.hive.serde2.lazy.LazySimpleSerDe");
    }
    storageDescriptor.setSerdeInfo(serDeInfo);
    return storageDescriptor;
  }

  /**
   * 提交后清理：提交失败时删除未提交的元数据文件，最终释放锁。
   *
   * @param commitStatus 提交状态
   * @param metadataLocation 新元数据文件路径
   * @param lock 持有的锁对象
   */
  private void cleanupMetadataAndUnlock(
      CommitStatus commitStatus, String metadataLocation, HiveLock lock) {
    try {
      if (commitStatus == CommitStatus.FAILURE) {
        // If we are sure the commit failed, clean up the uncommitted metadata file
        io().deleteFile(metadataLocation);
      }
    } catch (RuntimeException e) {
      LOG.error("Failed to cleanup metadata file at {}", metadataLocation, e);
    } finally {
      lock.unlock();
    }
  }

  /**
   * 校验 HMS 表是否为 Iceberg 表（table_type=ICEBERG）。
   *
   * @param table HMS 表对象
   * @param fullName 表全限定名（用于错误信息）
   * @throws NoSuchIcebergTableException 非 Iceberg 表时抛出
   */
  static void validateTableIsIceberg(Table table, String fullName) {
    String tableType = table.getParameters().get(TABLE_TYPE_PROP);
    NoSuchIcebergTableException.check(
        tableType != null && tableType.equalsIgnoreCase(ICEBERG_TABLE_TYPE_VALUE),
        "Not an iceberg table: %s (type=%s)",
        fullName,
        tableType);
  }

  /**
   * 判断是否为该表启用 Hive 引擎访问（使用 Iceberg 专用 InputFormat/OutputFormat/SerDe）。
   *
   * <p>判断优先级：表属性 {@link TableProperties#ENGINE_HIVE_ENABLED} → 配置 {@link
   * ConfigProperties#ENGINE_HIVE_ENABLED} → 默认值。
   *
   * @param metadata 表元数据
   * @param conf Hive 配置
   * @return true 表示启用 Hive 引擎访问
   */
  private static boolean hiveEngineEnabled(TableMetadata metadata, Configuration conf) {
    if (metadata.properties().get(TableProperties.ENGINE_HIVE_ENABLED) != null) {
      // We know that the property is set, so default value will not be used,
      return metadata.propertyAsBoolean(TableProperties.ENGINE_HIVE_ENABLED, false);
    }

    return conf.getBoolean(
        ConfigProperties.ENGINE_HIVE_ENABLED, TableProperties.ENGINE_HIVE_ENABLED_DEFAULT);
  }

  /**
   * 判断是否为该表启用 Hive Metastore 锁（{@link MetastoreLock}）。
   *
   * <p>判断优先级：表属性 {@link TableProperties#HIVE_LOCK_ENABLED} → 配置 {@link
   * ConfigProperties#LOCK_HIVE_ENABLED} → 默认值。 关闭时使用 {@link NoLock}（依赖 HIVE-26882 条件更新方案）。
   *
   * @param metadata 表元数据
   * @param conf Hive 配置
   * @return true 表示启用 HMS 锁
   */
  private static boolean hiveLockEnabled(TableMetadata metadata, Configuration conf) {
    if (metadata.properties().get(TableProperties.HIVE_LOCK_ENABLED) != null) {
      // We know that the property is set, so default value will not be used,
      return metadata.propertyAsBoolean(TableProperties.HIVE_LOCK_ENABLED, false);
    }

    return conf.getBoolean(
        ConfigProperties.LOCK_HIVE_ENABLED, TableProperties.HIVE_LOCK_ENABLED_DEFAULT);
  }

  /**
   * 根据配置创建锁对象。
   *
   * @param metadata 表元数据
   * @return 启用 HMS 锁时返回 {@link MetastoreLock}，否则返回 {@link NoLock}
   */
  @VisibleForTesting
  HiveLock lockObject(TableMetadata metadata) {
    if (hiveLockEnabled(metadata, conf)) {
      return new MetastoreLock(conf, metaClients, catalogName, database, tableName);
    } else {
      return new NoLock();
    }
  }
}
