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

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.assertj.core.api.Assertions;

/**
 * 文件级说明：测试 ValidationHelpers 相关功能。
 *
 * <p>所属模块：iceberg-spark（spark v3.2）。职责：验证 Iceberg 表在 Spark 引擎下 校验辅助 相关行为，覆盖正常路径与边界场景。
 *
 * <p>测试策略：基于 SparkSession + JUnit，通过构造测试数据、执行 SQL/DataFrame 操作并断言结果， 覆盖正常路径与边界情况。
 */
public class ValidationHelpers {

  /** 校验辅助。 */
  private ValidationHelpers() {}

  /** 数据seqs。 */
  public static List<Long> dataSeqs(Long... seqs) {
    return Arrays.asList(seqs);
  }

  /** 文件seqs。 */
  public static List<Long> fileSeqs(Long... seqs) {
    return Arrays.asList(seqs);
  }

  /** 快照ids。 */
  public static List<Long> snapshotIds(Long... ids) {
    return Arrays.asList(ids);
  }

  /** 文件。 */
  public static List<String> files(ContentFile<?>... files) {
    return Arrays.stream(files).map(file -> file.path().toString()).collect(Collectors.toList());
  }

  /** 校验数据清单。 */
  public static void validateDataManifest(
      Table table,
      ManifestFile manifest,
      List<Long> dataSeqs,
      List<Long> fileSeqs,
      List<Long> snapshotIds,
      List<String> files) {

    List<Long> actualDataSeqs = Lists.newArrayList();
    List<Long> actualFileSeqs = Lists.newArrayList();
    List<Long> actualSnapshotIds = Lists.newArrayList();
    List<String> actualFiles = Lists.newArrayList();

    for (ManifestEntry<DataFile> entry : ManifestFiles.read(manifest, table.io()).entries()) {
      actualDataSeqs.add(entry.dataSequenceNumber());
      actualFileSeqs.add(entry.fileSequenceNumber());
      actualSnapshotIds.add(entry.snapshotId());
      actualFiles.add(entry.file().path().toString());
    }

    assertSameElements("data seqs", actualDataSeqs, dataSeqs);
    assertSameElements("file seqs", actualFileSeqs, fileSeqs);
    assertSameElements("snapshot IDs", actualSnapshotIds, snapshotIds);
    assertSameElements("files", actualFiles, files);
  }

  /** 断言sameelements。 */
  private static <T> void assertSameElements(String context, List<T> actual, List<T> expected) {
    String errorMessage = String.format("%s must match", context);
    Assertions.assertThat(actual).as(errorMessage).hasSameElementsAs(expected);
  }
}
