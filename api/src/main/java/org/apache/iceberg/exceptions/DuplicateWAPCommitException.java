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
 * 重复 WAP 提交异常：在 WAP（Write-Audit-Publish）工作流中检测到重复的 wap id 提交时抛出。
 *
 * <p>所属模块：iceberg-api（定义核心公共 API 与异常契约的最底层模块）。
 *
 * <p>触发场景：客户端尝试 cherrypick 一个已经被发布过的 wap id，即同一 wap id 对应的快照 此前已被发布。这通常表示重复请求，用于帮助客户端识别并去重。
 *
 * <p>设计意图：继承 {@link ValidationException}（语义上属于"操作前提不满足"的校验失败）， 因此也是 {@link
 * CleanableFailure}，可安全清理本次重复 pick 产生的临时状态。
 *
 * <p>上下游关系：由 core 模块的 WAP cherrypick 操作在 wap id 去重检查阶段抛出； 调用方据以识别重复请求并跳过。
 */
public class DuplicateWAPCommitException extends ValidationException {

  /**
   * 构造一个重复 WAP 提交异常。
   *
   * @param wapId 被重复请求发布的 wap id
   */
  public DuplicateWAPCommitException(String wapId) {
    super("Duplicate request to cherry pick wap id that was published already: %s", wapId);
  }
}
