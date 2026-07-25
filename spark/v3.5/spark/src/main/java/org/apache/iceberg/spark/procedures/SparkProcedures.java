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
package org.apache.iceberg.spark.procedures;

import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.spark.sql.connector.catalog.TableCatalog;
import org.apache.spark.sql.connector.iceberg.catalog.Procedure;

/**
 * Iceberg Spark 存储过程注册表。
 *
 * <p>所属模块：iceberg-spark（Spark v3.5 集成模块），procedures 子包。
 *
 * <p>职责：维护 Iceberg 暴露给 Spark CALL 语句的存储过程名 -> builder 供应者映射， 提供 {@link #newBuilder(String)}
 * 按名称（大小写不敏感）创建过程 builder。
 *
 * <p>设计意图：用不可变 Map 静态注册所有过程，过程名小写化以匹配 Spark 大小写不敏感行为； 每个 builder supplier 每次返回新实例，保证过程无状态可重入。
 *
 * <p>上下游关系：被 Iceberg Spark catalog 调用以加载过程；上游是各 *Procedure 实现的 builder， 下游是 Spark CALL 语句执行。
 */
public class SparkProcedures {

  private static final Map<String, Supplier<ProcedureBuilder>> BUILDERS = initProcedureBuilders();

  private SparkProcedures() {}

  /**
   * 按名称创建过程 builder。
   *
   * <p>逻辑：name 转小写后查表，命中返回新 builder 实例，未命中返回 null。
   *
   * @param name 过程名（大小写不敏感）
   * @return ProcedureBuilder 或 null
   */
  public static ProcedureBuilder newBuilder(String name) {
    // procedure resolution is case insensitive to match the existing Spark behavior for functions
    Supplier<ProcedureBuilder> builderSupplier = BUILDERS.get(name.toLowerCase(Locale.ROOT));
    return builderSupplier != null ? builderSupplier.get() : null;
  }

  /** 初始化所有内置过程的 builder 映射（rollback/cherrypick/rewrite/migrate/snapshot 等）。 */
  private static Map<String, Supplier<ProcedureBuilder>> initProcedureBuilders() {
    ImmutableMap.Builder<String, Supplier<ProcedureBuilder>> mapBuilder = ImmutableMap.builder();
    mapBuilder.put("rollback_to_snapshot", RollbackToSnapshotProcedure::builder);
    mapBuilder.put("rollback_to_timestamp", RollbackToTimestampProcedure::builder);
    mapBuilder.put("set_current_snapshot", SetCurrentSnapshotProcedure::builder);
    mapBuilder.put("cherrypick_snapshot", CherrypickSnapshotProcedure::builder);
    mapBuilder.put("rewrite_data_files", RewriteDataFilesProcedure::builder);
    mapBuilder.put("rewrite_manifests", RewriteManifestsProcedure::builder);
    mapBuilder.put("remove_orphan_files", RemoveOrphanFilesProcedure::builder);
    mapBuilder.put("expire_snapshots", ExpireSnapshotsProcedure::builder);
    mapBuilder.put("migrate", MigrateTableProcedure::builder);
    mapBuilder.put("snapshot", SnapshotTableProcedure::builder);
    mapBuilder.put("add_files", AddFilesProcedure::builder);
    mapBuilder.put("ancestors_of", AncestorsOfProcedure::builder);
    mapBuilder.put("register_table", RegisterTableProcedure::builder);
    mapBuilder.put("publish_changes", PublishChangesProcedure::builder);
    mapBuilder.put("create_changelog_view", CreateChangelogViewProcedure::builder);
    mapBuilder.put("rewrite_position_delete_files", RewritePositionDeleteFilesProcedure::builder);
    mapBuilder.put("fast_forward", FastForwardBranchProcedure::builder);
    return mapBuilder.build();
  }

  /**
   * 过程 builder 接口：设置 catalog 并构造过程实例。
   *
   * <p>设计意图：把过程构造与 catalog 注入解耦，由基类 Builder 统一处理 catalog。
   */
  public interface ProcedureBuilder {
    ProcedureBuilder withTableCatalog(TableCatalog tableCatalog);

    Procedure build();
  }
}
