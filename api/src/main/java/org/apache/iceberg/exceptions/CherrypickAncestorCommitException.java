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
package org.apache.iceberg.exceptions;

/**
 * Cherrypick 祖先提交异常：在尝试 cherrypick 一个已是当前分支祖先的快照时抛出。
 *
 * <p>所属模块：iceberg-api（定义核心公共 API 与异常契约的最底层模块）。
 *
 * <p>触发场景：
 *
 * <ul>
 *   <li>cherrypick 的目标快照本身已是当前表状态的祖先，无需再 pick。
 *   <li>该快照此前已被 pick 用于生成某个已发布的祖先，重复 pick 会造成冗余。
 *   <li>用于避免对非 WAP（Write-Audit-Publish）快照的重复 cherrypick。
 * </ul>
 *
 * <p>设计意图：继承 {@link ValidationException}（语义上属于"操作前提不满足"的校验失败）， 因此也是 {@link
 * CleanableFailure}，可安全清理本次 pick 产生的临时状态。
 *
 * <p>上下游关系：由 core 模块的 cherrypick 操作在祖先检查阶段抛出；调用方据以跳过或提示。
 */
public class CherrypickAncestorCommitException extends ValidationException {

  /**
   * 构造一个"目标快照已是祖先"的异常。
   *
   * @param snapshotId 被尝试 cherrypick 的快照 ID
   */
  public CherrypickAncestorCommitException(long snapshotId) {
    super("Cannot cherrypick snapshot %s: already an ancestor", String.valueOf(snapshotId));
  }

  /**
   * 构造一个"目标快照已被 pick 生成已发布祖先"的异常。
   *
   * @param snapshotId 被尝试 cherrypick 的快照 ID
   * @param publishedAncestorId 此前 pick 生成的已发布祖先快照 ID
   */
  public CherrypickAncestorCommitException(long snapshotId, long publishedAncestorId) {
    super(
        "Cannot cherrypick snapshot %s: already picked to create ancestor %s",
        String.valueOf(snapshotId), String.valueOf(publishedAncestorId));
  }
}
