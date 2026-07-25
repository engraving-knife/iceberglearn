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
 * 增量清单集合，承载一次 checkpoint 周期内写入的 DataFile/DeleteFile 清单。
 *
 * <p>所属模块：iceberg-flink v1.15。职责：封装已写数据文件清单与删除文件清单，供 committer 合并提交。
 *
 * <p>设计意图：值对象；上下游：由 IcebergStreamWriter 产出，被 IcebergFilesCommitter 消费。
 */
class DeltaManifests {

  private static final CharSequence[] EMPTY_REF_DATA_FILES = new CharSequence[0];

  private final ManifestFile dataManifest;
  private final ManifestFile deleteManifest;
  private final CharSequence[] referencedDataFiles;

  DeltaManifests(ManifestFile dataManifest, ManifestFile deleteManifest) {
    this(dataManifest, deleteManifest, EMPTY_REF_DATA_FILES);
  }

  DeltaManifests(
      ManifestFile dataManifest, ManifestFile deleteManifest, CharSequence[] referencedDataFiles) {
    Preconditions.checkNotNull(referencedDataFiles, "Referenced data files shouldn't be null.");

    this.dataManifest = dataManifest;
    this.deleteManifest = deleteManifest;
    this.referencedDataFiles = referencedDataFiles;
  }

  ManifestFile dataManifest() {
    return dataManifest;
  }

  ManifestFile deleteManifest() {
    return deleteManifest;
  }

  CharSequence[] referencedDataFiles() {
    return referencedDataFiles;
  }

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
