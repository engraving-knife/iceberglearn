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

import java.util.List;
import org.apache.iceberg.ManifestFile;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;

/**
 * 文件级说明：单个 checkpoint 周期内写入的数据/删除 manifest 文件集合。
 *
 * <p>所属模块：iceberg-flink（sink 子包），表示一个 writer 任务在一次 checkpoint 中产出的增量文件。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>持有数据文件 manifest 和删除文件 manifest。
 *   <li>记录引用的数据文件列表（用于 CDC 场景的 referencedDataFiles）。
 *   <li>提供 manifests() 方法将非 null 的 manifest 汇总为列表。
 * </ul>
 *
 * <p>设计意图：Iceberg 的增量写入需要区分数据文件和删除文件，分别写入不同的 manifest。 DeltaManifests 作为 writer 与 committer
 * 之间的数据载体，在一次 checkpoint 中传递写出的文件清单。
 *
 * <p>上下游关系：由 {@link IcebergStreamWriter} 产出，被 {@link IcebergFilesCommitter} 消费并提交。
 */
class DeltaManifests {

  private static final CharSequence[] EMPTY_REF_DATA_FILES = new CharSequence[0];

  private final ManifestFile dataManifest;
  private final ManifestFile deleteManifest;
  private final CharSequence[] referencedDataFiles;

  /** 构造方法（无引用数据文件）。 */
  DeltaManifests(ManifestFile dataManifest, ManifestFile deleteManifest) {
    this(dataManifest, deleteManifest, EMPTY_REF_DATA_FILES);
  }

  /**
   * 构造方法。
   *
   * @param dataManifest 数据文件 manifest
   * @param deleteManifest 删除文件 manifest
   * @param referencedDataFiles 引用的数据文件路径数组
   */
  DeltaManifests(
      ManifestFile dataManifest, ManifestFile deleteManifest, CharSequence[] referencedDataFiles) {
    Preconditions.checkNotNull(referencedDataFiles, "Referenced data files shouldn't be null.");

    this.dataManifest = dataManifest;
    this.deleteManifest = deleteManifest;
    this.referencedDataFiles = referencedDataFiles;
  }

  /** 返回数据文件 manifest。 */
  ManifestFile dataManifest() {
    return dataManifest;
  }

  /** 返回删除文件 manifest。 */
  ManifestFile deleteManifest() {
    return deleteManifest;
  }

  /** 返回引用的数据文件路径数组。 */
  CharSequence[] referencedDataFiles() {
    return referencedDataFiles;
  }

  /**
   * 返回所有非 null 的 manifest 列表（数据 + 删除）。
   *
   * @return manifest 文件列表
   */
  List<ManifestFile> manifests() {
    List<ManifestFile> manifests = Lists.newArrayListWithCapacity(2);
    if (dataManifest != null) {
      manifests.add(dataManifest);
    }

    if (deleteManifest != null) {
      manifests.add(deleteManifest);
    }

    return manifests;
  }
}
