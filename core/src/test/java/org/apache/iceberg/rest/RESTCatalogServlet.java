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

import static java.lang.String.format;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.iceberg.exceptions.RESTException;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.relocated.com.google.common.io.CharStreams;
import org.apache.iceberg.rest.RESTCatalogAdapter.HTTPMethod;
import org.apache.iceberg.rest.RESTCatalogAdapter.Route;
import org.apache.iceberg.rest.responses.ErrorResponse;
import org.apache.iceberg.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 测试类：RESTCatalogServlet，用于验证 REST Catalog Servlet 相关功能。
 *
 * <p>所属模块：iceberg-core（测试目录 src/test）。 职责：针对 REST Catalog Servlet 的核心行为构造多种场景，覆盖正常路径、边界条件与异常输入，
 * 确保实现与预期语义一致。
 *
 * <p>测试策略：基于 JUnit（必要时配合参数化执行器）搭建表/目录等测试基座， 通过构造输入、执行被测方法并断言结果或状态来验证功能点。
 */
public class RESTCatalogServlet extends HttpServlet {
  private static final Logger LOG = LoggerFactory.getLogger(RESTCatalogServlet.class);

  private final RESTCatalogAdapter restCatalogAdapter;
  private final Map<String, String> responseHeaders =
      ImmutableMap.of(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType());

  /** 辅助方法：rest catalog servlet。 */
  public RESTCatalogServlet(RESTCatalogAdapter restCatalogAdapter) {
    this.restCatalogAdapter = restCatalogAdapter;
  }

  /** 辅助方法：do get。 */
  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    execute(ServletRequestContext.from(request), response);
  }

  /** 辅助方法：do head。 */
  @Override
  protected void doHead(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    execute(ServletRequestContext.from(request), response);
  }

  /** 辅助方法：do post。 */
  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    execute(ServletRequestContext.from(request), response);
  }

  /** 辅助方法：do delete。 */
  @Override
  protected void doDelete(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    execute(ServletRequestContext.from(request), response);
  }

  /** 辅助方法：execute。 */
  protected void execute(ServletRequestContext context, HttpServletResponse response)
      throws IOException {
    response.setStatus(HttpServletResponse.SC_OK);
    responseHeaders.forEach(response::setHeader);

    if (context.error().isPresent()) {
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      RESTObjectMapper.mapper().writeValue(response.getWriter(), context.error().get());
      return;
    }

    try {
      Object responseBody =
          restCatalogAdapter.execute(
              context.method(),
              context.path(),
              context.queryParams(),
              context.body(),
              context.route().responseClass(),
              context.headers(),
              handle(response));

      if (responseBody != null) {
        RESTObjectMapper.mapper().writeValue(response.getWriter(), responseBody);
      }
    } catch (RESTException e) {
      LOG.error("Error processing REST request", e);
      response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    } catch (Exception e) {
      LOG.error("Unexpected exception when processing REST request", e);
      response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
  }

  /** 辅助方法：handle。 */
  protected Consumer<ErrorResponse> handle(HttpServletResponse response) {
    return (errorResponse) -> {
      response.setStatus(errorResponse.code());
      try {
        RESTObjectMapper.mapper().writeValue(response.getWriter(), errorResponse);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    };
  }

  public static class ServletRequestContext {
    private HTTPMethod method;
    private Route route;
    private String path;
    private Map<String, String> headers;
    private Map<String, String> queryParams;
    private Object body;

    private ErrorResponse errorResponse;

    /** 辅助方法：servlet request context。 */
    private ServletRequestContext(ErrorResponse errorResponse) {
      this.errorResponse = errorResponse;
    }

    /** 辅助方法：servlet request context。 */
    private ServletRequestContext(
        HTTPMethod method,
        Route route,
        String path,
        Map<String, String> headers,
        Map<String, String> queryParams,
        Object body) {
      this.method = method;
      this.route = route;
      this.path = path;
      this.headers = headers;
      this.queryParams = queryParams;
      this.body = body;
    }

    static ServletRequestContext from(HttpServletRequest request) throws IOException {
      HTTPMethod method = HTTPMethod.valueOf(request.getMethod());
      String path = request.getRequestURI().substring(1);
      Pair<Route, Map<String, String>> routeContext = Route.from(method, path);

      if (routeContext == null) {
        return new ServletRequestContext(
            ErrorResponse.builder()
                .responseCode(400)
                .withType("BadRequestException")
                .withMessage(format("No route for request: %s %s", method, path))
                .build());
      }

      Route route = routeContext.first();
      Object requestBody = null;
      if (route.requestClass() != null) {
        requestBody =
            RESTObjectMapper.mapper().readValue(request.getReader(), route.requestClass());
      } else if (route == Route.TOKENS) {
        try (Reader reader = new InputStreamReader(request.getInputStream())) {
          requestBody = RESTUtil.decodeFormData(CharStreams.toString(reader));
        }
      }

      Map<String, String> queryParams =
          request.getParameterMap().entrySet().stream()
              .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue()[0]));
      Map<String, String> headers =
          Collections.list(request.getHeaderNames()).stream()
              .collect(Collectors.toMap(Function.identity(), request::getHeader));

      return new ServletRequestContext(method, route, path, headers, queryParams, requestBody);
    }

    /** 辅助方法：method。 */
    public HTTPMethod method() {
      return method;
    }

    /** 辅助方法：route。 */
    public Route route() {
      return route;
    }

    /** 辅助方法：path。 */
    public String path() {
      return path;
    }

    /** 辅助方法：headers。 */
    public Map<String, String> headers() {
      return headers;
    }

    /** 辅助方法：query params。 */
    public Map<String, String> queryParams() {
      return queryParams;
    }

    /** 辅助方法：body。 */
    public Object body() {
      return body;
    }

    /** 辅助方法：error。 */
    public Optional<ErrorResponse> error() {
      return Optional.ofNullable(errorResponse);
    }
  }
}
