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
package org.apache.iceberg.jdbc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.util.Map;
import java.util.Properties;
import org.apache.iceberg.CatalogProperties;
import org.apache.iceberg.ClientPoolImpl;

/**
 * 文件级说明：JDBC 连接池，继承 ClientPoolImpl 管理 Connection 的借出与归还。
 *
 * <p>所属模块：iceberg-core（jdbc 子包）。职责：实现 JDBC Connection 的池化管理， 包装 SQL 受检异常为 RuntimeException，提供 run
 * 方法执行数据库操作。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>继承 {@link org.apache.iceberg.ClientPoolImpl}，复用通用的连接池逻辑 （借出/归还/异常重试）。
 *   <li>把 SQLException 包装为 UncheckedSQLException，简化调用方异常处理。
 * </ul>
 *
 * <p>上下游关系：被 {@link JdbcCatalog} 持有和使用；产出 java.sql.Connection。
 */
public class JdbcClientPool extends ClientPoolImpl<Connection, SQLException> {

  private final String dbUrl;
  private final Map<String, String> properties;

  public JdbcClientPool(String dbUrl, Map<String, String> props) {
    this(
        Integer.parseInt(
            props.getOrDefault(
                CatalogProperties.CLIENT_POOL_SIZE,
                String.valueOf(CatalogProperties.CLIENT_POOL_SIZE_DEFAULT))),
        dbUrl,
        props);
  }

  public JdbcClientPool(int poolSize, String dbUrl, Map<String, String> props) {
    super(poolSize, SQLNonTransientConnectionException.class, true);
    properties = props;
    this.dbUrl = dbUrl;
  }

  @Override
  /**
   * 创建新的 JDBC 连接。
   *
   * <p>设计要点：通过 DriverManager 创建连接，可能加载驱动类。
   *
   * @return 新的数据库连接
   * @throws SQLException 连接失败时抛出
   */
  protected Connection newClient() {
    try {
      Properties dbProps = JdbcUtil.filterAndRemovePrefix(properties, JdbcCatalog.PROPERTY_PREFIX);
      return DriverManager.getConnection(dbUrl, dbProps);
    } catch (SQLException e) {
      throw new UncheckedSQLException(e, "Failed to connect: %s", dbUrl);
    }
  }

  @Override
  protected Connection reconnect(Connection client) {
    close(client);
    return newClient();
  }

  @Override
  /**
   * 关闭数据库连接。
   *
   * @param client 待关闭的连接
   */
  protected void close(Connection client) {
    try {
      client.close();
    } catch (SQLException e) {
      throw new UncheckedSQLException(e, "Failed to close connection");
    }
  }
}
