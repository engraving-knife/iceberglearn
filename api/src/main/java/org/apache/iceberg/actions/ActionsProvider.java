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

import org.apache.iceberg.Table;

/**
 * 查询引擎集成方需实现的"动作提供者"接口。
 *
 * <p>所属模块：iceberg-api。本接口是引擎集成（如 Spark/Flink）向使用方暴露表维护能力的统一入口， 起到工厂集合的作用。
 *
 * <p>职责：集中提供各类表维护动作（{@link SnapshotTable}、{@link MigrateTable}、 {@link DeleteOrphanFiles}、{@link
 * RewriteManifests}、{@link RewriteDataFiles}、 {@link ExpireSnapshots}、{@link
 * DeleteReachableFiles}、{@link RewritePositionDeleteFiles}） 的实例化入口。
 *
 * <p>设计意图：所有方法均为 default 实现，默认抛出 {@link UnsupportedOperationException}。引擎
 * 只需覆写其支持的动作方法，未支持的方法保持安全报错，避免强行要求实现全部动作。这种"可选能力" 模式让不同引擎按需接入、渐进式实现。
 *
 * <p>上下游关系：由引擎模块实现并被上层应用调用；各工厂方法返回的 Action 实例再由调用方链式配置 后执行。
 */
public interface ActionsProvider {

  /**
   * 创建一个"将已有表快照为新 Iceberg 表"的动作实例。
   *
   * @param sourceTableIdent 源表标识符
   * @return {@link SnapshotTable} 动作实例
   */
  default SnapshotTable snapshotTable(String sourceTableIdent) {
    throw new UnsupportedOperationException(
        this.getClass().getName() + " does not implement snapshotTable");
  }

  /**
   * 创建一个"将已有表迁移为 Iceberg 表"的动作实例。
   *
   * @param tableIdent 待迁移表的标识符
   * @return {@link MigrateTable} 动作实例
   */
  default MigrateTable migrateTable(String tableIdent) {
    throw new UnsupportedOperationException(
        this.getClass().getName() + " does not implement migrateTable");
  }

  /**
   * 创建一个"删除孤儿文件"的动作实例。
   *
   * @param table 目标表
   * @return {@link DeleteOrphanFiles} 动作实例
   */
  default DeleteOrphanFiles deleteOrphanFiles(Table table) {
    throw new UnsupportedOperationException(
        this.getClass().getName() + " does not implement deleteOrphanFiles");
  }

  /**
   * 创建一个"重写清单（manifests）"的动作实例。
   *
   * @param table 目标表
   * @return {@link RewriteManifests} 动作实例
   */
  default RewriteManifests rewriteManifests(Table table) {
    throw new UnsupportedOperationException(
        this.getClass().getName() + " does not implement rewriteManifests");
  }

  /**
   * 创建一个"重写数据文件"的动作实例。
   *
   * @param table 目标表
   * @return {@link RewriteDataFiles} 动作实例
   */
  default RewriteDataFiles rewriteDataFiles(Table table) {
    throw new UnsupportedOperationException(
        this.getClass().getName() + " does not implement rewriteDataFiles");
  }

  /**
   * 创建一个"过期快照"的动作实例。
   *
   * @param table 目标表
   * @return {@link ExpireSnapshots} 动作实例
   */
  default ExpireSnapshots expireSnapshots(Table table) {
    throw new UnsupportedOperationException(
        this.getClass().getName() + " does not implement expireSnapshots");
  }

  /**
   * 创建一个"删除给定元数据位置可达的全部文件"的动作实例。
   *
   * @param metadataLocation 表元数据文件位置
   * @return {@link DeleteReachableFiles} 动作实例
   */
  default DeleteReachableFiles deleteReachableFiles(String metadataLocation) {
    throw new UnsupportedOperationException(
        this.getClass().getName() + " does not implement deleteReachableFiles");
  }

  /**
   * 创建一个"重写位置删除文件（position delete files）"的动作实例。
   *
   * @param table 目标表
   * @return {@link RewritePositionDeleteFiles} 动作实例
   */
  default RewritePositionDeleteFiles rewritePositionDeletes(Table table) {
    throw new UnsupportedOperationException(
        this.getClass().getName() + " does not implement rewritePositionDeletes");
  }
}
