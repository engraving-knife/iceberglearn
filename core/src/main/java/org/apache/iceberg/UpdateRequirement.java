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

import org.apache.iceberg.exceptions.CommitFailedException;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;

/**
 * 表示一次 {@link MetadataUpdate} 提交时所必须满足的前提条件。
 *
 * <p>所属模块：iceberg-core；层次定位：表元数据提交协议核心接口。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>定义提交时对基础 {@link TableMetadata} 的前置断言条件
 *   <li>在 commit 落地前调用 {@link #validate(TableMetadata)} 校验基础元数据是否符合预期
 *   <li>校验失败时抛出 {@link CommitFailedException}，触发提交重试或回滚
 *   <li>为乐观并发控制提供"期望状态"载体，配合 {@link MetadataUpdate} 一起完成原子提交
 * </ul>
 *
 * <p>设计意图：采用"Requirement + Update"二元模型将"期望状态校验"与"实际状态变更"解耦， 使得同一份变更可以在不同的基础元数据上以乐观锁方式重试提交；每个
 * Requirement 都是不可变值对象， 线程安全且易于序列化到提交请求中。
 *
 * <p>上下游关系：由 {@link org.apache.iceberg.Transaction}、各类 commit 操作（如 {@code AppendFiles}、 {@code
 * RewriteFiles} 等）构造并组装到提交请求中；由 catalog 实现或 {@code CommitCallback} 在真正落库前调用 进行校验。
 */
public interface UpdateRequirement {
  /**
   * 在给定的基础表元数据上校验本 Requirement 是否满足。
   *
   * <p>逻辑：当条件不满足时抛出 {@link CommitFailedException}，调用方据此决定重试或终止提交。
   *
   * @param base 当前的基础 {@link TableMetadata}，可能为 {@code null}（例如建表场景）
   */
  void validate(TableMetadata base);

  /** 要求表在提交时还不存在的 Requirement，典型用于原子创建表场景。 */
  class AssertTableDoesNotExist implements UpdateRequirement {
    /** 默认构造器。 */
    public AssertTableDoesNotExist() {}

    /**
     * 校验基础表元数据为空。
     *
     * @param base 当前基础 {@link TableMetadata}，建表场景下应为 {@code null}
     */
    @Override
    public void validate(TableMetadata base) {
      if (base != null) {
        throw new CommitFailedException("Requirement failed: table already exists");
      }
    }
  }

  /**
   * 要求提交时的表 UUID 与指定值匹配的 Requirement，用于防止并发修改导致表身份被替换。
   *
   * <p>设计意图：UUID 在表生命周期内保持不变，校验 UUID 可作为表身份一致性的最强保证。
   */
  class AssertTableUUID implements UpdateRequirement {
    private final String uuid;

    /**
     * 构造 UUID 断言。
     *
     * @param uuid 期望的表 UUID，不能为 null
     */
    public AssertTableUUID(String uuid) {
      Preconditions.checkArgument(uuid != null, "Invalid required UUID: null");
      this.uuid = uuid;
    }

    /** 返回 期望的表 UUID。 */
    public String uuid() {
      return uuid;
    }

    /**
     * 校验基础表元数据的 UUID 与期望一致（忽略大小写）。
     *
     * <p>逻辑：当 UUID 不匹配时抛出 {@link CommitFailedException}，说明表被替换或重建。
     *
     * @param base 当前基础 {@link TableMetadata}，不能为 null
     */
    @Override
    public void validate(TableMetadata base) {
      if (!uuid.equalsIgnoreCase(base.uuid())) {
        throw new CommitFailedException(
            "Requirement failed: UUID does not match: expected %s != %s", base.uuid(), uuid);
      }
    }
  }

  /**
   * 要求指定分支或标签引用当前指向的快照 ID 与期望值一致的 Requirement。
   *
   * <p>设计意图：通过比较快照 ID 实现对单个引用的乐观并发控制，避免在并发提交时 覆盖他人对同一分支/标签的更新。{@code snapshotId} 为 null
   * 表示要求该引用尚不存在。
   */
  class AssertRefSnapshotID implements UpdateRequirement {
    private final String name;
    private final Long snapshotId;

    /**
     * 构造引用快照 ID 断言。
     *
     * @param name 引用名称（分支或标签名）
     * @param snapshotId 期望的快照 ID，{@code null} 表示该引用不应存在
     */
    public AssertRefSnapshotID(String name, Long snapshotId) {
      this.name = name;
      this.snapshotId = snapshotId;
    }

    /** 返回 引用名称。 */
    public String refName() {
      return name;
    }

    /** 返回 期望的快照 ID，{@code null} 表示要求引用不存在。 */
    public Long snapshotId() {
      return snapshotId;
    }

    /**
     * 校验基础元数据中指定引用的快照 ID 是否符合期望。
     *
     * <p>逻辑：
     *
     * <ol>
     *   <li>若引用已存在：当期望 ID 为 null 时，说明期望引用不存在，校验失败； 否则比较实际与期望 ID，不一致则失败
     *   <li>若引用不存在：当期望 ID 非 null 时，说明引用被并发删除，校验失败
     * </ol>
     *
     * @param base 当前基础 {@link TableMetadata}
     */
    @Override
    public void validate(TableMetadata base) {
      SnapshotRef ref = base.ref(name);
      if (ref != null) {
        String type = ref.isBranch() ? "branch" : "tag";
        if (snapshotId == null) {
          // a null snapshot ID means the ref should not exist already
          throw new CommitFailedException(
              "Requirement failed: %s %s was created concurrently", type, name);
        } else if (snapshotId != ref.snapshotId()) {
          throw new CommitFailedException(
              "Requirement failed: %s %s has changed: expected id %s != %s",
              type, name, snapshotId, ref.snapshotId());
        }
      } else if (snapshotId != null) {
        throw new CommitFailedException(
            "Requirement failed: branch or tag %s is missing, expected %s", name, snapshotId);
      }
    }
  }

  /**
   * 要求表已分配的最大字段 ID 与期望值一致的 Requirement。
   *
   * <p>设计意图：字段 ID 是 Iceberg schema 演进过程中保持列身份稳定的关键， 通过校验最大字段 ID 可避免并发 schema 变更导致的 ID 冲突或回退。
   */
  class AssertLastAssignedFieldId implements UpdateRequirement {
    private final int lastAssignedFieldId;

    /**
     * 构造最大字段 ID 断言。
     *
     * @param lastAssignedFieldId 期望的已分配最大字段 ID
     */
    public AssertLastAssignedFieldId(int lastAssignedFieldId) {
      this.lastAssignedFieldId = lastAssignedFieldId;
    }

    /** 返回 期望的已分配最大字段 ID。 */
    public int lastAssignedFieldId() {
      return lastAssignedFieldId;
    }

    /**
     * 校验基础元数据中已分配的最大字段 ID 是否符合期望。
     *
     * <p>逻辑：仅当 base 非 null 时比较，不一致则抛出 {@link CommitFailedException}。
     *
     * @param base 当前基础 {@link TableMetadata}，可为 {@code null}
     */
    @Override
    public void validate(TableMetadata base) {
      if (base != null && base.lastColumnId() != lastAssignedFieldId) {
        throw new CommitFailedException(
            "Requirement failed: last assigned field id changed: expected id %s != %s",
            lastAssignedFieldId, base.lastColumnId());
      }
    }
  }

  /**
   * 要求表当前生效的 schema ID 与期望值一致的 Requirement。
   *
   * <p>设计意图：防止并发切换 schema 或并发 schema 变更导致提交基于过时的 schema 落库。
   */
  class AssertCurrentSchemaID implements UpdateRequirement {
    private final int schemaId;

    /**
     * 构造当前 schema ID 断言。
     *
     * @param schemaId 期望的当前 schema ID
     */
    public AssertCurrentSchemaID(int schemaId) {
      this.schemaId = schemaId;
    }

    /** 返回 期望的当前 schema ID。 */
    public int schemaId() {
      return schemaId;
    }

    /**
     * 校验基础元数据的当前 schema ID 是否符合期望。
     *
     * @param base 当前基础 {@link TableMetadata}，不能为 null
     */
    @Override
    public void validate(TableMetadata base) {
      if (schemaId != base.currentSchemaId()) {
        throw new CommitFailedException(
            "Requirement failed: current schema changed: expected id %s != %s",
            schemaId, base.currentSchemaId());
      }
    }
  }

  /**
   * 要求表已分配的最大分区字段 ID 与期望值一致的 Requirement。
   *
   * <p>设计意图：分区字段 ID 同样是 schema 演进过程中需保持稳定的标识，校验它可避免 并发分区规格（spec）变更导致的 ID 冲突。
   */
  class AssertLastAssignedPartitionId implements UpdateRequirement {
    private final int lastAssignedPartitionId;

    /**
     * 构造最大分区字段 ID 断言。
     *
     * @param lastAssignedPartitionId 期望的已分配最大分区字段 ID
     */
    public AssertLastAssignedPartitionId(int lastAssignedPartitionId) {
      this.lastAssignedPartitionId = lastAssignedPartitionId;
    }

    /** 返回 期望的已分配最大分区字段 ID。 */
    public int lastAssignedPartitionId() {
      return lastAssignedPartitionId;
    }

    /**
     * 校验基础元数据中已分配的最大分区字段 ID 是否符合期望。
     *
     * <p>逻辑：仅当 base 非 null 时比较，不一致则抛出 {@link CommitFailedException}。
     *
     * @param base 当前基础 {@link TableMetadata}，可为 {@code null}
     */
    @Override
    public void validate(TableMetadata base) {
      if (base != null && base.lastAssignedPartitionId() != lastAssignedPartitionId) {
        throw new CommitFailedException(
            "Requirement failed: last assigned partition id changed: expected id %s != %s",
            lastAssignedPartitionId, base.lastAssignedPartitionId());
      }
    }
  }

  /**
   * 要求表默认分区规格（spec）ID 与期望值一致的 Requirement。
   *
   * <p>设计意图：防止并发切换默认 spec 或并发 spec 变更导致写入使用了非预期的分区规则。
   */
  class AssertDefaultSpecID implements UpdateRequirement {
    private final int specId;

    /**
     * 构造默认 spec ID 断言。
     *
     * @param specId 期望的默认分区规格 ID
     */
    public AssertDefaultSpecID(int specId) {
      this.specId = specId;
    }

    /** 返回 期望的默认分区规格 ID。 */
    public int specId() {
      return specId;
    }

    /**
     * 校验基础元数据的默认分区规格 ID 是否符合期望。
     *
     * @param base 当前基础 {@link TableMetadata}，不能为 null
     */
    @Override
    public void validate(TableMetadata base) {
      if (specId != base.defaultSpecId()) {
        throw new CommitFailedException(
            "Requirement failed: default partition spec changed: expected id %s != %s",
            specId, base.defaultSpecId());
      }
    }
  }

  /**
   * 要求表默认排序规则（sort order）ID 与期望值一致的 Requirement。
   *
   * <p>设计意图：防止并发切换默认排序规则导致写入数据的物理顺序不符合预期。
   */
  class AssertDefaultSortOrderID implements UpdateRequirement {
    private final int sortOrderId;

    /**
     * 构造默认 sort order ID 断言。
     *
     * @param sortOrderId 期望的默认排序规则 ID
     */
    public AssertDefaultSortOrderID(int sortOrderId) {
      this.sortOrderId = sortOrderId;
    }

    /** 返回 期望的默认排序规则 ID。 */
    public int sortOrderId() {
      return sortOrderId;
    }

    /**
     * 校验基础元数据的默认排序规则 ID 是否符合期望。
     *
     * @param base 当前基础 {@link TableMetadata}，不能为 null
     */
    @Override
    public void validate(TableMetadata base) {
      if (sortOrderId != base.defaultSortOrderId()) {
        throw new CommitFailedException(
            "Requirement failed: default sort order changed: expected id %s != %s",
            sortOrderId, base.defaultSortOrderId());
      }
    }
  }
}
