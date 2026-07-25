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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableSet;
import org.apache.iceberg.view.ViewMetadata;
import org.apache.iceberg.view.ViewVersion;

/**
 * 表或视图元数据变更的抽象表示：每个内部类表示一种具体的元数据变更操作。
 *
 * <p>所属模块：iceberg-core（元数据变更模型层，被 api 与 core 共享）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>把对表/视图元数据的各种修改（加 schema、改分区、加快照、设属性等）抽象为可序列化的 更新对象；
 *   <li>通过 {@link #applyTo} 方法把变更应用到 {@link TableMetadata.Builder} 或 {@link
 *       ViewMetadata.Builder}，支持"收集变更 → 批量应用"的事务模式。
 * </ul>
 *
 * <p>设计意图：采用命令模式（Command Pattern），每种元数据变更是独立的更新对象， 便于在事务中累积、审计、回放。默认 applyTo 抛出
 * UnsupportedOperationException， 子类按需覆盖对应的目标方法。
 *
 * <p>上下游关系：被 {@link BaseTransaction} 等事务实现收集与应用；被 catalog 在创建/修改表时 构造。
 */
public interface MetadataUpdate extends Serializable {
  /**
   * 把本变更应用到表元数据构建器。
   *
   * <p>默认抛出 {@link UnsupportedOperationException}，子类按需覆盖。
   *
   * @param metadataBuilder 表元数据构建器
   * @throws UnsupportedOperationException 若本变更不适用于表
   */
  default void applyTo(TableMetadata.Builder metadataBuilder) {
    throw new UnsupportedOperationException(
        String.format("Cannot apply update %s to a table", this.getClass().getSimpleName()));
  }

  /**
   * 把本变更应用到视图元数据构建器。
   *
   * <p>默认抛出 {@link UnsupportedOperationException}，子类按需覆盖。
   *
   * @param viewMetadataBuilder 视图元数据构建器
   * @throws UnsupportedOperationException 若本变更不适用于视图
   */
  default void applyTo(ViewMetadata.Builder viewMetadataBuilder) {
    throw new UnsupportedOperationException(
        String.format("Cannot apply update %s to a view", this.getClass().getSimpleName()));
  }

  /** 变更：分配/设置 UUID。 */
  class AssignUUID implements MetadataUpdate {
    private final String uuid;

    public AssignUUID(String uuid) {
      this.uuid = uuid;
    }

    public String uuid() {
      return uuid;
    }

    @Override
    public void applyTo(TableMetadata.Builder metadataBuilder) {
      metadataBuilder.assignUUID(uuid);
    }

    @Override
    public void applyTo(ViewMetadata.Builder viewMetadataBuilder) {
      viewMetadataBuilder.assignUUID(uuid);
    }
  }

  /** 变更：升级格式版本。 */
  class UpgradeFormatVersion implements MetadataUpdate {
    private final int formatVersion;

    public UpgradeFormatVersion(int formatVersion) {
      this.formatVersion = formatVersion;
    }

    public int formatVersion() {
      return formatVersion;
    }

    @Override
    public void applyTo(TableMetadata.Builder metadataBuilder) {
      metadataBuilder.upgradeFormatVersion(formatVersion);
    }

    @Override
    public void applyTo(ViewMetadata.Builder viewMetadataBuilder) {
      viewMetadataBuilder.upgradeFormatVersion(formatVersion);
    }
  }

  /** 变更：添加 schema。 */
  class AddSchema implements MetadataUpdate {
    private final Schema schema;
    private final int lastColumnId;

    public AddSchema(Schema schema, int lastColumnId) {
      this.schema = schema;
      this.lastColumnId = lastColumnId;
    }

    public Schema schema() {
      return schema;
    }

    public int lastColumnId() {
      return lastColumnId;
    }

    @Override
    public void applyTo(TableMetadata.Builder metadataBuilder) {
      metadataBuilder.addSchema(schema, lastColumnId);
    }

    @Override
    public void applyTo(ViewMetadata.Builder viewMetadataBuilder) {
      viewMetadataBuilder.addSchema(schema);
    }
  }

  /** 变更：设置当前 schema。 */
  class SetCurrentSchema implements MetadataUpdate {
    private final int schemaId;

    public SetCurrentSchema(int schemaId) {
      this.schemaId = schemaId;
    }

    public int schemaId() {
      return schemaId;
    }

    @Override
    public void applyTo(TableMetadata.Builder metadataBuilder) {
      metadataBuilder.setCurrentSchema(schemaId);
    }
  }

  /** 变更：添加分区规格。 */
  class AddPartitionSpec implements MetadataUpdate {
    private final UnboundPartitionSpec spec;

    public AddPartitionSpec(PartitionSpec spec) {
      this(spec.toUnbound());
    }

    public AddPartitionSpec(UnboundPartitionSpec spec) {
      this.spec = spec;
    }

    public UnboundPartitionSpec spec() {
      return spec;
    }

    @Override
    public void applyTo(TableMetadata.Builder metadataBuilder) {
      metadataBuilder.addPartitionSpec(spec);
    }
  }

  /** 变更：设置默认分区规格。 */
  class SetDefaultPartitionSpec implements MetadataUpdate {
    private final int specId;

    public SetDefaultPartitionSpec(int specId) {
      this.specId = specId;
    }

    public int specId() {
      return specId;
    }

    @Override
    public void applyTo(TableMetadata.Builder metadataBuilder) {
      metadataBuilder.setDefaultPartitionSpec(specId);
    }
  }

  /** 变更：添加排序顺序。 */
  class AddSortOrder implements MetadataUpdate {
    private final UnboundSortOrder sortOrder;

    public AddSortOrder(SortOrder sortOrder) {
      this(sortOrder.toUnbound());
    }

    public AddSortOrder(UnboundSortOrder sortOrder) {
      this.sortOrder = sortOrder;
    }

    public UnboundSortOrder sortOrder() {
      return sortOrder;
    }

    @Override
    public void applyTo(TableMetadata.Builder metadataBuilder) {
      metadataBuilder.addSortOrder(sortOrder);
    }
  }

  /** 变更：设置默认排序顺序。 */
  class SetDefaultSortOrder implements MetadataUpdate {
    private final int sortOrderId;

    public SetDefaultSortOrder(int sortOrderId) {
      this.sortOrderId = sortOrderId;
    }

    public int sortOrderId() {
      return sortOrderId;
    }

    @Override
    public void applyTo(TableMetadata.Builder metadataBuilder) {
      metadataBuilder.setDefaultSortOrder(sortOrderId);
    }
  }

  /** 变更：设置快照统计文件。 */
  class SetStatistics implements MetadataUpdate {
    private final long snapshotId;
    private final StatisticsFile statisticsFile;

    public SetStatistics(long snapshotId, StatisticsFile statisticsFile) {
      this.snapshotId = snapshotId;
      this.statisticsFile = statisticsFile;
    }

    public long snapshotId() {
      return snapshotId;
    }

    public StatisticsFile statisticsFile() {
      return statisticsFile;
    }

    @Override
    public void applyTo(TableMetadata.Builder metadataBuilder) {
      metadataBuilder.setStatistics(snapshotId, statisticsFile);
    }
  }

  /** 变更：移除快照统计文件。 */
  class RemoveStatistics implements MetadataUpdate {
    private final long snapshotId;

    public RemoveStatistics(long snapshotId) {
      this.snapshotId = snapshotId;
    }

    public long snapshotId() {
      return snapshotId;
    }

    @Override
    public void applyTo(TableMetadata.Builder metadataBuilder) {
      metadataBuilder.removeStatistics(snapshotId);
    }
  }

  /** 变更：添加快照。 */
  class AddSnapshot implements MetadataUpdate {
    private final Snapshot snapshot;

    public AddSnapshot(Snapshot snapshot) {
      this.snapshot = snapshot;
    }

    public Snapshot snapshot() {
      return snapshot;
    }

    @Override
    public void applyTo(TableMetadata.Builder metadataBuilder) {
      metadataBuilder.addSnapshot(snapshot);
    }
  }

  /** 变更：移除快照。 */
  class RemoveSnapshot implements MetadataUpdate {
    private final long snapshotId;

    public RemoveSnapshot(long snapshotId) {
      this.snapshotId = snapshotId;
    }

    public long snapshotId() {
      return snapshotId;
    }

    @Override
    public void applyTo(TableMetadata.Builder metadataBuilder) {
      metadataBuilder.removeSnapshots(ImmutableSet.of(snapshotId));
    }
  }

  /** 变更：移除快照引用。 */
  class RemoveSnapshotRef implements MetadataUpdate {
    private final String refName;

    public RemoveSnapshotRef(String refName) {
      this.refName = refName;
    }

    public String name() {
      return refName;
    }

    @Override
    public void applyTo(TableMetadata.Builder metadataBuilder) {
      metadataBuilder.removeRef(refName);
    }
  }

  /** 变更：设置快照引用（分支或标签）。 */
  class SetSnapshotRef implements MetadataUpdate {
    private final String refName;
    private final Long snapshotId;
    private final SnapshotRefType type;
    private Integer minSnapshotsToKeep;
    private Long maxSnapshotAgeMs;
    private Long maxRefAgeMs;

    public SetSnapshotRef(
        String refName,
        Long snapshotId,
        SnapshotRefType type,
        Integer minSnapshotsToKeep,
        Long maxSnapshotAgeMs,
        Long maxRefAgeMs) {
      this.refName = refName;
      this.snapshotId = snapshotId;
      this.type = type;
      this.minSnapshotsToKeep = minSnapshotsToKeep;
      this.maxSnapshotAgeMs = maxSnapshotAgeMs;
      this.maxRefAgeMs = maxRefAgeMs;
    }

    public String name() {
      return refName;
    }

    public String type() {
      return type.name().toLowerCase(Locale.ROOT);
    }

    public long snapshotId() {
      return snapshotId;
    }

    public Integer minSnapshotsToKeep() {
      return minSnapshotsToKeep;
    }

    public Long maxSnapshotAgeMs() {
      return maxSnapshotAgeMs;
    }

    public Long maxRefAgeMs() {
      return maxRefAgeMs;
    }

    @Override
    public void applyTo(TableMetadata.Builder metadataBuilder) {
      SnapshotRef ref =
          SnapshotRef.builderFor(snapshotId, type)
              .minSnapshotsToKeep(minSnapshotsToKeep)
              .maxSnapshotAgeMs(maxSnapshotAgeMs)
              .maxRefAgeMs(maxRefAgeMs)
              .build();
      metadataBuilder.setRef(refName, ref);
    }
  }

  /** 变更：设置表/视图属性。 */
  class SetProperties implements MetadataUpdate {
    private final Map<String, String> updated;

    public SetProperties(Map<String, String> updated) {
      this.updated = updated;
    }

    public Map<String, String> updated() {
      return updated;
    }

    @Override
    public void applyTo(TableMetadata.Builder metadataBuilder) {
      metadataBuilder.setProperties(updated);
    }

    @Override
    public void applyTo(ViewMetadata.Builder viewMetadataBuilder) {
      viewMetadataBuilder.setProperties(updated);
    }
  }

  /** 变更：移除表/视图属性。 */
  class RemoveProperties implements MetadataUpdate {
    private final Set<String> removed;

    public RemoveProperties(Set<String> removed) {
      this.removed = removed;
    }

    public Set<String> removed() {
      return removed;
    }

    @Override
    public void applyTo(TableMetadata.Builder metadataBuilder) {
      metadataBuilder.removeProperties(removed);
    }

    @Override
    public void applyTo(ViewMetadata.Builder viewMetadataBuilder) {
      viewMetadataBuilder.removeProperties(removed);
    }
  }

  /** 变更：设置表/视图位置。 */
  class SetLocation implements MetadataUpdate {
    private final String location;

    public SetLocation(String location) {
      this.location = location;
    }

    public String location() {
      return location;
    }

    @Override
    public void applyTo(TableMetadata.Builder metadataBuilder) {
      metadataBuilder.setLocation(location);
    }

    @Override
    public void applyTo(ViewMetadata.Builder viewMetadataBuilder) {
      viewMetadataBuilder.setLocation(location);
    }
  }

  /** 变更：添加视图版本。 */
  class AddViewVersion implements MetadataUpdate {
    private final ViewVersion viewVersion;

    public AddViewVersion(ViewVersion viewVersion) {
      this.viewVersion = viewVersion;
    }

    public ViewVersion viewVersion() {
      return viewVersion;
    }

    @Override
    public void applyTo(ViewMetadata.Builder viewMetadataBuilder) {
      viewMetadataBuilder.addVersion(viewVersion);
    }
  }

  /** 变更：设置当前视图版本。 */
  class SetCurrentViewVersion implements MetadataUpdate {
    private final int versionId;

    public SetCurrentViewVersion(int versionId) {
      this.versionId = versionId;
    }

    public int versionId() {
      return versionId;
    }

    @Override
    public void applyTo(ViewMetadata.Builder viewMetadataBuilder) {
      viewMetadataBuilder.setCurrentVersionId(versionId);
    }
  }
}
