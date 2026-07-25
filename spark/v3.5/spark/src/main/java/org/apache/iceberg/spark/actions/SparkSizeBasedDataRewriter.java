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
package org.apache.iceberg.spark.actions;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.apache.iceberg.DataFile;
import org.apache.iceberg.FileScanTask;
import org.apache.iceberg.Table;
import org.apache.iceberg.actions.SizeBasedDataRewriter;
import org.apache.iceberg.spark.FileRewriteCoordinator;
import org.apache.iceberg.spark.ScanTaskSetManager;
import org.apache.iceberg.spark.SparkTableCache;
import org.apache.spark.sql.SparkSession;

/**
 * 基于文件大小的数据重写器（Spark 实现，抽象）。
 *
 * <p>所属模块：iceberg-spark（actions 子包）。继承 Iceberg core 的 {@link SizeBasedDataRewriter}， 在按大小分组的基础上，借助
 * Spark 的任务集合管理与文件重写协调器完成数据文件重写。
 *
 * <p>职责：为每个待重写分组生成唯一 groupId，暂存扫描任务到 {@link ScanTaskSetManager}、 缓存表到 {@link SparkTableCache}，调用子类
 * {@link #doRewrite} 执行实际重写，最后从 {@link FileRewriteCoordinator} 收集新写出的数据文件。
 *
 * <p>设计意图：将"分组策略"（来自父类）与"Spark 执行协调"分离；groupId 作为本次重写的 会话标识贯穿缓存、任务暂存与结果收集，确保并发安全。finally
 * 中统一清理资源避免泄漏。
 *
 * <p>上下游关系：被 {@link SparkBinSizeBasedRewriter} 等子类继承；由 RewriteDataFiles 动作调用。
 */
abstract class SparkSizeBasedDataRewriter extends SizeBasedDataRewriter {

  private final SparkSession spark;
  private final SparkTableCache tableCache = SparkTableCache.get();
  private final ScanTaskSetManager taskSetManager = ScanTaskSetManager.get();
  private final FileRewriteCoordinator coordinator = FileRewriteCoordinator.get();

  SparkSizeBasedDataRewriter(SparkSession spark, Table table) {
    super(table);
    this.spark = spark;
  }

  /** 子类实现：对指定分组执行实际重写（提交 Spark 作业）。 */
  protected abstract void doRewrite(String groupId, List<FileScanTask> group);

  /** 返回当前 SparkSession。 */
  protected SparkSession spark() {
    return spark;
  }

  /**
   * 重写一个文件分组并返回新数据文件集合。
   *
   * <p>逻辑：生成随机 groupId；将表加入缓存并暂存该分组任务；调用 {@link #doRewrite} 执行重写； 从协调器取出新文件；finally
   * 中移除缓存、清理暂存任务与协调状态。
   *
   * @param group 待重写的文件扫描任务列表
   * @return 新写出的数据文件集合
   */
  @Override
  public Set<DataFile> rewrite(List<FileScanTask> group) {
    String groupId = UUID.randomUUID().toString();
    try {
      tableCache.add(groupId, table());
      taskSetManager.stageTasks(table(), groupId, group);

      doRewrite(groupId, group);

      return coordinator.fetchNewFiles(table(), groupId);
    } finally {
      tableCache.remove(groupId);
      taskSetManager.removeTasks(table(), groupId);
      coordinator.clearRewrite(table(), groupId);
    }
  }
}
