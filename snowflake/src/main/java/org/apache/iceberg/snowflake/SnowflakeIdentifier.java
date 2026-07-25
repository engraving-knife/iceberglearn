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

import org.apache.iceberg.relocated.com.google.common.base.Objects;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;

/**
 * Snowflake 资源的三级标识符，封装已校验的 database/schema/table 名称与资源类型。
 *
 * <p>所属模块：iceberg-snowflake（Iceberg 与 Snowflake 目录集成模块；本类是模块内部 通用的 Snowflake 资源标识，替代直接操作 Iceberg
 * TableIdentifier/Namespace， 避免在各处重复解析与校验层级）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>以类型安全的方式表达 Snowflake 的 ROOT / DATABASE / SCHEMA / TABLE 四级资源。
 *   <li>通过静态工厂方法在构造时完成非空校验，保证对象状态一致。
 *   <li>提供 {@link #toIdentifierString()} 生成可直接用于 Snowflake IDENTIFIER() 语法的字符串。
 * </ul>
 *
 * <p>设计意图：SnowflakeCatalog 恰好支持两级 Iceberg 命名空间，分别对应 Snowflake 的 database 与 schema。集中标识符的解析/校验逻辑于此，让
 * Snowflake 相关的工具类统一基于 此结构操作，减少重复。
 *
 * <p>上下游关系：被 {@link NamespaceHelpers}、{@link JdbcSnowflakeClient}、 {@link SnowflakeCatalog}、{@link
 * SnowflakeTableOperations} 等广泛使用。
 */
class SnowflakeIdentifier {
  /** Snowflake 资源类型枚举。 */
  public enum Type {
    /** 账户根，对应空命名空间。 */
    ROOT,
    /** 数据库层级。 */
    DATABASE,
    /** Schema 层级。 */
    SCHEMA,
    /** 表层级。 */
    TABLE
  }

  private final String databaseName;
  private final String schemaName;
  private final String tableName;
  private final Type type;

  private SnowflakeIdentifier(String databaseName, String schemaName, String tableName, Type type) {
    this.databaseName = databaseName;
    this.schemaName = schemaName;
    this.tableName = tableName;
    this.type = type;
  }

  /** 构造 ROOT 类型标识符（账户根），所有名称字段为 null。 */
  public static SnowflakeIdentifier ofRoot() {
    return new SnowflakeIdentifier(null, null, null, Type.ROOT);
  }

  /**
   * 构造 DATABASE 类型标识符。
   *
   * @param databaseName 数据库名，不可为 null
   */
  public static SnowflakeIdentifier ofDatabase(String databaseName) {
    Preconditions.checkArgument(null != databaseName, "databaseName must be non-null");
    return new SnowflakeIdentifier(databaseName, null, null, Type.DATABASE);
  }

  /**
   * 构造 SCHEMA 类型标识符。
   *
   * @param databaseName 所属数据库名，不可为 null
   * @param schemaName Schema 名，不可为 null
   */
  public static SnowflakeIdentifier ofSchema(String databaseName, String schemaName) {
    Preconditions.checkArgument(null != databaseName, "databaseName must be non-null");
    Preconditions.checkArgument(null != schemaName, "schemaName must be non-null");
    return new SnowflakeIdentifier(databaseName, schemaName, null, Type.SCHEMA);
  }

  /**
   * 构造 TABLE 类型标识符。
   *
   * @param databaseName 所属数据库名，不可为 null
   * @param schemaName 所属 Schema 名，不可为 null
   * @param tableName 表名，不可为 null
   */
  public static SnowflakeIdentifier ofTable(
      String databaseName, String schemaName, String tableName) {
    Preconditions.checkArgument(null != databaseName, "databaseName must be non-null");
    Preconditions.checkArgument(null != schemaName, "schemaName must be non-null");
    Preconditions.checkArgument(null != tableName, "tableName must be non-null");
    return new SnowflakeIdentifier(databaseName, schemaName, tableName, Type.TABLE);
  }

  /**
   * 返回资源类型。
   *
   * <p>类型与名称字段的非空关系：TABLE 时三者皆非空，SCHEMA 时 database/schema 非空， DATABASE 时仅 database 非空，ROOT 时三者皆
   * null。
   */
  public Type type() {
    return type;
  }

  /** 返回表名，仅 TABLE 类型时非 null。 */
  public String tableName() {
    return tableName;
  }

  /** 返回数据库名，ROOT 类型时为 null。 */
  public String databaseName() {
    return databaseName;
  }

  /** 返回 Schema 名，ROOT/DATABASE 类型时为 null。 */
  public String schemaName() {
    return schemaName;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    } else if (!(o instanceof SnowflakeIdentifier)) {
      return false;
    }

    SnowflakeIdentifier that = (SnowflakeIdentifier) o;
    return Objects.equal(this.databaseName, that.databaseName)
        && Objects.equal(this.schemaName, that.schemaName)
        && Objects.equal(this.tableName, that.tableName);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(databaseName, schemaName, tableName);
  }

  /**
   * 生成可用于 Snowflake {@code IDENTIFIER()} 语法的点分字符串。
   *
   * <p>逻辑：TABLE 返回 {@code db.schema.table}，SCHEMA 返回 {@code db.schema}， DATABASE 返回 {@code db}，ROOT
   * 返回空串。
   *
   * @return 点分标识符字符串
   */
  public String toIdentifierString() {
    switch (type()) {
      case TABLE:
        return String.format("%s.%s.%s", databaseName, schemaName, tableName);
      case SCHEMA:
        return String.format("%s.%s", databaseName, schemaName);
      case DATABASE:
        return databaseName;
      default:
        return "";
    }
  }

  @Override
  public String toString() {
    return String.format("%s: '%s'", type(), toIdentifierString());
  }
}
