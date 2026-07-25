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

import com.google.errorprone.annotations.FormatMethod;

/**
 * 提交失败异常：因元数据过期（乐观并发冲突）等原因导致本次提交未成功时抛出。
 *
 * <p>所属模块：iceberg-api（定义核心公共 API 与异常契约的最底层模块）。
 *
 * <p>触发场景：Iceberg 采用乐观并发控制，提交时若发现表元数据自读取后已被他人更新， 则提交被拒绝。这是可重试的常见竞态失败。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>实现 {@link CleanableFailure}：明确提交未成功，本次产生的临时 manifest/数据文件可清理。
 *   <li>与 {@link CommitStateUnknownException} 区分：本异常代表"确定失败"，后者代表"未知"。
 *   <li>提供多种构造器，支持仅消息、消息+原因、仅原因三种用法。
 * </ul>
 *
 * <p>上下游关系：由 core 模块的提交逻辑在冲突检测阶段抛出；引擎层通常捕获后重试整个操作。
 */
public class CommitFailedException extends RuntimeException implements CleanableFailure {
  /**
   * 构造一个提交失败异常，消息按 {@link String#format(String, Object...)} 格式化。
   *
   * @param message 消息模板
   * @param args 模板参数
   */
  @FormatMethod
  public CommitFailedException(String message, Object... args) {
    super(String.format(message, args));
  }

  /**
   * 构造一个带原因的提交失败异常，消息按 {@link String#format(String, Object...)} 格式化。
   *
   * @param cause 原始异常
   * @param message 消息模板
   * @param args 模板参数
   */
  @FormatMethod
  public CommitFailedException(Throwable cause, String message, Object... args) {
    super(String.format(message, args), cause);
  }

  /**
   * 构造一个仅带原因的提交失败异常，消息取自 cause。
   *
   * @param cause 原始异常
   */
  public CommitFailedException(Throwable cause) {
    super(cause);
  }
}
