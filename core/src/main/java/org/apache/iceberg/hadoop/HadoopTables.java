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
package org.apache.iceberg.hadoop;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Iterator;
import java.util.Map;
import org.apache.hadoop.conf.Configurable;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.iceberg.BaseTable;
import org.apache.iceberg.CatalogUtil;
import org.apache.iceberg.LockManager;
import org.apache.iceberg.MetadataTableType;
import org.apache.iceberg.MetadataTableUtils;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Schema;
import org.apache.iceberg.SortOrder;
import org.apache.iceberg.StaticTableOperations;
import org.apache.iceberg.Table;
import org.apache.iceberg.TableMetadata;
import org.apache.iceberg.TableOperations;
import org.apache.iceberg.Tables;
import org.apache.iceberg.Transaction;
import org.apache.iceberg.Transactions;
import org.apache.iceberg.catalog.Catalog;
import org.apache.iceberg.exceptions.AlreadyExistsException;
import org.apache.iceberg.exceptions.NoSuchTableException;
import org.apache.iceberg.relocated.com.google.common.annotations.VisibleForTesting;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.util.LockManagers;
import org.apache.iceberg.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 文件级说明：基于 Hadoop {@link org.apache.hadoop.fs.FileSystem} 的路径式 Iceberg 表入口。
 *
 * <p>所属模块：iceberg-core 的 hadoop 包。实现 {@link Tables} 接口，与 {@link HadoopCatalog}
 * 不同，本类直接以“文件路径”作为表标识，无需 catalog 层级命名空间。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>根据文件路径加载、创建、删除表，并支持事务化建表/替换表。
 *   <li>支持通过 URI fragment（如 {@code #snapshots}）加载元数据表。
 *   <li>从 Hadoop 配置中提取锁相关属性，惰性创建共享的 {@link LockManager}。
 * </ul>
 *
 * <p>设计意图：提供轻量级的路径式访问，适合已知表路径的脚本/工具场景。 表存在性由 metadata 目录下是否存在版本化元数据文件判定。
 *
 * <p>上下游关系：底层使用 {@link HadoopTableOperations} 管理元数据、{@link HadoopFileIO} 读写文件； 被引擎集成层或用户代码直接调用。
 */
public class HadoopTables implements Tables, Configurable {

  /** 锁相关配置项前缀，Hadoop 配置中以该前缀开头的项将被剥离前缀后传给 LockManager。 */
  public static final String LOCK_PROPERTY_PREFIX = "iceberg.tables.hadoop.";

  private static final Logger LOG = LoggerFactory.getLogger(HadoopTables.class);
  private static final String METADATA_JSON = "metadata.json";

  private static LockManager lockManager;

  private Configuration conf;

  /** 默认构造方法，使用空的 Hadoop {@link Configuration}。 */
  public HadoopTables() {
    this(new Configuration());
  }

  /**
   * 以指定 Hadoop 配置构造。
   *
   * @param conf Hadoop 配置
   */
  public HadoopTables(Configuration conf) {
    this.conf = conf;
  }

  /**
   * 从文件路径加载表。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>先用 {@link #parseMetadataType(String)} 判断路径是否带元数据表 fragment（如 #snapshots）。
   *   <li>若带则加载对应的元数据表；否则构造 {@link HadoopTableOperations}， 若 current() 非空返回 {@link BaseTable}，否则抛
   *       {@link NoSuchTableException}。
   * </ol>
   *
   * @param location 表路径 URI（如 hdfs:///warehouse/my_table/）
   * @return 表实现
   * @throws NoSuchTableException 表不存在时抛出
   */
  @Override
  public Table load(String location) {
    Table result;
    Pair<String, MetadataTableType> parsedMetadataType = parseMetadataType(location);

    if (parsedMetadataType != null) {
      // Load a metadata table
      result = loadMetadataTable(parsedMetadataType.first(), location, parsedMetadataType.second());
    } else {
      // Load a normal table
      TableOperations ops = newTableOps(location);
      if (ops.current() != null) {
        result = new BaseTable(ops, location);
      } else {
        throw new NoSuchTableException("Table does not exist at location: %s", location);
      }
    }

    LOG.info("Table location loaded: {}", result.location());
    return result;
  }

  /**
   * 判断指定路径是否存在 Iceberg 表。
   *
   * @param location 表路径
   * @return true 表示存在
   */
  @Override
  public boolean exists(String location) {
    return newTableOps(location).current() != null;
  }

  /**
   * 尝试从路径中解析元数据表类型（编码在 URI fragment 中）。
   *
   * <p>逻辑：查找最后一个 {@code #}，将其后字符串解析为 {@link MetadataTableType}； 解析失败或无 fragment 返回 null。
   *
   * @param location 待解析的路径
   * @return 基础表名与元数据表类型的 Pair；无则返回 null
   */
  private Pair<String, MetadataTableType> parseMetadataType(String location) {
    int hashIndex = location.lastIndexOf('#');
    if (hashIndex != -1 && !location.endsWith("#")) {
      String baseTable = location.substring(0, hashIndex);
      String metaTable = location.substring(hashIndex + 1);
      MetadataTableType type = MetadataTableType.from(metaTable);
      return (type == null) ? null : Pair.of(baseTable, type);
    } else {
      return null;
    }
  }

  /**
   * 加载元数据表（如 snapshots、history 等）。
   *
   * @param location 基础表路径
   * @param metadataTableName 元数据表显示名
   * @param type 元数据表类型
   * @return 元数据表实例
   * @throws NoSuchTableException 基础表不存在时抛出
   */
  private Table loadMetadataTable(
      String location, String metadataTableName, MetadataTableType type) {
    TableOperations ops = newTableOps(location);
    if (ops.current() == null) {
      throw new NoSuchTableException("Table does not exist at location: %s", location);
    }

    return MetadataTableUtils.createMetadataTableInstance(ops, location, metadataTableName, type);
  }

  /**
   * 在指定路径创建表。
   *
   * @param schema 表 schema
   * @param spec 分区 spec，为 null 则非分区
   * @param order 排序规则
   * @param properties 表属性，为 null 视为空
   * @param location 表路径 URI（如 hdfs:///warehouse/my_table）
   * @return 新创建的表
   */
  @Override
  public Table create(
      Schema schema,
      PartitionSpec spec,
      SortOrder order,
      Map<String, String> properties,
      String location) {
    return buildTable(location, schema)
        .withPartitionSpec(spec)
        .withSortOrder(order)
        .withProperties(properties)
        .create();
  }

  /**
   * 删除表并清理所有数据与元数据文件。
   *
   * @param location 表路径
   * @return true 表示删除成功；表不存在返回 false
   */
  public boolean dropTable(String location) {
    return dropTable(location, true);
  }

  /**
   * 删除表，可选清理数据与元数据文件。
   *
   * <p>逻辑：读取当前元数据；表不存在返回 false。purge 为 true 时先调用 {@link CatalogUtil#dropTableData} 删除元数据引用的数据文件，
   * 再递归删除表目录。IO 异常包装为 {@link UncheckedIOException}。
   *
   * @param location 表路径
   * @param purge 是否清理数据与元数据文件
   * @return true 表示删除成功；表不存在返回 false
   */
  public boolean dropTable(String location, boolean purge) {
    TableOperations ops = newTableOps(location);
    TableMetadata lastMetadata = null;
    if (ops.current() != null) {
      if (purge) {
        lastMetadata = ops.current();
      }
    } else {
      return false;
    }

    try {
      if (purge && lastMetadata != null) {
        // Since the data files and the metadata files may store in different locations,
        // so it has to call dropTableData to force delete the data file.
        CatalogUtil.dropTableData(ops.io(), lastMetadata);
      }
      Path tablePath = new Path(location);
      Util.getFs(tablePath, conf).delete(tablePath, true /* recursive */);
      return true;
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to delete file: " + location, e);
    }
  }

  /**
   * 根据路径创建表操作对象。
   *
   * <p>逻辑：若路径包含 {@code metadata.json}（即指向具体元数据文件）， 则构造 {@link StaticTableOperations}
   * 以只读方式加载；否则构造可读写的 {@link HadoopTableOperations}，并传入共享的 {@link LockManager}。
   *
   * @param location 表路径或元数据文件路径
   * @return 表操作对象
   */
  @VisibleForTesting
  TableOperations newTableOps(String location) {
    if (location.contains(METADATA_JSON)) {
      return new StaticTableOperations(location, new HadoopFileIO(conf));
    } else {
      return new HadoopTableOperations(
          new Path(location), new HadoopFileIO(conf), conf, createOrGetLockManager(this));
    }
  }

  /**
   * 懒创建（或返回已有的）共享 {@link LockManager}。
   *
   * <p>逻辑：从 Hadoop 配置中提取以 {@link #LOCK_PROPERTY_PREFIX} 开头的项，剥离前缀后 作为锁属性，通过 {@link
   * LockManagers#from(Map)} 构造。整个过程加类锁保证只创建一次。
   *
   * @param table 当前 HadoopTables 实例
   * @return 共享的 {@link LockManager}
   */
  private static synchronized LockManager createOrGetLockManager(HadoopTables table) {
    if (lockManager == null) {
      Map<String, String> properties = Maps.newHashMap();
      Iterator<Map.Entry<String, String>> configEntries = table.conf.iterator();
      while (configEntries.hasNext()) {
        Map.Entry<String, String> entry = configEntries.next();
        String key = entry.getKey();
        if (key.startsWith(LOCK_PROPERTY_PREFIX)) {
          properties.put(key.substring(LOCK_PROPERTY_PREFIX.length()), entry.getValue());
        }
      }

      lockManager = LockManagers.from(properties);
    }

    return lockManager;
  }

  /**
   * 构造新表的初始 {@link TableMetadata}。
   *
   * <p>逻辑：校验 schema 非空；spec/order 为 null 时退化为非分区/不排序； 委托 {@link TableMetadata#newTableMetadata} 构造。
   *
   * @param schema 表 schema
   * @param spec 分区 spec
   * @param order 排序规则
   * @param properties 表属性
   * @param location 表路径
   * @return 初始表元数据
   */
  private TableMetadata tableMetadata(
      Schema schema,
      PartitionSpec spec,
      SortOrder order,
      Map<String, String> properties,
      String location) {
    Preconditions.checkNotNull(schema, "A table schema is required");

    Map<String, String> tableProps = properties == null ? ImmutableMap.of() : properties;
    PartitionSpec partitionSpec = spec == null ? PartitionSpec.unpartitioned() : spec;
    SortOrder sortOrder = order == null ? SortOrder.unsorted() : order;
    return TableMetadata.newTableMetadata(schema, partitionSpec, sortOrder, location, tableProps);
  }

  /**
   * 开启一个建表事务。
   *
   * @param location 表路径
   * @param schema 表 schema
   * @param spec 分区 spec
   * @param properties 表属性
   * @return 建表事务
   * @throws AlreadyExistsException 表已存在时抛出
   */
  public Transaction newCreateTableTransaction(
      String location, Schema schema, PartitionSpec spec, Map<String, String> properties) {
    return buildTable(location, schema)
        .withPartitionSpec(spec)
        .withProperties(properties)
        .createTransaction();
  }

  /**
   * 开启一个替换表事务。
   *
   * @param location 表路径
   * @param schema 表 schema
   * @param spec 分区 spec
   * @param properties 表属性
   * @param orCreate 表不存在时是否改为建表
   * @return 替换表事务
   * @throws NoSuchTableException 表不存在且 orCreate 为 false 时抛出
   */
  public Transaction newReplaceTableTransaction(
      String location,
      Schema schema,
      PartitionSpec spec,
      Map<String, String> properties,
      boolean orCreate) {

    Catalog.TableBuilder builder =
        buildTable(location, schema).withPartitionSpec(spec).withProperties(properties);
    return orCreate ? builder.createOrReplaceTransaction() : builder.replaceTransaction();
  }

  /**
   * 构造表构建器。
   *
   * @param location 表路径
   * @param schema 表 schema
   * @return {@link HadoopTableBuilder} 实例
   */
  public Catalog.TableBuilder buildTable(String location, Schema schema) {
    return new HadoopTableBuilder(location, schema);
  }

  /**
   * 内部类：HadoopTables 的表构建器实现。
   *
   * <p>设计要点：location 由构造时确定，{@link #withLocation(String)} 仅允许传入与之一致的值或 null； 默认非分区、不排序。
   */
  private class HadoopTableBuilder implements Catalog.TableBuilder {
    private final String location;
    private final Schema schema;
    private final ImmutableMap.Builder<String, String> propertiesBuilder = ImmutableMap.builder();
    private PartitionSpec spec = PartitionSpec.unpartitioned();
    private SortOrder sortOrder = SortOrder.unsorted();

    HadoopTableBuilder(String location, Schema schema) {
      this.location = location;
      this.schema = schema;
    }

    @Override
    public Catalog.TableBuilder withPartitionSpec(PartitionSpec newSpec) {
      this.spec = newSpec != null ? newSpec : PartitionSpec.unpartitioned();
      return this;
    }

    @Override
    public Catalog.TableBuilder withSortOrder(SortOrder newSortOrder) {
      this.sortOrder = newSortOrder != null ? newSortOrder : SortOrder.unsorted();
      return this;
    }

    /**
     * 设置表 location，HadoopTables 路径式表 location 已由构造确定。
     *
     * @param newLocation 必须为 null 或与构造时一致
     * @return 当前构建器
     * @throws IllegalArgumentException 当 location 不一致时抛出
     */
    @Override
    public Catalog.TableBuilder withLocation(String newLocation) {
      Preconditions.checkArgument(
          newLocation == null || location.equals(newLocation),
          String.format(
              "Table location %s differs from the table location (%s) from the PathIdentifier",
              newLocation, location));
      return this;
    }

    @Override
    public Catalog.TableBuilder withProperties(Map<String, String> properties) {
      if (properties != null) {
        propertiesBuilder.putAll(properties);
      }
      return this;
    }

    @Override
    public Catalog.TableBuilder withProperty(String key, String value) {
      propertiesBuilder.put(key, value);
      return this;
    }

    /**
     * 创建表。
     *
     * <p>逻辑：若表已存在抛 {@link AlreadyExistsException}；否则构造初始元数据并提交。
     *
     * @return 新创建的表
     * @throws AlreadyExistsException 表已存在时抛出
     */
    @Override
    public Table create() {
      TableOperations ops = newTableOps(location);
      if (ops.current() != null) {
        throw new AlreadyExistsException("Table already exists at location: %s", location);
      }

      Map<String, String> properties = propertiesBuilder.build();
      TableMetadata metadata = tableMetadata(schema, spec, sortOrder, properties, location);
      ops.commit(null, metadata);
      return new BaseTable(ops, location);
    }

    /**
     * 开启建表事务。
     *
     * @return 建表事务
     * @throws AlreadyExistsException 表已存在时抛出
     */
    @Override
    public Transaction createTransaction() {
      TableOperations ops = newTableOps(location);
      if (ops.current() != null) {
        throw new AlreadyExistsException("Table already exists: %s", location);
      }

      Map<String, String> properties = propertiesBuilder.build();
      TableMetadata metadata = tableMetadata(schema, spec, null, properties, location);
      return Transactions.createTableTransaction(location, ops, metadata);
    }

    @Override
    public Transaction replaceTransaction() {
      return newReplaceTableTransaction(false);
    }

    @Override
    public Transaction createOrReplaceTransaction() {
      return newReplaceTableTransaction(true);
    }

    /**
     * 开启替换（或建表）事务的内部实现。
     *
     * <p>逻辑：若表存在则基于现有元数据构造替换元数据，否则构造初始元数据； 根据 orCreate 选择对应的事务工厂。
     *
     * @param orCreate 表不存在时是否改为建表
     * @return 替换或建表事务
     * @throws NoSuchTableException 表不存在且 orCreate 为 false 时抛出
     */
    private Transaction newReplaceTableTransaction(boolean orCreate) {
      TableOperations ops = newTableOps(location);
      if (!orCreate && ops.current() == null) {
        throw new NoSuchTableException("No such table: %s", location);
      }

      Map<String, String> properties = propertiesBuilder.build();
      TableMetadata metadata;
      if (ops.current() != null) {
        metadata = ops.current().buildReplacement(schema, spec, sortOrder, location, properties);
      } else {
        metadata = tableMetadata(schema, spec, sortOrder, properties, location);
      }

      if (orCreate) {
        return Transactions.createOrReplaceTableTransaction(location, ops, metadata);
      } else {
        return Transactions.replaceTableTransaction(location, ops, metadata);
      }
    }
  }

  /**
   * 注入 Hadoop {@link Configuration}。
   *
   * @param conf Hadoop 配置
   */
  @Override
  public void setConf(Configuration conf) {
    this.conf = conf;
  }

  /** 获取当前持有的 Hadoop {@link Configuration}。 */
  @Override
  public Configuration getConf() {
    return conf;
  }
}
