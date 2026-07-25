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

import org.apache.iceberg.BaseMetastoreTableOperations;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.exceptions.NoSuchTableException;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.relocated.com.google.common.annotations.VisibleForTesting;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 面向 Snowflake 管理的 Iceberg 表的 {@link BaseMetastoreTableOperations} 实现。
 *
 * <p>所属模块：iceberg-snowflake（Iceberg 与 Snowflake 目录集成模块；本类处于表操作层， 位于 catalog 与底层元数据文件之间，负责从
 * Snowflake 获取表元数据位置并刷新 Iceberg 表状态）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>通过 {@link SnowflakeClient} 加载 Snowflake 侧的表元数据，获取 Iceberg 元数据文件位置。
 *   <li>在 {@link #doRefresh()} 中将元数据位置交给父类完成 Iceberg 表元数据刷新。
 *   <li>持有该表对应的 {@link FileIO}，供父类读写底层元数据/数据文件。
 * </ul>
 *
 * <p>设计意图：本类为只读实现，不覆盖 {@code doCommit()} 等写方法，因为 Snowflake 管理的 Iceberg 表的元数据生命周期由 Snowflake
 * 控制，Iceberg 侧仅消费。表标识符在构造时即通过 {@link NamespaceHelpers} 转换并缓存，避免每次操作重复转换。
 *
 * <p>上下游关系：由 {@link SnowflakeCatalog#newTableOps} 创建；依赖 {@link SnowflakeClient} 获取元数据、{@link
 * FileIO} 读取底层文件；继承 {@link BaseMetastoreTableOperations} 复用 Iceberg 通用的元数据刷新逻辑。
 */
class SnowflakeTableOperations extends BaseMetastoreTableOperations {

  private static final Logger LOG = LoggerFactory.getLogger(SnowflakeTableOperations.class);

  private final FileIO fileIO;
  private final TableIdentifier tableIdentifier;
  private final SnowflakeIdentifier snowflakeIdentifierForTable;
  private final String fullTableName;

  private final SnowflakeClient snowflakeClient;

  /**
   * 构造表操作实例。
   *
   * <p>逻辑：保存 client、fileIO、catalogName 与表标识符，并通过 {@link NamespaceHelpers} 把 Iceberg {@link
   * TableIdentifier} 转为 {@link SnowflakeIdentifier} 缓存，供后续元数据加载使用。 同时拼装 {@code
   * fullTableName}（catalogName.table）用于日志与 {@link #tableName()}。
   *
   * @param snowflakeClient 与 Snowflake 通信的客户端
   * @param fileIO 读写底层文件的 FileIO
   * @param catalogName catalog 名称
   * @param tableIdentifier Iceberg 表标识符
   */
  protected SnowflakeTableOperations(
      SnowflakeClient snowflakeClient,
      FileIO fileIO,
      String catalogName,
      TableIdentifier tableIdentifier) {
    this.snowflakeClient = snowflakeClient;
    this.fileIO = fileIO;
    this.tableIdentifier = tableIdentifier;
    this.snowflakeIdentifierForTable = NamespaceHelpers.toSnowflakeIdentifier(tableIdentifier);
    this.fullTableName = String.format("%s.%s", catalogName, tableIdentifier);
  }

  /**
   * 刷新表元数据：从 Snowflake 获取最新元数据位置并交给父类加载。
   *
   * <p>逻辑：调用 {@link #loadTableMetadataLocation()} 获取 Iceberg 元数据文件位置， 校验非空后调用父类 {@code
   * refreshFromMetadataLocation} 完成元数据 JSON 的读取与版本推进。
   *
   * @throws IllegalStateException 获取到的元数据位置为 null 或空
   */
  @Override
  public void doRefresh() {
    LOG.debug("Getting metadata location for table {}", tableIdentifier);
    String location = loadTableMetadataLocation();
    Preconditions.checkState(
        location != null && !location.isEmpty(),
        "Got null or empty location %s for table %s",
        location,
        tableIdentifier);
    refreshFromMetadataLocation(location);
  }

  /** 返回本表操作使用的 {@link FileIO}。 */
  @Override
  public FileIO io() {
    return fileIO;
  }

  /** 返回全限定表名（catalog.table），用于日志与 {@link BaseMetastoreTableOperations}。 */
  @Override
  protected String tableName() {
    return fullTableName;
  }

  /** 返回全限定表名，供测试使用。 */
  @VisibleForTesting
  String fullTableName() {
    return tableName();
  }

  /**
   * 从 Snowflake 加载表元数据并提取 Iceberg 元数据位置。
   *
   * <p>逻辑：调用 {@link SnowflakeClient#loadTableMetadata} 获取 {@link SnowflakeTableMetadata}， 若返回 null
   * 则抛出 {@link NoSuchTableException}；若状态非 "success" 则记录告警日志； 最终返回转换后的 Iceberg 元数据位置。
   *
   * @return Iceberg 元数据文件位置
   * @throws NoSuchTableException Snowflake 中未找到该表
   */
  private String loadTableMetadataLocation() {
    SnowflakeTableMetadata metadata =
        snowflakeClient.loadTableMetadata(snowflakeIdentifierForTable);

    if (metadata == null) {
      throw new NoSuchTableException("Cannot find table %s", snowflakeIdentifierForTable);
    }

    if (!metadata.getStatus().equals("success")) {
      LOG.warn(
          "Got non-successful table metadata: {} with metadataLocation {} for table {}",
          metadata.getStatus(),
          metadata.icebergMetadataLocation(),
          snowflakeIdentifierForTable);
    }

    return metadata.icebergMetadataLocation();
  }
}
