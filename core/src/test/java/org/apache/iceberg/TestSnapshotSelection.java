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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.relocated.com.google.common.collect.Iterables;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * 测试类：TestSnapshotSelection，用于验证 Snapshot Selection 相关功能。
 *
 * <p>所属模块：iceberg-core（测试目录 src/test）。 职责：针对 Snapshot Selection 的核心行为构造多种场景，覆盖正常路径、边界条件与异常输入，
 * 确保实现与预期语义一致。
 *
 * <p>测试策略：基于 JUnit（必要时配合参数化执行器）搭建表/目录等测试基座， 通过构造输入、执行被测方法并断言结果或状态来验证功能点。
 */
@RunWith(Parameterized.class)
public class TestSnapshotSelection extends TableTestBase {
  /** 辅助方法：parameters。 */
  @Parameterized.Parameters(name = "formatVersion = {0}")
  public static Object[] parameters() {
    return new Object[] {1, 2};
  }

  /** 辅助方法：snapshot selection。 */
  public TestSnapshotSelection(int formatVersion) {
    super(formatVersion);
  }

  /**
   * 测试场景：snapshot selection by id。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testSnapshotSelectionById() {
    Assert.assertEquals("Table should start empty", 0, listManifestFiles().size());

    table.newFastAppend().appendFile(FILE_A).commit();
    Snapshot firstSnapshot = table.currentSnapshot();

    table.newFastAppend().appendFile(FILE_B).commit();
    Snapshot secondSnapshot = table.currentSnapshot();

    Assert.assertEquals("Table should have two snapshots", 2, Iterables.size(table.snapshots()));
    validateSnapshot(null, table.snapshot(firstSnapshot.snapshotId()), FILE_A);
    validateSnapshot(firstSnapshot, table.snapshot(secondSnapshot.snapshotId()), FILE_B);
  }

  /**
   * 测试场景：snapshot stats for added files。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testSnapshotStatsForAddedFiles() {
    DataFile fileWithStats =
        DataFiles.builder(SPEC)
            .withPath("/path/to/data-with-stats.parquet")
            .withFileSizeInBytes(10)
            .withPartitionPath("data_bucket=0")
            .withRecordCount(10)
            .withMetrics(
                new Metrics(
                    3L,
                    null, // no column sizes
                    ImmutableMap.of(1, 3L), // value count
                    ImmutableMap.of(1, 0L), // null count
                    null,
                    ImmutableMap.of(1, longToBuffer(20L)), // lower bounds
                    ImmutableMap.of(1, longToBuffer(22L)))) // upper bounds
            .build();

    table.newFastAppend().appendFile(fileWithStats).commit();

    Snapshot snapshot = table.currentSnapshot();
    Iterable<DataFile> addedFiles = snapshot.addedDataFiles(table.io());
    Assert.assertEquals(1, Iterables.size(addedFiles));
    DataFile dataFile = Iterables.getOnlyElement(addedFiles);
    Assert.assertNotNull("Value counts should be not null", dataFile.valueCounts());
    Assert.assertNotNull("Null value counts should be not null", dataFile.nullValueCounts());
    Assert.assertNotNull("Lower bounds should be not null", dataFile.lowerBounds());
    Assert.assertNotNull("Upper bounds should be not null", dataFile.upperBounds());
  }

  /** 辅助方法：long to buffer。 */
  private ByteBuffer longToBuffer(long value) {
    return ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(0, value);
  }
}
