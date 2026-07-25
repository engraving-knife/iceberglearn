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
import org.apache.iceberg.CombinedScanTask;
import org.apache.iceberg.FileScanTask;
import org.apache.iceberg.Table;
import org.apache.iceberg.exceptions.ValidationException;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.spark.FileScanTaskSetManager;
import org.apache.iceberg.spark.SparkReadConf;
import org.apache.iceberg.util.TableScanUtil;
import org.apache.spark.sql.SparkSession;

/**
 * Iceberg 表在 Spark DataSource V2 中的实现的扫描组件，负责构建和执行数据读取计划。
 *
 * <p>所属模块：iceberg-spark v3.2。 类型：类 SparkFilesScan。
 *
 * <p>上下游：被 SparkCatalog 创建，依赖 Iceberg Table API 与底层扫描/写入组件。
 */
class SparkFilesScan extends SparkScan {
  private final String taskSetID;
  private final long splitSize;
  private final int splitLookback;
  private final long splitOpenFileCost;

  private List<CombinedScanTask> tasks = null; // lazy cache of tasks

  SparkFilesScan(SparkSession spark, Table table, SparkReadConf readConf) {
    super(spark, table, readConf, table.schema(), ImmutableList.of());

    this.taskSetID = readConf.fileScanTaskSetId();
    this.splitSize = readConf.splitSize();
    this.splitLookback = readConf.splitLookback();
    this.splitOpenFileCost = readConf.splitOpenFileCost();
  }

  /** 执行该方法的具体逻辑。 */
  @Override
  protected List<CombinedScanTask> tasks() {
    if (tasks == null) {
      FileScanTaskSetManager taskSetManager = FileScanTaskSetManager.get();
      List<FileScanTask> files = taskSetManager.fetchTasks(table(), taskSetID);
      ValidationException.check(
          files != null,
          "Task set manager has no tasks for table %s with id %s",
          table(),
          taskSetID);

      CloseableIterable<FileScanTask> splitFiles =
          TableScanUtil.splitFiles(CloseableIterable.withNoopClose(files), splitSize);
      CloseableIterable<CombinedScanTask> scanTasks =
          TableScanUtil.planTasks(
              splitFiles, splitSize,
              splitLookback, splitOpenFileCost);
      this.tasks = Lists.newArrayList(scanTasks);
    }

    return tasks;
  }

  /** 判断是否与给定对象相等。 */
  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }

    if (other == null || getClass() != other.getClass()) {
      return false;
    }

    SparkFilesScan that = (SparkFilesScan) other;
    return table().name().equals(that.table().name())
        && Objects.equals(taskSetID, that.taskSetID)
        && Objects.equals(splitSize, that.splitSize)
        && Objects.equals(splitLookback, that.splitLookback)
        && Objects.equals(splitOpenFileCost, that.splitOpenFileCost);
  }

  /** 返回该对象的哈希码。 */
  @Override
  public int hashCode() {
    return Objects.hash(table().name(), taskSetID, splitSize, splitSize, splitOpenFileCost);
  }

  /** 返回该对象的字符串表示。 */
  @Override
  public String toString() {
    return String.format(
        "IcebergFilesScan(table=%s, type=%s, taskSetID=%s, caseSensitive=%s)",
        table(), expectedSchema().asStruct(), taskSetID, caseSensitive());
  }
}
