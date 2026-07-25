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
 * REST 客户端异常基类：所有与 Iceberg REST Catalog 通信相关的运行时异常的根。
 *
 * <p>所属模块：iceberg-api（定义核心公共 API 与异常契约的最底层模块）。
 *
 * <p>触发场景：REST 客户端在与 REST Catalog 服务端交互时发生错误（如序列化失败、 连接异常、非预期响应等）。子类按 HTTP 状态语义进一步细分： {@link
 * NotAuthorizedException}（401）、{@link ForbiddenException}（403，非 REST 子树）、 {@link
 * ServiceFailureException}（5XX）、{@link ServiceUnavailableException}（503）、 {@link
 * UnprocessableEntityException} 等。
 *
 * <p>设计意图：以 {@link RuntimeException} 为基类，避免调用方繁琐的受检异常处理； 构造器接受 {@code String.format} 风格的模板与可变参数，便于按
 * error-prone 的 {@link FormatMethod} 约束做格式化校验。
 *
 * <p>上下游关系：由 REST 客户端实现抛出；调用方捕获 RESTException 即可统一处理 REST 通道错误。
 */
public class RESTException extends RuntimeException {
  /**
   * 构造一个 REST 异常，消息按 {@link String#format(String, Object...)} 格式化。
   *
   * @param message 消息模板
   * @param args 模板参数
   */
  @FormatMethod
  public RESTException(String message, Object... args) {
    super(String.format(message, args));
  }

  /**
   * 构造一个带原因的 REST 异常，消息按 {@link String#format(String, Object...)} 格式化。
   *
   * @param cause 原始异常
   * @param message 消息模板
   * @param args 模板参数
   */
  @FormatMethod
  public RESTException(Throwable cause, String message, Object... args) {
    super(String.format(message, args), cause);
  }
}
