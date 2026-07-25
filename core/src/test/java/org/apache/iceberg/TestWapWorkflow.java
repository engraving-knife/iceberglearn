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

import org.apache.iceberg.exceptions.CherrypickAncestorCommitException;
import org.apache.iceberg.exceptions.DuplicateWAPCommitException;
import org.apache.iceberg.exceptions.ValidationException;
import org.apache.iceberg.relocated.com.google.common.collect.Iterables;
import org.apache.iceberg.relocated.com.google.common.collect.Streams;
import org.assertj.core.api.Assertions;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * 测试类：TestWapWorkflow，用于验证 Wap Workflow 相关功能。
 *
 * <p>所属模块：iceberg-core（测试目录 src/test）。 职责：针对 Wap Workflow 的核心行为构造多种场景，覆盖正常路径、边界条件与异常输入，
 * 确保实现与预期语义一致。
 *
 * <p>测试策略：基于 JUnit（必要时配合参数化执行器）搭建表/目录等测试基座， 通过构造输入、执行被测方法并断言结果或状态来验证功能点。
 */
@RunWith(Parameterized.class)
public class TestWapWorkflow extends TableTestBase {
  /** 辅助方法：parameters。 */
  @Parameterized.Parameters(name = "formatVersion = {0}")
  public static Object[] parameters() {
    return new Object[] {1, 2};
  }

  /** 辅助方法：wap workflow。 */
  public TestWapWorkflow(int formatVersion) {
    super(formatVersion);
  }

  /** 辅助方法：setup table properties。 */
  @Before
  public void setupTableProperties() {
    table.updateProperties().set(TableProperties.WRITE_AUDIT_PUBLISH_ENABLED, "true").commit();
  }

  /**
   * 测试场景：cherry pick overwrite。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testCherryPickOverwrite() {
    table.newAppend().appendFile(FILE_A).commit();

    table.newOverwrite().deleteFile(FILE_A).addFile(FILE_B).stageOnly().commit();

    // the overwrite should only be staged
    validateTableFiles(table, FILE_A);

    Snapshot overwrite =
        Streams.stream(table.snapshots())
            .filter(snap -> DataOperations.OVERWRITE.equals(snap.operation()))
            .findFirst()
            .get();

    // cherry-pick the overwrite; this works because it is a fast-forward commit
    table.manageSnapshots().cherrypick(overwrite.snapshotId()).commit();

    // the overwrite should only be staged
    validateTableFiles(table, FILE_B);
  }

  /**
   * 测试场景：cherry pick overwrite fails if current has changed。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testCherryPickOverwriteFailsIfCurrentHasChanged() {
    table.newAppend().appendFile(FILE_A).commit();

    table.newOverwrite().deleteFile(FILE_A).addFile(FILE_B).stageOnly().commit();

    // add a commit after the overwrite that prevents it from being a fast-forward operation
    table.newAppend().appendFile(FILE_C).commit();

    // the overwrite should only be staged
    validateTableFiles(table, FILE_A, FILE_C);

    Snapshot overwrite =
        Streams.stream(table.snapshots())
            .filter(snap -> DataOperations.OVERWRITE.equals(snap.operation()))
            .findFirst()
            .get();

    // try to cherry-pick, which should fail because the overwrite's parent is no longer current
    Assertions.assertThatThrownBy(
            () -> table.manageSnapshots().cherrypick(overwrite.snapshotId()).commit())
        .isInstanceOf(ValidationException.class)
        .hasMessage(
            "Cannot cherry-pick snapshot 2: not append, dynamic overwrite, or fast-forward");

    // the table state should not have changed
    validateTableFiles(table, FILE_A, FILE_C);
  }

  /**
   * 测试场景：current snapshot operation。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testCurrentSnapshotOperation() {

    table.newAppend().appendFile(FILE_A).commit();
    TableMetadata base = readMetadata();
    long firstSnapshotId = base.currentSnapshot().snapshotId();

    table.newAppend().appendFile(FILE_B).set("wap.id", "123456789").stageOnly().commit();
    base = readMetadata();

    Snapshot wapSnapshot = base.snapshots().get(1);

    Assert.assertEquals("Metadata should have both snapshots", 2, base.snapshots().size());
    Assert.assertEquals(
        "Snapshot should have wap id in summary", "123456789", wapSnapshot.summary().get("wap.id"));
    Assert.assertEquals(
        "Current snapshot should be first commit's snapshot",
        firstSnapshotId,
        base.currentSnapshot().snapshotId());
    Assert.assertEquals(
        "Snapshot log should indicate number of snapshots committed", 1, base.snapshotLog().size());

    // do setCurrentSnapshot
    table.manageSnapshots().setCurrentSnapshot(wapSnapshot.snapshotId()).commit();
    base = readMetadata();

    Assert.assertEquals(
        "Current snapshot should be what we rolled back to",
        wapSnapshot.snapshotId(),
        base.currentSnapshot().snapshotId());
    Assert.assertEquals("Metadata should have both snapshots", 2, base.snapshots().size());
    Assert.assertEquals(
        "Should contain manifests for both files",
        2,
        base.currentSnapshot().allManifests(table.io()).size());
    Assert.assertEquals(
        "Should contain append from last commit",
        1,
        Iterables.size(base.currentSnapshot().addedDataFiles(table.io())));
    Assert.assertEquals(
        "Snapshot log should indicate number of snapshots committed", 2, base.snapshotLog().size());
  }

  /**
   * 测试场景：set current snapshot no wap。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testSetCurrentSnapshotNoWAP() {

    table.newAppend().appendFile(FILE_A).commit();
    TableMetadata base = readMetadata();
    Snapshot firstSnapshot = base.currentSnapshot();
    long firstSnapshotId = firstSnapshot.snapshotId();

    table.newAppend().appendFile(FILE_B).commit();

    // do setCurrentSnapshot
    table.manageSnapshots().setCurrentSnapshot(firstSnapshotId).commit();
    base = readMetadata();

    Assert.assertEquals(
        "Current snapshot should be what we rolled back to",
        firstSnapshotId,
        base.currentSnapshot().snapshotId());
    Assert.assertEquals("Metadata should have both snapshots", 2, base.snapshots().size());
    Assert.assertEquals(
        "Should contain manifests for both files",
        1,
        base.currentSnapshot().allManifests(table.io()).size());
    Assert.assertEquals(
        "Should contain append from last commit",
        1,
        Iterables.size(base.currentSnapshot().addedDataFiles(table.io())));
    Assert.assertEquals(
        "Snapshot log should indicate number of snapshots committed", 3, base.snapshotLog().size());
  }

  /**
   * 测试场景：rollback on invalid non ancestor。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testRollbackOnInvalidNonAncestor() {

    table.newAppend().appendFile(FILE_A).commit();
    TableMetadata base = readMetadata();
    long firstSnapshotId = base.currentSnapshot().snapshotId();

    table.newAppend().appendFile(FILE_B).set("wap.id", "123456789").stageOnly().commit();
    base = readMetadata();

    Snapshot wapSnapshot = base.snapshots().get(1);

    Assert.assertEquals("Metadata should have both snapshots", 2, base.snapshots().size());
    Assert.assertEquals(
        "Snapshot should have wap id in summary", "123456789", wapSnapshot.summary().get("wap.id"));
    Assert.assertEquals(
        "Current snapshot should be first commit's snapshot",
        firstSnapshotId,
        base.currentSnapshot().snapshotId());
    Assert.assertEquals(
        "Snapshot log should indicate number of snapshots committed", 1, base.snapshotLog().size());

    // do rollback
    Assertions.assertThatThrownBy(
            // rollback to snapshot that is not an ancestor
            () -> table.manageSnapshots().rollbackTo(wapSnapshot.snapshotId()).commit())
        .isInstanceOf(ValidationException.class)
        .hasMessage("Cannot roll back to snapshot, not an ancestor of the current state: 2");
    base = readMetadata();

    Assert.assertEquals(
        "Current snapshot should be what we rolled back to",
        firstSnapshotId,
        base.currentSnapshot().snapshotId());
    Assert.assertEquals("Metadata should have both snapshots", 2, base.snapshots().size());
    Assert.assertEquals(
        "Should contain manifests for one snapshot",
        1,
        base.currentSnapshot().allManifests(table.io()).size());
    Assert.assertEquals(
        "Should contain append from last commit",
        1,
        Iterables.size(base.currentSnapshot().addedDataFiles(table.io())));
    Assert.assertEquals(
        "Snapshot log should indicate number of snapshots committed", 1, base.snapshotLog().size());
  }

  /**
   * 测试场景：rollback and cherrypick。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testRollbackAndCherrypick() {
    // first snapshot
    table.newAppend().appendFile(FILE_A).commit();
    TableMetadata base = readMetadata();
    Snapshot firstSnapshot = base.currentSnapshot();
    long firstSnapshotId = firstSnapshot.snapshotId();

    // second snapshot
    table.newAppend().appendFile(FILE_B).commit();
    base = readMetadata();
    Snapshot secondSnapshot = base.currentSnapshot();

    // third snapshot
    table.newAppend().appendFile(FILE_C).commit();
    base = readMetadata();
    Snapshot thirdSnapshot = base.currentSnapshot();

    // rollback to first snapshot
    table.manageSnapshots().rollbackTo(firstSnapshotId).commit();
    base = readMetadata();
    Assert.assertEquals(
        "Should be at first snapshot", firstSnapshotId, base.currentSnapshot().snapshotId());
    Assert.assertEquals(
        "Should have all three snapshots in the system", 3, base.snapshots().size());

    // fast forward to third snapshot
    table.manageSnapshots().cherrypick(thirdSnapshot.snapshotId()).commit();
    base = readMetadata();
    Assert.assertEquals(
        "Current state should be at third snapshot", 4, base.currentSnapshot().snapshotId());

    // fast forward to 2nd snapshot
    table.manageSnapshots().cherrypick(secondSnapshot.snapshotId()).commit();
    base = readMetadata();
    Assert.assertEquals(
        "Current state should be at second snapshot", 5, base.currentSnapshot().snapshotId());
    Assert.assertEquals("Count all snapshots", 5, base.snapshots().size());
  }

  /**
   * 测试场景：rollback to time。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testRollbackToTime() {

    // first snapshot
    table.newAppend().appendFile(FILE_A).commit();
    TableMetadata base = readMetadata();
    Snapshot firstSnapshot = base.currentSnapshot();
    long firstSnapshotId = firstSnapshot.snapshotId();

    // second snapshot
    table.newAppend().appendFile(FILE_B).commit();
    base = readMetadata();
    Snapshot secondSnapshot = base.currentSnapshot();

    // third snapshot
    table.newAppend().appendFile(FILE_C).commit();

    // rollback to before the second snapshot's time
    table.manageSnapshots().rollbackToTime(secondSnapshot.timestampMillis()).commit();
    base = readMetadata();

    Assert.assertEquals(
        "Should be at first snapshot", firstSnapshotId, base.currentSnapshot().snapshotId());
    Assert.assertEquals(
        "Should have all three snapshots in the system", 3, base.snapshots().size());
  }

  /**
   * 测试场景：with cherry picking。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testWithCherryPicking() {

    table.newAppend().appendFile(FILE_A).commit();
    TableMetadata base = readMetadata();
    long firstSnapshotId = base.currentSnapshot().snapshotId();

    // first WAP commit
    table.newAppend().appendFile(FILE_B).set("wap.id", "123456789").stageOnly().commit();
    base = readMetadata();

    // pick the snapshot that's staged but not committed
    Snapshot wapSnapshot = base.snapshots().get(1);

    Assert.assertEquals("Should have both snapshots", 2, base.snapshots().size());
    Assert.assertEquals(
        "Should have first wap id in summary", "123456789", wapSnapshot.summary().get("wap.id"));
    Assert.assertEquals(
        "Current snapshot should be first commit's snapshot",
        firstSnapshotId,
        base.currentSnapshot().snapshotId());
    Assert.assertEquals(
        "Snapshot log should indicate number of snapshots committed", 1, base.snapshotLog().size());

    // cherry-pick snapshot
    table.manageSnapshots().cherrypick(wapSnapshot.snapshotId()).commit();
    base = readMetadata();

    // check if the effective current snapshot is set to the new snapshot created
    //   as a result of the cherry-pick operation
    Assert.assertEquals(
        "Current snapshot should be fast-forwarded to wap snapshot",
        wapSnapshot.snapshotId(),
        base.currentSnapshot().snapshotId());
    Assert.assertEquals("Should have two snapshots", 2, base.snapshots().size());
    Assert.assertEquals(
        "Should contain manifests for both files",
        2,
        base.currentSnapshot().allManifests(table.io()).size());
    Assert.assertEquals(
        "Should contain append from last commit",
        1,
        Iterables.size(base.currentSnapshot().addedDataFiles(table.io())));
    Assert.assertEquals(
        "Snapshot log should indicate number of snapshots committed", 2, base.snapshotLog().size());
  }

  /**
   * 测试场景：with two phase cherry picking。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testWithTwoPhaseCherryPicking() {

    table.newAppend().appendFile(FILE_A).commit();
    TableMetadata base = readMetadata();
    // load current snapshot
    Snapshot parentSnapshot = base.currentSnapshot();
    long firstSnapshotId = parentSnapshot.snapshotId();

    // first WAP commit
    table.newAppend().appendFile(FILE_B).set("wap.id", "123456789").stageOnly().commit();

    // second WAP commit
    table.newAppend().appendFile(FILE_C).set("wap.id", "987654321").stageOnly().commit();
    base = readMetadata();

    // pick the snapshot that's staged but not committed
    Snapshot wap1Snapshot = base.snapshots().get(1);
    Snapshot wap2Snapshot = base.snapshots().get(2);

    Assert.assertEquals("Should have three snapshots", 3, base.snapshots().size());
    Assert.assertEquals(
        "Should have first wap id in summary", "123456789", wap1Snapshot.summary().get("wap.id"));
    Assert.assertEquals(
        "Should have second wap id in summary", "987654321", wap2Snapshot.summary().get("wap.id"));
    Assert.assertEquals(
        "Current snapshot should be first commit's snapshot",
        firstSnapshotId,
        base.currentSnapshot().snapshotId());
    Assert.assertEquals(
        "Parent snapshot id should be same for first WAP snapshot",
        firstSnapshotId,
        wap1Snapshot.parentId().longValue());
    Assert.assertEquals(
        "Parent snapshot id should be same for second WAP snapshot",
        firstSnapshotId,
        wap2Snapshot.parentId().longValue());
    Assert.assertEquals(
        "Snapshot log should indicate number of snapshots committed", 1, base.snapshotLog().size());

    // load current snapshot
    parentSnapshot = base.currentSnapshot();
    // cherry-pick first snapshot
    table.manageSnapshots().cherrypick(wap1Snapshot.snapshotId()).commit();
    base = readMetadata();

    // check if the effective current snapshot is set to the new snapshot created
    //   as a result of the cherry-pick operation
    Assert.assertEquals(
        "Current snapshot should be set to one after wap snapshot",
        parentSnapshot.snapshotId() + 1,
        base.currentSnapshot().snapshotId());
    Assert.assertEquals(
        "Should contain manifests for both files",
        2,
        base.currentSnapshot().allManifests(table.io()).size());
    Assert.assertEquals(
        "Should contain append from last commit",
        1,
        Iterables.size(base.currentSnapshot().addedDataFiles(table.io())));
    Assert.assertEquals(
        "Parent snapshot id should change to latest snapshot before commit",
        parentSnapshot.snapshotId(),
        base.currentSnapshot().parentId().longValue());
    Assert.assertEquals(
        "Snapshot log should indicate number of snapshots committed", 2, base.snapshotLog().size());

    // load current snapshot
    parentSnapshot = base.currentSnapshot();
    // cherry-pick second snapshot
    table.manageSnapshots().cherrypick(wap2Snapshot.snapshotId()).commit();
    base = readMetadata();

    // check if the effective current snapshot is set to the new snapshot created
    //   as a result of the cherry-pick operation
    Assert.assertEquals(
        "Current snapshot should be set to one after wap snapshot",
        parentSnapshot.snapshotId() + 1 /* one fast-forwarded snapshot */ + 1,
        base.currentSnapshot().snapshotId());
    Assert.assertEquals(
        "Should contain manifests for both files",
        3,
        base.currentSnapshot().allManifests(table.io()).size());
    Assert.assertEquals(
        "Should contain append from last commit",
        1,
        Iterables.size(base.currentSnapshot().addedDataFiles(table.io())));
    Assert.assertEquals(
        "Parent snapshot id should change to latest snapshot before commit",
        parentSnapshot.snapshotId(),
        base.currentSnapshot().parentId().longValue());
    Assert.assertEquals(
        "Snapshot log should indicate number of snapshots committed", 3, base.snapshotLog().size());
  }

  /**
   * 测试场景：with commits between cherry picking。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testWithCommitsBetweenCherryPicking() {
    table.newAppend().appendFile(FILE_A).commit();
    TableMetadata base = readMetadata();
    // load current snapshot
    Snapshot parentSnapshot = base.currentSnapshot();
    long firstSnapshotId = parentSnapshot.snapshotId();

    // first WAP commit
    table.newAppend().appendFile(FILE_B).set("wap.id", "123456789").stageOnly().commit();

    // second WAP commit
    table.newAppend().appendFile(FILE_C).set("wap.id", "987654321").stageOnly().commit();
    base = readMetadata();

    // pick the snapshot that's staged but not committed
    Snapshot wap1Snapshot = base.snapshots().get(1);
    Snapshot wap2Snapshot = base.snapshots().get(2);

    Assert.assertEquals("Should have three snapshots", 3, base.snapshots().size());
    Assert.assertEquals(
        "Should have first wap id in summary", "123456789", wap1Snapshot.summary().get("wap.id"));
    Assert.assertEquals(
        "Should have second wap id in summary", "987654321", wap2Snapshot.summary().get("wap.id"));
    Assert.assertEquals(
        "Current snapshot should be first commit's snapshot",
        firstSnapshotId,
        base.currentSnapshot().snapshotId());
    Assert.assertEquals(
        "Parent snapshot id should be same for first WAP snapshot",
        firstSnapshotId,
        wap1Snapshot.parentId().longValue());
    Assert.assertEquals(
        "Parent snapshot id should be same for second WAP snapshot",
        firstSnapshotId,
        wap2Snapshot.parentId().longValue());
    Assert.assertEquals(
        "Snapshot log should indicate number of snapshots committed", 1, base.snapshotLog().size());

    // load current snapshot
    parentSnapshot = base.currentSnapshot();

    // table has new commit
    table.newAppend().appendFile(FILE_D).commit();
    base = readMetadata();

    Assert.assertEquals("Should have four snapshots", 4, base.snapshots().size());
    Assert.assertEquals(
        "Current snapshot should carry over the parent snapshot",
        parentSnapshot.snapshotId(),
        base.currentSnapshot().parentId().longValue());
    Assert.assertEquals(
        "Should contain manifests for two files",
        2,
        base.currentSnapshot().allManifests(table.io()).size());
    Assert.assertEquals(
        "Snapshot log should indicate number of snapshots committed", 2, base.snapshotLog().size());

    // load current snapshot
    parentSnapshot = base.currentSnapshot();
    // cherry-pick first snapshot
    table.manageSnapshots().cherrypick(wap1Snapshot.snapshotId()).commit();
    base = readMetadata();

    // check if the effective current snapshot is set to the new snapshot created
    //   as a result of the cherry-pick operation
    Assert.assertEquals("Should have five snapshots", 5, base.snapshots().size());
    Assert.assertEquals(
        "Current snapshot should be set to one after wap snapshot",
        parentSnapshot.snapshotId() + 1,
        base.currentSnapshot().snapshotId());
    Assert.assertEquals(
        "Should contain manifests for three files",
        3,
        base.currentSnapshot().allManifests(table.io()).size());
    Assert.assertEquals(
        "Should contain append from last commit",
        1,
        Iterables.size(base.currentSnapshot().addedDataFiles(table.io())));
    Assert.assertEquals(
        "Parent snapshot id should point to same snapshot",
        parentSnapshot.snapshotId(),
        base.currentSnapshot().parentId().longValue());
    Assert.assertEquals(
        "Snapshot log should indicate number of snapshots committed", 3, base.snapshotLog().size());

    // load current snapshot
    parentSnapshot = base.currentSnapshot();
    // cherry-pick first snapshot
    table.manageSnapshots().cherrypick(wap2Snapshot.snapshotId()).commit();
    base = readMetadata();

    // check if the effective current snapshot is set to the new snapshot created
    //   as a result of the cherry-pick operation
    Assert.assertEquals("Should have all the snapshots", 6, base.snapshots().size());
    Assert.assertEquals(
        "Current snapshot should be set to one after wap snapshot",
        parentSnapshot.snapshotId() + 1,
        base.currentSnapshot().snapshotId());
    Assert.assertEquals(
        "Should contain manifests for four files",
        4,
        base.currentSnapshot().allManifests(table.io()).size());
    Assert.assertEquals(
        "Should contain append from last commit",
        1,
        Iterables.size(base.currentSnapshot().addedDataFiles(table.io())));
    Assert.assertEquals(
        "Parent snapshot id should point to same snapshot",
        parentSnapshot.snapshotId(),
        base.currentSnapshot().parentId().longValue());
    Assert.assertEquals(
        "Snapshot log should indicate number of snapshots committed", 4, base.snapshotLog().size());
  }

  /**
   * 测试场景：with cherry picking with commit retry。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testWithCherryPickingWithCommitRetry() {

    table.newAppend().appendFile(FILE_A).commit();
    TableMetadata base = readMetadata();
    // load current snapshot
    Snapshot parentSnapshot = base.currentSnapshot();
    long firstSnapshotId = parentSnapshot.snapshotId();

    // first WAP commit
    table.newAppend().appendFile(FILE_B).set("wap.id", "123456789").stageOnly().commit();

    base = readMetadata();

    // pick the snapshot that's staged but not committed
    Snapshot wap1Snapshot = base.snapshots().get(1);

    Assert.assertEquals("Should have two snapshots", 2, base.snapshots().size());
    Assert.assertEquals(
        "Should have first wap id in summary", "123456789", wap1Snapshot.summary().get("wap.id"));
    Assert.assertEquals(
        "Current snapshot should be first commit's snapshot",
        firstSnapshotId,
        base.currentSnapshot().snapshotId());
    Assert.assertEquals(
        "Parent snapshot id should be same for first WAP snapshot",
        firstSnapshotId,
        wap1Snapshot.parentId().longValue());
    Assert.assertEquals(
        "Snapshot log should indicate number of snapshots committed", 1, base.snapshotLog().size());

    // load current snapshot
    base = readMetadata();
    parentSnapshot = base.currentSnapshot();
    // cherry-pick first snapshot
    table.ops().failCommits(3);
    table.manageSnapshots().cherrypick(wap1Snapshot.snapshotId()).commit();
    base = readMetadata();

    // check if the effective current snapshot is set to the new snapshot created
    //   as a result of the cherry-pick operation
    Assert.assertEquals(
        "Current snapshot should be set to one after wap snapshot",
        parentSnapshot.snapshotId() + 1,
        base.currentSnapshot().snapshotId());
    Assert.assertEquals(
        "Should contain manifests for both files",
        2,
        base.currentSnapshot().allManifests(table.io()).size());
    Assert.assertEquals(
        "Should not contain redundant append due to retry",
        1,
        Iterables.size(base.currentSnapshot().addedDataFiles(table.io())));
    Assert.assertEquals(
        "Parent snapshot id should change to latest snapshot before commit",
        parentSnapshot.snapshotId(),
        base.currentSnapshot().parentId().longValue());
    Assert.assertEquals(
        "Snapshot log should indicate number of snapshots committed", 2, base.snapshotLog().size());
  }

  /**
   * 测试场景：cherrypicking ancestor。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testCherrypickingAncestor() {

    table.newAppend().appendFile(FILE_A).commit();
    TableMetadata base = readMetadata();
    long firstSnapshotId = base.currentSnapshot().snapshotId();

    // first WAP commit
    table.newAppend().appendFile(FILE_B).set("wap.id", "123456789").stageOnly().commit();
    base = readMetadata();

    // pick the snapshot that's staged but not committed
    Snapshot wapSnapshot = base.snapshots().get(1);

    Assert.assertEquals("Should have both snapshots", 2, base.snapshots().size());
    Assert.assertEquals(
        "Should have first wap id in summary", "123456789", wapSnapshot.summary().get("wap.id"));
    Assert.assertEquals(
        "Current snapshot should be first commit's snapshot",
        firstSnapshotId,
        base.currentSnapshot().snapshotId());
    Assert.assertEquals(
        "Snapshot log should indicate number of snapshots committed", 1, base.snapshotLog().size());

    // cherry-pick snapshot
    table.manageSnapshots().cherrypick(wapSnapshot.snapshotId()).commit();
    base = readMetadata();
    long wapPublishedId = table.currentSnapshot().snapshotId();

    // check if the effective current snapshot is set to the new snapshot created
    //   as a result of the cherry-pick operation
    Assert.assertEquals(
        "Current snapshot should be fast-forwarded to wap snapshot",
        wapSnapshot.snapshotId(),
        base.currentSnapshot().snapshotId());
    Assert.assertEquals("Should have two snapshots", 2, base.snapshots().size());
    Assert.assertEquals(
        "Should contain manifests for both files",
        2,
        base.currentSnapshot().allManifests(table.io()).size());
    Assert.assertEquals(
        "Should contain append from last commit",
        1,
        Iterables.size(base.currentSnapshot().addedDataFiles(table.io())));
    Assert.assertEquals(
        "Snapshot log should indicate number of snapshots committed", 2, base.snapshotLog().size());

    Assertions.assertThatThrownBy(
            // duplicate cherry-pick snapshot
            () -> table.manageSnapshots().cherrypick(firstSnapshotId).commit())
        .isInstanceOf(CherrypickAncestorCommitException.class)
        .hasMessage("Cannot cherrypick snapshot 1: already an ancestor");
  }

  /**
   * 测试场景：duplicate cherrypick。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testDuplicateCherrypick() {
    table.newAppend().appendFile(FILE_A).commit();
    TableMetadata base = readMetadata();
    long firstSnapshotId = base.currentSnapshot().snapshotId();

    // stage first WAP commit
    table.newAppend().appendFile(FILE_B).set("wap.id", "123456789").stageOnly().commit();
    // stage second WAP commit with same wap.id
    table.newAppend().appendFile(FILE_C).set("wap.id", "123456789").stageOnly().commit();
    base = readMetadata();

    // pick the snapshot that's staged but not committed
    Snapshot wapSnapshot1 = base.snapshots().get(1);
    Snapshot wapSnapshot2 = base.snapshots().get(2);

    Assert.assertEquals("Should have both snapshots", 3, base.snapshots().size());
    Assert.assertEquals(
        "Should have wap id in first wap snapshot summary",
        "123456789",
        wapSnapshot1.summary().get("wap.id"));
    Assert.assertEquals(
        "Should have wap id in second wap snapshot summary",
        "123456789",
        wapSnapshot2.summary().get("wap.id"));
    Assert.assertEquals(
        "Current snapshot should be first commit's snapshot",
        firstSnapshotId,
        base.currentSnapshot().snapshotId());
    Assert.assertEquals(
        "Snapshot log should indicate number of snapshots committed", 1, base.snapshotLog().size());

    // cherry-pick snapshot
    table.manageSnapshots().cherrypick(wapSnapshot1.snapshotId()).commit();
    base = readMetadata();

    Assert.assertEquals("Should have three snapshots", 3, base.snapshots().size());
    Assert.assertEquals(
        "Should contain manifests for both files",
        2,
        base.currentSnapshot().allManifests(table.io()).size());
    Assert.assertEquals(
        "Should contain append from last commit",
        1,
        Iterables.size(base.currentSnapshot().addedDataFiles(table.io())));
    Assert.assertEquals(
        "Snapshot log should indicate number of snapshots committed", 2, base.snapshotLog().size());

    Assertions.assertThatThrownBy(
            // duplicate cherry-pick snapshot
            () -> table.manageSnapshots().cherrypick(wapSnapshot2.snapshotId()).commit())
        .isInstanceOf(DuplicateWAPCommitException.class)
        .hasMessage(
            "Duplicate request to cherry pick wap id that was published already: 123456789");
  }

  /**
   * 测试场景：non wap cherrypick。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testNonWapCherrypick() {
    table.newAppend().appendFile(FILE_A).commit();
    TableMetadata base = readMetadata();
    long firstSnapshotId = base.currentSnapshot().snapshotId();

    table.newAppend().appendFile(FILE_B).commit();
    base = readMetadata();
    long secondSnapshotId = base.currentSnapshot().snapshotId();

    table.newAppend().appendFile(FILE_C).commit();
    base = readMetadata();
    long thirdSnapshotId = base.currentSnapshot().snapshotId();

    Assert.assertEquals(
        "Should be pointing to third snapshot",
        thirdSnapshotId,
        table.currentSnapshot().snapshotId());

    // NOOP commit
    table.manageSnapshots().commit();
    Assert.assertEquals(
        "Should still be pointing to third snapshot",
        thirdSnapshotId,
        table.currentSnapshot().snapshotId());

    // Rollback to second snapshot
    table.manageSnapshots().rollbackTo(secondSnapshotId).commit();
    Assert.assertEquals(
        "Should be pointing to second snapshot",
        secondSnapshotId,
        table.currentSnapshot().snapshotId());

    // Cherrypick down to third
    table.manageSnapshots().cherrypick(thirdSnapshotId).commit();
    Assert.assertEquals(
        "Should be re-using wap snapshot after cherrypick",
        3,
        table.currentSnapshot().snapshotId());

    // try double cherrypicking of the third snapshot
    Assertions.assertThatThrownBy(
            // double cherrypicking of second snapshot
            () -> table.manageSnapshots().cherrypick(thirdSnapshotId).commit())
        .isInstanceOf(CherrypickAncestorCommitException.class)
        .hasMessage("Cannot cherrypick snapshot 3: already an ancestor");

    // try cherrypicking an ancestor
    Assertions.assertThatThrownBy(
            // double cherrypicking of second snapshot
            () -> table.manageSnapshots().cherrypick(firstSnapshotId).commit())
        .isInstanceOf(CherrypickAncestorCommitException.class)
        .hasMessage(
            String.format("Cannot cherrypick snapshot %s: already an ancestor", firstSnapshotId));
  }
}
