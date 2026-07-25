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

import org.apache.iceberg.encryption.EncryptionManager;
import org.apache.iceberg.encryption.PlaintextEncryptionManager;
import org.apache.iceberg.exceptions.CleanableFailure;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.io.LocationProvider;

/**
 * 表元数据访问与更新的 SPI（服务提供者接口）抽象。
 *
 * <p>所属模块：iceberg-core，定位为表存储后端（如 Hive Metastore、Hadoop 文件系统、NESSIE 等） 与上层 Iceberg API 之间的桥梁。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>提供当前表元数据的读取（{@link #current()}）与刷新（{@link #refresh()}）能力；
 *   <li>定义原子提交契约（{@link #commit(TableMetadata, TableMetadata)}），由具体后端实现保证原子性与一致性；
 *   <li>暴露读写文件所需的 {@link FileIO}、{@link LocationProvider}、{@link EncryptionManager} 等基础设施；
 *   <li>提供临时 {@link TableOperations}、新快照 ID 生成、严格清理策略等辅助方法。
 * </ul>
 *
 * <p>设计意图：通过 SPI 接口将 Iceberg 核心逻辑与具体存储后端解耦，让 Hive、Hadoop、Glue、NESSIE 等 后端只需实现该接口即可接入 Iceberg
 * 生态。{@code commit} 方法明确要求实现方在状态未知时抛出 {@link
 * org.apache.iceberg.exceptions.CommitStateUnknownException}，以便上层正确处理文件清理。
 *
 * <p>上下游关系：上游被 {@link Table}、各种 SnapshotProducer / 扫描器调用；下游对接具体的元数据存储 （如 Hive Metastore、文件系统上的
 * metadata.json）。
 */
public interface TableOperations {

  /**
   * 返回当前已加载的表元数据，不会检查后端是否已有更新。
   *
   * <p>调用方需自行决定是否需要先调用 {@link #refresh()} 来获取最新版本。
   *
   * @return 当前内存中的表元数据
   */
  TableMetadata current();

  /**
   * 检查后端是否有更新，并返回最新的表元数据。
   *
   * <p>具体实现负责从底层存储重新加载元数据，并按需替换内存中的版本。
   *
   * @return 最新刷新后的表元数据
   */
  TableMetadata refresh();

  /**
   * 用新的元数据版本替换基线元数据，实现一次原子提交。
   *
   * <p>本方法应由实现方提供并明确文档化其原子性保证。
   *
   * <p>实现要求：
   *
   * <ul>
   *   <li>必须校验 base 元数据仍是当前版本，以避免覆盖其他并发更新；
   *   <li>原子提交成功后，不得执行任何可能失败的操作——因为此处的失败无法与提交失败区分；
   *   <li>当无法确定提交是否成功时（如网络分区导致提交确认丢失），必须抛出 {@link
   *       org.apache.iceberg.exceptions.CommitStateUnknownException}，以便上层决定是否清理
   *       提交产生的文件；其余异常将被视为提交失败。
   * </ul>
   *
   * @param base 提交所基于的旧元数据，用于乐观并发校验
   * @param metadata 待提交的新元数据
   */
  void commit(TableMetadata base, TableMetadata metadata);

  /** 返回用于读写表数据与元数据文件的 {@link FileIO}。 */
  FileIO io();

  /**
   * 返回用于加解密数据文件的 {@link org.apache.iceberg.encryption.EncryptionManager}。
   *
   * <p>默认实现返回 {@link PlaintextEncryptionManager}，即不进行加密。
   *
   * @return 加密管理器
   */
  default EncryptionManager encryption() {
    return new PlaintextEncryptionManager();
  }

  /**
   * 根据元数据文件名生成其在底层存储中的完整路径。
   *
   * <p>文件可能尚未创建，此时返回的路径应能直接用于 {@link FileIO#newOutputFile(String)} 等创建操作。
   *
   * @param fileName 元数据文件名
   * @return 完整的元数据文件路径
   */
  String metadataFileLocation(String fileName);

  /**
   * 返回用于为新增数据文件分配写入位置的 {@link LocationProvider}。
   *
   * <p>该 provider 基于当前表的状态（如表位置、分区策略）生成新数据文件路径。
   *
   * @return 与当前表状态匹配的位置提供者
   */
  LocationProvider locationProvider();

  /**
   * 基于未提交的元数据返回一个临时 {@link TableOperations} 实例。
   *
   * <p>用于事务场景：当事务内部尚未提交的元数据需要被使用时（例如根据事务中修改后的表位置生成元数据文件路径）， 通过本方法获得一个"仿佛未提交的元数据已经是当前版本"的临时 ops。
   *
   * <p>事务不会在该临时 ops 上调用 {@link #refresh()} 或 {@link #commit(TableMetadata, TableMetadata)}。
   *
   * @param uncommittedMetadata 未提交的表元数据
   * @return 表现为"未提交元数据即当前"的临时表操作对象
   */
  default TableOperations temp(TableMetadata uncommittedMetadata) {
    return this;
  }

  /**
   * 生成一个新的快照 ID。
   *
   * @return 一个 long 类型的快照 ID
   */
  default long newSnapshotId() {
    return SnapshotIdGeneratorUtil.generateSnapshotID();
  }

  /**
   * 是否仅在提交抛出 {@link CleanableFailure} 时才清理未提交的元数据文件。
   *
   * <p>默认返回 {@code true}：仅在异常被标记为 {@link CleanableFailure} 时才进行清理。
   *
   * @return 是否要求严格清理策略
   */
  default boolean requireStrictCleanup() {
    return true;
  }
}
