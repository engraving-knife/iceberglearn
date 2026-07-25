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
package org.apache.iceberg.flink;

import java.util.Map;
import java.util.Set;
import org.apache.flink.configuration.ConfigOption;
import org.apache.flink.configuration.ConfigOptions;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.table.api.TableSchema;
import org.apache.flink.table.catalog.CatalogBaseTable;
import org.apache.flink.table.catalog.CatalogDatabaseImpl;
import org.apache.flink.table.catalog.CatalogTable;
import org.apache.flink.table.catalog.ObjectIdentifier;
import org.apache.flink.table.catalog.ObjectPath;
import org.apache.flink.table.catalog.exceptions.DatabaseAlreadyExistException;
import org.apache.flink.table.catalog.exceptions.TableAlreadyExistException;
import org.apache.flink.table.connector.sink.DynamicTableSink;
import org.apache.flink.table.connector.source.DynamicTableSource;
import org.apache.flink.table.factories.DynamicTableSinkFactory;
import org.apache.flink.table.factories.DynamicTableSourceFactory;
import org.apache.flink.table.utils.TableSchemaUtils;
import org.apache.flink.util.Preconditions;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.exceptions.AlreadyExistsException;
import org.apache.iceberg.flink.source.IcebergTableSource;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;

/**
 * 文件级说明：Flink 动态表工厂，同时支持创建 Iceberg 表的 Source 和 Sink。
 *
 * <p>所属模块：iceberg-flink（Flink 集成模块），实现 Flink 的 {@link DynamicTableSinkFactory} 和 {@link
 * DynamicTableSourceFactory} SPI 接口。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>根据 Flink 表属性创建 {@link IcebergTableSource} 和 {@link IcebergTableSink}。
 *   <li>支持两种模式：通过已有 {@link FlinkCatalog} 创建（catalog 模式），或通过表属性 独立创建 catalog 和表（YAML/SQL DDL 模式）。
 *   <li>在独立模式下，自动创建不存在的数据库和表。
 * </ul>
 *
 * <p>设计意图：Flink 通过 factory identifier "iceberg" 发现本类。catalog 模式下直接复用 已有 catalog；独立模式下从表属性重建
 * catalog，便于在不注册 catalog 的情况下直接使用。
 *
 * <p>上下游关系：被 Flink Table API 通过 SPI 调用；内部委托 {@link FlinkCatalogFactory} 创建 catalog，委托 {@link
 * TableLoader} 加载表。
 */
public class FlinkDynamicTableFactory
    implements DynamicTableSinkFactory, DynamicTableSourceFactory {
  static final String FACTORY_IDENTIFIER = "iceberg";

  private static final ConfigOption<String> CATALOG_NAME =
      ConfigOptions.key("catalog-name")
          .stringType()
          .noDefaultValue()
          .withDescription("Catalog name");

  private static final ConfigOption<String> CATALOG_TYPE =
      ConfigOptions.key(FlinkCatalogFactory.ICEBERG_CATALOG_TYPE)
          .stringType()
          .noDefaultValue()
          .withDescription("Catalog type, the optional types are: custom, hadoop, hive.");

  private static final ConfigOption<String> CATALOG_DATABASE =
      ConfigOptions.key("catalog-database")
          .stringType()
          .defaultValue(FlinkCatalogFactory.DEFAULT_DATABASE_NAME)
          .withDescription("Database name managed in the iceberg catalog.");

  private static final ConfigOption<String> CATALOG_TABLE =
      ConfigOptions.key("catalog-table")
          .stringType()
          .noDefaultValue()
          .withDescription("Table name managed in the underlying iceberg catalog and database.");

  private final FlinkCatalog catalog;

  /** 无参构造（独立模式，catalog 为 null，将从表属性创建 catalog）。 */
  public FlinkDynamicTableFactory() {
    this.catalog = null;
  }

  /**
   * 带已有 catalog 的构造（catalog 模式）。
   *
   * @param catalog 已注册的 FlinkCatalog
   */
  public FlinkDynamicTableFactory(FlinkCatalog catalog) {
    this.catalog = catalog;
  }

  /**
   * 创建 Iceberg 表的动态 Source。
   *
   * <p>逻辑：从 context 获取表标识符和 schema，根据是否持有 catalog 选择 TableLoader 创建方式，最终返回 {@link
   * IcebergTableSource}。
   *
   * @param context Flink DynamicTableSource 上下文
   * @return IcebergTableSource 实例
   */
  @Override
  public DynamicTableSource createDynamicTableSource(Context context) {
    ObjectIdentifier objectIdentifier = context.getObjectIdentifier();
    CatalogTable catalogTable = context.getCatalogTable();
    Map<String, String> tableProps = catalogTable.getOptions();
    TableSchema tableSchema = TableSchemaUtils.getPhysicalSchema(catalogTable.getSchema());

    TableLoader tableLoader;
    if (catalog != null) {
      tableLoader = createTableLoader(catalog, objectIdentifier.toObjectPath());
    } else {
      tableLoader =
          createTableLoader(
              catalogTable,
              tableProps,
              objectIdentifier.getDatabaseName(),
              objectIdentifier.getObjectName());
    }

    return new IcebergTableSource(tableLoader, tableSchema, tableProps, context.getConfiguration());
  }

  /**
   * 创建 Iceberg 表的动态 Sink。
   *
   * <p>逻辑：从 context 获取表标识符和 schema，根据是否持有 catalog 选择 TableLoader 创建方式，最终返回 {@link
   * IcebergTableSink}。
   *
   * @param context Flink DynamicTableSink 上下文
   * @return IcebergTableSink 实例
   */
  @Override
  public DynamicTableSink createDynamicTableSink(Context context) {
    ObjectIdentifier objectIdentifier = context.getObjectIdentifier();
    CatalogTable catalogTable = context.getCatalogTable();
    Map<String, String> writeProps = catalogTable.getOptions();
    TableSchema tableSchema = TableSchemaUtils.getPhysicalSchema(catalogTable.getSchema());

    TableLoader tableLoader;
    if (catalog != null) {
      tableLoader = createTableLoader(catalog, objectIdentifier.toObjectPath());
    } else {
      tableLoader =
          createTableLoader(
              catalogTable,
              writeProps,
              objectIdentifier.getDatabaseName(),
              objectIdentifier.getObjectName());
    }

    return new IcebergTableSink(tableLoader, tableSchema, context.getConfiguration(), writeProps);
  }

  @Override
  public Set<ConfigOption<?>> requiredOptions() {
    Set<ConfigOption<?>> options = Sets.newHashSet();
    options.add(CATALOG_TYPE);
    options.add(CATALOG_NAME);
    return options;
  }

  @Override
  public Set<ConfigOption<?>> optionalOptions() {
    Set<ConfigOption<?>> options = Sets.newHashSet();
    options.add(CATALOG_DATABASE);
    options.add(CATALOG_TABLE);
    return options;
  }

  @Override
  public String factoryIdentifier() {
    return FACTORY_IDENTIFIER;
  }

  /**
   * 独立模式下创建 TableLoader：从表属性重建 catalog 并确保数据库/表存在。
   *
   * <p>逻辑：从属性解析 catalog-name/database/table → 创建 FlinkCatalog → 若数据库不存在则创建 → 若表不存在则创建 → 返回
   * fromCatalog 的 TableLoader。
   *
   * @param catalogBaseTable Flink catalog 表定义
   * @param tableProps 表属性
   * @param databaseName 数据库名
   * @param tableName 表名
   * @return TableLoader 实例
   */
  private static TableLoader createTableLoader(
      CatalogBaseTable catalogBaseTable,
      Map<String, String> tableProps,
      String databaseName,
      String tableName) {
    Configuration flinkConf = new Configuration();
    tableProps.forEach(flinkConf::setString);

    String catalogName = flinkConf.getString(CATALOG_NAME);
    Preconditions.checkNotNull(
        catalogName, "Table property '%s' cannot be null", CATALOG_NAME.key());

    String catalogDatabase = flinkConf.getString(CATALOG_DATABASE, databaseName);
    Preconditions.checkNotNull(catalogDatabase, "The iceberg database name cannot be null");

    String catalogTable = flinkConf.getString(CATALOG_TABLE, tableName);
    Preconditions.checkNotNull(catalogTable, "The iceberg table name cannot be null");

    org.apache.hadoop.conf.Configuration hadoopConf = FlinkCatalogFactory.clusterHadoopConf();
    FlinkCatalogFactory factory = new FlinkCatalogFactory();
    FlinkCatalog flinkCatalog =
        (FlinkCatalog) factory.createCatalog(catalogName, tableProps, hadoopConf);
    ObjectPath objectPath = new ObjectPath(catalogDatabase, catalogTable);

    // Create database if not exists in the external catalog.
    if (!flinkCatalog.databaseExists(catalogDatabase)) {
      try {
        flinkCatalog.createDatabase(
            catalogDatabase, new CatalogDatabaseImpl(Maps.newHashMap(), null), true);
      } catch (DatabaseAlreadyExistException e) {
        throw new AlreadyExistsException(
            e,
            "Database %s already exists in the iceberg catalog %s.",
            catalogName,
            catalogDatabase);
      }
    }

    // Create table if not exists in the external catalog.
    if (!flinkCatalog.tableExists(objectPath)) {
      try {
        flinkCatalog.createIcebergTable(objectPath, catalogBaseTable, true);
      } catch (TableAlreadyExistException e) {
        throw new AlreadyExistsException(
            e,
            "Table %s already exists in the database %s and catalog %s",
            catalogTable,
            catalogDatabase,
            catalogName);
      }
    }

    return TableLoader.fromCatalog(
        flinkCatalog.getCatalogLoader(), TableIdentifier.of(catalogDatabase, catalogTable));
  }

  /**
   * catalog 模式下创建 TableLoader：直接从已有 catalog 和表路径创建。
   *
   * @param catalog 已有的 FlinkCatalog
   * @param objectPath 表路径
   * @return TableLoader 实例
   */
  private static TableLoader createTableLoader(FlinkCatalog catalog, ObjectPath objectPath) {
    Preconditions.checkNotNull(catalog, "Flink catalog cannot be null");
    return TableLoader.fromCatalog(catalog.getCatalogLoader(), catalog.toIdentifier(objectPath));
  }
}
