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

import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 重写表 manifest 的 API。
 *
 * <p>所属模块：iceberg-api（表维护操作接口层）。
 *
 * <p>职责：累积 manifest 文件，生成一个仅由"新增 manifest"描述的表 {@link Snapshot} 并提交 为当前快照。支持按聚类函数重写匹配的
 * manifest，也可直接替换指定 manifest。被替换 manifest 中的活跃文件集合必须与新 manifest 一致。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>通过 {@link #deleteManifest(ManifestFile)} 或 {@link #addManifest(ManifestFile)} 直接增删的
 *       manifest 在重写过程中被忽略，避免重复处理。
 *   <li>提交时将变更应用到最新表快照；若发生冲突，则应用到新的最新快照并重试。
 * </ul>
 *
 * <p>上下游关系：继承 {@link SnapshotUpdate}；由 core 模块实现，被维护作业调用以整理 manifest 布局、压缩小 manifest 等。
 */
public interface RewriteManifests extends SnapshotUpdate<RewriteManifests> {
  /**
   * 按给定函数对现有 {@link DataFile} 进行聚类分组。
   *
   * <p>设计要点：cluster key 决定数据文件归属哪个 manifest；同一 cluster key 的数据文件会 写入同一 manifest（除非文件过大被拆分）。通过
   * {@link #deleteManifest(ManifestFile)} 或 {@link #addManifest(ManifestFile)} 直接增删的 manifest
   * 在重写中被忽略。
   *
   * @param func 用于将数据文件聚类到 manifest 的函数
   * @return this，便于链式调用
   */
  RewriteManifests clusterBy(Function<DataFile, Object> func);

  /**
   * 设定谓词以决定哪些现有 {@link ManifestFile} 需要重写。
   *
   * <p>设计要点：不匹配谓词的 manifest 原样保留；若未调用本方法且未设置谓词，则重写所有 manifest。
   *
   * @param predicate 判断 manifest 是否参与重写的谓词，返回 true 表示参与重写，false 表示保留
   * @return this，便于链式调用
   */
  RewriteManifests rewriteIf(Predicate<ManifestFile> predicate);

  /**
   * 从表中删除指定的 {@link ManifestFile}。
   *
   * @param manifest 待删除的 manifest
   * @return this，便于链式调用
   */
  RewriteManifests deleteManifest(ManifestFile manifest);

  /**
   * 向表中添加一个 {@link ManifestFile}。所添加 manifest 不能包含新增或删除的文件。
   *
   * <p>设计要点：
   *
   * <ul>
   *   <li>默认情况下该 manifest 会被重写以保证所有 entry 都带有显式 snapshot ID，此时 原 manifest 的生命周期由调用方管理。
   *   <li>若允许 entry 继承提交时分配的 snapshot ID，则提交成功后该 manifest 成为表元数据 一部分，不应手动删除，会在过期时被清理；若在准备新快照时与其他
   *       manifest 合并， 成功后会被自动删除；若提交失败，则不会被删除，是否删除或复用由调用方决定。
   * </ul>
   *
   * @param manifest 待添加的 manifest
   * @return this，便于链式调用
   */
  RewriteManifests addManifest(ManifestFile manifest);
}
