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
 * 资源已存在异常：尝试创建一个已存在的表/视图/分支/标签等资源时抛出。
 *
 * <p>所属模块：iceberg-api（定义核心公共 API 与异常契约的最底层模块）。
 *
 * <p>触发场景：Catalog 在创建表/视图/分支/标签等时发现同名资源已存在。
 *
 * <p>设计意图：以 {@link RuntimeException} 为基类，构造器接受 {@code String.format} 风格模板， 配合 {@link FormatMethod}
 * 做编译期格式化校验。未实现 {@link CleanableFailure}， 因为"已存在"通常代表环境状态有效而非失败产生的脏状态。
 *
 * <p>上下游关系：由各 Catalog/表操作实现抛出；调用方据以决定改为加载或抛错。
 */
public class AlreadyExistsException extends RuntimeException {
  /**
   * 构造一个资源已存在异常，消息按 {@link String#format(String, Object...)} 格式化。
   *
   * @param message 消息模板
   * @param args 模板参数
   */
  @FormatMethod
  public AlreadyExistsException(String message, Object... args) {
    super(String.format(message, args));
  }

  /**
   * 构造一个带原因的资源已存在异常，消息按 {@link String#format(String, Object...)} 格式化。
   *
   * @param cause 原始异常
   * @param message 消息模板
   * @param args 模板参数
   */
  @FormatMethod
  public AlreadyExistsException(Throwable cause, String message, Object... args) {
    super(String.format(message, args), cause);
  }
}
