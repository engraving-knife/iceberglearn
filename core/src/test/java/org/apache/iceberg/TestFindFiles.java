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
import java.util.Set;
import org.apache.iceberg.expressions.Expressions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.relocated.com.google.common.collect.Iterables;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.apache.iceberg.types.Conversions;
import org.apache.iceberg.types.Types;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * 测试类：TestFindFiles，用于验证 Find Files 相关功能。
 *
 * <p>所属模块：iceberg-core（测试目录 src/test）。 职责：针对 Find Files 的核心行为构造多种场景，覆盖正常路径、边界条件与异常输入， 确保实现与预期语义一致。
 *
 * <p>测试策略：基于 JUnit（必要时配合参数化执行器）搭建表/目录等测试基座， 通过构造输入、执行被测方法并断言结果或状态来验证功能点。
 */
@RunWith(Parameterized.class)
public class TestFindFiles extends TableTestBase {
  /** 辅助方法：parameters。 */
  @Parameterized.Parameters(name = "formatVersion = {0}")
  public static Object[] parameters() {
    return new Object[] {1, 2};
  }

  /** 辅助方法：find files。 */
  public TestFindFiles(int formatVersion) {
    super(formatVersion);
  }

  /**
   * 测试场景：basic behavior。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testBasicBehavior() {
    table.newAppend().appendFile(FILE_A).appendFile(FILE_B).commit();

    Iterable<DataFile> files = FindFiles.in(table).collect();

    Assert.assertEquals(pathSet(FILE_A, FILE_B), pathSet(files));
  }

  /**
   * 测试场景：with metadata matching。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testWithMetadataMatching() {
    table
        .newAppend()
        .appendFile(FILE_A)
        .appendFile(FILE_B)
        .appendFile(FILE_C)
        .appendFile(FILE_D)
        .commit();

    Iterable<DataFile> files =
        FindFiles.in(table)
            .withMetadataMatching(Expressions.startsWith("file_path", "/path/to/data-a"))
            .collect();

    Assert.assertEquals(pathSet(FILE_A), pathSet(files));
  }

  /**
   * 测试场景：with records matching。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testWithRecordsMatching() {
    table
        .newAppend()
        .appendFile(
            DataFiles.builder(SPEC)
                .withInputFile(Files.localInput("/path/to/data-e.parquet"))
                .withPartitionPath("data_bucket=4")
                .withMetrics(
                    new Metrics(
                        3L,
                        null, // no column sizes
                        ImmutableMap.of(1, 3L), // value count
                        ImmutableMap.of(1, 0L), // null count
                        null,
                        ImmutableMap.of(
                            1,
                            Conversions.toByteBuffer(Types.IntegerType.get(), 1)), // lower bounds
                        ImmutableMap.of(
                            1,
                            Conversions.toByteBuffer(Types.IntegerType.get(), 5)))) // lower bounds
                .build())
        .commit();

    final Iterable<DataFile> files =
        FindFiles.in(table).withRecordsMatching(Expressions.equal("id", 1)).collect();

    Assert.assertEquals(Sets.newHashSet("/path/to/data-e.parquet"), pathSet(files));
  }

  /**
   * 测试场景：in partition。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testInPartition() {
    table
        .newAppend()
        .appendFile(FILE_A) // bucket 0
        .appendFile(FILE_B) // bucket 1
        .appendFile(FILE_C) // bucket 2
        .appendFile(FILE_D) // bucket 3
        .commit();

    Iterable<DataFile> files =
        FindFiles.in(table)
            .inPartition(table.spec(), StaticDataTask.Row.of(1))
            .inPartition(table.spec(), StaticDataTask.Row.of(2))
            .collect();

    Assert.assertEquals(pathSet(FILE_B, FILE_C), pathSet(files));
  }

  /**
   * 测试场景：in partitions。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testInPartitions() {
    table
        .newAppend()
        .appendFile(FILE_A) // bucket 0
        .appendFile(FILE_B) // bucket 1
        .appendFile(FILE_C) // bucket 2
        .appendFile(FILE_D) // bucket 3
        .commit();

    Iterable<DataFile> files =
        FindFiles.in(table)
            .inPartitions(table.spec(), StaticDataTask.Row.of(1), StaticDataTask.Row.of(2))
            .collect();

    Assert.assertEquals(pathSet(FILE_B, FILE_C), pathSet(files));
  }

  /**
   * 测试场景：as of timestamp。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testAsOfTimestamp() {
    table.newAppend().appendFile(FILE_A).commit();

    table.newAppend().appendFile(FILE_B).commit();

    long timestamp = System.currentTimeMillis();

    table.newAppend().appendFile(FILE_C).commit();

    table.newAppend().appendFile(FILE_D).commit();

    Iterable<DataFile> files = FindFiles.in(table).asOfTime(timestamp).collect();

    Assert.assertEquals(pathSet(FILE_A, FILE_B), pathSet(files));
  }

  /**
   * 测试场景：snapshot id。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testSnapshotId() {
    table.newAppend().appendFile(FILE_A).appendFile(FILE_B).commit();

    table.newAppend().appendFile(FILE_C).commit();

    long snapshotId = table.currentSnapshot().snapshotId();

    table.newAppend().appendFile(FILE_D).commit();

    Iterable<DataFile> files = FindFiles.in(table).inSnapshot(snapshotId).collect();

    Assert.assertEquals(pathSet(FILE_A, FILE_B, FILE_C), pathSet(files));
  }

  /**
   * 测试场景：case sensitivity。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testCaseSensitivity() {
    table
        .newAppend()
        .appendFile(FILE_A)
        .appendFile(FILE_B)
        .appendFile(FILE_C)
        .appendFile(FILE_D)
        .commit();

    Iterable<DataFile> files =
        FindFiles.in(table)
            .caseInsensitive()
            .withMetadataMatching(Expressions.startsWith("FILE_PATH", "/path/to/data-a"))
            .collect();

    Assert.assertEquals(pathSet(FILE_A), pathSet(files));
  }

  /**
   * 测试场景：include column stats。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testIncludeColumnStats() {
    table.newAppend().appendFile(FILE_WITH_STATS).commit();

    Iterable<DataFile> files = FindFiles.in(table).includeColumnStats().collect();
    final DataFile file = files.iterator().next();

    Assert.assertEquals(FILE_WITH_STATS.columnSizes(), file.columnSizes());
    Assert.assertEquals(FILE_WITH_STATS.valueCounts(), file.valueCounts());
    Assert.assertEquals(FILE_WITH_STATS.nullValueCounts(), file.nullValueCounts());
    Assert.assertEquals(FILE_WITH_STATS.nanValueCounts(), file.nanValueCounts());
    Assert.assertEquals(FILE_WITH_STATS.lowerBounds(), file.lowerBounds());
    Assert.assertEquals(FILE_WITH_STATS.upperBounds(), file.upperBounds());
  }

  /**
   * 测试场景：no snapshot。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testNoSnapshot() {
    // a table has no snapshot when it just gets created and no data is loaded yet

    // if not handled properly, NPE will be thrown in collect()
    Iterable<DataFile> files = FindFiles.in(table).collect();

    // verify an empty collection of data file is returned
    Assert.assertEquals(0, Sets.newHashSet(files).size());
  }

  /** 辅助方法：path set。 */
  private Set<String> pathSet(DataFile... files) {
    return Sets.newHashSet(
        Iterables.transform(Arrays.asList(files), file -> file.path().toString()));
  }

  /** 辅助方法：path set。 */
  private Set<String> pathSet(Iterable<DataFile> files) {
    return Sets.newHashSet(Iterables.transform(files, file -> file.path().toString()));
  }
}
