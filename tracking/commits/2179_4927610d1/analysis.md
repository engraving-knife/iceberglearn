# 提交 2179：Core: Avoid table corruption from 409 on self conflicts after 5xx retries by throwing CommitStateUnknown (#12818)

## 提交信息

- **序号**：2179 / 4088
- **哈希**：4927610d1d29f9c5e9370d2c2e514631e1fa6465
- **短哈希**：4927610d1
- **日期**：2025-05-29 07:33:27 -0700
- **作者**：Prashant Singh
- **提交说明**：Core: Avoid table corruption from 409 on self conflicts after 5xx retries by throwing CommitStateUnknown (#12818)
- **PR/Issue**：#12818

## 总体目的

此提交修复了一个可能导致表损坏的严重 bug。在 REST Catalog 提交场景中，当服务器返回 5xx 错误时，客户端会自动重试。但问题是：第一次请求可能实际上在服务端已经成功提交（只是网络响应丢失），当重试请求到达时，由于基础快照已变更，服务端返回 409 Conflict。原代码将 409 一律视为 `CommitFailedException`，这会导致客户端执行文件清理（cleanup），但实际提交已经成功，从而删除了已提交的数据文件，导致表损坏。此提交通过区分"有重试的 409"和"无重试的 409"，对有重试的 409 抛出 `CommitStateUnknownException` 而非 `CommitFailedException`，避免触发文件清理，防止表损坏。

## 如何达成设计目的

- 在 `ErrorResponse` 中添加 `wasRetried` 字段，标记该错误是否发生在重试之后
- 在重试策略中，当决定重试时，在 HttpContext 中设置 `was-retried` 标记
- 在 `HTTPClient` 中，使用 HttpContext 传递重试标记，并在构建 ErrorResponse 时填充该字段
- 在 `ErrorHandlers` 的 409 处理中，检查 `wasRetried` 标记：如果为 true，抛出 `CommitStateUnknownException`；如果为 false，抛出 `CommitFailedException`
- `CommitStateUnknownException` 不会触发文件清理，从而保护已提交的数据

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/ErrorHandlers.java` (修改, +13/-1 lines)

**修改目的**：区分有重试和无重试的 409 错误处理。

**工作逻辑**：在 `case 409:` 分支中，检查 `error.wasRetried()`。如果为 true，说明请求经过了重试，409 可能是因为第一次请求已经成功提交导致的自冲突，此时抛出 `CommitStateUnknownException`（包装在 RESTException 中），避免客户端清理已提交的文件。如果为 false（正常并发冲突），仍抛出 `CommitFailedException`。

### `core/src/main/java/org/apache/iceberg/rest/ExponentialHttpRequestRetryStrategy.java` (修改, +10/-2 lines)

**修改目的**：在重试时记录重试标记。

**工作逻辑**：在 `retryRequest` 方法（基于响应码判断是否重试）和 `retryRequest` 方法（基于异常判断是否重试）中，当决定重试时，如果 context 不为 null，设置 `context.setAttribute("was-retried", Boolean.TRUE)`。

### `core/src/main/java/org/apache/iceberg/rest/HTTPClient.java` (修改, +20/-5 lines)

**修改目的**：传递重试标记到错误处理流程。

**工作逻辑**：
- 在 `throwFailure` 方法中添加 `Object wasRetried` 参数
- 使用传入的 `wasRetried` 值构建增强的 `ErrorResponse`，通过 `ErrorResponse.builder().wasRetried(wasRetried == Boolean.TRUE)` 设置
- 在 `execute` 方法中，创建 `BasicHttpContext` 并传递给 `httpClient.execute(request, context)`，之后从 context 获取 `was-retried` 属性传递给 `throwFailure`

### `core/src/main/java/org/apache/iceberg/rest/responses/ErrorResponse.java` (修改, +14/-3 lines)

**修改目的**：添加 `wasRetried` 字段到 ErrorResponse。

**工作逻辑**：
- 添加 `boolean wasRetried` 字段和 `wasRetried()` getter 方法
- 在构造函数和 Builder 中添加该字段
- Builder 添加 `wasRetried(boolean)` 方法

### `core/src/test/java/org/apache/iceberg/rest/TestExponentialHttpRequestRetryStrategy.java` (修改, +15/-8 lines)

**修改目的**：验证重试时正确设置 was-retried 标记。

**工作逻辑**：修改测试用例，传入 HttpContext 并验证重试决定后 context 中 `was-retried` 属性被设置为 `Boolean.TRUE`。

### `core/src/test/java/org/apache/iceberg/rest/TestRESTCatalog.java` (修改, +20/-0 lines)

**修改目的**：验证 409 错误在有重试和无重试时的不同处理。

**工作逻辑**：添加 `testErrorHandlingForConflicts` 测试方法，验证：
- 409 且 `wasRetried=false` 时，抛出 `CommitFailedException`
- 409 且 `wasRetried=true` 时，抛出 `CommitStateUnknownException`

## 总结

此提交修复了一个严重的表损坏 bug。当 REST 提交在 5xx 重试后收到 409 时，原代码会清理已成功提交的数据文件。修复通过在 ErrorResponse 中添加 `wasRetried` 标记，区分有重试和无重试的 409，对有重试的 409 抛出 `CommitStateUnknownException` 避免文件清理。修改涉及错误处理、重试策略、HTTP 客户端和 ErrorResponse 模型，并添加了完整的测试验证。
