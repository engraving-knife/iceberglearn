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
 * 表数据追加 API：将新的数据文件累积追加到表中，并生成一个新 {@link Snapshot} 作为当前快照。
 *
 * <p>所属模块：iceberg-api（顶层公共 API，定义表更新契约）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>累积待追加的 {@link DataFile} 或 {@link ManifestFile}。
 *   <li>提交时基于最新表快照应用追加，生成新快照并切换为当前。
 * </ul>
 *
 * <p>设计意图：提交时如果检测到表已前进到更新的快照，会自动把本次追加重新应用到新的最新快照上 再重试提交，从而实现乐观并发控制下的冲突解决。继承 {@link SnapshotUpdate}
 * 以复用快照提交与 指标上报等通用能力。
 *
 * <p>上下游关系：由 {@link Table#newAppend()} 创建；下游被 core 模块的 {@code SnapshotAppendFiles} 等实现类落地为元数据变更。
 */
public interface AppendFiles extends SnapshotUpdate<AppendFiles> {
  /**
   * 向表中追加一个数据文件。
   *
   * @param file 待追加的数据文件
   * @return this，便于链式调用
   */
  AppendFiles appendFile(DataFile file);

  /**
   * 向表中追加一个清单文件（manifest）。
   *
   * <p>逻辑：清单中的所有条目都会作为新增数据文件追加到本次更新生成的快照中。默认情况下， 清单会被重写以统一为本更新分配的 snapshot ID；若允许清单条目继承提交时的
   * snapshot ID， 则提交成功后该清单会并入表元数据，由快照过期机制统一回收，不应手动删除。
   *
   * <p>设计意图：支持外部已经预生成的清单直接并入，避免重复读取与重写大量条目，提升大批量 数据追加场景的性能。
   *
   * @param file 待追加的清单文件（必须只含新增文件条目）
   * @return this，便于链式调用
   */
  AppendFiles appendManifest(ManifestFile file);
}
