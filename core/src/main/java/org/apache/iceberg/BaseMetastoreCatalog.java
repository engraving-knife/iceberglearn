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

import java.util.Map;
import org.apache.iceberg.catalog.Catalog;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.exceptions.AlreadyExistsException;
import org.apache.iceberg.exceptions.CommitFailedException;
import org.apache.iceberg.exceptions.NoSuchTableException;
import org.apache.iceberg.io.InputFile;
import org.apache.iceberg.metrics.MetricsReporter;
import org.apache.iceberg.relocated.com.google.common.base.MoreObjects;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.util.PropertyUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于元数据存储（metastore）的 {@link Catalog} 抽象基类。
 *
 * <p>所属模块：iceberg-core。为 HiveCatalog、JdbcCatalog、NessieCatalog 等基于外部元数据存储 的 Catalog 实现提供通用骨架。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>实现表加载、注册、删除等通用流程，把"如何与具体 metastore 交互"延迟到子类。
 *   <li>处理普通表与元数据表（如 {@code table.files}、{@code table.history}）的统一标识符解析。
 *   <li>提供 {@link BaseMetastoreCatalogTableBuilder} 用于创建/替换表的事务编排。
 *   <li>统一加载 {@link MetricsReporter}，并按 catalog 属性注入表级默认/强制属性。
 * </ul>
 *
 * <p>设计意图：通过模板方法模式，子类只需实现 {@link #newTableOps(TableIdentifier)} 与 {@link
 * #defaultWarehouseLocation(TableIdentifier)} 即可接入新的 metastore； identifier 既可以指向普通表，也可以指向元数据表（命名形如
 * {@code db.table.files}）， 由 {@link #loadTable(TableIdentifier)} 内部按规则分流。
 *
 * <p>上下游关系：被 HiveCatalog、JdbcCatalog、RESTCatalog 等具体实现继承；通过 {@link TableOperations} 与底层元数据存储交互。
 */
public abstract class BaseMetastoreCatalog implements Catalog {
  private static final Logger LOG = LoggerFactory.getLogger(BaseMetastoreCatalog.class);

  private MetricsReporter metricsReporter;

  /**
   * 加载表：优先按普通表加载，若不存在再尝试按元数据表加载。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>若 identifier 合法，构造 {@link TableOperations} 并检查是否已有 current metadata。
   *   <li>有 current metadata 则构造 {@link BaseTable} 返回。
   *   <li>无 current metadata 时，若 identifier 实为元数据表标识符则加载元数据表， 否则抛出 {@link NoSuchTableException}。
   *   <li>identifier 不合法时也尝试按元数据表解析，仍失败则抛出异常。
   * </ol>
   *
   * @param identifier 表或元数据表标识符
   * @return 加载到的表对象
   * @throws NoSuchTableException 表或元数据表均不存在
   */
  @Override
  public Table loadTable(TableIdentifier identifier) {
    Table result;
    if (isValidIdentifier(identifier)) {
      TableOperations ops = newTableOps(identifier);
      if (ops.current() == null) {
        // the identifier may be valid for both tables and metadata tables
        if (isValidMetadataIdentifier(identifier)) {
          result = loadMetadataTable(identifier);

        } else {
          throw new NoSuchTableException("Table does not exist: %s", identifier);
        }

      } else {
        result = new BaseTable(ops, fullTableName(name(), identifier), metricsReporter());
      }

    } else if (isValidMetadataIdentifier(identifier)) {
      result = loadMetadataTable(identifier);

    } else {
      throw new NoSuchTableException("Invalid table identifier: %s", identifier);
    }

    LOG.info("Table loaded by catalog: {}", result);
    return result;
  }

  /**
   * 将一个已存在的表元数据文件注册到当前 catalog，常用于跨 catalog 迁移表。
   *
   * <p>逻辑：校验标识符与元数据文件位置合法；若表已存在则抛出异常；否则读取元数据文件， 通过 {@link TableOperations#commit} 写入 metastore 后返回
   * {@link BaseTable}。
   *
   * @param identifier 待注册的表标识符
   * @param metadataFileLocation 元数据 JSON 文件位置
   * @return 注册后的表对象
   * @throws AlreadyExistsException 表已存在
   */
  @Override
  public Table registerTable(TableIdentifier identifier, String metadataFileLocation) {
    Preconditions.checkArgument(
        identifier != null && isValidIdentifier(identifier), "Invalid identifier: %s", identifier);
    Preconditions.checkArgument(
        metadataFileLocation != null && !metadataFileLocation.isEmpty(),
        "Cannot register an empty metadata file location as a table");

    // Throw an exception if this table already exists in the catalog.
    if (tableExists(identifier)) {
      throw new AlreadyExistsException("Table already exists: %s", identifier);
    }

    TableOperations ops = newTableOps(identifier);
    InputFile metadataFile = ops.io().newInputFile(metadataFileLocation);
    TableMetadata metadata = TableMetadataParser.read(ops.io(), metadataFile);
    ops.commit(null, metadata);

    return new BaseTable(ops, fullTableName(name(), identifier), metricsReporter());
  }

  @Override
  public TableBuilder buildTable(TableIdentifier identifier, Schema schema) {
    return new BaseMetastoreCatalogTableBuilder(identifier, schema);
  }

  /**
   * 加载元数据表：根据表名末段解析出 {@link MetadataTableType}，再创建元数据表实例。
   *
   * <p>设计要点：把 identifier 的 namespace 部分作为底层真实表标识符， 末段作为元数据表类型，例如 {@code db.t.files} 解析为表 {@code
   * db.t} 的 files 元数据表。
   *
   * @param identifier 元数据表标识符
   * @return 元数据表实例
   * @throws NoSuchTableException 类型不存在或底层表不存在
   */
  private Table loadMetadataTable(TableIdentifier identifier) {
    String tableName = identifier.name();
    MetadataTableType type = MetadataTableType.from(tableName);
    if (type != null) {
      TableIdentifier baseTableIdentifier = TableIdentifier.of(identifier.namespace().levels());
      TableOperations ops = newTableOps(baseTableIdentifier);
      if (ops.current() == null) {
        throw new NoSuchTableException("Table does not exist: %s", baseTableIdentifier);
      }

      return MetadataTableUtils.createMetadataTableInstance(
          ops, name(), baseTableIdentifier, identifier, type);
    } else {
      throw new NoSuchTableException("Table does not exist: %s", identifier);
    }
  }

  /** 判断 identifier 是否为合法的元数据表标识符：末段是已知元数据表类型， 且剩余部分是合法的普通表标识符。 */
  private boolean isValidMetadataIdentifier(TableIdentifier identifier) {
    return MetadataTableType.from(identifier.name()) != null
        && isValidIdentifier(TableIdentifier.of(identifier.namespace().levels()));
  }

  /**
   * 校验普通表标识符是否合法，默认放行所有标识符；子类可覆盖以加入命名空间约束。
   *
   * @param tableIdentifier 待校验标识符
   * @return true 表示合法
   */
  protected boolean isValidIdentifier(TableIdentifier tableIdentifier) {
    // by default allow all identifiers
    return true;
  }

  /**
   * 返回 catalog 级配置属性，子类可覆盖以提供来自 catalog 配置的属性。
   *
   * @return 不可变属性 map，默认空
   */
  protected Map<String, String> properties() {
    return ImmutableMap.of();
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).toString();
  }

  /**
   * 创建与指定标识符绑定的 {@link TableOperations}，由子类实现具体的 metastore 访问逻辑。
   *
   * @param tableIdentifier 表标识符
   * @return 表操作对象
   */
  protected abstract TableOperations newTableOps(TableIdentifier tableIdentifier);

  /**
   * 返回表的默认存储位置，当创建表未显式指定 location 时使用。
   *
   * @param tableIdentifier 表标识符
   * @return 默认 warehouse 路径
   */
  protected abstract String defaultWarehouseLocation(TableIdentifier tableIdentifier);

  /**
   * 表构建器：编排创建表/替换表的元数据生成与提交。
   *
   * <p>设计意图：把"组装 schema/spec/sortOrder/location/properties → 生成 TableMetadata → 提交"这一流程封装在 catalog
   * 内部，保证 catalog 级默认/强制属性能正确注入； 同时区分 create 与 replace 两种语义。
   */
  protected class BaseMetastoreCatalogTableBuilder implements TableBuilder {
    private final TableIdentifier identifier;
    private final Schema schema;
    private final Map<String, String> tableProperties = Maps.newHashMap();
    private PartitionSpec spec = PartitionSpec.unpartitioned();
    private SortOrder sortOrder = SortOrder.unsorted();
    private String location = null;

    public BaseMetastoreCatalogTableBuilder(TableIdentifier identifier, Schema schema) {
      Preconditions.checkArgument(
          isValidIdentifier(identifier), "Invalid table identifier: %s", identifier);

      this.identifier = identifier;
      this.schema = schema;
      this.tableProperties.putAll(tableDefaultProperties());
    }

    @Override
    public TableBuilder withPartitionSpec(PartitionSpec newSpec) {
      this.spec = newSpec != null ? newSpec : PartitionSpec.unpartitioned();
      return this;
    }

    @Override
    public TableBuilder withSortOrder(SortOrder newSortOrder) {
      this.sortOrder = newSortOrder != null ? newSortOrder : SortOrder.unsorted();
      return this;
    }

    @Override
    public TableBuilder withLocation(String newLocation) {
      this.location = newLocation;
      return this;
    }

    @Override
    public TableBuilder withProperties(Map<String, String> properties) {
      if (properties != null) {
        tableProperties.putAll(properties);
      }
      return this;
    }

    @Override
    public TableBuilder withProperty(String key, String value) {
      tableProperties.put(key, value);
      return this;
    }

    /**
     * 创建表：组装元数据并提交到 metastore。
     *
     * <p>逻辑：若表已存在则抛出 {@link AlreadyExistsException}；计算 location（显式或默认）； 合并 catalog 级强制属性；构造 {@link
     * TableMetadata} 并提交；并发创建失败时转为 {@link AlreadyExistsException}。
     *
     * @return 新建的表对象
     */
    @Override
    public Table create() {
      TableOperations ops = newTableOps(identifier);
      if (ops.current() != null) {
        throw new AlreadyExistsException("Table already exists: %s", identifier);
      }

      String baseLocation = location != null ? location : defaultWarehouseLocation(identifier);
      tableProperties.putAll(tableOverrideProperties());
      TableMetadata metadata =
          TableMetadata.newTableMetadata(schema, spec, sortOrder, baseLocation, tableProperties);

      try {
        ops.commit(null, metadata);
      } catch (CommitFailedException ignored) {
        throw new AlreadyExistsException("Table was created concurrently: %s", identifier);
      }

      return new BaseTable(ops, fullTableName(name(), identifier), metricsReporter());
    }

    /**
     * 创建一个"创建表"事务，允许在事务内追加多个变更操作后一次性提交。
     *
     * @return 创建表的事务对象
     */
    @Override
    public Transaction createTransaction() {
      TableOperations ops = newTableOps(identifier);
      if (ops.current() != null) {
        throw new AlreadyExistsException("Table already exists: %s", identifier);
      }

      String baseLocation = location != null ? location : defaultWarehouseLocation(identifier);
      tableProperties.putAll(tableOverrideProperties());
      TableMetadata metadata =
          TableMetadata.newTableMetadata(schema, spec, sortOrder, baseLocation, tableProperties);
      return Transactions.createTableTransaction(identifier.toString(), ops, metadata);
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
     * 构造替换表事务的通用实现。
     *
     * <p>逻辑：若 orCreate=false 且表不存在则抛出异常；否则根据当前是否存在 metadata 选择 {@code buildReplacement}（替换）或 {@code
     * newTableMetadata}（新建）； 最后按 orCreate 选择对应事务工厂方法。
     *
     * @param orCreate true 表示允许表不存在时创建
     * @return 替换或创建-替换事务
     */
    private Transaction newReplaceTableTransaction(boolean orCreate) {
      TableOperations ops = newTableOps(identifier);
      if (!orCreate && ops.current() == null) {
        throw new NoSuchTableException("Table does not exist: %s", identifier);
      }

      TableMetadata metadata;
      tableProperties.putAll(tableOverrideProperties());
      if (ops.current() != null) {
        String baseLocation = location != null ? location : ops.current().location();
        metadata =
            ops.current().buildReplacement(schema, spec, sortOrder, baseLocation, tableProperties);
      } else {
        String baseLocation = location != null ? location : defaultWarehouseLocation(identifier);
        metadata =
            TableMetadata.newTableMetadata(schema, spec, sortOrder, baseLocation, tableProperties);
      }

      if (orCreate) {
        return Transactions.createOrReplaceTableTransaction(identifier.toString(), ops, metadata);
      } else {
        return Transactions.replaceTableTransaction(identifier.toString(), ops, metadata);
      }
    }

    /**
     * 从 catalog 属性中提取表级默认属性（前缀 {@code table-default.}）。
     *
     * <p>设计意图：允许在 catalog 层面统一为所有新表注入默认属性，例如默认文件格式等。
     *
     * @return catalog 级默认表属性
     */
    private Map<String, String> tableDefaultProperties() {
      Map<String, String> tableDefaultProperties =
          PropertyUtil.propertiesWithPrefix(properties(), CatalogProperties.TABLE_DEFAULT_PREFIX);
      LOG.info(
          "Table properties set at catalog level through catalog properties: {}",
          tableDefaultProperties);
      return tableDefaultProperties;
    }

    /**
     * 从 catalog 属性中提取表级强制属性（前缀 {@code table-override.}），会覆盖用户传入的同名属性。
     *
     * <p>设计意图：用于在 catalog 层面强制约束所有表的某些属性，例如统一开启 GC。
     *
     * @return catalog 级强制表属性
     */
    private Map<String, String> tableOverrideProperties() {
      Map<String, String> tableOverrideProperties =
          PropertyUtil.propertiesWithPrefix(properties(), CatalogProperties.TABLE_OVERRIDE_PREFIX);
      LOG.info(
          "Table properties enforced at catalog level through catalog properties: {}",
          tableOverrideProperties);
      return tableOverrideProperties;
    }
  }

  /**
   * 拼接 catalog 全限定表名，用于 metric/日志/异常信息中标识表。
   *
   * <p>逻辑：若 catalog 名称含 {@code /} 或 {@code :}（URI 形式，如 thrift://host:port）， 使用 {@code /} 作为分隔符；否则使用
   * {@code .}。随后追加 namespace 各级与表名。
   *
   * @param catalogName catalog 名称
   * @param identifier 表标识符
   * @return 全限定表名字符串
   */
  protected static String fullTableName(String catalogName, TableIdentifier identifier) {
    StringBuilder sb = new StringBuilder();

    if (catalogName.contains("/") || catalogName.contains(":")) {
      // use / for URI-like names: thrift://host:port/db.table
      sb.append(catalogName);
      if (!catalogName.endsWith("/")) {
        sb.append("/");
      }
    } else {
      // use . for non-URI named catalogs: prod.db.table
      sb.append(catalogName).append(".");
    }

    for (String level : identifier.namespace().levels()) {
      sb.append(level).append(".");
    }

    sb.append(identifier.name());

    return sb.toString();
  }

  /**
   * 懒加载 metrics reporter，首次调用时根据 catalog 属性构造。
   *
   * @return 当前 catalog 使用的 metrics reporter
   */
  private MetricsReporter metricsReporter() {
    if (metricsReporter == null) {
      metricsReporter = CatalogUtil.loadMetricsReporter(properties());
    }

    return metricsReporter;
  }
}
