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
 * 所属模块：iceberg-spark v3.4
 *
 * <p>职责：基于大小的数据文件重写器基类，按文件大小过滤与分组待重写文件。
 *
 * <p>设计意图：提供 bin-pack 重写的公共大小阈值与分组逻辑。
 *
 * <p>上下游关系：被 SparkBinPackDataRewriter 继承；由 RewriteDataFilesSparkAction 使用。
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
  /** 执行 doRewrite 相关操作。 */
  protected abstract void doRewrite(String groupId, List<FileScanTask> group);
  /** 执行 spark 相关操作。 */
  protected SparkSession spark() {
    return spark;
  }
  /** 重写计划。 */
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
