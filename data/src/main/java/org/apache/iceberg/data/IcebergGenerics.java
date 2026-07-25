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
package org.apache.iceberg.data;

import java.util.Collection;
import org.apache.iceberg.Schema;
import org.apache.iceberg.Table;
import org.apache.iceberg.TableScan;
import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.io.CloseableIterable;

/**
 * Iceberg 通用 Record 读写入口工具类：提供面向 JVM 应用的便捷表读取 API。
 *
 * <p>所属模块：iceberg-data（向 JVM 应用提供基于 {@link Record} 等通用模型的 Iceberg 表读写支持； 本类是该模块对外暴露的顶层入口）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>通过 {@link #read(Table)} 返回 {@link ScanBuilder}，以链式 API 配置并执行表扫描， 产出 {@link Record} 流。
 *   <li>屏蔽扫描、投影、过滤、删除应用等底层细节，提供最小依赖的“开箱即用”读取能力。
 * </ul>
 *
 * <p>设计意图：工具类风格，构造私有、仅静态方法；底层委托给 {@link TableScanIterable} 与 {@link
 * GenericReader}，本身不持有状态，便于在脚本/测试/迁移等场景直接使用。
 *
 * <p>上下游关系：被外部 JVM 应用、测试、迁移工具直接调用；依赖 iceberg-api 的 {@link Table} 与 {@link TableScan}，以及本模块的 {@link
 * ScanBuilder} / {@link TableScanIterable}。
 */
public class IcebergGenerics {
  private IcebergGenerics() {}

  /**
   * 入口方法：返回一个 {@link ScanBuilder} 用于配置并执行对指定表的读取，产出通用 {@link Record}。
   *
   * @param table 目标 Iceberg 表
   * @return 扫描构建器
   */
  public static ScanBuilder read(Table table) {
    return new ScanBuilder(table);
  }

  /**
   * 扫描构建器：以链式 API 包装 {@link TableScan}，配置投影/过滤/快照等参数后 {@link #build()} 产出 {@link Record} 可迭代对象。
   *
   * <p>设计意图：把 {@link TableScan} 的配置能力以更简洁的链式风格暴露给通用 Record 用户， 内部仍委托给 {@link TableScan}
   * 的对应方法，避免重复实现扫描语义。
   */
  public static class ScanBuilder {
    private TableScan tableScan;
    private boolean reuseContainers = false;

    /**
     * 构造扫描构建器，基于表创建一个新的 {@link TableScan}。
     *
     * @param table 目标表
     */
    public ScanBuilder(Table table) {
      this.tableScan = table.newScan();
    }

    /** 启用容器复用以降低 GC 压力（迭代过程中复用同一 Record 对象）。 */
    public ScanBuilder reuseContainers() {
      this.reuseContainers = true;
      return this;
    }

    /**
     * 设置行过滤表达式（下推到扫描）。
     *
     * @param rowFilter 行过滤表达式
     * @return this
     */
    public ScanBuilder where(Expression rowFilter) {
      this.tableScan = tableScan.filter(rowFilter);
      return this;
    }

    /** 设置扫描为大小写不敏感。 */
    public ScanBuilder caseInsensitive() {
      this.tableScan = tableScan.caseSensitive(false);
      return this;
    }

    /**
     * 按列名选择投影列（可变参数）。
     *
     * @param selectedColumns 选中的列名
     * @return this
     */
    public ScanBuilder select(String... selectedColumns) {
      this.tableScan = tableScan.select(selectedColumns);
      return this;
    }

    /**
     * 按列名集合选择投影列。
     *
     * @param columns 选中的列名集合
     * @return this
     */
    public ScanBuilder select(Collection<String> columns) {
      this.tableScan = tableScan.select(columns);
      return this;
    }

    /**
     * 设置投影 Schema。
     *
     * @param schema 投影 Schema
     * @return this
     */
    public ScanBuilder project(Schema schema) {
      this.tableScan = tableScan.project(schema);
      return this;
    }

    /**
     * 指定扫描使用的快照 ID。
     *
     * @param scanSnapshotId 快照 ID
     * @return this
     */
    public ScanBuilder useSnapshot(long scanSnapshotId) {
      this.tableScan = tableScan.useSnapshot(scanSnapshotId);
      return this;
    }

    /**
     * 扫描指定时间点（毫秒）对应的快照。
     *
     * @param scanTimestampMillis 时间戳（毫秒）
     * @return this
     */
    public ScanBuilder asOfTime(long scanTimestampMillis) {
      this.tableScan = tableScan.asOfTime(scanTimestampMillis);
      return this;
    }

    /**
     * 只扫描两个快照之间新增的文件（增量追加）。
     *
     * @param fromSnapshotId 起始快照 ID
     * @param toSnapshotId 结束快照 ID
     * @return this
     */
    public ScanBuilder appendsBetween(long fromSnapshotId, long toSnapshotId) {
      this.tableScan = tableScan.appendsBetween(fromSnapshotId, toSnapshotId);
      return this;
    }

    /**
     * 只扫描指定快照之后新增的文件（增量追加）。
     *
     * @param fromSnapshotId 起始快照 ID
     * @return this
     */
    public ScanBuilder appendsAfter(long fromSnapshotId) {
      this.tableScan = tableScan.appendsAfter(fromSnapshotId);
      return this;
    }

    /**
     * 构建并返回 Record 可迭代对象，实际执行扫描规划与读取。
     *
     * @return 扫描结果 Record 流
     */
    public CloseableIterable<Record> build() {
      return new TableScanIterable(tableScan, reuseContainers);
    }
  }
}
