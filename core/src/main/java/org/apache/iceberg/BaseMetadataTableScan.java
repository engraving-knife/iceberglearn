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

import org.apache.iceberg.util.PropertyUtil;

/**
 * 元数据表扫描的抽象基类：在 {@link BaseTableScan} 基础上记录元数据表类型，并禁用增量扫描。
 *
 * <p>所属模块：iceberg-core，为 {@code FilesTable}、{@code ManifestsTable}、{@code StaticTableScan}
 * 等各类元数据表扫描提供统一基类。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>携带 {@link MetadataTableType}，供日志与异常信息区分元数据表种类。
 *   <li>禁用 {@link #appendsBetween(long, long)} 与 {@link #appendsAfter(long)} 增量扫描，
 *       因为元数据表本身不具备"追加增量"语义。
 *   <li>覆写 {@link #targetSplitSize()}：优先用扫描选项中的分片大小，否则取表属性 {@code METADATA_SPLIT_SIZE} 的配置值。
 * </ul>
 *
 * <p>设计意图：元数据表扫描的目标分片大小通常与普通数据表不同（元数据文件较小）， 故单独读取 {@code metadata.split-size}
 * 属性；增量扫描对元数据表无意义，统一在此抛异常， 避免每个子类重复实现。
 *
 * <p>上下游关系：被 {@link StaticTableScan}、{@code BaseFilesTableScan} 等继承； 由各元数据表的 {@code newScan()} 创建。
 */
abstract class BaseMetadataTableScan extends BaseTableScan {

  private final MetadataTableType tableType;

  /**
   * 构造元数据表扫描（无上下文）。
   *
   * @param table 底层原始表
   * @param schema 输出 schema
   * @param tableType 元数据表类型
   */
  protected BaseMetadataTableScan(Table table, Schema schema, MetadataTableType tableType) {
    super(table, schema, TableScanContext.empty());
    this.tableType = tableType;
  }

  /**
   * 构造元数据表扫描（带上下文）。
   *
   * @param table 底层原始表
   * @param schema 输出 schema
   * @param tableType 元数据表类型
   * @param context 扫描上下文
   */
  protected BaseMetadataTableScan(
      Table table, Schema schema, MetadataTableType tableType, TableScanContext context) {
    super(table, schema, context);
    this.tableType = tableType;
  }

  /**
   * 返回当前扫描的元数据表类型，如扫描 {@link org.apache.iceberg.AllDataFilesTable} 时为 {@link
   * MetadataTableType#ALL_DATA_FILES}。
   *
   * <p>用于日志与错误信息中区分扫描种类。
   *
   * @return 元数据表类型
   */
  protected MetadataTableType tableType() {
    return tableType;
  }

  /**
   * 元数据表不支持增量扫描指定区间。
   *
   * @throws UnsupportedOperationException 始终抛出
   */
  @Override
  public TableScan appendsBetween(long fromSnapshotId, long toSnapshotId) {
    throw new UnsupportedOperationException(
        String.format("Cannot incrementally scan table of type %s", tableType()));
  }

  /**
   * 元数据表不支持增量扫描某快照之后的追加。
   *
   * @throws UnsupportedOperationException 始终抛出
   */
  @Override
  public TableScan appendsAfter(long fromSnapshotId) {
    throw new UnsupportedOperationException(
        String.format("Cannot incrementally scan table of type %s", tableType()));
  }

  /**
   * 返回元数据表扫描的目标分片大小。
   *
   * <p>逻辑：优先使用扫描选项 {@code split-size}；若未设置，则回退到表属性 {@link TableProperties#METADATA_SPLIT_SIZE}（默认值见
   * {@link TableProperties#METADATA_SPLIT_SIZE_DEFAULT}）。
   *
   * @return 目标分片大小（字节）
   */
  @Override
  public long targetSplitSize() {
    long tableValue =
        ((BaseTable) table())
            .operations()
            .current()
            .propertyAsLong(
                TableProperties.METADATA_SPLIT_SIZE, TableProperties.METADATA_SPLIT_SIZE_DEFAULT);
    return PropertyUtil.propertyAsLong(options(), TableProperties.SPLIT_SIZE, tableValue);
  }
}
