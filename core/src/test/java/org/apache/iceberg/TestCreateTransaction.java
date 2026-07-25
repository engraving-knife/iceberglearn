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

import static org.apache.iceberg.PartitionSpec.unpartitioned;
import static org.apache.iceberg.types.Types.NestedField.required;

import java.io.File;
import java.io.IOException;
import org.apache.iceberg.exceptions.CommitFailedException;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.apache.iceberg.types.TypeUtil;
import org.apache.iceberg.types.Types;
import org.assertj.core.api.Assertions;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * 测试类：TestCreateTransaction，用于验证 Create Transaction 相关功能。
 *
 * <p>所属模块：iceberg-core（测试目录 src/test）。 职责：针对 Create Transaction 的核心行为构造多种场景，覆盖正常路径、边界条件与异常输入，
 * 确保实现与预期语义一致。
 *
 * <p>测试策略：基于 JUnit（必要时配合参数化执行器）搭建表/目录等测试基座， 通过构造输入、执行被测方法并断言结果或状态来验证功能点。
 */
@RunWith(Parameterized.class)
public class TestCreateTransaction extends TableTestBase {
  /** 辅助方法：parameters。 */
  @Parameterized.Parameters(name = "formatVersion = {0}")
  public static Object[] parameters() {
    return new Object[] {1, 2};
  }

  /** 辅助方法：create transaction。 */
  public TestCreateTransaction(int formatVersion) {
    super(formatVersion);
  }

  /**
   * 测试场景：create transaction。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testCreateTransaction() throws IOException {
    File tableDir = temp.newFolder();
    Assert.assertTrue(tableDir.delete());

    Transaction txn = TestTables.beginCreate(tableDir, "test_create", SCHEMA, unpartitioned());

    Assert.assertNull(
        "Starting a create transaction should not commit metadata",
        TestTables.readMetadata("test_create"));
    Assert.assertNull("Should have no metadata version", TestTables.metadataVersion("test_create"));

    txn.commitTransaction();

    TableMetadata meta = TestTables.readMetadata("test_create");
    Assert.assertNotNull("Table metadata should be created after transaction commits", meta);
    Assert.assertEquals(
        "Should have metadata version 0", 0, (int) TestTables.metadataVersion("test_create"));
    Assert.assertEquals("Should have 0 manifest files", 0, listManifestFiles(tableDir).size());

    Assert.assertEquals(
        "Table schema should match with reassigned IDs",
        TypeUtil.assignIncreasingFreshIds(SCHEMA).asStruct(),
        meta.schema().asStruct());
    Assert.assertEquals("Table spec should match", unpartitioned(), meta.spec());
    Assert.assertEquals("Table should not have any snapshots", 0, meta.snapshots().size());
  }

  /**
   * 测试场景：create transaction and update schema。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testCreateTransactionAndUpdateSchema() throws IOException {
    File tableDir = temp.newFolder();
    Assert.assertTrue(tableDir.delete());

    Transaction txn = TestTables.beginCreate(tableDir, "test_create", SCHEMA, unpartitioned());

    Assert.assertNull(
        "Starting a create transaction should not commit metadata",
        TestTables.readMetadata("test_create"));
    Assert.assertNull("Should have no metadata version", TestTables.metadataVersion("test_create"));

    txn.updateSchema()
        .allowIncompatibleChanges()
        .addRequiredColumn("col", Types.StringType.get())
        .setIdentifierFields("id", "col")
        .commit();

    txn.commitTransaction();

    TableMetadata meta = TestTables.readMetadata("test_create");
    Assert.assertNotNull("Table metadata should be created after transaction commits", meta);
    Assert.assertEquals(
        "Should have metadata version 0", 0, (int) TestTables.metadataVersion("test_create"));
    Assert.assertEquals("Should have 0 manifest files", 0, listManifestFiles(tableDir).size());

    Schema resultSchema =
        new Schema(
            Lists.newArrayList(
                required(1, "id", Types.IntegerType.get()),
                required(2, "data", Types.StringType.get()),
                required(3, "col", Types.StringType.get())),
            Sets.newHashSet(1, 3));

    Assert.assertEquals(
        "Table schema should match with reassigned IDs",
        resultSchema.asStruct(),
        meta.schema().asStruct());
    Assert.assertEquals(
        "Table schema identifier should match",
        resultSchema.identifierFieldIds(),
        meta.schema().identifierFieldIds());
    Assert.assertEquals("Table spec should match", unpartitioned(), meta.spec());
    Assert.assertEquals("Table should not have any snapshots", 0, meta.snapshots().size());
  }

  /**
   * 测试场景：create and append with transaction。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testCreateAndAppendWithTransaction() throws IOException {
    File tableDir = temp.newFolder();
    Assert.assertTrue(tableDir.delete());

    Transaction txn = TestTables.beginCreate(tableDir, "test_append", SCHEMA, unpartitioned());

    Assert.assertNull(
        "Starting a create transaction should not commit metadata",
        TestTables.readMetadata("test_append"));
    Assert.assertNull("Should have no metadata version", TestTables.metadataVersion("test_append"));

    txn.newAppend().appendFile(FILE_A).appendFile(FILE_B).commit();

    Assert.assertNull(
        "Appending in a transaction should not commit metadata",
        TestTables.readMetadata("test_append"));
    Assert.assertNull("Should have no metadata version", TestTables.metadataVersion("test_append"));

    txn.commitTransaction();

    TableMetadata meta = TestTables.readMetadata("test_append");
    Assert.assertNotNull("Table metadata should be created after transaction commits", meta);
    Assert.assertEquals(
        "Should have metadata version 0", 0, (int) TestTables.metadataVersion("test_append"));
    Assert.assertEquals("Should have 1 manifest file", 1, listManifestFiles(tableDir).size());

    Assert.assertEquals(
        "Table schema should match with reassigned IDs",
        TypeUtil.assignIncreasingFreshIds(SCHEMA).asStruct(),
        meta.schema().asStruct());
    Assert.assertEquals("Table spec should match", unpartitioned(), meta.spec());
    Assert.assertEquals("Table should have one snapshot", 1, meta.snapshots().size());

    validateSnapshot(null, meta.currentSnapshot(), FILE_A, FILE_B);
  }

  /**
   * 测试场景：create and append with table。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testCreateAndAppendWithTable() throws IOException {
    File tableDir = temp.newFolder();
    Assert.assertTrue(tableDir.delete());

    Transaction txn = TestTables.beginCreate(tableDir, "test_append", SCHEMA, unpartitioned());

    Assert.assertNull(
        "Starting a create transaction should not commit metadata",
        TestTables.readMetadata("test_append"));
    Assert.assertNull("Should have no metadata version", TestTables.metadataVersion("test_append"));

    Assert.assertTrue(
        "Should return a transaction table",
        txn.table() instanceof BaseTransaction.TransactionTable);

    txn.table().newAppend().appendFile(FILE_A).appendFile(FILE_B).commit();

    Assert.assertNull(
        "Appending in a transaction should not commit metadata",
        TestTables.readMetadata("test_append"));
    Assert.assertNull("Should have no metadata version", TestTables.metadataVersion("test_append"));

    txn.commitTransaction();

    TableMetadata meta = TestTables.readMetadata("test_append");
    Assert.assertNotNull("Table metadata should be created after transaction commits", meta);
    Assert.assertEquals(
        "Should have metadata version 0", 0, (int) TestTables.metadataVersion("test_append"));
    Assert.assertEquals("Should have 1 manifest file", 1, listManifestFiles(tableDir).size());

    Assert.assertEquals(
        "Table schema should match with reassigned IDs",
        TypeUtil.assignIncreasingFreshIds(SCHEMA).asStruct(),
        meta.schema().asStruct());
    Assert.assertEquals("Table spec should match", unpartitioned(), meta.spec());
    Assert.assertEquals("Table should have one snapshot", 1, meta.snapshots().size());

    validateSnapshot(null, meta.currentSnapshot(), FILE_A, FILE_B);
  }

  /**
   * 测试场景：create and update properties with transaction。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testCreateAndUpdatePropertiesWithTransaction() throws IOException {
    File tableDir = temp.newFolder();
    Assert.assertTrue(tableDir.delete());

    Transaction txn = TestTables.beginCreate(tableDir, "test_properties", SCHEMA, unpartitioned());

    Assert.assertNull(
        "Starting a create transaction should not commit metadata",
        TestTables.readMetadata("test_properties"));
    Assert.assertNull(
        "Should have no metadata version", TestTables.metadataVersion("test_properties"));

    txn.updateProperties().set("test-property", "test-value").commit();

    Assert.assertNull(
        "Adding properties in a transaction should not commit metadata",
        TestTables.readMetadata("test_properties"));
    Assert.assertNull(
        "Should have no metadata version", TestTables.metadataVersion("test_properties"));

    txn.commitTransaction();

    TableMetadata meta = TestTables.readMetadata("test_properties");
    Assert.assertNotNull("Table metadata should be created after transaction commits", meta);
    Assert.assertEquals(
        "Should have metadata version 0", 0, (int) TestTables.metadataVersion("test_properties"));
    Assert.assertEquals("Should have 0 manifest files", 0, listManifestFiles(tableDir).size());

    Assert.assertEquals(
        "Table schema should match with reassigned IDs",
        TypeUtil.assignIncreasingFreshIds(SCHEMA).asStruct(),
        meta.schema().asStruct());
    Assert.assertEquals("Table spec should match", unpartitioned(), meta.spec());
    Assert.assertEquals("Table should not have any snapshots", 0, meta.snapshots().size());
    Assert.assertEquals("Should have one table property", 1, meta.properties().size());
    Assert.assertEquals(
        "Should have correct table property value",
        "test-value",
        meta.properties().get("test-property"));
  }

  /**
   * 测试场景：create and update properties with table。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testCreateAndUpdatePropertiesWithTable() throws IOException {
    File tableDir = temp.newFolder();
    Assert.assertTrue(tableDir.delete());

    Transaction txn = TestTables.beginCreate(tableDir, "test_properties", SCHEMA, unpartitioned());

    Assert.assertNull(
        "Starting a create transaction should not commit metadata",
        TestTables.readMetadata("test_properties"));
    Assert.assertNull(
        "Should have no metadata version", TestTables.metadataVersion("test_properties"));

    Assert.assertTrue(
        "Should return a transaction table",
        txn.table() instanceof BaseTransaction.TransactionTable);

    txn.table().updateProperties().set("test-property", "test-value").commit();

    Assert.assertNull(
        "Adding properties in a transaction should not commit metadata",
        TestTables.readMetadata("test_properties"));
    Assert.assertNull(
        "Should have no metadata version", TestTables.metadataVersion("test_properties"));

    txn.commitTransaction();

    TableMetadata meta = TestTables.readMetadata("test_properties");
    Assert.assertNotNull("Table metadata should be created after transaction commits", meta);
    Assert.assertEquals(
        "Should have metadata version 0", 0, (int) TestTables.metadataVersion("test_properties"));
    Assert.assertEquals("Should have 0 manifest files", 0, listManifestFiles(tableDir).size());

    Assert.assertEquals(
        "Table schema should match with reassigned IDs",
        TypeUtil.assignIncreasingFreshIds(SCHEMA).asStruct(),
        meta.schema().asStruct());
    Assert.assertEquals("Table spec should match", unpartitioned(), meta.spec());
    Assert.assertEquals("Table should not have any snapshots", 0, meta.snapshots().size());
    Assert.assertEquals("Should have one table property", 1, meta.properties().size());
    Assert.assertEquals(
        "Should have correct table property value",
        "test-value",
        meta.properties().get("test-property"));
  }

  /**
   * 测试场景：create detects uncommitted change。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testCreateDetectsUncommittedChange() throws IOException {
    File tableDir = temp.newFolder();
    Assert.assertTrue(tableDir.delete());

    Transaction txn =
        TestTables.beginCreate(tableDir, "uncommitted_change", SCHEMA, unpartitioned());

    Assert.assertNull(
        "Starting a create transaction should not commit metadata",
        TestTables.readMetadata("uncommitted_change"));
    Assert.assertNull(
        "Should have no metadata version", TestTables.metadataVersion("uncommitted_change"));

    txn.updateProperties().set("test-property", "test-value"); // not committed

    Assertions.assertThatThrownBy(txn::newDelete)
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Cannot create new DeleteFiles: last operation has not committed");
  }

  /**
   * 测试场景：create detects uncommitted change on commit。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testCreateDetectsUncommittedChangeOnCommit() throws IOException {
    File tableDir = temp.newFolder();
    Assert.assertTrue(tableDir.delete());

    Transaction txn =
        TestTables.beginCreate(tableDir, "uncommitted_change", SCHEMA, unpartitioned());

    Assert.assertNull(
        "Starting a create transaction should not commit metadata",
        TestTables.readMetadata("uncommitted_change"));
    Assert.assertNull(
        "Should have no metadata version", TestTables.metadataVersion("uncommitted_change"));

    txn.updateProperties().set("test-property", "test-value"); // not committed

    Assertions.assertThatThrownBy(txn::commitTransaction)
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Cannot commit transaction: last operation has not committed");
  }

  /**
   * 测试场景：create transaction conflict。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testCreateTransactionConflict() throws IOException {
    File tableDir = temp.newFolder();
    Assert.assertTrue(tableDir.delete());

    Transaction txn = TestTables.beginCreate(tableDir, "test_conflict", SCHEMA, SPEC);

    // append in the transaction to ensure a manifest file is created
    txn.newAppend().appendFile(FILE_A).commit();

    Assert.assertNull(
        "Starting a create transaction should not commit metadata",
        TestTables.readMetadata("test_conflict"));
    Assert.assertNull(
        "Should have no metadata version", TestTables.metadataVersion("test_conflict"));

    Table conflict =
        TestTables.create(tableDir, "test_conflict", SCHEMA, unpartitioned(), formatVersion);

    Assert.assertEquals(
        "Table schema should match with reassigned IDs",
        TypeUtil.assignIncreasingFreshIds(SCHEMA).asStruct(),
        conflict.schema().asStruct());
    Assert.assertEquals(
        "Table spec should match conflict table, not transaction table",
        unpartitioned(),
        conflict.spec());
    Assert.assertFalse(
        "Table should not have any snapshots", conflict.snapshots().iterator().hasNext());

    Assertions.assertThatThrownBy(txn::commitTransaction)
        .isInstanceOf(CommitFailedException.class)
        .hasMessageStartingWith("Commit failed: table was updated");

    Assert.assertEquals(
        "Should clean up metadata",
        Sets.newHashSet(),
        Sets.newHashSet(listManifestFiles(tableDir)));
  }
}
