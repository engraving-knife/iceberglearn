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
import java.util.UUID;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.Iterables;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.assertj.core.api.Assertions;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

/**
 * 测试类：DataTableScanTestBase，用于验证 Data Table Scan 相关功能。
 *
 * <p>所属模块：iceberg-core（测试目录 src/test）。 职责：针对 Data Table Scan 的核心行为构造多种场景，覆盖正常路径、边界条件与异常输入，
 * 确保实现与预期语义一致。
 *
 * <p>测试策略：基于 JUnit（必要时配合参数化执行器）搭建表/目录等测试基座， 通过构造输入、执行被测方法并断言结果或状态来验证功能点。
 */
public abstract class DataTableScanTestBase<
        ScanT extends Scan<ScanT, T, G>, T extends ScanTask, G extends ScanTaskGroup<T>>
    extends ScanTestBase<ScanT, T, G> {

  /** 辅助方法：data table scan test base。 */
  public DataTableScanTestBase(int formatVersion) {
    super(formatVersion);
  }

  /** 辅助方法：use ref。 */
  protected abstract ScanT useRef(ScanT scan, String ref);

  /** 辅助方法：use snapshot。 */
  protected abstract ScanT useSnapshot(ScanT scan, long snapshotId);

  /** 辅助方法：as of time。 */
  protected abstract ScanT asOfTime(ScanT scan, long timestampMillis);

  /**
   * 测试场景：task row counts。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testTaskRowCounts() {
    Assume.assumeTrue(formatVersion == 2);

    DataFile dataFile1 = newDataFile("data_bucket=0");
    table.newFastAppend().appendFile(dataFile1).commit();

    DataFile dataFile2 = newDataFile("data_bucket=1");
    table.newFastAppend().appendFile(dataFile2).commit();

    DeleteFile deleteFile1 = newDeleteFile("data_bucket=0");
    table.newRowDelta().addDeletes(deleteFile1).commit();

    DeleteFile deleteFile2 = newDeleteFile("data_bucket=1");
    table.newRowDelta().addDeletes(deleteFile2).commit();

    ScanT scan = newScan().option(TableProperties.SPLIT_SIZE, "50");

    List<T> fileScanTasks = Lists.newArrayList(scan.planFiles());
    Assert.assertEquals("Must have 2 FileScanTasks", 2, fileScanTasks.size());
    for (T task : fileScanTasks) {
      Assert.assertEquals("Rows count must match", 10, task.estimatedRowsCount());
    }

    List<G> combinedScanTasks = Lists.newArrayList(scan.planTasks());
    Assert.assertEquals("Must have 4 CombinedScanTask", 4, combinedScanTasks.size());
    for (G task : combinedScanTasks) {
      Assert.assertEquals("Rows count must match", 5, task.estimatedRowsCount());
    }
  }

  /** 辅助方法：new data file。 */
  protected DataFile newDataFile(String partitionPath) {
    return DataFiles.builder(table.spec())
        .withPath("/path/to/data-" + UUID.randomUUID() + ".parquet")
        .withFormat(FileFormat.PARQUET)
        .withFileSizeInBytes(100)
        .withPartitionPath(partitionPath)
        .withRecordCount(10)
        .build();
  }

  /** 辅助方法：new delete file。 */
  protected DeleteFile newDeleteFile(String partitionPath) {
    return FileMetadata.deleteFileBuilder(table.spec())
        .ofPositionDeletes()
        .withPath("/path/to/delete-" + UUID.randomUUID() + ".parquet")
        .withFormat(FileFormat.PARQUET)
        .withFileSizeInBytes(100)
        .withPartitionPath(partitionPath)
        .withRecordCount(10)
        .build();
  }

  /**
   * 测试场景：scan from branch tip。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testScanFromBranchTip() throws IOException {
    table.newFastAppend().appendFile(FILE_A).commit();
    // Add B and C to new branch
    table.newFastAppend().appendFile(FILE_B).appendFile(FILE_C).toBranch("testBranch").commit();
    // Add D to main
    table.newFastAppend().appendFile(FILE_D).commit();

    ScanT testBranchScan = useRef(newScan(), "testBranch");
    validateExpectedFileScanTasks(
        testBranchScan, ImmutableList.of(FILE_A.path(), FILE_B.path(), FILE_C.path()));

    ScanT mainScan = newScan();
    validateExpectedFileScanTasks(mainScan, ImmutableList.of(FILE_A.path(), FILE_D.path()));
  }

  /**
   * 测试场景：scan from tag。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testScanFromTag() throws IOException {
    table.newFastAppend().appendFile(FILE_A).appendFile(FILE_B).commit();
    table.manageSnapshots().createTag("tagB", table.currentSnapshot().snapshotId()).commit();
    table.newFastAppend().appendFile(FILE_C).commit();
    ScanT tagScan = useRef(newScan(), "tagB");
    validateExpectedFileScanTasks(tagScan, ImmutableList.of(FILE_A.path(), FILE_B.path()));
    ScanT mainScan = newScan();
    validateExpectedFileScanTasks(
        mainScan, ImmutableList.of(FILE_A.path(), FILE_B.path(), FILE_C.path()));
  }

  /**
   * 测试场景：scan from ref when snapshot set fails。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testScanFromRefWhenSnapshotSetFails() {
    table.newFastAppend().appendFile(FILE_A).appendFile(FILE_B).commit();
    table.manageSnapshots().createTag("tagB", table.currentSnapshot().snapshotId()).commit();

    Assertions.assertThatThrownBy(
            () -> useRef(useSnapshot(newScan(), table.currentSnapshot().snapshotId()), "tagB"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot override ref, already set snapshot id=1");
  }

  /**
   * 测试场景：setting snapshot when ref set fails。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testSettingSnapshotWhenRefSetFails() {
    table.newFastAppend().appendFile(FILE_A).commit();
    Snapshot snapshotA = table.currentSnapshot();
    table.newFastAppend().appendFile(FILE_B).commit();
    table.manageSnapshots().createTag("tagB", table.currentSnapshot().snapshotId()).commit();

    Assertions.assertThatThrownBy(
            () -> useSnapshot(useRef(newScan(), "tagB"), snapshotA.snapshotId()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot override snapshot, already set snapshot id=2");
  }

  /**
   * 测试场景：branch time travel fails。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testBranchTimeTravelFails() {
    table.newFastAppend().appendFile(FILE_A).appendFile(FILE_B).commit();
    table
        .manageSnapshots()
        .createBranch("testBranch", table.currentSnapshot().snapshotId())
        .commit();

    Assertions.assertThatThrownBy(
            () -> asOfTime(useRef(newScan(), "testBranch"), System.currentTimeMillis()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot override snapshot, already set snapshot id=1");
  }

  /**
   * 测试场景：setting multiple refs fails。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testSettingMultipleRefsFails() {
    table.newFastAppend().appendFile(FILE_A).commit();
    table.manageSnapshots().createTag("tagA", table.currentSnapshot().snapshotId()).commit();
    table.newFastAppend().appendFile(FILE_B).commit();
    table.manageSnapshots().createTag("tagB", table.currentSnapshot().snapshotId()).commit();

    Assertions.assertThatThrownBy(() -> useRef(useRef(newScan(), "tagB"), "tagA"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot override ref, already set snapshot id=2");
  }

  /**
   * 测试场景：setting invalid ref fails。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testSettingInvalidRefFails() {
    Assertions.assertThatThrownBy(() -> useRef(newScan(), "nonexisting"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot find ref nonexisting");
  }

  /** 辅助方法：validate expected file scan tasks。 */
  private void validateExpectedFileScanTasks(ScanT scan, List<CharSequence> expectedFileScanPaths)
      throws IOException {
    try (CloseableIterable<T> scanTasks = scan.planFiles()) {
      Assert.assertEquals(expectedFileScanPaths.size(), Iterables.size(scanTasks));
      List<CharSequence> actualFiles = Lists.newArrayList();
      for (T task : scanTasks) {
        actualFiles.add(((FileScanTask) task).file().path());
      }
      Assert.assertTrue(actualFiles.containsAll(expectedFileScanPaths));
    }
  }

  /**
   * 测试场景：sequence numbers through plan files。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testSequenceNumbersThroughPlanFiles() {
    Assume.assumeTrue(formatVersion == 2);

    DataFile dataFile1 = newDataFile("data_bucket=0");
    table.newFastAppend().appendFile(dataFile1).commit();

    DataFile dataFile2 = newDataFile("data_bucket=1");
    table.newFastAppend().appendFile(dataFile2).commit();

    DeleteFile deleteFile1 = newDeleteFile("data_bucket=0");
    table.newRowDelta().addDeletes(deleteFile1).commit();

    DeleteFile deleteFile2 = newDeleteFile("data_bucket=1");
    table.newRowDelta().addDeletes(deleteFile2).commit();

    ScanT scan = newScan();

    List<T> fileScanTasks = Lists.newArrayList(scan.planFiles());
    Assert.assertEquals("Must have 2 FileScanTasks", 2, fileScanTasks.size());
    for (T task : fileScanTasks) {
      FileScanTask fileScanTask = (FileScanTask) task;
      DataFile file = fileScanTask.file();
      long expectedDataSequenceNumber = 0L;
      long expectedDeleteSequenceNumber = 0L;
      if (file.path().equals(dataFile1.path())) {
        expectedDataSequenceNumber = 1L;
        expectedDeleteSequenceNumber = 3L;
      }

      if (file.path().equals(dataFile2.path())) {
        expectedDataSequenceNumber = 2L;
        expectedDeleteSequenceNumber = 4L;
      }

      Assert.assertEquals(
          "Data sequence number mismatch",
          expectedDataSequenceNumber,
          file.dataSequenceNumber().longValue());
      Assert.assertEquals(
          "File sequence number mismatch",
          expectedDataSequenceNumber,
          file.fileSequenceNumber().longValue());

      List<DeleteFile> deleteFiles = fileScanTask.deletes();
      Assert.assertEquals("Must have 1 delete file", 1, Iterables.size(deleteFiles));
      DeleteFile deleteFile = Iterables.getOnlyElement(deleteFiles);
      Assert.assertEquals(
          "Data sequence number mismatch",
          expectedDeleteSequenceNumber,
          deleteFile.dataSequenceNumber().longValue());
      Assert.assertEquals(
          "File sequence number mismatch",
          expectedDeleteSequenceNumber,
          deleteFile.fileSequenceNumber().longValue());
    }
  }
}
