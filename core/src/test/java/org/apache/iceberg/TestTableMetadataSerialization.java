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

import static org.apache.iceberg.TestHelpers.assertSameSchemaList;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * 测试类：TestTableMetadataSerialization，用于验证 Table Metadata Serialization 相关功能。
 *
 * <p>所属模块：iceberg-core（测试目录 src/test）。 职责：针对 Table Metadata Serialization
 * 的核心行为构造多种场景，覆盖正常路径、边界条件与异常输入， 确保实现与预期语义一致。
 *
 * <p>测试策略：基于 JUnit（必要时配合参数化执行器）搭建表/目录等测试基座， 通过构造输入、执行被测方法并断言结果或状态来验证功能点。
 */
@RunWith(Parameterized.class)
public class TestTableMetadataSerialization extends TableTestBase {
  /** 辅助方法：parameters。 */
  @Parameterized.Parameters(name = "formatVersion = {0}")
  public static Object[] parameters() {
    return new Object[] {1, 2};
  }

  /** 辅助方法：table metadata serialization。 */
  public TestTableMetadataSerialization(int formatVersion) {
    super(formatVersion);
  }

  /**
   * 测试场景：serialization。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testSerialization() throws Exception {
    // add a commit to the metadata so there is at least one snapshot, and history
    table.newAppend().appendFile(FILE_A).appendFile(FILE_B).commit();

    TableMetadata meta = table.ops().current();

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (ObjectOutputStream writer = new ObjectOutputStream(out)) {
      writer.writeObject(meta);
    }

    TableMetadata result;
    ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
    try (ObjectInputStream reader = new ObjectInputStream(in)) {
      result = (TableMetadata) reader.readObject();
    }

    Assert.assertEquals(
        "Metadata file location should match",
        meta.metadataFileLocation(),
        result.metadataFileLocation());
    Assert.assertEquals("UUID should match", meta.uuid(), result.uuid());
    Assert.assertEquals("Location should match", meta.location(), result.location());
    Assert.assertEquals(
        "Last updated should match", meta.lastUpdatedMillis(), result.lastUpdatedMillis());
    Assert.assertEquals("Last column id", meta.lastColumnId(), result.lastColumnId());
    Assert.assertEquals(
        "Schema should match", meta.schema().asStruct(), result.schema().asStruct());
    assertSameSchemaList(meta.schemas(), result.schemas());
    Assert.assertEquals(
        "Current schema id should match", meta.currentSchemaId(), result.currentSchemaId());
    Assert.assertEquals("Spec should match", meta.defaultSpecId(), result.defaultSpecId());
    Assert.assertEquals("Spec list should match", meta.specs(), result.specs());
    Assert.assertEquals("Properties should match", meta.properties(), result.properties());
    Assert.assertEquals(
        "Current snapshot ID should match",
        meta.currentSnapshot().snapshotId(),
        result.currentSnapshot().snapshotId());
    Assert.assertEquals(
        "Snapshots should match",
        Lists.transform(meta.snapshots(), Snapshot::snapshotId),
        Lists.transform(result.snapshots(), Snapshot::snapshotId));
    Assert.assertEquals("History should match", meta.snapshotLog(), result.snapshotLog());
  }
}
