# 提交 0511：Support usage of Separate OIDC Authorization Server URI

## 提交信息

- **序号**：0511 / 4088
- **哈希**：9dcf8dbc4285ad4ab4c1975562bf93fb04747cdd
- **短哈希**：9dcf8dbc4
- **日期**：2024-02-16（Fri Feb 16 18:35:53 2024 -0700）
- **作者**：Sung Yun（syun64）
- **提交说明**：Support usage of Separate OIDC Authorization Server URI (#8976)
- **PR/Issue**：#8976

## 总体目的

Iceberg REST Catalog 默认通过 `v1/oauth/tokens` 这个相对路径在 Rest Catalog 服务自身上完成 OAuth2 token 的获取与刷新（client credentials 流程与 token exchange 流程）。但很多企业部署中，授权服务器（OIDC Provider / IdP，如 Keycloak、Auth0、Okta）与 REST Catalog 服务并不在同一主机或同一路径下，REST Catalog 自身并不是授权服务器，仅是资源服务器。此时客户端无法从 REST Catalog 拿到 token，必须直接去独立的 OIDC 授权服务器请求。

本提交引入一个新的配置项 `oauth2-server-uri`，让用户能够显式指定一个独立的 OAuth2 / OIDC 授权服务器 token endpoint URI，使 REST Catalog 客户端（包括 `RESTSessionCatalog`、`S3V4RestSignerClient`）在面对“授权服务器与 Catalog 服务分离”的部署形态时，能把 `fetchToken`、`exchangeToken`、`refreshToken` 等所有 token 相关请求发送到这个外部 URI，而不是默认的 `v1/oauth/tokens`。同时为了保证默认行为完全向后兼容，未配置该项时回退到原 `ResourcePaths.tokens()`。

## 如何达成设计目的

设计思路分四层：

1. **新增配置常量**：在 `OAuth2Properties` 中加入 `OAUTH2_SERVER_URI = "oauth2-server-uri"`，作为统一的配置键，便于在 REST Catalog、S3V4RestSignerClient 中读取。

2. **核心 token 工具层支持自定义 endpoint**：在 `OAuth2Util` 的 `fetchToken`、`exchangeToken`（两个重载）方法签名中新增 `oauth2ServerUri` 参数，把原本硬编码的 `ResourcePaths.tokens()` 替换为可传入的 URI；同时保留旧签名作为兼容入口（内部以 `ResourcePaths.tokens()` 调用新方法），并把旧构造器标记 `@Deprecated`（1.5.0 起，1.6.0 移除），引导调用方向新签名迁移。`AuthSession` 内部新增 `oauth2ServerUri` 字段并在 `refreshExpiredToken` / `refreshToken` 路径上一路透传，使刷新流程同样走外部授权服务器。

3. **HTTPClient 支持绝对 URL**：原先 `HTTPClient.buildUri` 总是把 path 拼到 base uri 后面，且 `if (path.startsWith("/"))` 抛异常的逻辑放在了 `request` 方法中。本提交把这段校验提前到 `buildUri`，并在 `buildUri` 中识别 `http://` / `https://` 前缀：若 path 是绝对 URL，则直接使用之，不再与 base uri 拼接。这样 `postForm(oauth2ServerUri, ...)` 才能正确发到外部主机。`Builder.uri` 形参也由 `baseUri` 改名为 `path`，让命名更通用。

4. **上层入口注入新配置**：
   - `RESTSessionCatalog.initialize` 从 props 读取 `oauth2-server-uri`（默认 `ResourcePaths.tokens()`），用于初始 `fetchToken` 与构造 `catalogAuth`。
   - `S3V4RestSignerClient` 新增 `@Value.Lazy` 的 `oauth2ServerUri()` 方法，从 properties 读取并默认回退到 `ResourcePaths.tokens()`；在构造两个 `AuthSession`（带 token 的与仅有 credential 的）以及 `fetchToken` 时统一传入。

5. **测试**：原有一系列 `testCatalogCredential` 等测试改为 `@ParameterizedTest`，参数化注入两种 `oauth2ServerUri`：`v1/oauth/tokens`（默认路径，验证兼容）与 `https://auth-server.com/token`（外部 URI，验证新能力）。`RESTCatalogAdapter` 中新增 `SEPARATE_AUTH_TOKENS_URI` 路由与 default 分支兜底，让外部 URI 也能被 adapter mock 命中并复用 `handleOAuthRequest` 逻辑（同时把原 TOKENS case 中的内联 OAuth 处理逻辑抽到 `handleOAuthRequest` 方法以减少重复）。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/auth/OAuth2Properties.java`

**修改目的**：新增 `OAUTH2_SERVER_URI` 配置常量。

**工作逻辑**：新增 `public static final String OAUTH2_SERVER_URI = "oauth2-server-uri";`，注释说明：“Token endpoint URI to fetch token from if the Rest Catalog is not the authorization server.”。这是整个特性的配置入口，所有上层组件都通过这个 key 读取用户配置。

### `core/src/main/java/org/apache/iceberg/rest/auth/OAuth2Util.java`

**修改目的**：让 token 获取、交换、刷新支持自定义授权服务器 URI，并在 `AuthSession` 内部贯穿透传。

**工作逻辑**：
- `fetchToken(client, headers, credential, scope, oauth2ServerUri)`：在 `client.postForm` 调用中把 `ResourcePaths.tokens()` 改为传入的 `oauth2ServerUri`。新增一个 4 参数的旧重载（标记兼容），内部以 `ResourcePaths.tokens()` 调用 5 参数版本。
- 两个 `exchangeToken`（无 actor / 有 actor）重载同样新增 `oauth2ServerUri` 参数，把 `postForm(ResourcePaths.tokens(), ...)` 改为 `postForm(oauth2ServerUri, ...)`。同时新增一个不传 `oauth2ServerUri` 的兼容重载（内部以 `ResourcePaths.tokens()` 调用新方法）。
- `AuthSession`：
  - 新增字段 `private final String oauth2ServerUri;` 与访问器 `oauth2ServerUri()`。
  - 新增 6 参数构造器，把 `oauth2ServerUri` 存入字段。
  - 旧 5 参数构造器标 `@Deprecated`（since 1.5.0，will be removed in 1.6.0），内部把 `oauth2ServerUri` 默认置为 `ResourcePaths.tokens()`，保持旧行为。
  - `empty()` 调用新 6 参数构造器（最后一个参数传 `null`，因为空 session 不需要刷新）。
  - `refreshToken(...)` 调用从 `refreshToken(client, headers, token, tokenType, scope)` 改为传 `oauth2ServerUri` 的版本。
  - `refreshExpiredToken` 中使用 credential 刷新时也带上 `oauth2ServerUri`。
  - 三个 `fromTokenResponse` / `fromCredentialResponse` / `fromAccessToken` 路径上构造 child `AuthSession` 时都把 `parent.oauth2ServerUri()` 透传下去；相应地 `fetchToken`、`exchangeToken` 调用都改为带 `parent.oauth2ServerUri()` 的版本。

整体效果是：一次初始化阶段确定的 `oauth2ServerUri`，会随 `AuthSession` 链路一路传递给后续所有 token 获取/刷新/交换调用，确保用户配置独立授权服务器后整个生命周期都走外部 URI。

### `core/src/main/java/org/apache/iceberg/rest/HTTPClient.java`

**修改目的**：让 `buildUri` 支持绝对 URL，避免把外部授权服务器 URI 错误地拼接到 REST Catalog base uri 后面。

**工作逻辑**：
- 把原来位于 `request` 方法顶部的 `if (path.startsWith("/"))` 校验迁移到 `buildUri` 内部（位置不变，行为不变）。
- `buildUri` 中判断：若 `path` 以 `https://` 或 `http://` 开头，则 `fullPath = path`；否则按原逻辑 `String.format("%s/%s", uri, path)`。然后用 `fullPath` 构造 `URIBuilder` 与抛错信息。
- 把 `Builder.uri(String baseUri)` 形参 `baseUri` 改名为 `path`，更贴近“既可以是路径也可以是绝对 URL”的语义；Javadoc 中把 “URL path” 改为 “URL”。

这是让外部授权服务器 URI 真正可用的关键一环。没有它，`postForm("https://auth-server.com/token", ...)` 会变成 `<catalog-uri>/https://auth-server.com/token`，请求会失败。

### `core/src/main/java/org/apache/iceberg/rest/RESTSessionCatalog.java`

**修改目的**：在 Catalog 初始化阶段读取 `oauth2-server-uri` 并注入到首次 `fetchToken` 与 `catalogAuth`。

**工作逻辑**：
- 在 `initialize` 中新增 `String oauth2ServerUri = props.getOrDefault(OAuth2Properties.OAUTH2_SERVER_URI, ResourcePaths.tokens());`。
- 当配置了 `credential` 时，`OAuth2Util.fetchToken(initClient, initHeaders, credential, scope)` 改为带 `oauth2ServerUri` 的 5 参数版本。
- 构造 `catalogAuth` 时由 `new AuthSession(baseHeaders, null, null, credential, scope)` 改为 6 参数版本 `new AuthSession(baseHeaders, null, null, credential, scope, oauth2ServerUri)`，把授权服务器 URI 注入到 catalog 级 session，从而后续刷新也走外部 URI。

### `aws/src/main/java/org/apache/iceberg/aws/s3/signer/S3V4RestSignerClient.java`

**修改目的**：让 S3V4RestSignerClient（用于通过 REST Catalog 做 S3 sigv4 签名的客户端）也支持独立授权服务器。

**工作逻辑**：
- 新增 `@Value.Lazy public String oauth2ServerUri()`：从 properties 读取 `OAUTH2_SERVER_URI`，默认 `ResourcePaths.tokens()`。
- 新增 import `org.apache.iceberg.rest.ResourcePaths`。
- 两个构造 `AuthSession` 的位置（携带 token 的初始化与仅凭 credential 的 `fromCredentialResponse` 回调）都改为 6 参数版本，传入 `oauth2ServerUri()`。
- `OAuth2Util.fetchToken(httpClient(), session.headers(), credential(), SCOPE)` 改为带 `oauth2ServerUri()` 的 5 参数版本。

### `core/src/test/java/org/apache/iceberg/rest/RESTCatalogAdapter.java`

**修改目的**：让测试用 RESTCatalogAdapter 能识别并响应外部授权服务器 URI，避免 mock 链路被打断。

**工作逻辑**：
- 在 `Route` 枚举中新增 `SEPARATE_AUTH_TOKENS_URI(HTTPMethod.POST, "https://auth-server.com/token", null, OAuthTokenResponse.class)`，与测试参数化用到的外部 URI 对应。
- 把原 `case TOKENS:` 内联的 grant_type 分发逻辑抽到独立静态方法 `handleOAuthRequest(Object body)`：根据 `grant_type` 分别处理 `client_credentials`（返回 `client-credentials-token:sub=<client_id>`）与 `urn:ietf:params:oauth:grant-type:token-exchange`（返回 `token-exchange-token:sub=<subject_token>[,act=<actor>]`）。`case TOKENS:` 现在直接 `return castResponse(responseType, handleOAuthRequest(body));`。
- 在 `handleRequest` 的 `default:` 分支中新增兜底：当 `responseType == OAuthTokenResponse.class` 时调用 `handleOAuthRequest(body)` 返回。这样 `SEPARATE_AUTH_TOKENS_URI` 这条新路由（没有显式 case）会被 default 分支捕获并正确响应。

### `core/src/test/java/org/apache/iceberg/rest/TestRESTCatalog.java`

**修改目的**：把所有涉及 token endpoint 的测试改造为参数化测试，同时验证默认路径 `v1/oauth/tokens` 与外部 URI `https://auth-server.com/token` 两种场景。

**工作逻辑**：
- 涉及测试：`testCatalogCredential`、`testCatalogBearerTokenWithClientCredential`、`testCatalogCredentialWithClientCredential`、`testCatalogBearerTokenAndCredentialWithClientCredential`、`testCatalogRefreshedTokenIsUsed`、`testCatalogExpiredBearerTokenIsRefreshedWithCredential`、`testCatalogTokenRefreshFailsAndUsesCredentialForRefresh`、`testCatalogWithCustomTokenScope`、`testCatalogTokenRefreshDisabledWithToken`、`testCatalogTokenRefreshDisabledWithCredential`。
- 每个测试加 `@ParameterizedTest` + `@ValueSource(strings = {"v1/oauth/tokens", "https://auth-server.com/token"})`，把硬编码的 `"v1/oauth/tokens"` 改为参数 `oauth2ServerUri`，并在 catalog.initialize 的 props 中加入 `OAuth2Properties.OAUTH2_SERVER_URI, oauth2ServerUri`。
- 把原 `testCatalogCredential` 拆分为 `testCatalogCredentialNoOauth2ServerUri`（保留旧行为：不显式配置 oauth2-server-uri，使用默认 v1/oauth/tokens）与参数化版本 `testCatalogCredential(String oauth2ServerUri)`。

这样既保证向后兼容性被显式覆盖，又验证新外部 URI 行为完全一致。

## 小结

本提交以最小侵入方式为 Iceberg REST Catalog 客户端引入了独立 OIDC 授权服务器支持：通过一个新的配置项 `oauth2-server-uri` + `HTTPClient.buildUri` 对绝对 URL 的识别 + `OAuth2Util`/`AuthSession` 全链路透传，使得企业部署中“授权服务器与 REST Catalog 分离”的常见场景得以支持，同时默认行为完全向后兼容（未配置时回退到 `v1/oauth/tokens`）。

**影响范围**：
- 客户端配置层：新增一个用户可配置项。
- 内部 API：`OAuth2Util.fetchToken/exchangeToken` 与 `AuthSession` 构造器签名变化，旧签名标 `@Deprecated`（1.5.0 加，1.6.0 移除）。
- HTTPClient 路径解析行为：现在 path 可以是绝对 URL，原有以 `/` 开头抛错的逻辑被前置且保留。
- 测试覆盖：所有 token 相关测试都参数化，新增外部 URI 路径覆盖。

**回迁到 1.4.x 注意事项**：
1. 这是 1.5.0 引入的特性，回迁到 1.4.x 需要同时回迁 6 个文件改动；`OAuth2Util` 旧签名已 `@Deprecated` 但未删除，回迁后可保留旧签名以避免破坏 1.4.x 既有调用方。
2. `HTTPClient.buildUri` 行为变化是行为级修改：原本 `path` 以 `/` 开头会在 `request` 方法中抛错，现在改在 `buildUri` 中抛错；正常调用不会感知差异，但若有 1.4.x 自定义 RESTClient 子类覆盖了 `buildUri` 或 `request`，需要确认行为一致。
3. 测试侧 `RESTCatalogAdapter` 新增了 `SEPARATE_AUTH_TOKENS_URI` 路由与 default 兜底，回迁时需一并带回，否则参数化测试中 `https://auth-server.com/token` 路径无法被 mock 响应。
4. 配置项 `oauth2-server-uri` 在 1.4.x 之前并不存在，回迁后属于新增配置，不冲突。
