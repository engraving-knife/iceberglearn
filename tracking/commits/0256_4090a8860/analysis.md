# 提交 0256：Core: Add REST catalog table session cache (#8920)

## 提交信息

- **序号**：0256 / 4088
- **哈希**：4090a8860061f58748e3faa6804094f90d3575f3
- **短哈希**：4090a886
- **日期**：2023-12-10
- **作者**：Eduard Tudenhoefner
- **提交说明**：Core: Add REST catalog table session cache (#8920)
- **PR/Issue**：#8920

## 总体目的

在 Iceberg 的 REST Session Catalog 中，每一个被加载的表都可能携带自己的认证凭据（token / credential / exchange token），从而衍生出一个属于该表的 `AuthSession`。在本次提交之前，`tableSession(...)` 每次被调用时都会通过 `newSession(...)` 重新构建一个 `AuthSession`，并且这些会话会被纳入 catalog 级别的 token 刷新机制中持续刷新——即便该表早已不再被使用。这意味着大量已加载但不再访问的表会持续触发 OAuth token 的刷新请求，给 REST 服务端带来无谓的负担，也造成客户端不必要的网络与计算开销。

本次提交的核心目的是为「表级 auth session」引入独立的缓存（`tableSessions`），使得相同的凭据可以复用同一个 `AuthSession` 实例，并借助 Caffeine 缓存本身的过期/淘汰能力，让那些不再被引用的表级 session 能够随缓存淘汰而停止刷新。提交说明原文也点明了动机：缓存表级 auth session 的主要目的，是当某个表不再被使用时能够停止刷新它的 session。

此外，本次改动还顺手统一了 catalog 级 session 与表级 session 的构造路径：`newSession(...)` 的返回值由直接返回 `AuthSession` 改为返回 `Pair<String, Supplier<AuthSession>>`，把「缓存键」与「惰性构造 session 的工厂」一并暴露出来，让两条调用路径都能复用同一套凭据解析逻辑。

## 如何达成设计目的

整体设计思路是：把 `AuthSession` 的创建从「立即执行」改为「惰性工厂 + 缓存键」，然后引入一张独立的 `tableSessions` Caffeine 缓存来持有表级 session。`newSession(...)` 负责解析凭据类型并返回 `(key, supplier)`，其中 key 取自凭据本身（bearer token / client credential / exchange token 的原始字符串），supplier 在被调用时才真正发起网络请求换取/构造 `AuthSession`。catalog 级的 `session(...)` 与表级的 `tableSession(...)` 都改为先用 key 查缓存，命中即复用，未命中才触发 supplier。这样既避免了重复换 token，也让不再被引用的表级 session 通过缓存淘汰自然终止刷新。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/RESTSessionCatalog.java`

**修改目的**：新增表级 auth session 缓存，并将 `newSession(...)` 重构为返回缓存键 + 惰性工厂，使 catalog 级与表级 session 共享同一套凭据解析与缓存复用逻辑。

**工作逻辑**：

1. 新增字段 `private Cache<String, AuthSession> tableSessions = null;`（[RESTSessionCatalog.java](../core/src/main/java/org/apache/iceberg/rest/RESTSessionCatalog.java)），并在 `initialize(...)` 中通过 `this.tableSessions = newSessionCache(mergedProps);` 与 `sessions` 用同一套配置初始化，保证两个缓存具备相同的容量与过期策略。

2. `newSession(...)` 的签名由 `AuthSession newSession(...)` 改为 `Pair<String, Supplier<AuthSession>> newSession(...)`，并在 import 中新增 `org.apache.iceberg.util.Pair`。对三种凭据分支分别返回：
   - `OAuth2Properties.TOKEN`：以 token 字符串为 key，supplier 调用 `AuthSession.fromAccessToken(...)`；
   - `OAuth2Properties.CREDENTIAL`：以 credential 字符串为 key，supplier 调用 `AuthSession.fromCredential(...)`（走 client credentials flow）；
   - 其它 token 类型：以原始 token 为 key，supplier 调用 `AuthSession.fromTokenExchange(...)`（走 token exchange flow）。
   当 `credentials == null` 时返回 `null`，表示该上下文没有可用凭据。

3. catalog 级 `session(...)` 调整：原本 `sessions.get(context.sessionId(), id -> newSession(...))` 直接把 `newSession` 的结果放入缓存；现在改为先调用 `newSession(...)` 拿到 `Pair`，若非 null 则用 `newSession.second().get()` 取实际 session 放入以 `context.sessionId()` 为键的 `sessions` 缓存，否则返回 `null`，最终回退到 `catalogAuth`。这里 `sessions` 仍以 sessionId 为键，但 session 的构造走了新的工厂接口。

4. 表级 `tableSession(...)` 调整：原先直接 `AuthSession session = newSession(tableConf, tableConf, parent);`；现在先取 `Pair`，若为 `null`（无凭据）则直接返回 `parent`，否则以 `newSession.first()`（即凭据字符串）为键查询 `tableSessions` 缓存，未命中时调用 `newSession.second().get()`。这是本次的核心改动：相同凭据的表将共享同一个 `AuthSession`，且不再使用时随缓存淘汰停止刷新。

### `core/src/test/java/org/apache/iceberg/rest/TestRESTCatalog.java`

**修改目的**：适配表级 session 被缓存后的行为变化——同一表凭据不再重复换取 token。

**工作逻辑**：在加载表并随后操作表的测试场景中，原先会触发两次 `v1/oauth/tokens` 的 POST 请求（一次取表 token，后续可能再次刷新）；由于表级 session 现在被缓存复用，验证从 `times(2)` 调整为 `times(1)`：

```java
Mockito.verify(adapter, times(1))
    .execute(eq(HTTPMETHOD.POST), eq("v1/oauth/tokens"), ...);
```

## 小结

通过为表级 auth session 引入独立 Caffeine 缓存并将 session 构造改造为「缓存键 + 惰性工厂」，本提交让不再使用的表级 token 随缓存淘汰自然停止刷新，显著降低了 REST Catalog 在多表场景下的无谓 OAuth 流量。
