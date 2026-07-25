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

import java.io.Closeable;
import java.util.List;

/**
 * Snowflake 资源访问客户端抽象接口，屏蔽底层与 Snowflake 通信的具体协议细节。
 *
 * <p>所属模块：iceberg-snowflake（Iceberg 与 Snowflake 的目录集成模块；本接口是该模块对上层 {@link SnowflakeCatalog}
 * 暴露的统一数据访问出口，处于 catalog 与具体协议实现之间）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>定义对 Snowflake 三级资源（Database / Schema / Table）的存在性检查与列举能力。
 *   <li>定义加载 Snowflake Iceberg 表元数据的能力，供 {@link SnowflakeTableOperations} 使用。
 *   <li>继承 {@link Closeable}，要求实现类释放底层连接资源。
 * </ul>
 *
 * <p>设计意图：抽象出此接口使得上层 Catalog 不必关心底层是通过 JDBC、REST 还是其他协议与 Snowflake 通信，便于替换实现或在测试中注入 mock。当前唯一实现为
 * {@link JdbcSnowflakeClient}。
 *
 * <p>上下游关系：被 {@link SnowflakeCatalog} 与 {@link SnowflakeTableOperations} 调用； 实现类依赖 Snowflake 的 JDBC
 * 驱动或其它通信库。
 */
interface SnowflakeClient extends Closeable {

  /** 判断指定的 Snowflake 数据库是否存在，存在返回 true，否则返回 false。 */
  boolean databaseExists(SnowflakeIdentifier database);

  /** 判断指定的 Schema 及其父数据库是否存在，两者都存在时返回 true，否则返回 false。 */
  boolean schemaExists(SnowflakeIdentifier schema);

  /** 列举当前账户下的所有 Snowflake 数据库。 */
  List<SnowflakeIdentifier> listDatabases();

  /**
   * 列举指定范围内的所有 Snowflake Schema。
   *
   * <p>返回的 {@link SnowflakeIdentifier} 其 {@code type()} 必须为 {@link
   * SnowflakeIdentifier.Type#SCHEMA}。
   *
   * @param scope 列举范围，可为 ROOT（账户级）或单个 DATABASE
   */
  List<SnowflakeIdentifier> listSchemas(SnowflakeIdentifier scope);

  /**
   * 列举指定范围内的所有 Snowflake Iceberg 表。
   *
   * <p>返回的 {@link SnowflakeIdentifier} 其 {@code type()} 必须为 {@link SnowflakeIdentifier.Type#TABLE}。
   *
   * @param scope 列举范围，可为 ROOT、单个 DATABASE 或单个 SCHEMA
   */
  List<SnowflakeIdentifier> listIcebergTables(SnowflakeIdentifier scope);

  /**
   * 加载指定表的 Snowflake 级元数据，其中包含指向更详细 Iceberg 元数据的位置信息。
   *
   * @param tableIdentifier 完全限定的表标识符，其类型必须为 {@link SnowflakeIdentifier.Type#TABLE}
   */
  SnowflakeTableMetadata loadTableMetadata(SnowflakeIdentifier tableIdentifier);
}
