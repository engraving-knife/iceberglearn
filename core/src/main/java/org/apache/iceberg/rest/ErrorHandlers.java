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
import org.apache.iceberg.exceptions.AlreadyExistsException;
import org.apache.iceberg.exceptions.BadRequestException;
import org.apache.iceberg.exceptions.CommitFailedException;
import org.apache.iceberg.exceptions.CommitStateUnknownException;
import org.apache.iceberg.exceptions.ForbiddenException;
import org.apache.iceberg.exceptions.NoSuchNamespaceException;
import org.apache.iceberg.exceptions.NoSuchTableException;
import org.apache.iceberg.exceptions.NotAuthorizedException;
import org.apache.iceberg.exceptions.RESTException;
import org.apache.iceberg.exceptions.ServiceFailureException;
import org.apache.iceberg.exceptions.ServiceUnavailableException;
import org.apache.iceberg.rest.auth.OAuth2Properties;
import org.apache.iceberg.rest.responses.ErrorResponse;
import org.apache.iceberg.rest.responses.ErrorResponseParser;
import org.apache.iceberg.rest.responses.OAuthErrorResponseParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 所属模块：iceberg-core；REST Catalog 错误处理层。
 *
 * <p>职责：为不同类型的 REST 请求（表、命名空间、表提交、OAuth 等）提供对应的错误响应处理器， 将 {@link ErrorResponse} 转换为合适的 Iceberg
 * 运行时异常。具体包括：
 *
 * <ul>
 *   <li>对外暴露多个静态工厂方法，返回特定场景的 {@link Consumer}
 *   <li>内部以单例方式持有各 {@link ErrorHandler} 子类实例
 *   <li>每个子类按 HTTP 状态码（或 OAuth 错误类型）映射到对应异常
 * </ul>
 *
 * <p>设计意图：通过继承 {@link ErrorHandler} 形成责任链——特定处理器先处理自身关心的状态码， 其余交给父类 {@link DefaultErrorHandler}
 * 处理通用错误；单例模式避免重复创建。
 *
 * <p>上下游关系：依赖 {@link ErrorResponse}、{@link ErrorResponseParser} 等；被 {@code RESTClient} （如 {@link
 * HTTPClient}）在请求失败时调用。
 */
public class ErrorHandlers {

  private static final Logger LOG = LoggerFactory.getLogger(ErrorHandlers.class);

  /** 工具类，禁止实例化。 */
  private ErrorHandlers() {}

  /**
   * 返回命名空间相关操作的错误处理器。
   *
   * @return 命名空间错误处理器单例
   */
  public static Consumer<ErrorResponse> namespaceErrorHandler() {
    return NamespaceErrorHandler.INSTANCE;
  }

  /**
   * 返回表级别操作的错误处理器。
   *
   * @return 表错误处理器单例
   */
  public static Consumer<ErrorResponse> tableErrorHandler() {
    return TableErrorHandler.INSTANCE;
  }

  /**
   * 返回表提交（commit）操作的错误处理器。
   *
   * @return 表提交错误处理器单例
   */
  public static Consumer<ErrorResponse> tableCommitHandler() {
    return CommitErrorHandler.INSTANCE;
  }

  /**
   * 返回默认错误处理器，处理所有响应通用的错误状态码。
   *
   * @return 默认错误处理器单例
   */
  public static Consumer<ErrorResponse> defaultErrorHandler() {
    return DefaultErrorHandler.INSTANCE;
  }

  /**
   * 返回 OAuth 相关请求的错误处理器。
   *
   * @return OAuth 错误处理器单例
   */
  public static Consumer<ErrorResponse> oauthErrorHandler() {
    return OAuthErrorHandler.INSTANCE;
  }

  /**
   * 表提交错误处理器，将表提交特有的状态码映射为对应异常。
   *
   * <p>设计意图：表提交失败需要区分"提交冲突"与"服务端失败导致提交状态未知"，因此单独处理 409 / 5xx 等场景，其余交给父类。
   */
  private static class CommitErrorHandler extends DefaultErrorHandler {
    private static final ErrorHandler INSTANCE = new CommitErrorHandler();

    /**
     * 处理表提交的错误响应。
     *
     * <p>逻辑：404 抛 {@link NoSuchTableException}；409 抛 {@link CommitFailedException}； 500/502/504 抛
     * {@link CommitStateUnknownException}（包装 {@link ServiceFailureException}）； 其余状态码交由父类处理。
     *
     * @param error 错误响应
     */
    @Override
    public void accept(ErrorResponse error) {
      switch (error.code()) {
        case 404:
          throw new NoSuchTableException("%s", error.message());
        case 409:
          throw new CommitFailedException("Commit failed: %s", error.message());
        case 500:
        case 502:
        case 504:
          throw new CommitStateUnknownException(
              new ServiceFailureException("Service failed: %s: %s", error.code(), error.message()));
      }

      super.accept(error);
    }
  }

  /**
   * 表级别错误处理器，针对表的 CRUD 操作做异常映射。
   *
   * <p>设计意图：404 时需区分"命名空间不存在"与"表不存在"两种语义，故根据 error.type() 进一步判断。
   */
  private static class TableErrorHandler extends DefaultErrorHandler {
    private static final ErrorHandler INSTANCE = new TableErrorHandler();

    /**
     * 处理表级别错误响应。
     *
     * <p>逻辑：404 根据 type 区分抛 {@link NoSuchNamespaceException} 或 {@link NoSuchTableException}； 409 抛
     * {@link AlreadyExistsException}；其余交由父类处理。
     *
     * @param error 错误响应
     */
    @Override
    public void accept(ErrorResponse error) {
      switch (error.code()) {
        case 404:
          if (NoSuchNamespaceException.class.getSimpleName().equals(error.type())) {
            throw new NoSuchNamespaceException("%s", error.message());
          } else {
            throw new NoSuchTableException("%s", error.message());
          }
        case 409:
          throw new AlreadyExistsException("%s", error.message());
      }

      super.accept(error);
    }
  }

  /**
   * 命名空间 CRUD 操作专用的错误处理器。
   *
   * <p>设计意图：命名空间场景下 404 仅表示命名空间不存在，409 表示已存在，422 表示处理失败。
   */
  private static class NamespaceErrorHandler extends DefaultErrorHandler {
    private static final ErrorHandler INSTANCE = new NamespaceErrorHandler();

    /**
     * 处理命名空间错误响应。
     *
     * <p>逻辑：404 抛 {@link NoSuchNamespaceException}；409 抛 {@link AlreadyExistsException}； 422 抛
     * {@link RESTException}；其余交由父类处理。
     *
     * @param error 错误响应
     */
    @Override
    public void accept(ErrorResponse error) {
      switch (error.code()) {
        case 404:
          throw new NoSuchNamespaceException("%s", error.message());
        case 409:
          throw new AlreadyExistsException("%s", error.message());
        case 422:
          throw new RESTException("Unable to process: %s", error.message());
      }

      super.accept(error);
    }
  }

  /**
   * 默认错误处理器，处理所有响应通用的错误状态码（如 400、401、403、500 等）。
   *
   * <p>设计意图：作为各专项处理器的父类，集中处理公共错误状态码，避免重复代码； 同时提供默认的 {@link ErrorResponse} 解析实现。
   */
  private static class DefaultErrorHandler extends ErrorHandler {
    private static final ErrorHandler INSTANCE = new DefaultErrorHandler();

    /**
     * 将 HTTP 状态码与响应体 JSON 解析为 {@link ErrorResponse}。
     *
     * <p>逻辑：优先用 {@link ErrorResponseParser} 解析；若解析失败则记录警告并构造一个仅含 状态码与原始 JSON 的兜底 {@link
     * ErrorResponse}。
     *
     * @param code HTTP 状态码
     * @param json 响应体 JSON 字符串
     * @return 解析得到的 ErrorResponse
     */
    @Override
    public ErrorResponse parseResponse(int code, String json) {
      try {
        return ErrorResponseParser.fromJson(json);
      } catch (Exception x) {
        LOG.warn("Unable to parse error response", x);
      }
      return ErrorResponse.builder().responseCode(code).withMessage(json).build();
    }

    /**
     * 根据错误响应的状态码抛出对应的 Iceberg 异常。
     *
     * <p>逻辑：400→{@link BadRequestException}；401→{@link NotAuthorizedException}； 403→{@link
     * ForbiddenException}；405/406 静默忽略；500→{@link ServiceFailureException}； 501→{@link
     * UnsupportedOperationException}；503→{@link ServiceUnavailableException}； 其余状态码抛 {@link
     * RESTException}。
     *
     * @param error 错误响应
     */
    @Override
    public void accept(ErrorResponse error) {
      switch (error.code()) {
        case 400:
          throw new BadRequestException("Malformed request: %s", error.message());
        case 401:
          throw new NotAuthorizedException("Not authorized: %s", error.message());
        case 403:
          throw new ForbiddenException("Forbidden: %s", error.message());
        case 405:
        case 406:
          break;
        case 500:
          throw new ServiceFailureException("Server error: %s: %s", error.type(), error.message());
        case 501:
          throw new UnsupportedOperationException(error.message());
        case 503:
          throw new ServiceUnavailableException("Service unavailable: %s", error.message());
      }

      throw new RESTException("Unable to process: %s", error.message());
    }
  }

  /**
   * OAuth 请求的错误处理器，按 OAuth2 错误类型（type）映射到对应异常。
   *
   * <p>设计意图：OAuth2 错误响应不使用 HTTP 状态码区分语义，而是通过 type 字段标识错误类型， 因此该处理器使用 {@link
   * OAuthErrorResponseParser} 解析并按 type 分发异常。
   */
  private static class OAuthErrorHandler extends ErrorHandler {
    private static final ErrorHandler INSTANCE = new OAuthErrorHandler();

    /**
     * 将 OAuth 错误响应解析为 {@link ErrorResponse}。
     *
     * <p>逻辑：使用 {@link OAuthErrorResponseParser} 解析；若解析失败，构造兜底响应。
     *
     * @param code HTTP 状态码
     * @param json 响应体 JSON 字符串
     * @return 解析得到的 ErrorResponse
     */
    @Override
    public ErrorResponse parseResponse(int code, String json) {
      try {
        return OAuthErrorResponseParser.fromJson(code, json);
      } catch (Exception x) {
        LOG.warn("Unable to parse error response", x);
      }
      return ErrorResponse.builder().responseCode(code).withMessage(json).build();
    }

    /**
     * 根据 OAuth 错误类型抛出对应异常。
     *
     * <p>逻辑：若 type 为 {@link OAuth2Properties#INVALID_CLIENT_ERROR} 抛 {@link
     * NotAuthorizedException}；其余 OAuth 错误类型（INVALID_REQUEST、INVALID_GRANT 等） 抛 {@link
     * BadRequestException}；type 为空或未知时抛 {@link RESTException}。
     *
     * @param error 错误响应
     */
    @Override
    public void accept(ErrorResponse error) {
      if (error.type() != null) {
        switch (error.type()) {
          case OAuth2Properties.INVALID_CLIENT_ERROR:
            throw new NotAuthorizedException(
                "Not authorized: %s: %s", error.type(), error.message());
          case OAuth2Properties.INVALID_REQUEST_ERROR:
          case OAuth2Properties.INVALID_GRANT_ERROR:
          case OAuth2Properties.UNAUTHORIZED_CLIENT_ERROR:
          case OAuth2Properties.UNSUPPORTED_GRANT_TYPE_ERROR:
          case OAuth2Properties.INVALID_SCOPE_ERROR:
            throw new BadRequestException(
                "Malformed request: %s: %s", error.type(), error.message());
        }
      }
      throw new RESTException("Unable to process: %s", error.message());
    }
  }
}
