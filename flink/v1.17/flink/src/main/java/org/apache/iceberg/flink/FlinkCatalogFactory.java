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
 * 文件级说明：Flink Catalog 工厂实现，用于创建 {@link FlinkCatalog}。
 *
 * <p>所属模块：iceberg-flink（Flink 集成模块），实现 Flink 的 {@link CatalogFactory} SPI 接口。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>解析 Flink SQL CREATE CATALOG 时的配置项，创建对应的 Iceberg {@link CatalogLoader}。
 *   <li>加载 Hadoop/Hive 配置（hive-site.xml、hdfs-site.xml、core-site.xml）。
 *   <li>组装 {@link FlinkCatalog} 实例（含默认数据库、base namespace、缓存策略等）。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>支持 catalog-type（hive/hadoop/rest）与 catalog-impl（自定义）两种方式，互斥校验。
 *   <li>Hive 配置支持显式目录路径和 classpath 自动发现两种加载方式。
 *   <li>可通过继承并重写 {@link #createCatalogLoader} 来支持非内置 Catalog 类型。
 * </ul>
 *
 * <p>支持的配置项：
 *
 * <ul>
 *   <li><code>type</code> - Flink catalog factory key，固定为 "iceberg"
 *   <li><code>catalog-type</code> - Iceberg catalog 类型：hive、hadoop 或 rest
 *   <li><code>uri</code> - Hive Metastore URI（仅 Hive）
 *   <li><code>clients</code> - Hive 客户端连接池大小（仅 Hive）
 *   <li><code>warehouse</code> - 仓库路径（仅 Hadoop）
 *   <li><code>default-database</code> - 默认数据库名
 *   <li><code>base-namespace</code> - 基础命名空间前缀（仅 Hadoop）
 *   <li><code>cache-enabled</code> - 是否启用 catalog 缓存
 * </ul>
 *
 * <p>上下游关系：被 Flink Table API 通过 SPI 机制调用；内部委托 {@link CatalogLoader} 创建 底层 Iceberg Catalog，再包装为
 * {@link FlinkCatalog}。
 */
public class FlinkCatalogFactory implements CatalogFactory {

  // Can not just use "type", it conflicts with CATALOG_TYPE.
  public static final String ICEBERG_CATALOG_TYPE = "catalog-type";
  public static final String ICEBERG_CATALOG_TYPE_HADOOP = "hadoop";
  public static final String ICEBERG_CATALOG_TYPE_HIVE = "hive";
  public static final String ICEBERG_CATALOG_TYPE_REST = "rest";

  public static final String HIVE_CONF_DIR = "hive-conf-dir";
  public static final String HADOOP_CONF_DIR = "hadoop-conf-dir";
  public static final String DEFAULT_DATABASE = "default-database";
  public static final String DEFAULT_DATABASE_NAME = "default";
  public static final String BASE_NAMESPACE = "base-namespace";

  public static final String TYPE = "type";
  public static final String PROPERTY_VERSION = "property-version";

  /**
   * 根据配置创建 Iceberg {@link org.apache.iceberg.catalog.Catalog} 加载器。
   *
   * <p>逻辑：
   *
   * <ul>
   *   <li>若设置了 catalog-impl，则创建 CustomCatalogLoader（与 catalog-type 互斥）。
   *   <li>否则按 catalog-type 分支：hive → 合并 Hive 配置后创建 HiveCatalogLoader； hadoop →
   *       HadoopCatalogLoader；rest → RESTCatalogLoader。
   * </ul>
   *
   * @param name Flink catalog 名称
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

  /** 返回 Flink 识别此 factory 所需的必要上下文（type=iceberg, property-version=1）。 */
  @Override
  public Map<String, String> requiredContext() {
    Map<String, String> context = Maps.newHashMap();
    context.put(TYPE, "iceberg");
    context.put(PROPERTY_VERSION, "1");
    return context;
  }

  /** 返回支持的属性通配符（支持所有属性）。 */
  @Override
  public List<String> supportedProperties() {
    return ImmutableList.of("*");
  }

  /**
   * 创建 Flink Catalog 实例（使用集群 Hadoop 配置）。
   *
   * @param name catalog 名称
   * @param properties catalog 属性
   * @return FlinkCatalog 实例
   */
  @Override
  public Catalog createCatalog(String name, Map<String, String> properties) {
    return createCatalog(name, properties, clusterHadoopConf());
  }

  /**
   * 创建 Flink Catalog 实例的核心方法。
   *
   * <p>逻辑：创建 CatalogLoader → 解析 default-database → 解析 base-namespace → 解析缓存配置
   * （cache-enabled、cache-expiration-interval-ms）→ 组装 FlinkCatalog。
   *
   * @param name catalog 名称
   * @param properties catalog 属性
   * @param hadoopConf Hadoop 配置
   * @return FlinkCatalog 实例
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
   * 将 hive-site.xml / hdfs-site.xml / core-site.xml 合并到 Hadoop Configuration 中。
   *
   * <p>逻辑：以传入的 hadoopConf 为基础创建新 Configuration。若显式指定 hive-confDir， 则校验 hive-site.xml 存在并加载；否则从
   * classpath 尝试加载。hadoop-confDir 类似处理， 同时加载 hdfs-site.xml 和 core-site.xml。
   *
   * @param hadoopConf 基础 Hadoop 配置
   * @param hiveConfDir Hive 配置目录路径（可为空）
   * @param hadoopConfDir Hadoop 配置目录路径（可为空）
   * @return 合并后的新 Hadoop Configuration
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

  /**
   * 从 Flink 全局配置中提取 Hadoop Configuration。
   *
   * <p>逻辑：加载 Flink global configuration，通过 {@link HadoopUtils#getHadoopConfiguration} 转换为 Hadoop
   * Configuration。
   *
   * @return 集群 Hadoop 配置
   */
  public static Configuration clusterHadoopConf() {
    return HadoopUtils.getHadoopConfiguration(GlobalConfiguration.loadConfiguration());
  }
}
