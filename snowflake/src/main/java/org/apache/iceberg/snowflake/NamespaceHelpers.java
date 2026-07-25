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
package org.apache.iceberg.snowflake;

import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;

/**
 * Iceberg 命名空间 / 表标识符与 Snowflake 三级标识符之间的转换工具类。
 *
 * <p>所属模块：iceberg-snowflake（Iceberg 与 Snowflake 目录集成模块；本类是 Iceberg 通用 Namespace/TableIdentifier 与
 * Snowflake 专属 {@link SnowflakeIdentifier} 之间的桥梁）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>将 Iceberg {@link Namespace}（最多两级）映射为 {@link SnowflakeIdentifier}（ROOT / DATABASE / SCHEMA）。
 *   <li>将 Iceberg {@link TableIdentifier} 映射为 {@link SnowflakeIdentifier}（TABLE 类型）。
 *   <li>提供反向转换：Snowflake 标识符还原为 Iceberg Namespace / TableIdentifier。
 * </ul>
 *
 * <p>设计意图：SnowflakeCatalog 仅支持两级命名空间（database.schema），与 Iceberg Namespace
 * 的层级直接对应。集中所有层级校验与转换逻辑于此类，避免在 catalog 与 client 中重复解析。
 *
 * <p>上下游关系：被 {@link SnowflakeCatalog}、{@link SnowflakeTableOperations} 调用； 依赖 iceberg-api 的 {@link
 * Namespace} 与 {@link TableIdentifier}。
 */
class NamespaceHelpers {
  /** Snowflake 支持的最大命名空间深度（database + schema = 2 级）。 */
  private static final int MAX_NAMESPACE_DEPTH = 2;
  /** 根命名空间层级（长度为 0，表示账户根）。 */
  private static final int NAMESPACE_ROOT_LEVEL = 0;
  /** 数据库层级（命名空间长度为 1）。 */
  private static final int NAMESPACE_DB_LEVEL = 1;
  /** Schema 层级（命名空间长度为 2）。 */
  private static final int NAMESPACE_SCHEMA_LEVEL = 2;

  private NamespaceHelpers() {}

  /**
   * 将 Iceberg {@link Namespace} 转换为 {@link SnowflakeIdentifier}。
   *
   * <p>逻辑：按命名空间长度映射——0 级为 ROOT，1 级为 DATABASE，2 级为 SCHEMA； 超过 2 级则抛出 {@link
   * IllegalArgumentException}，因为 Snowflake 不支持更深的命名空间。
   *
   * @param namespace Iceberg 命名空间
   * @return 对应的 Snowflake 标识符
   * @throws IllegalArgumentException 命名空间深度超过 {@link #MAX_NAMESPACE_DEPTH}
   */
  public static SnowflakeIdentifier toSnowflakeIdentifier(Namespace namespace) {
    switch (namespace.length()) {
      case NAMESPACE_ROOT_LEVEL:
        return SnowflakeIdentifier.ofRoot();
      case NAMESPACE_DB_LEVEL:
        return SnowflakeIdentifier.ofDatabase(namespace.level(NAMESPACE_DB_LEVEL - 1));
      case NAMESPACE_SCHEMA_LEVEL:
        return SnowflakeIdentifier.ofSchema(
            namespace.level(NAMESPACE_DB_LEVEL - 1), namespace.level(NAMESPACE_SCHEMA_LEVEL - 1));
      default:
        throw new IllegalArgumentException(
            String.format(
                "Snowflake max namespace level is %d, got namespace '%s'",
                MAX_NAMESPACE_DEPTH, namespace));
    }
  }

  /**
   * 将 Iceberg {@link TableIdentifier} 转换为 TABLE 类型的 {@link SnowflakeIdentifier}。
   *
   * <p>逻辑：先将其 namespace 部分转换为 Snowflake 标识符，再校验该标识符必须为 SCHEMA 层级 （即表必须位于 database.schema
   * 下），最后拼上表名构造 TABLE 标识符。
   *
   * @param identifier Iceberg 表标识符，其 namespace 必须恰好为两级
   * @return TABLE 类型的 Snowflake 标识符
   * @throws IllegalArgumentException namespace 部分不是 SCHEMA 层级
   */
  public static SnowflakeIdentifier toSnowflakeIdentifier(TableIdentifier identifier) {
    SnowflakeIdentifier namespaceScope = toSnowflakeIdentifier(identifier.namespace());
    Preconditions.checkArgument(
        namespaceScope.type() == SnowflakeIdentifier.Type.SCHEMA,
        "Namespace portion of '%s' must be at the SCHEMA level, got namespaceScope '%s'",
        identifier,
        namespaceScope);
    return SnowflakeIdentifier.ofTable(
        namespaceScope.databaseName(), namespaceScope.schemaName(), identifier.name());
  }

  /**
   * 将 ROOT / DATABASE / SCHEMA 类型的 {@link SnowflakeIdentifier} 转换为 Iceberg {@link Namespace}。
   *
   * <p>逻辑：ROOT 映射为空命名空间，DATABASE 映射为单级命名空间，SCHEMA 映射为两级命名空间； TABLE 类型不支持转换并抛出异常。
   *
   * @param identifier Snowflake 标识符，类型须为 ROOT / DATABASE / SCHEMA
   * @return 对应的 Iceberg 命名空间
   * @throws IllegalArgumentException 标识符类型不是 ROOT / DATABASE / SCHEMA
   */
  public static Namespace toIcebergNamespace(SnowflakeIdentifier identifier) {
    switch (identifier.type()) {
      case ROOT:
        return Namespace.empty();
      case DATABASE:
        return Namespace.of(identifier.databaseName());
      case SCHEMA:
        return Namespace.of(identifier.databaseName(), identifier.schemaName());
      default:
        throw new IllegalArgumentException(
            String.format("Cannot convert identifier '%s' to Namespace", identifier));
    }
  }

  /**
   * 将 TABLE 类型的 {@link SnowflakeIdentifier} 转换为 Iceberg {@link TableIdentifier}。
   *
   * @param identifier Snowflake 标识符，类型必须为 TABLE
   * @return 对应的 Iceberg 表标识符（namespace 为 database.schema）
   * @throws IllegalArgumentException 标识符类型不是 TABLE
   */
  public static TableIdentifier toIcebergTableIdentifier(SnowflakeIdentifier identifier) {
    Preconditions.checkArgument(
        identifier.type() == SnowflakeIdentifier.Type.TABLE,
        "SnowflakeIdentifier must be type TABLE, got '%s'",
        identifier);
    return TableIdentifier.of(
        identifier.databaseName(), identifier.schemaName(), identifier.tableName());
  }
}
