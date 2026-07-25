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
package org.apache.iceberg;

import java.io.IOException;
import java.util.List;
import org.apache.iceberg.avro.Avro;
import org.apache.iceberg.exceptions.RuntimeIOException;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.io.InputFile;
import org.apache.iceberg.io.OutputFile;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;

/**
 * Manifest 列表（manifest list）的读写工具：负责读取/写入快照对应的 manifest list 文件。
 *
 * <p>所属模块：iceberg-core（元数据文件 IO 层）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>从 Avro 格式的 manifest list 文件读取 {@link ManifestFile} 列表；
 *   <li>按格式版本（V1/V2）创建对应的 {@link ManifestListWriter} 写入 manifest list。
 * </ul>
 *
 * <p>设计意图：manifest list 是快照的核心索引文件，本类封装 Avro 读写细节，并根据 formatVersion 选择 V1Writer 或 V2Writer（V2 引入
 * sequenceNumber）。
 *
 * <p>上下游关系：被 {@link SnapshotProducer} 等快照生产者在提交时调用写入；被表加载器在读取快照 元数据时调用读取。
 */
class ManifestLists {
  private ManifestLists() {}

  /**
   * 读取 manifest list 文件，返回其中的 {@link ManifestFile} 列表。
   *
   * <p>逻辑：使用 Avro 读取器，把 Avro 记录名重映射到 Iceberg 内部类（GenericManifestFile、
   * GenericPartitionFieldSummary），按 manifest file schema 投影读取，且不复用容器对象
   * （reuseContainers=false）以确保返回独立对象。
   *
   * @param manifestList 输入文件
   * @return manifest 文件列表（LinkedList）
   * @throws RuntimeIOException 读取失败
   */
  static List<ManifestFile> read(InputFile manifestList) {
    try (CloseableIterable<ManifestFile> files =
        Avro.read(manifestList)
            .rename("manifest_file", GenericManifestFile.class.getName())
            .rename("partitions", GenericPartitionFieldSummary.class.getName())
            .rename("r508", GenericPartitionFieldSummary.class.getName())
            .classLoader(GenericManifestFile.class.getClassLoader())
            .project(ManifestFile.schema())
            .reuseContainers(false)
            .build()) {

      return Lists.newLinkedList(files);

    } catch (IOException e) {
      throw new RuntimeIOException(
          e, "Cannot read manifest list file: %s", manifestList.location());
    }
  }

  /**
   * 创建 manifest list 写入器，按格式版本选择 V1 或 V2 writer。
   *
   * @param formatVersion 表格式版本（1 或 2）
   * @param manifestListFile 输出文件
   * @param snapshotId 本次提交的快照 id
   * @param parentSnapshotId 父快照 id（可为 null）
   * @param sequenceNumber 序列号（V2 使用）
   * @return 对应版本的 {@link ManifestListWriter}
   * @throws IllegalArgumentException 若 formatVersion 不支持或 V1 下 sequenceNumber 非初始值
   */
  static ManifestListWriter write(
      int formatVersion,
      OutputFile manifestListFile,
      long snapshotId,
      Long parentSnapshotId,
      long sequenceNumber) {
    switch (formatVersion) {
      case 1:
        Preconditions.checkArgument(
            sequenceNumber == TableMetadata.INITIAL_SEQUENCE_NUMBER,
            "Invalid sequence number for v1 manifest list: %s",
            sequenceNumber);
        return new ManifestListWriter.V1Writer(manifestListFile, snapshotId, parentSnapshotId);
      case 2:
        return new ManifestListWriter.V2Writer(
            manifestListFile, snapshotId, parentSnapshotId, sequenceNumber);
    }
    throw new UnsupportedOperationException(
        "Cannot write manifest list for table version: " + formatVersion);
  }
}
