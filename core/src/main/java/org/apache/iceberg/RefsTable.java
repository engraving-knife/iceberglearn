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

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.types.Types;

/**
 * 元数据表实现：把表的已知快照引用（refs）以行的形式暴露出来。
 *
 * <p>所属模块：iceberg-core（元数据表实现层）。
 *
 * <p>职责：扫描时返回表中所有 {@link SnapshotRef}（分支与标签）及其属性（类型、快照 id、 保留策略等）作为可查询行。
 *
 * <p>设计意图：作为只读元数据表，用于审计与查看分支/标签配置。结果通过 {@link StaticDataTask} 静态生成（数据来自表元数据的 refs map，不涉及文件扫描）。
 *
 * <p>上下游关系：继承 {@link BaseMetadataTable}；扫描委托给内部 {@link RefsTableScan}， 数据由 {@link StaticDataTask}
 * 提供。
 */
public class RefsTable extends BaseMetadataTable {
  private static final Schema SNAPSHOT_REF_SCHEMA =
      new Schema(
          Types.NestedField.required(1, "name", Types.StringType.get()),
          Types.NestedField.required(2, "type", Types.StringType.get()),
          Types.NestedField.required(3, "snapshot_id", Types.LongType.get()),
          Types.NestedField.optional(4, "max_reference_age_in_ms", Types.LongType.get()),
          Types.NestedField.optional(5, "min_snapshots_to_keep", Types.IntegerType.get()),
          Types.NestedField.optional(6, "max_snapshot_age_in_ms", Types.LongType.get()));

  /**
   * 构造一个名为 "&lt;表名&gt;.refs" 的 refs 元数据表。
   *
   * @param table 被包装的底层表
   */
  RefsTable(Table table) {
    this(table, table.name() + ".refs");
  }

  /**
   * 构造指定名称的 refs 元数据表。
   *
   * @param table 被包装的底层表
   * @param name 元数据表名
   */
  RefsTable(Table table, String name) {
    super(table, name);
  }

  /** 创建一个新的 refs 表扫描实例。 */
  @Override
  public TableScan newScan() {
    return new RefsTableScan(table());
  }

  /** 返回该元数据表的固定 schema。 */
  @Override
  public Schema schema() {
    return SNAPSHOT_REF_SCHEMA;
  }

  /**
   * 构造一个静态数据任务，把 refs map 转为可扫描行。
   *
   * @param scan 当前扫描
   * @return 静态数据任务
   */
  private DataTask task(BaseTableScan scan) {
    Collection<String> refNames = table().refs().keySet();
    return StaticDataTask.of(
        table().io().newInputFile(table().operations().current().metadataFileLocation()),
        schema(),
        scan.schema(),
        refNames,
        referencesToRows(table().refs()));
  }

  /** 返回该元数据表的类型标识。 */
  @Override
  MetadataTableType metadataTableType() {
    return MetadataTableType.REFS;
  }

  /** refs 表扫描实现：基于 {@link StaticTableScan}，在 planFiles 时调用外层的 task 方法生成静态数据。 */
  private class RefsTableScan extends StaticTableScan {
    RefsTableScan(Table table) {
      super(table, SNAPSHOT_REF_SCHEMA, MetadataTableType.REFS, RefsTable.this::task);
    }

    RefsTableScan(Table table, TableScanContext context) {
      super(table, SNAPSHOT_REF_SCHEMA, MetadataTableType.REFS, RefsTable.this::task, context);
    }

    @Override
    protected TableScan newRefinedScan(Table table, Schema schema, TableScanContext context) {
      return new RefsTableScan(table, context);
    }

    /** 执行计划：返回包含所有 refs 行的静态任务。 */
    @Override
    public CloseableIterable<FileScanTask> planFiles() {
      return CloseableIterable.withNoopClose(RefsTable.this.task(this));
    }
  }

  /**
   * 把 refs map 转换为行列表的函数：每个 ref 名映射为一行（name, type, snapshot_id, ...）。
   *
   * @param refs 表的 refs map
   * @return 把 ref 名转换为 {@link StaticDataTask.Row} 的函数
   */
  private static Function<String, StaticDataTask.Row> referencesToRows(
      Map<String, SnapshotRef> refs) {
    return refName ->
        StaticDataTask.Row.of(
            refName,
            refs.get(refName).type().name(),
            refs.get(refName).snapshotId(),
            refs.get(refName).maxRefAgeMs(),
            refs.get(refName).minSnapshotsToKeep(),
            refs.get(refName).maxSnapshotAgeMs());
  }
}
