# 提交 1865：AWS, Core, GCP: Auth Manager API enablement (#12197)

## 提交信息

- **序号**：1865 / 4088
- **哈希**：4816bf3b5ad68f1c9c2f98c55b56956e9449f178
- **短哈希**：4816bf3b5
- **日期**：2025-03-17 12:20:50 +0100
- **作者**：Alexandre Dutra
- **提交说明**：AWS, Core, GCP: Auth Manager API enablement (#12197)
- **PR/Issue**：#12197

## 总体目的

Iceberg 此前的认证（auth）逻辑散落在多处，每处都各自实现 OAuth2 token 获取、刷新、会话缓存、sigv4 签名等：

1. `RESTSessionCatalog`（core）：内嵌完整的 OAuth2 会话管理——token 缓存（Caffeine）、刷新线程池（`ScheduledExecutorService`）、credential→token 交换、token 刷新调度等，几百行代码混在 catalog 主类中。
2. `S3V4RestSignerClient`（aws）：S3 REST 签名器自己又实现了一套 token 缓存与刷新逻辑，与 catalog 侧重复。
3. `VendedCredentialsProvider`（aws）：S3 vended credentials 刷新也自己构造 `DefaultAuthSession`。
4. `OAuth2RefreshCredentialsHandler`（gcp）：GCS 凭证刷新同样自行构造 session。
5. `HTTPClient`（core）：通过反射动态加载 `RESTSigV4Signer` 作为 `HttpRequestInterceptor`，这是 sigv4 的老式集成方式。

这种"各自为政"导致：认证逻辑重复、难以统一维护、刷新线程池无法共享、bug 修需要多处同步。main 分支此前已引入 `AuthManager` 接口（`core/src/main/java/org/apache/iceberg/rest/auth/AuthManager.java`）作为统一的认证抽象，本提交把上述四处（REST catalog、S3 signer、S3 vended creds、GCS refresh handler）全部迁移到 `AuthManager` API，删除各自的 OAuth2/缓存/刷新逻辑，实现认证管理的统一。

## 如何达成设计目的

1. **扩展 `AuthManager` 接口**：新增 `default AuthSession tableSession(RESTClient, Map)` 方法（默认委托给 `catalogSession`），供非 catalog 组件（如 S3 signer）获取表级会话。
2. **`OAuth2Manager` 改造**：把内部 `RESTClient client` 重命名为 `refreshClient`，并在 `catalogSession` 中用 `sharedClient.withAuthSession(AuthSession.EMPTY)` 构造一个"无 auth session"的 client 专门用于 token 刷新（避免刷新请求自身携带过期 token）。`initSession` 时用 `keepRefreshed(false)` 禁止初始化阶段触发刷新。`catalogSession` 中 token 与 credential 的优先级调整为 token 优先（原来 credential 优先）。
3. **`RESTSessionCatalog` 大幅瘦身**：删除内嵌的 `sessions`/`tableSessions` Caffeine 缓存、`refreshExecutor` 线程池、`TOKEN_PREFERENCE_ORDER`/`TABLE_SESSION_ALLOW_LIST`、`newSessionCache` 等大量 OAuth2 管理代码（-656/+... 行净减）。改为持有 `AuthManager authManager`，通过 `AuthManagers.loadAuthManager(name, props)` 加载，认证逻辑全部委托给 `authManager`。
4. **`S3V4RestSignerClient` 改造**：删除自有的 `tokenRefreshExecutor`、`authSessionCache`、`authSession()` 中的 token/credential 分支逻辑，改为 `AuthManagers.loadAuthManager("s3-signer", properties())` + `authManager.tableSession(httpClient(), properties)`。实现 `AutoCloseable`，`close()` 时关闭 `authSession` 与 `authManager`。
5. **`VendedCredentialsProvider` 改造**：用 `AuthManagers.loadAuthManager("s3-credentials-refresh", properties)` + `authManager.catalogSession(httpClient, properties)` 替代自建 `DefaultAuthSession`。
6. **`OAuth2RefreshCredentialsHandler` 改造**：同样用 `AuthManager` 管理会话，实现 `AutoCloseable`，`close()` 用 `CloseableGroup` 统一关闭 `authSession`/`authManager`/`client`。
7. **`GCSFileIO`**：把 `refreshHandler` 提升为字段，`close()` 时关闭。
8. **`HTTPClient`**：删除 `loadInterceptorDynamically` 与 `requestInterceptor` 参数——sigv4 不再通过 HTTPClient 的请求拦截器集成，改由 `AuthManager` 体系（`RESTSigV4AuthManager`）处理。`RESTSigV4Signer` 标记 `@Deprecated`（1.10.0 移除）。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/auth/AuthManager.java` (修改, +10 lines)

**修改目的**：新增 `tableSession` 默认方法，供非 catalog 组件获取表级会话。

**工作逻辑**：`default AuthSession tableSession(RESTClient sharedClient, Map<String, String> properties) { return catalogSession(sharedClient, properties); }`。默认委托给 `catalogSession`，子类（如支持表级 token 的实现）可覆盖。

### `core/src/main/java/org/apache/iceberg/rest/auth/OAuth2Manager.java` (修改, +21/-16 lines)

**修改目的**：修正 token 刷新 client 的 auth session 隔离，调整 token/credential 优先级。

**工作逻辑**：
- `client` → `refreshClient`，`catalogSession` 中 `this.refreshClient = sharedClient.withAuthSession(AuthSession.EMPTY)`（刷新专用 client 不携带 auth session，避免循环）。
- `initSession` 用 `ImmutableAuthConfig.builder().from(AuthConfig.fromProperties(properties)).keepRefreshed(false).build()` 禁止初始化时刷新。
- `fetchToken` 调用改为 `initClient.withAuthSession(session)` + `Map.of()` headers（session 自带 headers）。
- `catalogSession` 中优先级改为：有 `authResponse` → 用之；否则有 `token` → `fromAccessToken`；否则有 `credential` → `fetchToken` + `fromTokenResponse`。
- `newSessionFromToken`/`newSessionFromCredential`/`newSessionFromTokenExchange` 全部用 `refreshClient`。

### `core/src/main/java/org/apache/iceberg/rest/RESTSessionCatalog.java` (修改, +大幅重构, -656/+... 行)

**修改目的**：把 OAuth2 会话管理委托给 `AuthManager`，移除内嵌缓存/线程池。

**工作逻辑**：
- 删除 `sessions`/`tableSessions`/`keepTokenRefreshed`/`refreshExecutor`/`TOKEN_PREFERENCE_ORDER`/`TABLE_SESSION_ALLOW_LIST` 等字段与常量。
- 新增 `private AuthManager authManager`，`initialize` 中 `this.authManager = AuthManagers.loadAuthManager(name, props)`。
- `initialize` 大幅简化：用 `authManager.initSession(initClient, props)` + `fetchConfig(initClient.withAuthSession(initSession), initSession, props)` 替代原先的 `DefaultAuthSession`/`fetchToken`/`authResponse` 流程。
- `catalogAuth` 改为 `authManager.catalogSession(client, mergedProps)`。
- 表级会话通过 `authManager.tableSession(...)` / `authManager.session(context, props)` 获取。
- 删除 `newSessionCache`/`refreshExecutor`/`newSession` 等私有方法。
- 净减约 350 行代码。

### `core/src/main/java/org/apache/iceberg/rest/HTTPClient.java` (修改, -53 lines)

**修改目的**：移除 sigv4 请求拦截器的动态加载机制。

**工作逻辑**：删除 `SIGV4_ENABLED`/`SIGV4_REQUEST_INTERCEPTOR_IMPL` 常量、构造函数的 `HttpRequestInterceptor requestInterceptor` 参数、`loadInterceptorDynamically` 方法、`addRequestInterceptorLast` 调用。sigv4 改由 `AuthManager` 体系处理。

### `aws/src/main/java/org/apache/iceberg/aws/RESTSigV4Signer.java` (修改, +3 lines)

**修改目的**：标记老式 sigv4 签名器为 deprecated。

**工作逻辑**：类上加 `@Deprecated`，javadoc 注明 "since 1.9.0, will be removed in 1.10.0; use RESTSigV4AuthManager instead"。

### `aws/src/main/java/org/apache/iceberg/aws/s3/VendedCredentialsProvider.java` (修改, +18/-9 lines)

**修改目的**：用 `AuthManager` 管理 S3 vended credentials 刷新的认证。

**工作逻辑**：新增 `authManager`/`authSession` 字段；`client` 初始化改为 `authManager = AuthManagers.loadAuthManager("s3-credentials-refresh", properties); HTTPClient httpClient = HTTPClient.builder(properties).uri(...).build(); authSession = authManager.catalogSession(httpClient, properties); client = httpClient.withAuthSession(authSession);`。`close()` 改用 `IoUtils.closeQuietlyV2` 关闭 `authSession`/`authManager`/`client`/`credentialCache`。

### `aws/src/main/java/org/apache/iceberg/aws/s3/signer/S3V4RestSignerClient.java` (修改, +大幅重构, -130/+40 行)

**修改目的**：用 `AuthManager` 替代自有的 token 缓存与刷新逻辑。

**工作逻辑**：
- 删除 `tokenRefreshExecutor`/`authSessionCache` 与相关懒加载方法。
- `authSession()` 改为：`authManager = AuthManagers.loadAuthManager("s3-signer", properties())`，构造 properties（含 `OAUTH2_SERVER_URI`/`SCOPE`/`TOKEN`/`CREDENTIAL`），`authSession = authManager.tableSession(httpClient(), properties.buildKeepingLast())`。
- 实现 `AutoCloseable`，`close()` 关闭 `authSession`/`authManager`。
- 删除 `expiresAtMillis` 方法（由 AuthManager 处理）。

### `gcp/src/main/java/org/apache/iceberg/gcp/gcs/GCSFileIO.java` (修改, +5/-1 lines)

**修改目的**：持有 `refreshHandler` 引用以便关闭。

**工作逻辑**：新增 `private OAuth2RefreshCredentialsHandler refreshHandler` 字段；`delegate()` 中 `refreshHandler = OAuth2RefreshCredentialsHandler.create(properties)` 并设为 refresh handler；`close()` 中 `if (refreshHandler != null) refreshHandler.close()`。

### `gcp/src/main/java/org/apache/iceberg/gcp/gcs/OAuth2RefreshCredentialsHandler.java` (修改, +48/-22 lines)

**修改目的**：用 `AuthManager` 管理会话并实现 `AutoCloseable`。

**工作逻辑**：新增 `client`/`authManager`/`authSession` 字段；`httpClient()` 懒加载改为 `authManager = AuthManagers.loadAuthManager("gcs-credentials-refresh", properties); ...; authSession = authManager.catalogSession(httpClient, properties); client = httpClient.withAuthSession(authSession);`。实现 `close()` 用 `CloseableGroup` 关闭三者。

### 测试文件（4 个）

- `TestRESTSigV4Signer.java`、`TestS3RestSigner.java`：适配 deprecated 签名器。
- `TestS3V4RestSignerClient.java`（新增 129 行）：S3V4RestSignerClient 的测试。
- `TestHTTPClient.java`：移除 `loadInterceptorDynamically` 相关测试。
- `TestOAuth2Manager.java`：适配 OAuth2Manager 的 token/credential 优先级与刷新 client 改动。

## 小结

- **成效**：把分散在 REST catalog、S3 signer、S3 vended creds、GCS refresh handler 四处的 OAuth2 认证逻辑统一到 `AuthManager` API，删除大量重复代码（RESTSessionCatalog 净减约 350 行），刷新线程池与会话缓存由 AuthManager 统一管理。sigv4 集成方式从 HTTPClient 请求拦截器迁移到 AuthManager 体系，老式 `RESTSigV4Signer` 标记 deprecated。
- **影响范围**：跨 aws/core/gcp 三模块，14 个文件、+603/-713 行（净减 110 行）。属于架构重构，影响 REST catalog、S3 REST signer、S3 vended credentials、GCS OAuth2 refresh 的认证路径。
- **回迁到 1.4.x 的注意事项**：**不建议直接回迁**。本提交依赖 main 分支已引入的 `AuthManager`/`AuthManagers`/`RESTSigV4AuthManager` 等基础设施（1.4.x 的 `RESTSessionCatalog` 仍是内嵌 OAuth2 管理）。回迁需先把整个 `AuthManager` 体系移植到 1.4.x，工作量巨大且风险高。建议 1.4.x 维持现有认证实现，待大版本升级时整体迁移。若 1.4.x 已部分引入 AuthManager，则可评估按文件分步回迁。
