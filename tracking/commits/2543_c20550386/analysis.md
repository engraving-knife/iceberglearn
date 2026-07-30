# 提交 2543：Spec, Core: Mark 503 as non retryable error code for Update Table (#13619)

## 提交信息

- **序号**：2543 / 4088
- **哈希**：c205503864b8dc0cf9562b30f47c3168f1cd86c9
- **短哈希**：c20550386
- **日期**：2025-08-21 15:59:30 -0700
- **作者**：Prashant Singh
- **提交说明**：Spec, Core: Mark 503 as non retryable error code for Update Table (#13619)
- **PR/Issue**：#13619

## 总体目的

REST catalog 的 `Update Table`（提交 commit）是非幂等操作。当服务端返回 503 Service Unavailable 时，原先的客户端重试策略把 503 列入 `retriableCodes`，会无条件重试。但 503 意味着服务不可用，请求可能已经被部分处理，盲目重试非幂等的 commit 可能导致重复提交或状态不一致。同理 `ErrorHandlers` 中 503 之前没有像 500/502/504 那样映射到 `CommitStateUnknownException`，导致语义不一致。

本提交调整 503 的处理策略：
1. **REST 规范**（open-api yaml）：更新 `ServiceUnavailableResponse` 描述，说明请求可能已被部分处理；非幂等请求只有在响应包含 `Retry-After` 头时才应重试。
2. **`ErrorHandlers`**：把 503 与 500/502/504 一致地映射为 `CommitStateUnknownException`（包装 `ServiceFailureException`），表示提交状态未知，让上层用 commit 重试机制（而非 HTTP 重试）处理，避免盲目 HTTP 重试。
3. **`ExponentialHttpRequestRetryStrategy`**：把 503 从默认 `retriableCodes` 移除，但增加一个例外——当 503 响应携带 `Retry-After` 头时仍允许重试（服务端显式提示可重试）。

这样既避免了对非幂等 commit 的盲目重试，又保留了服务端显式通过 `Retry-After` 引导重试的能力。

## 如何达成设计目的

- **ErrorHandlers**：在两个 commit 相关的错误处理分支（table commit 与 namespace commit）中，把 `case 503:` 加到 `case 500/502/504` 之后，统一抛 `CommitStateUnknownException(new ServiceFailureException(...))`。
- **ExponentialHttpRequestRetryStrategy**：
  - `retriableCodes` 从 `{429, 503}` 改为 `{429}`。
  - `retryRequest` 中新增 `is503Retryable` 判断：响应码为 503 且包含 `Retry-After` 头时视为可重试。
  - 最终重试条件：未超最大重试次数 且 （在 retriableCodes 中，或幂等请求命中 idempotentRetriableCodes，或 503 带 Retry-After）。
  - 类注释同步更新：从可重试列表中移除 503，并说明 503 仅在带 Retry-After 时重试。
- **规范 yaml**：`ServiceUnavailableResponse.description` 改为"服务不可用，请求可能已被部分处理；服务端可通过 Retry-After 头提示重试，非幂等请求仅在该头存在时才应重试"。
- **测试**：
  - `basicRetry` 与 `testRetryHappensOnAcceptableStatusCodes` 中移除 503 默认重试断言。
  - `testRetryDoesNotHappenOnUnacceptableStatusCodes` 加入 503，验证无 Retry-After 时不重试。
  - 新增 `testRetryHappensWith503WithRetryAfterHeader`：503 + `Retry-After: 60` 头时重试返回 true。
  - `testRetryHappensWithIdempotentMethods` 中 503 仍对幂等方法重试（因为 idempotentRetriableCodes 仍含 503）。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/ErrorHandlers.java` (+2)

**修改目的**：503 映射为 CommitStateUnknownException。

**工作逻辑**：在两个 commit 错误处理 switch 中，`case 503:` 与 `case 500/502/504` 一起抛 `CommitStateUnknownException(new ServiceFailureException(...))`。

### `core/src/main/java/org/apache/iceberg/rest/ExponentialHttpRequestRetryStrategy.java` (+18/-6)

**修改目的**：503 默认不重试，带 Retry-After 时例外。

**工作逻辑**：
- `retriableCodes` 改为 `{429}`。
- `retryRequest` 新增 `is503Retryable = code==503 && response.getFirstHeader(RETRY_AFTER)!=null`。
- 返回 `execCount<=maxRetries && (retriableCodes.contains(code) || shouldRetryIdempotent(...) || is503Retryable)`。
- 类注释同步更新。

### `core/src/test/java/org/apache/iceberg/rest/TestExponentialHttpRequestRetryStrategy.java` (+13/-5)

**修改目的**：覆盖 503 新策略。

**工作逻辑**：
- 移除 503 默认重试的断言。
- 把 503 加入"不重试"参数化测试。
- 新增 `testRetryHappensWith503WithRetryAfterHeader`：503 + Retry-After 头时重试为 true。
- 幂等方法测试中 503 仍重试（idempotentRetriableCodes 未变）。

### `open-api/rest-catalog-open-api.yaml` (+3/-3)

**修改目的**：更新规范描述。

**工作逻辑**：`ServiceUnavailableResponse.description` 改为说明请求可能部分处理、非幂等请求仅在有 Retry-After 时重试。

## 总结

将 503 从 REST 客户端的默认可重试状态码中移除（避免对非幂等 commit 盲目重试），改为仅在响应携带 `Retry-After` 头时才重试；同时在 `ErrorHandlers` 中把 503 与 500/502/504 一致映射为 `CommitStateUnknownException`，让上层 commit 重试机制处理。REST 规范同步更新描述。测试覆盖默认不重试、带 Retry-After 重试、幂等方法仍重试三种场景。
