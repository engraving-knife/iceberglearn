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

import java.io.File;
import java.io.IOException;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.types.Types;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * 测试类：TestPartitionSpecInfo，用于验证 Partition Spec Info 相关功能。
 *
 * <p>所属模块：iceberg-core（测试目录 src/test）。 职责：针对 Partition Spec Info 的核心行为构造多种场景，覆盖正常路径、边界条件与异常输入，
 * 确保实现与预期语义一致。
 *
 * <p>测试策略：基于 JUnit（必要时配合参数化执行器）搭建表/目录等测试基座， 通过构造输入、执行被测方法并断言结果或状态来验证功能点。
 */
@RunWith(Parameterized.class)
public class TestPartitionSpecInfo {

  @Rule public TemporaryFolder temp = new TemporaryFolder();
  private final Schema schema =
      new Schema(
          required(1, "id", Types.IntegerType.get()), required(2, "data", Types.StringType.get()));
  private File tableDir = null;

  /** 辅助方法：parameters。 */
  @Parameterized.Parameters(name = "formatVersion = {0}")
  public static Object[] parameters() {
    return new Object[] {1, 2};
  }

  private final int formatVersion;

  /** 辅助方法：partition spec info。 */
  public TestPartitionSpecInfo(int formatVersion) {
    this.formatVersion = formatVersion;
  }

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
   * 测试场景：spec is unpartitioned for void tranforms。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testSpecIsUnpartitionedForVoidTranforms() {
    PartitionSpec spec =
        PartitionSpec.builderFor(schema).alwaysNull("id").alwaysNull("data").build();

    Assert.assertTrue(spec.isUnpartitioned());
  }

  /**
   * 测试场景：spec info unpartitioned table。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testSpecInfoUnpartitionedTable() {
    PartitionSpec spec = PartitionSpec.unpartitioned();
    TestTables.TestTable table = TestTables.create(tableDir, "test", schema, spec, formatVersion);

    Assert.assertTrue(spec.isUnpartitioned());
    Assert.assertEquals(spec, table.spec());
    Assert.assertEquals(spec.lastAssignedFieldId(), table.spec().lastAssignedFieldId());
    Assert.assertEquals(ImmutableMap.of(spec.specId(), spec), table.specs());
    Assert.assertNull(table.specs().get(Integer.MAX_VALUE));
  }

  /**
   * 测试场景：spec info partitioned table。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testSpecInfoPartitionedTable() {
    PartitionSpec spec = PartitionSpec.builderFor(schema).identity("data").build();
    TestTables.TestTable table = TestTables.create(tableDir, "test", schema, spec, formatVersion);

    Assert.assertEquals(spec, table.spec());
    Assert.assertEquals(spec.lastAssignedFieldId(), table.spec().lastAssignedFieldId());
    Assert.assertEquals(ImmutableMap.of(spec.specId(), spec), table.specs());
    Assert.assertNull(table.specs().get(Integer.MAX_VALUE));
  }

  /**
   * 测试场景：column drop with partition spec evolution。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testColumnDropWithPartitionSpecEvolution() {
    PartitionSpec spec = PartitionSpec.builderFor(schema).identity("id").build();
    TestTables.TestTable table = TestTables.create(tableDir, "test", schema, spec, formatVersion);

    Assert.assertEquals(spec, table.spec());

    TableMetadata base = TestTables.readMetadata("test");
    PartitionSpec newSpec =
        PartitionSpec.builderFor(table.schema()).identity("data").withSpecId(1).build();
    table.ops().commit(base, base.updatePartitionSpec(newSpec));

    int initialColSize = table.schema().columns().size();
    table.updateSchema().deleteColumn("id").commit();

    final Schema expectedSchema = new Schema(required(2, "data", Types.StringType.get()));

    Assert.assertEquals(newSpec, table.spec());
    Assert.assertEquals(newSpec, table.specs().get(newSpec.specId()));
    Assert.assertEquals(spec, table.specs().get(spec.specId()));
    Assert.assertEquals(
        ImmutableMap.of(spec.specId(), spec, newSpec.specId(), newSpec), table.specs());
    Assert.assertNull(table.specs().get(Integer.MAX_VALUE));
    Assert.assertTrue(
        "Schema must have only \"data\" column", table.schema().sameSchema(expectedSchema));
  }

  /**
   * 测试场景：spec info partition spec evolution for 1 table。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testSpecInfoPartitionSpecEvolutionForV1Table() {
    PartitionSpec spec = PartitionSpec.builderFor(schema).bucket("data", 4).build();
    TestTables.TestTable table = TestTables.create(tableDir, "test", schema, spec, formatVersion);

    Assert.assertEquals(spec, table.spec());

    TableMetadata base = TestTables.readMetadata("test");
    PartitionSpec newSpec =
        PartitionSpec.builderFor(table.schema()).bucket("data", 10).withSpecId(1).build();
    table.ops().commit(base, base.updatePartitionSpec(newSpec));

    Assert.assertEquals(newSpec, table.spec());
    Assert.assertEquals(newSpec, table.specs().get(newSpec.specId()));
    Assert.assertEquals(spec, table.specs().get(spec.specId()));
    Assert.assertEquals(
        ImmutableMap.of(spec.specId(), spec, newSpec.specId(), newSpec), table.specs());
    Assert.assertNull(table.specs().get(Integer.MAX_VALUE));
  }
}
