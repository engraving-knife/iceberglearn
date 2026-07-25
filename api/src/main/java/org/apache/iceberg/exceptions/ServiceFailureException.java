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
 * 服务端失败异常：对应 HTTP 5XX 服务端错误。
 *
 * <p>所属模块：iceberg-api（定义核心公共 API 与异常契约的最底层模块）。
 *
 * <p>触发场景：REST Catalog 服务端在处理请求时发生内部错误（如 500、502 等）。
 *
 * <p>设计意图：继承 {@link RESTException}，归入 REST 客户端异常体系；构造器接受 {@code String.format} 风格模板，配合 {@link
 * FormatMethod} 做编译期格式化校验。 与 {@link ServiceUnavailableException} 区分：后者专指 503 且实现 {@link
 * CleanableFailure}。
 *
 * <p>上下游关系：由 REST 客户端在收到 5XX 响应时抛出；调用方据以重试或上报。
 */
public class ServiceFailureException extends RESTException {
  /**
   * 构造一个服务端失败异常，消息按 {@link String#format(String, Object...)} 格式化。
   *
   * @param message 消息模板
   * @param args 模板参数
   */
  @FormatMethod
  public ServiceFailureException(String message, Object... args) {
    super(message, args);
  }

  /**
   * 构造一个带原因的服务端失败异常，消息按 {@link String#format(String, Object...)} 格式化。
   *
   * @param cause 原始异常
   * @param message 消息模板
   * @param args 模板参数
   */
  @FormatMethod
  public ServiceFailureException(Throwable cause, String message, Object... args) {
    super(cause, message, args);
  }
}
