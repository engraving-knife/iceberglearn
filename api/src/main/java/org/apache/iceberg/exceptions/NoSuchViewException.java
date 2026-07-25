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
 * 视图不存在异常：尝试加载/操作一个不存在的视图时抛出。
 *
 * <p>所属模块：iceberg-api（定义核心公共 API 与异常契约的最底层模块）。
 *
 * <p>触发场景：Catalog 在按标识符加载视图时未找到对应视图，或在视图已被删除后继续操作。
 *
 * <p>设计意图：以 {@link RuntimeException} 为基类，构造器接受 {@code String.format} 风格模板， 配合 {@link FormatMethod}
 * 做编译期格式化校验。未实现 {@link CleanableFailure} （与 {@link NoSuchTableException} 不同），视图不存在通常不涉及提交临时状态清理。
 *
 * <p>上下游关系：由各 Catalog/视图操作实现抛出；调用方据以决定是否创建视图或回退逻辑。
 */
public class NoSuchViewException extends RuntimeException {
  /**
   * 构造一个视图不存在异常，消息按 {@link String#format(String, Object...)} 格式化。
   *
   * @param message 消息模板
   * @param args 模板参数
   */
  @FormatMethod
  public NoSuchViewException(String message, Object... args) {
    super(String.format(message, args));
  }

  /**
   * 构造一个带原因的视图不存在异常，消息按 {@link String#format(String, Object...)} 格式化。
   *
   * @param cause 原始异常
   * @param message 消息模板
   * @param args 模板参数
   */
  @FormatMethod
  public NoSuchViewException(Throwable cause, String message, Object... args) {
    super(String.format(message, args), cause);
  }
}
