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

import static org.apache.iceberg.types.Types.NestedField.optional;
import static org.apache.iceberg.types.Types.NestedField.required;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.types.Types;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * 测试类：TestSchemaID，用于验证 Schema ID 相关功能。
 *
 * <p>所属模块：iceberg-core（测试目录 src/test）。 职责：针对 Schema ID 的核心行为构造多种场景，覆盖正常路径、边界条件与异常输入， 确保实现与预期语义一致。
 *
 * <p>测试策略：基于 JUnit（必要时配合参数化执行器）搭建表/目录等测试基座， 通过构造输入、执行被测方法并断言结果或状态来验证功能点。
 */
@RunWith(Parameterized.class)
public class TestSchemaID extends TableTestBase {

  /** 辅助方法：parameters。 */
  @Parameterized.Parameters(name = "formatVersion = {0}")
  public static Object[] parameters() {
    return new Object[] {1, 2};
  }

  /** 辅助方法：schema id。 */
  public TestSchemaID(int formatVersion) {
    super(formatVersion);
  }

  /**
   * 测试场景：no change。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testNoChange() {
    int onlyId = table.schema().schemaId();
    Map<Integer, Schema> onlySchemaMap = schemaMap(table.schema());

    // add files to table
    table.newAppend().appendFile(FILE_A).appendFile(FILE_B).commit();

    TestHelpers.assertSameSchemaMap(onlySchemaMap, table.schemas());
    Assert.assertEquals(
        "Current snapshot's schemaId should be the current",
        table.schema().schemaId(),
        (int) table.currentSnapshot().schemaId());

    Assert.assertEquals(
        "Schema ids should be correct in snapshots",
        ImmutableList.of(onlyId),
        Lists.transform(Lists.newArrayList(table.snapshots()), Snapshot::schemaId));

    // remove file from table
    table.newDelete().deleteFile(FILE_A).commit();

    TestHelpers.assertSameSchemaMap(onlySchemaMap, table.schemas());
    Assert.assertEquals(
        "Current snapshot's schemaId should be the current",
        table.schema().schemaId(),
        (int) table.currentSnapshot().schemaId());

    Assert.assertEquals(
        "Schema ids should be correct in snapshots",
        ImmutableList.of(onlyId, onlyId),
        Lists.transform(Lists.newArrayList(table.snapshots()), Snapshot::schemaId));

    // add file to table
    table.newFastAppend().appendFile(FILE_A2).commit();

    TestHelpers.assertSameSchemaMap(onlySchemaMap, table.schemas());
    Assert.assertEquals(
        "Current snapshot's schemaId should be the current",
        table.schema().schemaId(),
        (int) table.currentSnapshot().schemaId());

    Assert.assertEquals(
        "Schema ids should be correct in snapshots",
        ImmutableList.of(onlyId, onlyId, onlyId),
        Lists.transform(Lists.newArrayList(table.snapshots()), Snapshot::schemaId));
  }

  /**
   * 测试场景：schema id change in schema update。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testSchemaIdChangeInSchemaUpdate() {
    Schema originalSchema = table.schema();

    // add files to table
    table.newAppend().appendFile(FILE_A).appendFile(FILE_B).commit();

    TestHelpers.assertSameSchemaMap(schemaMap(table.schema()), table.schemas());
    Assert.assertEquals(
        "Current snapshot's schemaId should be the current",
        table.schema().schemaId(),
        (int) table.currentSnapshot().schemaId());

    Assert.assertEquals(
        "Schema ids should be correct in snapshots",
        ImmutableList.of(originalSchema.schemaId()),
        Lists.transform(Lists.newArrayList(table.snapshots()), Snapshot::schemaId));

    // update schema
    table.updateSchema().addColumn("data2", Types.StringType.get()).commit();

    Schema updatedSchema =
        new Schema(
            1,
            required(1, "id", Types.IntegerType.get()),
            required(2, "data", Types.StringType.get()),
            optional(3, "data2", Types.StringType.get()));

    TestHelpers.assertSameSchemaMap(schemaMap(originalSchema, updatedSchema), table.schemas());
    Assert.assertEquals(
        "Current snapshot's schemaId should be old since update schema doesn't create new snapshot",
        originalSchema.schemaId(),
        (int) table.currentSnapshot().schemaId());
    Assert.assertEquals(
        "Current schema should match", updatedSchema.asStruct(), table.schema().asStruct());

    Assert.assertEquals(
        "Schema ids should be correct in snapshots",
        ImmutableList.of(originalSchema.schemaId()),
        Lists.transform(Lists.newArrayList(table.snapshots()), Snapshot::schemaId));

    // remove file from table
    table.newDelete().deleteFile(FILE_A).commit();

    TestHelpers.assertSameSchemaMap(schemaMap(originalSchema, updatedSchema), table.schemas());
    Assert.assertEquals(
        "Current snapshot's schemaId should be the current",
        updatedSchema.schemaId(),
        (int) table.currentSnapshot().schemaId());
    Assert.assertEquals(
        "Current schema should match", updatedSchema.asStruct(), table.schema().asStruct());

    Assert.assertEquals(
        "Schema ids should be correct in snapshots",
        ImmutableList.of(originalSchema.schemaId(), updatedSchema.schemaId()),
        Lists.transform(Lists.newArrayList(table.snapshots()), Snapshot::schemaId));

    // add files to table
    table.newAppend().appendFile(FILE_A2).commit();

    TestHelpers.assertSameSchemaMap(schemaMap(originalSchema, updatedSchema), table.schemas());
    Assert.assertEquals(
        "Current snapshot's schemaId should be the current",
        updatedSchema.schemaId(),
        (int) table.currentSnapshot().schemaId());
    Assert.assertEquals(
        "Current schema should match", updatedSchema.asStruct(), table.schema().asStruct());

    Assert.assertEquals(
        "Schema ids should be correct in snapshots",
        ImmutableList.of(
            originalSchema.schemaId(), updatedSchema.schemaId(), updatedSchema.schemaId()),
        Lists.transform(Lists.newArrayList(table.snapshots()), Snapshot::schemaId));
  }

  /** 辅助方法：schema map。 */
  private Map<Integer, Schema> schemaMap(Schema... schemas) {
    return Arrays.stream(schemas).collect(Collectors.toMap(Schema::schemaId, Function.identity()));
  }
}
