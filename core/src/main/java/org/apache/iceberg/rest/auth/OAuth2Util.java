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
package org.apache.iceberg.rest.auth;

import static org.apache.iceberg.TableProperties.COMMIT_MAX_RETRY_WAIT_MS_DEFAULT;
import static org.apache.iceberg.TableProperties.COMMIT_MIN_RETRY_WAIT_MS_DEFAULT;
import static org.apache.iceberg.TableProperties.COMMIT_TOTAL_RETRY_TIME_MS_DEFAULT;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import org.apache.iceberg.relocated.com.google.common.annotations.VisibleForTesting;
import org.apache.iceberg.relocated.com.google.common.base.Joiner;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.base.Splitter;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.apache.iceberg.rest.ErrorHandlers;
import org.apache.iceberg.rest.RESTClient;
import org.apache.iceberg.rest.RESTUtil;
import org.apache.iceberg.rest.ResourcePaths;
import org.apache.iceberg.rest.responses.OAuthTokenResponse;
import org.apache.iceberg.util.JsonUtil;
import org.apache.iceberg.util.Pair;
import org.apache.iceberg.util.Tasks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 文件级说明：OAuth2 认证工具类，封装令牌兑换/刷新、认证头构造、令牌响应序列化等能力。
 *
 * <p>所属模块：iceberg-core（REST Catalog 客户端的认证支持层，位于 rest.auth 子包）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>构造 HTTP 认证头（Bearer / Basic），并管理令牌与头之间的转换。
 *   <li>实现 OAuth2 三种主要流程：客户端凭证（client_credentials）、令牌交换（token-exchange， 参见 RFC 8693）、令牌刷新（refresh）。
 *   <li>提供 {@link OAuthTokenResponse} 的 JSON 序列化/反序列化，以及从 JWT 中解析过期时间。
 *   <li>通过内部类 {@link AuthSession} 封装“会话级”的认证状态与自动刷新调度。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>无状态工具方法 + 有状态会话分离：上层方法保持纯函数式，便于测试与复用；会话状态集中在 {@link AuthSession} 中，配合 {@link
 *       ScheduledExecutorService} 实现后台令牌刷新。
 *   <li>失败重试：刷新流程使用指数退避重试，且在主令牌刷新失败时回退到 credential 兑换，提升 长会话的健壮性。
 *   <li>线程安全：会话字段使用 volatile，使刷新线程与请求线程可见性一致。
 * </ul>
 *
 * <p>上下游关系：被 {@link org.apache.iceberg.rest.RESTSessionCatalog} 在初始化与请求阶段调用， 底层依赖 {@link
 * org.apache.iceberg.rest.RESTClient} 发送表单请求。
 */
public class OAuth2Util {
  private OAuth2Util() {}

  private static final Logger LOG = LoggerFactory.getLogger(OAuth2Util.class);

  // valid scope tokens are from ascii 0x21 to 0x7E, excluding 0x22 (") and 0x5C (\)
  private static final Pattern VALID_SCOPE_TOKEN = Pattern.compile("^[!-~&&[^\"\\\\]]+$");
  private static final Splitter SCOPE_DELIMITER = Splitter.on(" ");
  private static final Joiner SCOPE_JOINER = Joiner.on(" ");

  private static final String AUTHORIZATION_HEADER = "Authorization";
  private static final String BEARER_PREFIX = "Bearer ";
  private static final String BASIC_PREFIX = "Basic ";

  private static final Splitter CREDENTIAL_SPLITTER = Splitter.on(":").limit(2).trimResults();
  private static final String GRANT_TYPE = "grant_type";
  private static final String CLIENT_CREDENTIALS = "client_credentials";
  private static final String TOKEN_EXCHANGE = "urn:ietf:params:oauth:grant-type:token-exchange";
  private static final String SCOPE = "scope";
  private static final String CATALOG = "catalog";

  // Client credentials flow
  private static final String CLIENT_ID = "client_id";
  private static final String CLIENT_SECRET = "client_secret";

  // Token exchange flow
  private static final String SUBJECT_TOKEN = "subject_token";
  private static final String SUBJECT_TOKEN_TYPE = "subject_token_type";
  private static final String ACTOR_TOKEN = "actor_token";
  private static final String ACTOR_TOKEN_TYPE = "actor_token_type";
  private static final Set<String> VALID_TOKEN_TYPES =
      Sets.newHashSet(
          OAuth2Properties.ACCESS_TOKEN_TYPE,
          OAuth2Properties.REFRESH_TOKEN_TYPE,
          OAuth2Properties.ID_TOKEN_TYPE,
          OAuth2Properties.SAML1_TOKEN_TYPE,
          OAuth2Properties.SAML2_TOKEN_TYPE,
          OAuth2Properties.JWT_TOKEN_TYPE);

  // response serialization
  private static final String ACCESS_TOKEN = "access_token";
  private static final String TOKEN_TYPE = "token_type";
  private static final String EXPIRES_IN = "expires_in";
  private static final String ISSUED_TOKEN_TYPE = "issued_token_type";
  private static final String REFRESH_TOKEN = "refresh_token";

  /**
   * 根据令牌构造 Bearer 认证头。
   *
   * @param token Bearer 令牌，可为 null
   * @return 包含 Authorization 头的不可变 Map；token 为 null 时返回空 Map
   */
  public static Map<String, String> authHeaders(String token) {
    if (token != null) {
      return ImmutableMap.of(AUTHORIZATION_HEADER, BEARER_PREFIX + token);
    } else {
      return ImmutableMap.of();
    }
  }

  /**
   * 根据 credential 构造 Basic 认证头（Base64 编码）。
   *
   * @param credential 形如 {@code clientId:clientSecret} 或单独 secret 的凭证，可为 null
   * @return 包含 Authorization 头的不可变 Map；credential 为 null 时返回空 Map
   */
  public static Map<String, String> basicAuthHeaders(String credential) {
    if (credential != null) {
      return ImmutableMap.of(
          AUTHORIZATION_HEADER,
          BASIC_PREFIX
              + Base64.getEncoder().encodeToString(credential.getBytes(StandardCharsets.UTF_8)));
    } else {
      return ImmutableMap.of();
    }
  }

  /**
   * 判断给定的 scope token 是否合法。
   *
   * <p>合法字符范围：ASCII 0x21-0x7E，排除双引号和反斜杠（参见 RFC 6749 §3.3）。
   *
   * @param scopeToken 待校验的 scope token
   * @return 合法返回 true
   */
  public static boolean isValidScopeToken(String scopeToken) {
    return VALID_SCOPE_TOKEN.matcher(scopeToken).matches();
  }

  /**
   * 将以空格分隔的 scope 字符串拆分为列表。
   *
   * @param scope 空格分隔的 scope 字符串
   * @return scope 列表
   */
  public static List<String> parseScope(String scope) {
    return SCOPE_DELIMITER.splitToList(scope);
  }

  /**
   * 将多个 scope 拼接为空格分隔的字符串。
   *
   * @param scopes scope 可迭代集合
   * @return 空格分隔的 scope 字符串
   */
  public static String toScope(Iterable<String> scopes) {
    return SCOPE_JOINER.join(scopes);
  }

  /**
   * 使用 subject token 执行令牌刷新（token-exchange 流程的简化版，无 actor token）。
   *
   * <p>逻辑：构造 token-exchange 表单请求，通过 {@link RESTClient#postForm} 发往 {@link ResourcePaths#tokens()}
   * 端点，并对响应做校验。
   *
   * @param client REST 客户端
   * @param headers 请求头（通常含父会话认证信息）
   * @param subjectToken 待刷新的 subject 令牌
   * @param subjectTokenType subject 令牌类型 URN
   * @param scope 请求的 scope，可为 null
   * @return 刷新后的令牌响应
   */
  private static OAuthTokenResponse refreshToken(
      RESTClient client,
      Map<String, String> headers,
      String subjectToken,
      String subjectTokenType,
      String scope) {
    Map<String, String> request =
        tokenExchangeRequest(
            subjectToken,
            subjectTokenType,
            scope != null ? ImmutableList.of(scope) : ImmutableList.of());

    OAuthTokenResponse response =
        client.postForm(
            ResourcePaths.tokens(),
            request,
            OAuthTokenResponse.class,
            headers,
            ErrorHandlers.oauthErrorHandler());
    response.validate();

    return response;
  }

  /**
   * 执行 OAuth2 令牌交换（RFC 8693 Token Exchange），可携带 actor token 实现委托场景。
   *
   * <p>逻辑：构造包含 subject_token 与可选 actor_token 的 token-exchange 表单请求，POST 到 tokens 端点并校验响应。
   *
   * @param client REST 客户端
   * @param headers 请求头
   * @param subjectToken subject 令牌（被交换的令牌）
   * @param subjectTokenType subject 令牌类型 URN
   * @param actorToken actor 令牌（发起者令牌），可为 null
   * @param actorTokenType actor 令牌类型 URN，actorToken 非 null 时必填
   * @param scope 请求的 scope，可为 null
   * @return 交换得到的令牌响应
   */
  public static OAuthTokenResponse exchangeToken(
      RESTClient client,
      Map<String, String> headers,
      String subjectToken,
      String subjectTokenType,
      String actorToken,
      String actorTokenType,
      String scope) {
    Map<String, String> request =
        tokenExchangeRequest(
            subjectToken,
            subjectTokenType,
            actorToken,
            actorTokenType,
            scope != null ? ImmutableList.of(scope) : ImmutableList.of());

    OAuthTokenResponse response =
        client.postForm(
            ResourcePaths.tokens(),
            request,
            OAuthTokenResponse.class,
            headers,
            ErrorHandlers.oauthErrorHandler());
    response.validate();

    return response;
  }

  /**
   * 使用 credential 执行 OAuth2 客户端凭证流程获取新令牌。
   *
   * <p>逻辑：解析 credential 得到 clientId/clientSecret，构造 client_credentials 表单请求， POST 到 tokens 端点并校验响应。
   *
   * @param client REST 客户端
   * @param headers 请求头
   * @param credential 凭证字符串（{@code clientId:clientSecret} 或单独 secret）
   * @param scope 请求的 scope，可为 null
   * @return 获取到的令牌响应
   */
  public static OAuthTokenResponse fetchToken(
      RESTClient client, Map<String, String> headers, String credential, String scope) {
    Map<String, String> request =
        clientCredentialsRequest(
            credential, scope != null ? ImmutableList.of(scope) : ImmutableList.of());

    OAuthTokenResponse response =
        client.postForm(
            ResourcePaths.tokens(),
            request,
            OAuthTokenResponse.class,
            headers,
            ErrorHandlers.oauthErrorHandler());
    response.validate();

    return response;
  }

  /** 无 actor 的 token-exchange 表单请求构造（委托给完整版本）。 */
  private static Map<String, String> tokenExchangeRequest(
      String subjectToken, String subjectTokenType, List<String> scopes) {
    return tokenExchangeRequest(subjectToken, subjectTokenType, null, null, scopes);
  }

  /**
   * 构造 token-exchange 表单请求体。
   *
   * <p>逻辑：校验 subjectTokenType 与 actorTokenType 必须为合法 URN；组装 grant_type、scope、 subject_token 等字段；若提供
   * actorToken 则附加 actor_token 字段。
   *
   * @param subjectToken subject 令牌
   * @param subjectTokenType subject 令牌类型 URN
   * @param actorToken actor 令牌，可为 null
   * @param actorTokenType actor 令牌类型 URN
   * @param scopes scope 列表
   * @return 表单字段不可变 Map
   * @throws IllegalArgumentException 若 token 类型 URN 非法
   */
  private static Map<String, String> tokenExchangeRequest(
      String subjectToken,
      String subjectTokenType,
      String actorToken,
      String actorTokenType,
      List<String> scopes) {
    Preconditions.checkArgument(
        VALID_TOKEN_TYPES.contains(subjectTokenType), "Invalid token type: %s", subjectTokenType);
    Preconditions.checkArgument(
        actorToken == null || VALID_TOKEN_TYPES.contains(actorTokenType),
        "Invalid token type: %s",
        actorTokenType);

    ImmutableMap.Builder<String, String> formData = ImmutableMap.builder();
    formData.put(GRANT_TYPE, TOKEN_EXCHANGE);
    formData.put(SCOPE, toScope(scopes));
    formData.put(SUBJECT_TOKEN, subjectToken);
    formData.put(SUBJECT_TOKEN_TYPE, subjectTokenType);
    if (actorToken != null) {
      formData.put(ACTOR_TOKEN, actorToken);
      formData.put(ACTOR_TOKEN_TYPE, actorTokenType);
    }

    return formData.build();
  }

  /**
   * 解析 credential 字符串为 (clientId, clientSecret) 对。
   *
   * <p>逻辑：以 {@code :} 最多拆分两段。两段视为 clientId:clientSecret；一段视为仅 clientSecret （clientId 为 null）；其他情况（受
   * splitter 限制不会发生）抛 IllegalArgumentException。
   *
   * @param credential 凭证字符串
   * @return 包含 clientId（可能为 null）与 clientSecret 的 Pair
   * @throws IllegalArgumentException credential 为 null 或格式非法
   */
  private static Pair<String, String> parseCredential(String credential) {
    Preconditions.checkNotNull(credential, "Invalid credential: null");
    List<String> parts = CREDENTIAL_SPLITTER.splitToList(credential);
    switch (parts.size()) {
      case 2:
        // client ID and client secret
        return Pair.of(parts.get(0), parts.get(1));
      case 1:
        // client secret
        return Pair.of(null, parts.get(0));
      default:
        // this should never happen because the credential splitter is limited to 2
        throw new IllegalArgumentException("Invalid credential: " + credential);
    }
  }

  /** 通过 credential 字符串构造 client_credentials 表单请求。 */
  private static Map<String, String> clientCredentialsRequest(
      String credential, List<String> scopes) {
    Pair<String, String> credentialPair = parseCredential(credential);
    return clientCredentialsRequest(credentialPair.first(), credentialPair.second(), scopes);
  }

  /**
   * 构造 client_credentials 表单请求体。
   *
   * @param clientId 客户端 ID，可为 null（仅 secret 时省略）
   * @param clientSecret 客户端 secret
   * @param scopes scope 列表
   * @return 表单字段不可变 Map
   */
  private static Map<String, String> clientCredentialsRequest(
      String clientId, String clientSecret, List<String> scopes) {
    ImmutableMap.Builder<String, String> formData = ImmutableMap.builder();
    formData.put(GRANT_TYPE, CLIENT_CREDENTIALS);
    if (clientId != null) {
      formData.put(CLIENT_ID, clientId);
    }
    formData.put(CLIENT_SECRET, clientSecret);
    formData.put(SCOPE, toScope(scopes));

    return formData.build();
  }

  /**
   * 将 {@link OAuthTokenResponse} 序列化为 JSON 字符串（紧凑格式）。
   *
   * @param response 令牌响应
   * @return JSON 字符串
   */
  public static String tokenResponseToJson(OAuthTokenResponse response) {
    return JsonUtil.generate(gen -> tokenResponseToJson(response, gen), false);
  }

  /**
   * 将 {@link OAuthTokenResponse} 写入给定的 {@link JsonGenerator}。
   *
   * <p>逻辑：先校验响应合法性，再按 OAuth2 令牌响应字段顺序写入（access_token、token_type、
   * issued_token_type、expires_in、scope），可选字段仅在非 null/非空时写入。
   *
   * @param response 令牌响应
   * @param gen JSON 生成器
   * @throws IOException 写入失败
   */
  public static void tokenResponseToJson(OAuthTokenResponse response, JsonGenerator gen)
      throws IOException {
    response.validate();

    gen.writeStartObject();

    gen.writeStringField(ACCESS_TOKEN, response.token());
    gen.writeStringField(TOKEN_TYPE, response.tokenType());

    if (response.issuedTokenType() != null) {
      gen.writeStringField(ISSUED_TOKEN_TYPE, response.issuedTokenType());
    }

    if (response.expiresInSeconds() != null) {
      gen.writeNumberField(EXPIRES_IN, response.expiresInSeconds());
    }

    if (response.scopes() != null && !response.scopes().isEmpty()) {
      gen.writeStringField(SCOPE, toScope(response.scopes()));
    }

    gen.writeEndObject();
  }

  /**
   * 从 JSON 字符串反序列化为 {@link OAuthTokenResponse}。
   *
   * @param json JSON 字符串
   * @return 令牌响应对象
   */
  public static OAuthTokenResponse tokenResponseFromJson(String json) {
    return JsonUtil.parse(json, OAuth2Util::tokenResponseFromJson);
  }

  /**
   * 从 {@link JsonNode} 反序列化为 {@link OAuthTokenResponse}。
   *
   * <p>逻辑：校验 JSON 必须为对象；读取 access_token、token_type、issued_token_type； 若存在 expires_in 则设置过期秒数；若存在
   * scope 则解析并添加 scope 列表。
   *
   * @param json JSON 节点
   * @return 令牌响应对象
   */
  public static OAuthTokenResponse tokenResponseFromJson(JsonNode json) {
    Preconditions.checkArgument(
        json.isObject(), "Cannot parse token response from non-object: %s", json);

    OAuthTokenResponse.Builder builder =
        OAuthTokenResponse.builder()
            .withToken(JsonUtil.getString(ACCESS_TOKEN, json))
            .withTokenType(JsonUtil.getString(TOKEN_TYPE, json))
            .withIssuedTokenType(JsonUtil.getStringOrNull(ISSUED_TOKEN_TYPE, json));

    if (json.has(EXPIRES_IN)) {
      builder.setExpirationInSeconds(JsonUtil.getInt(EXPIRES_IN, json));
    }

    if (json.has(SCOPE)) {
      builder.addScopes(parseScope(JsonUtil.getString(SCOPE, json)));
    }

    return builder.build();
  }

  /**
   * 若令牌为 JWT，则从中解析 exp 声明得到过期时间戳（毫秒）。
   *
   * <p>逻辑：token 为 null 直接返回 null；按 {@code .} 拆分为 3 段（JWT 由 header.payload.signature 组成）；对 payload 做
   * Base64 URL 解码并解析为 JSON；读取 exp（秒）并转换为毫秒。任何解析失败 均返回 null，表示无法从令牌本身获取过期时间。
   *
   * @param token 令牌字符串
   * @return 令牌过期的 epoch 毫秒；非合法 JWT 或无 exp 声明时返回 null
   */
  static Long expiresAtMillis(String token) {
    if (null == token) {
      return null;
    }

    List<String> parts = Splitter.on('.').splitToList(token);
    if (parts.size() != 3) {
      return null;
    }

    JsonNode node;
    try {
      node = JsonUtil.mapper().readTree(Base64.getUrlDecoder().decode(parts.get(1)));
    } catch (IOException e) {
      return null;
    }

    Long expiresAtSeconds = JsonUtil.getLongOrNull("exp", node);
    if (expiresAtSeconds != null) {
      return TimeUnit.SECONDS.toMillis(expiresAtSeconds);
    }

    return null;
  }

  /**
   * 会话级认证状态管理器：维护当前令牌、认证头、过期时间，并支持自动刷新调度。
   *
   * <p>设计意图：
   *
   * <ul>
   *   <li>将一次认证交互所需的全部上下文（token、tokenType、credential、scope、headers）封装为 不可变快照（字段 volatile
   *       保证可见性），便于在多线程请求间共享。
   *   <li>提供多种“会话构造”入口：fromAccessToken / fromCredential / fromTokenResponse /
   *       fromTokenExchange，分别对应不同的上游认证方式。
   *   <li>刷新失败时回退到 credential 兑换，避免长会话因 token 过期而中断。
   * </ul>
   *
   * <p>上下游关系：由 {@link org.apache.iceberg.rest.RESTSessionCatalog} 持有，请求时通过 {@link #headers()}
   * 获取认证头；刷新由内部调度线程周期性触发。
   */
  public static class AuthSession {
    private static int tokenRefreshNumRetries = 5;
    private static final long MAX_REFRESH_WINDOW_MILLIS = 300_000; // 5 minutes
    private static final long MIN_REFRESH_WAIT_MILLIS = 10;
    private volatile Map<String, String> headers;
    private volatile String token;
    private volatile String tokenType;
    private volatile Long expiresAtMillis;
    private final String credential;
    private final String scope;
    private volatile boolean keepRefreshed = true;

    /**
     * 构造认证会话。
     *
     * <p>逻辑：合并 baseHeaders 与基于 token 生成的 Bearer 头；从 token 中解析过期时间； 保存 credential 与 scope 供后续刷新使用。
     *
     * @param baseHeaders 基础请求头（通常来自父会话）
     * @param token 当前令牌，可为 null
     * @param tokenType 令牌类型 URN
     * @param credential 凭证字符串，用于刷新失败时回退
     * @param scope OAuth2 scope
     */
    public AuthSession(
        Map<String, String> baseHeaders,
        String token,
        String tokenType,
        String credential,
        String scope) {
      this.headers = RESTUtil.merge(baseHeaders, authHeaders(token));
      this.token = token;
      this.tokenType = tokenType;
      this.expiresAtMillis = OAuth2Util.expiresAtMillis(token);
      this.credential = credential;
      this.scope = scope;
    }

    /** 返回当前请求应使用的认证头（含 Authorization）。 */
    public Map<String, String> headers() {
      return headers;
    }

    /** 返回当前令牌字符串。 */
    public String token() {
      return token;
    }

    /** 返回当前令牌类型 URN。 */
    public String tokenType() {
      return tokenType;
    }

    /** 返回令牌过期时间（epoch 毫秒），无法解析时为 null。 */
    public Long expiresAtMillis() {
      return expiresAtMillis;
    }

    /** 返回会话的 OAuth2 scope。 */
    public String scope() {
      return scope;
    }

    /** 停止后续的令牌刷新（关闭会话时调用）。 */
    public void stopRefreshing() {
      this.keepRefreshed = false;
    }

    /** 返回会话的凭证字符串（用于刷新回退）。 */
    public String credential() {
      return credential;
    }

    /** 测试用：设置令牌刷新重试次数。 */
    @VisibleForTesting
    static void setTokenRefreshNumRetries(int retries) {
      tokenRefreshNumRetries = retries;
    }

    /**
     * 创建一个空的认证会话（无 token、无 credential，仅含默认 catalog scope）。
     *
     * @return 空会话实例
     */
    public static AuthSession empty() {
      return new AuthSession(ImmutableMap.of(), null, null, null, OAuth2Properties.CATALOG_SCOPE);
    }

    /**
     * 尝试通过 token-exchange 流程刷新会话令牌。
     *
     * <p>逻辑：
     *
     * <ol>
     *   <li>仅当 token 非 null 且 keepRefreshed 为 true 时才执行刷新。
     *   <li>使用 {@link Tasks} 进行指数退避重试（最多 tokenRefreshNumRetries 次）。
     *   <li>重试失败时回退到 {@link #refreshExpiredToken}（基于 credential 兑换）。
     *   <li>刷新成功则更新 token/tokenType/expiresAtMillis/headers，并返回新的过期间隔。
     * </ol>
     *
     * @param client REST 客户端
     * @return 下次刷新前应等待的间隔与单位；无需刷新或刷新失败返回 null
     */
    public Pair<Integer, TimeUnit> refresh(RESTClient client) {
      if (token != null && keepRefreshed) {
        AtomicReference<OAuthTokenResponse> ref = new AtomicReference<>(null);
        boolean isSuccessful =
            Tasks.foreach(ref)
                .suppressFailureWhenFinished()
                .retry(tokenRefreshNumRetries)
                .onFailure(
                    (holder, err) -> {
                      // attempt to refresh using the client credential instead of the parent token
                      holder.set(refreshExpiredToken(client));
                      if (holder.get() == null) {
                        LOG.warn("Failed to refresh token", err);
                      }
                    })
                .exponentialBackoff(
                    COMMIT_MIN_RETRY_WAIT_MS_DEFAULT,
                    COMMIT_MAX_RETRY_WAIT_MS_DEFAULT,
                    COMMIT_TOTAL_RETRY_TIME_MS_DEFAULT,
                    2.0 /* exponential */)
                .run(holder -> holder.set(refreshCurrentToken(client)));

        if (!isSuccessful || ref.get() == null) {
          return null;
        }

        OAuthTokenResponse response = ref.get();
        this.token = response.token();
        this.tokenType = response.issuedTokenType();
        this.expiresAtMillis = OAuth2Util.expiresAtMillis(token);
        this.headers = RESTUtil.merge(headers, authHeaders(token));

        if (response.expiresInSeconds() != null) {
          return Pair.of(response.expiresInSeconds(), TimeUnit.SECONDS);
        }
      }

      return null;
    }

    /**
     * 刷新当前令牌：若已过期则回退到 credential 兑换，否则执行普通 token-exchange 刷新。
     *
     * @param client REST 客户端
     * @return 刷新后的令牌响应
     */
    private OAuthTokenResponse refreshCurrentToken(RESTClient client) {
      if (null != expiresAtMillis && expiresAtMillis <= System.currentTimeMillis()) {
        // the token has already expired, attempt to refresh using the credential
        return refreshExpiredToken(client);
      } else {
        // attempt a normal refresh
        return refreshToken(client, headers(), token, tokenType, scope);
      }
    }

    /**
     * 令牌过期时的刷新回退：使用 credential 构造 Basic 认证头后执行 token-exchange。 若无 credential 则返回 null。
     *
     * @param client REST 客户端
     * @return 刷新后的令牌响应；无 credential 时返回 null
     */
    private OAuthTokenResponse refreshExpiredToken(RESTClient client) {
      if (credential != null) {
        Map<String, String> basicHeaders = RESTUtil.merge(headers(), basicAuthHeaders(credential));
        return refreshToken(client, basicHeaders, token, tokenType, scope);
      }

      return null;
    }

    /**
     * 基于过期时间调度令牌刷新任务。
     *
     * <p>逻辑：
     *
     * <ol>
     *   <li>计算距离过期的剩余时间 expiresInMillis。
     *   <li>提前量 refreshWindowMillis 取剩余时间的 1/10（上限 5 分钟），用于让刷新请求在过期前完成。
     *   <li>实际等待时间 = 剩余时间 - 提前量，且不小于 MIN_REFRESH_WAIT_MILLIS（10ms）。
     *   <li>调度执行：刷新成功后根据新的过期间隔递归调度下一次刷新。
     * </ol>
     *
     * @param client REST 客户端
     * @param executor 调度执行器
     * @param session 待刷新的认证会话
     * @param expiresAtMillis 令牌过期的 epoch 毫秒
     */
    @SuppressWarnings("FutureReturnValueIgnored")
    private static void scheduleTokenRefresh(
        RESTClient client,
        ScheduledExecutorService executor,
        AuthSession session,
        long expiresAtMillis) {
      long expiresInMillis = expiresAtMillis - System.currentTimeMillis();
      // how much ahead of time to start the request to allow it to complete
      long refreshWindowMillis = Math.min(expiresInMillis / 10, MAX_REFRESH_WINDOW_MILLIS);
      // how much time to wait before expiration
      long waitIntervalMillis = expiresInMillis - refreshWindowMillis;
      // how much time to actually wait
      long timeToWait = Math.max(waitIntervalMillis, MIN_REFRESH_WAIT_MILLIS);

      executor.schedule(
          () -> {
            long refreshStartTime = System.currentTimeMillis();
            Pair<Integer, TimeUnit> expiration = session.refresh(client);
            if (expiration != null) {
              scheduleTokenRefresh(
                  client,
                  executor,
                  session,
                  refreshStartTime + expiration.second().toMillis(expiration.first()));
            }
          },
          timeToWait,
          TimeUnit.MILLISECONDS);
    }

    /**
     * 基于已有的 access token 构造会话，并在需要时安排自动刷新。
     *
     * <p>逻辑：用父会话头和新 token 构造 AuthSession；若 token 已过期则立即刷新； 若无法从 token 解析过期时间但提供了
     * defaultExpiresAtMillis 则使用之； 最终若有执行器和过期时间则调度周期刷新。
     *
     * @param client REST 客户端
     * @param executor 调度执行器，可为 null（不自动刷新）
     * @param token access token
     * @param defaultExpiresAtMillis 默认过期时间（token 无 exp 时使用），可为 null
     * @param parent 父会话（提供基础头与 credential）
     * @return 新的认证会话
     */
    public static AuthSession fromAccessToken(
        RESTClient client,
        ScheduledExecutorService executor,
        String token,
        Long defaultExpiresAtMillis,
        AuthSession parent) {
      AuthSession session =
          new AuthSession(
              parent.headers(),
              token,
              OAuth2Properties.ACCESS_TOKEN_TYPE,
              parent.credential(),
              parent.scope());

      long startTimeMillis = System.currentTimeMillis();
      Long expiresAtMillis = session.expiresAtMillis();

      if (null != expiresAtMillis && expiresAtMillis <= startTimeMillis) {
        Pair<Integer, TimeUnit> expiration = session.refresh(client);
        // if expiration is non-null, then token refresh was successful
        if (expiration != null) {
          if (null != session.expiresAtMillis()) {
            // use the new expiration time from the refreshed token
            expiresAtMillis = session.expiresAtMillis();
          } else {
            // otherwise use the expiration time from the token response
            expiresAtMillis = startTimeMillis + expiration.second().toMillis(expiration.first());
          }
        } else {
          // token refresh failed, don't reattempt with the original expiration
          expiresAtMillis = null;
        }
      } else if (null == expiresAtMillis && defaultExpiresAtMillis != null) {
        expiresAtMillis = defaultExpiresAtMillis;
      }

      if (null != executor && null != expiresAtMillis) {
        scheduleTokenRefresh(client, executor, session, expiresAtMillis);
      }

      return session;
    }

    /**
     * 使用 credential 执行客户端凭证流程获取令牌，并构造会话。
     *
     * @param client REST 客户端
     * @param executor 调度执行器
     * @param credential 凭证字符串
     * @param parent 父会话
     * @return 新的认证会话
     */
    public static AuthSession fromCredential(
        RESTClient client,
        ScheduledExecutorService executor,
        String credential,
        AuthSession parent) {
      long startTimeMillis = System.currentTimeMillis();
      OAuthTokenResponse response =
          fetchToken(client, parent.headers(), credential, parent.scope());
      return fromTokenResponse(client, executor, response, startTimeMillis, parent, credential);
    }

    /**
     * 基于令牌响应构造会话（使用父会话的 credential）。
     *
     * @param client REST 客户端
     * @param executor 调度执行器
     * @param response 令牌响应
     * @param startTimeMillis 流程开始时间（用于计算过期时间）
     * @param parent 父会话
     * @return 新的认证会话
     */
    public static AuthSession fromTokenResponse(
        RESTClient client,
        ScheduledExecutorService executor,
        OAuthTokenResponse response,
        long startTimeMillis,
        AuthSession parent) {
      return fromTokenResponse(
          client, executor, response, startTimeMillis, parent, parent.credential());
    }

    /**
     * 基于令牌响应构造会话的内部实现。
     *
     * <p>逻辑：用响应中的 token 构造 AuthSession；若无法从 token 解析过期时间但响应含 expires_in， 则以 startTimeMillis +
     * expires_in 计算过期时间；最终调度周期刷新。
     *
     * @param client REST 客户端
     * @param executor 调度执行器
     * @param response 令牌响应
     * @param startTimeMillis 流程开始时间
     * @param parent 父会话
     * @param credential 凭证字符串
     * @return 新的认证会话
     */
    private static AuthSession fromTokenResponse(
        RESTClient client,
        ScheduledExecutorService executor,
        OAuthTokenResponse response,
        long startTimeMillis,
        AuthSession parent,
        String credential) {
      AuthSession session =
          new AuthSession(
              parent.headers(),
              response.token(),
              response.issuedTokenType(),
              credential,
              parent.scope());

      Long expiresAtMillis = session.expiresAtMillis();
      if (null == expiresAtMillis && response.expiresInSeconds() != null) {
        expiresAtMillis = startTimeMillis + TimeUnit.SECONDS.toMillis(response.expiresInSeconds());
      }

      if (null != executor && null != expiresAtMillis) {
        scheduleTokenRefresh(client, executor, session, expiresAtMillis);
      }

      return session;
    }

    /**
     * 执行 token-exchange 流程获取令牌并构造会话。
     *
     * <p>逻辑：以父会话的 token 作为 actor token、传入的 token 作为 subject token 执行交换， 然后委托给 fromTokenResponse
     * 构造会话。
     *
     * @param client REST 客户端
     * @param executor 调度执行器
     * @param token subject 令牌
     * @param tokenType subject 令牌类型 URN
     * @param parent 父会话（提供 actor token）
     * @return 新的认证会话
     */
    public static AuthSession fromTokenExchange(
        RESTClient client,
        ScheduledExecutorService executor,
        String token,
        String tokenType,
        AuthSession parent) {
      long startTimeMillis = System.currentTimeMillis();
      OAuthTokenResponse response =
          exchangeToken(
              client,
              parent.headers(),
              token,
              tokenType,
              parent.token(),
              parent.tokenType(),
              parent.scope());
      return fromTokenResponse(client, executor, response, startTimeMillis, parent);
    }
  }
}
