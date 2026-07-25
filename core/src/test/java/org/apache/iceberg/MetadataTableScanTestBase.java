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
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.apache.iceberg.expressions.Expressions;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.relocated.com.google.common.collect.Iterables;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.apache.iceberg.types.Types;
import org.apache.iceberg.util.PartitionUtil;
import org.junit.Assert;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * 测试类：MetadataTableScanTestBase，用于验证 Metadata Table Scan 相关功能。
 *
 * <p>所属模块：iceberg-core（测试目录 src/test）。 职责：针对 Metadata Table Scan 的核心行为构造多种场景，覆盖正常路径、边界条件与异常输入，
 * 确保实现与预期语义一致。
 *
 * <p>测试策略：基于 JUnit（必要时配合参数化执行器）搭建表/目录等测试基座， 通过构造输入、执行被测方法并断言结果或状态来验证功能点。
 */
@RunWith(Parameterized.class)
public abstract class MetadataTableScanTestBase extends TableTestBase {

  /** 辅助方法：parameters。 */
  @Parameterized.Parameters(name = "formatVersion = {0}")
  public static Object[] parameters() {
    return new Object[] {1, 2};
  }

  /** 辅助方法：metadata table scan test base。 */
  public MetadataTableScanTestBase(int formatVersion) {
    super(formatVersion);
  }

  /** 辅助方法：actual manifest list paths。 */
  protected Set<String> actualManifestListPaths(TableScan allManifestsTableScan) {
    return StreamSupport.stream(allManifestsTableScan.planFiles().spliterator(), false)
        .map(t -> (AllManifestsTable.ManifestListReadTask) t)
        .map(t -> t.file().path().toString())
        .collect(Collectors.toSet());
  }

  /** 辅助方法：expected manifest list paths。 */
  protected Set<String> expectedManifestListPaths(
      Iterable<Snapshot> snapshots, Long... snapshotIds) {
    Set<Long> snapshotIdSet = Sets.newHashSet(snapshotIds);
    return StreamSupport.stream(snapshots.spliterator(), false)
        .filter(s -> snapshotIdSet.contains(s.snapshotId()))
        .map(Snapshot::manifestListLocation)
        .collect(Collectors.toSet());
  }

  /** 辅助方法：validate task scan residuals。 */
  protected void validateTaskScanResiduals(TableScan scan, boolean ignoreResiduals)
      throws IOException {
    try (CloseableIterable<CombinedScanTask> tasks = scan.planTasks()) {
      Assert.assertTrue("Tasks should not be empty", Iterables.size(tasks) > 0);
      for (CombinedScanTask combinedScanTask : tasks) {
        for (FileScanTask fileScanTask : combinedScanTask.files()) {
          if (ignoreResiduals) {
            Assert.assertEquals(
                "Residuals must be ignored", Expressions.alwaysTrue(), fileScanTask.residual());
          } else {
            Assert.assertNotEquals(
                "Residuals must be preserved", Expressions.alwaysTrue(), fileScanTask.residual());
          }
        }
      }
    }
  }

  /** 辅助方法：validate single field partition。 */
  protected void validateSingleFieldPartition(
      CloseableIterable<ManifestEntry<?>> files, int partitionValue) {
    validatePartition(files, 0, partitionValue);
  }

  /** 辅助方法：validate partition。 */
  protected void validatePartition(
      CloseableIterable<ManifestEntry<? extends ContentFile<?>>> entries,
      int position,
      int partitionValue) {
    Assert.assertTrue(
        "File scan tasks do not include correct file",
        StreamSupport.stream(entries.spliterator(), false)
            .anyMatch(
                entry -> {
                  StructLike partition = entry.file().partition();
                  if (position >= partition.size()) {
                    return false;
                  }

                  return Objects.equals(partitionValue, partition.get(position, Object.class));
                }));
  }

  /** 辅助方法：constants map。 */
  protected Map<Integer, ?> constantsMap(
      PositionDeletesScanTask task, Types.StructType partitionType) {
    return PartitionUtil.constantsMap(task, partitionType, (type, constant) -> constant);
  }
}
