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
 * 不可处理实体异常：请求格式合法但语义上无法应用时抛出。
 *
 * <p>所属模块：iceberg-api（定义核心公共 API 与异常契约的最底层模块）。
 *
 * <p>触发场景：REST Catalog 客户端发送的请求本身格式正确，但其内容与服务端状态冲突而无法执行。 例如：在同一次属性更新请求中同时要求"设置某属性"和"删除该属性"。
 *
 * <p>设计意图：继承 {@link RESTException}，归入 REST 客户端异常体系；构造器接受 {@code String.format} 风格模板，配合 {@link
 * FormatMethod} 做编译期格式化校验。
 *
 * <p>上下游关系：由 REST 客户端在收到 422 类响应或检测到语义冲突时抛出；调用方据以修正请求语义。
 */
public class UnprocessableEntityException extends RESTException {
  /**
   * 构造一个不可处理实体异常，消息按 {@link String#format(String, Object...)} 格式化。
   *
   * @param message 消息模板
   * @param args 模板参数
   */
  @FormatMethod
  public UnprocessableEntityException(String message, Object... args) {
    super(message, args);
  }
}
