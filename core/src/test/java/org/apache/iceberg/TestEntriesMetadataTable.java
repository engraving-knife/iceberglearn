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

import static org.junit.Assert.assertEquals;

import java.util.List;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.Iterables;
import org.apache.iceberg.types.TypeUtil;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * 测试类：TestEntriesMetadataTable，用于验证 Entries Metadata Table 相关功能。
 *
 * <p>所属模块：iceberg-core（测试目录 src/test）。 职责：针对 Entries Metadata Table 的核心行为构造多种场景，覆盖正常路径、边界条件与异常输入，
 * 确保实现与预期语义一致。
 *
 * <p>测试策略：基于 JUnit（必要时配合参数化执行器）搭建表/目录等测试基座， 通过构造输入、执行被测方法并断言结果或状态来验证功能点。
 */
@RunWith(Parameterized.class)
public class TestEntriesMetadataTable extends TableTestBase {
  /** 辅助方法：parameters。 */
  @Parameterized.Parameters(name = "formatVersion = {0}")
  public static Object[] parameters() {
    return new Object[] {1, 2};
  }

  /** 辅助方法：entries metadata table。 */
  public TestEntriesMetadataTable(int formatVersion) {
    super(formatVersion);
  }

  /**
   * 测试场景：entries table。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testEntriesTable() {
    table.newAppend().appendFile(FILE_A).appendFile(FILE_B).commit();

    Table entriesTable = new ManifestEntriesTable(table);

    Schema readSchema = ManifestEntry.getSchema(table.spec().partitionType());
    Schema expectedSchema =
        TypeUtil.join(readSchema, MetricsUtil.readableMetricsSchema(table.schema(), readSchema));

    assertEquals(
        "A tableScan.select() should prune the schema",
        expectedSchema.asStruct(),
        entriesTable.schema().asStruct());
  }

  /**
   * 测试场景：entries table scan。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testEntriesTableScan() {
    table.newAppend().appendFile(FILE_A).appendFile(FILE_B).commit();

    Table entriesTable = new ManifestEntriesTable(table);
    TableScan scan = entriesTable.newScan();

    Schema readSchema = ManifestEntry.getSchema(table.spec().partitionType());
    Schema expectedSchema =
        TypeUtil.join(readSchema, MetricsUtil.readableMetricsSchema(table.schema(), readSchema));

    assertEquals(
        "A tableScan.select() should prune the schema",
        expectedSchema.asStruct(),
        scan.schema().asStruct());

    FileScanTask file = Iterables.getOnlyElement(scan.planFiles());
    Assert.assertEquals(
        "Data file should be the table's manifest",
        Iterables.getOnlyElement(table.currentSnapshot().allManifests(table.io())).path(),
        file.file().path());
    Assert.assertEquals("Should contain 2 data file records", 2, file.file().recordCount());
  }

  /**
   * 测试场景：split planning with metadata split size property。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testSplitPlanningWithMetadataSplitSizeProperty() {
    table.newAppend().appendFile(FILE_A).appendFile(FILE_B).commit();

    table.newAppend().appendFile(FILE_C).appendFile(FILE_D).commit();

    // set the split size to a large value so that both manifests are in 1 split
    table
        .updateProperties()
        .set(TableProperties.METADATA_SPLIT_SIZE, String.valueOf(128 * 1024 * 1024))
        .commit();

    Table entriesTable = new ManifestEntriesTable(table);

    Assert.assertEquals(1, Iterables.size(entriesTable.newScan().planTasks()));

    // set the split size to a small value so that manifests end up in different splits
    table.updateProperties().set(TableProperties.METADATA_SPLIT_SIZE, String.valueOf(1)).commit();

    Assert.assertEquals(2, Iterables.size(entriesTable.newScan().planTasks()));

    // override the table property with a large value so that both manifests are in 1 split
    TableScan scan =
        entriesTable
            .newScan()
            .option(TableProperties.SPLIT_SIZE, String.valueOf(128 * 1024 * 1024));

    Assert.assertEquals(1, Iterables.size(scan.planTasks()));
  }

  /**
   * 测试场景：split planning with default metadata split size。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testSplitPlanningWithDefaultMetadataSplitSize() {
    table.newAppend().appendFile(FILE_A).appendFile(FILE_B).commit();

    int splitSize =
        (int) TableProperties.METADATA_SPLIT_SIZE_DEFAULT; // default split size is 32 MB

    Table entriesTable = new ManifestEntriesTable(table);
    Assert.assertEquals(1, entriesTable.currentSnapshot().allManifests(table.io()).size());

    int expectedSplits =
        ((int) entriesTable.currentSnapshot().allManifests(table.io()).get(0).length()
                + splitSize
                - 1)
            / splitSize;

    TableScan scan = entriesTable.newScan();

    Assert.assertEquals(expectedSplits, Iterables.size(scan.planTasks()));
  }

  /**
   * 测试场景：entries table with delete manifests。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testEntriesTableWithDeleteManifests() {
    Assume.assumeTrue("Only V2 Tables Support Deletes", formatVersion >= 2);
    table.newAppend().appendFile(FILE_A).appendFile(FILE_B).commit();

    table.newRowDelta().addDeletes(FILE_A_DELETES).commit();

    Table entriesTable = new ManifestEntriesTable(table);
    TableScan scan = entriesTable.newScan();

    Schema readSchema = ManifestEntry.getSchema(table.spec().partitionType());
    Schema expectedSchema =
        TypeUtil.join(readSchema, MetricsUtil.readableMetricsSchema(table.schema(), readSchema));

    assertEquals(
        "A tableScan.select() should prune the schema",
        expectedSchema.asStruct(),
        scan.schema().asStruct());

    List<FileScanTask> files = ImmutableList.copyOf(scan.planFiles());
    Assert.assertEquals(
        "Data file should be the table's manifest",
        Iterables.getOnlyElement(table.currentSnapshot().dataManifests(table.io())).path(),
        files.get(0).file().path());
    Assert.assertEquals("Should contain 2 data file records", 2, files.get(0).file().recordCount());
    Assert.assertEquals(
        "Delete file should be in the table manifest",
        Iterables.getOnlyElement(table.currentSnapshot().deleteManifests(table.io())).path(),
        files.get(1).file().path());
    Assert.assertEquals(
        "Should contain 1 delete file record", 1, files.get(1).file().recordCount());
  }
}
