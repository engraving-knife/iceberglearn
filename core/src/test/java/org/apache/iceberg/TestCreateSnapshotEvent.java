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

import org.apache.iceberg.events.CreateSnapshotEvent;
import org.apache.iceberg.events.Listener;
import org.apache.iceberg.events.Listeners;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * 测试类：TestCreateSnapshotEvent，用于验证 Create Snapshot Event 相关功能。
 *
 * <p>所属模块：iceberg-core（测试目录 src/test）。 职责：针对 Create Snapshot Event 的核心行为构造多种场景，覆盖正常路径、边界条件与异常输入，
 * 确保实现与预期语义一致。
 *
 * <p>测试策略：基于 JUnit（必要时配合参数化执行器）搭建表/目录等测试基座， 通过构造输入、执行被测方法并断言结果或状态来验证功能点。
 */
@RunWith(Parameterized.class)
public class TestCreateSnapshotEvent extends TableTestBase {
  /** 辅助方法：parameters。 */
  @Parameterized.Parameters(name = "formatVersion = {0}")
  public static Object[] parameters() {
    return new Object[] {1, 2};
  }

  private CreateSnapshotEvent currentEvent;

  /** 辅助方法：create snapshot event。 */
  public TestCreateSnapshotEvent(int formatVersion) {
    super(formatVersion);
    Listeners.register(new MyListener(), CreateSnapshotEvent.class);
  }

  /**
   * 测试场景：append commit event。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testAppendCommitEvent() {
    Assert.assertEquals("Table should start empty", 0, listManifestFiles().size());

    table.newAppend().appendFile(FILE_A).commit();
    Assert.assertNotNull(currentEvent);
    Assert.assertEquals(
        "Added records in the table should be 1", "1", currentEvent.summary().get("added-records"));
    Assert.assertEquals(
        "Added files in the table should be 1",
        "1",
        currentEvent.summary().get("added-data-files"));
    Assert.assertEquals(
        "Total records in the table should be 1", "1", currentEvent.summary().get("total-records"));
    Assert.assertEquals(
        "Total data files in the table should be 1",
        "1",
        currentEvent.summary().get("total-data-files"));

    table.newAppend().appendFile(FILE_A).commit();
    Assert.assertNotNull(currentEvent);
    Assert.assertEquals(
        "Added records in the table should be 1", "1", currentEvent.summary().get("added-records"));
    Assert.assertEquals(
        "Added files in the table should be 1",
        "1",
        currentEvent.summary().get("added-data-files"));
    Assert.assertEquals(
        "Total records in the table should be 2", "2", currentEvent.summary().get("total-records"));
    Assert.assertEquals(
        "Total data files in the table should be 2",
        "2",
        currentEvent.summary().get("total-data-files"));
  }

  /**
   * 测试场景：append and delete commit event。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testAppendAndDeleteCommitEvent() {
    Assert.assertEquals("Table should start empty", 0, listManifestFiles().size());

    table.newAppend().appendFile(FILE_A).appendFile(FILE_B).commit();
    Assert.assertNotNull(currentEvent);
    Assert.assertEquals(
        "Added records in the table should be 2", "2", currentEvent.summary().get("added-records"));
    Assert.assertEquals(
        "Added files in the table should be 2",
        "2",
        currentEvent.summary().get("added-data-files"));
    Assert.assertEquals(
        "Total records in the table should be 2", "2", currentEvent.summary().get("total-records"));
    Assert.assertEquals(
        "Total data files in the table should be 2",
        "2",
        currentEvent.summary().get("total-data-files"));

    table.newDelete().deleteFile(FILE_A).commit();
    Assert.assertNotNull(currentEvent);
    Assert.assertEquals(
        "Deleted records in the table should be 1",
        "1",
        currentEvent.summary().get("deleted-records"));
    Assert.assertEquals(
        "Deleted files in the table should be 1",
        "1",
        currentEvent.summary().get("deleted-data-files"));
    Assert.assertEquals(
        "Total records in the table should be 1", "1", currentEvent.summary().get("total-records"));
    Assert.assertEquals(
        "Total data files in the table should be 1",
        "1",
        currentEvent.summary().get("total-data-files"));
  }

  class MyListener implements Listener<CreateSnapshotEvent> {
    /** 辅助方法：notify。 */
    @Override
    public void notify(CreateSnapshotEvent event) {
      currentEvent = event;
    }
  }
}
