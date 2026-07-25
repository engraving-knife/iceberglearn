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
package org.apache.iceberg.spark.source;

import java.util.List;
import java.util.Objects;
import org.apache.iceberg.ScanTask;
import org.apache.iceberg.ScanTaskGroup;
import org.apache.iceberg.Table;
import org.apache.iceberg.exceptions.ValidationException;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.spark.ScanTaskSetManager;
import org.apache.iceberg.spark.SparkReadConf;
import org.apache.iceberg.util.TableScanUtil;
import org.apache.spark.sql.SparkSession;

/**
 * 暂存扫描（staged scan）：从 {@link ScanTaskSetManager} 读取预先规划好的扫描任务集。
 *
 * <p>所属模块：iceberg-spark（source 子包，服务于文件重写等需要外部驱动任务集的场景）。
 *
 * <p>职责：按 taskSetId 从 {@link ScanTaskSetManager} 取出暂存的扫描任务， 再按 split 大小等参数规划为任务组，供 Spark 读取执行。
 *
 * <p>设计意图：常规扫描由 Iceberg 自行规划任务，而重写场景需要先在驱动端规划特定任务集， 再以 staged scan 形式让数据源读取这些任务。任务组采用懒加载与缓存，避免重复规划。
 *
 * <p>上下游关系：继承 {@link SparkScan}，由 {@link SparkWriteBuilder} 等在重写读取时构造， 任务来源于 {@link
 * ScanTaskSetManager}。
 */
class SparkStagedScan extends SparkScan {

  private final String taskSetId;
  private final long splitSize;
  private final int splitLookback;
  private final long openFileCost;

  private List<ScanTaskGroup<ScanTask>> taskGroups = null; // lazy cache of tasks

  /**
   * 构造暂存扫描。
   *
   * @param spark Spark 会话
   * @param table 目标表
   * @param readConf Spark 读取配置，提供 taskSetId 与 split 参数
   */
  SparkStagedScan(SparkSession spark, Table table, SparkReadConf readConf) {
    super(spark, table, readConf, table.schema(), ImmutableList.of(), null);

    this.taskSetId = readConf.scanTaskSetId();
    this.splitSize = readConf.splitSize();
    this.splitLookback = readConf.splitLookback();
    this.openFileCost = readConf.splitOpenFileCost();
  }

  /**
   * 返回扫描任务组，懒加载并缓存。
   *
   * <p>逻辑：首次调用时从 {@link ScanTaskSetManager} 按 taskSetId 取出任务， 校验非空后用 {@link
   * TableScanUtil#planTaskGroups} 规划任务组并缓存。
   *
   * @throws ValidationException 当任务集管理器中没有对应任务时抛出
   */
  @Override
  protected List<ScanTaskGroup<ScanTask>> taskGroups() {
    if (taskGroups == null) {
      ScanTaskSetManager taskSetManager = ScanTaskSetManager.get();
      List<ScanTask> tasks = taskSetManager.fetchTasks(table(), taskSetId);
      ValidationException.check(
          tasks != null,
          "Task set manager has no tasks for table %s with task set ID %s",
          table(),
          taskSetId);

      this.taskGroups = TableScanUtil.planTaskGroups(tasks, splitSize, splitLookback, openFileCost);
    }
    return taskGroups;
  }
  /** 判断是否相等。 */
  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }

    if (other == null || getClass() != other.getClass()) {
      return false;
    }

    SparkStagedScan that = (SparkStagedScan) other;
    return table().name().equals(that.table().name())
        && Objects.equals(taskSetId, that.taskSetId)
        && Objects.equals(splitSize, that.splitSize)
        && Objects.equals(splitLookback, that.splitLookback)
        && Objects.equals(openFileCost, that.openFileCost);
  }
  /** 返回哈希码。 */
  @Override
  public int hashCode() {
    return Objects.hash(table().name(), taskSetId, splitSize, splitSize, openFileCost);
  }
  /** 返回字符串表示。 */
  @Override
  public String toString() {
    return String.format(
        "IcebergStagedScan(table=%s, type=%s, taskSetID=%s, caseSensitive=%s)",
        table(), expectedSchema().asStruct(), taskSetId, caseSensitive());
  }
}
