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
package org.apache.iceberg.nessie;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import org.apache.iceberg.CatalogProperties;
import org.apache.iceberg.SnapshotRef;
import org.apache.iceberg.TableMetadata;
import org.apache.iceberg.TableProperties;
import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.projectnessie.model.CommitMeta;
import org.projectnessie.model.ContentKey;
import org.projectnessie.model.IcebergTable;
import org.projectnessie.model.ImmutableCommitMeta;
import org.projectnessie.model.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 文件级说明：Nessie 集成的工具类（纯静态方法集合）。
 *
 * <p>所属模块：iceberg-nessie。职责：为 {@link NessieCatalog}/{@link NessieIcebergClient} 提供 一组与 Nessie
 * 交互的辅助工具，包括：标识符转换（Iceberg TableIdentifier ↔ Nessie ContentKey）、 commit 元数据构造（作者、应用类型）、以及加载表时把
 * Nessie 特有信息（commit id、GC 警告） 注入到 {@link TableMetadata}。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>把无状态的工具方法从 Catalog 主体剥离，降低主体类复杂度。
 *   <li>GC 安全检查：Nessie 多分支场景下，Iceberg 原生 GC（expire_snapshots 等）可能误删 被其他分支引用的文件，故在加载表时检测 GC_ENABLED
 *       并发出警告，引导用户使用 nessie-gc 工具做引用感知的回收。
 * </ul>
 *
 * <p>上下游：被 {@link NessieCatalog}、{@link NessieIcebergClient}、{@link NessieTableOperations} 调用；依赖
 * Nessie 客户端 SDK 与 Iceberg core 的 TableMetadata。
 */
public final class NessieUtil {

  private static final Logger LOG = LoggerFactory.getLogger(NessieUtil.class);

  public static final String NESSIE_CONFIG_PREFIX = "nessie.";
  static final String APPLICATION_TYPE = "application-type";

  public static final String CLIENT_API_VERSION = "nessie.client-api-version";

  private NessieUtil() {}

  /**
   * 从 TableIdentifier 的 namespace 中移除 catalog 名前缀（如果首层等于 catalog 名）。
   *
   * <p>逻辑：若 namespace 层级 >= 2 且首层等于 catalog 名，则截掉首层返回新标识符； 否则原样返回。
   *
   * @param to 原始标识符
   * @param name catalog 名
   * @return 去除 catalog 名前缀后的标识符
   */
  static TableIdentifier removeCatalogName(TableIdentifier to, String name) {

    String[] levels = to.namespace().levels();
    // check if the identifier includes the catalog name and remove it
    if (levels.length >= 2 && name.equalsIgnoreCase(to.namespace().level(0))) {
      Namespace trimmedNamespace = Namespace.of(Arrays.copyOfRange(levels, 1, levels.length));
      return TableIdentifier.of(trimmedNamespace, to.name());
    }

    // return the original unmodified
    return to;
  }

  /**
   * 把 Iceberg {@link TableIdentifier} 转换为 Nessie {@link ContentKey}。
   *
   * <p>逻辑：把 namespace 各层与表名按顺序拼接为字符串列表，构造 ContentKey。
   *
   * @param tableIdentifier Iceberg 表标识符
   * @return Nessie ContentKey
   */
  static ContentKey toKey(TableIdentifier tableIdentifier) {
    List<String> identifiers = Lists.newArrayList();
    if (tableIdentifier.hasNamespace()) {
      identifiers.addAll(Arrays.asList(tableIdentifier.namespace().levels()));
    }
    identifiers.add(tableIdentifier.name());

    return ContentKey.of(identifiers);
  }

  /**
   * 构造 Nessie commit 元数据（CommitMeta），包含 commit message、作者与应用类型。
   *
   * @param commitMsg commit 信息
   * @param catalogOptions catalog 配置（用于提取作者、app-id）
   * @return 构造好的 {@link CommitMeta}
   */
  static CommitMeta buildCommitMetadata(String commitMsg, Map<String, String> catalogOptions) {
    return catalogOptions(CommitMeta.builder().message(commitMsg), catalogOptions).build();
  }

  /**
   * 把 catalog 配置中的作者、应用类型、app-id 写入 commit meta builder。
   *
   * <p>逻辑：作者取 catalogOptions 的 user，缺失则取 JVM user.name； 应用类型固定为 "iceberg"；若配置了 app-id 则一并写入。
   *
   * @param commitMetaBuilder Nessie commit meta 构建器
   * @param catalogOptions catalog 配置
   * @return 传入的 builder（便于链式调用）
   */
  static ImmutableCommitMeta.Builder catalogOptions(
      ImmutableCommitMeta.Builder commitMetaBuilder, Map<String, String> catalogOptions) {
    Preconditions.checkArgument(null != catalogOptions, "catalogOptions must not be null");
    commitMetaBuilder.author(NessieUtil.commitAuthor(catalogOptions));
    commitMetaBuilder.putProperties(APPLICATION_TYPE, "iceberg");
    if (catalogOptions.containsKey(CatalogProperties.APP_ID)) {
      commitMetaBuilder.putProperties(
          CatalogProperties.APP_ID, catalogOptions.get(CatalogProperties.APP_ID));
    }
    return commitMetaBuilder;
  }

  /**
   * 解析 commit 作者。
   *
   * @param catalogOptions catalog 配置，从中查找 user
   * @return 作者名；优先取 catalogOptions 的 user，否则取 JVM 的 user.name
   */
  @Nullable
  private static String commitAuthor(Map<String, String> catalogOptions) {
    return Optional.ofNullable(catalogOptions.get(CatalogProperties.USER))
        .orElseGet(() -> System.getProperty("user.name"));
  }

  /**
   * 检查并警告 Nessie 场景下的 Iceberg GC 配置。
   *
   * <p>逻辑：
   *
   * <ul>
   *   <li>若表已标记 NESSIE_GC_NO_WARNING_PROPERTY=true 则直接返回（不再重复告警）。
   *   <li>若启用了 GC_ENABLED 或 METADATA_DELETE_AFTER_COMMIT_ENABLED，则记录警告日志： 这些操作可能误删被其他分支/tag
   *       引用的文件，建议设为 false 并使用 nessie-gc 工具。 同时把 NESSIE_GC_NO_WARNING_PROPERTY 置 true，避免后续重复告警。
   * </ul>
   *
   * @param tableMetadata 表元数据
   * @param updatedProperties 待更新的属性 map（会把警告标记写入）
   * @param identifier 表标识（用于日志）
   */
  private static void checkAndUpdateGCProperties(
      TableMetadata tableMetadata, Map<String, String> updatedProperties, String identifier) {
    if (tableMetadata.propertyAsBoolean(
        NessieTableOperations.NESSIE_GC_NO_WARNING_PROPERTY, false)) {
      return;
    }

    // To prevent accidental deletion of files that are still referenced by other branches/tags,
    // setting GC_ENABLED to 'false' is recommended, so that all Iceberg's gc operations like
    // expire_snapshots, remove_orphan_files, drop_table with purge will fail with an error.
    // `nessie-gc` CLI provides a reference-aware GC functionality for the expired/unreferenced
    // files.
    // Advanced users may still want to use the simpler Iceberg GC tools iff their Nessie Server
    // contains only one branch (in which case the full Nessie history will be reflected in the
    // Iceberg sequence of snapshots).
    if (tableMetadata.propertyAsBoolean(
            TableProperties.GC_ENABLED, TableProperties.GC_ENABLED_DEFAULT)
        || tableMetadata.propertyAsBoolean(
            TableProperties.METADATA_DELETE_AFTER_COMMIT_ENABLED,
            TableProperties.METADATA_DELETE_AFTER_COMMIT_ENABLED_DEFAULT)) {
      updatedProperties.put(NessieTableOperations.NESSIE_GC_NO_WARNING_PROPERTY, "true");
      LOG.warn(
          "The Iceberg property '{}' and/or '{}' is enabled on table '{}' in NessieCatalog."
              + " This will likely make data in other Nessie branches and tags and in earlier, historical Nessie"
              + " commits inaccessible. The recommended setting for those properties is 'false'. Use the 'nessie-gc'"
              + " tool for Nessie reference-aware garbage collection.",
          TableProperties.GC_ENABLED,
          TableProperties.METADATA_DELETE_AFTER_COMMIT_ENABLED,
          identifier);
    }
  }

  /**
   * 加载表元数据时，把 Nessie 特有信息注入到 {@link TableMetadata}。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>拷贝原表属性，写入 Nessie commit id（NESSIE_COMMIT_ID_PROPERTY）。
   *   <li>调用 {@link #checkAndUpdateGCProperties} 做 GC 配置检查与告警。
   *   <li>基于原 metadata 构建新的 TableMetadata：清除 previousFileLocation、 设置当前
   *       schema/sortOrder/partitionSpec（来自 Nessie IcebergTable 内容）、 设置 metadataLocation 与新属性。
   *   <li>若 snapshotId != -1，设置 main 分支的 snapshot。
   *   <li>discardChanges 后 build 返回。
   * </ol>
   *
   * @param tableMetadata 原始表元数据
   * @param metadataLocation 元数据文件位置
   * @param table Nessie 的 IcebergTable 内容对象
   * @param identifier 表标识（用于日志）
   * @param reference Nessie 引用（提供 commit hash）
   * @return 注入 Nessie 信息后的新 {@link TableMetadata}
   */
  public static TableMetadata updateTableMetadataWithNessieSpecificProperties(
      TableMetadata tableMetadata,
      String metadataLocation,
      IcebergTable table,
      String identifier,
      Reference reference) {
    // Update the TableMetadata with the Content of NessieTableState.
    Map<String, String> newProperties = Maps.newHashMap(tableMetadata.properties());
    newProperties.put(NessieTableOperations.NESSIE_COMMIT_ID_PROPERTY, reference.getHash());

    checkAndUpdateGCProperties(tableMetadata, newProperties, identifier);

    TableMetadata.Builder builder =
        TableMetadata.buildFrom(tableMetadata)
            .setPreviousFileLocation(null)
            .setCurrentSchema(table.getSchemaId())
            .setDefaultSortOrder(table.getSortOrderId())
            .setDefaultPartitionSpec(table.getSpecId())
            .withMetadataLocation(metadataLocation)
            .setProperties(newProperties);
    if (table.getSnapshotId() != -1) {
      builder.setBranchSnapshot(table.getSnapshotId(), SnapshotRef.MAIN_BRANCH);
    }
    LOG.info(
        "loadTableMetadata for '{}' from location '{}' at '{}'",
        identifier,
        metadataLocation,
        reference);

    return builder.discardChanges().build();
  }
}
