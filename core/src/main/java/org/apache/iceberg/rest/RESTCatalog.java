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
package org.apache.iceberg.rest;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.apache.iceberg.CatalogProperties;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Schema;
import org.apache.iceberg.Table;
import org.apache.iceberg.Transaction;
import org.apache.iceberg.catalog.Catalog;
import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.catalog.SessionCatalog;
import org.apache.iceberg.catalog.SupportsNamespaces;
import org.apache.iceberg.catalog.TableCommit;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.exceptions.NamespaceNotEmptyException;
import org.apache.iceberg.exceptions.NoSuchNamespaceException;
import org.apache.iceberg.hadoop.Configurable;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;

/**
 * 文件级说明：基于 REST 协议的 {@link Catalog} 实现，作为面向用户的表与命名空间入口。
 *
 * <p>所属模块：iceberg-core（REST Catalog 客户端门面层）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>实现 {@link Catalog} 与 {@link SupportsNamespaces} 接口，对外提供表与命名空间的增删改查能力。
 *   <li>持有一个 {@link RESTSessionCatalog} 并通过 {@link RESTSessionCatalog#asCatalog} 转换得到的委托对象 （{@link
 *       #delegate} / {@link #nsDelegate}）执行具体操作。
 *   <li>支持多表事务提交 {@link #commitTransaction}。
 * </ul>
 *
 * <p>设计意图：本类本身不直接发起 HTTP 请求，而是作为薄封装层把调用委托给 {@link RESTSessionCatalog}。 这样可以让无会话语境（{@link
 * SessionCatalog.SessionContext#createEmpty}）的调用方使用熟悉的 {@link Catalog} API，而真正持有连接、认证、缓存逻辑的是 {@link
 * RESTSessionCatalog}。
 *
 * <p>上下游关系：依赖 {@link RESTSessionCatalog} 与 {@link HTTPClient}；被上层应用 （如 Spark/Iceberg 集成）通过 {@link
 * Catalog} 接口调用。
 */
public class RESTCatalog implements Catalog, SupportsNamespaces, Configurable<Object>, Closeable {
  private final RESTSessionCatalog sessionCatalog;
  private final Catalog delegate;
  private final SupportsNamespaces nsDelegate;
  private final SessionCatalog.SessionContext context;

  /** 默认构造函数，使用空会话语境与默认 HTTP 客户端构建器。 */
  public RESTCatalog() {
    this(
        SessionCatalog.SessionContext.createEmpty(),
        config -> HTTPClient.builder(config).uri(config.get(CatalogProperties.URI)).build());
  }

  /**
   * 使用指定的 HTTP 客户端构建器构造，会话语境为空。
   *
   * @param clientBuilder 根据配置 map 构建 {@link RESTClient} 的函数
   */
  public RESTCatalog(Function<Map<String, String>, RESTClient> clientBuilder) {
    this(SessionCatalog.SessionContext.createEmpty(), clientBuilder);
  }

  /**
   * 使用指定的会话语境与 HTTP 客户端构建器构造，并初始化内部 {@link RESTSessionCatalog}。
   *
   * @param context 会话语境，携带凭证与用户属性
   * @param clientBuilder 根据配置 map 构建 {@link RESTClient} 的函数
   */
  public RESTCatalog(
      SessionCatalog.SessionContext context,
      Function<Map<String, String>, RESTClient> clientBuilder) {
    this.sessionCatalog = new RESTSessionCatalog(clientBuilder, null);
    this.delegate = sessionCatalog.asCatalog(context);
    this.nsDelegate = (SupportsNamespaces) delegate;
    this.context = context;
  }

  /**
   * 初始化 Catalog，校验配置并委托给 {@link RESTSessionCatalog#initialize}。
   *
   * @param name Catalog 名称
   * @param props 配置属性，不能为 null
   */
  @Override
  public void initialize(String name, Map<String, String> props) {
    Preconditions.checkArgument(props != null, "Invalid configuration: null");
    sessionCatalog.initialize(name, props);
  }

  /** 返回 Catalog 名称。 */
  @Override
  public String name() {
    return sessionCatalog.name();
  }

  /** 返回 Catalog 配置属性。 */
  public Map<String, String> properties() {
    return sessionCatalog.properties();
  }

  /** 列出指定命名空间下的所有表，委托给 {@link #delegate}。 */
  @Override
  public List<TableIdentifier> listTables(Namespace ns) {
    return delegate.listTables(ns);
  }

  /** 判断指定表是否存在，委托给 {@link #delegate}。 */
  @Override
  public boolean tableExists(TableIdentifier ident) {
    return delegate.tableExists(ident);
  }

  /** 加载指定表，委托给 {@link #delegate}。 */
  @Override
  public Table loadTable(TableIdentifier ident) {
    return delegate.loadTable(ident);
  }

  /** 失效指定表的本地缓存，委托给 {@link #delegate}。 */
  @Override
  public void invalidateTable(TableIdentifier ident) {
    delegate.invalidateTable(ident);
  }

  /** 创建表构建器，委托给 {@link #delegate}。 */
  @Override
  public TableBuilder buildTable(TableIdentifier ident, Schema schema) {
    return delegate.buildTable(ident, schema);
  }

  /** 创建表（完整参数），委托给 {@link #delegate}。 */
  @Override
  public Table createTable(
      TableIdentifier ident,
      Schema schema,
      PartitionSpec spec,
      String location,
      Map<String, String> props) {
    return delegate.createTable(ident, schema, spec, location, props);
  }

  /** 创建表（含分区与属性），委托给 {@link #delegate}。 */
  @Override
  public Table createTable(
      TableIdentifier ident, Schema schema, PartitionSpec spec, Map<String, String> props) {
    return delegate.createTable(ident, schema, spec, props);
  }

  /** 创建表（含分区），委托给 {@link #delegate}。 */
  @Override
  public Table createTable(TableIdentifier ident, Schema schema, PartitionSpec spec) {
    return delegate.createTable(ident, schema, spec);
  }

  /** 创建表（仅 schema），委托给 {@link #delegate}。 */
  @Override
  public Table createTable(TableIdentifier identifier, Schema schema) {
    return delegate.createTable(identifier, schema);
  }

  /** 创建表的建表事务（完整参数），委托给 {@link #delegate}。 */
  @Override
  public Transaction newCreateTableTransaction(
      TableIdentifier ident,
      Schema schema,
      PartitionSpec spec,
      String location,
      Map<String, String> props) {
    return delegate.newCreateTableTransaction(ident, schema, spec, location, props);
  }

  /** 创建表的建表事务（含分区与属性），委托给 {@link #delegate}。 */
  @Override
  public Transaction newCreateTableTransaction(
      TableIdentifier ident, Schema schema, PartitionSpec spec, Map<String, String> props) {
    return delegate.newCreateTableTransaction(ident, schema, spec, props);
  }

  /** 创建表的建表事务（含分区），委托给 {@link #delegate}。 */
  @Override
  public Transaction newCreateTableTransaction(
      TableIdentifier ident, Schema schema, PartitionSpec spec) {
    return delegate.newCreateTableTransaction(ident, schema, spec);
  }

  /** 创建表的建表事务（仅 schema），委托给 {@link #delegate}。 */
  @Override
  public Transaction newCreateTableTransaction(TableIdentifier identifier, Schema schema) {
    return delegate.newCreateTableTransaction(identifier, schema);
  }

  /** 替换表的事务（完整参数），委托给 {@link #delegate}。 */
  @Override
  public Transaction newReplaceTableTransaction(
      TableIdentifier ident,
      Schema schema,
      PartitionSpec spec,
      String location,
      Map<String, String> props,
      boolean orCreate) {
    return delegate.newReplaceTableTransaction(ident, schema, spec, location, props, orCreate);
  }

  /** 替换表的事务（含分区与属性），委托给 {@link #delegate}。 */
  @Override
  public Transaction newReplaceTableTransaction(
      TableIdentifier ident,
      Schema schema,
      PartitionSpec spec,
      Map<String, String> props,
      boolean orCreate) {
    return delegate.newReplaceTableTransaction(ident, schema, spec, props, orCreate);
  }

  /** 替换表的事务（含分区），委托给 {@link #delegate}。 */
  @Override
  public Transaction newReplaceTableTransaction(
      TableIdentifier ident, Schema schema, PartitionSpec spec, boolean orCreate) {
    return delegate.newReplaceTableTransaction(ident, schema, spec, orCreate);
  }

  /** 替换表的事务（仅 schema），委托给 {@link #delegate}。 */
  @Override
  public Transaction newReplaceTableTransaction(
      TableIdentifier ident, Schema schema, boolean orCreate) {
    return delegate.newReplaceTableTransaction(ident, schema, orCreate);
  }

  /** 删除表，委托给 {@link #delegate}。 */
  @Override
  public boolean dropTable(TableIdentifier ident) {
    return delegate.dropTable(ident);
  }

  /** 删除表，可选是否清除数据文件，委托给 {@link #delegate}。 */
  @Override
  public boolean dropTable(TableIdentifier ident, boolean purge) {
    return delegate.dropTable(ident, purge);
  }

  /** 重命名表，委托给 {@link #delegate}。 */
  @Override
  public void renameTable(TableIdentifier from, TableIdentifier to) {
    delegate.renameTable(from, to);
  }

  /** 通过已有的元数据文件位置注册表，委托给 {@link #delegate}。 */
  @Override
  public Table registerTable(TableIdentifier ident, String metadataFileLocation) {
    return delegate.registerTable(ident, metadataFileLocation);
  }

  /** 创建命名空间并设置属性，委托给 {@link #nsDelegate}。 */
  @Override
  public void createNamespace(Namespace ns, Map<String, String> props) {
    nsDelegate.createNamespace(ns, props);
  }

  /** 列出指定父命名空间下的子命名空间，委托给 {@link #nsDelegate}。 */
  @Override
  public List<Namespace> listNamespaces(Namespace ns) throws NoSuchNamespaceException {
    return nsDelegate.listNamespaces(ns);
  }

  /** 加载命名空间元数据属性，委托给 {@link #nsDelegate}。 */
  @Override
  public Map<String, String> loadNamespaceMetadata(Namespace ns) throws NoSuchNamespaceException {
    return nsDelegate.loadNamespaceMetadata(ns);
  }

  /** 删除命名空间，委托给 {@link #nsDelegate}。 */
  @Override
  public boolean dropNamespace(Namespace ns) throws NamespaceNotEmptyException {
    return nsDelegate.dropNamespace(ns);
  }

  /** 设置命名空间属性，委托给 {@link #nsDelegate}。 */
  @Override
  public boolean setProperties(Namespace ns, Map<String, String> props)
      throws NoSuchNamespaceException {
    return nsDelegate.setProperties(ns, props);
  }

  /** 移除命名空间属性，委托给 {@link #nsDelegate}。 */
  @Override
  public boolean removeProperties(Namespace ns, Set<String> props) throws NoSuchNamespaceException {
    return nsDelegate.removeProperties(ns, props);
  }

  /** 设置底层 Hadoop 配置，委托给 {@link RESTSessionCatalog#setConf}。 */
  @Override
  public void setConf(Object conf) {
    sessionCatalog.setConf(conf);
  }

  /** 关闭 Catalog 释放 HTTP 客户端等资源，委托给 {@link RESTSessionCatalog#close}。 */
  @Override
  public void close() throws IOException {
    sessionCatalog.close();
  }

  /**
   * 提交多表事务，委托给 {@link RESTSessionCatalog#commitTransaction}。
   *
   * @param commits 多个表的提交变更列表
   */
  public void commitTransaction(List<TableCommit> commits) {
    sessionCatalog.commitTransaction(context, commits);
  }

  /**
   * 提交多表事务（可变参数形式），将参数包装为不可变列表后委托给 {@link RESTSessionCatalog#commitTransaction}。
   *
   * @param commits 多个表的提交变更
   */
  public void commitTransaction(TableCommit... commits) {
    sessionCatalog.commitTransaction(
        context, ImmutableList.<TableCommit>builder().add(commits).build());
  }
}
