# 提交 2418：Core: Allow retries for Idempotent Requests with Certain Codes (#13449)

## 提交信息

- **序号**：2418 / 4088
- **哈希**：360f87326d4ccf67512a0240e529035801d9db2b
- **短哈希**：360f87326
- **日期**：2025-07-25 15:07:03 -0700
- **作者**：Russell Spitzer
- **提交说明**：Core: Allow retries for Idempotent Requests with Certain Codes (#13449)
- **PR/Issue**：#13449

## 总体目的

本提交扩展了 Iceberg REST HTTP 客户端的重试策略，使得幂等请求（如 GET、HEAD）在遇到更多类型的 HTTP 错误状态码时也能进行重试。

Iceberg REST 客户端使用 `ExponentialHttpRequestRetryStrategy` 来决定何时重试失败的 HTTP 请求。此前的策略对所有请求只重试两种状态码：429（Too Many Requests）和 503（Service Unavailable）。然而，对于幂等请求（即重复执行不会产生不同结果的请求），更激进的重试策略是安全的——即使服务器返回 500（Internal Server Error）、502（Bad Gateway）、504（Gateway Timeout）或 408（Request Timeout），重试幂等请求不会造成副作用。

本提交引入了针对幂等请求的额外可重试状态码集合，使得 GET 和 HEAD 等幂等方法在遇到这些暂时性服务端错误时也能自动重试，提高 REST Catalog 操作的可靠性。

## 如何达成设计目的

1. 在 `ExponentialHttpRequestRetryStrategy` 中新增 `idempotentRetriableCodes` 集合，包含比通用 `retriableCodes` 更多的状态码
2. 修改 `retryRequest` 方法，从 `HttpContext` 中获取请求对象，判断请求方法是否为幂等的，如果是幂等方法且响应码在 `idempotentRetriableCodes` 中，则允许重试
3. 修改 `HTTPClient`，在执行请求时传入 `HttpClientContext`，使重试策略能够访问请求信息

关键设计点：
- 通用可重试码（适用于所有请求）：429、503
- 幂等可重试码（仅适用于幂等请求）：429、503、500、502、504、408
- 使用 Apache HttpClient 的 `Method.isIdempotent()` 判断请求是否幂等

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/ExponentialHttpRequestRetryStrategy.java` (+43/-1 lines)

**修改目的**：扩展重试策略，支持幂等请求的额外可重试状态码。

**工作逻辑**：
- 新增 `idempotentRetriableCodes` 字段，包含 429、503、500、502、504、408 六个状态码（注意 503 在代码中被重复添加了一次，这不会影响 Set 的行为但属于代码冗余）
- 修改 `retryRequest` 方法：从 `HttpContext` 中获取 `HttpRequest` 对象（通过 `HttpCoreContext`），然后检查是否满足通用可重试条件或幂等可重试条件
- 新增 `shouldRetryIdempotent` 私有方法：检查请求是否为 null，然后使用 `Method.isIdempotent(request.getMethod())` 判断方法是否幂等，并检查响应码是否在 `idempotentRetriableCodes` 中
- 更新类文档注释，列出幂等请求的额外可重试状态码

### `core/src/main/java/org/apache/iceberg/rest/HTTPClient.java` (+5/-1 lines)

**修改目的**：在执行 HTTP 请求时传入 HttpContext，使重试策略能获取请求信息。

**工作逻辑**：
- 导入 `HttpClientContext` 和 `HttpContext`
- 在 `execute` 方法中，创建 `HttpContext context = HttpClientContext.create()`，然后将 `httpClient.execute(request)` 改为 `httpClient.execute(request, context)`。这使得重试策略在 `retryRequest` 方法中可以通过 `HttpCoreContext` 获取到原始的请求对象，从而判断请求方法是否幂等。

### `core/src/test/java/org/apache/iceberg/rest/TestExponentialHttpRequestRetryStrategy.java` (+11/-0 lines)

**修改目的**：测试幂等方法的重试行为。

**工作逻辑**：新增 `testRetryHappensWithIdempotentMethods` 参数化测试，对 429、503、500、502、504、408 六个状态码进行测试。创建 `BasicHttpResponse` 和 `HttpClientContext`（设置 GET 请求），验证重试策略返回 true。

### `core/src/test/java/org/apache/iceberg/rest/TestHTTPClient.java` (+54/-2 lines)

**修改目的**：端到端测试幂等请求的重试行为。

**工作逻辑**：
- 新增 `testRetryIdemmpotentMethods` 参数化测试（使用 `@EnumSource(HttpMethod.class)`），假设只对 GET 和 HEAD 方法运行。第一次请求返回 504（Gateway Timeout），第二次请求返回 200（OK），验证最终请求成功且无异常抛出。
- 重构 `addRequestTestCaseAndGetPath` 方法：新增一个接受自定义 path 参数的重载版本，并修改原方法使用 `Times.exactly(1)` 确保每个 mock 响应只匹配一次。这使得可以针对同一路径设置连续的不同响应（先失败后成功），支持重试场景的测试。

## 总结

本提交增强了 Iceberg REST 客户端的容错能力，使得幂等请求（GET、HEAD）在遇到 500、502、504、408 等服务端错误时能够自动重试。这在面对暂时性服务端故障时显著提高了 REST Catalog 操作的可靠性。非幂等请求（如 POST）仍然只在 429 和 503 时重试，保证了安全性。
