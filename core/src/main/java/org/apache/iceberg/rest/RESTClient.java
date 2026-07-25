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

import java.io.Closeable;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.rest.responses.ErrorResponse;

/**
 * 文件级说明：REST Catalog HTTP 客户端接口。
 *
 * <p>所属模块：iceberg-core（REST Catalog 传输层抽象）。
 *
 * <p>职责：定义与 REST Catalog 服务端交互的 HTTP 方法——HEAD/GET/POST/DELETE/postForm， 支持泛型响应类型与错误处理回调。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>接口抽象使上层（{@link RESTSessionCatalog}）与具体 HTTP 实现（如 {@link HTTPClient}）解耦。
 *   <li>提供 {@code Supplier<Map>} 和 Map 两种 header 传参重载——Supplier 版本支持动态获取认证头 （如令牌刷新后自动更新），default
 *       方法委托给 Map 版本。
 *   <li>继承 {@link Closeable} 以支持 try-with-resources。
 * </ul>
 *
 * <p>上下游关系：被 {@link HTTPClient} 实现；被 {@link RESTSessionCatalog}、 {@link RESTMetricsReporter}、{@link
 * org.apache.iceberg.rest.auth.OAuth2Util} 调用。
 */
public interface RESTClient extends Closeable {

  default void head(
      String path, Supplier<Map<String, String>> headers, Consumer<ErrorResponse> errorHandler) {
    head(path, headers.get(), errorHandler);
  }

  void head(String path, Map<String, String> headers, Consumer<ErrorResponse> errorHandler);

  default <T extends RESTResponse> T delete(
      String path,
      Map<String, String> queryParams,
      Class<T> responseType,
      Supplier<Map<String, String>> headers,
      Consumer<ErrorResponse> errorHandler) {
    return delete(path, queryParams, responseType, headers.get(), errorHandler);
  }

  default <T extends RESTResponse> T delete(
      String path,
      Class<T> responseType,
      Supplier<Map<String, String>> headers,
      Consumer<ErrorResponse> errorHandler) {
    return delete(path, ImmutableMap.of(), responseType, headers.get(), errorHandler);
  }

  <T extends RESTResponse> T delete(
      String path,
      Class<T> responseType,
      Map<String, String> headers,
      Consumer<ErrorResponse> errorHandler);

  default <T extends RESTResponse> T delete(
      String path,
      Map<String, String> queryParams,
      Class<T> responseType,
      Map<String, String> headers,
      Consumer<ErrorResponse> errorHandler) {
    if (null != queryParams && !queryParams.isEmpty()) {
      throw new UnsupportedOperationException("Query params are not supported");
    }

    return delete(path, responseType, headers, errorHandler);
  }

  default <T extends RESTResponse> T get(
      String path,
      Class<T> responseType,
      Supplier<Map<String, String>> headers,
      Consumer<ErrorResponse> errorHandler) {
    return get(path, ImmutableMap.of(), responseType, headers, errorHandler);
  }

  default <T extends RESTResponse> T get(
      String path,
      Class<T> responseType,
      Map<String, String> headers,
      Consumer<ErrorResponse> errorHandler) {
    return get(path, ImmutableMap.of(), responseType, headers, errorHandler);
  }

  default <T extends RESTResponse> T get(
      String path,
      Map<String, String> queryParams,
      Class<T> responseType,
      Supplier<Map<String, String>> headers,
      Consumer<ErrorResponse> errorHandler) {
    return get(path, queryParams, responseType, headers.get(), errorHandler);
  }

  <T extends RESTResponse> T get(
      String path,
      Map<String, String> queryParams,
      Class<T> responseType,
      Map<String, String> headers,
      Consumer<ErrorResponse> errorHandler);

  default <T extends RESTResponse> T post(
      String path,
      RESTRequest body,
      Class<T> responseType,
      Supplier<Map<String, String>> headers,
      Consumer<ErrorResponse> errorHandler) {
    return post(path, body, responseType, headers.get(), errorHandler);
  }

  default <T extends RESTResponse> T post(
      String path,
      RESTRequest body,
      Class<T> responseType,
      Supplier<Map<String, String>> headers,
      Consumer<ErrorResponse> errorHandler,
      Consumer<Map<String, String>> responseHeaders) {
    return post(path, body, responseType, headers.get(), errorHandler, responseHeaders);
  }

  default <T extends RESTResponse> T post(
      String path,
      RESTRequest body,
      Class<T> responseType,
      Map<String, String> headers,
      Consumer<ErrorResponse> errorHandler,
      Consumer<Map<String, String>> responseHeaders) {
    if (null != responseHeaders) {
      throw new UnsupportedOperationException("Returning response headers is not supported");
    }

    return post(path, body, responseType, headers, errorHandler);
  }

  <T extends RESTResponse> T post(
      String path,
      RESTRequest body,
      Class<T> responseType,
      Map<String, String> headers,
      Consumer<ErrorResponse> errorHandler);

  default <T extends RESTResponse> T postForm(
      String path,
      Map<String, String> formData,
      Class<T> responseType,
      Supplier<Map<String, String>> headers,
      Consumer<ErrorResponse> errorHandler) {
    return postForm(path, formData, responseType, headers.get(), errorHandler);
  }

  <T extends RESTResponse> T postForm(
      String path,
      Map<String, String> formData,
      Class<T> responseType,
      Map<String, String> headers,
      Consumer<ErrorResponse> errorHandler);
}
