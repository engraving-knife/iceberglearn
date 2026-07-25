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

import java.util.List;
import java.util.Set;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;

/**
 * 更新前置要求的构造工具。
 *
 * <p>所属模块：iceberg-core。职责：根据提交场景（建表/替换表/更新表）与一组 {@link MetadataUpdate}， 推导出提交时需要校验的前置 {@link
 * UpdateRequirement} 列表。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>场景入口：{@code forCreateTable}/{@code forReplaceTable}/{@code forUpdateTable} 三个工厂方法。
 *   <li>Builder 累积：在 Builder 中按 update 类型动态追加断言（如 schema 变更则追加 schema id 断言）。
 *   <li>OCC 基线保护：确保提交基于调用方持有的基线，防止丢失更新。
 * </ul>
 *
 * <p>上下游关系：上游为各 {@link Update} 实现或 REST 提交端点；产物经 {@link UpdateRequirementParser} 序列化后随提交请求发出。
 */
public class UpdateRequirements {

  /** 私有构造：工具类禁止实例化。 */
  private UpdateRequirements() {}

  /**
   * 为建表场景构造更新前置要求列表。
   *
   * <p>步骤：创建无 base 元数据的 Builder，先断言表不存在，再逐条处理 metadataUpdates。
   *
   * @param metadataUpdates 建表时的元数据变更列表
   * @return 前置要求列表
   */
  public static List<UpdateRequirement> forCreateTable(List<MetadataUpdate> metadataUpdates) {
    Preconditions.checkArgument(null != metadataUpdates, "Invalid metadata updates: null");
    Builder builder = new Builder(null, false);
    builder.require(new UpdateRequirement.AssertTableDoesNotExist());
    metadataUpdates.forEach(builder::update);
    return builder.build();
  }

  /**
   * 为替换表场景构造更新前置要求列表。
   *
   * <p>步骤：创建以 base 为基准的 Builder（isReplace=true），先断言表 UUID 一致，再逐条处理 metadataUpdates。
   *
   * @param base 基线表元数据
   * @param metadataUpdates 替换表时的元数据变更列表
   * @return 前置要求列表
   */
  public static List<UpdateRequirement> forReplaceTable(
      TableMetadata base, List<MetadataUpdate> metadataUpdates) {
    Preconditions.checkArgument(null != base, "Invalid table metadata: null");
    Preconditions.checkArgument(null != metadataUpdates, "Invalid metadata updates: null");
    Builder builder = new Builder(base, true);
    builder.require(new UpdateRequirement.AssertTableUUID(base.uuid()));
    metadataUpdates.forEach(builder::update);
    return builder.build();
  }

  /**
   * 为更新表场景构造更新前置要求列表。
   *
   * <p>步骤：创建以 base 为基准的 Builder（isReplace=false），先断言表 UUID 一致，再逐条处理 metadataUpdates，
   * 确保基于调用方持有的基线提交，防止丢失更新。
   *
   * @param base 基线表元数据
   * @param metadataUpdates 更新表时的元数据变更列表
   * @return 前置要求列表
   */
  public static List<UpdateRequirement> forUpdateTable(
      TableMetadata base, List<MetadataUpdate> metadataUpdates) {
    Preconditions.checkArgument(null != base, "Invalid table metadata: null");
    Preconditions.checkArgument(null != metadataUpdates, "Invalid metadata updates: null");
    Builder builder = new Builder(base, false);
    builder.require(new UpdateRequirement.AssertTableUUID(base.uuid()));
    metadataUpdates.forEach(builder::update);
    return builder.build();
  }

  private static class Builder {
    private final TableMetadata base;
    private final ImmutableList.Builder<UpdateRequirement> requirements = ImmutableList.builder();
    private final Set<String> changedRefs = Sets.newHashSet();
    private final boolean isReplace;
    private boolean addedSchema = false;
    private boolean setSchemaId = false;
    private boolean addedSpec = false;
    private boolean setSpecId = false;
    private boolean setOrderId = false;

    private Builder(TableMetadata base, boolean isReplace) {
      this.base = base;
      this.isReplace = isReplace;
    }

    private Builder require(UpdateRequirement requirement) {
      Preconditions.checkArgument(requirement != null, "Invalid requirement: null");
      requirements.add(requirement);
      return this;
    }

    private Builder update(MetadataUpdate update) {
      Preconditions.checkArgument(update != null, "Invalid update: null");

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

    private void update(MetadataUpdate.SetSnapshotRef setRef) {
      // require that the ref is unchanged from the base
      String name = setRef.name();
      // add returns true the first time the ref name is added
      boolean added = changedRefs.add(name);
      if (added && base != null && !isReplace) {
        SnapshotRef baseRef = base.ref(name);
        // require that the ref does not exist (null) or is the same as the base snapshot
        require(
            new UpdateRequirement.AssertRefSnapshotID(
                name, baseRef != null ? baseRef.snapshotId() : null));
      }
    }

    private void update(MetadataUpdate.AddSchema update) {
      if (!addedSchema) {
        if (base != null) {
          require(new UpdateRequirement.AssertLastAssignedFieldId(base.lastColumnId()));
        }
        this.addedSchema = true;
      }
    }

    private void update(MetadataUpdate.SetCurrentSchema update) {
      if (!setSchemaId) {
        if (base != null && !isReplace) {
          // require that the current schema has not changed
          require(new UpdateRequirement.AssertCurrentSchemaID(base.currentSchemaId()));
        }
        this.setSchemaId = true;
      }
    }

    private void update(MetadataUpdate.AddPartitionSpec update) {
      if (!addedSpec) {
        if (base != null) {
          require(
              new UpdateRequirement.AssertLastAssignedPartitionId(base.lastAssignedPartitionId()));
        }
        this.addedSpec = true;
      }
    }

    private void update(MetadataUpdate.SetDefaultPartitionSpec update) {
      if (!setSpecId) {
        if (base != null && !isReplace) {
          // require that the default spec has not changed
          require(new UpdateRequirement.AssertDefaultSpecID(base.defaultSpecId()));
        }
        this.setSpecId = true;
      }
    }

    private void update(MetadataUpdate.SetDefaultSortOrder update) {
      if (!setOrderId) {
        if (base != null && !isReplace) {
          // require that the default write order has not changed
          require(new UpdateRequirement.AssertDefaultSortOrderID(base.defaultSortOrderId()));
        }
        this.setOrderId = true;
      }
    }

    private List<UpdateRequirement> build() {
      return requirements.build();
    }
  }
}
