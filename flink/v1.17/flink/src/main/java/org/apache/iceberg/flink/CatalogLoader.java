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

import java.io.Serializable;
import java.util.Map;
import org.apache.hadoop.conf.Configuration;
import org.apache.iceberg.CatalogProperties;
import org.apache.iceberg.CatalogUtil;
import org.apache.iceberg.catalog.Catalog;
import org.apache.iceberg.hadoop.HadoopCatalog;
import org.apache.iceberg.hadoop.SerializableConfiguration;
import org.apache.iceberg.hive.HiveCatalog;
import org.apache.iceberg.relocated.com.google.common.base.MoreObjects;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.rest.RESTCatalog;

/**
 * 文件级说明：可序列化的 Iceberg {@link Catalog} 加载器接口及其内置实现。
 *
 * <p>所属模块：iceberg-flink（Iceberg 与 Flink 集成模块，提供 Catalog/Sink/Source 等能力）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>定义统一的 {@link Catalog} 加载契约，封装各类 Catalog 的创建细节。
 *   <li>提供 Hadoop/Hive/REST/Custom 四种内置实现，分别对应文件系统、Hive Metastore、 REST 服务和用户自定义 Catalog。
 *   <li>实现 {@link Serializable} 与 {@link Cloneable}，便于在 Flink 各进程间序列化传输。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>跨进程一致：Flink SQL 客户端 / JobManager 端构建 loader 后，序列化到 TaskManager， 在 TaskManager 端反序列化并真正创建
 *       Catalog，确保各 Task 使用同一份 Catalog 配置。
 *   <li>将 Hadoop Configuration 包装为 {@link SerializableConfiguration}，解决原生 Configuration 不可序列化的问题。
 *   <li>属性 Map 统一拷贝为 HashMap，避免不可序列化的 Map 实现导致序列化失败。
 * </ul>
 *
 * <p>上下游关系：被 {@code FlinkCatalogFactory} / {@code FlinkDynamicTableFactory} 等调用以获取 Catalog 实例；底层委托给
 * {@link CatalogUtil#loadCatalog} 完成具体 Catalog 实例化。
 */
public interface CatalogLoader extends Serializable, Cloneable {

  /**
   * 根据持有的配置创建并返回一个新的 {@link Catalog} 实例。
   *
   * <p>设计要点：Flink 中可能在 SQL 客户端或 JobManager 侧初始化本 loader，随后将其序列化到 TaskManager，最终在 TaskManager
   * 侧反序列化并调用本方法创建 Catalog。
   *
   * @return 新创建的 {@link Catalog}
   */
  Catalog loadCatalog();

  /**
   * 克隆当前 CatalogLoader。
   *
   * <p>设计要点：Flink 在算子链构建/并发场景下可能需要独立副本，避免共享可变状态。
   *
   * @return 当前 loader 的克隆副本
   */
  @SuppressWarnings({"checkstyle:NoClone", "checkstyle:SuperClone"})
  CatalogLoader clone();

  /**
   * 构建一个基于 Hadoop 文件系统的 Catalog 加载器。
   *
   * @param name Catalog 名称
   * @param hadoopConf Hadoop 配置
   * @param properties Catalog 属性
   * @return HadoopCatalogLoader 实例
   */
  static CatalogLoader hadoop(
      String name, Configuration hadoopConf, Map<String, String> properties) {
    return new HadoopCatalogLoader(name, hadoopConf, properties);
  }

  /**
   * 构建一个基于 Hive Metastore 的 Catalog 加载器。
   *
   * @param name Catalog 名称
   * @param hadoopConf Hadoop 配置
   * @param properties Catalog 属性（含 uri、warehouse、clientPoolSize 等）
   * @return HiveCatalogLoader 实例
   */
  static CatalogLoader hive(String name, Configuration hadoopConf, Map<String, String> properties) {
    return new HiveCatalogLoader(name, hadoopConf, properties);
  }

  /**
   * 构建一个基于 REST 服务的 Catalog 加载器。
   *
   * @param name Catalog 名称
   * @param hadoopConf Hadoop 配置
   * @param properties Catalog 属性
   * @return RESTCatalogLoader 实例
   */
  static CatalogLoader rest(String name, Configuration hadoopConf, Map<String, String> properties) {
    return new RESTCatalogLoader(name, hadoopConf, properties);
  }

  /**
   * 构建一个自定义实现的 Catalog 加载器。
   *
   * @param name Catalog 名称
   * @param properties Catalog 属性
   * @param hadoopConf Hadoop 配置
   * @param impl 自定义 Catalog 实现类全限定名，不可为空
   * @return CustomCatalogLoader 实例
   */
  static CatalogLoader custom(
      String name, Map<String, String> properties, Configuration hadoopConf, String impl) {
    return new CustomCatalogLoader(name, properties, hadoopConf, impl);
  }

  /**
   * Hadoop 文件系统 Catalog 加载器实现。
   *
   * <p>设计意图：将 Hadoop Configuration 与 Catalog 属性序列化保存，加载时委托 {@link CatalogUtil#loadCatalog} 以 {@link
   * HadoopCatalog} 为实现类创建 Catalog。
   */
  class HadoopCatalogLoader implements CatalogLoader {
    private final String catalogName;
    private final SerializableConfiguration hadoopConf;
    private final String warehouseLocation;
    private final Map<String, String> properties;

    private HadoopCatalogLoader(
        String catalogName, Configuration conf, Map<String, String> properties) {
      this.catalogName = catalogName;
      this.hadoopConf = new SerializableConfiguration(conf);
      this.warehouseLocation = properties.get(CatalogProperties.WAREHOUSE_LOCATION);
      this.properties = Maps.newHashMap(properties);
    }

    /**
     * 加载并返回 HadoopCatalog 实例。
     *
     * <p>逻辑：委托 {@link CatalogUtil#loadCatalog}，传入 {@link HadoopCatalog} 类名、 catalog 名称、属性和反序列化后的
     * Hadoop Configuration。
     *
     * @return 新创建的 {@link Catalog}
     */
    @Override
    public Catalog loadCatalog() {
      return CatalogUtil.loadCatalog(
          HadoopCatalog.class.getName(), catalogName, properties, hadoopConf.get());
    }

    /**
     * 克隆本 loader，使用新的 Configuration 副本以避免共享。
     *
     * @return 新的 HadoopCatalogLoader 实例
     */
    @Override
    @SuppressWarnings({"checkstyle:NoClone", "checkstyle:SuperClone"})
    public CatalogLoader clone() {
      return new HadoopCatalogLoader(catalogName, new Configuration(hadoopConf.get()), properties);
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("catalogName", catalogName)
          .add("warehouseLocation", warehouseLocation)
          .toString();
    }
  }

  /**
   * Hive Metastore Catalog 加载器实现。
   *
   * <p>设计意图：额外抽取 uri、warehouse、clientPoolSize 字段用于 toString 展示； clientPoolSize 未配置时使用默认值。加载时委托
   * {@link CatalogUtil#loadCatalog} 以 {@link HiveCatalog} 为实现类创建 Catalog。
   */
  class HiveCatalogLoader implements CatalogLoader {
    private final String catalogName;
    private final SerializableConfiguration hadoopConf;
    private final String uri;
    private final String warehouse;
    private final int clientPoolSize;
    private final Map<String, String> properties;

    private HiveCatalogLoader(
        String catalogName, Configuration conf, Map<String, String> properties) {
      this.catalogName = catalogName;
      this.hadoopConf = new SerializableConfiguration(conf);
      this.uri = properties.get(CatalogProperties.URI);
      this.warehouse = properties.get(CatalogProperties.WAREHOUSE_LOCATION);
      this.clientPoolSize =
          properties.containsKey(CatalogProperties.CLIENT_POOL_SIZE)
              ? Integer.parseInt(properties.get(CatalogProperties.CLIENT_POOL_SIZE))
              : CatalogProperties.CLIENT_POOL_SIZE_DEFAULT;
      this.properties = Maps.newHashMap(properties);
    }

    /**
     * 加载并返回 HiveCatalog 实例。
     *
     * <p>逻辑：委托 {@link CatalogUtil#loadCatalog}，传入 {@link HiveCatalog} 类名、 catalog 名称、属性和反序列化后的
     * Hadoop Configuration。
     *
     * @return 新创建的 {@link Catalog}
     */
    @Override
    public Catalog loadCatalog() {
      return CatalogUtil.loadCatalog(
          HiveCatalog.class.getName(), catalogName, properties, hadoopConf.get());
    }

    /**
     * 克隆本 loader，使用新的 Configuration 副本以避免共享。
     *
     * @return 新的 HiveCatalogLoader 实例
     */
    @Override
    @SuppressWarnings({"checkstyle:NoClone", "checkstyle:SuperClone"})
    public CatalogLoader clone() {
      return new HiveCatalogLoader(catalogName, new Configuration(hadoopConf.get()), properties);
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("catalogName", catalogName)
          .add("uri", uri)
          .add("warehouse", warehouse)
          .add("clientPoolSize", clientPoolSize)
          .toString();
    }
  }

  /**
   * REST Catalog 加载器实现。
   *
   * <p>设计意图：REST Catalog 通过 HTTP 与远端 Catalog 服务交互，加载时委托 {@link CatalogUtil#loadCatalog} 以 {@link
   * RESTCatalog} 为实现类创建 Catalog。
   */
  class RESTCatalogLoader implements CatalogLoader {
    private final String catalogName;
    private final SerializableConfiguration hadoopConf;
    private final Map<String, String> properties;

    private RESTCatalogLoader(
        String catalogName, Configuration conf, Map<String, String> properties) {
      this.catalogName = catalogName;
      this.hadoopConf = new SerializableConfiguration(conf);
      this.properties = Maps.newHashMap(properties);
    }

    /**
     * 加载并返回 RESTCatalog 实例。
     *
     * <p>逻辑：委托 {@link CatalogUtil#loadCatalog}，传入 {@link RESTCatalog} 类名、 catalog 名称、属性和反序列化后的
     * Hadoop Configuration。
     *
     * @return 新创建的 {@link Catalog}
     */
    @Override
    public Catalog loadCatalog() {
      return CatalogUtil.loadCatalog(
          RESTCatalog.class.getName(), catalogName, properties, hadoopConf.get());
    }

    /**
     * 克隆本 loader，使用新的 Configuration 副本以避免共享。
     *
     * @return 新的 RESTCatalogLoader 实例
     */
    @Override
    @SuppressWarnings({"checkstyle:NoClone", "checkstyle:SuperClone"})
    public CatalogLoader clone() {
      return new RESTCatalogLoader(catalogName, new Configuration(hadoopConf.get()), properties);
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("catalogName", catalogName)
          .add("properties", properties)
          .toString();
    }
  }

  /**
   * 自定义 Catalog 加载器实现。
   *
   * <p>设计意图：支持用户通过 {@code catalog-impl} 属性指定任意 Catalog 实现类。 构造时校验 impl 类名非空；属性 Map 拷贝为 HashMap
   * 以保证可序列化。
   */
  class CustomCatalogLoader implements CatalogLoader {

    private final SerializableConfiguration hadoopConf;
    private final Map<String, String> properties;
    private final String name;
    private final String impl;

    private CustomCatalogLoader(
        String name, Map<String, String> properties, Configuration conf, String impl) {
      this.hadoopConf = new SerializableConfiguration(conf);
      this.properties = Maps.newHashMap(properties); // wrap into a hashmap for serialization
      this.name = name;
      this.impl =
          Preconditions.checkNotNull(
              impl, "Cannot initialize custom Catalog, impl class name is null");
    }

    /**
     * 加载并返回自定义 Catalog 实例。
     *
     * <p>逻辑：委托 {@link CatalogUtil#loadCatalog}，传入用户指定的 impl 类名、 catalog 名称、属性和反序列化后的 Hadoop
     * Configuration。
     *
     * @return 新创建的 {@link Catalog}
     */
    @Override
    public Catalog loadCatalog() {
      return CatalogUtil.loadCatalog(impl, name, properties, hadoopConf.get());
    }

    /**
     * 克隆本 loader，使用新的 Configuration 副本以避免共享。
     *
     * @return 新的 CustomCatalogLoader 实例
     */
    @Override
    @SuppressWarnings({"checkstyle:NoClone", "checkstyle:SuperClone"})
    public CatalogLoader clone() {
      return new CustomCatalogLoader(name, properties, new Configuration(hadoopConf.get()), impl);
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this).add("name", name).add("impl", impl).toString();
    }
  }
}
