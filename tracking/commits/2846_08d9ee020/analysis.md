# 提交 2846：AWS, S3 Signing: Fix leaked credentials when contacting multiple catalogs (#14178)

## 提交信息

- **序号**：2846 / 4088
- **哈希**：08d9ee02092f6fd59f8103c1c4de1a7f1e9fa1fe
- **短哈希**：08d9ee020
- **日期**：2025-11-07 14:57:55 -0800
- **作者**：Alexandre Dutra
- **提交说明**：AWS, S3 Signing: Fix leaked credentials when contacting multiple catalogs (#14178)
- **PR/Issue**：#14178（修复 #14100）

## 总体目的

Iceberg 的 S3 REST 签名客户端（`S3V4RestSignerClient`）通过 OAuth2 客户端凭证流从认证服务器换取 token，再用 token 签名 S3 请求。`OAuth2Manager` 负责管理 token 的获取与刷新，并维护一个 `AuthSessionCache`，按"凭证（credential 或 token）"作为 key 缓存已建立的会话，避免重复换取 token。

Issue #14100 报告：当同一个 `S3V4RestSignerClient` 实例（或共享的 `HTTPClient`/`OAuth2Manager`）需要联系**多个不同的 catalog**（即多个认证服务器 / 多个 OAuth2 server URI）时，会发生**凭证泄漏**——一个 catalog 的凭证被错误地用于另一个 catalog。根因有两个：

1. **sessionCache 的 key 只用了凭证本身**（`config.token()` 或 `config.credential()`），没有包含 OAuth2 server URI。当两个 catalog 用相同凭证但不同 auth server 时，第二个 catalog 会命中第一个 catalog 的缓存会话，导致用错 token/会话，进而把凭证发给错误的 server，造成泄漏。
2. **`S3V4RestSignerClient` 的 `endpoint`/`oauth2ServerUri`/`httpClient` 处理不当**：`HTTPClient` 构建时绑定了 `baseSignerUri` 作为 base URI，但该 client 实际会被用于联系不同 catalog 的不同 base URI；同时 `oauth2ServerUri` 默认值 `ResourcePaths.tokens()` 是相对路径，没有相对 `baseSignerUri` 解析，导致 token 请求发到错误地址。

该提交修复这两个根因：(a) sessionCache key 加入 OAuth2 server URI；(b) `S3V4RestSignerClient` 用 `RESTUtil.resolveEndpoint` 正确解析相对 endpoint，HTTPClient 不再绑定 base URI 以支持多 catalog；(c) `OAuth2Manager` 的 `refreshClient`/`sessionCache` 改为 `volatile` + 双重检查锁，保证多线程安全初始化。

## 如何达成设计目的

1. **`OAuth2Manager.tableSession`**：
   - 把 `refreshClient` 与 `sessionCache` 字段改为 `volatile`。
   - 初始化 `refreshClient`/`sessionCache` 时用双重检查锁（`synchronized(this)` + 再次 null 检查），保证多线程下只初始化一次。
   - 计算 `oauth2ServerUri`（从 properties 取，默认 `ResourcePaths.tokens()`）。
   - sessionCache 的 key 由单纯的 `config.token()`/`config.credential()` 改为 `oauth2ServerUri + ":" + config.token()` / `oauth2ServerUri + ":" + config.credential()`，确保不同 auth server 的相同凭证不会命中同一缓存项。
2. **`S3V4RestSignerClient`**：
   - `endpoint()`：用 `RESTUtil.resolveEndpoint(baseSignerUri(), properties().getOrDefault(S3_SIGNER_ENDPOINT, S3_SIGNER_DEFAULT_ENDPOINT))` 把相对 endpoint 解析为绝对（相对 `baseSignerUri`）。
   - `oauth2ServerUri()`：先取 `oauth2ServerUri`，若已是 `http` 开头（绝对）直接用，否则用 `RESTUtil.resolveEndpoint(baseSignerUri(), oauth2ServerUri)` 解析。
   - `httpClient()`：构建 `HTTPClient` 时**不再 `.uri(baseSignerUri())`**，因为该 client 可能用于联系不同 catalog（不同 base URI）。注释明确说明这一点。
3. **测试**：新增 `standaloneTableSessionCredentialProvidedMultipleAuthServers`，模拟两个表属性集（一个用默认 token endpoint，一个用显式 `https://auth-server2.com/v1/token`），均用相同 `client:secret` 凭证，断言两次 `tableSession` 返回**不同**的会话对象（`isNotSameAs`），且各自正确发起 token 请求；调整原测试断言文案。

## 修改详情

### `aws/src/main/java/org/apache/iceberg/aws/s3/signer/S3V4RestSignerClient.java` (+10/-7 lines)

**修改目的**：正确解析相对 endpoint，并让共享 HTTPClient 支持多 catalog。

**工作逻辑**：
- `endpoint()` 改用 `RESTUtil.resolveEndpoint(baseSignerUri(), ...)`：若 endpoint 是绝对 URL 直接返回，否则拼接到 `baseSignerUri`。
- `oauth2ServerUri()`：取 `oauth2ServerUri`，若 `startsWith("http")` 直接用，否则 `RESTUtil.resolveEndpoint(baseSignerUri(), oauth2ServerUri)`。
- `httpClient()`：`HTTPClient.builder(properties()).withObjectMapper(S3ObjectMapper.mapper()).build()`，去掉 `.uri(baseSignerUri())`。注释说明该 client 可能联系多个 catalog，不应绑定单一 base URI。

### `core/src/main/java/org/apache/iceberg/rest/auth/OAuth2Manager.java` (+22/-7 lines)

**修改目的**：修复多 auth server 下的会话缓存命中错误与多线程初始化竞态。

**工作逻辑**：
- `refreshClient`、`sessionCache` 改为 `volatile`。
- `tableSession` 中：`refreshClient == null` 时进 `synchronized(this)` 双重检查初始化；`sessionCache == null` 同理。
- 计算 `oauth2ServerUri = properties.getOrDefault(OAuth2Properties.OAUTH2_SERVER_URI, ResourcePaths.tokens())`。
- token 分支：`cacheKey = oauth2ServerUri + ":" + config.token()`，用 `sessionCache.cachedSession(cacheKey, ...)`。
- credential 分支：`cacheKey = oauth2ServerUri + ":" + config.credential()`，用 `sessionCache.cachedSession(cacheKey, ...)`。
- 注释强调该方法可能被多线程调用，必须同步 refresh client 与 session cache。

### `core/src/test/java/org/apache/iceberg/rest/auth/TestOAuth2Manager.java` (+33/-1 lines)

**修改目的**：验证多 auth server 下相同凭证不会复用同一会话。

**工作逻辑**：
- `standaloneTableSessionCredentialProvidedMultipleAuthServers`：
  - `tableProperties1`：只有 `CREDENTIAL=client:secret`（默认 token endpoint）。
  - `tableProperties2`：`OAUTH2_SERVER_URI=https://auth-server2.com/v1/token` + `CREDENTIAL=client:secret`。
  - 调用 `manager.tableSession(client, ...)` 两次，断言两个 `tableSession` 的 `Authorization` 头都是 `Bearer test`，但 `tableSession1` 与 `tableSession2` **不是同一对象**（`isNotSameAs`）。
  - 验证 `client.withAuthSession` 调用一次，`client.postForm` 调用两次（两个 auth server 各一次 token 请求），无多余交互。
- 另调整一处现有测试的断言文案（`should create session cache for table with token` → `should create session cache for empty table properties`）。

## 总结

该提交修复了 Iceberg S3 REST 签名客户端在联系多个 catalog（多 auth server）时的凭证泄漏问题。根因是 `OAuth2Manager` 的会话缓存 key 只用凭证、不含 OAuth2 server URI，导致不同 catalog 用相同凭证时复用错会话；同时 `S3V4RestSignerClient` 的 HTTPClient 绑定了单一 base URI、endpoint 解析未相对化。修复包括：缓存 key 加入 auth server URI、`refreshClient`/`sessionCache` 用 volatile + 双重检查锁保证线程安全、endpoint 用 `RESTUtil.resolveEndpoint` 正确解析、HTTPClient 不再绑定 base URI。新增测试覆盖"相同凭证 + 不同 auth server 应得到不同会话"的场景。这是一个重要的安全修复。
