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
 * 为已有表创建独立 Iceberg 快照表的动作。
 *
 * <p>所属模块：iceberg-api。继承自 {@link Action}，用于在不影响原表的前提下，将其数据文件 以 Iceberg 表的形式快照出来。
 *
 * <p>职责：基于源表数据文件创建新的 Iceberg 表，可指定目标表标识符、存储位置与表属性。
 *
 * <p>设计意图：与 {@link MigrateTable}（就地迁移、替换原表）不同，本动作保留原表不动，仅创建 一个引用相同数据文件的独立 Iceberg
 * 表，适合在不破坏既有管线的情况下试用 Iceberg。
 *
 * <p>上下游关系：由引擎模块实现；结果通过 {@link Result} 返回导入统计信息。
 */
public interface SnapshotTable extends Action<SnapshotTable, SnapshotTable.Result> {
  /**
   * 设置新创建 Iceberg 表的表标识符。
   *
   * @param destTableIdent 目标表标识符
   * @return this，便于链式调用
   */
  SnapshotTable as(String destTableIdent);

  /**
   * 设置新创建 Iceberg 表的存储位置。
   *
   * @param location 表存储位置
   * @return this，便于链式调用
   */
  SnapshotTable tableLocation(String location);

  /**
   * 为新创建的 Iceberg 表设置一组表属性。同名属性将被覆盖。
   *
   * @param properties 属性键值对集合
   * @return this，便于链式调用
   */
  SnapshotTable tableProperties(Map<String, String> properties);

  /**
   * 为新创建的 Iceberg 表设置单个表属性。同名属性将被覆盖。
   *
   * @param key 属性名
   * @param value 属性值
   * @return this，便于链式调用
   */
  SnapshotTable tableProperty(String key, String value);

  /** 动作执行结果，包含执行摘要统计。 */
  interface Result {
    /**
     * 返回导入的数据文件数量。
     *
     * @return 导入数据文件数
     */
    long importedDataFilesCount();
  }
}
