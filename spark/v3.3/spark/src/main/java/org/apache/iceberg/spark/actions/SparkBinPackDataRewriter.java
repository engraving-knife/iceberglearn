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
 * 基于 Spark 执行的 Iceberg 表维护动作的写入器，负责把 Spark 内部数据写入 Iceberg 底层存储。
 *
 * <p>所属模块：iceberg-spark v3.3。 类型：类 SparkBinPackDataRewriter。
 *
 * <p>设计意图：Catalyst 规则，通过 transformation 介入计划处理。
 *
 * <p>上下游：由 SparkActions 创建，委托 Spark 作业执行实际数据处理。
 */
class SparkBinPackDataRewriter extends SparkSizeBasedDataRewriter {

  SparkBinPackDataRewriter(SparkSession spark, Table table) {
    super(spark, table);
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  @Override
  public String description() {
    return "BIN-PACK";
  }

  /** 执行该方法的具体逻辑。 */
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

  // invoke a shuffle if the original spec does not match the output spec
  /** 执行该方法的具体逻辑。 */
  private DistributionMode distributionMode(List<FileScanTask> group) {
    boolean requiresRepartition = !group.get(0).spec().equals(table().spec());
    return requiresRepartition ? DistributionMode.RANGE : DistributionMode.NONE;
  }
}
