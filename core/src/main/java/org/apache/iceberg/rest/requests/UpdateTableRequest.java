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
package org.apache.iceberg.rest.requests;

import java.util.List;
import java.util.Set;
import org.apache.iceberg.MetadataUpdate;
import org.apache.iceberg.SnapshotRef;
import org.apache.iceberg.TableMetadata;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.exceptions.CommitFailedException;
import org.apache.iceberg.relocated.com.google.common.base.MoreObjects;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.apache.iceberg.rest.RESTRequest;

/**
 * 更新表的 REST 请求。
 *
 * <p>所属模块：iceberg-core，REST 请求模型层（{@code rest.requests} 包）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>承载对表进行更新提交所需的全部信息：表标识、前置要求（requirements）与变更 列表（updates）；
 *   <li>支持在事务上下文中作为单个表变更参与多表事务提交；
 *   <li>提供用于构造请求的静态工厂方法 {@link #create} 与（已弃用的）Builder 体系；
 *   <li>承载一组已弃用的 {@link UpdateRequirement} 内部类实现，向后兼容旧 API。
 * </ul>
 *
 * <p>设计意图：作为不可变值对象，构造完成后字段只读；requirements 与 updates 内部 均以 List 形式保留变更顺序，保证提交语义可重现。{@code
 * validate()} 故意留空，因为 实际校验在服务端基于 {@link TableMetadata} 完成。无参构造仅为 Jackson 反序列化保留。
 *
 * <p>上下游关系：由 {@code RESTCatalog} 在 commit 阶段构造，经 {@link org.apache.iceberg.rest.RESTClient} 序列化后发送至
 * REST 服务端的 表更新接口或事务提交接口。
 */
public class UpdateTableRequest implements RESTRequest {

  private TableIdentifier identifier;
  private List<org.apache.iceberg.UpdateRequirement> requirements;
  private List<MetadataUpdate> updates;

  /** Jackson 反序列化所需的无参构造器，外部代码不应直接调用。 */
  public UpdateTableRequest() {
    // needed for Jackson deserialization
  }

  /**
   * 不带表标识的构造器，适用于请求路径中已隐含表标识的场景。
   *
   * @param requirements 前置要求列表
   * @param updates 元数据变更列表
   */
  public UpdateTableRequest(
      List<org.apache.iceberg.UpdateRequirement> requirements, List<MetadataUpdate> updates) {
    this.requirements = requirements;
    this.updates = updates;
  }

  /**
   * 包含表标识的包级私有构造器，适用于事务提交等需要显式标识的场景。
   *
   * @param identifier 表标识
   * @param requirements 前置要求列表
   * @param updates 元数据变更列表
   */
  UpdateTableRequest(
      TableIdentifier identifier,
      List<org.apache.iceberg.UpdateRequirement> requirements,
      List<MetadataUpdate> updates) {
    this(requirements, updates);
    this.identifier = identifier;
  }

  /**
   * 校验请求字段合法性。
   *
   * <p>逻辑：故意留空。实际的字段合法性校验在服务端基于 {@link TableMetadata} 完成， 客户端不在此处执行额外校验。
   */
  @Override
  public void validate() {}

  /**
   * 返回前置要求列表。
   *
   * @return 不可变列表；当未设置时返回空列表而非 null
   */
  public List<org.apache.iceberg.UpdateRequirement> requirements() {
    return requirements != null ? requirements : ImmutableList.of();
  }

  /**
   * 返回元数据变更列表。
   *
   * @return 不可变列表；当未设置时返回空列表而非 null
   */
  public List<MetadataUpdate> updates() {
    return updates != null ? updates : ImmutableList.of();
  }

  /** 返回表标识，可能为 null（当表标识由请求路径隐含时）。 */
  public TableIdentifier identifier() {
    return identifier;
  }

  /** 返回该请求的可读字符串表示，便于日志输出与调试。 */
  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("requirements", requirements)
        .add("updates", updates)
        .toString();
  }

  /**
   * 静态工厂方法，构造带表标识的 {@link UpdateTableRequest} 实例。
   *
   * @param identifier 表标识
   * @param requirements 前置要求列表
   * @param updates 元数据变更列表
   * @return 新的 {@link UpdateTableRequest} 实例
   */
  public static UpdateTableRequest create(
      TableIdentifier identifier,
      List<org.apache.iceberg.UpdateRequirement> requirements,
      List<MetadataUpdate> updates) {
    return new UpdateTableRequest(identifier, requirements, updates);
  }

  /**
   * 创建用于"新建表"场景的 Builder。
   *
   * <p>逻辑：构造一个不依赖已有元数据（base 为 null）且非替换模式的 Builder， 并添加"表不存在"的前置要求。
   *
   * @deprecated will be removed in 1.5.0, use {@link
   *     org.apache.iceberg.UpdateRequirements#forCreateTable(List)} instead.
   */
  @Deprecated
  public static Builder builderForCreate() {
    return new Builder(null, false).requireCreate();
  }

  /**
   * 创建用于"替换表"场景的 Builder。
   *
   * <p>逻辑：构造一个依赖已有元数据且为替换模式的 Builder，并添加 UUID 一致性要求。
   *
   * @param base 已存在的表元数据，不可为 null
   * @return 已添加 UUID 要求的 Builder
   * @deprecated will be removed in 1.5.0, use {@link
   *     org.apache.iceberg.UpdateRequirements#forReplaceTable(TableMetadata, List)} instead.
   */
  @Deprecated
  public static Builder builderForReplace(TableMetadata base) {
    Preconditions.checkNotNull(base, "Cannot create a builder from table metadata: null");
    return new Builder(base, true).requireTableUUID(base.uuid());
  }

  /**
   * 创建用于"更新表"场景的 Builder。
   *
   * <p>逻辑：构造一个依赖已有元数据且非替换模式的 Builder，并添加 UUID 一致性要求。
   *
   * @param base 已存在的表元数据，不可为 null
   * @return 已添加 UUID 要求的 Builder
   * @deprecated will be removed in 1.5.0, use {@link
   *     org.apache.iceberg.UpdateRequirements#forUpdateTable(TableMetadata, List)} instead.
   */
  @Deprecated
  public static Builder builderFor(TableMetadata base) {
    Preconditions.checkNotNull(base, "Cannot create a builder from table metadata: null");
    return new Builder(base, false).requireTableUUID(base.uuid());
  }

  /**
   * {@link UpdateTableRequest} 的构造器，已弃用。
   *
   * <p>设计意图：在收集变更的同时，根据变更类型自动推导并追加相应的前置要求 （requirements），保证提交时基础状态未被并发修改。内部维护多个布尔标志位
   * （addedSchema、setSchemaId 等）用于避免同一类要求被重复添加。
   *
   * @deprecated will be removed in 1.5.0, use {@link org.apache.iceberg.UpdateRequirements}
   *     instead.
   */
  @Deprecated
  public static class Builder {
    private final TableMetadata base;
    private final ImmutableList.Builder<org.apache.iceberg.UpdateRequirement> requirements =
        ImmutableList.builder();
    private final List<MetadataUpdate> updates = Lists.newArrayList();
    private final Set<String> changedRefs = Sets.newHashSet();
    private final boolean isReplace;
    private boolean addedSchema = false;
    private boolean setSchemaId = false;
    private boolean addedSpec = false;
    private boolean setSpecId = false;
    private boolean setOrderId = false;

    /**
     * 构造 Builder。
     *
     * @param base 已有的表元数据，新建场景可为 null
     * @param isReplace 是否为替换表场景（替换场景下部分前置要求会被跳过）
     */
    public Builder(TableMetadata base, boolean isReplace) {
      this.base = base;
      this.isReplace = isReplace;
    }

    /**
     * 追加一个前置要求。
     *
     * @param requirement 前置要求，不可为 null
     * @return 当前 Builder
     */
    private Builder require(UpdateRequirement requirement) {
      Preconditions.checkArgument(requirement != null, "Invalid requirement: null");
      requirements.add(requirement);
      return this;
    }

    /** 追加"表必须不存在"的前置要求。 */
    private Builder requireCreate() {
      return require(new UpdateRequirement.AssertTableDoesNotExist());
    }

    /**
     * 追加"表 UUID 必须一致"的前置要求。
     *
     * @param uuid 期望的表 UUID，不可为 null
     * @return 当前 Builder
     */
    private Builder requireTableUUID(String uuid) {
      Preconditions.checkArgument(uuid != null, "Invalid required UUID: null");
      return require(new UpdateRequirement.AssertTableUUID(uuid));
    }

    /**
     * 追加"指定 ref 的快照 ID 必须一致"的前置要求。
     *
     * @param ref 快照引用名称
     * @param snapshotId 期望的快照 ID，null 表示 ref 应不存在
     * @return 当前 Builder
     */
    private Builder requireRefSnapshotId(String ref, Long snapshotId) {
      return require(new UpdateRequirement.AssertRefSnapshotID(ref, snapshotId));
    }

    /**
     * 追加"最后分配的字段 ID 必须一致"的前置要求。
     *
     * @param fieldId 期望的最后分配字段 ID
     * @return 当前 Builder
     */
    private Builder requireLastAssignedFieldId(int fieldId) {
      return require(new UpdateRequirement.AssertLastAssignedFieldId(fieldId));
    }

    /**
     * 追加"当前 Schema ID 必须一致"的前置要求。
     *
     * @param schemaId 期望的当前 Schema ID
     * @return 当前 Builder
     */
    private Builder requireCurrentSchemaId(int schemaId) {
      return require(new UpdateRequirement.AssertCurrentSchemaID(schemaId));
    }

    /**
     * 追加"最后分配的分区 ID 必须一致"的前置要求。
     *
     * @param partitionId 期望的最后分配分区 ID
     * @return 当前 Builder
     */
    private Builder requireLastAssignedPartitionId(int partitionId) {
      return require(new UpdateRequirement.AssertLastAssignedPartitionId(partitionId));
    }

    /**
     * 追加"默认分区规则 ID 必须一致"的前置要求。
     *
     * @param specId 期望的默认分区规则 ID
     * @return 当前 Builder
     */
    private Builder requireDefaultSpecId(int specId) {
      return require(new UpdateRequirement.AssertDefaultSpecID(specId));
    }

    /**
     * 追加"默认排序规则 ID 必须一致"的前置要求。
     *
     * @param orderId 期望的默认排序规则 ID
     * @return 当前 Builder
     */
    private Builder requireDefaultSortOrderId(int orderId) {
      return require(new UpdateRequirement.AssertDefaultSortOrderID(orderId));
    }

    /**
     * 追加一个元数据变更，并根据变更类型自动追加对应的前置要求。
     *
     * <p>逻辑：先将变更加入 updates 列表；随后根据变更的具体类型分派到对应的 重载方法，由其负责补充前置要求。变更类型包括设置快照引用、新增 Schema、 切换当前
     * Schema、新增分区规则、切换默认分区规则、切换默认排序规则等。
     *
     * @param update 元数据变更，不可为 null
     * @return 当前 Builder
     */
    public Builder update(MetadataUpdate update) {
      Preconditions.checkArgument(update != null, "Invalid update: null");
      updates.add(update);

      // add requirements based on the change
      if (update instanceof MetadataUpdate.SetSnapshotRef) {
        update((MetadataUpdate.SetSnapshotRef) update);
      } else if (update instanceof MetadataUpdate.AddSchema) {
        update((MetadataUpdate.AddSchema) update);
      } else if (update instanceof MetadataUpdate.SetCurrentSchema) {
        update((MetadataUpdate.SetCurrentSchema) update);
      } else if (update instanceof MetadataUpdate.AddPartitionSpec) {
        update((MetadataUpdate.AddPartitionSpec) update);
      } else if (update instanceof MetadataUpdate.SetDefaultPartitionSpec) {
        update((MetadataUpdate.SetDefaultPartitionSpec) update);
      } else if (update instanceof MetadataUpdate.SetDefaultSortOrder) {
        update((MetadataUpdate.SetDefaultSortOrder) update);
      }

      return this;
    }

    /**
     * 处理设置快照引用的变更，并追加对应前置要求。
     *
     * <p>逻辑：通过 changedRefs 集合判断该 ref 是否首次被修改；若是首次、且非新建、 非替换场景，则基于 base 中该 ref 的当前快照 ID 追加一致性要求（ref
     * 不存在时 期望快照 ID 为 null）。
     *
     * @param setRef 设置快照引用的变更
     */
    private void update(MetadataUpdate.SetSnapshotRef setRef) {
      // require that the ref is unchanged from the base
      String name = setRef.name();
      // add returns true the first time the ref name is added
      boolean added = changedRefs.add(name);
      if (added && base != null && !isReplace) {
        SnapshotRef baseRef = base.ref(name);
        // require that the ref does not exist (null) or is the same as the base snapshot
        requireRefSnapshotId(name, baseRef != null ? baseRef.snapshotId() : null);
      }
    }

    /**
     * 处理新增 Schema 的变更，并追加对应前置要求。
     *
     * <p>逻辑：仅在该 Builder 首次新增 Schema 时，基于 base 追加"最后分配字段 ID 一致" 的前置要求；通过 addedSchema 标志避免重复添加。
     *
     * @param update 新增 Schema 的变更
     */
    private void update(MetadataUpdate.AddSchema update) {
      if (!addedSchema) {
        if (base != null) {
          requireLastAssignedFieldId(base.lastColumnId());
        }
        this.addedSchema = true;
      }
    }

    /**
     * 处理切换当前 Schema 的变更，并追加对应前置要求。
     *
     * <p>逻辑：仅在该 Builder 首次切换当前 Schema 时，若非新建、非替换场景，基于 base 追加"当前 Schema ID 一致"的前置要求；通过 setSchemaId
     * 标志避免重复添加。
     *
     * @param update 切换当前 Schema 的变更
     */
    private void update(MetadataUpdate.SetCurrentSchema update) {
      if (!setSchemaId) {
        if (base != null && !isReplace) {
          // require that the current schema has not changed
          requireCurrentSchemaId(base.currentSchemaId());
        }
        this.setSchemaId = true;
      }
    }

    /**
     * 处理新增分区规则的变更，并追加对应前置要求。
     *
     * <p>逻辑：仅在该 Builder 首次新增分区规则时，基于 base 追加"最后分配分区 ID 一致" 的前置要求；通过 addedSpec 标志避免重复添加。
     *
     * @param update 新增分区规则的变更
     */
    private void update(MetadataUpdate.AddPartitionSpec update) {
      if (!addedSpec) {
        if (base != null) {
          requireLastAssignedPartitionId(base.lastAssignedPartitionId());
        }
        this.addedSpec = true;
      }
    }

    /**
     * 处理切换默认分区规则的变更，并追加对应前置要求。
     *
     * <p>逻辑：仅在该 Builder 首次切换默认分区规则时，若非新建、非替换场景，基于 base 追加"默认分区规则 ID 一致"的前置要求；通过 setSpecId 标志避免重复添加。
     *
     * @param update 切换默认分区规则的变更
     */
    private void update(MetadataUpdate.SetDefaultPartitionSpec update) {
      if (!setSpecId) {
        if (base != null && !isReplace) {
          // require that the default spec has not changed
          requireDefaultSpecId(base.defaultSpecId());
        }
        this.setSpecId = true;
      }
    }

    /**
     * 处理切换默认排序规则的变更，并追加对应前置要求。
     *
     * <p>逻辑：仅在该 Builder 首次切换默认排序规则时，若非新建、非替换场景，基于 base 追加"默认排序规则 ID 一致"的前置要求；通过 setOrderId
     * 标志避免重复添加。
     *
     * @param update 切换默认排序规则的变更
     */
    private void update(MetadataUpdate.SetDefaultSortOrder update) {
      if (!setOrderId) {
        if (base != null && !isReplace) {
          // require that the default write order has not changed
          requireDefaultSortOrderId(base.defaultSortOrderId());
        }
        this.setOrderId = true;
      }
    }

    /**
     * 构建并返回 {@link UpdateTableRequest} 实例。
     *
     * <p>逻辑：将收集到的前置要求构建为不可变列表，将变更列表拷贝为不可变列表， 传入构造器创建实例。
     *
     * @return 新的 {@link UpdateTableRequest} 实例
     */
    public UpdateTableRequest build() {
      return new UpdateTableRequest(requirements.build(), ImmutableList.copyOf(updates));
    }
  }

  /**
   * 已弃用的前置要求接口，扩展自 {@link org.apache.iceberg.UpdateRequirement}。
   *
   * <p>设计意图：将各种"断言式"前置要求以内部类形式聚合在该接口下，每个实现类封装 一种特定的状态一致性检查；提交时通过 {@link #validate(TableMetadata)}
   * 校验， 失败则抛出 {@link CommitFailedException}，触发提交重试。
   *
   * @deprecated will be removed in 1.5.0, use {@link org.apache.iceberg.UpdateRequirement} instead.
   */
  @Deprecated
  public interface UpdateRequirement extends org.apache.iceberg.UpdateRequirement {

    /**
     * 断言表当前不存在。
     *
     * <p>逻辑：若 base 非 null（即表已存在），则提交失败。
     */
    class AssertTableDoesNotExist implements UpdateRequirement {
      AssertTableDoesNotExist() {}

      /**
       * 校验表不存在。
       *
       * @param base 当前表元数据，null 表示表不存在
       */
      @Override
      public void validate(TableMetadata base) {
        if (base != null) {
          throw new CommitFailedException("Requirement failed: table already exists");
        }
      }
    }

    /** 断言表的 UUID 与期望值一致。 */
    class AssertTableUUID implements UpdateRequirement {
      private final String uuid;

      /**
       * 构造 UUID 一致性断言。
       *
       * @param uuid 期望的表 UUID
       */
      AssertTableUUID(String uuid) {
        this.uuid = uuid;
      }

      /** 返回期望的表 UUID。 */
      public String uuid() {
        return uuid;
      }

      /**
       * 校验表 UUID 一致。
       *
       * <p>逻辑：忽略大小写比较期望 UUID 与 base 的 UUID，不一致则提交失败。
       *
       * @param base 当前表元数据
       */
      @Override
      public void validate(TableMetadata base) {
        if (!uuid.equalsIgnoreCase(base.uuid())) {
          throw new CommitFailedException(
              "Requirement failed: UUID does not match: expected %s != %s", base.uuid(), uuid);
        }
      }
    }

    /** 断言指定 ref（分支或标签）的快照 ID 与期望值一致。 */
    class AssertRefSnapshotID implements UpdateRequirement {
      private final String name;
      private final Long snapshotId;

      /**
       * 构造 ref 快照 ID 一致性断言。
       *
       * @param name ref 名称
       * @param snapshotId 期望的快照 ID，null 表示 ref 应不存在
       */
      AssertRefSnapshotID(String name, Long snapshotId) {
        this.name = name;
        this.snapshotId = snapshotId;
      }

      /** 返回 ref 名称。 */
      public String refName() {
        return name;
      }

      /** 返回期望的快照 ID，null 表示 ref 应不存在。 */
      public Long snapshotId() {
        return snapshotId;
      }

      /**
       * 校验 ref 快照 ID 一致。
       *
       * <p>逻辑：
       *
       * <ul>
       *   <li>ref 存在但期望 snapshotId 为 null：说明 ref 被并发创建，提交失败；
       *   <li>ref 存在且快照 ID 不一致：说明 ref 被并发修改，提交失败；
       *   <li>ref 不存在但期望 snapshotId 非 null：说明 ref 被并发删除，提交失败。
       * </ul>
       *
       * @param base 当前表元数据
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

    /** 断言最后分配的字段 ID 与期望值一致。 */
    class AssertLastAssignedFieldId implements UpdateRequirement {
      private final int lastAssignedFieldId;

      /**
       * 构造字段 ID 一致性断言。
       *
       * @param lastAssignedFieldId 期望的最后分配字段 ID
       */
      public AssertLastAssignedFieldId(int lastAssignedFieldId) {
        this.lastAssignedFieldId = lastAssignedFieldId;
      }

      /** 返回期望的最后分配字段 ID。 */
      public int lastAssignedFieldId() {
        return lastAssignedFieldId;
      }

      /**
       * 校验最后分配字段 ID 一致。
       *
       * <p>逻辑：base 非 null 且其 lastColumnId 与期望值不一致时，提交失败。
       *
       * @param base 当前表元数据
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

    /** 断言当前 Schema ID 与期望值一致。 */
    class AssertCurrentSchemaID implements UpdateRequirement {
      private final int schemaId;

      /**
       * 构造当前 Schema ID 一致性断言。
       *
       * @param schemaId 期望的当前 Schema ID
       */
      AssertCurrentSchemaID(int schemaId) {
        this.schemaId = schemaId;
      }

      /** 返回期望的当前 Schema ID。 */
      public int schemaId() {
        return schemaId;
      }

      /**
       * 校验当前 Schema ID 一致。
       *
       * <p>逻辑：base 的 currentSchemaId 与期望值不一致时，提交失败。
       *
       * @param base 当前表元数据
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

    /** 断言最后分配的分区 ID 与期望值一致。 */
    class AssertLastAssignedPartitionId implements UpdateRequirement {
      private final int lastAssignedPartitionId;

      /**
       * 构造分区 ID 一致性断言。
       *
       * @param lastAssignedPartitionId 期望的最后分配分区 ID
       */
      public AssertLastAssignedPartitionId(int lastAssignedPartitionId) {
        this.lastAssignedPartitionId = lastAssignedPartitionId;
      }

      /** 返回期望的最后分配分区 ID。 */
      public int lastAssignedPartitionId() {
        return lastAssignedPartitionId;
      }

      /**
       * 校验最后分配分区 ID 一致。
       *
       * <p>逻辑：base 非 null 且其 lastAssignedPartitionId 与期望值不一致时，提交失败。
       *
       * @param base 当前表元数据
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

    /** 断言默认分区规则 ID 与期望值一致。 */
    class AssertDefaultSpecID implements UpdateRequirement {
      private final int specId;

      /**
       * 构造默认分区规则 ID 一致性断言。
       *
       * @param specId 期望的默认分区规则 ID
       */
      AssertDefaultSpecID(int specId) {
        this.specId = specId;
      }

      /** 返回期望的默认分区规则 ID。 */
      public int specId() {
        return specId;
      }

      /**
       * 校验默认分区规则 ID 一致。
       *
       * <p>逻辑：base 的 defaultSpecId 与期望值不一致时，提交失败。
       *
       * @param base 当前表元数据
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

    /** 断言默认排序规则 ID 与期望值一致。 */
    class AssertDefaultSortOrderID implements UpdateRequirement {
      private final int sortOrderId;

      /**
       * 构造默认排序规则 ID 一致性断言。
       *
       * @param sortOrderId 期望的默认排序规则 ID
       */
      AssertDefaultSortOrderID(int sortOrderId) {
        this.sortOrderId = sortOrderId;
      }

      /** 返回期望的默认排序规则 ID。 */
      public int sortOrderId() {
        return sortOrderId;
      }

      /**
       * 校验默认排序规则 ID 一致。
       *
       * <p>逻辑：base 的 defaultSortOrderId 与期望值不一致时，提交失败。
       *
       * @param base 当前表元数据
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
}
