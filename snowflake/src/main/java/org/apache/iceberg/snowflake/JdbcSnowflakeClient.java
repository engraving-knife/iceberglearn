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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import org.apache.iceberg.exceptions.NoSuchNamespaceException;
import org.apache.iceberg.exceptions.NoSuchTableException;
import org.apache.iceberg.jdbc.JdbcClientPool;
import org.apache.iceberg.jdbc.UncheckedInterruptedException;
import org.apache.iceberg.jdbc.UncheckedSQLException;
import org.apache.iceberg.relocated.com.google.common.annotations.VisibleForTesting;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableSet;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;

/**
 * 基于 Snowflake JDBC 驱动实现的 {@link SnowflakeClient}，用于与 Snowflake 的 Iceberg 资源模型交互。
 *
 * <p>所属模块：iceberg-snowflake（Iceberg 与 Snowflake 的目录集成模块，位于 catalog 抽象之下， 负责把对 Snowflake 资源的访问封装为
 * Iceberg 统一的 client 接口）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>通过 JDBC 连接池执行 Snowflake 的 SHOW / SELECT 系列命令，列举数据库、Schema 与 Iceberg 表。
 *   <li>调用 {@code SYSTEM$GET_ICEBERG_TABLE_INFORMATION} 获取表的元数据 JSON。
 *   <li>将 Snowflake 的 SQL 异常（按错误码）映射为 Iceberg 的 {@link NoSuchNamespaceException} / {@link
 *       NoSuchTableException} / {@link UncheckedSQLException}，统一对外语义。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>协议解耦：{@link SnowflakeClient} 接口不假设底层是 REST 还是 JDBC，本类作为 JDBC 实现， 可被替换为其他协议实现以便测试或演进。
 *   <li>{@link QueryHarness} 抽象 PreparedStatement 的样板代码，便于注入子类进行调试与测试。
 *   <li>错误码集合（{@code *_NOT_FOUND_ERROR_CODES}）覆盖多个语义等价的 Snowflake 错误码， 兼容不同 Snowflake 版本的差异。
 * </ul>
 *
 * <p>上下游关系：被 {@link SnowflakeCatalog} 与 {@link SnowflakeTableOperations} 调用； 依赖 {@link
 * JdbcClientPool}（iceberg-jdbc）管理连接池。
 */
class JdbcSnowflakeClient implements SnowflakeClient {
  /** 期望加载的 Snowflake JDBC 驱动实现类全限定名，加载失败时仅告警不阻断。 */
  static final String EXPECTED_JDBC_IMPL = "net.snowflake.client.jdbc.SnowflakeDriver";

  /** Snowflake 中表示"数据库不存在"的错误码集合，用于将 SQL 异常映射为 Iceberg 语义异常。 */
  @VisibleForTesting
  static final Set<Integer> DATABASE_NOT_FOUND_ERROR_CODES = ImmutableSet.of(2001, 2003, 2043);

  /** Snowflake 中表示"Schema 不存在"的错误码集合。 */
  @VisibleForTesting
  static final Set<Integer> SCHEMA_NOT_FOUND_ERROR_CODES = ImmutableSet.of(2001, 2003, 2043);

  /** Snowflake 中表示"表不存在"的错误码集合。 */
  @VisibleForTesting
  static final Set<Integer> TABLE_NOT_FOUND_ERROR_CODES = ImmutableSet.of(2001, 2003, 2043);

  /**
   * 函数式接口：将 {@link ResultSet} 解析为目标类型 T。
   *
   * <p>设计意图：把"执行 SQL"与"解析结果"分离，使查询执行逻辑可复用，解析逻辑可替换。
   */
  @FunctionalInterface
  interface ResultSetParser<T> {
    T parse(ResultSet rs) throws SQLException;
  }

  /**
   * SQL 执行封装：负责 PreparedStatement 的创建、参数绑定、执行与结果解析的样板流程。
   *
   * <p>设计意图：抽出此内部类便于在测试中通过 {@link #setQueryHarness} 注入子类， 从而在不真实连接 Snowflake 的情况下验证查询逻辑。
   */
  static class QueryHarness {
    /**
     * 执行一条 SQL 查询并用给定解析器将结果集转换为对象。
     *
     * <p>逻辑：创建 {@link PreparedStatement}，按顺序绑定字符串参数，执行查询后将 {@link ResultSet} 交给 {@code parser}
     * 解析。所有资源（statement、result set）均在 try-with-resources 中自动关闭。
     *
     * @param conn JDBC 连接
     * @param sql 待执行的 SQL，使用 "?" 占位
     * @param parser 结果集解析器
     * @param args 绑定到占位符的字符串参数，按顺序对应
     * @param <T> 解析结果的类型
     * @return 解析后的对象
     * @throws SQLException 执行或解析过程中发生 SQL 异常
     */
    public <T> T query(Connection conn, String sql, ResultSetParser<T> parser, String... args)
        throws SQLException {
      try (PreparedStatement statement = conn.prepareStatement(sql)) {
        if (args != null) {
          for (int i = 0; i < args.length; ++i) {
            statement.setString(i + 1, args[i]);
          }
        }

        try (ResultSet rs = statement.executeQuery()) {
          return parser.parse(rs);
        }
      }
    }
  }

  /**
   * 结果集解析器：将仅含 "name" 列的结果集解析为 Snowflake 数据库标识符列表。
   *
   * <p>预期结果集格式：每行一个 "name" 字段，代表数据库名。
   */
  public static final ResultSetParser<List<SnowflakeIdentifier>> DATABASE_RESULT_SET_HANDLER =
      rs -> {
        List<SnowflakeIdentifier> databases = Lists.newArrayList();
        while (rs.next()) {
          String databaseName = rs.getString("name");
          databases.add(SnowflakeIdentifier.ofDatabase(databaseName));
        }
        return databases;
      };

  /**
   * 结果集解析器：将含 "database_name" 和 "name" 列的结果集解析为 Schema 标识符列表。
   *
   * <p>预期结果集格式：每行包含 "database_name" 与 "name"（Schema 名）。
   */
  public static final ResultSetParser<List<SnowflakeIdentifier>> SCHEMA_RESULT_SET_HANDLER =
      rs -> {
        List<SnowflakeIdentifier> schemas = Lists.newArrayList();
        while (rs.next()) {
          String databaseName = rs.getString("database_name");
          String schemaName = rs.getString("name");
          schemas.add(SnowflakeIdentifier.ofSchema(databaseName, schemaName));
        }
        return schemas;
      };

  /** 结果集解析器：将含 "database_name"、"schema_name"、"name" 列的结果集解析为表标识符列表。 */
  public static final ResultSetParser<List<SnowflakeIdentifier>> TABLE_RESULT_SET_HANDLER =
      rs -> {
        List<SnowflakeIdentifier> tables = Lists.newArrayList();
        while (rs.next()) {
          String databaseName = rs.getString("database_name");
          String schemaName = rs.getString("schema_name");
          String tableName = rs.getString("name");
          tables.add(SnowflakeIdentifier.ofTable(databaseName, schemaName, tableName));
        }
        return tables;
      };

  /**
   * 结果集解析器：将单行结果集中的 "METADATA" 列解析为 {@link SnowflakeTableMetadata}。
   *
   * <p>预期结果集仅一行，包含 "METADATA" 字段（JSON 文本）。无数据时返回 null。
   */
  public static final ResultSetParser<SnowflakeTableMetadata> TABLE_METADATA_RESULT_SET_HANDLER =
      rs -> {
        if (!rs.next()) {
          return null;
        }

        String rawJsonVal = rs.getString("METADATA");
        return SnowflakeTableMetadata.parseJson(rawJsonVal);
      };

  private final JdbcClientPool connectionPool;
  private QueryHarness queryHarness;

  /**
   * 构造客户端，绑定一个 JDBC 连接池。
   *
   * @param conn 已配置好的 Snowflake JDBC 连接池，不可为 null
   */
  JdbcSnowflakeClient(JdbcClientPool conn) {
    Preconditions.checkArgument(null != conn, "JdbcClientPool must be non-null");
    connectionPool = conn;
    queryHarness = new QueryHarness();
  }

  /**
   * 替换默认的查询执行器，用于测试注入。
   *
   * @param queryHarness 自定义的 {@link QueryHarness}
   */
  @VisibleForTesting
  void setQueryHarness(QueryHarness queryHarness) {
    this.queryHarness = queryHarness;
  }

  /**
   * 判断指定数据库是否存在。
   *
   * <p>逻辑：执行 {@code SHOW SCHEMAS IN DATABASE IDENTIFIER(?) LIMIT 1}， 若能查到 Schema 则数据库存在；若 Snowflake
   * 返回"数据库不存在"错误码则返回 false； 其他异常包装为 {@link UncheckedSQLException} 抛出。
   *
   * @param database 类型必须为 {@link SnowflakeIdentifier.Type#DATABASE} 的标识符
   * @return 数据库存在返回 true，否则 false
   */
  @Override
  public boolean databaseExists(SnowflakeIdentifier database) {
    Preconditions.checkArgument(
        database.type() == SnowflakeIdentifier.Type.DATABASE,
        "databaseExists requires a DATABASE identifier, got '%s'",
        database);

    final String finalQuery = "SHOW SCHEMAS IN DATABASE IDENTIFIER(?) LIMIT 1";

    List<SnowflakeIdentifier> schemas;
    try {
      schemas =
          connectionPool.run(
              conn ->
                  queryHarness.query(
                      conn, finalQuery, SCHEMA_RESULT_SET_HANDLER, database.databaseName()));
    } catch (SQLException e) {
      if (DATABASE_NOT_FOUND_ERROR_CODES.contains(e.getErrorCode())) {
        return false;
      }
      throw new UncheckedSQLException(e, "Failed to check if database '%s' exists", database);
    } catch (InterruptedException e) {
      throw new UncheckedInterruptedException(
          e, "Interrupted while checking if database '%s' exists", database);
    }

    return !schemas.isEmpty();
  }

  /**
   * 判断指定 Schema 及其父数据库是否存在。
   *
   * <p>逻辑：先检查父数据库是否存在，不存在直接返回 false；再执行 {@code SHOW TABLES IN SCHEMA IDENTIFIER(?) LIMIT
   * 1}，若返回"Schema 不存在"错误码则返回 false， 否则视为存在。父数据库存在性检查作为快速短路，避免无谓的 Schema 查询。
   *
   * @param schema 类型必须为 {@link SnowflakeIdentifier.Type#SCHEMA} 的标识符
   * @return Schema 及其父数据库都存在时返回 true，否则 false
   */
  @Override
  public boolean schemaExists(SnowflakeIdentifier schema) {
    Preconditions.checkArgument(
        schema.type() == SnowflakeIdentifier.Type.SCHEMA,
        "schemaExists requires a SCHEMA identifier, got '%s'",
        schema);

    if (!databaseExists(SnowflakeIdentifier.ofDatabase(schema.databaseName()))) {
      return false;
    }

    final String finalQuery = "SHOW TABLES IN SCHEMA IDENTIFIER(?) LIMIT 1";

    List<SnowflakeIdentifier> tables;
    try {
      tables =
          connectionPool.run(
              conn ->
                  queryHarness.query(
                      conn, finalQuery, TABLE_RESULT_SET_HANDLER, schema.toIdentifierString()));
    } catch (SQLException e) {
      if (SCHEMA_NOT_FOUND_ERROR_CODES.contains(e.getErrorCode())) {
        return false;
      }
      throw new UncheckedSQLException(e, "Failed to check if schema '%s' exists", schema);
    } catch (InterruptedException e) {
      throw new UncheckedInterruptedException(
          e, "Interrupted while checking if schema '%s' exists", schema);
    }

    return true;
  }

  /**
   * 列举当前账户下的所有 Snowflake 数据库。
   *
   * <p>执行 {@code SHOW DATABASES IN ACCOUNT}，结果经 {@link #DATABASE_RESULT_SET_HANDLER} 解析。
   * 每个结果项的类型会被校验为 DATABASE。
   *
   * @return 数据库标识符列表
   */
  @Override
  public List<SnowflakeIdentifier> listDatabases() {
    List<SnowflakeIdentifier> databases;
    try {
      databases =
          connectionPool.run(
              conn ->
                  queryHarness.query(
                      conn, "SHOW DATABASES IN ACCOUNT", DATABASE_RESULT_SET_HANDLER));
    } catch (SQLException e) {
      throw snowflakeExceptionToIcebergException(
          SnowflakeIdentifier.ofRoot(), e, "Failed to list databases");
    } catch (InterruptedException e) {
      throw new UncheckedInterruptedException(e, "Interrupted while listing databases");
    }
    databases.forEach(
        db ->
            Preconditions.checkState(
                db.type() == SnowflakeIdentifier.Type.DATABASE,
                "Expected DATABASE, got identifier '%s'",
                db));
    return databases;
  }

  /**
   * 列举指定范围内的所有 Snowflake Schema。
   *
   * <p>逻辑：根据 scope 类型构造不同的 SHOW SCHEMAS 语句——ROOT 时列举账户级， DATABASE 时列举指定数据库下。执行后将结果解析为 Schema
   * 标识符，并校验每项类型为 SCHEMA。
   *
   * @param scope 列举范围，可为 ROOT 或 DATABASE
   * @return Schema 标识符列表
   * @throws IllegalArgumentException scope 类型不被支持
   */
  @Override
  public List<SnowflakeIdentifier> listSchemas(SnowflakeIdentifier scope) {
    StringBuilder baseQuery = new StringBuilder("SHOW SCHEMAS");
    String[] queryParams = null;
    switch (scope.type()) {
      case ROOT:
        // account-level listing
        baseQuery.append(" IN ACCOUNT");
        break;
      case DATABASE:
        // database-level listing
        baseQuery.append(" IN DATABASE IDENTIFIER(?)");
        queryParams = new String[] {scope.toIdentifierString()};
        break;
      default:
        throw new IllegalArgumentException(
            String.format("Unsupported scope type for listSchemas: %s", scope));
    }

    final String finalQuery = baseQuery.toString();
    final String[] finalQueryParams = queryParams;
    List<SnowflakeIdentifier> schemas;
    try {
      schemas =
          connectionPool.run(
              conn ->
                  queryHarness.query(
                      conn, finalQuery, SCHEMA_RESULT_SET_HANDLER, finalQueryParams));
    } catch (SQLException e) {
      throw snowflakeExceptionToIcebergException(
          scope, e, String.format("Failed to list schemas for scope '%s'", scope));
    } catch (InterruptedException e) {
      throw new UncheckedInterruptedException(
          e, "Interrupted while listing schemas for scope '%s'", scope);
    }
    schemas.forEach(
        schema ->
            Preconditions.checkState(
                schema.type() == SnowflakeIdentifier.Type.SCHEMA,
                "Expected SCHEMA, got identifier '%s' for scope '%s'",
                schema,
                scope));
    return schemas;
  }

  /**
   * 列举指定范围内的所有 Snowflake Iceberg 表。
   *
   * <p>逻辑：根据 scope 类型构造不同的 SHOW ICEBERG TABLES 语句——ROOT 列举账户级， DATABASE 列举数据库级，SCHEMA 列举 Schema
   * 级。结果经 {@link #TABLE_RESULT_SET_HANDLER} 解析并校验每项类型为 TABLE。
   *
   * @param scope 列举范围，可为 ROOT、DATABASE 或 SCHEMA
   * @return Iceberg 表标识符列表
   * @throws IllegalArgumentException scope 类型不被支持
   */
  @Override
  public List<SnowflakeIdentifier> listIcebergTables(SnowflakeIdentifier scope) {
    StringBuilder baseQuery = new StringBuilder("SHOW ICEBERG TABLES");
    String[] queryParams = null;
    switch (scope.type()) {
      case ROOT:
        // account-level listing
        baseQuery.append(" IN ACCOUNT");
        break;
      case DATABASE:
        // database-level listing
        baseQuery.append(" IN DATABASE IDENTIFIER(?)");
        queryParams = new String[] {scope.toIdentifierString()};
        break;
      case SCHEMA:
        // schema-level listing
        baseQuery.append(" IN SCHEMA IDENTIFIER(?)");
        queryParams = new String[] {scope.toIdentifierString()};
        break;
      default:
        throw new IllegalArgumentException(
            String.format("Unsupported scope type for listIcebergTables: %s", scope));
    }

    final String finalQuery = baseQuery.toString();
    final String[] finalQueryParams = queryParams;
    List<SnowflakeIdentifier> tables;
    try {
      tables =
          connectionPool.run(
              conn ->
                  queryHarness.query(conn, finalQuery, TABLE_RESULT_SET_HANDLER, finalQueryParams));
    } catch (SQLException e) {
      throw snowflakeExceptionToIcebergException(
          scope, e, String.format("Failed to list tables for scope '%s'", scope));
    } catch (InterruptedException e) {
      throw new UncheckedInterruptedException(
          e, "Interrupted while listing tables for scope '%s'", scope);
    }
    tables.forEach(
        table ->
            Preconditions.checkState(
                table.type() == SnowflakeIdentifier.Type.TABLE,
                "Expected TABLE, got identifier '%s' for scope '%s'",
                table,
                scope));
    return tables;
  }

  /**
   * 加载指定表的 Snowflake 元数据（含 Iceberg 元数据位置）。
   *
   * <p>逻辑：执行 {@code SELECT SYSTEM$GET_ICEBERG_TABLE_INFORMATION(?) AS METADATA}， 用 {@link
   * #TABLE_METADATA_RESULT_SET_HANDLER} 把返回的 JSON 解析为 {@link SnowflakeTableMetadata}。表不存在时返回 null。
   *
   * @param tableIdentifier 类型必须为 {@link SnowflakeIdentifier.Type#TABLE}
   * @return 表元数据，表不存在时可能为 null
   */
  @Override
  public SnowflakeTableMetadata loadTableMetadata(SnowflakeIdentifier tableIdentifier) {
    Preconditions.checkArgument(
        tableIdentifier.type() == SnowflakeIdentifier.Type.TABLE,
        "loadTableMetadata requires a TABLE identifier, got '%s'",
        tableIdentifier);
    SnowflakeTableMetadata tableMeta;
    try {
      final String finalQuery = "SELECT SYSTEM$GET_ICEBERG_TABLE_INFORMATION(?) AS METADATA";
      tableMeta =
          connectionPool.run(
              conn ->
                  queryHarness.query(
                      conn,
                      finalQuery,
                      TABLE_METADATA_RESULT_SET_HANDLER,
                      tableIdentifier.toIdentifierString()));
    } catch (SQLException e) {
      throw snowflakeExceptionToIcebergException(
          tableIdentifier,
          e,
          String.format("Failed to get table metadata for '%s'", tableIdentifier));
    } catch (InterruptedException e) {
      throw new UncheckedInterruptedException(
          e, "Interrupted while getting table metadata for '%s'", tableIdentifier);
    }
    return tableMeta;
  }

  /** 关闭底层 JDBC 连接池，释放资源。 */
  @Override
  public void close() {
    connectionPool.close();
  }

  /**
   * 将 Snowflake 的 SQL 异常转换为对应的 Iceberg 异常。
   *
   * <p>逻辑：根据标识符类型与 SQL 错误码判定语义——DATABASE/SCHEMA 不存在时返回 {@link NoSuchNamespaceException}，TABLE
   * 不存在时返回 {@link NoSuchTableException}， 其余情况回退为 {@link UncheckedSQLException}。
   *
   * @param identifier 出错时涉及的资源标识符，其类型决定异常映射分支
   * @param ex 原始 SQL 异常
   * @param defaultExceptionMessage 默认异常消息（用于回退情况）
   * @return 转换后的 RuntimeException 子类
   */
  private RuntimeException snowflakeExceptionToIcebergException(
      SnowflakeIdentifier identifier, SQLException ex, String defaultExceptionMessage) {
    // NoSuchNamespace exception for Database and Schema cases
    if ((identifier.type() == SnowflakeIdentifier.Type.DATABASE
            && DATABASE_NOT_FOUND_ERROR_CODES.contains(ex.getErrorCode()))
        || (identifier.type() == SnowflakeIdentifier.Type.SCHEMA
            && SCHEMA_NOT_FOUND_ERROR_CODES.contains(ex.getErrorCode()))) {
      return new NoSuchNamespaceException(
          ex,
          "Identifier not found: '%s'. Underlying exception: '%s'",
          identifier,
          ex.getMessage());
    }
    // NoSuchTable exception for Table cases
    else if (identifier.type() == SnowflakeIdentifier.Type.TABLE
        && TABLE_NOT_FOUND_ERROR_CODES.contains(ex.getErrorCode())) {
      return new NoSuchTableException(
          ex,
          "Identifier not found: '%s'. Underlying exception: '%s'",
          identifier,
          ex.getMessage());
    }
    // Unchecked SQL Exception in all other cases as fall back
    return new UncheckedSQLException(ex, "Exception Message: %s", defaultExceptionMessage);
  }
}
