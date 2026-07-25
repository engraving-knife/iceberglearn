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

import java.io.Serializable;
import java.util.Objects;
import org.apache.iceberg.relocated.com.google.common.base.MoreObjects;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;

/**
 * 文件级说明：快照引用（snapshot reference）类，表示指向某个快照的命名引用（branch 或 tag）。
 *
 * <p>所属模块：iceberg-api（核心接口层，由 core 与 catalog 模块使用）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>封装快照引用的元信息：快照 ID、引用类型（branch/tag）、保留策略 （minSnapshotsToKeep、maxSnapshotAgeMs、maxRefAgeMs）。
 *   <li>提供 {@link Builder} 构建器，校验 tag 不支持快照保留策略等约束。
 *   <li>实现 {@link Serializable} 以支持序列化。
 * </ul>
 *
 * <p>设计意图：Iceberg 通过引用机制支持分支（branch，可移动指针，类似 Git branch）与标签 （tag，不可变快照指针，类似 Git
 * tag）。引用携带保留策略，指导快照过期任务决定何时清理 该引用下的旧快照。{@code main} 分支是表的默认分支。Builder 中对 tag 类型禁止设置
 * minSnapshotsToKeep/maxSnapshotAgeMs，因为 tag 本质指向单个快照。
 *
 * <p>上下游关系：由表元数据管理，被快照过期、扫描（useRef）、分支管理等组件使用。
 */
public class SnapshotRef implements Serializable {

  /** 主分支名称常量。 */
  public static final String MAIN_BRANCH = "main";

  private final long snapshotId;
  private final SnapshotRefType type;
  private final Integer minSnapshotsToKeep;
  private final Long maxSnapshotAgeMs;
  private final Long maxRefAgeMs;

  private SnapshotRef(
      long snapshotId,
      SnapshotRefType type,
      Integer minSnapshotsToKeep,
      Long maxSnapshotAgeMs,
      Long maxRefAgeMs) {
    this.snapshotId = snapshotId;
    this.type = type;
    this.minSnapshotsToKeep = minSnapshotsToKeep;
    this.maxSnapshotAgeMs = maxSnapshotAgeMs;
    this.maxRefAgeMs = maxRefAgeMs;
  }

  /** 返回本引用指向的快照 ID。 */
  public long snapshotId() {
    return snapshotId;
  }

  /** 返回本引用的类型（{@link SnapshotRefType#BRANCH} 或 {@link SnapshotRefType#TAG}）。 */
  public SnapshotRefType type() {
    return type;
  }

  /** 返回本引用是否为分支（branch）。 */
  public boolean isBranch() {
    return type == SnapshotRefType.BRANCH;
  }

  /** 返回本引用是否为标签（tag）。 */
  public boolean isTag() {
    return type == SnapshotRefType.TAG;
  }

  /** 返回分支上需保留的最小快照数量；仅 branch 适用，tag 返回 null。 */
  public Integer minSnapshotsToKeep() {
    return minSnapshotsToKeep;
  }

  /** 返回快照的最大存活时间（毫秒）；仅 branch 适用，tag 返回 null。 */
  public Long maxSnapshotAgeMs() {
    return maxSnapshotAgeMs;
  }

  /** 返回本引用的最大存活时间（毫秒）；过期后引用本身将被删除。 */
  public Long maxRefAgeMs() {
    return maxRefAgeMs;
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }

    if (!(other instanceof SnapshotRef)) {
      return false;
    }

    SnapshotRef ref = (SnapshotRef) other;
    return ref.snapshotId == snapshotId
        && Objects.equals(ref.type(), type)
        && Objects.equals(ref.maxRefAgeMs(), maxRefAgeMs)
        && Objects.equals(ref.minSnapshotsToKeep(), minSnapshotsToKeep)
        && Objects.equals(ref.maxSnapshotAgeMs(), maxSnapshotAgeMs);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        this.snapshotId,
        this.type,
        this.maxRefAgeMs,
        this.maxSnapshotAgeMs,
        this.minSnapshotsToKeep);
  }

  /** 创建一个指向指定快照的 tag 引用构建器。 */
  public static Builder tagBuilder(long snapshotId) {
    return builderFor(snapshotId, SnapshotRefType.TAG);
  }

  /** 创建一个指向指定快照的 branch 引用构建器。 */
  public static Builder branchBuilder(long snapshotId) {
    return builderFor(snapshotId, SnapshotRefType.BRANCH);
  }

  /** 基于已有引用创建构建器，继承其全部属性（类型、快照 ID、保留策略）。 */
  public static Builder builderFrom(SnapshotRef ref) {
    return new Builder(ref.type(), ref.snapshotId())
        .minSnapshotsToKeep(ref.minSnapshotsToKeep())
        .maxSnapshotAgeMs(ref.maxSnapshotAgeMs())
        .maxRefAgeMs(ref.maxRefAgeMs());
  }

  /**
   * 基于已有引用创建构建器，继承其保留策略，但将引用指向新的快照 ID。
   *
   * @param ref 被继承属性的引用
   * @param snapshotId 新指向的快照 ID
   * @return 引用构建器，保留策略与 ref 相同，但指向指定的快照 ID
   */
  public static Builder builderFrom(SnapshotRef ref, long snapshotId) {
    return new Builder(ref.type(), snapshotId)
        .minSnapshotsToKeep(ref.minSnapshotsToKeep())
        .maxSnapshotAgeMs(ref.maxSnapshotAgeMs())
        .maxRefAgeMs(ref.maxRefAgeMs());
  }

  /** 通用工厂方法：创建一个指向指定快照、指定类型的引用构建器。 */
  public static Builder builderFor(long snapshotId, SnapshotRefType type) {
    return new Builder(type, snapshotId);
  }

  /** 快照引用构建器，用于链式配置引用的保留策略并构建 {@link SnapshotRef}。 */
  public static class Builder {

    private final SnapshotRefType type;
    private final long snapshotId;
    private Integer minSnapshotsToKeep;
    private Long maxSnapshotAgeMs;
    private Long maxRefAgeMs;

    /**
     * 构造构建器，指定引用类型与快照 ID。
     *
     * @param type 引用类型，不能为 null
     * @param snapshotId 快照 ID
     */
    Builder(SnapshotRefType type, long snapshotId) {
      Preconditions.checkArgument(type != null, "Snapshot reference type must not be null");
      this.type = type;
      this.snapshotId = snapshotId;
    }

    /**
     * 设置分支上需保留的最小快照数量。
     *
     * <p>tag 类型不支持设置此属性；值必须大于 0。
     *
     * @param value 最小保留快照数，null 表示不限制
     * @return this，便于链式调用
     */
    public Builder minSnapshotsToKeep(Integer value) {
      Preconditions.checkArgument(
          value == null || !type.equals(SnapshotRefType.TAG),
          "Tags do not support setting minSnapshotsToKeep");
      Preconditions.checkArgument(
          value == null || value > 0, "Min snapshots to keep must be greater than 0");
      this.minSnapshotsToKeep = value;
      return this;
    }

    /**
     * 设置快照的最大存活时间（毫秒）。
     *
     * <p>tag 类型不支持设置此属性；值必须大于 0。
     *
     * @param value 最大存活时间（毫秒），null 表示不限制
     * @return this，便于链式调用
     */
    public Builder maxSnapshotAgeMs(Long value) {
      Preconditions.checkArgument(
          value == null || !type.equals(SnapshotRefType.TAG),
          "Tags do not support setting maxSnapshotAgeMs");
      Preconditions.checkArgument(
          value == null || value > 0, "Max snapshot age must be greater than 0 ms");
      this.maxSnapshotAgeMs = value;
      return this;
    }

    /**
     * 设置引用本身的最大存活时间（毫秒），过期后引用将被删除。
     *
     * @param value 最大引用存活时间（毫秒），null 表示不限制；若非 null 必须大于 0
     * @return this，便于链式调用
     */
    public Builder maxRefAgeMs(Long value) {
      Preconditions.checkArgument(
          value == null || value > 0, "Max reference age must be greater than 0");
      this.maxRefAgeMs = value;
      return this;
    }

    /** 构建并返回 {@link SnapshotRef} 实例。 */
    public SnapshotRef build() {
      return new SnapshotRef(snapshotId, type, minSnapshotsToKeep, maxSnapshotAgeMs, maxRefAgeMs);
    }
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("snapshotId", snapshotId)
        .add("type", type)
        .add("minSnapshotsToKeep", minSnapshotsToKeep)
        .add("maxSnapshotAgeMs", maxSnapshotAgeMs)
        .add("maxRefAgeMs", maxRefAgeMs)
        .toString();
  }
}
