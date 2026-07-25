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
 * 可序列化的 Iceberg {@link Table} 加载器接口。
 *
 * <p>所属模块：iceberg-flink v1.15。职责：Flink 集群中（如获取 split 时）需要 {@link Table} 对象，本接口提供在 TaskManager
 * 端懒加载并复用 Table 的能力。
 *
 * <p>设计意图：可序列化 + 懒加载，避免在客户端构造不可序列化的 Table 对象。 上下游：由 FlinkSource/FlinkSink 等 Builder 创建；向下调用 Catalog
 * 或 HadoopTables。
 */
public interface TableLoader extends Closeable, Serializable, Cloneable {

  /** 初始化加载器，必须在 loadTable 之前调用。 */
  void open();

  /** 是否已调用 open 并完成初始化。 */
  boolean isOpen();

  /** 加载并返回 Iceberg Table 实例。 */
  Table loadTable();

  /** 克隆 TableLoader。 */
  @SuppressWarnings({"checkstyle:NoClone", "checkstyle:SuperClone"})
  TableLoader clone();

  /** 基于 Catalog 与表标识符创建 TableLoader。 */
  static TableLoader fromCatalog(CatalogLoader catalogLoader, TableIdentifier identifier) {
    return new CatalogTableLoader(catalogLoader, identifier);
  }

  /** 基于表文件位置（使用集群 Hadoop 配置）创建 TableLoader。 */
  static TableLoader fromHadoopTable(String location) {
    return fromHadoopTable(location, FlinkCatalogFactory.clusterHadoopConf());
  }

  /** 基于表文件位置与显式 Hadoop 配置创建 TableLoader。 */
  static TableLoader fromHadoopTable(String location, Configuration hadoopConf) {
    return new HadoopTableLoader(location, hadoopConf);
  }

  /**
   * 基于文件路径的 TableLoader 实现，使用 {@link HadoopTables} 加载表。
   *
   * <p>所属模块：iceberg-flink v1.15。职责：携带表文件路径与可序列化 Hadoop 配置， 在 open 时构造 HadoopTables，loadTable
   * 时按路径加载表。
   */
  class HadoopTableLoader implements TableLoader {

    private static final long serialVersionUID = 1L;

    private final String location;
    private final SerializableConfiguration hadoopConf;

    private transient HadoopTables tables;

    private HadoopTableLoader(String location, Configuration conf) {
      this.location = location;
      this.hadoopConf = new SerializableConfiguration(conf);
    }

    /** 构造 HadoopTables 实例。 */
    @Override
    public void open() {
      tables = new HadoopTables(hadoopConf.get());
    }

    @Override
    public boolean isOpen() {
      return tables != null;
    }

    /** 初始化 Flink 环境上下文后按路径加载表。 */
    @Override
    public Table loadTable() {
      FlinkEnvironmentContext.init();
      return tables.load(location);
    }

    /** 克隆当前加载器。 */
    @Override
    @SuppressWarnings({"checkstyle:NoClone", "checkstyle:SuperClone"})
    public TableLoader clone() {
      return new HadoopTableLoader(location, new Configuration(hadoopConf.get()));
    }

    @Override
    public void close() {}

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this).add("location", location).toString();
    }
  }

  /**
   * 基于 Catalog 的 TableLoader 实现。
   *
   * <p>所属模块：iceberg-flink v1.15。职责：携带 CatalogLoader 与表标识符， 在 open 时加载 Catalog，loadTable 时按标识符加载表。
   */
  class CatalogTableLoader implements TableLoader {

    private static final long serialVersionUID = 1L;

    private final CatalogLoader catalogLoader;
    private final String identifier;

    private transient Catalog catalog;

    private CatalogTableLoader(CatalogLoader catalogLoader, TableIdentifier tableIdentifier) {
      this.catalogLoader = catalogLoader;
      this.identifier = tableIdentifier.toString();
    }

    /** 通过 CatalogLoader 加载 Catalog。 */
    @Override
    public void open() {
      catalog = catalogLoader.loadCatalog();
    }

    @Override
    public boolean isOpen() {
      return catalog != null;
    }

    /** 初始化 Flink 环境上下文后按标识符加载表。 */
    @Override
    public Table loadTable() {
      FlinkEnvironmentContext.init();
      return catalog.loadTable(TableIdentifier.parse(identifier));
    }

    /** 关闭 catalog（若实现了 Closeable）并清空引用。 */
    @Override
    public void close() throws IOException {
      if (catalog instanceof Closeable) {
        ((Closeable) catalog).close();
      }

      catalog = null;
    }

    /** 克隆当前加载器，并克隆其内部的 CatalogLoader。 */
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
