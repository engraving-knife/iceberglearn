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
import org.apache.iceberg.DistributionMode;
import org.apache.iceberg.FileScanTask;
import org.apache.iceberg.Table;
import org.apache.iceberg.spark.SparkReadOptions;
import org.apache.iceberg.spark.SparkWriteOptions;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;

/**
 * 基于装箱（bin-pack）策略的数据文件重写器。
 *
 * <p>所属模块：iceberg-spark（actions 子包，提供基于 Spark 的表维护动作实现）。
 *
 * <p>职责：将一组待重写的文件扫描任务按目标大小重新打包成新的数据文件， 使每个输出文件接近目标大小，从而减少小文件、均衡文件体积。
 *
 * <p>设计意图：复用 Spark 数据源读写能力——以指定 split 大小读取输入文件， 再以目标文件大小写出，每个 split 对应一个新文件。当输入分区规格与表当前规格不一致时， 触发
 * range 重分布以对齐分区，否则不引入额外 shuffle。
 *
 * <p>上下游关系：继承自 {@link SparkSizeBasedDataRewriter}，被数据文件重写动作 （如 RewriteDataFilesSparkAction）按策略选中并调用
 * {@link #doRewrite} 执行实际重写。
 */
class SparkBinPackDataRewriter extends SparkSizeBasedDataRewriter {

  SparkBinPackDataRewriter(SparkSession spark, Table table) {
    super(spark, table);
  }

  /** 返回该重写策略的可读名称。 */
  @Override
  public String description() {
    return "BIN-PACK";
  }

  /**
   * 执行装箱重写：读取指定文件组并按目标大小写出为新文件。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>以 groupId 标识的任务集合读取输入文件，按 {@code splitSize(inputSize(group))} 计算的 split 大小切分。
   *   <li>将读到的数据以 append 模式写回，目标文件大小取 {@link #writeMaxFileSize()}， 分布模式由 {@link #distributionMode}
   *       决定。
   * </ol>
   *
   * @param groupId 文件组标识，同时作为读写的任务集 ID
   * @param group 待重写的文件扫描任务列表
   */
  @Override
  protected void doRewrite(String groupId, List<FileScanTask> group) {
    // read the files packing them into splits of the required size
    Dataset<Row> scanDF =
        spark()
            .read()
            .format("iceberg")
            .option(SparkReadOptions.SCAN_TASK_SET_ID, groupId)
            .option(SparkReadOptions.SPLIT_SIZE, splitSize(inputSize(group)))
            .option(SparkReadOptions.FILE_OPEN_COST, "0")
            .load(groupId);

    // write the packed data into new files where each split becomes a new file
    scanDF
        .write()
        .format("iceberg")
        .option(SparkWriteOptions.REWRITTEN_FILE_SCAN_TASK_SET_ID, groupId)
        .option(SparkWriteOptions.TARGET_FILE_SIZE_BYTES, writeMaxFileSize())
        .option(SparkWriteOptions.DISTRIBUTION_MODE, distributionMode(group).modeName())
        .mode("append")
        .save(groupId);
  }

  /**
   * 判断本次重写所需的分布模式。
   *
   * <p>设计要点：当输入文件所在分区规格与表当前分区规格不一致时，需要 range 重分布 以对齐分区；否则无需额外 shuffle，返回 NONE。
   */
  // invoke a shuffle if the original spec does not match the output spec
  private DistributionMode distributionMode(List<FileScanTask> group) {
    boolean requiresRepartition = !group.get(0).spec().equals(table().spec());
    return requiresRepartition ? DistributionMode.RANGE : DistributionMode.NONE;
  }
}
