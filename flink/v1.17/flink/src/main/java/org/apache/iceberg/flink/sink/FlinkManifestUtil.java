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

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.apache.iceberg.DataFile;
import org.apache.iceberg.DeleteFile;
import org.apache.iceberg.ManifestFile;
import org.apache.iceberg.ManifestFiles;
import org.apache.iceberg.ManifestWriter;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Table;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.io.OutputFile;
import org.apache.iceberg.io.WriteResult;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;

/**
 * 文件级说明：Flink sink 中操作 Iceberg manifest 文件的工具类。
 *
 * <p>所属模块：iceberg-flink v1.17（Iceberg 与 Flink v1.17 集成模块的 sink 子包）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>把 {@link WriteResult} 中的数据文件与删除文件写入临时 manifest 文件， 形成 {@link DeltaManifests} 用于 checkpoint
 *       持久化。
 *   <li>从持久化的 DeltaManifests 读取回 WriteResult，用于故障恢复或最终提交。
 *   <li>创建 {@link ManifestOutputFileFactory} 用于按规则生成 manifest 输出文件路径。
 * </ul>
 *
 * <p>设计意图：Flink checkpoint 需要把待提交文件写入稳定存储， manifest 文件是 Iceberg 表元数据的标准载体，复用其格式便于跨任务恢复与最终提交。
 *
 * <p>上下游关系：上游为 {@code IcebergFilesCommitter} 的 checkpoint 流程， 下游为 Iceberg 的 {@link ManifestFiles}
 * 读写 API。
 */
class FlinkManifestUtil {
  private static final int FORMAT_V2 = 2;
  private static final Long DUMMY_SNAPSHOT_ID = 0L;

  /** 私有构造，工具类禁止实例化。 */
  private FlinkManifestUtil() {}

  /** 把数据文件列表写入 manifest 文件并返回 ManifestFile。 */
  static ManifestFile writeDataFiles(
      OutputFile outputFile, PartitionSpec spec, List<DataFile> dataFiles) throws IOException {
    ManifestWriter<DataFile> writer =
        ManifestFiles.write(FORMAT_V2, spec, outputFile, DUMMY_SNAPSHOT_ID);

    try (ManifestWriter<DataFile> closeableWriter = writer) {
      closeableWriter.addAll(dataFiles);
    }

    return writer.toManifestFile();
  }

  /** 从 manifest 文件读取数据文件列表。 */
  static List<DataFile> readDataFiles(
      ManifestFile manifestFile, FileIO io, Map<Integer, PartitionSpec> specsById)
      throws IOException {
    try (CloseableIterable<DataFile> dataFiles = ManifestFiles.read(manifestFile, io, specsById)) {
      return Lists.newArrayList(dataFiles);
    }
  }

  /** 创建 manifest 输出文件工厂，用于按 Flink 任务与算子维度生成 manifest 文件路径。 */
  static ManifestOutputFileFactory createOutputFileFactory(
      Supplier<Table> tableSupplier,
      Map<String, String> tableProps,
      String flinkJobId,
      String operatorUniqueId,
      int subTaskId,
      long attemptNumber) {
    return new ManifestOutputFileFactory(
        tableSupplier, tableProps, flinkJobId, operatorUniqueId, subTaskId, attemptNumber);
  }

  /**
   * 把 {@link WriteResult} 中的数据/删除文件写入临时 manifest 文件。
   *
   * <p>说明：WriteResult 中的所有 DataFiles 与 DeleteFiles 必须属于同一分区 schema。
   *
   * @param result 待持久化的写入结果
   * @param outputFileSupplier 提供输出文件句柄的供应器
   * @param spec 数据/删除文件所属的分区 schema
   * @return 包含数据与删除 manifest 的 DeltaManifests
   * @throws IOException 写入失败时抛出
   */
  static DeltaManifests writeCompletedFiles(
      WriteResult result, Supplier<OutputFile> outputFileSupplier, PartitionSpec spec)
      throws IOException {

    ManifestFile dataManifest = null;
    ManifestFile deleteManifest = null;

    // Write the completed data files into a newly created data manifest file.
    if (result.dataFiles() != null && result.dataFiles().length > 0) {
      dataManifest =
          writeDataFiles(outputFileSupplier.get(), spec, Lists.newArrayList(result.dataFiles()));
    }

    // Write the completed delete files into a newly created delete manifest file.
    if (result.deleteFiles() != null && result.deleteFiles().length > 0) {
      OutputFile deleteManifestFile = outputFileSupplier.get();

      ManifestWriter<DeleteFile> deleteManifestWriter =
          ManifestFiles.writeDeleteManifest(FORMAT_V2, spec, deleteManifestFile, DUMMY_SNAPSHOT_ID);
      try (ManifestWriter<DeleteFile> writer = deleteManifestWriter) {
        for (DeleteFile deleteFile : result.deleteFiles()) {
          writer.add(deleteFile);
        }
      }

      deleteManifest = deleteManifestWriter.toManifestFile();
    }

    return new DeltaManifests(dataManifest, deleteManifest, result.referencedDataFiles());
  }

  /**
   * 从 DeltaManifests 读取回 WriteResult。
   *
   * <p>逻辑：分别从数据 manifest 与删除 manifest 读取文件列表， 合并引用的数据文件，构造完整的 WriteResult。
   *
   * @param deltaManifests 持久化的增量 manifest 信息
   * @param io 文件 IO
   * @param specsById 分区 schema 字典
   * @return 还原的 WriteResult
   * @throws IOException 读取失败时抛出
   */
  static WriteResult readCompletedFiles(
      DeltaManifests deltaManifests, FileIO io, Map<Integer, PartitionSpec> specsById)
      throws IOException {
    WriteResult.Builder builder = WriteResult.builder();

    // Read the completed data files from persisted data manifest file.
    if (deltaManifests.dataManifest() != null) {
      builder.addDataFiles(readDataFiles(deltaManifests.dataManifest(), io, specsById));
    }

    // Read the completed delete files from persisted delete manifests file.
    if (deltaManifests.deleteManifest() != null) {
      try (CloseableIterable<DeleteFile> deleteFiles =
          ManifestFiles.readDeleteManifest(deltaManifests.deleteManifest(), io, specsById)) {
        builder.addDeleteFiles(deleteFiles);
      }
    }

    return builder.addReferencedDataFiles(deltaManifests.referencedDataFiles()).build();
  }
}
