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
import java.util.stream.Collectors;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * 测试类：TestBatchScans，用于验证 Batch Scans 相关功能。
 *
 * <p>所属模块：iceberg-core（测试目录 src/test）。 职责：针对 Batch Scans 的核心行为构造多种场景，覆盖正常路径、边界条件与异常输入， 确保实现与预期语义一致。
 *
 * <p>测试策略：基于 JUnit（必要时配合参数化执行器）搭建表/目录等测试基座， 通过构造输入、执行被测方法并断言结果或状态来验证功能点。
 */
@RunWith(Parameterized.class)
public class TestBatchScans extends TableTestBase {

  /** 辅助方法：parameters。 */
  @Parameterized.Parameters(name = "formatVersion = {0}")
  public static Object[] parameters() {
    return new Object[] {1, 2};
  }

  /** 辅助方法：batch scans。 */
  public TestBatchScans(int formatVersion) {
    super(formatVersion);
  }

  /**
   * 测试场景：data table scan。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testDataTableScan() {
    table.newFastAppend().appendFile(FILE_A).appendFile(FILE_B).commit();

    if (formatVersion > 1) {
      table.newRowDelta().addDeletes(FILE_A_DELETES).commit();
    }

    BatchScan scan = table.newBatchScan();

    List<ScanTask> tasks = planTasks(scan);
    Assert.assertEquals("Expected 2 tasks", 2, tasks.size());

    FileScanTask t1 = tasks.get(0).asFileScanTask();
    Assert.assertEquals("Task file must match", t1.file().path(), FILE_A.path());
    V1Assert.assertEquals("Task deletes size must match", 0, t1.deletes().size());
    V2Assert.assertEquals("Task deletes size must match", 1, t1.deletes().size());

    FileScanTask t2 = tasks.get(1).asFileScanTask();
    Assert.assertEquals("Task file must match", t2.file().path(), FILE_B.path());
    Assert.assertEquals("Task deletes size must match", 0, t2.deletes().size());

    List<ScanTaskGroup<ScanTask>> taskGroups = planTaskGroups(scan);
    Assert.assertEquals("Expected 1 task group", 1, taskGroups.size());

    ScanTaskGroup<ScanTask> tg = taskGroups.get(0);
    Assert.assertEquals("Task number must match", 2, tg.tasks().size());
    V1Assert.assertEquals("Files count must match", 2, tg.filesCount());
    V2Assert.assertEquals("Files count must match", 3, tg.filesCount());
  }

  /**
   * 测试场景：files table scan。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testFilesTableScan() {
    table.newFastAppend().appendFile(FILE_A).commit();
    table.newFastAppend().appendFile(FILE_B).commit();

    List<String> manifestPaths =
        table.currentSnapshot().dataManifests(table.io()).stream()
            .map(ManifestFile::path)
            .sorted()
            .collect(Collectors.toList());
    Assert.assertEquals("Must have 2 manifests", 2, manifestPaths.size());

    FilesTable filesTable = new FilesTable(table);

    BatchScan scan = filesTable.newBatchScan();

    List<ScanTask> tasks = planTasks(scan);
    Assert.assertEquals("Expected 2 tasks", 2, tasks.size());

    FileScanTask t1 = tasks.get(0).asFileScanTask();
    Assert.assertEquals("Task file must match", t1.file().path(), manifestPaths.get(0));

    FileScanTask t2 = tasks.get(1).asFileScanTask();
    Assert.assertEquals("Task file must match", t2.file().path(), manifestPaths.get(1));

    List<ScanTaskGroup<ScanTask>> taskGroups = planTaskGroups(scan);
    Assert.assertEquals("Expected 1 task group", 1, taskGroups.size());

    ScanTaskGroup<ScanTask> tg = taskGroups.get(0);
    Assert.assertEquals("Task number must match", 2, tg.tasks().size());
  }

  // plans tasks and reorders them by file name to have deterministic order
  private List<ScanTask> planTasks(BatchScan scan) {
    try (CloseableIterable<ScanTask> tasks = scan.planFiles()) {
      List<ScanTask> tasksAsList = Lists.newArrayList(tasks);
      tasksAsList.sort((t1, t2) -> path(t1).compareTo(path(t2)));
      return tasksAsList;

    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /** 辅助方法：plan task groups。 */
  private List<ScanTaskGroup<ScanTask>> planTaskGroups(BatchScan scan) {
    try (CloseableIterable<ScanTaskGroup<ScanTask>> taskGroups = scan.planTasks()) {
      return Lists.newArrayList(taskGroups);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /** 辅助方法：path。 */
  private String path(ScanTask task) {
    return ((ContentScanTask<?>) task).file().path().toString();
  }
}
