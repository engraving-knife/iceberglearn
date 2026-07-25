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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.io.LocationProvider;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.util.SerializableSupplier;
import org.assertj.core.api.Assumptions;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.Mockito;

/**
 * 测试类：TestSnapshotLoading，用于验证 Snapshot Loading 相关功能。
 *
 * <p>所属模块：iceberg-core（测试目录 src/test）。 职责：针对 Snapshot Loading 的核心行为构造多种场景，覆盖正常路径、边界条件与异常输入，
 * 确保实现与预期语义一致。
 *
 * <p>测试策略：基于 JUnit（必要时配合参数化执行器）搭建表/目录等测试基座， 通过构造输入、执行被测方法并断言结果或状态来验证功能点。
 */
@RunWith(Parameterized.class)
public class TestSnapshotLoading extends TableTestBase {
  /** 辅助方法：parameters。 */
  @Parameterized.Parameters(name = "formatVersion = {0}")
  public static Object[] parameters() {
    return new Object[] {1, 2};
  }

  /** 辅助方法：snapshot loading。 */
  public TestSnapshotLoading(int formatVersion) {
    super(formatVersion);
  }

  private Snapshot currentSnapshot;
  private List<Snapshot> allSnapshots;
  private TableMetadata originalTableMetadata;
  private TableMetadata latestTableMetadata;

  private SerializableSupplier<List<Snapshot>> snapshotsSupplierMock;

  /** 辅助方法：before。 */
  @Before
  public void before() {
    table.newFastAppend().appendFile(FILE_A).commit();
    table.newFastAppend().appendFile(FILE_B).commit();

    this.currentSnapshot = table.currentSnapshot();
    this.allSnapshots = Lists.newArrayList(table.snapshots());

    // Anonymous class is required for proper mocking as opposed to lambda
    SerializableSupplier<List<Snapshot>> snapshotSupplier =
        new SerializableSupplier<List<Snapshot>>() {
          /** 辅助方法：get。 */
          @Override
          public List<Snapshot> get() {
            return allSnapshots;
          }
        };

    this.snapshotsSupplierMock = Mockito.spy(snapshotSupplier);

    this.originalTableMetadata = table.ops().current();
    this.latestTableMetadata =
        TableMetadata.buildFrom(originalTableMetadata)
            .removeSnapshots(
                allSnapshots.stream()
                    .filter(Predicate.isEqual(currentSnapshot).negate())
                    .collect(Collectors.toList()))
            .setSnapshotsSupplier(snapshotsSupplierMock)
            .discardChanges()
            .build();
  }

  /**
   * 测试场景：snapshots are loaded once。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testSnapshotsAreLoadedOnce() {
    latestTableMetadata.snapshots();
    latestTableMetadata.snapshots();
    latestTableMetadata.snapshots();

    verify(snapshotsSupplierMock, times(1)).get();

    assertThat(latestTableMetadata.snapshots())
        .containsExactlyElementsOf(originalTableMetadata.snapshots());
  }

  /**
   * 测试场景：current and main snapshot does not load。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testCurrentAndMainSnapshotDoesNotLoad() {
    latestTableMetadata.currentSnapshot();
    latestTableMetadata.snapshot(latestTableMetadata.ref(SnapshotRef.MAIN_BRANCH).snapshotId());

    verify(snapshotsSupplierMock, times(0)).get();
  }

  /**
   * 测试场景：unloaded snapshot loads once。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testUnloadedSnapshotLoadsOnce() {
    Snapshot unloadedSnapshot =
        allSnapshots.stream().filter(s -> !s.equals(currentSnapshot)).findFirst().get();

    latestTableMetadata.snapshot(unloadedSnapshot.snapshotId());
    latestTableMetadata.snapshot(unloadedSnapshot.snapshotId());

    verify(snapshotsSupplierMock, times(1)).get();
  }

  /**
   * 测试场景：current table scan does not load。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testCurrentTableScanDoesNotLoad() {
    latestTableMetadata.currentSnapshot();

    Table latestTable =
        new BaseTable(new MetadataTableOperations(table.io(), latestTableMetadata), "latestTable");

    latestTable.newScan().planFiles().forEach(t -> {});

    verify(snapshotsSupplierMock, times(0)).get();
  }

  /**
   * 测试场景：future snapshots are removed。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testFutureSnapshotsAreRemoved() {
    Assumptions.assumeThat(formatVersion)
        .as("Future snapshots are only removed for V2 tables")
        .isGreaterThan(1);

    table.newFastAppend().appendFile(FILE_C).commit();

    TableMetadata futureTableMetadata =
        TableMetadata.buildFrom(originalTableMetadata)
            .removeSnapshots(
                allSnapshots.stream()
                    .filter(Predicate.isEqual(currentSnapshot).negate())
                    .collect(Collectors.toList()))
            .setSnapshotsSupplier(() -> ImmutableList.copyOf(table.snapshots()))
            .discardChanges()
            .build();

    assertThat(futureTableMetadata.snapshots())
        .containsExactlyInAnyOrderElementsOf(originalTableMetadata.snapshots());
  }

  /**
   * 测试场景：removed current snapshot fails。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testRemovedCurrentSnapshotFails() {
    List<Snapshot> snapshotsMissingCurrent =
        allSnapshots.stream()
            .filter(Predicate.isEqual(currentSnapshot).negate())
            .collect(Collectors.toList());

    TableMetadata tableMetadata =
        TableMetadata.buildFrom(originalTableMetadata)
            .removeSnapshots(
                allSnapshots.stream()
                    .filter(Predicate.isEqual(currentSnapshot).negate())
                    .collect(Collectors.toList()))
            .setSnapshotsSupplier(() -> snapshotsMissingCurrent)
            .discardChanges()
            .build();

    assertThatThrownBy(tableMetadata::snapshots)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid table metadata: Cannot find current version");
  }

  /**
   * 测试场景：removed ref snapshot fails。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testRemovedRefSnapshotFails() {
    Snapshot referencedSnapshot =
        allSnapshots.stream().filter(Predicate.isEqual(currentSnapshot).negate()).findFirst().get();

    TableMetadata tableMetadata =
        TableMetadata.buildFrom(originalTableMetadata)
            .setRef("toRemove", SnapshotRef.branchBuilder(referencedSnapshot.snapshotId()).build())
            .setSnapshotsSupplier(() -> Lists.newArrayList(currentSnapshot))
            .build();

    long fakeSnapshotId = 123;

    // trigger loading the snapshots to cause ref failure
    assertThatThrownBy(() -> tableMetadata.snapshot(fakeSnapshotId))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith("Snapshot for reference")
        .hasMessageEndingWith("does not exist in the existing snapshots list");
  }

  /**
   * 测试场景：building new metadata triggers snapshot load。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testBuildingNewMetadataTriggersSnapshotLoad() {
    TableMetadata newTableMetadata =
        TableMetadata.buildFrom(latestTableMetadata).removeRef(SnapshotRef.MAIN_BRANCH).build();

    verify(snapshotsSupplierMock, times(1)).get();
  }

  private static class MetadataTableOperations implements TableOperations {
    private final FileIO io;
    private final TableMetadata currentMetadata;

    MetadataTableOperations(FileIO io, TableMetadata currentMetadata) {
      this.io = io;
      this.currentMetadata = currentMetadata;
    }

    /** 辅助方法：current。 */
    @Override
    public TableMetadata current() {
      return currentMetadata;
    }

    /** 辅助方法：refresh。 */
    @Override
    public TableMetadata refresh() {
      throw new UnsupportedOperationException("refresh not supported for test ops implementation.");
    }

    /** 辅助方法：commit。 */
    @Override
    public void commit(TableMetadata base, TableMetadata metadata) {
      throw new UnsupportedOperationException("commit not supported for test ops implementation.");
    }

    /** 辅助方法：io。 */
    @Override
    public FileIO io() {
      return io;
    }

    /** 辅助方法：metadata file location。 */
    @Override
    public String metadataFileLocation(String fileName) {
      throw new UnsupportedOperationException(
          "metadataFileLocation not supported for test ops implementation.");
    }

    /** 辅助方法：location provider。 */
    @Override
    public LocationProvider locationProvider() {
      throw new UnsupportedOperationException(
          "locationProvider not supported for test ops implementation.");
    }
  }
}
