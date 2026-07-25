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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.apache.hc.client5.http.classic.methods.HttpUriRequest;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.impl.EnglishReasonPhraseCatalog;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.net.URIBuilder;
import org.apache.iceberg.IcebergBuild;
import org.apache.iceberg.common.DynConstructors;
import org.apache.iceberg.common.DynMethods;
import org.apache.iceberg.exceptions.RESTException;
import org.apache.iceberg.relocated.com.google.common.annotations.VisibleForTesting;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.rest.responses.ErrorResponse;
import org.apache.iceberg.util.PropertyUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 所属模块：iceberg-core；REST Catalog HTTP 客户端层。
 *
 * <p>职责：基于 Apache HttpClient 5 实现 {@link RESTClient}，为 REST Catalog 提供底层的 HTTP 通信 能力。具体包括：
 *
 * <ul>
 *   <li>封装 HEAD / GET / POST / DELETE / 表单 POST 等常用 HTTP 方法
 *   <li>将请求体序列化为 JSON 或表单编码，将响应体反序列化为指定类型
 *   <li>通过 {@link ErrorHandler} 处理失败响应并抛出对应的 Iceberg 异常
 *   <li>支持可选的请求拦截器（如 AWS SigV4 签名）与指数退避重试
 * </ul>
 *
 * <p>设计意图：将 HTTP 通信细节集中在本类，使上层 REST Catalog 只需关心业务语义； 使用 Builder 模式构造客户端，便于配置
 * URI、Header、ObjectMapper 与属性； 实例非线程安全但 HttpClient 本身线程安全，可被多线程并发使用。
 *
 * <p>上下游关系：实现 {@link RESTClient}；被 {@code RESTSessionCatalog} 等上层使用； 依赖 {@link ObjectMapper}、{@link
 * ErrorHandler}、{@link RESTObjectMapper} 等。
 */
public class HTTPClient implements RESTClient {

  private static final Logger LOG = LoggerFactory.getLogger(HTTPClient.class);
  // 是否启用 AWS SigV4 签名的属性键
  private static final String SIGV4_ENABLED = "rest.sigv4-enabled";
  // SigV4 请求拦截器实现类的全限定名（位于 iceberg-aws 模块）
  private static final String SIGV4_REQUEST_INTERCEPTOR_IMPL =
      "org.apache.iceberg.aws.RESTSigV4Signer";
  // 客户端版本号请求头名
  @VisibleForTesting static final String CLIENT_VERSION_HEADER = "X-Client-Version";

  // 客户端 git commit 短哈希请求头名
  @VisibleForTesting
  static final String CLIENT_GIT_COMMIT_SHORT_HEADER = "X-Client-Git-Commit-Short";

  // 最大重试次数属性键
  private static final String REST_MAX_RETRIES = "rest.client.max-retries";

  // 基础 URI
  private final String uri;
  // 实际执行请求的 HttpClient
  private final CloseableHttpClient httpClient;
  // 用于 JSON 序列化/反序列化的 ObjectMapper
  private final ObjectMapper mapper;

  /**
   * 私有构造器，由 {@link Builder} 调用。
   *
   * <p>逻辑：保存 uri 与 mapper；基于 {@link HttpClients#custom()} 构建 HttpClient，依次设置 默认
   * Header、可选的请求拦截器，以及指数退避重试策略（默认重试 5 次）。
   *
   * @param uri 基础 URI
   * @param baseHeaders 默认请求头
   * @param objectMapper JSON 序列化器
   * @param requestInterceptor 请求拦截器（可为 null）
   * @param properties 客户端属性
   */
  private HTTPClient(
      String uri,
      Map<String, String> baseHeaders,
      ObjectMapper objectMapper,
      HttpRequestInterceptor requestInterceptor,
      Map<String, String> properties) {
    this.uri = uri;
    this.mapper = objectMapper;

    HttpClientBuilder clientBuilder = HttpClients.custom();

    if (baseHeaders != null) {
      clientBuilder.setDefaultHeaders(
          baseHeaders.entrySet().stream()
              .map(e -> new BasicHeader(e.getKey(), e.getValue()))
              .collect(Collectors.toList()));
    }

    if (requestInterceptor != null) {
      clientBuilder.addRequestInterceptorLast(requestInterceptor);
    }

    int maxRetries = PropertyUtil.propertyAsInt(properties, REST_MAX_RETRIES, 5);
    clientBuilder.setRetryStrategy(new ExponentialHttpRequestRetryStrategy(maxRetries));

    this.httpClient = clientBuilder.build();
  }

  /**
   * 将 HTTP 响应体提取为字符串。
   *
   * <p>逻辑：若响应无 entity 则返回 null；否则用 UTF-8 解码为字符串。IO 或解析异常包装为 {@link RESTException}。
   *
   * @param response HTTP 响应
   * @return 响应体字符串，可能为 null
   */
  private static String extractResponseBodyAsString(CloseableHttpResponse response) {
    try {
      if (response.getEntity() == null) {
        return null;
      }

      // EntityUtils.toString returns null when HttpEntity.getContent returns null.
      return EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
    } catch (IOException | ParseException e) {
      throw new RESTException(e, "Failed to convert HTTP response body to string");
    }
  }

  /**
   * 判断响应是否为成功（按 REST 规范，成功状态码为 200、202、204）。
   *
   * @param response HTTP 响应
   * @return 是否成功
   */
  // Per the spec, the only currently defined /used "success" responses are 200 and 202.
  private static boolean isSuccessful(CloseableHttpResponse response) {
    int code = response.getCode();
    return code == HttpStatus.SC_OK
        || code == HttpStatus.SC_ACCEPTED
        || code == HttpStatus.SC_NO_CONTENT;
  }

  /**
   * 当无法从响应体解析出结构化错误时，根据 HTTP 状态码与 reason 短语构造兜底 {@link ErrorResponse}。
   *
   * @param response HTTP 响应
   * @return 兜底错误响应
   */
  private static ErrorResponse buildDefaultErrorResponse(CloseableHttpResponse response) {
    String responseReason = response.getReasonPhrase();
    String message =
        responseReason != null && !responseReason.isEmpty()
            ? responseReason
            : EnglishReasonPhraseCatalog.INSTANCE.getReason(response.getCode(), null /* ignored */);
    String type = "RESTException";
    return ErrorResponse.builder()
        .responseCode(response.getCode())
        .withMessage(message)
        .withType(type)
        .build();
  }

  /**
   * 处理失败响应：解析错误响应体并交给 errorHandler 抛出对应异常；若 errorHandler 未抛出， 则抛出 {@link RESTException} 兜底。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>若响应体非空，尝试通过 {@link ErrorHandler#parseResponse} 解析；非 ErrorHandler 实例时 仅将原始响应体塞入
   *       ErrorResponse
   *   <li>解析失败（如负载均衡器返回的非标准 5xx）时记录日志并继续，构造兜底 ErrorResponse
   *   <li>调用 errorHandler.accept 触发对应异常
   *   <li>若 handler 未抛出异常，则抛 {@link RESTException}
   * </ol>
   *
   * @param response HTTP 响应
   * @param responseBody 响应体字符串
   * @param errorHandler 错误处理器
   */
  // Process a failed response through the provided errorHandler, and throw a RESTException if the
  // provided error handler doesn't already throw.
  private static void throwFailure(
      CloseableHttpResponse response, String responseBody, Consumer<ErrorResponse> errorHandler) {
    ErrorResponse errorResponse = null;

    if (responseBody != null) {
      try {
        if (errorHandler instanceof ErrorHandler) {
          errorResponse =
              ((ErrorHandler) errorHandler).parseResponse(response.getCode(), responseBody);
        } else {
          LOG.warn(
              "Unknown error handler {}, response body won't be parsed",
              errorHandler.getClass().getName());
          errorResponse =
              ErrorResponse.builder()
                  .responseCode(response.getCode())
                  .withMessage(responseBody)
                  .build();
        }

      } catch (UncheckedIOException | IllegalArgumentException e) {
        // It's possible to receive a non-successful response that isn't a properly defined
        // ErrorResponse
        // without any bugs in the server implementation. So we ignore this exception and build an
        // error
        // response for the user.
        //
        // For example, the connection could time out before every reaching the server, in which
        // case we'll
        // likely get a 5xx with the load balancers default 5xx response.
        LOG.error("Failed to parse an error response. Will create one instead.", e);
      }
    }

    if (errorResponse == null) {
      errorResponse = buildDefaultErrorResponse(response);
    }

    errorHandler.accept(errorResponse);

    // Throw an exception in case the provided error handler does not throw.
    throw new RESTException("Unhandled error: %s", errorResponse);
  }

  /**
   * 基于基础 URI 与路径、查询参数构造完整请求 URI。
   *
   * <p>逻辑：拼接 {@code uri/path}，通过 {@link URIBuilder} 添加查询参数；语法异常包装为 {@link RESTException}。
   *
   * @param path 相对路径
   * @param params 查询参数映射
   * @return 构造完成的 URI
   */
  private URI buildUri(String path, Map<String, String> params) {
    String baseUri = String.format("%s/%s", uri, path);
    try {
      URIBuilder builder = new URIBuilder(baseUri);
      if (params != null) {
        params.forEach(builder::addParameter);
      }
      return builder.build();
    } catch (URISyntaxException e) {
      throw new RESTException(
          "Failed to create request URI from base %s, params %s", baseUri, params);
    }
  }

  /**
   * 执行 HTTP 请求并处理响应（不接收响应头）。
   *
   * <p>逻辑：委托给带 {@code responseHeaders} 参数的重载版本，传入空消费者。
   *
   * @param method HTTP 方法
   * @param path URL 路径
   * @param queryParams 查询参数
   * @param requestBody 请求体
   * @param responseType 响应类型
   * @param headers 请求头
   * @param errorHandler 错误处理器
   * @param <T> 响应类型
   * @return 解析后的响应对象
   */
  private <T> T execute(
      Method method,
      String path,
      Map<String, String> queryParams,
      Object requestBody,
      Class<T> responseType,
      Map<String, String> headers,
      Consumer<ErrorResponse> errorHandler) {
    return execute(
        method, path, queryParams, requestBody, responseType, headers, errorHandler, h -> {});
  }

  /**
   * 执行 HTTP 请求并处理响应，同时将响应头回传给调用方。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>校验路径不能以 '/' 开头
   *   <li>构造请求；若请求体为 Map 则按表单编码，否则按 JSON 序列化；无请求体时仅设置 Content-Type
   *   <li>执行请求并收集响应头
   *   <li>对于 204 或无响应类型的成功响应，直接返回 null
   *   <li>失败响应交给 {@link #throwFailure} 处理
   *   <li>成功响应用 ObjectMapper 反序列化为指定类型
   * </ol>
   *
   * @param method HTTP 方法
   * @param path URL 路径
   * @param queryParams 查询参数
   * @param requestBody 请求体
   * @param responseType 响应类型
   * @param headers 请求头
   * @param errorHandler 错误处理器
   * @param responseHeaders 响应头消费者
   * @param <T> 响应类型
   * @return 解析后的响应对象
   */
  private <T> T execute(
      Method method,
      String path,
      Map<String, String> queryParams,
      Object requestBody,
      Class<T> responseType,
      Map<String, String> headers,
      Consumer<ErrorResponse> errorHandler,
      Consumer<Map<String, String>> responseHeaders) {
    if (path.startsWith("/")) {
      throw new RESTException(
          "Received a malformed path for a REST request: %s. Paths should not start with /", path);
    }

    HttpUriRequestBase request = new HttpUriRequestBase(method.name(), buildUri(path, queryParams));

    if (requestBody instanceof Map) {
      // encode maps as form data, application/x-www-form-urlencoded
      addRequestHeaders(request, headers, ContentType.APPLICATION_FORM_URLENCODED.getMimeType());
      request.setEntity(toFormEncoding((Map<?, ?>) requestBody));
    } else if (requestBody != null) {
      // other request bodies are serialized as JSON, application/json
      addRequestHeaders(request, headers, ContentType.APPLICATION_JSON.getMimeType());
      request.setEntity(toJson(requestBody));
    } else {
      addRequestHeaders(request, headers, ContentType.APPLICATION_JSON.getMimeType());
    }

    try (CloseableHttpResponse response = httpClient.execute(request)) {
      Map<String, String> respHeaders = Maps.newHashMap();
      for (Header header : response.getHeaders()) {
        respHeaders.put(header.getName(), header.getValue());
      }

      responseHeaders.accept(respHeaders);

      // Skip parsing the response stream for any successful request not expecting a response body
      if (response.getCode() == HttpStatus.SC_NO_CONTENT
          || (responseType == null && isSuccessful(response))) {
        return null;
      }

      String responseBody = extractResponseBodyAsString(response);

      if (!isSuccessful(response)) {
        // The provided error handler is expected to throw, but a RESTException is thrown if not.
        throwFailure(response, responseBody, errorHandler);
      }

      if (responseBody == null) {
        throw new RESTException(
            "Invalid (null) response body for request (expected %s): method=%s, path=%s, status=%d",
            responseType.getSimpleName(), method.name(), path, response.getCode());
      }

      try {
        return mapper.readValue(responseBody, responseType);
      } catch (JsonProcessingException e) {
        throw new RESTException(
            e,
            "Received a success response code of %d, but failed to parse response body into %s",
            response.getCode(),
            responseType.getSimpleName());
      }
    } catch (IOException e) {
      throw new RESTException(e, "Error occurred while processing %s request", method);
    }
  }

  /**
   * 发送 HEAD 请求，仅关心响应状态码，不解析响应体。
   *
   * @param path URL 路径
   * @param headers 请求头
   * @param errorHandler 错误处理器
   */
  @Override
  public void head(String path, Map<String, String> headers, Consumer<ErrorResponse> errorHandler) {
    execute(Method.HEAD, path, null, null, null, headers, errorHandler);
  }

  /**
   * 发送 GET 请求并将响应反序列化为指定类型。
   *
   * @param path URL 路径
   * @param queryParams 查询参数
   * @param responseType 响应类型
   * @param headers 请求头
   * @param errorHandler 错误处理器
   * @param <T> 响应类型
   * @return 解析后的响应对象
   */
  @Override
  public <T extends RESTResponse> T get(
      String path,
      Map<String, String> queryParams,
      Class<T> responseType,
      Map<String, String> headers,
      Consumer<ErrorResponse> errorHandler) {
    return execute(Method.GET, path, queryParams, null, responseType, headers, errorHandler);
  }

  /**
   * 发送 POST 请求（JSON 请求体），不接收响应头。
   *
   * @param path URL 路径
   * @param body 请求体
   * @param responseType 响应类型
   * @param headers 请求头
   * @param errorHandler 错误处理器
   * @param <T> 响应类型
   * @return 解析后的响应对象
   */
  @Override
  public <T extends RESTResponse> T post(
      String path,
      RESTRequest body,
      Class<T> responseType,
      Map<String, String> headers,
      Consumer<ErrorResponse> errorHandler) {
    return execute(Method.POST, path, null, body, responseType, headers, errorHandler);
  }

  /**
   * 发送 POST 请求（JSON 请求体），并将响应头回传给调用方。
   *
   * @param path URL 路径
   * @param body 请求体
   * @param responseType 响应类型
   * @param headers 请求头
   * @param errorHandler 错误处理器
   * @param responseHeaders 响应头消费者
   * @param <T> 响应类型
   * @return 解析后的响应对象
   */
  @Override
  public <T extends RESTResponse> T post(
      String path,
      RESTRequest body,
      Class<T> responseType,
      Map<String, String> headers,
      Consumer<ErrorResponse> errorHandler,
      Consumer<Map<String, String>> responseHeaders) {
    return execute(
        Method.POST, path, null, body, responseType, headers, errorHandler, responseHeaders);
  }

  /**
   * 发送 DELETE 请求（不带查询参数）。
   *
   * @param path URL 路径
   * @param responseType 响应类型
   * @param headers 请求头
   * @param errorHandler 错误处理器
   * @param <T> 响应类型
   * @return 解析后的响应对象
   */
  @Override
  public <T extends RESTResponse> T delete(
      String path,
      Class<T> responseType,
      Map<String, String> headers,
      Consumer<ErrorResponse> errorHandler) {
    return execute(Method.DELETE, path, null, null, responseType, headers, errorHandler);
  }

  /**
   * 发送 DELETE 请求，允许携带查询参数。
   *
   * @param path URL 路径
   * @param queryParams 查询参数
   * @param responseType 响应类型
   * @param headers 请求头
   * @param errorHandler 错误处理器
   * @param <T> 响应类型
   * @return 解析后的响应对象
   */
  @Override
  public <T extends RESTResponse> T delete(
      String path,
      Map<String, String> queryParams,
      Class<T> responseType,
      Map<String, String> headers,
      Consumer<ErrorResponse> errorHandler) {
    return execute(Method.DELETE, path, queryParams, null, responseType, headers, errorHandler);
  }

  /**
   * 以表单编码（application/x-www-form-urlencoded）发送 POST 请求。
   *
   * @param path URL 路径
   * @param formData 表单数据
   * @param responseType 响应类型
   * @param headers 请求头
   * @param errorHandler 错误处理器
   * @param <T> 响应类型
   * @return 解析后的响应对象
   */
  @Override
  public <T extends RESTResponse> T postForm(
      String path,
      Map<String, String> formData,
      Class<T> responseType,
      Map<String, String> headers,
      Consumer<ErrorResponse> errorHandler) {
    return execute(Method.POST, path, null, formData, responseType, headers, errorHandler);
  }

  /**
   * 为请求添加 Accept、Content-Type 头以及自定义请求头。
   *
   * <p>设计要点：即使无请求体也设置 Content-Type，避免部分服务在空请求时校验失败。
   *
   * @param request HTTP 请求
   * @param requestHeaders 自定义请求头
   * @param bodyMimeType 请求体的 MIME 类型
   */
  private void addRequestHeaders(
      HttpUriRequest request, Map<String, String> requestHeaders, String bodyMimeType) {
    request.setHeader(HttpHeaders.ACCEPT, ContentType.APPLICATION_JSON.getMimeType());
    // Many systems require that content type is set regardless and will fail, even on an empty
    // bodied request.
    request.setHeader(HttpHeaders.CONTENT_TYPE, bodyMimeType);
    requestHeaders.forEach(request::setHeader);
  }

  /**
   * 优雅关闭底层 HttpClient。
   *
   * @throws IOException 关闭过程中发生 IO 异常
   */
  @Override
  public void close() throws IOException {
    httpClient.close(CloseMode.GRACEFUL);
  }

  /**
   * 通过反射动态加载并初始化 {@link HttpRequestInterceptor} 实例（如 SigV4 签名器）。
   *
   * <p>逻辑：用 {@link DynConstructors} 查找无参构造器创建实例；用 {@link DynMethods} 调用其 {@code initialize(Map)}
   * 方法完成初始化；缺失构造器或类型不匹配时抛出 {@link IllegalArgumentException}。
   *
   * @param impl 拦截器实现类的全限定名
   * @param properties 初始化属性
   * @return 拦截器实例
   */
  @VisibleForTesting
  static HttpRequestInterceptor loadInterceptorDynamically(
      String impl, Map<String, String> properties) {
    HttpRequestInterceptor instance;

    DynConstructors.Ctor<HttpRequestInterceptor> ctor;
    try {
      ctor =
          DynConstructors.builder(HttpRequestInterceptor.class)
              .loader(HTTPClient.class.getClassLoader())
              .impl(impl)
              .buildChecked();
    } catch (NoSuchMethodException e) {
      throw new IllegalArgumentException(
          String.format(
              "Cannot initialize RequestInterceptor, missing no-arg constructor: %s", impl),
          e);
    }

    try {
      instance = ctor.newInstance();
    } catch (ClassCastException e) {
      throw new IllegalArgumentException(
          String.format("Cannot initialize, %s does not implement RequestInterceptor", impl), e);
    }

    DynMethods.builder("initialize")
        .hiddenImpl(impl, Map.class)
        .orNoop()
        .build(instance)
        .invoke(properties);

    return instance;
  }

  /**
   * 创建 {@link Builder} 实例，传入客户端属性。
   *
   * @param properties 客户端属性
   * @return Builder 实例
   */
  public static Builder builder(Map<String, String> properties) {
    return new Builder(properties);
  }

  /**
   * {@link HTTPClient} 的构建器，采用链式 API 配置 URI、Header、ObjectMapper 等并最终构建客户端。
   *
   * <p>设计意图：将复杂的客户端配置与构造分离，避免构造器参数过多；构建时自动注入客户端版本与 git commit 头，便于服务端识别客户端。
   */
  public static class Builder {
    private final Map<String, String> properties;
    private final Map<String, String> baseHeaders = Maps.newHashMap();
    private String uri;
    private ObjectMapper mapper = RESTObjectMapper.mapper();

    /**
     * 构造构建器，传入客户端属性。
     *
     * @param properties 客户端属性
     */
    private Builder(Map<String, String> properties) {
      this.properties = properties;
    }

    /**
     * 设置基础 URI，会去除末尾的 '/'。
     *
     * @param baseUri 基础 URI
     * @return 当前 Builder
     */
    public Builder uri(String baseUri) {
      Preconditions.checkNotNull(baseUri, "Invalid uri for http client: null");
      this.uri = RESTUtil.stripTrailingSlash(baseUri);
      return this;
    }

    /**
     * 添加单个请求头。
     *
     * @param key 头名
     * @param value 头值
     * @return 当前 Builder
     */
    public Builder withHeader(String key, String value) {
      baseHeaders.put(key, value);
      return this;
    }

    /**
     * 批量添加请求头。
     *
     * @param headers 请求头映射
     * @return 当前 Builder
     */
    public Builder withHeaders(Map<String, String> headers) {
      baseHeaders.putAll(headers);
      return this;
    }

    /**
     * 设置自定义的 {@link ObjectMapper}，默认使用 {@link RESTObjectMapper#mapper()}。
     *
     * @param objectMapper 自定义 ObjectMapper
     * @return 当前 Builder
     */
    public Builder withObjectMapper(ObjectMapper objectMapper) {
      this.mapper = objectMapper;
      return this;
    }

    /**
     * 构建 {@link HTTPClient} 实例。
     *
     * <p>逻辑：注入客户端版本与 git commit 头；若启用 SigV4 则动态加载签名拦截器； 最终用收集到的配置创建 HTTPClient。
     *
     * @return 新建的 HTTPClient 实例
     */
    public HTTPClient build() {
      withHeader(CLIENT_VERSION_HEADER, IcebergBuild.fullVersion());
      withHeader(CLIENT_GIT_COMMIT_SHORT_HEADER, IcebergBuild.gitCommitShortId());

      HttpRequestInterceptor interceptor = null;

      if (PropertyUtil.propertyAsBoolean(properties, SIGV4_ENABLED, false)) {
        interceptor = loadInterceptorDynamically(SIGV4_REQUEST_INTERCEPTOR_IMPL, properties);
      }

      return new HTTPClient(uri, baseHeaders, mapper, interceptor, properties);
    }
  }

  /**
   * 将请求体序列化为 JSON 的 {@link StringEntity}。
   *
   * @param requestBody 请求体对象
   * @return 包含 JSON 字符串的 StringEntity
   */
  private StringEntity toJson(Object requestBody) {
    try {
      return new StringEntity(mapper.writeValueAsString(requestBody), StandardCharsets.UTF_8);
    } catch (JsonProcessingException e) {
      throw new RESTException(e, "Failed to write request body: %s", requestBody);
    }
  }

  /**
   * 将表单数据编码为 {@link StringEntity}。
   *
   * @param formData 表单数据
   * @return 包含表单编码字符串的 StringEntity
   */
  private StringEntity toFormEncoding(Map<?, ?> formData) {
    return new StringEntity(RESTUtil.encodeFormData(formData), StandardCharsets.UTF_8);
  }
}
