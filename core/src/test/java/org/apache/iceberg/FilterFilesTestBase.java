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

import static org.apache.iceberg.types.Types.NestedField.required;
import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;
import org.apache.iceberg.expressions.Expressions;
import org.apache.iceberg.relocated.com.google.common.collect.Iterables;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.types.Conversions;
import org.apache.iceberg.types.Types;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * 测试类：FilterFilesTestBase，用于验证 Filter Files 相关功能。
 *
 * <p>所属模块：iceberg-core（测试目录 src/test）。 职责：针对 Filter Files 的核心行为构造多种场景，覆盖正常路径、边界条件与异常输入，
 * 确保实现与预期语义一致。
 *
 * <p>测试策略：基于 JUnit（必要时配合参数化执行器）搭建表/目录等测试基座， 通过构造输入、执行被测方法并断言结果或状态来验证功能点。
 */
public abstract class FilterFilesTestBase<
    ScanT extends Scan<ScanT, T, G>, T extends ScanTask, G extends ScanTaskGroup<T>> {

  public final int formatVersion;

  /** 辅助方法：filter files test base。 */
  public FilterFilesTestBase(int formatVersion) {
    this.formatVersion = formatVersion;
  }

  /** 辅助方法：new scan。 */
  protected abstract ScanT newScan(Table table);

  @Rule public TemporaryFolder temp = new TemporaryFolder();
  private final Schema schema =
      new Schema(
          required(1, "id", Types.IntegerType.get()), required(2, "data", Types.StringType.get()));
  private File tableDir = null;

  /** 辅助方法：setup table dir。 */
  @Before
  public void setupTableDir() throws IOException {
    this.tableDir = temp.newFolder();
  }

  /** 辅助方法：cleanup tables。 */
  @After
  public void cleanupTables() {
    TestTables.clearTables();
  }

  /**
   * 测试场景：filter files unpartitioned table。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testFilterFilesUnpartitionedTable() {
    PartitionSpec spec = PartitionSpec.unpartitioned();
    Table table = TestTables.create(tableDir, "test", schema, spec, formatVersion);
    testFilterFiles(table);
  }

  /**
   * 测试场景：case insensitive filter files unpartitioned table。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testCaseInsensitiveFilterFilesUnpartitionedTable() {
    PartitionSpec spec = PartitionSpec.unpartitioned();
    Table table = TestTables.create(tableDir, "test", schema, spec, formatVersion);
    testCaseInsensitiveFilterFiles(table);
  }

  /**
   * 测试场景：filter files partitioned table。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testFilterFilesPartitionedTable() {
    PartitionSpec spec = PartitionSpec.builderFor(schema).bucket("data", 16).build();
    Table table = TestTables.create(tableDir, "test", schema, spec, formatVersion);
    testFilterFiles(table);
  }

  /**
   * 测试场景：case insensitive filter files partitioned table。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testCaseInsensitiveFilterFilesPartitionedTable() {
    PartitionSpec spec = PartitionSpec.builderFor(schema).bucket("data", 16).build();
    Table table = TestTables.create(tableDir, "test", schema, spec, formatVersion);
    testCaseInsensitiveFilterFiles(table);
  }

  /**
   * 测试场景：filter files。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  private void testFilterFiles(Table table) {
    Map<Integer, ByteBuffer> lowerBounds = Maps.newHashMap();
    Map<Integer, ByteBuffer> upperBounds = Maps.newHashMap();
    lowerBounds.put(1, Conversions.toByteBuffer(Types.IntegerType.get(), 1));
    upperBounds.put(1, Conversions.toByteBuffer(Types.IntegerType.get(), 2));

    Metrics metrics =
        new Metrics(
            2L,
            Maps.newHashMap(),
            Maps.newHashMap(),
            Maps.newHashMap(),
            null,
            lowerBounds,
            upperBounds);

    DataFile file =
        DataFiles.builder(table.spec())
            .withPath("/path/to/file.parquet")
            .withFileSizeInBytes(0)
            .withMetrics(metrics)
            .build();

    table.newAppend().appendFile(file).commit();

    table.refresh();

    ScanT emptyScan = newScan(table).filter(Expressions.equal("id", 5));
    assertEquals(0, Iterables.size(emptyScan.planFiles()));

    ScanT nonEmptyScan = newScan(table).filter(Expressions.equal("id", 1));
    assertEquals(1, Iterables.size(nonEmptyScan.planFiles()));
  }

  /**
   * 测试场景：case insensitive filter files。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  private void testCaseInsensitiveFilterFiles(Table table) {
    Map<Integer, ByteBuffer> lowerBounds = Maps.newHashMap();
    Map<Integer, ByteBuffer> upperBounds = Maps.newHashMap();
    lowerBounds.put(1, Conversions.toByteBuffer(Types.IntegerType.get(), 1));
    upperBounds.put(1, Conversions.toByteBuffer(Types.IntegerType.get(), 2));

    Metrics metrics =
        new Metrics(
            2L,
            Maps.newHashMap(),
            Maps.newHashMap(),
            Maps.newHashMap(),
            null,
            lowerBounds,
            upperBounds);

    DataFile file =
        DataFiles.builder(table.spec())
            .withPath("/path/to/file.parquet")
            .withFileSizeInBytes(0)
            .withMetrics(metrics)
            .build();

    table.newAppend().appendFile(file).commit();

    table.refresh();

    ScanT emptyScan = newScan(table).caseSensitive(false).filter(Expressions.equal("ID", 5));
    assertEquals(0, Iterables.size(emptyScan.planFiles()));

    ScanT nonEmptyScan = newScan(table).caseSensitive(false).filter(Expressions.equal("ID", 1));
    assertEquals(1, Iterables.size(nonEmptyScan.planFiles()));
  }
}
