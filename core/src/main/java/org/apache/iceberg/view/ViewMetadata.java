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
package org.apache.iceberg.view;

import java.io.Serializable;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.apache.iceberg.MetadataUpdate;
import org.apache.iceberg.Schema;
import org.apache.iceberg.exceptions.ValidationException;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.util.PropertyUtil;
import org.immutables.value.Value;
import org.immutables.value.Value.Style.ImplementationVisibility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 文件级说明：视图元数据的核心不可变模型（Immutables 生成）。
 *
 * <p>所属模块：iceberg-core（视图元数据实现模块，是视图 catalog 操作的数据中枢）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>承载视图的完整元数据：UUID、格式版本、存储位置、schema 列表、版本列表、 历史记录、属性及待提交变更。
 *   <li>提供 {@link Builder} 支持视图元数据的增量构建与变更收集（AddViewVersion、
 *       SetCurrentViewVersion、AddSchema、SetProperties 等）。
 *   <li>维护版本过期策略：按 {@link ViewProperties#VERSION_HISTORY_SIZE} 限制保留的版本数。
 * </ul>
 *
 * <p>设计意图：通过 Immutables {@code @Value.Immutable(builder = false)} + allParameters 生成包级可见的不可变实现
 * {@code ImmutableViewMetadata}；{@code @Value.Derived} 派生 versionsById/schemasById
 * 索引避免重复计算；{@code @Value.Check} 在构建时校验格式版本； Builder 内部收集 changes 列表，供 catalog 提交时生成 commit。
 *
 * <p>上下游关系：由 {@link ViewMetadataParser} 从 JSON 文件加载或由 catalog 操作通过 Builder 构建；被视图 catalog（如
 * BaseView 等）在 create/replace/update 时使用。
 */
@SuppressWarnings("ImmutablesStyle")
@Value.Immutable(builder = false)
@Value.Style(allParameters = true, visibility = ImplementationVisibility.PACKAGE)
public interface ViewMetadata extends Serializable {
  Logger LOG = LoggerFactory.getLogger(ViewMetadata.class);
  int SUPPORTED_VIEW_FORMAT_VERSION = 1;
  int DEFAULT_VIEW_FORMAT_VERSION = 1;

  String uuid();

  int formatVersion();

  String location();

  /**
   * 返回当前 schema ID（取自当前版本的 schemaId）。
   *
   * <p>逻辑：从 {@link #currentVersion()} 取 schemaId，并校验该 ID 存在于 schemas 列表中， 若不存在（如元数据文件引用了无效 schema
   * id）则抛出参数异常。
   *
   * @return 当前 schema ID
   */
  default Integer currentSchemaId() {
    // fail when accessing the current schema if ViewMetadata was created through the
    // ViewMetadataParser with an invalid schema id
    int currentSchemaId = currentVersion().schemaId();
    Preconditions.checkArgument(
        schemasById().containsKey(currentSchemaId),
        "Cannot find current schema with id %s in schemas: %s",
        currentSchemaId,
        schemasById().keySet());

    return currentSchemaId;
  }

  List<Schema> schemas();

  int currentVersionId();

  List<ViewVersion> versions();

  List<ViewHistoryEntry> history();

  Map<String, String> properties();

  List<MetadataUpdate> changes();

  @Nullable
  String metadataFileLocation();

  /** 按 versionId 查找并返回对应的 {@link ViewVersion}，不存在返回 null。 */
  default ViewVersion version(int versionId) {
    return versionsById().get(versionId);
  }

  /**
   * 返回当前视图版本。
   *
   * <p>逻辑：校验 currentVersionId 存在于 versions 列表中，若不存在则抛出参数异常， 否则从 versionsById 索引返回对应版本。
   *
   * @return 当前 {@link ViewVersion}
   */
  default ViewVersion currentVersion() {
    // fail when accessing the current version if ViewMetadata was created through the
    // ViewMetadataParser with an invalid view version id
    Preconditions.checkArgument(
        versionsById().containsKey(currentVersionId()),
        "Cannot find current version %s in view versions: %s",
        currentVersionId(),
        versionsById().keySet());

    return versionsById().get(currentVersionId());
  }

  /**
   * 派生 versions 的 ID 索引（{@code versionId -> ViewVersion}）。
   *
   * <p>设计要点：标记 {@code @Value.Derived}，由 Immutables 在构建时计算一次并缓存。
   *
   * @return 版本 ID 到版本的映射
   */
  @Value.Derived
  default Map<Integer, ViewVersion> versionsById() {
    ImmutableMap.Builder<Integer, ViewVersion> builder = ImmutableMap.builder();
    for (ViewVersion version : versions()) {
      builder.put(version.versionId(), version);
    }

    return builder.build();
  }

  /**
   * 派生 schemas 的 ID 索引（{@code schemaId -> Schema}）。
   *
   * <p>设计要点：标记 {@code @Value.Derived}，由 Immutables 在构建时计算一次并缓存。
   *
   * @return schema ID 到 schema 的映射
   */
  @Value.Derived
  default Map<Integer, Schema> schemasById() {
    ImmutableMap.Builder<Integer, Schema> builder = ImmutableMap.builder();
    for (Schema schema : schemas()) {
      builder.put(schema.schemaId(), schema);
    }

    return builder.build();
  }

  /** 返回当前 schema（按 currentSchemaId 从索引中查找）。 */
  default Schema schema() {
    return schemasById().get(currentSchemaId());
  }

  /**
   * 构建时校验：formatVersion 必须为正且不超过 {@link #SUPPORTED_VIEW_FORMAT_VERSION}。
   *
   * <p>设计要点：标记 {@code @Value.Check}，由 Immutables 在构建不可变对象时自动调用。
   */
  @Value.Check
  default void check() {
    Preconditions.checkArgument(
        formatVersion() > 0 && formatVersion() <= ViewMetadata.SUPPORTED_VIEW_FORMAT_VERSION,
        "Unsupported format version: %s",
        formatVersion());
  }

  /** 创建新的 {@link Builder}。 */
  static Builder builder() {
    return new Builder();
  }

  /** 基于已有 {@link ViewMetadata} 创建 {@link Builder}，用于增量修改。 */
  static Builder buildFrom(ViewMetadata base) {
    return new Builder(base);
  }

  /**
   * 视图元数据构建器，支持增量修改并收集待提交的 {@link MetadataUpdate} 变更列表。
   *
   * <p>设计意图：所有变更方法返回 this 以支持链式调用；内部维护 versionsById/schemasById 索引以支持 ID 查找与去重；{@code LAST_ADDED =
   * -1} 作为魔法值表示"使用最近添加的版本"； {@link #build()} 时按版本历史大小策略执行过期清理。
   */
  class Builder {
    private static final int LAST_ADDED = -1;
    private final List<ViewVersion> versions;
    private final List<Schema> schemas;
    private final List<ViewHistoryEntry> history;
    private final Map<String, String> properties;
    private final List<MetadataUpdate> changes;
    private int formatVersion = DEFAULT_VIEW_FORMAT_VERSION;
    private int currentVersionId;
    private String location;
    private String uuid;
    private String metadataLocation;

    // internal change tracking
    private Integer lastAddedVersionId = null;

    // indexes
    private final Map<Integer, ViewVersion> versionsById;
    private final Map<Integer, Schema> schemasById;

    private Builder() {
      this.versions = Lists.newArrayList();
      this.versionsById = Maps.newHashMap();
      this.schemas = Lists.newArrayList();
      this.schemasById = Maps.newHashMap();
      this.history = Lists.newArrayList();
      this.properties = Maps.newHashMap();
      this.changes = Lists.newArrayList();
      this.uuid = null;
    }

    private Builder(ViewMetadata base) {
      this.versions = Lists.newArrayList(base.versions());
      this.versionsById = Maps.newHashMap(base.versionsById());
      this.schemas = Lists.newArrayList(base.schemas());
      this.schemasById = Maps.newHashMap(base.schemasById());
      this.history = Lists.newArrayList(base.history());
      this.properties = Maps.newHashMap(base.properties());
      this.changes = Lists.newArrayList();
      this.formatVersion = base.formatVersion();
      this.currentVersionId = base.currentVersionId();
      this.location = base.location();
      this.uuid = base.uuid();
      this.metadataLocation = null;
    }

    /**
     * 升级格式版本（不允许降级）。
     *
     * <p>逻辑：校验新版本 >= 当前版本；相同则直接返回；否则更新 formatVersion 并记录 {@link
     * MetadataUpdate.UpgradeFormatVersion} 变更。
     *
     * @param newFormatVersion 新的格式版本
     * @return this
     */
    public Builder upgradeFormatVersion(int newFormatVersion) {
      Preconditions.checkArgument(
          newFormatVersion >= formatVersion,
          "Cannot downgrade v%s view to v%s",
          formatVersion,
          newFormatVersion);

      if (formatVersion == newFormatVersion) {
        return this;
      }

      this.formatVersion = newFormatVersion;
      changes.add(new MetadataUpdate.UpgradeFormatVersion(newFormatVersion));
      return this;
    }

    /**
     * 设置视图存储位置。
     *
     * <p>逻辑：校验非空；与当前位置相同则跳过；否则更新 location 并记录 {@link MetadataUpdate.SetLocation} 变更。
     *
     * @param newLocation 新的存储位置
     * @return this
     */
    public Builder setLocation(String newLocation) {
      Preconditions.checkArgument(null != newLocation, "Invalid location: null");
      if (null != location && location.equals(newLocation)) {
        return this;
      }

      this.location = newLocation;
      changes.add(new MetadataUpdate.SetLocation(newLocation));
      return this;
    }

    /**
     * 设置元数据文件位置（不产生变更，仅用于标记元数据来源）。
     *
     * @param newMetadataLocation 元数据文件路径
     * @return this
     */
    public Builder setMetadataLocation(String newMetadataLocation) {
      this.metadataLocation = newMetadataLocation;
      return this;
    }

    /**
     * 设置当前视图版本 ID。
     *
     * <p>逻辑：若 newVersionId 为 {@code LAST_ADDED}(-1) 则使用最近添加的版本 ID； 若与当前相同则跳过；否则校验该版本存在，更新
     * currentVersionId 并记录 {@link MetadataUpdate.SetCurrentViewVersion} 变更（若指向最近添加的版本则记为
     * LAST_ADDED）。
     *
     * @param newVersionId 新的版本 ID，或 {@code LAST_ADDED}
     * @return this
     */
    public Builder setCurrentVersionId(int newVersionId) {
      if (newVersionId == LAST_ADDED) {
        ValidationException.check(
            lastAddedVersionId != null,
            "Cannot set last version id: no current version id has been set");
        return setCurrentVersionId(lastAddedVersionId);
      }

      if (currentVersionId == newVersionId) {
        return this;
      }

      ViewVersion version = versionsById.get(newVersionId);
      Preconditions.checkArgument(
          version != null, "Cannot set current version to unknown version: %s", newVersionId);

      this.currentVersionId = newVersionId;

      if (lastAddedVersionId != null && lastAddedVersionId == newVersionId) {
        changes.add(new MetadataUpdate.SetCurrentViewVersion(LAST_ADDED));
      } else {
        changes.add(new MetadataUpdate.SetCurrentViewVersion(newVersionId));
      }

      return this;
    }

    /**
     * 设置当前视图版本，同时关联新的 schema。
     *
     * <p>逻辑：先添加 schema 获取 schemaId -> 用该 schemaId 重建 ViewVersion -> 调用 {@link
     * #setCurrentVersionId(int)} 设为当前版本。
     *
     * @param version 视图版本
     * @param schema 关联的 schema
     * @return this
     */
    public Builder setCurrentVersion(ViewVersion version, Schema schema) {
      int newSchemaId = addSchemaInternal(schema);
      ViewVersion newVersion =
          ImmutableViewVersion.builder().from(version).schemaId(newSchemaId).build();
      return setCurrentVersionId(addVersionInternal(newVersion));
    }

    /**
     * 添加一个视图版本（不影响当前版本设置）。
     *
     * @param version 待添加的视图版本
     * @return this
     */
    public Builder addVersion(ViewVersion version) {
      addVersionInternal(version);
      return this;
    }

    /**
     * 内部添加视图版本，返回分配的版本 ID。
     *
     * <p>逻辑：复用或创建新版本 ID -> 若已存在且为本 Builder 内添加则记 lastAddedVersionId 并返回 -> 校验 schema 存在 -> 必要时以新 ID
     * 重建版本 -> 加入 versions 列表与索引 -> 记录 {@link MetadataUpdate.AddViewVersion} 变更与历史条目 -> 更新
     * lastAddedVersionId。
     *
     * @param version 待添加的视图版本
     * @return 分配的版本 ID
     */
    private int addVersionInternal(ViewVersion version) {
      int newVersionId = reuseOrCreateNewViewVersionId(version);
      if (versionsById.containsKey(newVersionId)) {
        boolean addedInBuilder =
            changes(MetadataUpdate.AddViewVersion.class)
                .anyMatch(added -> added.viewVersion().versionId() == newVersionId);
        this.lastAddedVersionId = addedInBuilder ? newVersionId : null;
        return newVersionId;
      }

      Preconditions.checkArgument(
          schemasById.containsKey(version.schemaId()),
          "Cannot add version with unknown schema: %s",
          version.schemaId());

      ViewVersion newVersion;
      if (newVersionId != version.versionId()) {
        newVersion = ImmutableViewVersion.builder().from(version).versionId(newVersionId).build();
      } else {
        newVersion = version;
      }

      versions.add(newVersion);
      versionsById.put(newVersion.versionId(), newVersion);
      changes.add(new MetadataUpdate.AddViewVersion(newVersion));
      history.add(
          ImmutableViewHistoryEntry.builder()
              .timestampMillis(newVersion.timestampMillis())
              .versionId(newVersion.versionId())
              .build());

      this.lastAddedVersionId = newVersionId;

      return newVersionId;
    }

    /**
     * 复用已有等价版本的 ID，或分配新的版本 ID（最大 ID + 1）。
     *
     * <p>逻辑：遍历现有版本，若找到 equals 的版本则复用其 ID；否则取最大 ID + 1。
     *
     * @param viewVersion 待判定的视图版本
     * @return 复用或新建的版本 ID
     */
    private int reuseOrCreateNewViewVersionId(ViewVersion viewVersion) {
      // if the view version already exists, use its id; otherwise use the highest id + 1
      int newVersionId = viewVersion.versionId();
      for (ViewVersion version : versions) {
        if (version.equals(viewVersion)) {
          return version.versionId();
        } else if (version.versionId() >= newVersionId) {
          newVersionId = viewVersion.versionId() + 1;
        }
      }

      return newVersionId;
    }

    /**
     * 添加一个 schema。
     *
     * @param schema 待添加的 schema
     * @return this
     */
    public Builder addSchema(Schema schema) {
      addSchemaInternal(schema);
      return this;
    }

    /**
     * 内部添加 schema，返回分配的 schema ID。
     *
     * <p>逻辑：复用或创建新 schema ID -> 若已存在则直接返回 -> 必要时以新 ID 重建 schema -> 计算最高字段 ID -> 加入 schemas 列表与索引 ->
     * 记录 {@link MetadataUpdate.AddSchema} 变更。
     *
     * @param schema 待添加的 schema
     * @return 分配的 schema ID
     */
    private int addSchemaInternal(Schema schema) {
      int newSchemaId = reuseOrCreateNewSchemaId(schema);
      if (schemasById.containsKey(newSchemaId)) {
        // this schema existed or was already added in the builder
        return newSchemaId;
      }

      Schema newSchema;
      if (newSchemaId != schema.schemaId()) {
        newSchema = new Schema(newSchemaId, schema.columns(), schema.identifierFieldIds());
      } else {
        newSchema = schema;
      }

      int highestFieldId = Math.max(highestFieldId(), newSchema.highestFieldId());
      schemas.add(newSchema);
      schemasById.put(newSchema.schemaId(), newSchema);
      changes.add(new MetadataUpdate.AddSchema(newSchema, highestFieldId));

      return newSchemaId;
    }

    /** 返回当前所有 schema 中的最高字段 ID，用于新增 schema 时维护字段 ID 单调性。 */
    private int highestFieldId() {
      return schemas.stream().map(Schema::highestFieldId).max(Integer::compareTo).orElse(0);
    }

    /**
     * 复用已有等价 schema 的 ID，或分配新的 schema ID（最大 ID + 1）。
     *
     * <p>逻辑：遍历现有 schema，若 sameSchema 则复用其 ID；否则取最大 ID + 1。
     *
     * @param newSchema 待判定的 schema
     * @return 复用或新建的 schema ID
     */
    private int reuseOrCreateNewSchemaId(Schema newSchema) {
      // if the schema already exists, use its id; otherwise use the highest id + 1
      int newSchemaId = newSchema.schemaId();
      for (Schema schema : schemas) {
        if (schema.sameSchema(newSchema)) {
          return schema.schemaId();
        } else if (schema.schemaId() >= newSchemaId) {
          newSchemaId = schema.schemaId() + 1;
        }
      }

      return newSchemaId;
    }

    /**
     * 批量设置视图属性（合并到已有属性）。
     *
     * @param updated 待设置的属性键值对
     * @return this
     */
    public Builder setProperties(Map<String, String> updated) {
      if (updated.isEmpty()) {
        return this;
      }

      properties.putAll(updated);
      changes.add(new MetadataUpdate.SetProperties(updated));
      return this;
    }

    /**
     * 批量移除视图属性。
     *
     * @param propertiesToRemove 待移除的属性键集合
     * @return this
     */
    public Builder removeProperties(Set<String> propertiesToRemove) {
      if (propertiesToRemove.isEmpty()) {
        return this;
      }

      propertiesToRemove.forEach(properties::remove);
      changes.add(new MetadataUpdate.RemoveProperties(propertiesToRemove));
      return this;
    }

    /**
     * 分配视图 UUID（仅允许设置一次或赋相同值）。
     *
     * @param newUUID 新的 UUID
     * @return this
     */
    public ViewMetadata.Builder assignUUID(String newUUID) {
      Preconditions.checkArgument(newUUID != null, "Cannot set uuid to null");
      Preconditions.checkArgument(uuid == null || newUUID.equals(uuid), "Cannot reassign uuid");

      if (!newUUID.equals(uuid)) {
        this.uuid = newUUID;
        changes.add(new MetadataUpdate.AssignUUID(uuid));
      }

      return this;
    }

    /**
     * 构建不可变的 {@link ViewMetadata}。
     *
     * <p>逻辑：校验 location 非空且至少有一个版本 -> 校验 metadataLocation 与 changes 互斥 （从文件加载的元数据不应有待提交变更） ->
     * 读取版本历史大小属性 -> 按策略过期旧版本 （保留至少本次 Builder 新增的版本数） -> 调用 ImmutableViewMetadata.of 组装。
     *
     * @return 视图元数据
     */
    public ViewMetadata build() {
      Preconditions.checkArgument(null != location, "Invalid location: null");
      Preconditions.checkArgument(versions.size() > 0, "Invalid view: no versions were added");

      // when associated with a metadata file, metadata must have no changes so that the metadata
      // matches exactly what is in the metadata file, which does not store changes. metadata
      // location with changes is inconsistent.
      Preconditions.checkArgument(
          metadataLocation == null || changes.isEmpty(),
          "Cannot create view metadata with a metadata location and changes");

      int historySize =
          PropertyUtil.propertyAsInt(
              properties,
              ViewProperties.VERSION_HISTORY_SIZE,
              ViewProperties.VERSION_HISTORY_SIZE_DEFAULT);

      Preconditions.checkArgument(
          historySize > 0,
          "%s must be positive but was %s",
          ViewProperties.VERSION_HISTORY_SIZE,
          historySize);

      // expire old versions, but keep at least the versions added in this builder
      int numAddedVersions = (int) changes(MetadataUpdate.AddViewVersion.class).count();
      int numVersionsToKeep = Math.max(numAddedVersions, historySize);

      List<ViewVersion> retainedVersions;
      List<ViewHistoryEntry> retainedHistory;
      if (versions.size() > numVersionsToKeep) {
        retainedVersions = expireVersions(versionsById, numVersionsToKeep);
        Set<Integer> retainedVersionIds =
            retainedVersions.stream().map(ViewVersion::versionId).collect(Collectors.toSet());
        retainedHistory = updateHistory(history, retainedVersionIds);
      } else {
        retainedVersions = versions;
        retainedHistory = history;
      }

      return ImmutableViewMetadata.of(
          null == uuid ? UUID.randomUUID().toString() : uuid,
          formatVersion,
          location,
          schemas,
          currentVersionId,
          retainedVersions,
          retainedHistory,
          properties,
          changes,
          metadataLocation);
    }

    /**
     * 按版本 ID 降序保留指定数量的版本，其余过期。
     *
     * <p>逻辑：版本 ID 按降序排序，取前 numVersionsToKeep 个保留。
     *
     * @param versionsById 版本 ID 到版本的映射
     * @param numVersionsToKeep 保留的版本数
     * @return 保留的版本列表
     */
    static List<ViewVersion> expireVersions(
        Map<Integer, ViewVersion> versionsById, int numVersionsToKeep) {
      // version ids are assigned sequentially. keep the latest versions by ID.
      List<Integer> ids = Lists.newArrayList(versionsById.keySet());
      ids.sort(Comparator.reverseOrder());

      List<ViewVersion> retainedVersions = Lists.newArrayList();
      for (int idToKeep : ids.subList(0, numVersionsToKeep)) {
        retainedVersions.add(versionsById.get(idToKeep));
      }

      return retainedVersions;
    }

    /**
     * 根据保留的版本 ID 集合更新历史记录，清除指向已过期版本的历史条目。
     *
     * <p>逻辑：遍历历史，保留指向仍在用版本的条目；一旦遇到指向已过期版本的条目， 清空之前累积的历史（因历史应连续）。
     *
     * @param history 原始历史列表
     * @param ids 保留的版本 ID 集合
     * @return 更新后的历史列表
     */
    static List<ViewHistoryEntry> updateHistory(List<ViewHistoryEntry> history, Set<Integer> ids) {
      List<ViewHistoryEntry> retainedHistory = Lists.newArrayList();
      for (ViewHistoryEntry entry : history) {
        if (ids.contains(entry.versionId())) {
          retainedHistory.add(entry);
        } else {
          // clear history past any unknown version
          retainedHistory.clear();
        }
      }

      return retainedHistory;
    }

    /**
     * 按 MetadataUpdate 子类型过滤已收集的变更列表。
     *
     * @param updateClass 变更类型
     * @param <U> 变更泛型
     * @return 匹配类型的变更流
     */
    private <U extends MetadataUpdate> Stream<U> changes(Class<U> updateClass) {
      return changes.stream().filter(updateClass::isInstance).map(updateClass::cast);
    }
  }
}
