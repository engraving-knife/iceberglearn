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
package org.apache.iceberg.actions;

import java.util.Map;

/**
 * 将已有表迁移为 Iceberg 表的动作。
 *
 * <p>所属模块：iceberg-api。继承自 {@link Action}，用于将非 Iceberg 表就地转换为 Iceberg 表。
 *
 * <p>职责：把现有表的数据文件纳入 Iceberg 元数据管理，使其成为 Iceberg 表，同时可设置新表属性、 备份原表等。
 *
 * <p>设计意图：与 {@link SnapshotTable}（保留原表、另建独立 Iceberg 快照表）不同，本动作是"就地 迁移"——原表被替换为 Iceberg
 * 表。为降低风险，提供备份原表的能力。dropBackup 与 backupTableName 默认抛 {@link
 * UnsupportedOperationException}，表示这些是可选能力，由具体引擎按需实现。
 *
 * <p>上下游关系：由引擎模块实现；结果通过 {@link Result} 返回迁移统计信息。
 */
public interface MigrateTable extends Action<MigrateTable, MigrateTable.Result> {
  /**
   * 为新建的 Iceberg 表设置一组表属性。同名属性将被覆盖。
   *
   * @param properties 属性键值对集合
   * @return this，便于链式调用
   */
  MigrateTable tableProperties(Map<String, String> properties);

  /**
   * 为新建的 Iceberg 表设置单个表属性。同名属性将被覆盖。
   *
   * @param name 属性名
   * @param value 属性值
   * @return this，便于链式调用
   */
  MigrateTable tableProperty(String name, String value);

  /**
   * 在迁移成功后删除原表的备份。
   *
   * <p>默认抛出 {@link UnsupportedOperationException}，表示不支持删除备份。
   *
   * @return this，便于链式调用
   */
  default MigrateTable dropBackup() {
    throw new UnsupportedOperationException("Dropping a backup is not supported");
  }

  /**
   * 为原表备份指定表名。
   *
   * <p>默认抛出 {@link UnsupportedOperationException}，表示不支持指定备份表名。
   *
   * @param tableName 备份表名
   * @return this，便于链式调用
   */
  default MigrateTable backupTableName(String tableName) {
    throw new UnsupportedOperationException("Backup table name cannot be specified");
  }

  /** 动作执行结果，包含执行摘要统计。 */
  interface Result {
    /**
     * 返回已迁移的数据文件数量。
     *
     * @return 已迁移数据文件数
     */
    long migratedDataFilesCount();
  }
}
