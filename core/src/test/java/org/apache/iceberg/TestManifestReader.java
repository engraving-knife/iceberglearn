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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.iceberg.ManifestEntry.Status;
import org.apache.iceberg.expressions.Expressions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.Iterables;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.relocated.com.google.common.collect.Streams;
import org.apache.iceberg.types.Types;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * 测试类：TestManifestReader，用于验证 Manifest Reader 相关功能。
 *
 * <p>所属模块：iceberg-core（测试目录 src/test）。 职责：针对 Manifest Reader 的核心行为构造多种场景，覆盖正常路径、边界条件与异常输入，
 * 确保实现与预期语义一致。
 *
 * <p>测试策略：基于 JUnit（必要时配合参数化执行器）搭建表/目录等测试基座， 通过构造输入、执行被测方法并断言结果或状态来验证功能点。
 */
@RunWith(Parameterized.class)
public class TestManifestReader extends TableTestBase {
  /** 辅助方法：parameters。 */
  @Parameterized.Parameters(name = "formatVersion = {0}")
  public static Object[] parameters() {
    return new Object[] {1, 2};
  }

  private static final RecursiveComparisonConfiguration FILE_COMPARISON_CONFIG =
      RecursiveComparisonConfiguration.builder()
          .withIgnoredFields(
              "dataSequenceNumber", "fileOrdinal", "fileSequenceNumber", "fromProjectionPos")
          .build();

  /** 辅助方法：manifest reader。 */
  public TestManifestReader(int formatVersion) {
    super(formatVersion);
  }

  /**
   * 测试场景：manifest reader with empty inheritable metadata。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testManifestReaderWithEmptyInheritableMetadata() throws IOException {
    ManifestFile manifest = writeManifest(1000L, manifestEntry(Status.EXISTING, 1000L, FILE_A));
    try (ManifestReader<DataFile> reader = ManifestFiles.read(manifest, FILE_IO)) {
      ManifestEntry<DataFile> entry = Iterables.getOnlyElement(reader.entries());
      Assert.assertEquals(Status.EXISTING, entry.status());
      Assert.assertEquals(FILE_A.path(), entry.file().path());
      Assert.assertEquals(1000L, (long) entry.snapshotId());
    }
  }

  /**
   * 测试场景：reader with filter without select。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testReaderWithFilterWithoutSelect() throws IOException {
    ManifestFile manifest = writeManifest(1000L, FILE_A, FILE_B, FILE_C);
    try (ManifestReader<DataFile> reader =
        ManifestFiles.read(manifest, FILE_IO).filterRows(Expressions.equal("id", 0))) {
      List<DataFile> files = Streams.stream(reader).collect(Collectors.toList());

      // note that all files are returned because the reader returns data files that may match, and
      // the partition is
      // bucketing by data, which doesn't help filter files
      assertThat(files)
          .usingRecursiveComparison(FILE_COMPARISON_CONFIG)
          .isEqualTo(Lists.newArrayList(FILE_A, FILE_B, FILE_C));
    }
  }

  /**
   * 测试场景：invalid usage。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testInvalidUsage() throws IOException {
    ManifestFile manifest = writeManifest(FILE_A, FILE_B);
    Assertions.assertThatThrownBy(() -> ManifestFiles.read(manifest, FILE_IO))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot read from ManifestFile with null (unassigned) snapshot ID");
  }

  /**
   * 测试场景：manifest reader with partition metadata。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testManifestReaderWithPartitionMetadata() throws IOException {
    ManifestFile manifest = writeManifest(1000L, manifestEntry(Status.EXISTING, 123L, FILE_A));
    try (ManifestReader<DataFile> reader = ManifestFiles.read(manifest, FILE_IO)) {
      ManifestEntry<DataFile> entry = Iterables.getOnlyElement(reader.entries());
      Assert.assertEquals(123L, (long) entry.snapshotId());

      List<Types.NestedField> fields =
          ((PartitionData) entry.file().partition()).getPartitionType().fields();
      Assert.assertEquals(1, fields.size());
      Assert.assertEquals(1000, fields.get(0).fieldId());
      Assert.assertEquals("data_bucket", fields.get(0).name());
      Assert.assertEquals(Types.IntegerType.get(), fields.get(0).type());
    }
  }

  /**
   * 测试场景：manifest reader with updated partition metadata for 1 table。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testManifestReaderWithUpdatedPartitionMetadataForV1Table() throws IOException {
    PartitionSpec spec =
        PartitionSpec.builderFor(table.schema()).bucket("id", 8).bucket("data", 16).build();
    table.ops().commit(table.ops().current(), table.ops().current().updatePartitionSpec(spec));

    ManifestFile manifest = writeManifest(1000L, manifestEntry(Status.EXISTING, 123L, FILE_A));
    try (ManifestReader<DataFile> reader = ManifestFiles.read(manifest, FILE_IO)) {
      ManifestEntry<DataFile> entry = Iterables.getOnlyElement(reader.entries());
      Assert.assertEquals(123L, (long) entry.snapshotId());

      List<Types.NestedField> fields =
          ((PartitionData) entry.file().partition()).getPartitionType().fields();
      Assert.assertEquals(2, fields.size());
      Assert.assertEquals(1000, fields.get(0).fieldId());
      Assert.assertEquals("id_bucket", fields.get(0).name());
      Assert.assertEquals(Types.IntegerType.get(), fields.get(0).type());

      Assert.assertEquals(1001, fields.get(1).fieldId());
      Assert.assertEquals("data_bucket", fields.get(1).name());
      Assert.assertEquals(Types.IntegerType.get(), fields.get(1).type());
    }
  }

  /**
   * 测试场景：data file positions。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testDataFilePositions() throws IOException {
    ManifestFile manifest = writeManifest(1000L, FILE_A, FILE_B, FILE_C);
    try (ManifestReader<DataFile> reader = ManifestFiles.read(manifest, FILE_IO)) {
      long expectedPos = 0L;
      for (DataFile file : reader) {
        Assert.assertEquals("Position should match", (Long) expectedPos, file.pos());
        Assert.assertEquals(
            "Position from field index should match", expectedPos, ((BaseFile) file).get(17));
        expectedPos += 1;
      }
    }
  }

  /**
   * 测试场景：delete file positions。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testDeleteFilePositions() throws IOException {
    Assume.assumeTrue("Delete files only work for format version 2", formatVersion == 2);
    ManifestFile manifest =
        writeDeleteManifest(formatVersion, 1000L, FILE_A_DELETES, FILE_B_DELETES);
    try (ManifestReader<DeleteFile> reader =
        ManifestFiles.readDeleteManifest(manifest, FILE_IO, null)) {
      long expectedPos = 0L;
      for (DeleteFile file : reader) {
        Assert.assertEquals("Position should match", (Long) expectedPos, file.pos());
        Assert.assertEquals(
            "Position from field index should match", expectedPos, ((BaseFile) file).get(17));
        expectedPos += 1;
      }
    }
  }

  /**
   * 测试场景：data file split offsets null when invalid。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testDataFileSplitOffsetsNullWhenInvalid() throws IOException {
    DataFile invalidOffset =
        DataFiles.builder(SPEC)
            .withPath("/path/to/invalid-offsets.parquet")
            .withFileSizeInBytes(10)
            .withRecordCount(1)
            .withSplitOffsets(ImmutableList.of(2L, 1000L)) // Offset 1000 is out of bounds
            .build();
    ManifestFile manifest = writeManifest(1000L, invalidOffset);
    try (ManifestReader<DataFile> reader = ManifestFiles.read(manifest, FILE_IO)) {
      DataFile file = Iterables.getOnlyElement(reader);
      Assertions.assertThat(file.splitOffsets()).isNull();
    }
  }
}
