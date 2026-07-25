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
package org.apache.iceberg;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import org.apache.hadoop.conf.Configuration;
import org.apache.iceberg.encryption.EncryptionManager;
import org.apache.iceberg.hadoop.HadoopConfigurable;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.io.LocationProvider;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.util.SerializableMap;
import org.apache.iceberg.util.SerializableSupplier;

/**
 * 可序列化的只读表实现（iceberg-core 分布式执行层）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>将表的当前状态以可序列化形式封装，便于在集群节点间传输；
 *   <li>持久化常用元数据（schema/spec/sortOrder/properties 的 JSON）以避免远端读取元数据文件；
 *   <li>对写操作与 refresh 抛出 {@link UnsupportedOperationException}，保证只读语义。
 * </ul>
 *
 * <p>设计意图：
 *
 * <p>此类表示表状态的不可变可序列化副本，不反映原始表的后续变更。虽然保留了元数据文件位置 以便按需加载完整元数据，但常用元数据直接序列化存储。{@link FileIO}、{@link
 * EncryptionManager}、 {@link LocationProvider} 实例需可序列化（Kryo 等自定义框架需自行支持这些类型）。
 *
 * <p>注意：从大量节点同时加载完整元数据可能压垮底层存储。
 *
 * <p>上下游关系：由引擎层通过 {@link #copyOf(Table)} 创建并分发到执行节点； 依赖 {@link SchemaParser}、{@link
 * PartitionSpecParser}、{@link SortOrderParser} 等解析器。
 */
public class SerializableTable implements Table, Serializable {

  private final String name;
  private final String location;
  private final String metadataFileLocation;
  private final Map<String, String> properties;
  private final String schemaAsJson;
  private final int defaultSpecId;
  private final Map<Integer, String> specAsJsonMap;
  private final String sortOrderAsJson;
  private final FileIO io;
  private final EncryptionManager encryption;
  private final LocationProvider locationProvider;
  private final Map<String, SnapshotRef> refs;

  private transient volatile Table lazyTable = null;
  private transient volatile Schema lazySchema = null;
  private transient volatile Map<Integer, PartitionSpec> lazySpecs = null;
  private transient volatile SortOrder lazySortOrder = null;

  /**
   * 从原始表构造可序列化副本，将常用元数据序列化为 JSON 字符串。
   *
   * @param table 原始表
   */
  protected SerializableTable(Table table) {
    this.name = table.name();
    this.location = table.location();
    this.metadataFileLocation = metadataFileLocation(table);
    this.properties = SerializableMap.copyOf(table.properties());
    this.schemaAsJson = SchemaParser.toJson(table.schema());
    this.defaultSpecId = table.spec().specId();
    this.specAsJsonMap = Maps.newHashMap();
    Map<Integer, PartitionSpec> specs = table.specs();
    specs.forEach((specId, spec) -> specAsJsonMap.put(specId, PartitionSpecParser.toJson(spec)));
    this.sortOrderAsJson = SortOrderParser.toJson(table.sortOrder());
    this.io = fileIO(table);
    this.encryption = table.encryption();
    this.locationProvider = table.locationProvider();
    this.refs = SerializableMap.copyOf(table.refs());
  }

  /**
   * 创建可序列化的只读表副本，元数据表会返回 {@link SerializableMetadataTable}。
   *
   * @param table 原始表
   * @return 反映原始表当前状态的可序列化只读表
   */
  public static Table copyOf(Table table) {
    if (table instanceof BaseMetadataTable) {
      return new SerializableMetadataTable((BaseMetadataTable) table);
    } else {
      return new SerializableTable(table);
    }
  }

  /**
   * 获取表的元数据文件位置，若表非 {@link HasTableOperations} 则返回 null。
   *
   * @param table 原始表
   * @return 元数据文件位置，无法获取时返回 null
   */
  private String metadataFileLocation(Table table) {
    if (table instanceof HasTableOperations) {
      TableOperations ops = ((HasTableOperations) table).operations();
      return ops.current().metadataFileLocation();
    } else {
      return null;
    }
  }

  /**
   * 获取表的 {@link FileIO}，若为 {@link HadoopConfigurable} 则用可序列化供应商封装 Configuration。
   *
   * @param table 原始表
   * @return 可序列化的 FileIO
   */
  private FileIO fileIO(Table table) {
    if (table.io() instanceof HadoopConfigurable) {
      ((HadoopConfigurable) table.io()).serializeConfWith(SerializableConfSupplier::new);
    }

    return table.io();
  }

  /**
   * 懒加载完整表对象，使用双重检查锁保证线程安全。
   *
   * <p>逻辑：metadataFileLocation 为 null 时抛出异常；否则基于 {@link StaticTableOperations} 构造 {@link
   * BaseTable}。
   *
   * @return 加载完成的表对象
   */
  private Table lazyTable() {
    if (lazyTable == null) {
      synchronized (this) {
        if (lazyTable == null) {
          if (metadataFileLocation == null) {
            throw new UnsupportedOperationException(
                "Cannot load metadata: metadata file location is null");
          }

          TableOperations ops =
              new StaticTableOperations(metadataFileLocation, io, locationProvider);
          this.lazyTable = newTable(ops, name);
        }
      }
    }

    return lazyTable;
  }

  /**
   * 根据表操作句柄与表名创建表实例，子类可覆盖以创建元数据表实例。
   *
   * @param ops 表操作句柄
   * @param tableName 表名
   * @return 新建的 {@link BaseTable}
   */
  protected Table newTable(TableOperations ops, String tableName) {
    return new BaseTable(ops, tableName);
  }

  /** 返回表名。 */
  @Override
  public String name() {
    return name;
  }

  /** 返回表存储位置。 */
  @Override
  public String location() {
    return location;
  }

  /** 返回表属性（可序列化副本）。 */
  @Override
  public Map<String, String> properties() {
    return properties;
  }

  /**
   * 懒加载并返回当前 schema，优先从 JSON 解析以避免读取元数据文件。
   *
   * <p>逻辑：若未加载且未加载完整表，则从 schemaAsJson 解析；否则从 lazyTable 获取。 使用双重检查锁保证线程安全。
   *
   * @return 当前 schema
   */
  @Override
  public Schema schema() {
    if (lazySchema == null) {
      synchronized (this) {
        if (lazySchema == null && lazyTable == null) {
          // prefer parsing JSON as opposed to loading the metadata
          this.lazySchema = SchemaParser.fromJson(schemaAsJson);
        } else if (lazySchema == null) {
          this.lazySchema = lazyTable.schema();
        }
      }
    }

    return lazySchema;
  }

  /** 返回所有历史 schema，需加载完整表。 */
  @Override
  public Map<Integer, Schema> schemas() {
    return lazyTable().schemas();
  }

  /** 返回默认分区 spec。 */
  @Override
  public PartitionSpec spec() {
    return specs().get(defaultSpecId);
  }

  /**
   * 懒加载并返回所有分区 spec，优先从 JSON 解析以避免读取元数据文件。
   *
   * <p>逻辑：若未加载且未加载完整表，则从 specAsJsonMap 解析（依赖当前 schema）； 否则从 lazyTable 获取。使用双重检查锁保证线程安全。
   *
   * @return specId 到分区 spec 的映射
   */
  @Override
  public Map<Integer, PartitionSpec> specs() {
    if (lazySpecs == null) {
      synchronized (this) {
        if (lazySpecs == null && lazyTable == null) {
          // prefer parsing JSON as opposed to loading the metadata
          Map<Integer, PartitionSpec> specs = Maps.newHashMapWithExpectedSize(specAsJsonMap.size());
          specAsJsonMap.forEach(
              (specId, specAsJson) -> {
                specs.put(specId, PartitionSpecParser.fromJson(schema(), specAsJson));
              });
          this.lazySpecs = specs;
        } else if (lazySpecs == null) {
          this.lazySpecs = lazyTable.specs();
        }
      }
    }

    return lazySpecs;
  }

  /**
   * 懒加载并返回当前排序顺序，优先从 JSON 解析以避免读取元数据文件。
   *
   * <p>逻辑：若未加载且未加载完整表，则从 sortOrderAsJson 解析；否则从 lazyTable 获取。 使用双重检查锁保证线程安全。
   *
   * @return 当前排序顺序
   */
  @Override
  public SortOrder sortOrder() {
    if (lazySortOrder == null) {
      synchronized (this) {
        if (lazySortOrder == null && lazyTable == null) {
          // prefer parsing JSON as opposed to loading the metadata
          this.lazySortOrder = SortOrderParser.fromJson(schema(), sortOrderAsJson);
        } else if (lazySortOrder == null) {
          this.lazySortOrder = lazyTable.sortOrder();
        }
      }
    }

    return lazySortOrder;
  }

  /** 返回所有历史排序顺序，需加载完整表。 */
  @Override
  public Map<Integer, SortOrder> sortOrders() {
    return lazyTable().sortOrders();
  }

  /** 返回表的 {@link FileIO}。 */
  @Override
  public FileIO io() {
    return io;
  }

  /** 返回表的加密管理器。 */
  @Override
  public EncryptionManager encryption() {
    return encryption;
  }

  /** 返回表的位置提供者。 */
  @Override
  public LocationProvider locationProvider() {
    return locationProvider;
  }

  /** 返回表的统计文件列表，需加载完整表。 */
  @Override
  public List<StatisticsFile> statisticsFiles() {
    return lazyTable().statisticsFiles();
  }

  /** 返回表的快照引用映射（可序列化副本）。 */
  @Override
  public Map<String, SnapshotRef> refs() {
    return refs;
  }

  /** 序列化表不支持 refresh 操作。 */
  @Override
  public void refresh() {
    throw new UnsupportedOperationException(errorMsg("refresh"));
  }

  /** 创建新扫描，需加载完整表。 */
  @Override
  public TableScan newScan() {
    return lazyTable().newScan();
  }

  /** 创建新批量扫描，需加载完整表。 */
  @Override
  public BatchScan newBatchScan() {
    return lazyTable().newBatchScan();
  }

  /** 返回当前快照，需加载完整表。 */
  @Override
  public Snapshot currentSnapshot() {
    return lazyTable().currentSnapshot();
  }

  /** 按 ID 返回快照，需加载完整表。 */
  @Override
  public Snapshot snapshot(long snapshotId) {
    return lazyTable().snapshot(snapshotId);
  }

  /** 返回所有快照，需加载完整表。 */
  @Override
  public Iterable<Snapshot> snapshots() {
    return lazyTable().snapshots();
  }

  /** 返回表历史记录，需加载完整表。 */
  @Override
  public List<HistoryEntry> history() {
    return lazyTable().history();
  }

  /** 序列化表不支持 updateSchema 操作。 */
  @Override
  public UpdateSchema updateSchema() {
    throw new UnsupportedOperationException(errorMsg("updateSchema"));
  }

  /** 序列化表不支持 updateSpec 操作。 */
  @Override
  public UpdatePartitionSpec updateSpec() {
    throw new UnsupportedOperationException(errorMsg("updateSpec"));
  }

  /** 序列化表不支持 updateProperties 操作。 */
  @Override
  public UpdateProperties updateProperties() {
    throw new UnsupportedOperationException(errorMsg("updateProperties"));
  }

  /** 序列化表不支持 replaceSortOrder 操作。 */
  @Override
  public ReplaceSortOrder replaceSortOrder() {
    throw new UnsupportedOperationException(errorMsg("replaceSortOrder"));
  }

  /** 序列化表不支持 updateLocation 操作。 */
  @Override
  public UpdateLocation updateLocation() {
    throw new UnsupportedOperationException(errorMsg("updateLocation"));
  }

  /** 序列化表不支持 newAppend 操作。 */
  @Override
  public AppendFiles newAppend() {
    throw new UnsupportedOperationException(errorMsg("newAppend"));
  }

  /** 序列化表不支持 newRewrite 操作。 */
  @Override
  public RewriteFiles newRewrite() {
    throw new UnsupportedOperationException(errorMsg("newRewrite"));
  }

  /** 序列化表不支持 rewriteManifests 操作。 */
  @Override
  public RewriteManifests rewriteManifests() {
    throw new UnsupportedOperationException(errorMsg("rewriteManifests"));
  }

  /** 序列化表不支持 newOverwrite 操作。 */
  @Override
  public OverwriteFiles newOverwrite() {
    throw new UnsupportedOperationException(errorMsg("newOverwrite"));
  }

  /** 序列化表不支持 newRowDelta 操作。 */
  @Override
  public RowDelta newRowDelta() {
    throw new UnsupportedOperationException(errorMsg("newRowDelta"));
  }

  /** 序列化表不支持 newReplacePartitions 操作。 */
  @Override
  public ReplacePartitions newReplacePartitions() {
    throw new UnsupportedOperationException(errorMsg("newReplacePartitions"));
  }

  /** 序列化表不支持 newDelete 操作。 */
  @Override
  public DeleteFiles newDelete() {
    throw new UnsupportedOperationException(errorMsg("newDelete"));
  }

  /** 序列化表不支持 updateStatistics 操作。 */
  @Override
  public UpdateStatistics updateStatistics() {
    throw new UnsupportedOperationException(errorMsg("updateStatistics"));
  }

  /** 序列化表不支持 expireSnapshots 操作。 */
  @Override
  public ExpireSnapshots expireSnapshots() {
    throw new UnsupportedOperationException(errorMsg("expireSnapshots"));
  }

  /** 序列化表不支持 manageSnapshots 操作。 */
  @Override
  public ManageSnapshots manageSnapshots() {
    throw new UnsupportedOperationException(errorMsg("manageSnapshots"));
  }

  /** 序列化表不支持 newTransaction 操作。 */
  @Override
  public Transaction newTransaction() {
    throw new UnsupportedOperationException(errorMsg("newTransaction"));
  }

  /**
   * 构造不支持操作的错误信息。
   *
   * @param operation 操作名
   * @return 错误信息字符串
   */
  private String errorMsg(String operation) {
    return String.format("Operation %s is not supported after the table is serialized", operation);
  }

  /** 可序列化的元数据表实现，封装 {@link BaseMetadataTable} 的可序列化副本。 */
  public static class SerializableMetadataTable extends SerializableTable {
    private final MetadataTableType type;
    private final String baseTableName;

    /**
     * 从元数据表构造可序列化副本。
     *
     * @param metadataTable 原始元数据表
     */
    protected SerializableMetadataTable(BaseMetadataTable metadataTable) {
      super(metadataTable);
      this.type = metadataTable.metadataTableType();
      this.baseTableName = metadataTable.table().name();
    }

    /**
     * 创建元数据表实例而非普通表。
     *
     * @param ops 表操作句柄
     * @param tableName 表名
     * @return 通过 {@link MetadataTableUtils} 创建的元数据表实例
     */
    @Override
    protected Table newTable(TableOperations ops, String tableName) {
      return MetadataTableUtils.createMetadataTableInstance(ops, baseTableName, tableName, type);
    }

    /** 返回元数据表类型。 */
    public MetadataTableType type() {
      return type;
    }
  }

  /** 以可序列化方式捕获 Hadoop Configuration 当前状态，避免直接序列化 Configuration。 */
  // captures the current state of a Hadoop configuration in a serializable manner
  private static class SerializableConfSupplier implements SerializableSupplier<Configuration> {

    private final Map<String, String> confAsMap;
    private transient volatile Configuration conf = null;

    /**
     * 将 Configuration 的所有条目拷贝到 Map 中以便序列化。
     *
     * @param conf 待捕获的 Hadoop Configuration
     */
    SerializableConfSupplier(Configuration conf) {
      this.confAsMap = Maps.newHashMapWithExpectedSize(conf.size());
      conf.forEach(entry -> confAsMap.put(entry.getKey(), entry.getValue()));
    }

    /**
     * 懒重建并返回 Configuration，使用双重检查锁保证线程安全。
     *
     * <p>逻辑：以 false 禁止加载默认配置，再从 confAsMap 逐条设置。
     *
     * @return 重建的 Configuration
     */
    @Override
    public Configuration get() {
      if (conf == null) {
        synchronized (this) {
          if (conf == null) {
            Configuration newConf = new Configuration(false);
            confAsMap.forEach(newConf::set);
            this.conf = newConf;
          }
        }
      }

      return conf;
    }
  }
}
