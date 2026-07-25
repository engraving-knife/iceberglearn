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
package org.apache.iceberg;

import org.apache.iceberg.exceptions.CommitFailedException;
import org.apache.iceberg.exceptions.ValidationException;

/**
 * 事务接口：把多个表更新操作打包为一次原子提交。
 *
 * <p>所属模块：iceberg-api（顶层公共 API 模块）。
 *
 * <p>职责：提供与 {@link Table} 几乎一致的更新入口（追加、覆写、删除、schema 演进等）， 但所有操作在 {@link #commitTransaction()}
 * 时一次性提交，保证原子性。
 *
 * <p>设计意图：事务内部维护一个"暂存"表视图，各更新操作基于该视图累积变更，互不干扰； 提交时把全部变更应用到最新表元数据上，若冲突则抛出 {@link
 * CommitFailedException}。
 *
 * <p>上下游关系：由 {@link Table#newTransaction()} 创建；下游实现位于 core 模块。
 */
public interface Transaction {
  /**
   * 返回本事务将更新的 {@link Table}（事务暂存视图）。
   *
   * @return 事务关联的表
   */
  Table table();

  /**
   * 创建 {@link UpdateSchema} 以修改本表列（事务内）。
   *
   * @return 新的 schema 更新器
   */
  UpdateSchema updateSchema();

  /**
   * 创建 {@link UpdatePartitionSpec} 以修改分区规范（事务内）。
   *
   * @return 新的分区规范更新器
   */
  UpdatePartitionSpec updateSpec();

  /**
   * 创建 {@link UpdateProperties} 以更新表属性（事务内）。
   *
   * @return 新的属性更新器
   */
  UpdateProperties updateProperties();

  /**
   * 创建 {@link ReplaceSortOrder} 以设置排序顺序（事务内）。
   *
   * @return 新的排序顺序替换器
   */
  ReplaceSortOrder replaceSortOrder();

  /**
   * 创建 {@link UpdateLocation} 以更新表存储位置（事务内）。
   *
   * @return 新的位置更新器
   */
  UpdateLocation updateLocation();

  /**
   * 创建 {@link AppendFiles} 追加 API（事务内）。
   *
   * @return 新的追加 API
   */
  AppendFiles newAppend();

  /**
   * 创建快速追加 {@link AppendFiles} API（事务内）。
   *
   * <p>逻辑：通知底层实现跳过额外工作以尽快提交。不推荐用于常规写入，因为快速提交可能 导致后续分片规划变慢。若实现不支持快速追加，则退化为 {@link #newAppend()}。
   *
   * @return 新的追加 API
   */
  default AppendFiles newFastAppend() {
    return newAppend();
  }

  /**
   * 创建 {@link RewriteFiles} 重写 API（事务内）。
   *
   * @return 新的文件重写 API
   */
  RewriteFiles newRewrite();

  /**
   * 创建 {@link RewriteManifests} 清单重写 API（事务内）。
   *
   * @return 新的清单重写 API
   */
  RewriteManifests rewriteManifests();

  /**
   * 创建 {@link OverwriteFiles} 覆写 API（事务内）。
   *
   * @return 新的覆写 API
   */
  OverwriteFiles newOverwrite();

  /**
   * 创建 {@link RowDelta} 行级增量 API（事务内）。
   *
   * @return 新的行级增量 API
   */
  RowDelta newRowDelta();

  /**
   * 不推荐：创建 {@link ReplacePartitions} 分区替换 API（事务内）。
   *
   * <p>主要为兼容 Hive 风格 SQL 提供，推荐优先使用 {@link OverwriteFiles}。
   *
   * @return 新的分区替换 API
   */
  ReplacePartitions newReplacePartitions();

  /**
   * 创建 {@link DeleteFiles} 删除 API（事务内）。
   *
   * @return 新的删除 API
   */
  DeleteFiles newDelete();

  /**
   * 创建 {@link UpdateStatistics} 统计文件更新 API（事务内）。
   *
   * <p>默认实现抛出 {@link UnsupportedOperationException}，由具体实现类提供支持。
   *
   * @return 新的统计更新 API
   */
  default UpdateStatistics updateStatistics() {
    throw new UnsupportedOperationException(
        "Updating statistics is not supported by " + getClass().getName());
  }

  /**
   * 创建 {@link ExpireSnapshots} 过期 API（事务内）。
   *
   * @return 新的快照过期 API
   */
  ExpireSnapshots expireSnapshots();

  /**
   * 创建 {@link ManageSnapshots} 快照管理 API（事务内）。
   *
   * <p>默认实现抛出 {@link UnsupportedOperationException}，由具体实现类提供支持。
   *
   * @return 新的快照管理 API
   */
  default ManageSnapshots manageSnapshots() {
    throw new UnsupportedOperationException(
        "Managing snapshots is not supported by " + getClass().getName());
  }

  /**
   * 应用所有操作的暂存变更并提交。
   *
   * @throws ValidationException 某个更新无法应用到当前表元数据
   * @throws CommitFailedException 因冲突导致无法提交
   */
  void commitTransaction();
}
