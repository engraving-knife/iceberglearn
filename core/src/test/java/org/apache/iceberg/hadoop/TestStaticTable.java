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
package org.apache.iceberg.hadoop;

import org.apache.iceberg.HasTableOperations;
import org.apache.iceberg.MetadataTableType;
import org.apache.iceberg.StaticTableOperations;
import org.apache.iceberg.Table;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 测试类：TestStaticTable，用于验证 Static Table 相关功能。
 *
 * <p>所属模块：iceberg-core（测试目录 src/test）。 职责：针对 Static Table 的核心行为构造多种场景，覆盖正常路径、边界条件与异常输入，
 * 确保实现与预期语义一致。
 *
 * <p>测试策略：基于 JUnit（必要时配合参数化执行器）搭建表/目录等测试基座， 通过构造输入、执行被测方法并断言结果或状态来验证功能点。
 */
public class TestStaticTable extends HadoopTableTestBase {

  /** 辅助方法：get static table。 */
  private Table getStaticTable() {
    return TABLES.load(((HasTableOperations) table).operations().current().metadataFileLocation());
  }

  /** 辅助方法：get static table。 */
  private Table getStaticTable(MetadataTableType type) {
    return TABLES.load(
        ((HasTableOperations) table).operations().current().metadataFileLocation() + "#" + type);
  }

  /**
   * 测试场景：load from metadata。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testLoadFromMetadata() {
    Table staticTable = getStaticTable();
    Assertions.assertThat(((HasTableOperations) staticTable).operations())
        .as("Loading a metadata file based table should return StaticTableOperations")
        .isInstanceOf(StaticTableOperations.class);
  }

  /**
   * 测试场景：cannot be added to。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testCannotBeAddedTo() {
    Table staticTable = getStaticTable();
    Assertions.assertThatThrownBy(() -> staticTable.newOverwrite().addFile(FILE_A).commit())
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessage("Cannot modify a static table");
  }

  /**
   * 测试场景：cannot be deleted from。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testCannotBeDeletedFrom() {
    table.newAppend().appendFile(FILE_A).commit();
    Table staticTable = getStaticTable();
    Assertions.assertThatThrownBy(() -> staticTable.newDelete().deleteFile(FILE_A).commit())
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessage("Cannot modify a static table");
  }

  /**
   * 测试场景：cannot do incremental scan on metadata table。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testCannotDoIncrementalScanOnMetadataTable() {
    table.newAppend().appendFile(FILE_A).commit();

    for (MetadataTableType type : MetadataTableType.values()) {
      Table staticTable = getStaticTable(type);

      if (type.equals(MetadataTableType.POSITION_DELETES)) {
        Assertions.assertThatThrownBy(staticTable::newScan)
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessage("Cannot create TableScan from table of type POSITION_DELETES");
      } else {
        Assertions.assertThatThrownBy(() -> staticTable.newScan().appendsAfter(1))
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessage(String.format("Cannot incrementally scan table of type %s", type));
      }
    }
  }

  /**
   * 测试场景：has same properties。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testHasSameProperties() {
    table.newAppend().appendFile(FILE_A).commit();
    table.newAppend().appendFile(FILE_B).commit();
    table.newOverwrite().deleteFile(FILE_B).addFile(FILE_C).commit();
    Table staticTable = getStaticTable();
    Assertions.assertThat(table.history()).as("Same history?").containsAll(staticTable.history());
    Assertions.assertThat(table.currentSnapshot().snapshotId())
        .as("Same snapshot?")
        .isEqualTo(staticTable.currentSnapshot().snapshotId());
    Assertions.assertThat(table.properties())
        .as("Same properties?")
        .isEqualTo(staticTable.properties());
  }

  /**
   * 测试场景：immutable。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testImmutable() {
    table.newAppend().appendFile(FILE_A).commit();
    Table staticTable = getStaticTable();
    long originalSnapshot = table.currentSnapshot().snapshotId();

    table.newAppend().appendFile(FILE_B).commit();
    table.newOverwrite().deleteFile(FILE_B).addFile(FILE_C).commit();
    staticTable.refresh();

    Assertions.assertThat(staticTable.currentSnapshot().snapshotId())
        .as("Snapshot unchanged after table modified")
        .isEqualTo(originalSnapshot);
  }

  /**
   * 测试场景：metadata tables。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testMetadataTables() {
    for (MetadataTableType type : MetadataTableType.values()) {
      String enumName = type.name().replace("_", "").toLowerCase();
      Assertions.assertThat(getStaticTable(type).getClass().getName().toLowerCase())
          .as("Should be able to get MetadataTable of type : " + type)
          .contains(enumName);
    }
  }
}
