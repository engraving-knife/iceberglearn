# 提交 0572：将 502/504 错误标记为可重试

## 提交信息

- **序号**：0572 / 4088
- **哈希**：e447277b6672fd9de95d9cbd183d91982f35054b
- **短哈希**：e447277b6
- **日期**：2024-03-08 17:25:15 -0800
- **作者**：Amogh Jahagirdar <amogh@tabular.io>
- **提交说明**：Core: Mark 502 and 504 failures as retryable to the exponential retry strategy (#9885)
- **PR/Issue**：#9885

## 总体目的

本提交扩展了 Iceberg REST 客户端使用的指数退避重试策略 `ExponentialHttpRequestRetryStrategy`，把 HTTP 502 Bad Gateway 与 504 Gateway Timeout 两个网关层错误码纳入可重试状态码集合，提升 REST Catalog 在生产环境（尤其是位于负载均衡 / 网关后面的部署）下的容错能力。

背景动机：
- 原有可重试码仅包含 `SC_TOO_MANY_REQUESTS (429)` 与 `SC_SERVICE_UNAVAILABLE (503)`。
- REST Catalog 在真实部署中经常位于 LB / 反向代理之后（如 AWS ALB、Nginx、API Gateway），当后端短暂不可用或超时，这些网关往往会返回 502 / 504。这类错误通常是瞬时的——后端实例可能正在重启或被滚动更新，下一次重试很可能成功。
- 如果不在客户端层做重试，REST Catalog 的稳定性会强依赖于网关后端的可用性，影响生产可用性。把 502/504 纳入可重试码可以让客户端在网关抖动时透明地自愈。

## 如何达成设计目的

提交采用最小改动方式：在 `ExponentialHttpRequestRetryStrategy` 的 `retriableCodes` 不可变集合中追加 502、504 两个常量，并同步更新类的 Javadoc 列表，最后补两个单元测试覆盖新加入的两个状态码。

之所以改动如此之小就能达成目的，是因为该重试策略已经把「哪些状态码可重试」抽象成一个集合 `retriableCodes`，并在 `retryRequest(HttpResponse, int, HttpContext)` 中以 `retriableCodes.contains(response.getCode())` 统一判断——只需向集合新增成员即可让整个重试链路（包括 `getRetryInterval` 指数退避）一致地覆盖新状态码，无需修改退避逻辑或重试计数逻辑。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/ExponentialHttpRequestRetryStrategy.java`

**修改目的**：将 502 与 504 加入可重试状态码集合，并更新 Javadoc。

**工作逻辑**：
- 类级 Javadoc 中「retriable HTTP status codes」列表新增 `SC_BAD_GATEWAY (502)` 与 `SC_GATEWAY_TIMEOUT (504)`，与代码事实保持一致。
- 构造函数 `ExponentialHttpRequestRetryStrategy(int maximumRetries)` 中，把 `retriableCodes` 由：
  ```java
  ImmutableSet.of(HttpStatus.SC_TOO_MANY_REQUESTS, HttpStatus.SC_SERVICE_UNAVAILABLE)
  ```
  扩展为：
  ```java
  ImmutableSet.of(
      HttpStatus.SC_TOO_MANY_REQUESTS,
      HttpStatus.SC_SERVICE_UNAVAILABLE,
      HttpStatus.SC_BAD_GATEWAY,
      HttpStatus.SC_GATEWAY_TIMEOUT);
  ```
- 重试决策由既有方法 `retryRequest(HttpResponse response, int execCount, HttpContext context)` 统一处理：
  ```java
  return execCount <= maxRetries && retriableCodes.contains(response.getCode());
  ```
  只要 `execCount` 未超过 `maxRetries`、且响应码在 `retriableCodes` 中就触发重试。因此新增 502/504 后会自动套用现有上限与退避策略，无需额外改动。
- 退避由 `getRetryInterval(...)` 决定：优先看响应的 `Retry-After` 头（429/503 服务端可能下发），否则按指数退避 `1000 * 2^(execCount-1)`（上限 64 秒）+ 10% jitter。注意 502/504 通常不会带 `Retry-After`，因此默认走指数退避路径，这是合理设计。

类注释中保留了「Most code and behavior is taken from `DefaultHttpRequestRetryStrategy`」的说明，本次只是把可重试码集合在默认实现的基础上做了扩展。

### `core/src/test/java/org/apache/iceberg/rest/TestExponentialHttpRequestRetryStrategy.java`

**修改目的**：为新增的 502、504 重试行为补单元测试。

**工作逻辑**：
- 新增 `testRetryBadGateway()`：构造 `BasicHttpResponse(502, "Bad gateway failure")`，调用 `retryStrategy.retryRequest(response502, 3, null)` 断言返回 `true`。
- 新增 `testRetryGatewayTimeout()`：构造 `BasicHttpResponse(504, "Gateway timeout")`，调用 `retryStrategy.retryRequest(response504, 3, null)` 断言返回 `true`。
- 测试中 `execCount` 传入 3，表明在 `maxRetries >= 3` 的策略实例上，第 3 次执行遇到 502/504 仍应被允许重试（具体取决于 fixture 中 `retryStrategy` 实例的 `maxRetries` 设定，从既有测试上下文看是一个允许至少 3 次的实例）。
- 这两个用例与既有的 `testRetryServiceUnavailable`（503）和 429 用例对称，覆盖集合中的全部 4 个状态码。

## 小结

- **成效**：让 REST Catalog 客户端在面对网关层瞬时错误（502/504）时具备自动重试能力，提升与 LB / 反向代理配合的鲁棒性；改动小而集中，与既有指数退避机制无缝衔接。
- **影响范围**：仅影响 REST Catalog 客户端的 HTTP 重试行为，对所有使用 `ExponentialHttpRequestRetryStrategy` 的 REST 调用生效。其它 Catalog（Hive、JDBC、Nessie 等）不受影响。
- **回迁到 1.4.x 注意事项**：
  1. 本提交是纯行为增强（增大可重试码集合），无 API 变更，回迁风险低。
  2. 回迁前需确认 1.4.x 分支上 `ExponentialHttpRequestRetryStrategy` 类已存在且结构一致；如果该分支上该类尚未引入（或 `retriableCodes` 字段实现不同），可能需要带上前置提交。
  3. 测试用例依赖 `BasicHttpResponse(int, String)` 构造与 `retryStrategy` fixture 的 `maxRetries` 设置，回迁时应确认 fixture 与本提交上下文兼容。
  4. 注意业务侧风险：502/504 在少数场景下可能伴随请求已被实际处理（如后端写操作完成但网关在返回响应时超时），对非幂等请求重试会带来副作用。所幸该策略对 IOException 路径已经要求 `Method.isIdempotent(request.getMethod())`，对状态码路径虽然未单独校验幂等性，但 Iceberg REST 调用大部分为幂等的读 / 元数据操作，整体风险可控。回迁后建议观测实际重试日志，确认对非幂等写操作（如 commit）无副作用。
