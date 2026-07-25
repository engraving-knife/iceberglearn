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
package org.apache.iceberg.delta;

import java.util.Map;
import org.apache.hadoop.conf.Configuration;
import org.apache.iceberg.actions.Action;
import org.apache.iceberg.catalog.Catalog;
import org.apache.iceberg.catalog.TableIdentifier;
import org.immutables.value.Value;

/**
 * 将现有 Delta Lake 表快照为 Iceberg 表的动作接口。
 *
 * <p>所属模块：iceberg-delta-lake（Delta Lake 表迁移到 Iceberg 的支持模块）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>定义"把 Delta Lake 表快照为 Iceberg 表"这一迁移动作的公共契约。
 *   <li>以 Builder 风格链式配置目标表属性、位置、标识符、Iceberg Catalog 及 Hadoop 配置。
 *   <li>通过 {@link Result} 返回迁移执行摘要（如已迁移数据文件数）。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>面向接口编程：将动作的"契约"与"实现"分离。默认实现见 {@link BaseSnapshotDeltaLakeTableAction}，引擎可通过 {@link
 *       DeltaLakeToIcebergMigrationActionsProvider} 获取或替换实现。
 *   <li>Immutables：{@link Result} 使用 {@code @Value.Immutable} 生成不可变值对象， 保证结果不可变且线程安全。
 *   <li>继承 {@link Action}：复用 Iceberg actions 框架的通用契约（配置 + 执行 + 结果）。
 * </ul>
 *
 * <p>上下游关系：由 {@link DeltaLakeToIcebergMigrationActionsProvider#snapshotDeltaLakeTable} 创建实例；执行时调用
 * Delta Lake Standalone API 读取 Delta 日志，并通过 Iceberg Catalog 创建新表、提交事务。
 */
@Value.Enclosing
public interface SnapshotDeltaLakeTable
    extends Action<SnapshotDeltaLakeTable, SnapshotDeltaLakeTable.Result> {

  /**
   * 设置新建 Iceberg 表的表属性（批量）。同名属性将被覆盖。
   *
   * @param properties 属性键值对
   * @return 当前动作实例，用于链式调用
   */
  SnapshotDeltaLakeTable tableProperties(Map<String, String> properties);

  /**
   * 设置新建 Iceberg 表的单个表属性。同名属性将被覆盖。
   *
   * @param name 属性名
   * @param value 属性值
   * @return 当前动作实例，用于链式调用
   */
  SnapshotDeltaLakeTable tableProperty(String name, String value);

  /**
   * 设置新建 Iceberg 表的存储位置。默认与 Delta Lake 表位置相同。
   *
   * @param location 新表存储路径
   * @return 当前动作实例，用于链式调用
   */
  SnapshotDeltaLakeTable tableLocation(String location);

  /**
   * 设置新建 Iceberg 表的标识符。执行动作前必须设置。
   *
   * @param identifier 表标识符（命名空间 + 表名）
   * @return 当前动作实例，用于链式调用
   */
  SnapshotDeltaLakeTable as(TableIdentifier identifier);

  /**
   * 设置新建 Iceberg 表所使用的 Iceberg Catalog。执行动作前必须设置。
   *
   * @param catalog Iceberg Catalog 实例
   * @return 当前动作实例，用于链式调用
   */
  SnapshotDeltaLakeTable icebergCatalog(Catalog catalog);

  /**
   * 设置访问 Delta Lake 表日志和数据文件所用的 Hadoop 配置。执行动作前必须设置。
   *
   * @param conf Hadoop 配置
   * @return 当前动作实例，用于链式调用
   */
  SnapshotDeltaLakeTable deltaLakeConfiguration(Configuration conf);

  /**
   * 动作执行结果，包含执行摘要信息。
   *
   * <p>设计意图：使用 {@code @Value.Immutable} 由 Immutables 框架生成不可变实现， 保证结果对象线程安全且不可篡改。
   */
  @Value.Immutable
  interface Result {

    /**
     * 获取已迁移的数据文件数量。
     *
     * @return 迁移的数据文件总数
     */
    long snapshotDataFilesCount();
  }
}
