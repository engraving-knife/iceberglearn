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
package org.apache.iceberg.util;

import static org.apache.iceberg.types.Types.NestedField.optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.List;
import java.util.Set;
import org.apache.hadoop.conf.Configuration;
import org.apache.iceberg.BaseTable;
import org.apache.iceberg.DataFile;
import org.apache.iceberg.DataFiles;
import org.apache.iceberg.HasTableOperations;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.ReachableFileUtil;
import org.apache.iceberg.Schema;
import org.apache.iceberg.StaticTableOperations;
import org.apache.iceberg.Table;
import org.apache.iceberg.TableMetadata;
import org.apache.iceberg.TableOperations;
import org.apache.iceberg.TableProperties;
import org.apache.iceberg.hadoop.HadoopTables;
import org.apache.iceberg.hadoop.Util;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.types.Types;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 测试类：TestReachableFileUtil，用于验证 Reachable File Util 相关功能。
 *
 * <p>所属模块：iceberg-core（测试目录 src/test）。 职责：针对 Reachable File Util 的核心行为构造多种场景，覆盖正常路径、边界条件与异常输入，
 * 确保实现与预期语义一致。
 *
 * <p>测试策略：基于 JUnit（必要时配合参数化执行器）搭建表/目录等测试基座， 通过构造输入、执行被测方法并断言结果或状态来验证功能点。
 */
public class TestReachableFileUtil {
  private static final HadoopTables TABLES = new HadoopTables(new Configuration());
  private static final Schema SCHEMA =
      new Schema(
          optional(1, "c1", Types.IntegerType.get()), optional(2, "c2", Types.StringType.get()));

  private static final PartitionSpec SPEC = PartitionSpec.builderFor(SCHEMA).identity("c1").build();

  private static final DataFile FILE_A =
      DataFiles.builder(SPEC)
          .withPath("/path/to/data-a.parquet")
          .withFileSizeInBytes(10)
          .withRecordCount(1)
          .build();
  private static final DataFile FILE_B =
      DataFiles.builder(SPEC)
          .withPath("/path/to/data-b.parquet")
          .withFileSizeInBytes(10)
          .withRecordCount(1)
          .build();
  @TempDir private File tableDir;
  private Table table;

  /** 辅助方法：setup table location。 */
  @BeforeEach
  public void setupTableLocation() throws Exception {
    String tableLocation = tableDir.toURI().toString();
    this.table = TABLES.create(SCHEMA, SPEC, Maps.newHashMap(), tableLocation);
  }

  /**
   * 测试场景：manifest list locations。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testManifestListLocations() {
    table.newAppend().appendFile(FILE_A).commit();

    table.newAppend().appendFile(FILE_B).commit();

    List<String> manifestListPaths = ReachableFileUtil.manifestListLocations(table);
    assertThat(manifestListPaths).hasSize(2);
  }

  /**
   * 测试场景：metadata file locations。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testMetadataFileLocations() {
    table.updateProperties().set(TableProperties.METADATA_PREVIOUS_VERSIONS_MAX, "1").commit();

    table.newAppend().appendFile(FILE_A).commit();

    table.newAppend().appendFile(FILE_B).commit();

    Set<String> metadataFileLocations = ReachableFileUtil.metadataFileLocations(table, true);
    assertThat(metadataFileLocations).hasSize(4);

    metadataFileLocations = ReachableFileUtil.metadataFileLocations(table, false);
    assertThat(metadataFileLocations).hasSize(2);
  }

  /**
   * 测试场景：metadata file locations with missing files。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testMetadataFileLocationsWithMissingFiles() {
    table.updateProperties().set(TableProperties.METADATA_PREVIOUS_VERSIONS_MAX, "1").commit();

    table.newAppend().appendFile(FILE_A).commit();

    TableOperations operations = ((HasTableOperations) table).operations();
    String location = operations.current().metadataFileLocation();
    table.newAppend().appendFile(FILE_B).commit();

    // delete v3.metadata.json making v2.metadata.json and v1.metadata.json inaccessible
    table.io().deleteFile(location);

    Set<String> metadataFileLocations = ReachableFileUtil.metadataFileLocations(table, true);
    assertThat(metadataFileLocations).hasSize(2);
  }

  /**
   * 测试场景：version hint with static tables。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testVersionHintWithStaticTables() {
    TableOperations ops = ((HasTableOperations) table).operations();
    TableMetadata metadata = ops.current();
    String metadataFileLocation = metadata.metadataFileLocation();

    StaticTableOperations staticOps = new StaticTableOperations(metadataFileLocation, table.io());
    Table staticTable = new BaseTable(staticOps, metadataFileLocation);

    String reportedVersionHintLocation = ReachableFileUtil.versionHintLocation(staticTable);
    String expectedVersionHintLocation = ops.metadataFileLocation(Util.VERSION_HINT_FILENAME);
    assertThat(reportedVersionHintLocation).isEqualTo(expectedVersionHintLocation);
  }

  /**
   * 测试场景：version hint with bucket name as location。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testVersionHintWithBucketNameAsLocation() {
    Table mockTable = mock(Table.class);
    when(mockTable.location()).thenReturn("s3://bucket1");
    String reportedVersionHintLocation = ReachableFileUtil.versionHintLocation(mockTable);
    String expectedVersionHintLocation = "s3://bucket1/metadata/" + Util.VERSION_HINT_FILENAME;
    assertThat(reportedVersionHintLocation).isEqualTo(expectedVersionHintLocation);
  }
}
