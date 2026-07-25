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

/**
 * Delta Lake 到 Iceberg 迁移动作的提供者接口（服务定位器模式）。
 *
 * <p>所属模块：iceberg-delta-lake（Delta Lake 表迁移到 Iceberg 的支持模块）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>作为迁移动作的统一入口，向查询引擎提供"快照 Delta Lake 表为 Iceberg 表"的动作实例。
 *   <li>通过 {@link #defaultActions()} 暴露默认实现，也允许引擎自行实现本接口以替换默认行为。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>服务提供者接口（SPI）模式：将动作的"创建"与"使用"解耦。引擎依赖本接口而非具体实现， 便于扩展和替换迁移逻辑。
 *   <li>默认实现单例：{@link DefaultDeltaLakeToIcebergMigrationActions} 以单例形式持有， 避免重复创建，且构造函数私有化以防外部实例化。
 * </ul>
 *
 * <p>上下游关系：被各查询引擎（Spark/Flink 等）调用以发起 Delta Lake 到 Iceberg 的迁移； 内部创建 {@link SnapshotDeltaLakeTable}
 * 动作实例（默认为 {@link BaseSnapshotDeltaLakeTableAction}）。
 */
public interface DeltaLakeToIcebergMigrationActionsProvider {

  /**
   * 发起将现有 Delta Lake 表快照为 Iceberg 表的动作。
   *
   * @param sourceTableLocation Delta Lake 表的存储路径
   * @return 一个 {@link SnapshotDeltaLakeTable} 动作实例，调用方可继续链式配置后执行
   */
  default SnapshotDeltaLakeTable snapshotDeltaLakeTable(String sourceTableLocation) {
    return new BaseSnapshotDeltaLakeTableAction(sourceTableLocation);
  }

  /**
   * 获取 {@link DeltaLakeToIcebergMigrationActionsProvider} 的默认实现实例。
   *
   * @return 拥有全部默认动作实现的提供者实例
   */
  static DeltaLakeToIcebergMigrationActionsProvider defaultActions() {
    return DefaultDeltaLakeToIcebergMigrationActions.defaultMigrationActions();
  }

  /**
   * {@link DeltaLakeToIcebergMigrationActionsProvider} 的默认实现（单例）。
   *
   * <p>设计意图：私有构造 + 静态单例，确保全局唯一实例，避免重复创建开销， 同时阻止外部直接实例化。
   */
  class DefaultDeltaLakeToIcebergMigrationActions
      implements DeltaLakeToIcebergMigrationActionsProvider {
    private static final DefaultDeltaLakeToIcebergMigrationActions defaultMigrationActions =
        new DefaultDeltaLakeToIcebergMigrationActions();

    private DefaultDeltaLakeToIcebergMigrationActions() {}

    /**
     * 获取默认实现的单例实例。
     *
     * @return 全局唯一的默认迁移动作提供者
     */
    static DefaultDeltaLakeToIcebergMigrationActions defaultMigrationActions() {
      return defaultMigrationActions;
    }
  }
}
