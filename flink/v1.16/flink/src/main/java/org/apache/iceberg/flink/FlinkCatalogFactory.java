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

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.flink.configuration.GlobalConfiguration;
import org.apache.flink.runtime.util.HadoopUtils;
import org.apache.flink.table.catalog.Catalog;
import org.apache.flink.table.factories.CatalogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.iceberg.CatalogProperties;
import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.base.Strings;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.util.PropertyUtil;

/**
 * Flink Catalog 工厂实现，用于创建 {@link FlinkCatalog}。
 *
 * <p>所属模块：iceberg-flink v1.15。职责：将 Flink SQL 中 CREATE CATALOG 语句的属性 解析为 Iceberg Catalog 配置，构造并返回
 * {@link FlinkCatalog} 实例。
 *
 * <p>设计意图：SPI 工厂模式——通过 META-INF/services 注册到 Flink，按 type=iceberg 匹配。 上下游：被 Flink TableEnvironment
 * 的 CatalogStore 调用；向下委托 {@link CatalogLoader} 加载底层 Iceberg Catalog。
 *
 * <p>支持的配置项：
 *
 * <ul>
 *   <li><code>type</code> - Flink catalog factory key，必须是 "iceberg"
 *   <li><code>catalog-type</code> - Iceberg catalog 类型，"hive"、"hadoop" 或 "rest"
 *   <li><code>uri</code> - Hive Metastore URI（仅 Hive catalog）
 *   <li><code>clients</code> - Hive 客户端池大小（仅 Hive catalog）
 *   <li><code>warehouse</code> - warehouse 路径（仅 Hadoop catalog）
 *   <li><code>default-database</code> - 默认数据库名
 *   <li><code>base-namespace</code> - 数据库前缀的基础命名空间（仅 Hadoop catalog）
 *   <li><code>cache-enabled</code> - 是否启用 catalog 缓存
 * </ul>
 *
 * <p>若需扩展自定义 catalog（非 Hive/Hadoop），可继承本类并重写 {@link #createCatalogLoader(String, Map,
 * Configuration)}。
 */
public class FlinkCatalogFactory implements CatalogFactory {

  // Can not just use "type", it conflicts with CATALOG_TYPE.
  /** Iceberg catalog 类型属性 key（避免直接使用 "type" 与 CATALOG_TYPE 冲突）。 */
  public static final String ICEBERG_CATALOG_TYPE = "catalog-type";
  /** Hadoop catalog 类型常量。 */
  public static final String ICEBERG_CATALOG_TYPE_HADOOP = "hadoop";
  /** Hive catalog 类型常量。 */
  public static final String ICEBERG_CATALOG_TYPE_HIVE = "hive";
  /** REST catalog 类型常量。 */
  public static final String ICEBERG_CATALOG_TYPE_REST = "rest";

  /** Hive 配置目录属性 key。 */
  public static final String HIVE_CONF_DIR = "hive-conf-dir";
  /** Hadoop 配置目录属性 key。 */
  public static final String HADOOP_CONF_DIR = "hadoop-conf-dir";
  /** 默认数据库属性 key。 */
  public static final String DEFAULT_DATABASE = "default-database";
  /** 默认数据库名。 */
  public static final String DEFAULT_DATABASE_NAME = "default";
  /** 基础命名空间属性 key。 */
  public static final String BASE_NAMESPACE = "base-namespace";

  /** Flink catalog type 属性 key。 */
  public static final String TYPE = "type";
  /** 属性版本 key。 */
  public static final String PROPERTY_VERSION = "property-version";

  /**
   * 创建本 Flink catalog 适配器使用的 Iceberg {@link org.apache.iceberg.catalog.Catalog} 加载器。
   *
   * <p>逻辑：优先按 catalog-impl 加载自定义 Catalog；否则按 catalog-type 选择 hive/hadoop/rest 三种内置加载器之一。两者不能同时设置。
   *
   * @param name Flink 端 catalog 名称
   * @param properties Flink catalog 属性
   * @param hadoopConf Hadoop 配置
   * @return Iceberg catalog 加载器
   */
  static CatalogLoader createCatalogLoader(
      String name, Map<String, String> properties, Configuration hadoopConf) {
    String catalogImpl = properties.get(CatalogProperties.CATALOG_IMPL);
    if (catalogImpl != null) {
      String catalogType = properties.get(ICEBERG_CATALOG_TYPE);
      Preconditions.checkArgument(
          catalogType == null,
          "Cannot create catalog %s, both catalog-type and catalog-impl are set: catalog-type=%s, catalog-impl=%s",
          name,
          catalogType,
          catalogImpl);
      return CatalogLoader.custom(name, properties, hadoopConf, catalogImpl);
    }

    String catalogType = properties.getOrDefault(ICEBERG_CATALOG_TYPE, ICEBERG_CATALOG_TYPE_HIVE);
    switch (catalogType.toLowerCase(Locale.ENGLISH)) {
      case ICEBERG_CATALOG_TYPE_HIVE:
        // The values of properties 'uri', 'warehouse', 'hive-conf-dir' are allowed to be null, in
        // that case it will
        // fallback to parse those values from hadoop configuration which is loaded from classpath.
        String hiveConfDir = properties.get(HIVE_CONF_DIR);
        String hadoopConfDir = properties.get(HADOOP_CONF_DIR);
        Configuration newHadoopConf = mergeHiveConf(hadoopConf, hiveConfDir, hadoopConfDir);
        return CatalogLoader.hive(name, newHadoopConf, properties);

      case ICEBERG_CATALOG_TYPE_HADOOP:
        return CatalogLoader.hadoop(name, hadoopConf, properties);

      case ICEBERG_CATALOG_TYPE_REST:
        return CatalogLoader.rest(name, hadoopConf, properties);

      default:
        throw new UnsupportedOperationException(
            "Unknown catalog-type: " + catalogType + " (Must be 'hive', 'hadoop' or 'rest')");
    }
  }

  /** 返回 Flink 创建 catalog 所需的最小上下文（type=iceberg，property-version=1）。 */
  @Override
  public Map<String, String> requiredContext() {
    Map<String, String> context = Maps.newHashMap();
    context.put(TYPE, "iceberg");
    context.put(PROPERTY_VERSION, "1");
    return context;
  }

  /** 返回支持的属性列表，"*" 表示接受任意属性。 */
  @Override
  public List<String> supportedProperties() {
    return ImmutableList.of("*");
  }

  /** 使用集群 Hadoop 配置创建 Flink Catalog。 */
  @Override
  public Catalog createCatalog(String name, Map<String, String> properties) {
    return createCatalog(name, properties, clusterHadoopConf());
  }

  /**
   * 创建 Flink Catalog 实例。
   *
   * <p>逻辑：构造 CatalogLoader、解析默认数据库与基础命名空间、读取缓存开关与过期时间， 然后组装出 {@link FlinkCatalog}。
   */
  protected Catalog createCatalog(
      String name, Map<String, String> properties, Configuration hadoopConf) {
    CatalogLoader catalogLoader = createCatalogLoader(name, properties, hadoopConf);
    String defaultDatabase = properties.getOrDefault(DEFAULT_DATABASE, DEFAULT_DATABASE_NAME);

    Namespace baseNamespace = Namespace.empty();
    if (properties.containsKey(BASE_NAMESPACE)) {
      baseNamespace = Namespace.of(properties.get(BASE_NAMESPACE).split("\\."));
    }

    boolean cacheEnabled =
        PropertyUtil.propertyAsBoolean(
            properties, CatalogProperties.CACHE_ENABLED, CatalogProperties.CACHE_ENABLED_DEFAULT);

    long cacheExpirationIntervalMs =
        PropertyUtil.propertyAsLong(
            properties,
            CatalogProperties.CACHE_EXPIRATION_INTERVAL_MS,
            CatalogProperties.CACHE_EXPIRATION_INTERVAL_MS_OFF);
    Preconditions.checkArgument(
        cacheExpirationIntervalMs != 0,
        "%s is not allowed to be 0.",
        CatalogProperties.CACHE_EXPIRATION_INTERVAL_MS);

    return new FlinkCatalog(
        name,
        defaultDatabase,
        baseNamespace,
        catalogLoader,
        cacheEnabled,
        cacheExpirationIntervalMs);
  }

  /**
   * 将 hive-conf-dir / hadoop-conf-dir 中的 hive-site.xml、hdfs-site.xml、core-site.xml 合并到给定 Hadoop 配置。
   *
   * <p>逻辑：若显式给出 hive-conf-dir，则加载其中的 hive-site.xml；否则尝试从 classpath 加载。 若给出 hadoop-conf-dir，则加载其中的
   * hdfs-site.xml 与 core-site.xml。
   */
  private static Configuration mergeHiveConf(
      Configuration hadoopConf, String hiveConfDir, String hadoopConfDir) {
    Configuration newConf = new Configuration(hadoopConf);
    if (!Strings.isNullOrEmpty(hiveConfDir)) {
      Preconditions.checkState(
          Files.exists(Paths.get(hiveConfDir, "hive-site.xml")),
          "There should be a hive-site.xml file under the directory %s",
          hiveConfDir);
      newConf.addResource(new Path(hiveConfDir, "hive-site.xml"));
    } else {
      // If don't provide the hive-site.xml path explicitly, it will try to load resource from
      // classpath. If still
      // couldn't load the configuration file, then it will throw exception in HiveCatalog.
      URL configFile = CatalogLoader.class.getClassLoader().getResource("hive-site.xml");
      if (configFile != null) {
        newConf.addResource(configFile);
      }
    }

    if (!Strings.isNullOrEmpty(hadoopConfDir)) {
      Preconditions.checkState(
          Files.exists(Paths.get(hadoopConfDir, "hdfs-site.xml")),
          "Failed to load Hadoop configuration: missing %s",
          Paths.get(hadoopConfDir, "hdfs-site.xml"));
      newConf.addResource(new Path(hadoopConfDir, "hdfs-site.xml"));
      Preconditions.checkState(
          Files.exists(Paths.get(hadoopConfDir, "core-site.xml")),
          "Failed to load Hadoop configuration: missing %s",
          Paths.get(hadoopConfDir, "core-site.xml"));
      newConf.addResource(new Path(hadoopConfDir, "core-site.xml"));
    }

    return newConf;
  }

  /** 从 Flink 全局配置加载集群 Hadoop 配置。 */
  public static Configuration clusterHadoopConf() {
    return HadoopUtils.getHadoopConfiguration(GlobalConfiguration.loadConfiguration());
  }
}
