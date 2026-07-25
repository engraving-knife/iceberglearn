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

import java.io.Closeable;
import java.io.IOException;
import java.io.Serializable;
import org.apache.hadoop.conf.Configuration;
import org.apache.iceberg.Table;
import org.apache.iceberg.catalog.Catalog;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.hadoop.HadoopTables;
import org.apache.iceberg.hadoop.SerializableConfiguration;
import org.apache.iceberg.relocated.com.google.common.base.MoreObjects;

/**
 * 文件级说明：可序列化的 Iceberg {@link Table} 加载器接口及其内置实现。
 *
 * <p>所属模块：iceberg-flink v1.17（Iceberg 与 Flink v1.17 集成模块根包）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>定义统一的 {@link Table} 加载契约，使 Flink 集群端（如 TaskManager）能够按需加载表对象。
 *   <li>提供基于 Hadoop 文件路径与基于 Catalog 的两种内置实现。
 *   <li>实现 {@link Serializable} 与 {@link Cloneable}，便于在 Flink 各进程间序列化传输与复制。
 * </ul>
 *
 * <p>设计意图：Flink 作业在 JobManager 端构建 TableLoader 后，需要序列化到 TaskManager 端按需加载 Table，因此 loader
 * 必须可序列化，并且打开/关闭有明确生命周期。
 *
 * <p>上下游关系：上游为 Flink SQL 客户端或 source/sink 工厂，下游为 Iceberg 的 {@link Catalog} 与 {@link HadoopTables}。
 */
public interface TableLoader extends Closeable, Serializable, Cloneable {

  /** 打开加载器，初始化底层 Catalog 或 HadoopTables 资源。 */
  void open();

  /** 返回加载器是否已打开。 */
  boolean isOpen();

  /** 加载并返回 Iceberg Table 对象。 */
  Table loadTable();

  /** 克隆一个 TableLoader，用于多并行度场景下各自维护一份资源。 */
  @SuppressWarnings({"checkstyle:NoClone", "checkstyle:SuperClone"})
  TableLoader clone();

  /** 基于 Catalog 加载表的工厂方法。 */
  static TableLoader fromCatalog(CatalogLoader catalogLoader, TableIdentifier identifier) {
    return new CatalogTableLoader(catalogLoader, identifier);
  }

  /** 基于 Hadoop 文件路径加载表的工厂方法，使用集群默认 Hadoop 配置。 */
  static TableLoader fromHadoopTable(String location) {
    return fromHadoopTable(location, FlinkCatalogFactory.clusterHadoopConf());
  }

  /** 基于 Hadoop 文件路径加载表的工厂方法，可显式指定 Hadoop 配置。 */
  static TableLoader fromHadoopTable(String location, Configuration hadoopConf) {
    return new HadoopTableLoader(location, hadoopConf);
  }

  /** 基于 Hadoop 文件路径的 TableLoader 实现。 */
  class HadoopTableLoader implements TableLoader {

    private static final long serialVersionUID = 1L;

    private final String location;
    private final SerializableConfiguration hadoopConf;

    private transient HadoopTables tables;

    /** 构造加载器，保存表路径与可序列化的 Hadoop 配置。 */
    private HadoopTableLoader(String location, Configuration conf) {
      this.location = location;
      this.hadoopConf = new SerializableConfiguration(conf);
    }

    /** 初始化 HadoopTables，必须在 loadTable 前调用。 */
    @Override
    public void open() {
      tables = new HadoopTables(hadoopConf.get());
    }

    @Override
    public boolean isOpen() {
      return tables != null;
    }

    /** 加载指定路径的 Iceberg 表，并初始化 Flink 环境上下文。 */
    @Override
    public Table loadTable() {
      FlinkEnvironmentContext.init();
      return tables.load(location);
    }

    /** 克隆本加载器，复用同一 Hadoop 配置副本。 */
    @Override
    @SuppressWarnings({"checkstyle:NoClone", "checkstyle:SuperClone"})
    public TableLoader clone() {
      return new HadoopTableLoader(location, new Configuration(hadoopConf.get()));
    }

    /** HadoopTableLoader 无需关闭的资源。 */
    @Override
    public void close() {}

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this).add("location", location).toString();
    }
  }

  /** 基于 Iceberg Catalog 的 TableLoader 实现。 */
  class CatalogTableLoader implements TableLoader {

    private static final long serialVersionUID = 1L;

    private final CatalogLoader catalogLoader;
    private final String identifier;

    private transient Catalog catalog;

    /** 构造加载器，保存 CatalogLoader 与表标识。 */
    private CatalogTableLoader(CatalogLoader catalogLoader, TableIdentifier tableIdentifier) {
      this.catalogLoader = catalogLoader;
      this.identifier = tableIdentifier.toString();
    }

    /** 加载 Catalog 实例，必须在 loadTable 前调用。 */
    @Override
    public void open() {
      catalog = catalogLoader.loadCatalog();
    }

    @Override
    public boolean isOpen() {
      return catalog != null;
    }

    /** 通过 Catalog 加载指定表，并初始化 Flink 环境上下文。 */
    @Override
    public Table loadTable() {
      FlinkEnvironmentContext.init();
      return catalog.loadTable(TableIdentifier.parse(identifier));
    }

    /** 关闭底层 Catalog（若可关闭）并清空引用。 */
    @Override
    public void close() throws IOException {
      if (catalog instanceof Closeable) {
        ((Closeable) catalog).close();
      }

      catalog = null;
    }

    /** 克隆本加载器，CatalogLoader 也会被克隆以避免共享状态。 */
    @Override
    @SuppressWarnings({"checkstyle:NoClone", "checkstyle:SuperClone"})
    public TableLoader clone() {
      return new CatalogTableLoader(catalogLoader.clone(), TableIdentifier.parse(identifier));
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("tableIdentifier", identifier)
          .add("catalogLoader", catalogLoader)
          .toString();
    }
  }
}
