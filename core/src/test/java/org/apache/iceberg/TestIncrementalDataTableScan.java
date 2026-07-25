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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.iceberg.events.IncrementalScanEvent;
import org.apache.iceberg.events.Listener;
import org.apache.iceberg.events.Listeners;
import org.apache.iceberg.expressions.Expressions;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.relocated.com.google.common.collect.Iterables;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.assertj.core.api.Assertions;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * 测试类：TestIncrementalDataTableScan，用于验证 Incremental Data Table Scan 相关功能。
 *
 * <p>所属模块：iceberg-core（测试目录 src/test）。 职责：针对 Incremental Data Table Scan
 * 的核心行为构造多种场景，覆盖正常路径、边界条件与异常输入， 确保实现与预期语义一致。
 *
 * <p>测试策略：基于 JUnit（必要时配合参数化执行器）搭建表/目录等测试基座， 通过构造输入、执行被测方法并断言结果或状态来验证功能点。
 */
@RunWith(Parameterized.class)
public class TestIncrementalDataTableScan extends TableTestBase {
  /** 辅助方法：parameters。 */
  @Parameterized.Parameters(name = "formatVersion = {0}")
  public static Object[] parameters() {
    return new Object[] {1, 2};
  }

  /** 辅助方法：incremental data table scan。 */
  public TestIncrementalDataTableScan(int formatVersion) {
    super(formatVersion);
  }

  /** 辅助方法：setup table properties。 */
  @Before
  public void setupTableProperties() {
    table.updateProperties().set(TableProperties.MANIFEST_MIN_MERGE_COUNT, "3").commit();
  }

  /**
   * 测试场景：invalid scans。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testInvalidScans() {
    add(table.newAppend(), files("A"));
    Assertions.assertThatThrownBy(() -> appendsBetweenScan(1, 1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("from and to snapshot ids cannot be the same");

    add(table.newAppend(), files("B"));
    add(table.newAppend(), files("C"));
    add(table.newAppend(), files("D"));
    add(table.newAppend(), files("E"));
    Assertions.assertThatThrownBy(() -> table.newScan().appendsBetween(2, 5).appendsBetween(1, 4))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("from snapshot id 1 not in existing snapshot ids range (2, 4]");
    Assertions.assertThatThrownBy(() -> table.newScan().appendsBetween(1, 2).appendsBetween(1, 3))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("to snapshot id 3 not in existing snapshot ids range (1, 2]");
  }

  /**
   * 测试场景：appends。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testAppends() {
    add(table.newAppend(), files("A")); // 1
    add(table.newAppend(), files("B"));
    add(table.newAppend(), files("C"));
    add(table.newAppend(), files("D"));
    add(table.newAppend(), files("E")); // 5

    class MyListener implements Listener<IncrementalScanEvent> {

      IncrementalScanEvent lastEvent = null;

      /** 辅助方法：notify。 */
      @Override
      public void notify(IncrementalScanEvent event) {
        this.lastEvent = event;
      }

      /** 辅助方法：event。 */
      public IncrementalScanEvent event() {
        return lastEvent;
      }
    }

    MyListener listener1 = new MyListener();
    Listeners.register(listener1, IncrementalScanEvent.class);
    filesMatch(Lists.newArrayList("B", "C", "D", "E"), appendsBetweenScan(1, 5));
    Assert.assertTrue(listener1.event().fromSnapshotId() == 1);
    Assert.assertTrue(listener1.event().toSnapshotId() == 5);
    filesMatch(Lists.newArrayList("C", "D", "E"), appendsBetweenScan(2, 5));
    Assert.assertTrue(listener1.event().fromSnapshotId() == 2);
    Assert.assertTrue(listener1.event().toSnapshotId() == 5);
    Assert.assertEquals(table.schema(), listener1.event().projection());
    Assert.assertEquals(Expressions.alwaysTrue(), listener1.event().filter());
    Assert.assertEquals("test", listener1.event().tableName());
    Assert.assertEquals(false, listener1.event().isFromSnapshotInclusive());
  }

  /**
   * 测试场景：replace overwrites deletes。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testReplaceOverwritesDeletes() {
    add(table.newAppend(), files("A")); // 1
    add(table.newAppend(), files("B"));
    add(table.newAppend(), files("C"));
    add(table.newAppend(), files("D"));
    add(table.newAppend(), files("E")); // 5
    filesMatch(Lists.newArrayList("B", "C", "D", "E"), appendsBetweenScan(1, 5));

    replace(table.newRewrite(), files("A", "B", "C"), files("F", "G")); // 6
    // Replace commits are ignored
    filesMatch(Lists.newArrayList("B", "C", "D", "E"), appendsBetweenScan(1, 6));
    filesMatch(Lists.newArrayList("E"), appendsBetweenScan(4, 6));
    // 6th snapshot is a replace. No new content is added
    Assert.assertTrue("Replace commits are ignored", appendsBetweenScan(5, 6).isEmpty());
    delete(table.newDelete(), files("D")); // 7
    // 7th snapshot is a delete.
    Assert.assertTrue("Replace and delete commits are ignored", appendsBetweenScan(5, 7).isEmpty());
    Assert.assertTrue("Delete commits are ignored", appendsBetweenScan(6, 7).isEmpty());
    add(table.newAppend(), files("I")); // 8
    // snapshots 6 and 7 are ignored
    filesMatch(Lists.newArrayList("B", "C", "D", "E", "I"), appendsBetweenScan(1, 8));
    filesMatch(Lists.newArrayList("I"), appendsBetweenScan(6, 8));
    filesMatch(Lists.newArrayList("I"), appendsBetweenScan(7, 8));

    overwrite(table.newOverwrite(), files("H"), files("E")); // 9
    Assertions.assertThatThrownBy(() -> appendsBetweenScan(8, 9))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessage(
            "Found overwrite operation, cannot support incremental data in snapshots (8, 9]");
  }

  /**
   * 测试场景：transactions。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testTransactions() {
    Transaction transaction = table.newTransaction();

    add(transaction.newAppend(), files("A")); // 1
    add(transaction.newAppend(), files("B"));
    add(transaction.newAppend(), files("C"));
    add(transaction.newAppend(), files("D"));
    add(transaction.newAppend(), files("E")); // 5
    transaction.commitTransaction();
    filesMatch(Lists.newArrayList("B", "C", "D", "E"), appendsBetweenScan(1, 5));

    transaction = table.newTransaction();
    replace(transaction.newRewrite(), files("A", "B", "C"), files("F", "G")); // 6
    transaction.commitTransaction();
    // Replace commits are ignored
    filesMatch(Lists.newArrayList("B", "C", "D", "E"), appendsBetweenScan(1, 6));
    filesMatch(Lists.newArrayList("E"), appendsBetweenScan(4, 6));
    // 6th snapshot is a replace. No new content is added
    Assert.assertTrue("Replace commits are ignored", appendsBetweenScan(5, 6).isEmpty());

    transaction = table.newTransaction();
    delete(transaction.newDelete(), files("D")); // 7
    transaction.commitTransaction();
    // 7th snapshot is a delete.
    Assert.assertTrue("Replace and delete commits are ignored", appendsBetweenScan(5, 7).isEmpty());
    Assert.assertTrue("Delete commits are ignored", appendsBetweenScan(6, 7).isEmpty());

    transaction = table.newTransaction();
    add(transaction.newAppend(), files("I")); // 8
    transaction.commitTransaction();
    // snapshots 6, 7 and 8 are ignored
    filesMatch(Lists.newArrayList("B", "C", "D", "E", "I"), appendsBetweenScan(1, 8));
    filesMatch(Lists.newArrayList("I"), appendsBetweenScan(6, 8));
    filesMatch(Lists.newArrayList("I"), appendsBetweenScan(7, 8));
  }

  /**
   * 测试场景：rollbacks。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testRollbacks() {
    add(table.newAppend(), files("A")); // 1
    add(table.newAppend(), files("B"));
    add(table.newAppend(), files("C")); // 3
    // Go back to snapshot "B"
    table.manageSnapshots().rollbackTo(2).commit(); // 2
    Assert.assertEquals(2, table.currentSnapshot().snapshotId());
    filesMatch(Lists.newArrayList("B"), appendsBetweenScan(1, 2));
    filesMatch(Lists.newArrayList("B"), appendsAfterScan(1));

    Transaction transaction = table.newTransaction();
    add(transaction.newAppend(), files("D")); // 4
    add(transaction.newAppend(), files("E")); // 5
    add(transaction.newAppend(), files("F"));
    transaction.commitTransaction();
    // Go back to snapshot "E"
    table.manageSnapshots().rollbackTo(5).commit();
    Assert.assertEquals(5, table.currentSnapshot().snapshotId());
    filesMatch(Lists.newArrayList("B", "D", "E"), appendsBetweenScan(1, 5));
    filesMatch(Lists.newArrayList("B", "D", "E"), appendsAfterScan(1));
  }

  /**
   * 测试场景：ignore residuals。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testIgnoreResiduals() throws IOException {
    add(table.newAppend(), files("A"));
    add(table.newAppend(), files("B"));
    add(table.newAppend(), files("C"));

    TableScan scan1 = table.newScan().filter(Expressions.equal("id", 5)).appendsBetween(1, 3);

    try (CloseableIterable<CombinedScanTask> tasks = scan1.planTasks()) {
      Assert.assertTrue(
          "Tasks should not be empty", com.google.common.collect.Iterables.size(tasks) > 0);
      for (CombinedScanTask combinedScanTask : tasks) {
        for (FileScanTask fileScanTask : combinedScanTask.files()) {
          Assert.assertNotEquals(
              "Residuals must be preserved", Expressions.alwaysTrue(), fileScanTask.residual());
        }
      }
    }

    TableScan scan2 =
        table.newScan().filter(Expressions.equal("id", 5)).appendsBetween(1, 3).ignoreResiduals();

    try (CloseableIterable<CombinedScanTask> tasks = scan2.planTasks()) {
      Assert.assertTrue(
          "Tasks should not be empty", com.google.common.collect.Iterables.size(tasks) > 0);
      for (CombinedScanTask combinedScanTask : tasks) {
        for (FileScanTask fileScanTask : combinedScanTask.files()) {
          Assert.assertEquals(
              "Residuals must be ignored", Expressions.alwaysTrue(), fileScanTask.residual());
        }
      }
    }
  }

  /**
   * 测试场景：plan with executor。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testPlanWithExecutor() throws IOException {
    add(table.newAppend(), files("A"));
    add(table.newAppend(), files("B"));
    add(table.newAppend(), files("C"));

    AtomicInteger planThreadsIndex = new AtomicInteger(0);
    TableScan scan =
        table
            .newScan()
            .appendsAfter(1)
            .planWith(
                Executors.newFixedThreadPool(
                    1,
                    runnable -> {
                      Thread thread = new Thread(runnable);
                      thread.setName("plan-" + planThreadsIndex.getAndIncrement());
                      thread.setDaemon(
                          true); // daemon threads will be terminated abruptly when the JVM exits
                      return thread;
                    }));
    Assert.assertEquals(2, Iterables.size(scan.planFiles()));
    Assert.assertTrue("Thread should be created in provided pool", planThreadsIndex.get() > 0);
  }

  /** 辅助方法：file。 */
  private static DataFile file(String name) {
    return DataFiles.builder(SPEC)
        .withPath(name + ".parquet")
        .withFileSizeInBytes(10)
        .withPartitionPath("data_bucket=0") // easy way to set partition data for now
        .withRecordCount(1)
        .build();
  }

  /** 辅助方法：add。 */
  private static void add(AppendFiles appendFiles, List<DataFile> adds) {
    for (DataFile f : adds) {
      appendFiles.appendFile(f);
    }
    appendFiles.commit();
  }

  /** 辅助方法：delete。 */
  private static void delete(DeleteFiles deleteFiles, List<DataFile> deletes) {
    for (DataFile f : deletes) {
      deleteFiles.deleteFile(f);
    }
    deleteFiles.commit();
  }

  /** 辅助方法：replace。 */
  private static void replace(
      RewriteFiles rewriteFiles, List<DataFile> deletes, List<DataFile> adds) {
    rewriteFiles.rewriteFiles(Sets.newHashSet(deletes), Sets.newHashSet(adds));
    rewriteFiles.commit();
  }

  /** 辅助方法：overwrite。 */
  private static void overwrite(
      OverwriteFiles overwriteFiles, List<DataFile> adds, List<DataFile> deletes) {
    for (DataFile f : adds) {
      overwriteFiles.addFile(f);
    }
    for (DataFile f : deletes) {
      overwriteFiles.deleteFile(f);
    }
    overwriteFiles.commit();
  }

  /** 辅助方法：files。 */
  private static List<DataFile> files(String... names) {
    return Lists.transform(Lists.newArrayList(names), TestIncrementalDataTableScan::file);
  }

  /** 辅助方法：appends after scan。 */
  private List<String> appendsAfterScan(long fromSnapshotId) {
    final TableScan appendsAfter = table.newScan().appendsAfter(fromSnapshotId);
    return filesToScan(appendsAfter);
  }

  /** 辅助方法：appends between scan。 */
  private List<String> appendsBetweenScan(long fromSnapshotId, long toSnapshotId) {
    Snapshot s1 = table.snapshot(fromSnapshotId);
    Snapshot s2 = table.snapshot(toSnapshotId);
    TableScan appendsBetween = table.newScan().appendsBetween(s1.snapshotId(), s2.snapshotId());
    return filesToScan(appendsBetween);
  }

  /** 辅助方法：files to scan。 */
  private static List<String> filesToScan(TableScan tableScan) {
    Iterable<String> filesToRead =
        Iterables.transform(
            tableScan.planFiles(),
            t -> {
              String path = t.file().path().toString();
              return path.split("\\.")[0];
            });
    return Lists.newArrayList(filesToRead);
  }

  /** 辅助方法：files match。 */
  private static void filesMatch(List<String> expected, List<String> actual) {
    Collections.sort(expected);
    Collections.sort(actual);
    Assert.assertEquals(expected, actual);
  }
}
