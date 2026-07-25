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
package org.apache.iceberg.util;

import org.apache.iceberg.Snapshot;
import org.apache.iceberg.SnapshotSummary;
import org.apache.iceberg.TableMetadata;
import org.apache.iceberg.exceptions.DuplicateWAPCommitException;

/**
 * Write-Audit-Publish (WAP) 工作流工具。
 *
 * <p>所属模块：iceberg-core（util 子包）。职责：从快照 summary 中提取 staged/published WAP id， 并在发布 staged
 * 快照时校验是否重复发布。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>基于 summary 的 WAP id：staged 快照在 summary 中记录 {@code staged-wap-id}， 发布时改为 {@code
 *       published-wap-id}。
 *   <li>重复发布检测：若同一 wap-id 已被发布过，抛出 {@link DuplicateWAPCommitException}。
 *   <li>非 WAP 流程放行：没有 wap id 的快照直接放行，不影响普通提交。
 * </ul>
 *
 * <p>上下游关系：被 {@link SnapshotManager} 在 cherry-pick 发布 staged 快照时调用。
 */
public class WapUtil {

  /** 私有构造：工具类禁止实例化。 */
  private WapUtil() {}

  /**
   * 从快照 summary 中提取 staged WAP id（待发布的写审计发布标识）。
   *
   * @param snapshot 快照
   * @return staged WAP id，若无则返回 null
   */
  public static String stagedWapId(Snapshot snapshot) {
    return snapshot.summary() != null
        ? snapshot.summary().get(SnapshotSummary.STAGED_WAP_ID_PROP)
        : null;
  }

  /**
   * 从快照 summary 中提取 published WAP id（已发布的写审计发布标识）。
   *
   * @param snapshot 快照
   * @return published WAP id，若无则返回 null
   */
  public static String publishedWapId(Snapshot snapshot) {
    return snapshot.summary() != null
        ? snapshot.summary().get(SnapshotSummary.PUBLISHED_WAP_ID_PROP)
        : null;
  }

  /**
   * 校验给定 staged 快照关联的 wap-id 是否已被发布。
   *
   * <p>步骤：取出快照的 staged wap-id，若非空则检查是否已在祖先快照中发布过， 已发布则抛出 {@link DuplicateWAPCommitException}。非 WAP
   * 流程（无 wap-id）直接放行。
   *
   * @param current 当前表元数据
   * @param wapSnapshotId 待发布的 staged 快照 id
   * @return 该快照关联的 WAP ID（无则返回 null）
   * @throws DuplicateWAPCommitException 当 wap-id 已被发布过时
   */
  public static String validateWapPublish(TableMetadata current, long wapSnapshotId) {
    Snapshot cherryPickSnapshot = current.snapshot(wapSnapshotId);
    String wapId = stagedWapId(cherryPickSnapshot);
    if (wapId != null && !wapId.isEmpty()) {
      if (WapUtil.isWapIdPublished(current, wapId)) {
        throw new DuplicateWAPCommitException(wapId);
      }
    }

    return wapId;
  }

  /**
   * 遍历当前快照的所有祖先，检查是否有任一祖先的 staged 或 published wap-id 与给定值匹配。
   *
   * @param current 当前表元数据
   * @param wapId 待检查的 WAP ID
   * @return true 表示该 wap-id 已被发布过
   */
  private static boolean isWapIdPublished(TableMetadata current, String wapId) {
    for (long ancestorId : SnapshotUtil.ancestorIds(current.currentSnapshot(), current::snapshot)) {
      Snapshot snapshot = current.snapshot(ancestorId);
      if (wapId.equals(stagedWapId(snapshot)) || wapId.equals(publishedWapId(snapshot))) {
        return true;
      }
    }
    return false;
  }
}
