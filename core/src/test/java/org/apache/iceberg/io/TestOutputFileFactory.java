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
package org.apache.iceberg.io;

import org.apache.iceberg.FileFormat;
import org.apache.iceberg.PartitionKey;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.TableTestBase;
import org.apache.iceberg.data.GenericRecord;
import org.apache.iceberg.data.Record;
import org.apache.iceberg.encryption.EncryptedOutputFile;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.assertj.core.api.Assertions;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * 测试类：TestOutputFileFactory，用于验证 Output File Factory 相关功能。
 *
 * <p>所属模块：iceberg-core（测试目录 src/test）。 职责：针对 Output File Factory 的核心行为构造多种场景，覆盖正常路径、边界条件与异常输入，
 * 确保实现与预期语义一致。
 *
 * <p>测试策略：基于 JUnit（必要时配合参数化执行器）搭建表/目录等测试基座， 通过构造输入、执行被测方法并断言结果或状态来验证功能点。
 */
@RunWith(Parameterized.class)
public class TestOutputFileFactory extends TableTestBase {

  /** 辅助方法：parameters。 */
  @Parameterized.Parameters(name = "formatVersion = {0}")
  public static Object[] parameters() {
    return new Object[] {1, 2};
  }

  private static final int PARTITION_ID = 1;
  private static final int TASK_ID = 100;

  /** 辅助方法：output file factory。 */
  public TestOutputFileFactory(int formatVersion) {
    super(formatVersion);
  }

  /**
   * 测试场景：output file factory with custom format。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testOutputFileFactoryWithCustomFormat() {
    table.updateProperties().defaultFormat(FileFormat.ORC).commit();

    OutputFileFactory fileFactory =
        OutputFileFactory.builderFor(table, PARTITION_ID, TASK_ID).format(FileFormat.AVRO).build();

    String location = fileFactory.newOutputFile().encryptingOutputFile().location();
    Assert.assertEquals(
        "File format should be correct", FileFormat.AVRO, FileFormat.fromFileName(location));
  }

  /**
   * 测试场景：output file factory with multiple specs。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testOutputFileFactoryWithMultipleSpecs() {
    OutputFileFactory fileFactory =
        OutputFileFactory.builderFor(table, PARTITION_ID, TASK_ID).operationId("append").build();

    EncryptedOutputFile unpartitionedFile =
        fileFactory.newOutputFile(PartitionSpec.unpartitioned(), null);
    String unpartitionedFileLocation = unpartitionedFile.encryptingOutputFile().location();
    Assert.assertTrue(unpartitionedFileLocation.endsWith("data/00001-100-append-00001.parquet"));

    Record record = GenericRecord.create(table.schema()).copy(ImmutableMap.of("data", "aaa"));
    PartitionKey partitionKey = new PartitionKey(table.spec(), table.schema());
    partitionKey.partition(record);
    EncryptedOutputFile partitionedFile = fileFactory.newOutputFile(table.spec(), partitionKey);
    String partitionedFileLocation = partitionedFile.encryptingOutputFile().location();
    Assert.assertTrue(
        partitionedFileLocation.endsWith("data_bucket=7/00001-100-append-00002.parquet"));
  }

  /**
   * 测试场景：with custom suffix。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testWithCustomSuffix() {
    OutputFileFactory fileFactory =
        OutputFileFactory.builderFor(table, PARTITION_ID, TASK_ID)
            .operationId("append")
            .suffix("suffix")
            .build();

    EncryptedOutputFile unpartitionedFile =
        fileFactory.newOutputFile(PartitionSpec.unpartitioned(), null);
    String unpartitionedFileLocation = unpartitionedFile.encryptingOutputFile().location();
    Assertions.assertThat(unpartitionedFileLocation)
        .endsWith("data/00001-100-append-00001-suffix.parquet");

    Record record = GenericRecord.create(table.schema()).copy(ImmutableMap.of("data", "aaa"));
    PartitionKey partitionKey = new PartitionKey(table.spec(), table.schema());
    partitionKey.partition(record);
    EncryptedOutputFile partitionedFile = fileFactory.newOutputFile(table.spec(), partitionKey);
    String partitionedFileLocation = partitionedFile.encryptingOutputFile().location();
    Assertions.assertThat(partitionedFileLocation)
        .endsWith("data_bucket=7/00001-100-append-00002-suffix.parquet");
  }
}
