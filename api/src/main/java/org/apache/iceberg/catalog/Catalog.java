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
package org.apache.iceberg.catalog;

import java.util.List;
import java.util.Map;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Schema;
import org.apache.iceberg.SortOrder;
import org.apache.iceberg.Table;
import org.apache.iceberg.Transaction;
import org.apache.iceberg.exceptions.AlreadyExistsException;
import org.apache.iceberg.exceptions.NoSuchNamespaceException;
import org.apache.iceberg.exceptions.NoSuchTableException;

/**
 * 文件级说明：Iceberg 表目录服务（Catalog）的顶层抽象接口。
 *
 * <p>所属模块：iceberg-api（最核心的 API 抽象层，定义表/视图/命名空间等核心契约， 由 iceberg-core 及各存储/引擎集成模块实现，被
 * Spark/Flink/Hive 等引擎直接调用）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>定义表的创建（createTable）、加载（loadTable）、删除（dropTable）、重命名（renameTable）、 列举（listTables）等目录管理操作。
 *   <li>提供基于 {@link TableBuilder} 的统一构建入口，支持创建表及开启创建/替换事务。
 *   <li>提供表注册（registerTable）、缓存失效（invalidateTable）等可选能力。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>面向接口编程：Catalog 仅描述“做什么”，不规定“怎么做”，从而允许 Hive Metastore、 Hadoop 文件系统、REST 服务、JDBC 等不同后端以一致方式接入
 *       Iceberg。
 *   <li>默认方法（default）封装通用编排逻辑（如 createTable 默认实现委托给 buildTable）， 减少实现类样板代码；同时保留抽象方法（如
 *       loadTable/dropTable）由具体实现提供。
 *   <li>计算引擎两阶段初始化：实现类需有无参构造，引擎先实例化再调用 {@link #initialize(String, Map)} 完成配置注入。
 * </ul>
 *
 * <p>上下游关系：被 Spark/Flink/Hive/Trino 等引擎通过 CatalogPlugin 适配调用； 实现侧依赖 iceberg-core 提供的
 * Table/Transaction 等基础类型。
 */
public interface Catalog {

  /**
   * 返回本 Catalog 的名称。
   *
   * <p>默认实现返回 {@link #toString()}。具体实现可覆盖以返回引擎配置中注册的 catalog 名称。
   *
   * @return 本 catalog 的名称
   */
  default String name() {
    return toString();
  }

  /**
   * 列举指定命名空间下的所有表标识符。
   *
   * @param namespace 命名空间
   * @return 该命名空间下的表标识符列表
   * @throws NoSuchNamespaceException 当命名空间不存在时抛出
   */
  List<TableIdentifier> listTables(Namespace namespace);

  /**
   * 创建一张表（完整参数版）。
   *
   * <p>逻辑：默认实现委托给 {@link #buildTable(TableIdentifier, Schema)} 构建器， 依次设置分区规格、存储位置和表属性后调用 {@link
   * TableBuilder#create()} 完成创建。
   *
   * @param identifier 表标识符
   * @param schema 表 Schema
   * @param spec 分区规格
   * @param location 表存储位置；为 null 表示由实现决定
   * @param properties 表属性键值对
   * @return 已创建的 {@link Table} 实例
   * @throws AlreadyExistsException 当表已存在时抛出
   */
  default Table createTable(
      TableIdentifier identifier,
      Schema schema,
      PartitionSpec spec,
      String location,
      Map<String, String> properties) {

    return buildTable(identifier, schema)
        .withPartitionSpec(spec)
        .withLocation(location)
        .withProperties(properties)
        .create();
  }

  /**
   * 创建一张表（不带 location）。
   *
   * @param identifier 表标识符
   * @param schema 表 Schema
   * @param spec 分区规格
   * @param properties 表属性键值对
   * @return 已创建的 {@link Table} 实例
   * @throws AlreadyExistsException 当表已存在时抛出
   */
  default Table createTable(
      TableIdentifier identifier,
      Schema schema,
      PartitionSpec spec,
      Map<String, String> properties) {
    return createTable(identifier, schema, spec, null, properties);
  }

  /**
   * 创建一张表（不带 location 与属性）。
   *
   * @param identifier 表标识符
   * @param schema 表 Schema
   * @param spec 分区规格
   * @return 已创建的 {@link Table} 实例
   * @throws AlreadyExistsException 当表已存在时抛出
   */
  default Table createTable(TableIdentifier identifier, Schema schema, PartitionSpec spec) {
    return createTable(identifier, schema, spec, null, null);
  }

  /**
   * 创建一张非分区表。
   *
   * @param identifier 表标识符
   * @param schema 表 Schema
   * @return 已创建的 {@link Table} 实例
   * @throws AlreadyExistsException 当表已存在时抛出
   */
  default Table createTable(TableIdentifier identifier, Schema schema) {
    return createTable(identifier, schema, PartitionSpec.unpartitioned(), null, null);
  }

  /**
   * 开启一个“创建表”事务（完整参数版）。
   *
   * <p>逻辑：默认实现委托给 {@link #buildTable(TableIdentifier, Schema)} 构建器， 设置分区规格、位置与属性后调用 {@link
   * TableBuilder#createTransaction()}。 事务允许在提交前执行多个表操作（如 append/overwrite）原子化提交。
   *
   * @param identifier 表标识符
   * @param schema 表 Schema
   * @param spec 分区规格
   * @param location 表存储位置；为 null 表示由实现决定
   * @param properties 表属性键值对
   * @return 用于创建表的事务 {@link Transaction}
   * @throws AlreadyExistsException 当表已存在时抛出
   */
  default Transaction newCreateTableTransaction(
      TableIdentifier identifier,
      Schema schema,
      PartitionSpec spec,
      String location,
      Map<String, String> properties) {

    return buildTable(identifier, schema)
        .withPartitionSpec(spec)
        .withLocation(location)
        .withProperties(properties)
        .createTransaction();
  }

  /**
   * 开启一个“创建表”事务（不带 location）。
   *
   * @param identifier 表标识符
   * @param schema 表 Schema
   * @param spec 分区规格
   * @param properties 表属性键值对
   * @return 用于创建表的事务 {@link Transaction}
   * @throws AlreadyExistsException 当表已存在时抛出
   */
  default Transaction newCreateTableTransaction(
      TableIdentifier identifier,
      Schema schema,
      PartitionSpec spec,
      Map<String, String> properties) {
    return newCreateTableTransaction(identifier, schema, spec, null, properties);
  }

  /**
   * 开启一个“创建表”事务（不带 location 与属性）。
   *
   * @param identifier 表标识符
   * @param schema 表 Schema
   * @param spec 分区规格
   * @return 用于创建表的事务 {@link Transaction}
   * @throws AlreadyExistsException 当表已存在时抛出
   */
  default Transaction newCreateTableTransaction(
      TableIdentifier identifier, Schema schema, PartitionSpec spec) {
    return newCreateTableTransaction(identifier, schema, spec, null, null);
  }

  /**
   * 开启一个“创建表”事务（非分区表）。
   *
   * @param identifier 表标识符
   * @param schema 表 Schema
   * @return 用于创建表的事务 {@link Transaction}
   * @throws AlreadyExistsException 当表已存在时抛出
   */
  default Transaction newCreateTableTransaction(TableIdentifier identifier, Schema schema) {
    return newCreateTableTransaction(identifier, schema, PartitionSpec.unpartitioned(), null, null);
  }

  /**
   * 开启一个“替换表”事务（完整参数版）。
   *
   * <p>逻辑：默认实现委托给 {@link #buildTable(TableIdentifier, Schema)} 构建器， 设置分区规格、位置与属性；当 {@code
   * orCreate=true} 时调用 {@link TableBuilder#createOrReplaceTransaction()}（不存在则创建）， 否则调用 {@link
   * TableBuilder#replaceTransaction()}（要求表已存在）。
   *
   * @param identifier 表标识符
   * @param schema 表 Schema
   * @param spec 分区规格
   * @param location 表存储位置；为 null 表示由实现决定
   * @param properties 表属性键值对
   * @param orCreate 是否在表不存在时改为创建
   * @return 用于替换表的事务 {@link Transaction}
   * @throws NoSuchTableException 当表不存在且 orCreate 为 false 时抛出
   */
  default Transaction newReplaceTableTransaction(
      TableIdentifier identifier,
      Schema schema,
      PartitionSpec spec,
      String location,
      Map<String, String> properties,
      boolean orCreate) {

    TableBuilder tableBuilder =
        buildTable(identifier, schema)
            .withPartitionSpec(spec)
            .withLocation(location)
            .withProperties(properties);

    if (orCreate) {
      return tableBuilder.createOrReplaceTransaction();
    } else {
      return tableBuilder.replaceTransaction();
    }
  }

  /**
   * 开启一个“替换表”事务（不带 location）。
   *
   * @param identifier 表标识符
   * @param schema 表 Schema
   * @param spec 分区规格
   * @param properties 表属性键值对
   * @param orCreate 是否在表不存在时改为创建
   * @return 用于替换表的事务 {@link Transaction}
   * @throws NoSuchTableException 当表不存在且 orCreate 为 false 时抛出
   */
  default Transaction newReplaceTableTransaction(
      TableIdentifier identifier,
      Schema schema,
      PartitionSpec spec,
      Map<String, String> properties,
      boolean orCreate) {
    return newReplaceTableTransaction(identifier, schema, spec, null, properties, orCreate);
  }

  /**
   * 开启一个“替换表”事务（不带 location 与属性）。
   *
   * @param identifier 表标识符
   * @param schema 表 Schema
   * @param spec 分区规格
   * @param orCreate 是否在表不存在时改为创建
   * @return 用于替换表的事务 {@link Transaction}
   * @throws NoSuchTableException 当表不存在且 orCreate 为 false 时抛出
   */
  default Transaction newReplaceTableTransaction(
      TableIdentifier identifier, Schema schema, PartitionSpec spec, boolean orCreate) {
    return newReplaceTableTransaction(identifier, schema, spec, null, null, orCreate);
  }

  /**
   * 开启一个“替换表”事务（非分区表）。
   *
   * @param identifier 表标识符
   * @param schema 表 Schema
   * @param orCreate 是否在表不存在时改为创建
   * @return 用于替换表的事务 {@link Transaction}
   * @throws NoSuchTableException 当表不存在且 orCreate 为 false 时抛出
   */
  default Transaction newReplaceTableTransaction(
      TableIdentifier identifier, Schema schema, boolean orCreate) {
    return newReplaceTableTransaction(
        identifier, schema, PartitionSpec.unpartitioned(), null, null, orCreate);
  }

  /**
   * 判断指定表是否存在。
   *
   * <p>逻辑：默认实现尝试调用 {@link #loadTable(TableIdentifier)}，捕获 {@link NoSuchTableException} 时返回 false。
   *
   * @param identifier 表标识符
   * @return 表存在返回 true，否则返回 false
   */
  default boolean tableExists(TableIdentifier identifier) {
    try {
      loadTable(identifier);
      return true;
    } catch (NoSuchTableException e) {
      return false;
    }
  }

  /**
   * 删除表并同时删除其所有数据文件与元数据文件。
   *
   * @param identifier 表标识符
   * @return 表存在并已删除返回 true，表不存在返回 false
   */
  default boolean dropTable(TableIdentifier identifier) {
    return dropTable(identifier, true /* drop data and metadata files */);
  }

  /**
   * 删除表，可选是否立即清理数据与元数据文件。
   *
   * <p>当 {@code purge=true} 时，实现应删除表的所有数据文件和元数据文件； 当 {@code purge=false} 时仅删除 catalog
   * 中的注册条目，文件保留以便后续恢复。
   *
   * @param identifier 表标识符
   * @param purge 是否立即删除所有数据与元数据文件
   * @return 表存在并已删除返回 true，表不存在返回 false
   */
  boolean dropTable(TableIdentifier identifier, boolean purge);

  /**
   * 重命名表。
   *
   * @param from 原表标识符
   * @param to 新表标识符
   * @throws NoSuchTableException 当 from 表不存在时抛出
   * @throws AlreadyExistsException 当 to 表已存在时抛出
   */
  void renameTable(TableIdentifier from, TableIdentifier to);

  /**
   * 加载表。
   *
   * @param identifier 表标识符
   * @return 该标识符对应的 {@link Table} 实现实例
   * @throws NoSuchTableException 当表不存在时抛出
   */
  Table loadTable(TableIdentifier identifier);

  /**
   * 使本 catalog 中缓存的表元数据失效。
   *
   * <p>若表已被加载或缓存，则丢弃缓存数据；若表不存在或未被缓存，则不做任何操作。 用于在表外部被修改后强制下次 {@link #loadTable(TableIdentifier)}
   * 重新拉取最新元数据。
   *
   * @param identifier 表标识符
   */
  default void invalidateTable(TableIdentifier identifier) {}

  /**
   * 将一个已存在的表（以 metadata 文件位置指定）注册到当前 catalog。
   *
   * <p>用于跨 catalog 引用表或恢复丢失的 catalog 条目。若表已存在则抛异常。
   *
   * @param identifier 表标识符
   * @param metadataFileLocation 元数据文件位置
   * @return 注册后的 {@link Table} 实例
   * @throws AlreadyExistsException 当表已在 catalog 中存在时抛出
   */
  default Table registerTable(TableIdentifier identifier, String metadataFileLocation) {
    throw new UnsupportedOperationException("Registering tables is not supported");
  }

  /**
   * 实例化一个 {@link TableBuilder}，用于创建表或开启创建/替换事务。
   *
   * <p>默认实现抛出 {@link UnsupportedOperationException}，要求具体 Catalog 实现覆盖。
   *
   * @param identifier 表标识符
   * @param schema 表 Schema
   * @return 用于创建表或开启事务的构建器
   */
  default TableBuilder buildTable(TableIdentifier identifier, Schema schema) {
    throw new UnsupportedOperationException(
        this.getClass().getName() + " does not implement buildTable");
  }

  /**
   * 使用自定义名称和属性映射初始化 catalog。
   *
   * <p>计算引擎（如 Spark/Flink）会先以无参构造实例化 Catalog，再调用本方法注入引擎传入的 catalog 配置属性，完成两阶段初始化。默认实现为空，子类按需覆盖。
   *
   * @param name catalog 的自定义名称
   * @param properties catalog 配置属性
   */
  default void initialize(String name, Map<String, String> properties) {}

  /**
   * 表构建器：用于创建 {@link Table} 或开启创建/替换 {@link Transaction}。
   *
   * <p>通过 {@link #buildTable(TableIdentifier, Schema)} 获取构建器实例。 采用 Builder
   * 模式集中配置分区规格、排序顺序、存储位置和属性，避免长参数列表。
   */
  interface TableBuilder {
    /**
     * 设置表的分区规格。
     *
     * @param spec 分区规格
     * @return this，便于链式调用
     */
    TableBuilder withPartitionSpec(PartitionSpec spec);

    /**
     * 设置表的排序顺序。
     *
     * @param sortOrder 排序顺序
     * @return this，便于链式调用
     */
    TableBuilder withSortOrder(SortOrder sortOrder);

    /**
     * 设置表的存储位置。
     *
     * @param location 存储位置
     * @return this，便于链式调用
     */
    TableBuilder withLocation(String location);

    /**
     * 批量添加键值属性到表。
     *
     * @param properties 键值属性
     * @return this，便于链式调用
     */
    TableBuilder withProperties(Map<String, String> properties);

    /**
     * 添加单个键值属性到表。
     *
     * @param key 属性键
     * @param value 属性值
     * @return this，便于链式调用
     */
    TableBuilder withProperty(String key, String value);

    /**
     * 创建表并返回。
     *
     * @return 已创建的表
     */
    Table create();

    /**
     * 开启“创建表”事务。
     *
     * @return 用于创建表的 {@link Transaction}
     */
    Transaction createTransaction();

    /**
     * 开启“替换表”事务。
     *
     * @return 用于替换表的 {@link Transaction}
     */
    Transaction replaceTransaction();

    /**
     * 开启“创建或替换表”事务。
     *
     * @return 用于创建或替换表的 {@link Transaction}
     */
    Transaction createOrReplaceTransaction();
  }
}
