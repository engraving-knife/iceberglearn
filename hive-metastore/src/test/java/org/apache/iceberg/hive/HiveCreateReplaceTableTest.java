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
package org.apache.iceberg.hive;

import static org.apache.iceberg.PartitionSpec.builderFor;
import static org.apache.iceberg.types.Types.NestedField.required;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Path;
import org.apache.iceberg.AppendFiles;
import org.apache.iceberg.DataFile;
import org.apache.iceberg.DataFiles;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Schema;
import org.apache.iceberg.Snapshot;
import org.apache.iceberg.Table;
import org.apache.iceberg.TableProperties;
import org.apache.iceberg.Transaction;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.exceptions.AlreadyExistsException;
import org.apache.iceberg.exceptions.NoSuchTableException;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.types.Types;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 文件级说明：测试 HiveCreateReplaceTableTest 的功能。
 *
 * <p>所属模块：iceberg-hive-metastore。职责：验证 HiveCreateReplaceTableTest 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 JUnit 框架，通过构造输入、调用方法、断言结果来覆盖功能点。
 */
public class HiveCreateReplaceTableTest extends HiveMetastoreTest {

  private static final String TABLE_NAME = "tbl";
  private static final TableIdentifier TABLE_IDENTIFIER = TableIdentifier.of(DB_NAME, TABLE_NAME);
  private static final Schema SCHEMA =
      new Schema(
          required(3, "id", Types.IntegerType.get()), required(4, "data", Types.StringType.get()));
  private static final PartitionSpec SPEC = builderFor(SCHEMA).identity("id").build();

  @TempDir private Path temp;

  private String tableLocation;

  /** 辅助方法：createTableLocation。 */
  @BeforeEach
  public void createTableLocation() throws IOException {
    tableLocation = temp.resolve("hive-").toString();
  }

  /** 辅助方法：cleanup。 */
  @AfterEach
  public void cleanup() {
    catalog.dropTable(TABLE_IDENTIFIER);
  }

  /**
   * 测试场景：Create Table Txn。
   *
   * <p>验证该方法在 Create Table Txn 条件下的行为是否符合预期。
   */
  @Test
  public void testCreateTableTxn() {
    assertThat(catalog.tableExists(TABLE_IDENTIFIER)).as("Table should not exist").isFalse();

    Transaction txn =
        catalog.newCreateTableTransaction(
            TABLE_IDENTIFIER, SCHEMA, SPEC, tableLocation, Maps.newHashMap());
    txn.updateProperties().set("prop", "value").commit();

    // verify the table is still not visible before the transaction is committed
    assertThat(catalog.tableExists(TABLE_IDENTIFIER)).isFalse();

    txn.commitTransaction();

    Table table = catalog.loadTable(TABLE_IDENTIFIER);
    assertThat(table.properties()).as("Table props should match").containsEntry("prop", "value");
  }

  /**
   * 测试场景：Create Table Txn Table Created Concurrently。
   *
   * <p>验证该方法在 Create Table Txn Table Created Concurrently 条件下的行为是否符合预期。
   */
  @Test
  public void testCreateTableTxnTableCreatedConcurrently() {
    assertThat(catalog.tableExists(TABLE_IDENTIFIER)).as("Table should not exist").isFalse();

    Transaction txn =
        catalog.newCreateTableTransaction(
            TABLE_IDENTIFIER, SCHEMA, SPEC, tableLocation, Maps.newHashMap());

    // create the table concurrently
    catalog.createTable(TABLE_IDENTIFIER, SCHEMA, SPEC);
    assertThat(catalog.tableExists(TABLE_IDENTIFIER)).as("Table should be created").isTrue();

    assertThatThrownBy(txn::commitTransaction)
        .isInstanceOf(AlreadyExistsException.class)
        .hasMessage("Table already exists: hivedb.tbl");
  }

  /**
   * 测试场景：Create Table Txn And Append。
   *
   * <p>验证该方法在 Create Table Txn And Append 条件下的行为是否符合预期。
   */
  @Test
  public void testCreateTableTxnAndAppend() {
    assertThat(catalog.tableExists(TABLE_IDENTIFIER)).as("Table should not exist").isFalse();

    Transaction txn =
        catalog.newCreateTableTransaction(
            TABLE_IDENTIFIER, SCHEMA, SPEC, tableLocation, Maps.newHashMap());

    AppendFiles append = txn.newAppend();
    DataFile dataFile =
        DataFiles.builder(SPEC)
            .withPath("/path/to/data-a.parquet")
            .withFileSizeInBytes(0)
            .withRecordCount(1)
            .build();
    append.appendFile(dataFile);
    append.commit();
    txn.commitTransaction();

    Table table = catalog.loadTable(TABLE_IDENTIFIER);
    Snapshot snapshot = table.currentSnapshot();
    assertThat(snapshot.allManifests(table.io()))
        .as("Table should have one manifest file")
        .hasSize(1);
  }

  /**
   * 测试场景：Create Table Txn Table Already Exists。
   *
   * <p>验证该方法在 Create Table Txn Table Already Exists 条件下的行为是否符合预期。
   */
  @Test
  public void testCreateTableTxnTableAlreadyExists() {
    assertThat(catalog.tableExists(TABLE_IDENTIFIER)).as("Table should not exist").isFalse();

    // create a table before starting a transaction
    catalog.createTable(TABLE_IDENTIFIER, SCHEMA, SPEC);
    assertThat(catalog.tableExists(TABLE_IDENTIFIER)).as("Table should be created").isTrue();

    assertThatThrownBy(
            () ->
                catalog.newCreateTableTransaction(
                    TABLE_IDENTIFIER, SCHEMA, SPEC, tableLocation, Maps.newHashMap()))
        .isInstanceOf(AlreadyExistsException.class)
        .hasMessage("Table already exists: hivedb.tbl");
  }

  /**
   * 测试场景：Replace Table Txn。
   *
   * <p>验证该方法在 Replace Table Txn 条件下的行为是否符合预期。
   */
  @ParameterizedTest
  @ValueSource(ints = {1, 2})
  public void testReplaceTableTxn(int formatVersion) {
    catalog.createTable(
        TABLE_IDENTIFIER,
        SCHEMA,
        SPEC,
        tableLocation,
        ImmutableMap.of(TableProperties.FORMAT_VERSION, String.valueOf(formatVersion)));
    assertThat(catalog.tableExists(TABLE_IDENTIFIER)).as("Table should exist").isTrue();

    Transaction txn = catalog.newReplaceTableTransaction(TABLE_IDENTIFIER, SCHEMA, false);
    txn.commitTransaction();

    Table table = catalog.loadTable(TABLE_IDENTIFIER);
    if (formatVersion == 1) {
      PartitionSpec v1Expected =
          PartitionSpec.builderFor(table.schema()).alwaysNull("id", "id").withSpecId(1).build();
      assertThat(table.spec())
          .as("Table should have a spec with one void field")
          .isEqualTo(v1Expected);
    } else {
      assertThat(table.spec().isUnpartitioned()).as("Table spec must be unpartitioned").isTrue();
    }
  }

  /**
   * 测试场景：Replace Table Txn Table Not Exists。
   *
   * <p>验证该方法在 Replace Table Txn Table Not Exists 条件下的行为是否符合预期。
   */
  @Test
  public void testReplaceTableTxnTableNotExists() {
    assertThatThrownBy(
            () -> catalog.newReplaceTableTransaction(TABLE_IDENTIFIER, SCHEMA, SPEC, false))
        .isInstanceOf(NoSuchTableException.class)
        .hasMessage("Table does not exist: hivedb.tbl");
  }

  /**
   * 测试场景：Replace Table Txn Table Deleted Concurrently。
   *
   * <p>验证该方法在 Replace Table Txn Table Deleted Concurrently 条件下的行为是否符合预期。
   */
  @Test
  public void testReplaceTableTxnTableDeletedConcurrently() {
    catalog.createTable(TABLE_IDENTIFIER, SCHEMA, SPEC, tableLocation, Maps.newHashMap());
    assertThat(catalog.tableExists(TABLE_IDENTIFIER)).as("Table should exist").isTrue();

    Transaction txn = catalog.newReplaceTableTransaction(TABLE_IDENTIFIER, SCHEMA, SPEC, false);

    catalog.dropTable(TABLE_IDENTIFIER);

    txn.updateProperties().set("prop", "value").commit();

    assertThatThrownBy(txn::commitTransaction)
        .isInstanceOf(NoSuchTableException.class)
        .hasMessage("No such table: hivedb.tbl");
  }

  /**
   * 测试场景：Replace Table Txn Table Modified Concurrently。
   *
   * <p>验证该方法在 Replace Table Txn Table Modified Concurrently 条件下的行为是否符合预期。
   */
  @Test
  public void testReplaceTableTxnTableModifiedConcurrently() {
    Table table =
        catalog.createTable(TABLE_IDENTIFIER, SCHEMA, SPEC, tableLocation, Maps.newHashMap());
    assertThat(catalog.tableExists(TABLE_IDENTIFIER)).as("Table should exist").isTrue();

    Transaction txn = catalog.newReplaceTableTransaction(TABLE_IDENTIFIER, SCHEMA, SPEC, false);

    // update the table concurrently
    table.updateProperties().set("another-prop", "another-value").commit();

    txn.updateProperties().set("prop", "value").commit();
    txn.commitTransaction();

    // the replace should still succeed
    table = catalog.loadTable(TABLE_IDENTIFIER);
    assertThat(table.properties())
        .as("Table props should be updated")
        .doesNotContainKey("another-prop")
        .containsEntry("prop", "value");
  }

  /**
   * 测试场景：Create Or Replace Table Txn Table Not Exists。
   *
   * <p>验证该方法在 Create Or Replace Table Txn Table Not Exists 条件下的行为是否符合预期。
   */
  @Test
  public void testCreateOrReplaceTableTxnTableNotExists() {
    assertThat(catalog.tableExists(TABLE_IDENTIFIER)).as("Table should not exist").isFalse();

    Transaction txn = catalog.newReplaceTableTransaction(TABLE_IDENTIFIER, SCHEMA, SPEC, true);
    txn.updateProperties().set("prop", "value").commit();
    txn.commitTransaction();

    Table table = catalog.loadTable(TABLE_IDENTIFIER);
    assertThat(table.properties()).as("Table props should match").containsEntry("prop", "value");
  }

  /**
   * 测试场景：Create Or Replace Table Txn Table Exists。
   *
   * <p>验证该方法在 Create Or Replace Table Txn Table Exists 条件下的行为是否符合预期。
   */
  @ParameterizedTest
  @ValueSource(ints = {1, 2})
  public void testCreateOrReplaceTableTxnTableExists(int formatVersion) {
    catalog.createTable(
        TABLE_IDENTIFIER,
        SCHEMA,
        SPEC,
        tableLocation,
        ImmutableMap.of(TableProperties.FORMAT_VERSION, String.valueOf(formatVersion)));
    assertThat(catalog.tableExists(TABLE_IDENTIFIER)).as("Table should exist").isTrue();

    Transaction txn = catalog.newReplaceTableTransaction(TABLE_IDENTIFIER, SCHEMA, true);
    txn.commitTransaction();

    Table table = catalog.loadTable(TABLE_IDENTIFIER);
    if (formatVersion == 1) {
      PartitionSpec v1Expected =
          PartitionSpec.builderFor(table.schema()).alwaysNull("id", "id").withSpecId(1).build();
      assertThat(table.spec())
          .as("Table should have a spec with one void field")
          .isEqualTo(v1Expected);
    } else {
      assertThat(table.spec().isUnpartitioned()).as("Table spec must be unpartitioned").isTrue();
    }
  }

  /**
   * 测试场景：Create Or Replace Table Txn Table Deleted Concurrently。
   *
   * <p>验证该方法在 Create Or Replace Table Txn Table Deleted Concurrently 条件下的行为是否符合预期。
   */
  @Test
  public void testCreateOrReplaceTableTxnTableDeletedConcurrently() {
    assertThat(catalog.tableExists(TABLE_IDENTIFIER)).as("Table should not exist").isFalse();
    catalog.createTable(TABLE_IDENTIFIER, SCHEMA, SPEC);
    assertThat(catalog.tableExists(TABLE_IDENTIFIER)).as("Table should be created").isTrue();

    Transaction txn =
        catalog.newReplaceTableTransaction(
            TABLE_IDENTIFIER,
            SCHEMA,
            PartitionSpec.unpartitioned(),
            tableLocation,
            Maps.newHashMap(),
            true);
    txn.updateProperties().set("prop", "value").commit();

    // drop the table concurrently
    catalog.dropTable(TABLE_IDENTIFIER);

    // expect the transaction to succeed anyway
    txn.commitTransaction();

    Table table = catalog.loadTable(TABLE_IDENTIFIER);
    assertThat(table.properties()).as("Table props should match").containsEntry("prop", "value");
  }

  /**
   * 测试场景：Create Or Replace Table Txn Table Created Concurrently。
   *
   * <p>验证该方法在 Create Or Replace Table Txn Table Created Concurrently 条件下的行为是否符合预期。
   */
  @Test
  public void testCreateOrReplaceTableTxnTableCreatedConcurrently() {
    assertThat(catalog.tableExists(TABLE_IDENTIFIER)).as("Table should not exist").isFalse();

    Transaction txn =
        catalog.newReplaceTableTransaction(
            TABLE_IDENTIFIER,
            SCHEMA,
            PartitionSpec.unpartitioned(),
            tableLocation,
            Maps.newHashMap(),
            true);
    txn.updateProperties().set("prop", "value").commit();

    // create the table concurrently
    catalog.createTable(TABLE_IDENTIFIER, SCHEMA, SPEC);
    assertThat(catalog.tableExists(TABLE_IDENTIFIER)).as("Table should be created").isTrue();

    // expect the transaction to succeed anyway
    txn.commitTransaction();

    Table table = catalog.loadTable(TABLE_IDENTIFIER);
    assertThat(table.spec())
        .as("Partition spec should match")
        .isEqualTo(PartitionSpec.unpartitioned());
    assertThat(table.properties()).as("Table props should match").containsEntry("prop", "value");
  }

  /**
   * 测试场景：Create Table Txn With Global Table Location。
   *
   * <p>验证该方法在 Create Table Txn With Global Table Location 条件下的行为是否符合预期。
   */
  @Test
  public void testCreateTableTxnWithGlobalTableLocation() {
    assertThat(catalog.tableExists(TABLE_IDENTIFIER)).as("Table should not exist").isFalse();

    Transaction txn =
        catalog.newCreateTableTransaction(
            TABLE_IDENTIFIER, SCHEMA, SPEC, "file:///" + tableLocation, Maps.newHashMap());
    txn.commitTransaction();

    Table table = catalog.loadTable(TABLE_IDENTIFIER);

    DataFile dataFile =
        DataFiles.builder(SPEC)
            .withPath("/path/to/data-a.parquet")
            .withFileSizeInBytes(0)
            .withRecordCount(1)
            .build();

    table.newAppend().appendFile(dataFile).commit();

    assertThat(table.snapshots()).as("Write should succeed").hasSize(1);
  }
}
