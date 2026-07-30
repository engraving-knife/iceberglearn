# 提交 0584：Make OAuth `audience` and `resource` configurable

## 提交信息

- **序号**：0584 / 4088
- **哈希**：5f655a323a98a2ee3b04498aec2edeb818766373
- **短哈希**：5f655a323
- **日期**：2024-03-11（Mon Mar 11 14:22:28 2024 -0700）
- **作者**：Himadri Pal <mehimu@gmail.com>
- **提交说明**：Make OAuth `audience` and `resource` configurable (#9839)
- **PR/Issue**：#9839

## 总体目的

Iceberg 的 REST Catalog 和 S3v4 Rest Signer 在与 REST 服务端交互时，需要通过 OAuth2 进行鉴权。在 OAuth2 规范（RFC 6749 和 RFC 8693 Token Exchange）中，访问令牌请求（client_credentials 拨发与 token exchange 刷新）除了 `grant_type`、`scope`、`subject_token` 等必填字段外，还允许携带若干可选参数，其中两个最常见且重要的可选参数是：

- **`audience`**：标识令牌的目标受众（resource server），让授权服务器在签发的 access token 中嵌入该 audience 声明，使下游服务可校验 token 是否确实是为自己签发的，避免 token 跨服务误用；
- **`resource`**：标识令牌所要访问的资源（可以是 URI 或资源标识符），让授权服务器签发针对特定资源范围的 token，常用于多租户或多 API 的统一授权服务器场景。

在许多企业级 OAuth2 / OIDC 部署中（例如 Auth0、Okta、Azure AD、Keycloak），授权服务器要求客户端在请求 token 时显式传入 `audience` 和/或 `resource` 才能签发可用的 access token；如果不传，要么直接拒绝，要么签发的 token 缺少必要的 audience 声明，导致下游 REST Catalog 服务端拒绝该 token。Iceberg 1.5.0 之前只允许配置 `scope`，无法传递 `audience` 和 `resource`，这导致 Iceberg REST Catalog 在这类授权服务器环境下无法完成 OAuth2 鉴权，从而无法使用。

本提交的目的就是让用户能够通过 catalog 属性配置 `audience` 和 `resource`（以及未来其他可选 OAuth 参数），并把这些参数自动注入到所有 OAuth2 token 请求路径中（初始拨发、token exchange 刷新、客户端凭据刷新），使 Iceberg REST Catalog 能与要求这些参数的企业级授权服务器协同工作。

## 如何达成设计目的

整体设计思路是"参数收集 + 全链路传递"：

1. **统一收集入口** `OAuth2Util.buildOptionalParam(properties)`：定义一个白名单（目前为 `AUDIENCE`、`RESOURCE`），从 catalog 属性中提取这些可选参数，组装成 `Map<String, String>`，同时把 `scope` 也并入其中（用于统一通过 `formData.putAll` 注入表单）。
2. **全链路传递** `optionalOAuthParams`：把该 Map 通过方法签名层层透传——
   - `fetchToken`（client_credentials 拨发）
   - `refreshToken`（token exchange 刷新）
   - `exchangeToken`（带 actor token 的 token exchange）
   - 内部 `tokenExchangeRequest` / `clientCredentialsRequest` 构造表单时调用 `formData.putAll(optionalParams)` 注入；
3. **`AuthSession` 持有 optionalOAuthParams**：会话对象在构造时持有该 Map，后续 refresh 时直接复用，避免每次刷新都要重新从属性中提取；
4. **入口接入** `RESTSessionCatalog` 与 `S3V4RestSignerClient`：在初始化时调用 `buildOptionalParam` 一次性收集，传入 `AuthSession` 和 `fetchToken`；
5. **向后兼容**：保留旧版 `AuthSession` 构造器（不带 `optionalOAuthParams` 参数），标记 `@Deprecated since 1.6.0`，内部默认用 `ImmutableMap.of()`，保证外部子类化或调用方不受影响。

这套设计的关键在于"集中收集 + 沿调用链透传"，避免在每处 token 请求点重复读取属性，也方便未来扩展新的可选 OAuth 参数（只需在 `buildOptionalParam` 的白名单里加一个常量即可）。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/auth/OAuth2Properties.java`

**修改目的**：定义 `audience` 和 `resource` 两个 OAuth2 可选参数的属性键常量，作为统一的配置入口名。

**工作逻辑**：

```java
/** Optional param audience for OAuth2. */
public static final String AUDIENCE = "audience";

/** Optional param resource for OAuth2. */
public static final String RESOURCE = "resource";
```

新增两个 `public static final String` 常量，键名直接采用 OAuth2 规范定义的字段名 `audience` 和 `resource`，这样用户在 catalog 属性中写 `audience=xxx` 时，键名与发往授权服务器的表单字段名一致，便于理解与排查。这两个常量后续在 `OAuth2Util.buildOptionalParam` 的白名单里被引用。

### `core/src/main/java/org/apache/iceberg/rest/auth/OAuth2Util.java`

**修改目的**：实现可选参数的收集、向所有 token 请求路径的透传，以及 `AuthSession` 持有与复用。

**工作逻辑**：

#### (1) 新增 `buildOptionalParam` 收集器

```java
public static Map<String, String> buildOptionalParam(Map<String, String> properties) {
  // these are some options oauth params based on specification
  // for any new optional oauth param, define the constant and add the constant to this list
  Set<String> optionalParamKeys =
      ImmutableSet.of(OAuth2Properties.AUDIENCE, OAuth2Properties.RESOURCE);
  ImmutableMap.Builder<String, String> optionalParamBuilder = ImmutableMap.builder();
  // add scope too,
  optionalParamBuilder.put(
      OAuth2Properties.SCOPE,
      properties.getOrDefault(OAuth2Properties.SCOPE, OAuth2Properties.CATALOG_SCOPE));
  // add all other parameters
  for (String key : optionalParamKeys) {
    String value = properties.get(key);
    if (value != null) {
      optionalParamBuilder.put(key, value);
    }
  }
  return optionalParamBuilder.buildKeepingLast();
}
```

该静态方法接受 catalog 属性 Map，返回一个"已合并好的可选参数 Map"，包含：
- `scope`：从属性取，缺省为 `CATALOG_SCOPE`；这里把 scope 也并入是为了在表单构造阶段统一用 `formData.putAll(optionalParams)` 注入，让 scope 与 audience/resource 走同一条注入路径，避免重复处理；
- `audience` 和 `resource`：仅当属性中显式配置了对应键时才加入，未配置则不出现该字段（符合 OAuth2 "可选参数未配置则不发"的语义）。

`buildKeepingLast()` 用于在 builder 中出现重复键时保留后放入的值，等于用 optionalParams 中的 scope 覆盖（虽然此处不会与已有 scope 重复，但用法上更稳健）。

注释明确指引后续维护者："for any new optional oauth param, define the constant and add the constant to this list"——即新增可选参数只需两步：定义常量 + 加入 `optionalParamKeys` 集合，扩展性良好。

#### (2) 给 `fetchToken` / `refreshToken` / `exchangeToken` 增加 `optionalOAuthParams` 参数

每个 token 请求方法都新增一个 `Map<String, String> optionalOAuthParams` 形参，并最终透传到表单构造方法。以 `fetchToken` 为例：

```java
public static OAuthTokenResponse fetchToken(
    RESTClient client,
    Map<String, String> headers,
    String credential,
    String scope,
    String oauth2ServerUri,
    Map<String, String> optionalParams) {
  Map<String, String> request =
      clientCredentialsRequest(
          credential,
          scope != null ? ImmutableList.of(scope) : ImmutableList.of(),
          optionalParams);
  ...
}
```

旧的重载（不带 `optionalOAuthParams`）保留并委托新版，传入 `ImmutableMap.of()`：

```java
public static OAuthTokenResponse fetchToken(
    RESTClient client, Map<String, String> headers, String credential, String scope) {
  return fetchToken(client, headers, credential, scope, ResourcePaths.tokens(), ImmutableMap.of());
}
```

`refreshToken`、`exchangeToken` 同理：旧重载保留，内部默认空 Map，保证调用方向后兼容。

#### (3) 表单构造方法注入 optionalParams

`tokenExchangeRequest` 和 `clientCredentialsRequest` 两个内部方法在末尾追加 `formData.putAll(optionalParams)`，并用 `buildKeepingLast()` 构建最终表单。这样 `audience`、`resource`、`scope` 三个字段（以及任何未来新增的可选字段）都会作为表单字段发送给授权服务器：

```java
private static Map<String, String> clientCredentialsRequest(
    String clientId,
    String clientSecret,
    List<String> scopes,
    Map<String, String> optionalOAuthParams) {
  ImmutableMap.Builder<String, String> formData = ImmutableMap.builder();
  formData.put(GRANT_TYPE, CLIENT_CREDENTIALS);
  if (clientId != null) {
    formData.put(CLIENT_ID, clientId);
  }
  formData.put(CLIENT_SECRET, clientSecret);
  formData.put(SCOPE, toScope(scopes));
  formData.putAll(optionalOAuthParams);
  return formData.buildKeepingLast();
}
```

`buildKeepingLast()` 在此起关键作用：表单中先放入了 `SCOPE`（来自 `toScope(scopes)`），随后 `putAll(optionalOAuthParams)` 又可能包含一个 `scope` 键（因为 `buildOptionalParam` 把 scope 也并入了 optionalParams）。`buildKeepingLast()` 保证后者覆盖前者，最终生效的是 `buildOptionalParam` 中合并好的 scope 值（含默认值 `CATALOG_SCOPE`），避免重复键导致 `ImmutableMap.builder().build()` 抛出 `IllegalArgumentException`。

#### (4) `AuthSession` 持有 `optionalOAuthParams`

```java
private Map<String, String> optionalOAuthParams = ImmutableMap.of();

public AuthSession(
    Map<String, String> baseHeaders,
    String token,
    String tokenType,
    String credential,
    String scope,
    String oauth2ServerUri,
    Map<String, String> optionalOAuthParams) {
  ...
  this.optionalOAuthParams = optionalOAuthParams;
}
```

会话对象构造时持有该 Map，提供 `optionalOAuthParams()` 访问器。后续在 `refreshExpiredToken`、`refresh`、`fetchToken`、`exchangeToken` 等内部刷新流程中复用：

```java
return refreshToken(
    client, headers(), token, tokenType, scope, oauth2ServerUri, optionalOAuthParams);
```

子会话（`fromTokenResponse`、`fromCredential` 等）创建时也会从 parent 透传 `optionalOAuthParams`，保证 token 刷新链路始终携带可选参数。

#### (5) 旧构造器标 `@Deprecated` 保留向后兼容

```java
/** @deprecated since 1.6.0, will be removed in 1.7.0 */
@Deprecated
public AuthSession(
    Map<String, String> baseHeaders,
    String token,
    String tokenType,
    String credential,
    String scope,
    String oauth2ServerUri) {
  ...
  this.optionalOAuthParams = ImmutableMap.of();
}
```

旧的 6 参数构造器被标记为 `@Deprecated since 1.6.0, will be removed in 1.7.0`，内部把 `optionalOAuthParams` 设为空 Map，保证外部子类化或老调用方在 1.5.x → 1.6.0 升级期间继续可用，符合 Iceberg 的语义化版本和 deprecation 周期约定。这同时满足了 Revapi 的兼容性检查（既有 public 构造器未删除，新增了带额外参数的构造器，API 兼容）。

### `core/src/main/java/org/apache/iceberg/rest/RESTSessionCatalog.java`

**修改目的**：在 REST Catalog 初始化阶段一次性收集可选 OAuth 参数，传递给首次 `fetchToken` 调用与 `AuthSession` 会话对象。

**工作逻辑**：

在 `initialize` 流程中新增一行：

```java
String scope = props.getOrDefault(OAuth2Properties.SCOPE, OAuth2Properties.CATALOG_SCOPE);
Map<String, String> optionalOAuthParams = OAuth2Util.buildOptionalParam(props);
String oauth2ServerUri =
    props.getOrDefault(OAuth2Properties.OAUTH2_SERVER_URI, ResourcePaths.tokens());
```

随后两处使用：
1. 初始 client_credentials 拨发 token 时传入：

```java
authResponse =
    OAuth2Util.fetchToken(initClient, initHeaders, credential, scope, oauth2ServerUri, optionalOAuthParams);
```

2. 构造主会话 `catalogAuth` 时传入：

```java
this.catalogAuth =
    new AuthSession(baseHeaders, null, null, credential, scope, oauth2ServerUri, optionalOAuthParams);
```

这样 `catalogAuth` 在后续 token 刷新流程中始终携带 `audience` 和 `resource`（若配置），无需重复读取属性。

### `aws/src/main/java/org/apache/iceberg/aws/s3/signer/S3V4RestSignerClient.java`

**修改目的**：让 S3 REST 签名客户端（用 REST 服务端代签 S3v4 请求）也支持把可选 OAuth 参数传给 token 拨发/刷新流程，与主 Catalog 鉴权行为一致。

**工作逻辑**：

新增 lazy 属性和两处构造点：

```java
@Value.Lazy
public Map<String, String> optionalOAuthParams() {
  return OAuth2Util.buildOptionalParam(properties());
}
```

`@Value.Lazy` 来自 Immutables 框架，表示该属性在首次访问时计算并缓存，避免每次签名都重新解析属性。

随后两处构造 `AuthSession` 时把 `optionalOAuthParams()` 传入：
- 静态 token 模式（仅持有 token）：
```java
new AuthSession(
    ImmutableMap.of(), token, null, credential(), SCOPE, oauth2ServerUri(), optionalOAuthParams())
```
- credential 模式（用 client_credentials 拨发 token）：
```java
AuthSession session =
    new AuthSession(
        ImmutableMap.of(), null, null, credential(), SCOPE, oauth2ServerUri(), optionalOAuthParams());
OAuthTokenResponse authResponse =
    OAuth2Util.fetchToken(
        httpClient(),
        session.headers(),
        credential(),
        SCOPE,
        oauth2ServerUri(),
        optionalOAuthParams());
```

这样 S3 签名客户端与 REST Catalog 主鉴权共享同一套可选参数语义，用户配置 `audience`/`resource` 后两者行为一致。

### `core/src/test/java/org/apache/iceberg/rest/TestRESTCatalog.java`

**修改目的**：扩展测试以验证新行为——可选参数能正确进入 token 请求表单——并保持旧调用方（无可选参数）行为不变。

**工作逻辑**：

1. **修改 `testClientAuth` 方法签名**：增加 `Map<String, String> optionalOAuthParams` 形参；构建 catalog 初始化属性时 `putAll(optionalOAuthParams)`；并在 `optionalOAuthParams` 非空时新增一段 `Mockito.verify`，断言 POST `/tokens` 请求的 body Map 包含所有可选参数的键：

```java
if (!optionalOAuthParams.isEmpty()) {
  Mockito.verify(adapter)
      .execute(
          eq(HTTPMethod.POST),
          eq(oauth2ServerUri),
          any(),
          Mockito.argThat(
              body ->
                  ((Map<String, String>) body)
                      .keySet()
                      .containsAll(optionalOAuthParams.keySet())),
          eq(OAuthTokenResponse.class),
          eq(catalogHeaders),
          any());
}
```

2. **所有既有调用点**追加 `ImmutableMap.of()` 实参，保持原行为（无可选参数）不变：

```java
... oauth2ServerUri, ImmutableMap.of());
```

3. **新增参数化测试 `testClientAccessTokenWithOptionalParams`**：覆盖 `v1/oauth/tokens` 和 `https://auth-server.com/token` 两种 oauth2ServerUri，传入 `scope=custom_scope`、`audience=test_audience`、`resource=test_resource`，验证这三者都被包含在 token 请求 body 中，从而保证新增功能正确：

```java
@ParameterizedTest
@ValueSource(strings = {"v1/oauth/tokens", "https://auth-server.com/token"})
public void testClientAccessTokenWithOptionalParams(String oauth2ServerUri) {
  testClientAuth(
      "bearer-token",
      ImmutableMap.of(...),
      ImmutableMap.of("Authorization", "Bearer token-exchange-token:sub=access-token,act=bearer-token"),
      oauth2ServerUri,
      ImmutableMap.of(
          "scope", "custom_scope", "audience", "test_audience", "resource", "test_resource"));
}
```

## 小结

- **成效**：完成 OAuth2 可选参数 `audience` 和 `resource` 的可配置化。用户通过 catalog 属性 `audience=...` 和 `resource=...` 即可让 Iceberg REST Catalog 与要求这些参数的企业级授权服务器（Auth0/Okta/Azure AD/Keycloak 等）协同工作，覆盖 client_credentials 拨发和 token exchange 刷新两条主要鉴权路径，同时 S3 REST 签名客户端一并支持。
- **影响范围**：改动 5 个文件，234 行新增 / 47 行删除。主要触及 `core` 模块的 REST 鉴权链路（`OAuth2Util`、`OAuth2Properties`、`RESTSessionCatalog`）和 `aws` 模块的 S3 签名客户端（`S3V4RestSignerClient`），以及 `core` 的测试 `TestRESTCatalog`。无运行时行为变化（不配置 `audience`/`resource` 时行为与 1.4.x 完全一致）。
- **设计亮点**：采用"集中收集 + 全链路透传"模式，新增可选参数只需在 `buildOptionalParam` 的白名单加常量；`AuthSession` 持有 optionalOAuthParams 避免重复解析；旧构造器 `@Deprecated` 保留并默认空 Map，遵守 deprecation 周期，通过 Revapi 兼容性检查。
- **回迁到 1.4.x 的注意事项**：**可以考虑回迁**，因为这是一个纯增强、向后兼容的功能改动，无 API 破坏（旧 API 标 `@Deprecated` 但保留），无运行时行为变化（不配置时行为不变），且解决了实际生产环境无法与某些授权服务器对接的问题。回迁时需注意：
  1. 需要同时回迁 5 个文件的改动（不能只回迁部分），以保证 `AuthSession` 新构造器与 `RESTSessionCatalog`/`S3V4RestSignerClient` 的调用方一致；
  2. `OAuth2Util` 中 `buildKeepingLast()` 方法依赖 relocated Guava 的版本，需确认 1.4.x 分支的 relocated Guava 已包含该方法（`ImmutableMap.Builder.buildKeepingLast()` 在 Guava 31+ 可用，1.4.x 已满足）；
  3. `@Deprecated since 1.6.0, will be removed in 1.7.0` 标注在回迁后应保持，不应改成 1.4.x 版本号，避免与 main 分支后续删除时机产生分歧；
  4. 回迁后应在 1.4.x 的发行说明中明确标注"backported from 1.5.0"以便用户感知；
  5. 需同步回迁对应的 `TestRESTCatalog` 测试改动，确保新行为有测试覆盖。
