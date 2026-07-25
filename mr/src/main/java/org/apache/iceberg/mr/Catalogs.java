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
package org.apache.iceberg.mr;

import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.hadoop.conf.Configuration;
import org.apache.iceberg.CatalogProperties;
import org.apache.iceberg.CatalogUtil;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.PartitionSpecParser;
import org.apache.iceberg.Schema;
import org.apache.iceberg.SchemaParser;
import org.apache.iceberg.Table;
import org.apache.iceberg.catalog.Catalog;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.hadoop.HadoopTables;
import org.apache.iceberg.relocated.com.google.common.annotations.VisibleForTesting;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableSet;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.relocated.com.google.common.collect.Streams;

/**
 * 文件级说明：Iceberg Catalog 解析与统一访问入口，为 Hive/MR 集成层提供 catalog 加载、表 CRUD 能力。
 *
 * <p>所属模块：iceberg-mr（Iceberg 与 Hive/MapReduce 集成模块；本类是该模块中 catalog 相关的 公共门面，位于
 * iceberg-api/iceberg-core 之上，桥接 Hive 配置与 Iceberg Catalog API）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>根据 Hadoop Configuration / Properties 解析出应使用的 catalog 类型与实现。
 *   <li>提供统一的 {@link Table} 加载、创建、删除接口，屏蔽底层 catalog 实现差异。
 *   <li>区分“命名 catalog”（hive/hadoop/catalog-impl）与“基于路径的表”（location_based_table） 两种寻址方式。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>类型解析优先级：若指定了 catalogName，则从 {@code iceberg.catalog.<catalogName>.type} 读取类型；当 catalogName 为
 *       {@link #ICEBERG_HADOOP_TABLE_NAME} 时视为“无 catalog”，直接走 {@link HadoopTables} 按路径加载；type 为
 *       null 时再回退到 {@code catalog-impl} 指定的实现类。
 *   <li>未指定 catalogName 时，从全局 {@link CatalogUtil#ICEBERG_CATALOG_TYPE} 读取类型： hive={@code
 *       HiveCatalog}，location={@code HadoopTables}，hadoop={@code HadoopCatalog}。
 *   <li>把控制属性（schema/spec/location/name/catalogName）从最终交给 Iceberg 的属性表中剔除， 避免污染表属性。
 * </ul>
 *
 * <p>上下游关系：上游被 HiveIcebergSerDe、HiveIcebergStorageHandler、HiveIcebergInputFormat 等 Hive 集成类调用；下游依赖
 * iceberg-core 的 {@link CatalogUtil}、{@link HadoopTables} 与 各 {@link Catalog} 实现。
 */
public final class Catalogs {

  /** 默认 catalog 名称，当配置中未显式指定 catalogName 时使用。 */
  public static final String ICEBERG_DEFAULT_CATALOG_NAME = "default_iceberg";
  /** 特殊 catalog 名称，表示“基于路径的表”，将跳过 catalog 直接用 {@link HadoopTables} 加载。 */
  public static final String ICEBERG_HADOOP_TABLE_NAME = "location_based_table";
  /** Properties 中的表标识符键（catalog 寻址时使用）。 */
  public static final String NAME = "name";
  /** Properties 中的表路径键（基于路径寻址时使用）。 */
  public static final String LOCATION = "location";

  private static final String NO_CATALOG_TYPE = "no catalog";
  private static final Set<String> PROPERTIES_TO_REMOVE =
      ImmutableSet.of(
          InputFormatConfig.TABLE_SCHEMA,
          InputFormatConfig.PARTITION_SPEC,
          LOCATION,
          NAME,
          InputFormatConfig.CATALOG_NAME);

  private Catalogs() {}

  /**
   * 根据 Configuration 中的配置加载 Iceberg 表。
   *
   * <p>从 conf 中分别读取表标识符、表路径、catalog 名称，委托给三参数的 {@link #loadTable(Configuration, String, String,
   * String)} 执行实际加载。
   *
   * @param conf Hadoop 配置，需包含 table.identifier / table.location / catalog.name 等键
   * @return 加载到的 Iceberg 表
   */
  public static Table loadTable(Configuration conf) {
    return loadTable(
        conf,
        conf.get(InputFormatConfig.TABLE_IDENTIFIER),
        conf.get(InputFormatConfig.TABLE_LOCATION),
        conf.get(InputFormatConfig.CATALOG_NAME));
  }

  /**
   * 根据 Properties 中的配置加载 Iceberg 表（Hive SerDe/StorageHandler 入口）。
   *
   * <p>需在 props 中提供表标识符（{@link #NAME}）+ catalog 名称 （{@link
   * InputFormatConfig#CATALOG_NAME}），或仅提供表路径（{@link #LOCATION}）。
   *
   * @param conf Hadoop 配置
   * @param props 控制属性，至少包含 name 或 location
   * @return 加载到的 Iceberg 表
   */
  public static Table loadTable(Configuration conf, Properties props) {
    return loadTable(
        conf,
        props.getProperty(NAME),
        props.getProperty(LOCATION),
        props.getProperty(InputFormatConfig.CATALOG_NAME));
  }

  /**
   * 实际执行表加载的内部方法。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>先按 catalogName 加载 {@link Catalog}（可能为空 Optional）。
   *   <li>若 catalog 存在：要求 tableIdentifier 非空，按 {@link TableIdentifier} 解析后调用 {@link
   *       Catalog#loadTable(TableIdentifier)}。
   *   <li>若 catalog 不存在：要求 tableLocation 非空，回退到 {@link HadoopTables#load(String)}。
   * </ol>
   *
   * @param conf Hadoop 配置
   * @param tableIdentifier 表标识符字符串，catalog 模式下必填
   * @param tableLocation 表路径，无 catalog 模式下必填
   * @param catalogName catalog 名称，可为 null
   * @return 加载到的 Iceberg 表
   */
  private static Table loadTable(
      Configuration conf, String tableIdentifier, String tableLocation, String catalogName) {
    Optional<Catalog> catalog = loadCatalog(conf, catalogName);

    if (catalog.isPresent()) {
      Preconditions.checkArgument(tableIdentifier != null, "Table identifier not set");
      return catalog.get().loadTable(TableIdentifier.parse(tableIdentifier));
    }

    Preconditions.checkArgument(tableLocation != null, "Table location not set");
    return new HadoopTables(conf).load(tableLocation);
  }

  /**
   * 创建一张 Iceberg 表。
   *
   * <p>props 需包含：
   *
   * <ul>
   *   <li>表标识符（{@link #NAME}）或表路径（{@link #LOCATION}），二者至少一个。
   *   <li>表 schema（{@link InputFormatConfig#TABLE_SCHEMA}），必填。
   *   <li>分区规格（{@link InputFormatConfig#PARTITION_SPEC}），可选，缺省为非分区表。
   * </ul>
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>解析 schema 与 partition spec。
   *   <li>构造表属性 map，剔除 schema/spec/location/name/catalogName 等控制属性。
   *   <li>若 catalog 存在：按 {@link TableIdentifier} 走 {@link Catalog#createTable}。
   *   <li>否则回退到 {@link HadoopTables#create}，要求 location 非空。
   * </ol>
   *
   * @param conf Hadoop 配置
   * @param props 控制属性
   * @return 创建好的 Iceberg 表
   */
  public static Table createTable(Configuration conf, Properties props) {
    String schemaString = props.getProperty(InputFormatConfig.TABLE_SCHEMA);
    Preconditions.checkNotNull(schemaString, "Table schema not set");
    Schema schema = SchemaParser.fromJson(props.getProperty(InputFormatConfig.TABLE_SCHEMA));

    String specString = props.getProperty(InputFormatConfig.PARTITION_SPEC);
    PartitionSpec spec = PartitionSpec.unpartitioned();
    if (specString != null) {
      spec = PartitionSpecParser.fromJson(schema, specString);
    }

    String location = props.getProperty(LOCATION);
    String catalogName = props.getProperty(InputFormatConfig.CATALOG_NAME);

    // Create a table property map without the controlling properties
    Map<String, String> map = Maps.newHashMapWithExpectedSize(props.size());
    for (Object key : props.keySet()) {
      if (!PROPERTIES_TO_REMOVE.contains(key)) {
        map.put(key.toString(), props.get(key).toString());
      }
    }

    Optional<Catalog> catalog = loadCatalog(conf, catalogName);

    if (catalog.isPresent()) {
      String name = props.getProperty(NAME);
      Preconditions.checkNotNull(name, "Table identifier not set");
      return catalog.get().createTable(TableIdentifier.parse(name), schema, spec, location, map);
    }

    Preconditions.checkNotNull(location, "Table location not set");
    return new HadoopTables(conf).create(schema, spec, map, location);
  }

  /**
   * 删除一张 Iceberg 表。
   *
   * <p>逻辑：若 catalog 存在则按表标识符调用 {@link Catalog#dropTable}；否则按路径调用 {@link HadoopTables#dropTable}。
   *
   * @param conf Hadoop 配置
   * @param props 控制属性，需包含 name 或 location
   * @return 是否删除成功
   */
  public static boolean dropTable(Configuration conf, Properties props) {
    String location = props.getProperty(LOCATION);
    String catalogName = props.getProperty(InputFormatConfig.CATALOG_NAME);

    Optional<Catalog> catalog = loadCatalog(conf, catalogName);

    if (catalog.isPresent()) {
      String name = props.getProperty(NAME);
      Preconditions.checkNotNull(name, "Table identifier not set");
      return catalog.get().dropTable(TableIdentifier.parse(name));
    }

    Preconditions.checkNotNull(location, "Table location not set");
    return new HadoopTables(conf).dropTable(location);
  }

  /**
   * 判断当前配置是否使用 HiveCatalog。
   *
   * <p>逻辑：依次按 catalogName、默认 catalogName 查找 type；若 type 非空则判断是否为 {@code hive}；若 type 为 null，则再判断
   * catalog 属性中是否未指定 catalog-impl （未指定时默认走 HiveCatalog）。
   *
   * @param conf Hadoop 配置
   * @param props 控制属性，需包含 catalogName
   * @return true 表示当前使用 HiveCatalog
   */
  public static boolean hiveCatalog(Configuration conf, Properties props) {
    String catalogName = props.getProperty(InputFormatConfig.CATALOG_NAME);
    String catalogType = getCatalogType(conf, catalogName);
    if (catalogType != null) {
      return CatalogUtil.ICEBERG_CATALOG_TYPE_HIVE.equalsIgnoreCase(catalogType);
    }
    catalogType = getCatalogType(conf, ICEBERG_DEFAULT_CATALOG_NAME);
    if (catalogType != null) {
      return CatalogUtil.ICEBERG_CATALOG_TYPE_HIVE.equalsIgnoreCase(catalogType);
    }
    return getCatalogProperties(conf, catalogName, catalogType).get(CatalogProperties.CATALOG_IMPL)
        == null;
  }

  /**
   * 加载 catalog 实例，返回 Optional。
   *
   * <p>逻辑：若 catalogType 为 {@link #NO_CATALOG_TYPE}（基于路径的表），返回 empty； 否则用 {@link
   * CatalogUtil#buildIcebergCatalog} 构建 catalog，name 缺省时使用 {@link #ICEBERG_DEFAULT_CATALOG_NAME}。
   *
   * @param conf Hadoop 配置
   * @param catalogName catalog 名称，可为 null
   * @return catalog 实例的 Optional，无 catalog 时为 empty
   */
  @VisibleForTesting
  static Optional<Catalog> loadCatalog(Configuration conf, String catalogName) {
    String catalogType = getCatalogType(conf, catalogName);
    if (NO_CATALOG_TYPE.equalsIgnoreCase(catalogType)) {
      return Optional.empty();
    } else {
      String name = catalogName == null ? ICEBERG_DEFAULT_CATALOG_NAME : catalogName;
      return Optional.of(
          CatalogUtil.buildIcebergCatalog(
              name, getCatalogProperties(conf, name, catalogType), conf));
    }
  }

  /**
   * 从全局 Hadoop 配置中收集指定 catalog 的属性。
   *
   * <p>逻辑：扫描 conf 中以 {@code iceberg.catalog.<catalogName>.} 为前缀的所有键，去掉前缀后组装成属性 map。
   *
   * @param conf Hadoop 配置
   * @param catalogName catalog 名称
   * @param catalogType catalog 类型
   * @return catalog 属性 map
   */
  private static Map<String, String> getCatalogProperties(
      Configuration conf, String catalogName, String catalogType) {
    String keyPrefix = InputFormatConfig.CATALOG_CONFIG_PREFIX + catalogName;

    return Streams.stream(conf.iterator())
        .filter(e -> e.getKey().startsWith(keyPrefix))
        .collect(
            Collectors.toMap(
                e -> e.getKey().substring(keyPrefix.length() + 1), Map.Entry::getValue));
  }

  /**
   * 根据 catalogName 解析 catalog 类型。
   *
   * <p>逻辑：
   *
   * <ul>
   *   <li>catalogName 非空：读取 {@code iceberg.catalog.<catalogName>.type}；若 catalogName 为 {@link
   *       #ICEBERG_HADOOP_TABLE_NAME}，返回 {@link #NO_CATALOG_TYPE}。
   *   <li>catalogName 为 null：读取全局 {@link CatalogUtil#ICEBERG_CATALOG_TYPE}； 若值为 {@link
   *       #LOCATION}，返回 {@link #NO_CATALOG_TYPE}。
   * </ul>
   *
   * @param conf 全局 Hive 配置
   * @param catalogName catalog 名称，可为 null
   * @return catalog 类型字符串，可为 null
   */
  private static String getCatalogType(Configuration conf, String catalogName) {
    if (catalogName != null) {
      String catalogType =
          conf.get(
              InputFormatConfig.catalogPropertyConfigKey(
                  catalogName, CatalogUtil.ICEBERG_CATALOG_TYPE));
      if (catalogName.equals(ICEBERG_HADOOP_TABLE_NAME)) {
        return NO_CATALOG_TYPE;
      } else {
        return catalogType;
      }
    } else {
      String catalogType = conf.get(CatalogUtil.ICEBERG_CATALOG_TYPE);
      if (catalogType != null && catalogType.equals(LOCATION)) {
        return NO_CATALOG_TYPE;
      } else {
        return catalogType;
      }
    }
  }
}
