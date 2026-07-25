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
 * 提交状态未知异常：无法确认提交是成功还是失败时抛出。
 *
 * <p>所属模块：iceberg-api（定义核心公共 API 与异常契约的最底层模块）。
 *
 * <p>触发场景：提交请求已发出，但在收到 Catalog 确认响应前发生网络中断/超时/客户端崩溃等， 导致客户端无法判断提交是否已落库。直接重试可能导致重复写入或意外修改。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>故意<strong>不</strong>实现 {@link CleanableFailure}：状态未知时清理可能误删已成功提交 所需的数据文件，因此禁止自动清理。
 *   <li>通过 {@link #COMMON_INFO} 常量附加固定指引，提示人工核查并说明可用 Remove Orphan Files 动作在恢复 Catalog 连接后清理潜在孤儿文件。
 * </ul>
 *
 * <p>上下游关系：由 core 模块的提交逻辑在通讯异常路径上抛出；调用方必须人工确认后再决定重试或回滚。
 */
public class CommitStateUnknownException extends RuntimeException {

  private static final String COMMON_INFO =
      "Cannot determine whether the commit was successful or not, the underlying data files may or "
          + "may not be needed. Manual intervention via the Remove Orphan Files Action can remove these "
          + "files when a connection to the Catalog can be re-established if the commit was actually unsuccessful.\n"
          + "Please check to see whether or not your commit was successful before retrying this commit. Retrying "
          + "an already successful operation will result in duplicate records or unintentional modifications.\n"
          + "At this time no files will be deleted including possibly unused manifest lists.";

  /**
   * 构造一个提交状态未知异常，消息由 cause 消息与公共说明拼接而成。
   *
   * @param cause 导致状态未知的原始异常
   */
  public CommitStateUnknownException(Throwable cause) {
    super(cause.getMessage() + "\n" + COMMON_INFO, cause);
  }

  /**
   * 构造一个带自定义前缀消息的提交状态未知异常。
   *
   * <p>最终消息格式为：{@code message + cause 消息 + COMMON_INFO}。
   *
   * @param message 自定义前缀消息
   * @param cause 导致状态未知的原始异常
   */
  public CommitStateUnknownException(String message, Throwable cause) {
    super(message + "\n" + cause.getMessage() + "\n" + COMMON_INFO, cause);
  }
}
