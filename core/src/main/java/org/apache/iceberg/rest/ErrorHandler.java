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
package org.apache.iceberg.rest;

import java.util.function.Consumer;
import org.apache.iceberg.rest.responses.ErrorResponse;

/**
 * 文件级说明：REST 错误响应处理器抽象基类。
 *
 * <p>所属模块：iceberg-core（REST Catalog 客户端/服务端共用的错误处理抽象）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>实现 {@link Consumer}，将 REST 错误响应转换为 Iceberg 对应的运行时异常。
 *   <li>定义 {@link #parseResponse(int, String)} 抽象方法，由子类决定如何从 HTTP 状态码与 响应体 JSON 解析出 {@link
 *       ErrorResponse}。
 * </ul>
 *
 * <p>设计意图：将“错误响应解析”与“异常抛出”解耦为两个阶段——先解析为结构化的 {@link ErrorResponse}，再由 {@link #accept(ErrorResponse)}
 * 根据状态码/类型映射到具体异常， 便于不同 REST 端点复用并按需定制异常映射。
 *
 * <p>上下游关系：被 {@link ErrorHandlers} 中各子类继承；由 {@link RESTClient} 在请求失败时调用。
 */
public abstract class ErrorHandler implements Consumer<ErrorResponse> {

  /**
   * 将 HTTP 状态码与响应体 JSON 解析为结构化的 {@link ErrorResponse}。
   *
   * @param code HTTP 状态码
   * @param json 响应体 JSON 字符串
   * @return 解析后的错误响应对象
   */
  public abstract ErrorResponse parseResponse(int code, String json);
}
