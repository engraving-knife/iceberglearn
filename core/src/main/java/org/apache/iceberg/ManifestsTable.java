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
import java.util.Map;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.types.Conversions;
import org.apache.iceberg.types.Types;

/**
 * 元数据表：把表的 manifest 文件以行形式暴露，便于查询/排查 manifest 信息。
 *
 * <p>所属模块：iceberg-core（元数据表层）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>定义 manifest 信息的 schema：content、path、length、partition_spec_id、
 *       added_snapshot_id、各类文件计数、分区摘要等。
 *   <li>把当前快照的所有 manifest 转换为 {@link StaticDataTask} 行，供 SQL 引擎查询。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>作为只读元数据表，数据直接来自快照 manifest list，不涉及实际数据扫描。
 *   <li>使用 {@link StaticDataTask} 把内存对象包装成扫描任务，无需文件 IO。
 * </ul>
 *
 * <p>上下游关系：继承 {@link BaseMetadataTable}；通过 {@link ManifestsTableScan} 提供扫描； 被用户通过
 * "tableName.manifests" 访问。
 */
public class ManifestsTable extends BaseMetadataTable {
  private static final Schema SNAPSHOT_SCHEMA =
      new Schema(
          Types.NestedField.required(14, "content", Types.IntegerType.get()),
          Types.NestedField.required(1, "path", Types.StringType.get()),
          Types.NestedField.required(2, "length", Types.LongType.get()),
          Types.NestedField.required(3, "partition_spec_id", Types.IntegerType.get()),
          Types.NestedField.required(4, "added_snapshot_id", Types.LongType.get()),
          Types.NestedField.required(5, "added_data_files_count", Types.IntegerType.get()),
          Types.NestedField.required(6, "existing_data_files_count", Types.IntegerType.get()),
          Types.NestedField.required(7, "deleted_data_files_count", Types.IntegerType.get()),
          Types.NestedField.required(15, "added_delete_files_count", Types.IntegerType.get()),
          Types.NestedField.required(16, "existing_delete_files_count", Types.IntegerType.get()),
          Types.NestedField.required(17, "deleted_delete_files_count", Types.IntegerType.get()),
          Types.NestedField.required(
              8,
              "partition_summaries",
              Types.ListType.ofRequired(
                  9,
                  Types.StructType.of(
                      Types.NestedField.required(10, "contains_null", Types.BooleanType.get()),
                      Types.NestedField.optional(11, "contains_nan", Types.BooleanType.get()),
                      Types.NestedField.optional(12, "lower_bound", Types.StringType.get()),
                      Types.NestedField.optional(13, "upper_bound", Types.StringType.get())))));

  /**
   * 构造 ManifestsTable，名称自动追加 ".manifests"。
   *
   * @param table 主表
   */
  ManifestsTable(Table table) {
    this(table, table.name() + ".manifests");
  }

  /**
   * 构造 ManifestsTable，指定元数据表名称。
   *
   * @param table 主表
   * @param name 元数据表名
   */
  ManifestsTable(Table table, String name) {
    super(table, name);
  }

  /** 创建 manifest 元数据表扫描器。 */
  @Override
  public TableScan newScan() {
    return new ManifestsTableScan(table());
  }

  /** 返回 manifest 元数据表的固定 schema。 */
  @Override
  public Schema schema() {
    return SNAPSHOT_SCHEMA;
  }

  /** 返回元数据表类型标识。 */
  @Override
  MetadataTableType metadataTableType() {
    return MetadataTableType.MANIFESTS;
  }

  /**
   * 构造 manifest 元数据表的静态数据任务。
   *
   * <p>逻辑：从扫描快照获取 manifest list 位置（若为空则回退到当前元数据文件位置）， 把所有 manifest 通过 {@link #manifestFileToRow}
   * 转换为行，包装为 {@link StaticDataTask} 返回。
   *
   * @param scan 表扫描
   * @return 静态数据任务
   */
  protected DataTask task(TableScan scan) {
    FileIO io = table().io();
    String location = scan.snapshot().manifestListLocation();
    Map<Integer, PartitionSpec> specs = Maps.newHashMap(table().specs());

    return StaticDataTask.of(
        io.newInputFile(
            location != null ? location : table().operations().current().metadataFileLocation()),
        schema(),
        scan.schema(),
        scan.snapshot().allManifests(io),
        manifest -> {
          PartitionSpec spec = specs.get(manifest.partitionSpecId());
          return ManifestsTable.manifestFileToRow(spec, manifest);
        });
  }

  /** ManifestsTable 专用的静态表扫描器，绑定 task 方法引用。 */
  private class ManifestsTableScan extends StaticTableScan {
    ManifestsTableScan(Table table) {
      super(table, SNAPSHOT_SCHEMA, MetadataTableType.MANIFESTS, ManifestsTable.this::task);
    }
  }

  /**
   * 把单个 {@link ManifestFile} 转换为静态数据行。
   *
   * <p>逻辑：按 schema 顺序填充 content、path、length、partition_spec_id、added_snapshot_id、
   * 数据文件计数、删除文件计数、分区摘要；其中数据/删除文件计数根据 manifest 内容类型 分别填到对应列，另一组列填 0。
   *
   * @param spec manifest 所属分区 spec
   * @param manifest manifest 文件
   * @return 数据行
   */
  static StaticDataTask.Row manifestFileToRow(PartitionSpec spec, ManifestFile manifest) {
    return StaticDataTask.Row.of(
        manifest.content().id(),
        manifest.path(),
        manifest.length(),
        manifest.partitionSpecId(),
        manifest.snapshotId(),
        manifest.content() == ManifestContent.DATA ? manifest.addedFilesCount() : 0,
        manifest.content() == ManifestContent.DATA ? manifest.existingFilesCount() : 0,
        manifest.content() == ManifestContent.DATA ? manifest.deletedFilesCount() : 0,
        manifest.content() == ManifestContent.DELETES ? manifest.addedFilesCount() : 0,
        manifest.content() == ManifestContent.DELETES ? manifest.existingFilesCount() : 0,
        manifest.content() == ManifestContent.DELETES ? manifest.deletedFilesCount() : 0,
        partitionSummariesToRows(spec, manifest.partitions()));
  }

  /**
   * 把 manifest 分区字段摘要列表转换为行列表。
   *
   * <p>逻辑：对每个分区字段摘要，输出 containsNull、containsNaN，并通过分区字段的 transform 把上下界字节转换为人类可读字符串。
   *
   * @param spec 分区 spec
   * @param summaries 分区字段摘要列表
   * @return 行列表；输入为 null 时返回 null
   */
  static List<StaticDataTask.Row> partitionSummariesToRows(
      PartitionSpec spec, List<ManifestFile.PartitionFieldSummary> summaries) {
    if (summaries == null) {
      return null;
    }

    List<StaticDataTask.Row> rows = Lists.newArrayList();

    for (int i = 0; i < summaries.size(); i += 1) {
      ManifestFile.PartitionFieldSummary summary = summaries.get(i);
      rows.add(
          StaticDataTask.Row.of(
              summary.containsNull(),
              summary.containsNaN(),
              spec.fields()
                  .get(i)
                  .transform()
                  .toHumanString(
                      spec.partitionType().fields().get(i).type(),
                      Conversions.fromByteBuffer(
                          spec.partitionType().fields().get(i).type(), summary.lowerBound())),
              spec.fields()
                  .get(i)
                  .transform()
                  .toHumanString(
                      spec.partitionType().fields().get(i).type(),
                      Conversions.fromByteBuffer(
                          spec.partitionType().fields().get(i).type(), summary.upperBound()))));
    }

    return rows;
  }
}
