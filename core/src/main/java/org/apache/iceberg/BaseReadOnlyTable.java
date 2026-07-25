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

/**
 * 只读 {@link Table} 的抽象基类：把所有写操作 API 一律抛出 {@link UnsupportedOperationException}。
 *
 * <p>所属模块：iceberg-core（核心实现层），是元数据表等只读表实现的公共父类。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>为只读表（如元数据表、静态表）统一禁用 schema/spec/properties/sortOrder/location
 *       修改、append/rewrite/overwrite/delete、事务、快照管理等所有写操作。
 *   <li>通过 {@code descriptor} 在异常信息中描述表类型（如 "metadata"），便于使用者定位问题。
 * </ul>
 *
 * <p>设计意图：模板方法 + 默认拒绝实现，让具体只读子类只关心读路径实现，避免在每个子类中 重复实现几十个“不支持”方法。这是“接口隔离 + 默认拒绝”模式的典型应用。
 *
 * <p>上下游关系：被 {@link BaseMetadataTable}、{@link StaticTable} 等只读表实现继承。
 */
abstract class BaseReadOnlyTable implements Table {

  private final String descriptor;

  /**
   * 构造方法。
   *
   * @param descriptor 表类型描述，用于异常信息中说明禁止写操作的原因
   */
  BaseReadOnlyTable(String descriptor) {
    this.descriptor = descriptor;
  }

  /** 禁止修改只读表的 schema。 */
  @Override
  public UpdateSchema updateSchema() {
    throw new UnsupportedOperationException(
        "Cannot update the schema of a " + descriptor + " table");
  }

  /** 禁止修改只读表的分区 spec。 */
  @Override
  public UpdatePartitionSpec updateSpec() {
    throw new UnsupportedOperationException(
        "Cannot update the partition spec of a " + descriptor + " table");
  }

  /** 禁止修改只读表的属性。 */
  @Override
  public UpdateProperties updateProperties() {
    throw new UnsupportedOperationException(
        "Cannot update the properties of a " + descriptor + " table");
  }

  /** 禁止修改只读表的排序顺序。 */
  @Override
  public ReplaceSortOrder replaceSortOrder() {
    throw new UnsupportedOperationException(
        "Cannot update the sort order of a " + descriptor + " table");
  }

  /** 禁止修改只读表的存储位置。 */
  @Override
  public UpdateLocation updateLocation() {
    throw new UnsupportedOperationException(
        "Cannot update the location of a " + descriptor + " table");
  }

  /** 禁止向只读表追加文件。 */
  @Override
  public AppendFiles newAppend() {
    throw new UnsupportedOperationException("Cannot append to a " + descriptor + " table");
  }

  /** 禁止重写只读表文件。 */
  @Override
  public RewriteFiles newRewrite() {
    throw new UnsupportedOperationException("Cannot rewrite in a " + descriptor + " table");
  }

  /** 禁止重写只读表 manifest。 */
  @Override
  public RewriteManifests rewriteManifests() {
    throw new UnsupportedOperationException(
        "Cannot rewrite manifests in a " + descriptor + " table");
  }

  /** 禁止覆盖只读表文件。 */
  @Override
  public OverwriteFiles newOverwrite() {
    throw new UnsupportedOperationException("Cannot overwrite in a " + descriptor + " table");
  }

  /** 禁止对只读表执行行级增删。 */
  @Override
  public RowDelta newRowDelta() {
    throw new UnsupportedOperationException(
        "Cannot remove or replace rows in a " + descriptor + " table");
  }

  /** 禁止替换只读表分区。 */
  @Override
  public ReplacePartitions newReplacePartitions() {
    throw new UnsupportedOperationException(
        "Cannot replace partitions in a " + descriptor + " table");
  }

  /** 禁止从只读表删除文件。 */
  @Override
  public DeleteFiles newDelete() {
    throw new UnsupportedOperationException("Cannot delete from a " + descriptor + " table");
  }

  /** 禁止更新只读表统计信息。 */
  @Override
  public UpdateStatistics updateStatistics() {
    throw new UnsupportedOperationException(
        "Cannot update statistics of a " + descriptor + " table");
  }

  /** 禁止对只读表过期快照。 */
  @Override
  public ExpireSnapshots expireSnapshots() {
    throw new UnsupportedOperationException(
        "Cannot expire snapshots from a " + descriptor + " table");
  }

  /** 禁止管理只读表快照。 */
  @Override
  public ManageSnapshots manageSnapshots() {
    throw new UnsupportedOperationException(
        "Cannot manage snapshots in a " + descriptor + " table");
  }

  /** 禁止对只读表创建事务。 */
  @Override
  public Transaction newTransaction() {
    throw new UnsupportedOperationException(
        "Cannot create transactions for a " + descriptor + " table");
  }
}
