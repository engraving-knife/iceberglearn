# 提交 1591：Auth Manager API part 3: OAuth2 Manager (#11844)

## 提交信息

- **序号**：1591
- **哈希**：a0777bca714fc16941dae25a3044445f3758dbd1
- **短哈希**：a0777bca7
- **日期**：2025-01-16（Thu Jan 16 17:44:27 2025 +0100）
- **作者**：Alexandre Dutra <adutra@users.noreply.github.com>
- **提交说明**：Auth Manager API part 3: OAuth2 Manager (#11844)
- **PR/Issue**：#11844

## 总体目的

本提交是 **Auth Manager API 重构的第三部分**，引入 OAuth2 认证管理器的完整实现 `OAuth2Manager`，以及配套的会话缓存 `AuthSessionCache`、可刷新管理器基类 `RefreshingAuthManager`，并把现有的 `OAuth2Util.AuthSession` 适配到新的 `AuthSession` 接口。

背景：Iceberg REST Catalog 此前的认证逻辑耦合在 `OAuth2Util.AuthSession` 中，难以扩展（如支持新的认证方式、定制 token 刷新策略、按上下文/表级别隔离会话）。社区正在分阶段引入一套 **AuthManager API**（本系列 part 1/2 已定义 `AuthManager`、`AuthSession` 接口及 `NoopAuthManager`、`BasicAuthManager`），把认证从 REST 客户端中解耦。本提交（part 3）补上最核心的 OAuth2 实现，使原有 OAuth2 能力在新框架下完整可用，包括：

- **三阶段会话生命周期**：`initSession`（初始化/预取 token，短期、不刷新）→ `catalogSession`（catalog 级长期会话，启用后台刷新）→ `contextualSession`/`tableSession`（按用户上下文或表级别派生子会话，支持 token 透传与 token exchange）。
- **多种凭据来源**：bearer token 直传、client credentials 流程换取 token、token exchange 流程（ID/Access/JWT/SAML2/SAML1 token 按优先级交换）。
- **后台 token 刷新**：通过 `RefreshingAuthManager` 提供的 `ScheduledExecutorService` 异步刷新即将过期的 token。
- **会话缓存与自动清理**：`AuthSessionCache` 基于 Caffeine，按空闲超时淘汰子会话并自动 `close`，避免泄露。
- **向后兼容的自动推断**：用户若未显式设置 `rest.auth.type` 但提供了 `token` 或 `credential`，自动推断为 OAuth2 并告警，平滑迁移。

这是 REST Catalog 认证子系统的重大重构落地，为后续支持更多认证方式（如 Kerberos、自定义）打下基础。

## 如何达成设计目的

整体由一个抽象基类 `RefreshingAuthManager`（提供刷新基础设施）、一个具体实现 `OAuth2Manager`（实现 OAuth2 各流程）、一个缓存 `AuthSessionCache`（管理子会话生命周期）组成，并辅以 `AuthConfig` 工厂方法、`AuthManagers` 加载逻辑、`OAuth2Util.AuthSession` 接口适配。下面分别说明。

### 修改详情

#### `core/src/main/java/org/apache/iceberg/rest/auth/RefreshingAuthManager.java`（新增，90 行）

**修改目的**：为需要后台刷新 token 的 AuthManager 提供公共基础设施。

**工作逻辑**：
- 抽象类，实现 `AuthManager`，持有 `executorNamePrefix`、`keepRefreshed` 标志、懒初始化的 `ScheduledExecutorService refreshExecutor`。
- `refreshExecutor()`：若 `keepRefreshed` 为 false 返回 null（不刷新）；否则双重检查锁懒创建一个单线程调度池（`ThreadPools.newScheduledPool`），子类用它调度 token 刷新任务。
- `keepRefreshed(boolean)`：允许子类在会话建立时开关刷新（如 init session 不刷新，catalog session 开启刷新）。
- `close()`：shutdownNow 刷新池，取消所有未完成的 Future，最多等 1 分钟终止，超时告警。这保证 manager 关闭时后台线程干净退出，不泄露。

#### `core/src/main/java/org/apache/iceberg/rest/auth/AuthSessionCache.java`（新增，140 行）

**修改目的**：缓存并按空闲超时淘汰 `AuthSession` 子会话，自动释放资源。

**工作逻辑**：
- 基于 Caffeine 缓存，`expireAfterAccess(sessionTimeout)`——会话在指定时长无访问后可被淘汰。
- 构造函数接收 `name`、`sessionTimeout`，内部创建专用淘汰执行器 `ThreadPools.newExitingWorkerPool(name + "-auth-session-evict", 1)`（单线程，JVM 退出时自动关闭）。
- `removalListener`：当会话被淘汰时调用 `auth.close()`，确保子会话的刷新任务停止、资源释放。
- `cachedSession(key, loader)`：`cache.get(key, loader)` 语义，命中返回缓存、未命中调 loader 加载并缓存。
- `sessionCache()`：双重检查锁懒初始化 Caffeine cache（可见性注解 `@VisibleForTesting`）。
- `close()`：`invalidateAll` + `cleanUp` 清空缓存（触发 removalListener 关闭所有会话），再关闭淘汰执行器（等 10 秒终止）。
- 提供测试构造函数接收自定义 `Executor` 与 `Ticker`，便于测试用虚拟时钟控制淘汰。

#### `core/src/main/java/org/apache/iceberg/rest/auth/OAuth2Manager.java`（新增，259 行）

**修改目的**：OAuth2 认证的完整实现。

**核心字段与流程**：
- `TOKEN_PREFERENCE_ORDER`：token exchange 时按 `ID_TOKEN` > `ACCESS_TOKEN` > `JWT` > `SAML2` > `SAML1` 优先级选取。
- `TABLE_SESSION_ALLOW_LIST`：表级会话允许透传的属性集合（`TOKEN` + 各 token 类型），防止无关属性泄露到表会话。
- 持有 `client`（RESTClient）、`startTimeMillis`、`authResponse`（init 阶段换取的 token 响应）、`sessionCache`。

**`initSession(initClient, properties)`**：
- 从 properties 构建 `AuthConfig`，构造初始 `AuthSession`（headers 来自 token）。
- 若提供 credential：调 `OAuth2Util.fetchToken` 走 client_credentials 流程换 token，记录 `startTimeMillis` 与 `authResponse`，返回 `fromTokenResponse` 构建的会话——**但不启用刷新**（init 是短生命周期会话），仅记录起始时间供后续 catalogSession 复用。
- 若仅提供 token：`fromAccessToken` 构建会话，同样不刷新。
- 都没有：返回空 headers 会话。

**`catalogSession(sharedClient, properties)`**：
- 保存 `client`、创建 `sessionCache`（超时来自 `AUTH_SESSION_TIMEOUT_MS`）。
- `keepRefreshed(config.keepRefreshed())`：根据配置开关刷新。
- 若 init 阶段已换取 `authResponse`：复用它 + `refreshExecutor()` 构建可刷新会话（避免重复换 token）。
- 否则若有 credential：现换 token 再构建可刷新会话。
- 若仅有 token：`fromAccessToken` 带 `expiresAtMillis` 构建可刷新会话。
- 都没有：返回空会话。

**`contextualSession(context, parent)`**：从 `SessionContext` 的 credentials/properties 派生子会话，用 `context.sessionId()` 作缓存 key。

**`tableSession(table, properties, parent)`**：从表属性派生子会话，但先用 `Maps.filterKeys` 仅保留 `TABLE_SESSION_ALLOW_LIST` 中的属性作为 credentials，用属性本身作 properties，缓存 key 取属性值。

**`maybeCreateChildSession(...)`**：子会话创建的核心调度，按凭据类型分支：
1. 有 `TOKEN`：直接用 bearer token（不交换），缓存 key 为 token 类型。
2. 有 `CREDENTIAL`：走 client_credentials 流程换 token。
3. 否则按 `TOKEN_PREFERENCE_ORDER` 找到第一个提供的 token 类型，走 token exchange 流程。
4. 都没有：返回 parent（不创建子会话）。
所有分支都用 `sessionCache.cachedSession` 缓存，相同 key 只创建一次。

**`warnIfDeprecatedTokenEndpointUsed`**：若 OAuth2 server URI 未显式配置或与 catalog URI 同源（相对路径或同 host），且提供了 credential/token，则告警提示该自动回退将来会移除，引导用户显式配置 `OAUTH2_SERVER_URI`（关联 issue #10537）。

**`close()`**：先 `super.close()`（关刷新池），再关 `sessionCache`。

#### `core/src/main/java/org/apache/iceberg/rest/auth/AuthConfig.java`

**修改目的**：支持从 properties 构建 AuthConfig，并允许 expiresAtMillis 在 builder 中覆盖。

**主要变更**：
- `expiresAtMillis()` 从 `@Value.Lazy`（不可覆盖）改为 `@Value.Default`（可在 builder 设置）。
- 新增 `static AuthConfig fromProperties(Map)`：从 properties 读取 credential、token、scope、oauth2ServerUri（默认 `ResourcePaths.tokens()`）、optionalOAuthParams、keepRefreshed（默认 true）、expiresAtMillis。
- 新增私有 `expiresAtMillis(Map)`：优先从 token 解析过期时间；若无则从 `TOKEN_EXPIRES_IN_MS` 计算 `now + millis`。
- 增加 `@SuppressWarnings("SafeLoggingPropagation")` 注解（与 Immutables 的安全日志相关）。

#### `core/src/main/java/org/apache/iceberg/rest/auth/AuthManagers.java`

**修改目的**：支持 OAuth2 类型加载与自动推断。

**主要变更**：
- `loadAuthManager` 中，若 `AUTH_TYPE` 未设置：
  - 若有 `CREDENTIAL` 或 `TOKEN`：告警并推断为 `oauth2`（"Inferring rest.auth.type=oauth2 since property ... was provided. Please explicitly set rest.auth.type to avoid this warning."）。
  - 否则默认 `none`。
- switch 新增 `case AUTH_TYPE_OAUTH2: impl = AUTH_MANAGER_IMPL_OAUTH2;`。

#### `core/src/main/java/org/apache/iceberg/rest/auth/AuthProperties.java`

**修改目的**：新增 OAuth2 类型常量。

新增：
- `AUTH_TYPE_OAUTH2 = "oauth2"`
- `AUTH_MANAGER_IMPL_OAUTH2 = "org.apache.iceberg.rest.auth.OAuth2Manager"`

#### `core/src/main/java/org/apache/iceberg/rest/auth/OAuth2Util.java`

**修改目的**：让 `AuthSession` 适配新的 `AuthSession` 接口。

**主要变更**：
- `AuthSession` 改为 `implements org.apache.iceberg.rest.auth.AuthSession`（新接口）。
- 构造函数改名参数 `baseHeaders`→`headers`，且**不再在构造时 merge authHeaders**，而是直接 `ImmutableMap.copyOf(headers)` 接收已构建好的 headers。这把 header 合并职责上移到调用方（`fromAccessToken`/`fromTokenResponse`），使 AuthSession 更纯粹。
- 新增 `authenticate(HTTPRequest)`：实现新接口方法，把会话 headers 通过 `putIfAbsent` 加到请求上（不覆盖请求已有的头），返回新请求。这统一了认证注入入口。
- 新增 `close()`：实现 AutoCloseable，调 `stopRefreshing()` 停止后台刷新。
- `fromAccessToken` / `fromTokenResponse`：创建子会话时，改为 `RESTUtil.merge(parent.headers(), authHeaders(token))`——把父会话 headers 与新 token 的 auth headers 合并后再传入构造函数（弥补构造函数不再 merge 的变化）。

#### 测试

- `TestAuthManagers.java`：新增 `oauth2Explicit`（显式 oauth2 类型）、`oauth2InferredFromToken`、`oauth2InferredFromCredential` 三个测试，验证加载与告警。
- `TestAuthSessionCache.java`（新增，91 行）：验证缓存命中/未命中、close 时会话被关闭、空闲超时淘汰触发 `session.close()`（用 AtomicLong ticker 模拟时间）。
- `TestOAuth2Manager.java`（新增，529 行）：全面覆盖 initSession/catalogSession/contextualSession/tableSession 各组合（无属性、token、credential、refresh 禁用、init 与不 init 等），用 mock RESTClient 验证 token 交换请求与 headers。

## 小结

- **成效**：完成了 Auth Manager API 的 OAuth2 实现，REST Catalog 的 OAuth2 认证能力在新框架下完整可用，并具备了此前缺失或散落的特性：三阶段会话生命周期、按上下文/表隔离的子会话缓存与自动清理、统一的后台刷新基础设施、token exchange 多类型支持、向后兼容的 auth 类型自动推断。这是认证子系统模块化重构的关键一步，为后续扩展（自定义 AuthManager、新认证方式）奠定基础。
- **影响范围**：`core` 模块的 `rest.auth` 包，新增 3 个类（OAuth2Manager、AuthSessionCache、RefreshingAuthManager）共约 490 行生产代码，修改 AuthConfig/AuthManagers/AuthProperties/OAuth2Util 4 个类，新增/扩展 3 个测试类共约 660 行。这是较大规模的重构，但通过自动推断与接口适配保持了向后兼容。
- **回迁到 1.4.x 的注意事项**：这是一个**大型重构的第三部分**，依赖 part 1/2（`AuthManager`/`AuthSession` 接口、`NoopAuthManager`、`BasicAuthManager`）已落地。回迁到 1.4.x 需**整体回迁 part 1-3**，单独回迁本提交无法编译/工作。考虑 1.4.x 作为维护分支的稳定性，建议谨慎评估：
  1. 若 1.4.x 已回迁 part 1/2，则本提交应一并回迁以补全 OAuth2 能力。
  2. 若 1.4.x 未启动 Auth Manager 重构，则**不建议**为回迁本提交而引入整套重构——维护分支通常避免大规模架构变更。1.4.x 可继续使用原有 `OAuth2Util.AuthSession` 路径。
  3. 需特别注意 `OAuth2Util.AuthSession` 构造函数行为变化（不再内部 merge headers）对 1.4.x 中既有调用方的影响，若部分回迁需手工核对所有调用点。
  4. `AuthConfig.fromProperties` 与 `AuthManagers` 自动推断逻辑是用户可见行为变化（未设 auth.type 但提供 token/credential 会告警），回迁需在 release notes 中说明。
