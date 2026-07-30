# 提交 2302：REST: Revert #12818 and additionally stop retrying on 502/504 (#13352)

## 提交信息

- **序号**：2302 / 4088
- **哈希**：b1c8bc589e0caae3d0a1649e354c44d0fb23759c
- **短哈希**：b1c8bc589
- **日期**：2025-07-01 13:10:30 -0500
- **作者**：Prashant Singh
- **提交说明**：REST: Revert #12818 and additionally stop retrying on 502/504 (#13352)
- **PR/Issue**：#13352

## 总体目的

本提交回退了 PR #12818 的所有变更，并额外停止对 HTTP 502（Bad Gateway）和 504（Gateway Timeout）状态码的重试。

PR #12818 曾引入了以下变更：
1. 将 502 和 504 添加到可重试状态码列表
2. 在 HTTP 请求上下文中跟踪请求是否被重试（`was-retried` 属性）
3. 当重试后的请求返回 409（Conflict）时，抛出 `CommitStateUnknownException` 而非 `CommitFailedException`，因为重试可能导致服务端已经持久化了提交
4. 在 `ErrorResponse` 中添加 `wasRetried` 字段

这些变更的初衷是处理 REST Catalog 服务在 5xx 错误后重试可能导致的状态不一致问题。然而，实践中发现这些变更引入了复杂性和潜在问题：
- 502 和 504 通常表示网关/代理层的问题，重试可能加剧问题而非解决
- `wasRetried` 跟踪逻辑增加了代码复杂度，且对 409 的特殊处理可能掩盖真正的冲突问题

因此决定回退到更简单、更保守的重试策略。

## 如何达成设计目的

回退操作包括：

1. **移除 502/504 从可重试状态码**：只保留 429（Too Many Requests）和 503（Service Unavailable）作为可重试状态码
2. **移除 `was-retried` 上下文跟踪**：不再在 HTTP 上下文中设置和检查 `was-retried` 属性
3. **简化 409 错误处理**：无论是否重试，409 一律抛出 `CommitFailedException`
4. **移除 `ErrorResponse.wasRetried`**：从 `ErrorResponse` 类及其 Builder 中移除 `wasRetried` 字段和方法
5. **移除 `HttpContext` 使用**：`HTTPClient` 不再创建和传递 `HttpContext`

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/ErrorHandlers.java` (+2/-12 lines)

**修改目的**：简化 409 Conflict 的错误处理。

**工作逻辑**：移除了根据 `error.wasRetried()` 区分处理的逻辑。此前，如果请求被重试后返回 409，会抛出 `CommitStateUnknownException`（因为服务端可能在重试时已持久化提交）；未被重试的 409 则抛出 `CommitFailedException`。现在统一抛出 `CommitFailedException`。

### `core/src/main/java/org/apache/iceberg/rest/ExponentialHttpRequestRetryStrategy.java` (+3/-17 lines)

**修改目的**：移除 502/504 从可重试状态码，移除 `was-retried` 上下文跟踪。

**工作逻辑**：
- `retriableCodes` 从 `{429, 503, 502, 504}` 简化为 `{429, 503}`
- `retryRequest` 方法中移除了 `context.setAttribute("was-retried", Boolean.TRUE)` 逻辑
- `retryRequest`（针对异常的重试）也移除了上下文设置

### `core/src/main/java/org/apache/iceberg/rest/HTTPClient.java` (+4/-21 lines)

**修改目的**：移除 `HttpContext` 的创建和 `wasRetried` 的传递。

**工作逻辑**：
- 移除 `BasicHttpContext` 和 `HttpContext` 的导入
- `throwFailure` 方法签名移除 `Object wasRetried` 参数
- 移除构建增强版 `ErrorResponse`（包含 `wasRetried`）的逻辑，直接使用原始 `errorResponse`
- 请求执行从 `httpClient.execute(request, context)` 简化为 `httpClient.execute(request)`
- `throwFailure` 调用不再传递 `wasRetried` 信息

### `core/src/main/java/org/apache/iceberg/rest/responses/ErrorResponse.java` (+3/-14 lines)

**修改目的**：移除 `wasRetried` 字段。

**工作逻辑**：从 `ErrorResponse` 类中移除 `wasRetried` 字段、`wasRetried()` 方法，以及 Builder 中的 `wasRetried(boolean)` 方法和字段。构造函数和 `build()` 方法相应简化。

### `core/src/test/java/org/apache/iceberg/rest/TestExponentialHttpRequestRetryStrategy.java` (+9/-28 lines)

**修改目的**：更新测试以反映新的重试策略。

**工作逻辑**：
- `basicRetry` 测试移除 `HttpContext` 创建和 `was-retried` 属性验证，直接传 `null` 作为 context
- `retryOnNonAbortedRequests` 测试移除 `HttpContext`
- 将 `testRetryBadGateway` 和 `testRetryGatewayTimeout` 两个测试替换为参数化测试 `testRetryHappensOnAcceptableStatusCodes`（验证 429 和 503 重试）和 `testRetryDoesNotHappenOnUnacceptableStatusCodes`（验证 500、502、504 不重试）

### `core/src/test/java/org/apache/iceberg/rest/TestRESTCatalog.java` (+0/-20 lines)

**修改目的**：移除针对 `wasRetried` 的冲突处理测试。

**工作逻辑**：删除 `testErrorHandlingForConflicts` 测试方法，该测试验证了重试和非重试场景下 409 的不同处理行为。同时移除 `CommitStateUnknownException` 的导入。

## 总结

本提交回退了 PR #12818 引入的重试增强逻辑，恢复了更简单、更保守的重试策略。主要变化是：不再重试 502/504 错误，不再跟踪请求是否被重试，409 冲突统一抛出 `CommitFailedException`。这简化了 REST 客户端的错误处理逻辑，减少了因复杂重试逻辑可能引入的问题。回退的决策基于实践中的反馈，表明原有的增强逻辑可能带来的问题多于其解决的问题。
