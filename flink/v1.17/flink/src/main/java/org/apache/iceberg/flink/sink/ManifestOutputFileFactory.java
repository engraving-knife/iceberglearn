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
package org.apache.iceberg.flink.sink;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.apache.iceberg.FileFormat;
import org.apache.iceberg.HasTableOperations;
import org.apache.iceberg.Table;
import org.apache.iceberg.TableOperations;
import org.apache.iceberg.io.OutputFile;
import org.apache.iceberg.relocated.com.google.common.base.Strings;

/**
 * 文件级说明：Flink sink 中 manifest 输出文件路径的工厂类。
 *
 * <p>所属模块：iceberg-flink v1.17（Iceberg 与 Flink v1.17 集成模块的 sink 子包）。
 *
 * <p>职责：按 Flink 任务 ID、算子 ID、子任务 ID、尝试次数与 checkpoint ID 生成唯一的 manifest 文件路径，并创建对应的 {@link
 * OutputFile}。
 *
 * <p>设计意图：Flink checkpoint 期间需要把待提交的 manifest 文件写入稳定存储， 路径需保证全局唯一且可追溯来源算子，避免不同子任务/不同 checkpoint
 * 之间互相覆盖。 同时允许用户通过表属性 {@link #FLINK_MANIFEST_LOCATION} 自定义 manifest 存储目录。
 *
 * <p>上下游关系：上游为 {@code FlinkManifestUtil}（调用工厂创建输出文件）， 下游为 Iceberg 的 {@link
 * org.apache.iceberg.io.FileIO}（按路径创建输出文件）。
 */
class ManifestOutputFileFactory {
  // 用户可通过在表属性中设置该值来自定义 Flink manifest 文件的存储目录。
  static final String FLINK_MANIFEST_LOCATION = "flink.manifests.location";

  private final Supplier<Table> tableSupplier;
  private final Map<String, String> props;
  private final String flinkJobId;
  private final String operatorUniqueId;
  private final int subTaskId;
  private final long attemptNumber;
  private final AtomicInteger fileCount = new AtomicInteger(0);

  /**
   * 构造 manifest 输出文件工厂。
   *
   * @param tableSupplier 表的供应器，用于获取表元数据与 IO
   * @param props 表属性，可用于读取自定义 manifest 目录
   * @param flinkJobId Flink 任务 ID
   * @param operatorUniqueId 算子唯一 ID
   * @param subTaskId 子任务 ID
   * @param attemptNumber 任务尝试次数
   */
  ManifestOutputFileFactory(
      Supplier<Table> tableSupplier,
      Map<String, String> props,
      String flinkJobId,
      String operatorUniqueId,
      int subTaskId,
      long attemptNumber) {
    this.tableSupplier = tableSupplier;
    this.props = props;
    this.flinkJobId = flinkJobId;
    this.operatorUniqueId = operatorUniqueId;
    this.subTaskId = subTaskId;
    this.attemptNumber = attemptNumber;
  }

  /**
   * 生成 manifest 文件名（不含目录）。
   *
   * <p>逻辑：把 Flink 任务 ID、算子 ID、子任务 ID、尝试次数、checkpoint ID 与自增计数器按固定模板拼接，并添加 AVRO 扩展名，保证全局唯一。
   *
   * @param checkpointId 当前 checkpoint ID
   * @return 带扩展名的文件名
   */
  private String generatePath(long checkpointId) {
    return FileFormat.AVRO.addExtension(
        String.format(
            "%s-%s-%05d-%d-%d-%05d",
            flinkJobId,
            operatorUniqueId,
            subTaskId,
            attemptNumber,
            checkpointId,
            fileCount.incrementAndGet()));
  }

  /**
   * 按 checkpoint ID 创建 manifest 输出文件。
   *
   * <p>逻辑：若用户在表属性中指定了自定义 manifest 目录， 则把文件路径拼接为「自定义目录/文件名」；否则使用表元数据默认目录。 最后通过 {@link
   * org.apache.iceberg.io.FileIO} 创建输出文件。
   *
   * @param checkpointId 当前 checkpoint ID
   * @return 用于写入 manifest 的 OutputFile
   */
  OutputFile create(long checkpointId) {
    String flinkManifestDir = props.get(FLINK_MANIFEST_LOCATION);
    TableOperations ops = ((HasTableOperations) tableSupplier.get()).operations();

    String newManifestFullPath;
    if (Strings.isNullOrEmpty(flinkManifestDir)) {
      // 用户未指定自定义 manifest 目录，使用默认的元数据目录。
      newManifestFullPath = ops.metadataFileLocation(generatePath(checkpointId));
    } else {
      newManifestFullPath =
          String.format("%s/%s", stripTrailingSlash(flinkManifestDir), generatePath(checkpointId));
    }

    return tableSupplier.get().io().newOutputFile(newManifestFullPath);
  }

  /** 去除路径末尾的所有「/」，避免拼接出现重复斜杠。 */
  private static String stripTrailingSlash(String path) {
    String result = path;
    while (result.endsWith("/")) {
      result = result.substring(0, result.length() - 1);
    }
    return result;
  }
}
