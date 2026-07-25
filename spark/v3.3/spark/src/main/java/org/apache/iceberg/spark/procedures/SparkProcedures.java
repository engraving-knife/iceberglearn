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
 * Iceberg 存储过程，通过 Spark SQL CALL 调用，封装为可通过 SQL CALL 调用的存储过程。
 *
 * <p>所属模块：iceberg-spark v3.3。 类型：类 SparkProcedures。
 *
 * <p>上下游：由 SparkSessionProcedures 注册，被 Spark SQL CALL 语句调用。
 */
public class SparkProcedures {

  private static final Map<String, Supplier<ProcedureBuilder>> BUILDERS = initProcedureBuilders();

  /** 构造 SparkProcedures 实例。 */
  private SparkProcedures() {}

  /** 执行该方法的具体逻辑。 */
  public static ProcedureBuilder newBuilder(String name) {
    // procedure resolution is case insensitive to match the existing Spark behavior for functions
    Supplier<ProcedureBuilder> builderSupplier = BUILDERS.get(name.toLowerCase(Locale.ROOT));
    return builderSupplier != null ? builderSupplier.get() : null;
  }

  /** 执行初始化。 */
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
   * Iceberg 存储过程，通过 Spark SQL CALL 调用的构建器，负责分步骤构造目标对象。
   *
   * <p>所属模块：iceberg-spark v3.3。 类型：接口 ProcedureBuilder。
   *
   * <p>设计意图：建造者模式，分离复杂对象的构造与表示。
   *
   * <p>上下游：由 SparkSessionProcedures 注册，被 Spark SQL CALL 语句调用。
   */
  public interface ProcedureBuilder {
    /** 返回带新设置的副本。 */
    ProcedureBuilder withTableCatalog(TableCatalog tableCatalog);

    /** 构造并返回目标对象。 */
    Procedure build();
  }
}
