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
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.iceberg.ContentScanTask;
import org.apache.iceberg.PositionDeletesScanTask;
import org.apache.iceberg.Schema;
import org.apache.iceberg.StructLike;
import org.apache.iceberg.Table;
import org.apache.iceberg.relocated.com.google.common.base.Joiner;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.spark.ScanTaskSetManager;
import org.apache.iceberg.spark.SparkSchemaUtil;
import org.apache.iceberg.spark.SparkWriteConf;
import org.apache.iceberg.util.StructLikeSet;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.connector.write.LogicalWriteInfo;
import org.apache.spark.sql.connector.write.Write;
import org.apache.spark.sql.connector.write.WriteBuilder;
import org.apache.spark.sql.types.StructType;

/**
 * 位置删除文件重写的写入构建器：基于 Spark 数据源创建 {@link SparkPositionDeletesRewrite}。
 *
 * <p>所属模块：iceberg-spark（source 子包，Spark 数据源写入路径）。
 *
 * <p>职责：从 {@link ScanTaskSetManager} 取出待重写的位置删除任务，校验它们属于同一分区规格 与同一分区，并构造位置删除重写写入器。
 *
 * <p>设计意图：本类专用于位置删除文件重写动作，假定所有输入删除任务同属一个分区、 数据来源于 {@link ScanTaskSetManager} 暂存的任务集，从而简化写入逻辑。
 *
 * <p>上下游关系：实现 {@link WriteBuilder}，由位置删除重写动作触发写入， 产出 {@link SparkPositionDeletesRewrite} 执行实际重写。
 */
public class SparkPositionDeletesRewriteBuilder implements WriteBuilder {

  private final SparkSession spark;
  private final Table table;
  private final SparkWriteConf writeConf;
  private final LogicalWriteInfo writeInfo;
  private final StructType dsSchema;
  private final Schema writeSchema;

  SparkPositionDeletesRewriteBuilder(
      SparkSession spark, Table table, String branch, LogicalWriteInfo info) {
    this.spark = spark;
    this.table = table;
    this.writeConf = new SparkWriteConf(spark, table, branch, info.options());
    this.writeInfo = info;
    this.dsSchema = info.schema();
    this.writeSchema = SparkSchemaUtil.convert(table.schema(), dsSchema, writeConf.caseSensitive());
  }

  /**
   * 构建位置删除重写写入器。
   *
   * <p>逻辑：取重写文件集 ID，从 {@link ScanTaskSetManager} 获取任务并校验非空； 校验所有任务分区规格 ID 与分区值一致，最后构造 {@link
   * SparkPositionDeletesRewrite}。
   *
   * @return 位置删除重写写入器
   * @throws IllegalArgumentException 当未通过动作触发或任务不满足同分区约束时抛出
   */
  @Override
  public Write build() {
    String fileSetId = writeConf.rewrittenFileSetId();

    Preconditions.checkArgument(
        fileSetId != null, "Can only write to %s via actions", table.name());

    // all files of rewrite group have same partition and spec id
    ScanTaskSetManager taskSetManager = ScanTaskSetManager.get();
    List<PositionDeletesScanTask> tasks = taskSetManager.fetchTasks(table, fileSetId);
    Preconditions.checkArgument(
        tasks != null && tasks.size() > 0, "No scan tasks found for %s", fileSetId);

    int specId = specId(fileSetId, tasks);
    StructLike partition = partition(fileSetId, tasks);

    return new SparkPositionDeletesRewrite(
        spark, table, writeConf, writeInfo, writeSchema, dsSchema, specId, partition);
  }
  /** 执行 specId 相关操作。 */
  private int specId(String fileSetId, List<PositionDeletesScanTask> tasks) {
    Set<Integer> specIds = tasks.stream().map(t -> t.spec().specId()).collect(Collectors.toSet());
    Preconditions.checkArgument(
        specIds.size() == 1,
        "All scan tasks of %s are expected to have same spec id, but got %s",
        fileSetId,
        Joiner.on(",").join(specIds));
    return tasks.get(0).spec().specId();
  }
  /** 执行 partition 相关操作。 */
  private StructLike partition(String fileSetId, List<PositionDeletesScanTask> tasks) {
    StructLikeSet partitions = StructLikeSet.create(tasks.get(0).spec().partitionType());
    tasks.stream().map(ContentScanTask::partition).forEach(partitions::add);
    Preconditions.checkArgument(
        partitions.size() == 1,
        "All scan tasks of %s are expected to have the same partition, but got %s",
        fileSetId,
        Joiner.on(",").join(partitions));
    return tasks.get(0).partition();
  }
}
