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
package org.apache.iceberg.actions;

import java.util.function.Predicate;
import org.apache.iceberg.ManifestFile;

/**
 * 重写清单（manifests）的动作。
 *
 * <p>所属模块：iceberg-api。继承自 {@link SnapshotUpdate}，重写过程会产出新快照。
 *
 * <p>职责：按分区规范 ID、谓词过滤等条件重写清单文件，将多个小清单合并或重新组织，以改善读取 性能；可指定暂存位置写入新清单。
 *
 * <p>设计意图：随着表持续写入，清单文件可能过多或布局不佳，影响计划阶段效率。本动作提供受控的 清单重写能力，支持按 specId 与谓词精确选择待重写清单，避免无差别全量重写。
 *
 * <p>上下游关系：由引擎模块实现；结果通过 {@link Result} 返回被重写与新增的清单。
 */
public interface RewriteManifests
    extends SnapshotUpdate<RewriteManifests, RewriteManifests.Result> {
  /**
   * 重写指定分区规范 ID 对应的清单。
   *
   * <p>若未设置，默认使用表的默认 spec ID。
   *
   * @param specId 分区规范 ID
   * @return this，便于链式调用
   */
  RewriteManifests specId(int specId);

  /**
   * 仅重写匹配给定谓词的清单。
   *
   * <p>若未设置，则重写全部清单。
   *
   * @param predicate 清单过滤谓词
   * @return this，便于链式调用
   */
  RewriteManifests rewriteIf(Predicate<ManifestFile> predicate);

  /**
   * 指定暂存清单的写入位置。
   *
   * <p>若未设置，默认使用表的元数据位置。
   *
   * @param stagingLocation 暂存位置
   * @return this，便于链式调用
   */
  RewriteManifests stagingLocation(String stagingLocation);

  /** 动作执行结果，包含执行摘要。 */
  interface Result {
    /**
     * 返回被重写（替换）的清单。
     *
     * @return 被重写清单可迭代集合
     */
    Iterable<ManifestFile> rewrittenManifests();

    /**
     * 返回新增的清单。
     *
     * @return 新增清单可迭代集合
     */
    Iterable<ManifestFile> addedManifests();
  }
}
