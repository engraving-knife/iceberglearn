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

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 文件级说明：测试 TestSnapshotRef 的功能。
 *
 * <p>所属模块：iceberg-api。职责：验证 TestSnapshotRef 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 JUnit 框架，通过构造输入、调用方法、断言结果来覆盖功能点。
 */
public class TestSnapshotRef {

  /**
   * 测试场景：Tag Default。
   *
   * <p>验证该方法在 Tag Default 条件下的行为是否符合预期。
   */
  @Test
  public void testTagDefault() {
    SnapshotRef ref = SnapshotRef.tagBuilder(1L).build();
    assertThat(ref.snapshotId()).isEqualTo(1L);
    assertThat(ref.type()).isEqualTo(SnapshotRefType.TAG);
    assertThat(ref.minSnapshotsToKeep()).isNull();
    assertThat(ref.maxSnapshotAgeMs()).isNull();
    assertThat(ref.maxRefAgeMs()).isNull();
  }

  /**
   * 测试场景：Branch Default。
   *
   * <p>验证该方法在 Branch Default 条件下的行为是否符合预期。
   */
  @Test
  public void testBranchDefault() {
    SnapshotRef ref = SnapshotRef.branchBuilder(1L).build();
    assertThat(ref.snapshotId()).isEqualTo(1L);
    assertThat(ref.type()).isEqualTo(SnapshotRefType.BRANCH);
    assertThat(ref.minSnapshotsToKeep()).isNull();
    assertThat(ref.maxSnapshotAgeMs()).isNull();
  }

  /**
   * 测试场景：Tag With Override。
   *
   * <p>验证该方法在 Tag With Override 条件下的行为是否符合预期。
   */
  @Test
  public void testTagWithOverride() {
    SnapshotRef ref = SnapshotRef.branchBuilder(1L).maxRefAgeMs(10L).build();
    assertThat(ref.snapshotId()).isEqualTo(1L);
    assertThat(ref.type()).isEqualTo(SnapshotRefType.BRANCH);
    assertThat((long) ref.maxRefAgeMs()).isEqualTo(10L);
  }

  /**
   * 测试场景：Branch With Override。
   *
   * <p>验证该方法在 Branch With Override 条件下的行为是否符合预期。
   */
  @Test
  public void testBranchWithOverride() {
    SnapshotRef ref =
        SnapshotRef.branchBuilder(1L)
            .minSnapshotsToKeep(10)
            .maxSnapshotAgeMs(20L)
            .maxRefAgeMs(30L)
            .build();
    assertThat(ref.snapshotId()).isEqualTo(1L);
    assertThat(ref.type()).isEqualTo(SnapshotRefType.BRANCH);
    assertThat((int) ref.minSnapshotsToKeep()).isEqualTo(10);
    assertThat((long) ref.maxSnapshotAgeMs()).isEqualTo(20L);
    assertThat((long) ref.maxRefAgeMs()).isEqualTo(30L);
  }

  /**
   * 测试场景：No Type Failure。
   *
   * <p>验证该方法在 No Type Failure 条件下的行为是否符合预期。
   */
  @Test
  public void testNoTypeFailure() {
    Assertions.assertThatThrownBy(() -> SnapshotRef.builderFor(1L, null).build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Snapshot reference type must not be null");
  }

  /**
   * 测试场景：Tag Build Failures。
   *
   * <p>验证该方法在 Tag Build Failures 条件下的行为是否符合预期。
   */
  @Test
  public void testTagBuildFailures() {
    Assertions.assertThatThrownBy(() -> SnapshotRef.tagBuilder(1L).maxRefAgeMs(-1L).build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Max reference age must be greater than 0");

    Assertions.assertThatThrownBy(() -> SnapshotRef.tagBuilder(1L).minSnapshotsToKeep(2).build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Tags do not support setting minSnapshotsToKeep");

    Assertions.assertThatThrownBy(() -> SnapshotRef.tagBuilder(1L).maxSnapshotAgeMs(2L).build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Tags do not support setting maxSnapshotAgeMs");
  }

  /**
   * 测试场景：Branch Build Failures。
   *
   * <p>验证该方法在 Branch Build Failures 条件下的行为是否符合预期。
   */
  @Test
  public void testBranchBuildFailures() {
    Assertions.assertThatThrownBy(() -> SnapshotRef.branchBuilder(1L).maxSnapshotAgeMs(-1L).build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Max snapshot age must be greater than 0 ms");

    Assertions.assertThatThrownBy(
            () -> SnapshotRef.branchBuilder(1L).minSnapshotsToKeep(-1).build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Min snapshots to keep must be greater than 0");

    Assertions.assertThatThrownBy(() -> SnapshotRef.branchBuilder(1L).maxRefAgeMs(-1L).build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Max reference age must be greater than 0");
  }
}
