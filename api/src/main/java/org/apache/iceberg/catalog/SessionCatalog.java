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
import java.util.Set;
import java.util.UUID;
import org.apache.iceberg.Schema;
import org.apache.iceberg.Table;
import org.apache.iceberg.exceptions.AlreadyExistsException;
import org.apache.iceberg.exceptions.NamespaceNotEmptyException;
import org.apache.iceberg.exceptions.NoSuchNamespaceException;
import org.apache.iceberg.exceptions.NoSuchTableException;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;

/**
 * 文件级说明：带会话上下文（SessionContext）的目录服务接口。
 *
 * <p>所属模块：iceberg-api（核心 API 抽象层）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>在 {@link Catalog} 基础上引入 {@link SessionContext}，使表的加载/创建/删除、命名空间 管理等操作可以绑定到特定会话（含身份、凭证、属性）。
 *   <li>定义支持会话语境下的表 CRUD、命名空间 CRUD 与缓存失效等操作。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>多租户/多用户隔离：REST catalog 等场景需在服务端区分发起请求的用户与凭证， SessionContext 携带这些信息由实现侧用于鉴权与缓存隔离。
 *   <li>与 {@link Catalog} 解耦：Catalog 的方法不含会话参数，适合单用户引擎内嵌场景； SessionCatalog 面向服务化、需要上下文透传的部署。
 * </ul>
 *
 * <p>上下游关系：被 REST catalog 等需要会话隔离的实现实现；上层由 Spark/Flink 等引擎 在每次请求时构造 SessionContext 调用。
 */
public interface SessionCatalog {
  /**
   * 会话上下文：封装一次会话的标识、身份、凭证与属性。
   *
   * <p>设计意图：作为不可变的请求元数据载体在引擎与 catalog 之间传递；sessionId 可用于 服务端按会话缓存状态，identity/credentials
   * 用于鉴权。wrappedIdentity 用于承载引擎 特定的复杂身份对象（如 Spark 的 Principal），避免在 API 层引入引擎依赖。
   */
  final class SessionContext {
    private final String sessionId;
    private final String identity;
    private final Map<String, String> credentials;
    private final Map<String, String> properties;
    private final Object wrappedIdentity;

    /**
     * 创建一个空的会话上下文（随机 sessionId、无身份、无凭证、空属性）。
     *
     * <p>用于无需鉴权或上下文无关的调用场景。
     *
     * @return 空 {@link SessionContext}
     */
    public static SessionContext createEmpty() {
      return new SessionContext(UUID.randomUUID().toString(), null, null, ImmutableMap.of());
    }

    /**
     * 构造会话上下文（不带 wrappedIdentity）。
     *
     * @param sessionId 会话标识
     * @param identity 用户/主体身份字符串
     * @param credentials 凭证映射
     * @param properties 会话属性
     */
    public SessionContext(
        String sessionId,
        String identity,
        Map<String, String> credentials,
        Map<String, String> properties) {
      this(sessionId, identity, credentials, properties, null);
    }

    /**
     * 构造会话上下文（完整版，含 wrappedIdentity）。
     *
     * @param sessionId 会话标识
     * @param identity 用户/主体身份字符串
     * @param credentials 凭证映射
     * @param properties 会话属性
     * @param wrappedIdentity 引擎特定的不透明身份对象
     */
    public SessionContext(
        String sessionId,
        String identity,
        Map<String, String> credentials,
        Map<String, String> properties,
        Object wrappedIdentity) {
      this.sessionId = sessionId;
      this.identity = identity;
      this.credentials = credentials;
      this.properties = properties;
      this.wrappedIdentity = wrappedIdentity;
    }

    /**
     * 返回会话标识字符串。
     *
     * <p>可用于在会话内缓存状态。
     *
     * @return 会话标识
     */
    public String sessionId() {
      return sessionId;
    }

    /**
     * 返回当前用户/主体的身份字符串。
     *
     * <p>同一 sessionId 下 identity 不可变。
     *
     * @return 用户/主体身份字符串
     */
    public String identity() {
      return identity;
    }

    /**
     * 返回会话的凭证映射。
     *
     * <p>同一 sessionId 下 credentials 不可变。
     *
     * @return 凭证字符串映射
     */
    public Map<String, String> credentials() {
      return credentials;
    }

    /**
     * 返回会话当前属性映射。
     *
     * @return 会话属性映射
     */
    public Map<String, String> properties() {
      return properties;
    }

    /**
     * 返回不透明的包装身份对象。
     *
     * <p>承载引擎特定的复杂身份信息，API 层不解析其内容。
     *
     * @return 包装身份对象
     */
    public Object wrappedIdentity() {
      return wrappedIdentity;
    }
  }

  /**
   * 使用自定义名称和属性映射初始化 catalog。
   *
   * @param name catalog 自定义名称
   * @param properties catalog 配置属性
   */
  void initialize(String name, Map<String, String> properties);

  /**
   * 返回本 catalog 的名称。
   *
   * @return catalog 名称
   */
  String name();

  /**
   * 返回本 catalog 的配置属性。
   *
   * @return catalog 配置属性映射
   */
  Map<String, String> properties();

  /**
   * 列举指定命名空间下的所有表标识符。
   *
   * @param context 会话上下文
   * @param namespace 命名空间
   * @return 该命名空间下的表标识符列表
   * @throws NoSuchNamespaceException 当命名空间不存在时抛出
   */
  List<TableIdentifier> listTables(SessionContext context, Namespace namespace);

  /**
   * 构造一个 {@link Catalog.TableBuilder}，用于在指定会话下创建表或开启创建/替换事务。
   *
   * @param context 会话上下文
   * @param ident 表标识符
   * @param schema 表 Schema
   * @return 表构建器
   */
  Catalog.TableBuilder buildTable(SessionContext context, TableIdentifier ident, Schema schema);

  /**
   * 在指定会话下注册一个已存在的表（由 metadata 文件位置指定）。
   *
   * @param context 会话上下文
   * @param ident 表标识符
   * @param metadataFileLocation 元数据文件位置
   * @return 注册后的 {@link Table} 实例
   * @throws AlreadyExistsException 当表已在 catalog 中存在时抛出
   */
  Table registerTable(SessionContext context, TableIdentifier ident, String metadataFileLocation);

  /**
   * 判断指定会话下表是否存在。
   *
   * <p>逻辑：默认实现尝试 {@link #loadTable(SessionContext, TableIdentifier)}，捕获 {@link
   * NoSuchTableException} 时返回 false。
   *
   * @param context 会话上下文
   * @param ident 表标识符
   * @return 表存在返回 true，否则 false
   */
  default boolean tableExists(SessionContext context, TableIdentifier ident) {
    try {
      loadTable(context, ident);
      return true;
    } catch (NoSuchTableException e) {
      return false;
    }
  }

  /**
   * 在指定会话下加载表。
   *
   * @param context 会话上下文
   * @param ident 表标识符
   * @return 该标识符对应的 {@link Table} 实现实例
   * @throws NoSuchTableException 当表不存在时抛出
   */
  Table loadTable(SessionContext context, TableIdentifier ident);

  /**
   * 在指定会话下删除表，不要求立即删除文件。
   *
   * <p>数据与元数据文件应按 catalog 自身策略删除。
   *
   * @param context 会话上下文
   * @param ident 表标识符
   * @return 表存在并已删除返回 true，表不存在返回 false
   */
  boolean dropTable(SessionContext context, TableIdentifier ident);

  /**
   * 在指定会话下删除表并要求立即清理文件。
   *
   * @param context 会话上下文
   * @param ident 表标识符
   * @return 表存在并已清理返回 true，表不存在返回 false
   * @throws UnsupportedOperationException 当实现不支持立即删除时抛出
   */
  boolean purgeTable(SessionContext context, TableIdentifier ident);

  /**
   * 在指定会话下重命名表。
   *
   * @param context 会话上下文
   * @param from 原表标识符
   * @param to 新表标识符
   * @throws NoSuchTableException 当 from 表不存在时抛出
   * @throws AlreadyExistsException 当 to 表已存在时抛出
   */
  void renameTable(SessionContext context, TableIdentifier from, TableIdentifier to);

  /**
   * 在指定会话下使本 catalog 中缓存的表元数据失效。
   *
   * <p>若表已被加载或缓存，则丢弃缓存数据；若表不存在或未被缓存，则不做任何操作。
   *
   * @param context 会话上下文
   * @param ident 表标识符
   */
  void invalidateTable(SessionContext context, TableIdentifier ident);

  /**
   * 在指定会话下创建命名空间（不带属性）。
   *
   * @param context 会话上下文
   * @param namespace 命名空间
   * @throws AlreadyExistsException 当命名空间已存在时抛出
   * @throws UnsupportedOperationException 当实现不支持创建命名空间时抛出
   */
  default void createNamespace(SessionContext context, Namespace namespace) {
    createNamespace(context, namespace, ImmutableMap.of());
  }

  /**
   * 在指定会话下创建命名空间（带属性）。
   *
   * @param context 会话上下文
   * @param namespace 命名空间
   * @param metadata 命名空间属性映射
   * @throws AlreadyExistsException 当命名空间已存在时抛出
   * @throws UnsupportedOperationException 当实现不支持创建命名空间时抛出
   */
  void createNamespace(SessionContext context, Namespace namespace, Map<String, String> metadata);

  /**
   * 列举顶层命名空间。
   *
   * <p>若某对象（表/视图/函数）存在，其各级父命名空间也必须存在并被本方法返回。 例如存在表 a.b.t 时，本方法须返回 ["a"]。
   *
   * @param context 会话上下文
   * @return 顶层命名空间列表
   */
  default List<Namespace> listNamespaces(SessionContext context) {
    return listNamespaces(context, Namespace.empty());
  }

  /**
   * 列举指定命名空间的子命名空间。
   *
   * <p>例如对已存在的表 a.b.c.table 与 a.b.d.table：
   *
   * <ul>
   *   <li>传入 {@code Namespace.empty()} 返回 {@code Namespace.of("a")}
   *   <li>传入 {@code Namespace.of("a")} 返回 {@code Namespace.of("a","b")}
   *   <li>传入 {@code Namespace.of("a","b")} 返回 {@code Namespace.of("a","b","c")} 与 {@code
   *       Namespace.of("a","b","d")}
   *   <li>传入 {@code Namespace.of("a","b","c")} 返回空列表（无子命名空间）
   * </ul>
   *
   * @param context 会话上下文
   * @param namespace 父命名空间
   * @return 子命名空间列表
   * @throws NoSuchNamespaceException 当命名空间不存在时抛出（可选）
   */
  List<Namespace> listNamespaces(SessionContext context, Namespace namespace);

  /**
   * 加载命名空间的元数据属性。
   *
   * @param context 会话上下文
   * @param namespace 命名空间
   * @return 命名空间属性映射
   * @throws NoSuchNamespaceException 当命名空间不存在时抛出（可选）
   */
  Map<String, String> loadNamespaceMetadata(SessionContext context, Namespace namespace);

  /**
   * 删除命名空间。命名空间存在并已删除返回 true。
   *
   * @param context 会话上下文
   * @param namespace 命名空间
   * @return 已删除返回 true，否则 false
   * @throws NamespaceNotEmptyException 当命名空间非空时抛出
   */
  boolean dropNamespace(SessionContext context, Namespace namespace);

  /**
   * 在指定会话下批量更新命名空间属性。
   *
   * <p>未出现在 updates 中的属性不会被修改或移除。
   *
   * @param context 会话上下文
   * @param namespace 命名空间
   * @param updates 待设置的属性
   * @param removals 待移除的属性键集合
   * @return 成功更新返回 true
   * @throws NoSuchNamespaceException 当命名空间不存在时抛出（可选）
   * @throws UnsupportedOperationException 当实现不支持命名空间属性时抛出
   */
  boolean updateNamespaceMetadata(
      SessionContext context,
      Namespace namespace,
      Map<String, String> updates,
      Set<String> removals);

  /**
   * 判断指定命名空间是否存在。
   *
   * <p>逻辑：默认实现尝试 {@link #loadNamespaceMetadata(SessionContext, Namespace)}， 捕获 {@link
   * NoSuchNamespaceException} 时返回 false。
   *
   * @param context 会话上下文
   * @param namespace 命名空间
   * @return 存在返回 true，否则 false
   */
  default boolean namespaceExists(SessionContext context, Namespace namespace) {
    try {
      loadNamespaceMetadata(context, namespace);
      return true;
    } catch (NoSuchNamespaceException e) {
      return false;
    }
  }
}
