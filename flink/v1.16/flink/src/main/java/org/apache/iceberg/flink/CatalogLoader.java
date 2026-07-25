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
 * 可序列化的 Iceberg {@link Catalog} 加载器接口。
 *
 * <p>所属模块：iceberg-flink v1.15。职责：在客户端或 JobManager 端构造、序列化后传输到 TaskManager，再反序列化并加载真正的 Iceberg
 * Catalog，避免分布式环境下不可序列化的问题。
 *
 * <p>设计意图：将 Catalog 创建过程封装为可序列化对象，按 hive/hadoop/rest/custom 四种 内置实现提供静态工厂方法。上下游：由 {@link
 * FlinkCatalogFactory} 创建；被 {@link org.apache.iceberg.flink.TableLoader} 等使用。
 */
public interface CatalogLoader extends Serializable, Cloneable {

  /**
   * 根据所持属性创建新的 {@link Catalog} 实例。
   *
   * <p>注意：Flink 中可能在 SQL 客户端或 JobManager 端初始化 {@link CatalogLoader}， 然后序列化到 TaskManager，最终在
   * TaskManager 反序列化后调用本方法创建 Catalog。
   *
   * @return 新创建的 {@link Catalog}
   */
  Catalog loadCatalog();

  /** 克隆 CatalogLoader。 */
  @SuppressWarnings({"checkstyle:NoClone", "checkstyle:SuperClone"})
  CatalogLoader clone();

  /** 创建基于 Hadoop 文件系统的 Catalog 加载器。 */
  static CatalogLoader hadoop(
      String name, Configuration hadoopConf, Map<String, String> properties) {
    return new HadoopCatalogLoader(name, hadoopConf, properties);
  }

  /** 创建 Hive Metastore Catalog 加载器。 */
  static CatalogLoader hive(String name, Configuration hadoopConf, Map<String, String> properties) {
    return new HiveCatalogLoader(name, hadoopConf, properties);
  }

  /** 创建 REST Catalog 加载器。 */
  static CatalogLoader rest(String name, Configuration hadoopConf, Map<String, String> properties) {
    return new RESTCatalogLoader(name, hadoopConf, properties);
  }

  /** 创建用户自定义实现类的 Catalog 加载器。 */
  static CatalogLoader custom(
      String name, Map<String, String> properties, Configuration hadoopConf, String impl) {
    return new CustomCatalogLoader(name, properties, hadoopConf, impl);
  }

  /**
   * 基于 Hadoop 文件系统的 Catalog 加载器实现，对应 {@link HadoopCatalog}。
   *
   * <p>所属模块：iceberg-flink v1.15。职责：序列化 warehouse 路径、Hadoop 配置及属性， 在 TaskManager 端反序列化后通过 {@link
   * CatalogUtil#loadCatalog} 创建 {@link HadoopCatalog}。
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

    /** 加载并返回 HadoopCatalog 实例。 */
    @Override
    public Catalog loadCatalog() {
      return CatalogUtil.loadCatalog(
          HadoopCatalog.class.getName(), catalogName, properties, hadoopConf.get());
    }

    /** 克隆当前加载器，复制一份 Hadoop 配置以避免共享可变状态。 */
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
   * Hive Metastore Catalog 加载器实现，对应 {@link HiveCatalog}。
   *
   * <p>所属模块：iceberg-flink v1.15。职责：携带 uri、warehouse、clientPoolSize 等属性， 在 TaskManager 端反序列化后创建
   * {@link HiveCatalog}。
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

    /** 加载并返回 HiveCatalog 实例。 */
    @Override
    public Catalog loadCatalog() {
      return CatalogUtil.loadCatalog(
          HiveCatalog.class.getName(), catalogName, properties, hadoopConf.get());
    }

    /** 克隆当前加载器。 */
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
   * REST Catalog 加载器实现，对应 {@link RESTCatalog}。
   *
   * <p>所属模块：iceberg-flink v1.15。职责：携带属性与 Hadoop 配置， 在 TaskManager 端创建 {@link RESTCatalog}。
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

    /** 加载并返回 RESTCatalog 实例。 */
    @Override
    public Catalog loadCatalog() {
      return CatalogUtil.loadCatalog(
          RESTCatalog.class.getName(), catalogName, properties, hadoopConf.get());
    }

    /** 克隆当前加载器。 */
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
   * 自定义 Catalog 加载器实现，按用户指定的实现类全限定名加载 Catalog。
   *
   * <p>所属模块：iceberg-flink v1.15。职责：携带 impl 类名与属性，在 TaskManager 端 通过反射创建用户自定义 Catalog。
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

    /** 通过反射加载并返回用户自定义 Catalog 实例。 */
    @Override
    public Catalog loadCatalog() {
      return CatalogUtil.loadCatalog(impl, name, properties, hadoopConf.get());
    }

    /** 克隆当前加载器。 */
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
