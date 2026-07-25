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
 * 命名空间不存在异常：尝试加载/操作一个不存在的命名空间时抛出。
 *
 * <p>所属模块：iceberg-api（定义核心公共 API 与异常契约的最底层模块）。
 *
 * <p>触发场景：Catalog 在按标识符加载命名空间时未找到对应记录，或在命名空间已被删除后继续操作。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>实现 {@link CleanableFailure}：命名空间不存在意味着提交必然失败，相关临时状态可安全清理。
 *   <li>构造器接受 {@code String.format} 风格模板，配合 {@link FormatMethod} 做编译期格式化校验。
 * </ul>
 *
 * <p>上下游关系：由各 Catalog 实现抛出；调用方据以决定是否创建命名空间或回退逻辑。
 */
public class NoSuchNamespaceException extends RuntimeException implements CleanableFailure {
  /**
   * 构造一个命名空间不存在异常，消息按 {@link String#format(String, Object...)} 格式化。
   *
   * @param message 消息模板
   * @param args 模板参数
   */
  @FormatMethod
  public NoSuchNamespaceException(String message, Object... args) {
    super(String.format(message, args));
  }

  /**
   * 构造一个带原因的命名空间不存在异常，消息按 {@link String#format(String, Object...)} 格式化。
   *
   * @param cause 原始异常
   * @param message 消息模板
   * @param args 模板参数
   */
  @FormatMethod
  public NoSuchNamespaceException(Throwable cause, String message, Object... args) {
    super(String.format(message, args), cause);
  }
}
