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
package org.apache.iceberg.aws.s3.signer;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalListener;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.apache.iceberg.CatalogProperties;
import org.apache.iceberg.relocated.com.google.common.annotations.VisibleForTesting;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.rest.ErrorHandlers;
import org.apache.iceberg.rest.HTTPClient;
import org.apache.iceberg.rest.RESTClient;
import org.apache.iceberg.rest.auth.OAuth2Properties;
import org.apache.iceberg.rest.auth.OAuth2Util;
import org.apache.iceberg.rest.auth.OAuth2Util.AuthSession;
import org.apache.iceberg.rest.responses.OAuthTokenResponse;
import org.apache.iceberg.util.PropertyUtil;
import org.apache.iceberg.util.ThreadPools;
import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.signer.internal.AbstractAws4Signer;
import software.amazon.awssdk.auth.signer.internal.Aws4SignerRequestParams;
import software.amazon.awssdk.auth.signer.params.Aws4PresignerParams;
import software.amazon.awssdk.auth.signer.params.AwsS3V4SignerParams;
import software.amazon.awssdk.core.checksums.SdkChecksum;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.utils.IoUtils;

/**
 * 通过 REST 远程服务进行 S3 SigV4 签名的签名器实现。
 *
 * <p>所属模块：iceberg-aws（Iceberg 与 AWS 服务集成模块，处于引擎层之下）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>继承 AWS SDK 的 {@link AbstractAws4Signer}，将签名计算委托给远程 REST 签名服务， 而非在本地使用 AWS 凭证计算签名。
 *   <li>将 S3 请求（method、region、uri、headers、body）封装为 {@link S3SignRequest}， 通过 HTTP POST
 *       发送给签名服务，获取签名后的 URI 和 headers。
 *   <li>支持签名结果缓存（30 秒 TTL），减少对签名服务的请求量。
 *   <li>支持 OAuth2 令牌认证和令牌自动刷新，用于与签名服务的身份验证。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>远程签名场景：当 Iceberg 客户端无法直接访问 AWS 凭证（如通过中间签名服务代理） 时，使用此签名器将签名请求转发给持有凭证的远程服务。
 *   <li>签名缓存：相同请求（method+region+uri）的签名结果缓存 30 秒，由服务端通过 Cache-Control: private 控制是否可缓存，减少网络往返。
 *   <li>不支持预签名（presign）和 payload 签名、分块编码，仅支持标准请求签名。
 *   <li>使用 Immutables 生成不可变实现，静态字段（httpClient、authSessionCache 等） 使用 volatile + DCL 延迟初始化。
 * </ul>
 *
 * <p>上下游关系：由 {@link S3FileIOProperties#applySignerConfiguration} 创建并注入到 S3Client 的签名器配置中；通过 {@link
 * #create(Map)} 工厂方法实例化。
 */
@Value.Immutable
public abstract class S3V4RestSignerClient
    extends AbstractAws4Signer<AwsS3V4SignerParams, Aws4PresignerParams> {

  private static final Logger LOG = LoggerFactory.getLogger(S3V4RestSignerClient.class);
  public static final String S3_SIGNER_URI = "s3.signer.uri";
  public static final String S3_SIGNER_ENDPOINT = "s3.signer.endpoint";
  static final String S3_SIGNER_DEFAULT_ENDPOINT = "v1/aws/s3/sign";
  static final String UNSIGNED_PAYLOAD = "UNSIGNED-PAYLOAD";
  static final String CACHE_CONTROL = "Cache-Control";
  static final String CACHE_CONTROL_PRIVATE = "private";
  static final String CACHE_CONTROL_NO_CACHE = "no-cache";

  private static final Cache<Key, SignedComponent> SIGNED_COMPONENT_CACHE =
      Caffeine.newBuilder().expireAfterWrite(30, TimeUnit.SECONDS).maximumSize(100).build();

  private static final String SCOPE = "sign";

  @SuppressWarnings("immutables:incompat")
  private static volatile ScheduledExecutorService tokenRefreshExecutor;

  @SuppressWarnings("immutables:incompat")
  private static volatile RESTClient httpClient;

  @SuppressWarnings("immutables:incompat")
  private static volatile Cache<String, AuthSession> authSessionCache;

  public abstract Map<String, String> properties();

  @Value.Default
  public Supplier<Map<String, String>> requestPropertiesSupplier() {
    return Collections::emptyMap;
  }

  @Value.Lazy
  public String baseSignerUri() {
    return properties().getOrDefault(S3_SIGNER_URI, properties().get(CatalogProperties.URI));
  }

  @Value.Lazy
  public String endpoint() {
    return properties().getOrDefault(S3_SIGNER_ENDPOINT, S3_SIGNER_DEFAULT_ENDPOINT);
  }

  /** OAuth2 客户端凭证，用于通过 client credentials flow 换取访问令牌。 */
  @Nullable
  @Value.Lazy
  public String credential() {
    return properties().get(OAuth2Properties.CREDENTIAL);
  }

  /** 与签名服务交互时使用的 Bearer 令牌提供者。 */
  @Value.Default
  public Supplier<String> token() {
    return () -> properties().get(OAuth2Properties.TOKEN);
  }

  @Value.Lazy
  boolean keepTokenRefreshed() {
    return PropertyUtil.propertyAsBoolean(
        properties(),
        OAuth2Properties.TOKEN_REFRESH_ENABLED,
        OAuth2Properties.TOKEN_REFRESH_ENABLED_DEFAULT);
  }

  @VisibleForTesting
  ScheduledExecutorService tokenRefreshExecutor() {
    if (!keepTokenRefreshed()) {
      return null;
    }

    if (null == tokenRefreshExecutor) {
      synchronized (S3V4RestSignerClient.class) {
        if (null == tokenRefreshExecutor) {
          tokenRefreshExecutor = ThreadPools.newScheduledPool("s3-signer-token-refresh", 1);
        }
      }
    }

    return tokenRefreshExecutor;
  }

  private Cache<String, AuthSession> authSessionCache() {
    if (null == authSessionCache) {
      synchronized (S3V4RestSignerClient.class) {
        if (null == authSessionCache) {
          long expirationIntervalMs =
              PropertyUtil.propertyAsLong(
                  properties(),
                  CatalogProperties.AUTH_SESSION_TIMEOUT_MS,
                  CatalogProperties.AUTH_SESSION_TIMEOUT_MS_DEFAULT);

          authSessionCache =
              Caffeine.newBuilder()
                  .expireAfterAccess(Duration.ofMillis(expirationIntervalMs))
                  .removalListener(
                      (RemovalListener<String, AuthSession>)
                          (id, auth, cause) -> {
                            if (null != auth) {
                              LOG.trace("Stopping refresh for AuthSession");
                              auth.stopRefreshing();
                            }
                          })
                  .build();
        }
      }
    }

    return authSessionCache;
  }

  private RESTClient httpClient() {
    if (null == httpClient) {
      synchronized (S3V4RestSignerClient.class) {
        if (null == httpClient) {
          httpClient =
              HTTPClient.builder(properties())
                  .uri(baseSignerUri())
                  .withObjectMapper(S3ObjectMapper.mapper())
                  .build();
        }
      }
    }

    return httpClient;
  }

  /**
   * 获取或创建认证会话。
   *
   * <p>逻辑：
   *
   * <ul>
   *   <li>若提供了 token，从 authSessionCache 获取或创建基于访问令牌的会话（支持自动刷新）。
   *   <li>否则若提供了 credential，通过 client credentials flow 换取令牌并创建会话。
   *   <li>都没有则返回空会话。
   * </ul>
   *
   * @return 认证会话
   */
  private AuthSession authSession() {
    String token = token().get();
    if (null != token) {
      return authSessionCache()
          .get(
              token,
              id ->
                  AuthSession.fromAccessToken(
                      httpClient(),
                      tokenRefreshExecutor(),
                      token,
                      expiresAtMillis(properties()),
                      new AuthSession(ImmutableMap.of(), token, null, credential(), SCOPE)));
    }

    if (credentialProvided()) {
      return authSessionCache()
          .get(
              credential(),
              id -> {
                AuthSession session =
                    new AuthSession(ImmutableMap.of(), null, null, credential(), SCOPE);
                long startTimeMillis = System.currentTimeMillis();
                OAuthTokenResponse authResponse =
                    OAuth2Util.fetchToken(httpClient(), session.headers(), credential(), SCOPE);
                return AuthSession.fromTokenResponse(
                    httpClient(), tokenRefreshExecutor(), authResponse, startTimeMillis, session);
              });
    }

    return AuthSession.empty();
  }

  private boolean credentialProvided() {
    return null != credential() && !credential().isEmpty();
  }

  private Long expiresAtMillis(Map<String, String> properties) {
    if (properties.containsKey(OAuth2Properties.TOKEN_EXPIRES_IN_MS)) {
      long expiresInMillis =
          PropertyUtil.propertyAsLong(
              properties,
              OAuth2Properties.TOKEN_EXPIRES_IN_MS,
              OAuth2Properties.TOKEN_EXPIRES_IN_MS_DEFAULT);
      return System.currentTimeMillis() + expiresInMillis;
    } else {
      return null;
    }
  }

  /** 校验签名服务 URI 已配置（s3.signer.uri 或 catalog URI）。 */
  @Value.Check
  protected void check() {
    Preconditions.checkArgument(
        properties().containsKey(S3_SIGNER_URI) || properties().containsKey(CatalogProperties.URI),
        "S3 signer service URI is required");
  }

  @Override
  protected void processRequestPayload(
      SdkHttpFullRequest.Builder mutableRequest,
      byte[] signature,
      byte[] signingKey,
      Aws4SignerRequestParams signerRequestParams,
      AwsS3V4SignerParams signerParams) {
    checkSignerParams(signerParams);
  }

  @Override
  protected void processRequestPayload(
      SdkHttpFullRequest.Builder mutableRequest,
      byte[] signature,
      byte[] signingKey,
      Aws4SignerRequestParams signerRequestParams,
      AwsS3V4SignerParams signerParams,
      SdkChecksum sdkChecksum) {
    checkSignerParams(signerParams);
  }

  @Override
  protected String calculateContentHashPresign(
      SdkHttpFullRequest.Builder mutableRequest, Aws4PresignerParams signerParams) {
    return UNSIGNED_PAYLOAD;
  }

  /**
   * 预签名不支持，直接抛出 UnsupportedOperationException。
   *
   * @throws UnsupportedOperationException 远程签名不支持预签名
   */
  @Override
  public SdkHttpFullRequest presign(
      SdkHttpFullRequest request, ExecutionAttributes executionAttributes) {
    throw new UnsupportedOperationException("Pre-signing not allowed.");
  }

  /**
   * 对 S3 HTTP 请求进行远程签名。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>从 ExecutionAttributes 提取签名参数（region 等），构建 {@link S3SignRequest}。
   *   <li>计算缓存 Key（method+region+uri），先查缓存命中则直接使用。
   *   <li>未命中则通过 HTTP POST 将签名请求发送到签名服务端点，携带 OAuth2 认证头， 获取 {@link S3SignResponse}（含签名后的 URI 和
   *       headers）。
   *   <li>若服务端返回 Cache-Control: private，则将签名结果缓存。
   *   <li>用签名后的 URI 和 headers 重建 SdkHttpFullRequest 并返回。
   * </ol>
   *
   * @param request 待签名的 S3 HTTP 请求
   * @param executionAttributes 执行属性（含签名参数）
   * @return 签名后的 S3 HTTP 请求
   */
  @Override
  public SdkHttpFullRequest sign(
      SdkHttpFullRequest request, ExecutionAttributes executionAttributes) {
    AwsS3V4SignerParams signerParams =
        extractSignerParams(AwsS3V4SignerParams.builder(), executionAttributes).build();

    S3SignRequest remoteSigningRequest =
        ImmutableS3SignRequest.builder()
            .method(request.method().name())
            .region(signerParams.signingRegion().id())
            .uri(request.getUri())
            .headers(request.headers())
            .properties(requestPropertiesSupplier().get())
            .body(bodyAsString(request))
            .build();

    Key cacheKey = Key.from(remoteSigningRequest);
    SignedComponent cachedSignedComponent = SIGNED_COMPONENT_CACHE.getIfPresent(cacheKey);
    SignedComponent signedComponent;

    if (null != cachedSignedComponent) {
      signedComponent = cachedSignedComponent;
    } else {
      Map<String, String> responseHeaders = Maps.newHashMap();
      Consumer<Map<String, String>> responseHeadersConsumer = responseHeaders::putAll;
      S3SignResponse s3SignResponse =
          httpClient()
              .post(
                  endpoint(),
                  remoteSigningRequest,
                  S3SignResponse.class,
                  () -> authSession().headers(),
                  ErrorHandlers.defaultErrorHandler(),
                  responseHeadersConsumer);

      signedComponent =
          ImmutableSignedComponent.builder()
              .headers(s3SignResponse.headers())
              .signedURI(s3SignResponse.uri())
              .build();

      if (canBeCached(responseHeaders)) {
        SIGNED_COMPONENT_CACHE.put(cacheKey, signedComponent);
      }
    }

    // The SdkHttpFullRequest Builder appends the raw path from the input URI in .uri(),
    // so we need to clear the current path from the request
    SdkHttpFullRequest.Builder mutableRequest = request.toBuilder();
    mutableRequest.encodedPath("");
    mutableRequest.uri(signedComponent.signedURI());
    reconstructHeaders(signedComponent.headers(), mutableRequest);

    return mutableRequest.build();
  }

  /**
   * 仅对 DeleteObjectsRequest 提取 body 字符串（该请求通过 POST body 传递待删除对象列表）。
   *
   * @param request S3 HTTP 请求
   * @return body 字符串，非 DeleteObjects 请求返回 null
   */
  private String bodyAsString(SdkHttpFullRequest request) {
    if (isDeleteObjectsRequest(request) && request.contentStreamProvider().isPresent()) {
      try (InputStream is = request.contentStreamProvider().get().newStream()) {
        return IoUtils.toUtf8String(is);
      } catch (IOException e) {
        LOG.debug("Failed to determine body for S3 sign request", e);
      }
    }

    return null;
  }

  private boolean isDeleteObjectsRequest(SdkHttpFullRequest request) {
    return request.method() == SdkHttpMethod.POST
        && request.rawQueryParameters().containsKey("delete");
  }

  /**
   * 用签名服务返回的 headers 重建请求头。
   *
   * <p>逻辑：移除服务端发送的 Cache-Control 头，用原始请求中的 header 覆盖签名 header， 再将所有 header 写回请求构建器。
   *
   * @param signedAndUnsignedHeaders 签名服务返回的 headers
   * @param mutableRequest 请求构建器
   */
  private void reconstructHeaders(
      Map<String, List<String>> signedAndUnsignedHeaders,
      SdkHttpFullRequest.Builder mutableRequest) {
    Map<String, List<String>> headers = Maps.newHashMap(signedAndUnsignedHeaders);
    // we need to remove the Cache-Control header that is being sent by the server
    headers.remove(CACHE_CONTROL);

    // we need to overwrite whatever headers the server signed/unsigned with the ones from the
    // original request and then put all headers back to the request
    headers.putAll(mutableRequest.headers());
    headers.forEach(mutableRequest::putHeader);
  }

  private boolean canBeCached(Map<String, String> responseHeaders) {
    return CACHE_CONTROL_PRIVATE.equals(responseHeaders.get(CACHE_CONTROL));
  }

  /**
   * 校验签名参数不支持 payload 签名和分块编码。
   *
   * @throws UnsupportedOperationException 启用了 payload 签名或分块编码
   */
  private void checkSignerParams(AwsS3V4SignerParams signerParams) {
    if (signerParams.enablePayloadSigning()) {
      throw new UnsupportedOperationException("Payload signing not supported");
    }

    if (signerParams.enableChunkedEncoding()) {
      throw new UnsupportedOperationException("Chunked encoding not supported");
    }
  }

  /** 签名缓存键：由 method、region、uri 组成，用于标识可复用的签名结果。 */
  @Value.Immutable
  interface Key {
    String method();

    String region();

    String uri();

    static Key from(S3SignRequest request) {
      return ImmutableKey.builder()
          .method(request.method())
          .region(request.region())
          .uri(request.uri().toString())
          .build();
    }
  }

  /** 签名缓存值：包含签名后的 headers 和 URI。 */
  @Value.Immutable
  interface SignedComponent {
    Map<String, List<String>> headers();

    URI signedURI();
  }

  /**
   * 静态工厂方法：根据属性创建 S3V4RestSignerClient 实例。
   *
   * @param properties 配置属性
   * @return 签名器实例
   */
  public static S3V4RestSignerClient create(Map<String, String> properties) {
    return ImmutableS3V4RestSignerClient.builder().properties(properties).build();
  }
}
