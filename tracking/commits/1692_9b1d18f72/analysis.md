# 提交 1692：Auth Manager API part 4: RESTClient, HTTPClient (#11992)

## 提交信息

- **序号**：1692 / 4088
- **哈希**：9b1d18f72c185b6e53f90b778ca412960a64adf5
- **短哈希**：9b1d18f72
- **日期**：2025-02-06 09:32:13 -0800
- **作者**：Alexandre Dutra
- **提交说明**：Auth Manager API part 4: RESTClient, HTTPClient (#11992)
- **PR/Issue**：#11992

## 总体目的

这是 Auth Manager API 系列重构的第 4 部分，聚焦于 REST 客户端层（`RESTClient` 接口和 `HTTPClient` 实现）。此前的 `HTTPClient` 把鉴权头处理与 HTTP 请求执行混在一起：调用方每次调用 `get/post/delete` 等方法时都需要手动传入鉴权 headers（如 OAuth2 token），`HTTPClient` 内部用 `addRequestHeaders` 把这些 headers 设置到 Apache HttpClient 请求上。这种设计有几个问题：

1. 鉴权逻辑分散在多处调用点，难以统一管理。例如 `RESTSessionCatalog`、`VendedCredentialsProvider`、`S3V4RestSignerClient`、`OAuth2RefreshCredentialsHandler` 都要各自维护 token 到 headers 的转换。

2. 一个 `HTTPClient` 实例的鉴权头在构造时就固定（通过 `baseHeaders`），无法在运行时随 token 刷新而更新，导致 token 刷新场景需要绕路处理。

3. `HTTPClient` 实现了 `RESTClient` 的所有方法（head/get/post/delete/postForm），但每个方法体几乎相同（只是构造请求再执行），存在大量样板代码。

本提交引入"AuthSession 绑定到 RESTClient"的模型：`RESTClient` 新增 `withAuthSession(AuthSession)` 方法返回一个绑定了鉴权会话的客户端视图；`HTTPClient` 持有一个 `AuthSession` 字段，在构建请求时由 `AuthSession.authenticate(request)` 统一注入鉴权头。同时抽取 `BaseHTTPClient` 基类把所有 REST 方法的样板实现上提，`HTTPClient` 只需实现 `buildRequest` 和 `execute` 两个核心方法。这是为后续 Auth Manager API（可插拔的鉴权管理器）铺路的关键一步。

## 如何达成设计目的

整体设计分三层：

1. **`RESTClient` 接口扩展**：新增 `withAuthSession(AuthSession session)` 默认方法（默认返回 this，表示无操作），允许子类返回绑定鉴权会话的客户端实例。

2. **`BaseHTTPClient` 抽象基类**：实现 `RESTClient` 的所有方法（head/get/post/delete/postForm），每个方法都是"用 buildRequest 构造 HTTPRequest，再用 execute 执行"的统一模式。子类只需实现 `withAuthSession`、`buildRequest`、`execute` 三个抽象方法。这消除了 `HTTPClient` 中重复的样板代码。

3. **`HTTPClient` 重构**：改为继承 `BaseHTTPClient`。持有 `AuthSession authSession` 字段，在 `buildRequest` 中构造 `HTTPRequest` 后调用 `authSession.authenticate(request)` 注入鉴权头。新增 `withAuthSession` 方法返回一个共享底层 httpClient/mapper 但绑定新 AuthSession 的轻量子实例（私有构造函数 `HTTPClient(HTTPClient parent, AuthSession authSession)`）。`close()` 时先关闭 authSession 再关闭 httpClient。Builder 新增 `withAuthSession` 方法支持构造时绑定。

4. **调用方适配**：所有原本手动传鉴权 headers 的调用点改为先 `withAuthSession(...)` 再调用方法时传 `Map.of()`。`RESTSessionCatalog` 把 catalog 级别的 AuthSession 绑定到 client；`VendedCredentialsProvider`、`OAuth2RefreshCredentialsHandler` 用 `DefaultAuthSession.of(headers)` 构造会话；`S3V4RestSignerClient` 用 `AuthSession.EMPTY` 构造用于 token 刷新的"无鉴权"客户端，避免刷新请求被旧 token 污染。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/BaseHTTPClient.java` (new file, +126 lines)

**修改目的**：抽取 RESTClient 方法实现的样板代码到抽象基类。

**工作逻辑**：
```java
public abstract class BaseHTTPClient implements RESTClient {
  @Override
  public abstract RESTClient withAuthSession(AuthSession session);

  @Override
  public void head(String path, Map<String, String> headers, Consumer<ErrorResponse> errorHandler) {
    HTTPRequest request = buildRequest(HTTPMethod.HEAD, path, null, headers, null);
    execute(request, null, errorHandler, h -> {});
  }

  @Override
  public <T extends RESTResponse> T get(...) {
    HTTPRequest request = buildRequest(HTTPMethod.GET, path, queryParams, headers, null);
    return execute(request, responseType, errorHandler, h -> {});
  }
  // post / delete / postForm 同理

  protected abstract HTTPRequest buildRequest(HTTPMethod method, String path,
      Map<String, String> queryParams, Map<String, String> headers, Object body);
  protected abstract <T extends RESTResponse> T execute(HTTPRequest request, Class<T> responseType,
      Consumer<ErrorResponse> errorHandler, Consumer<Map<String, String>> responseHeaders);
}
```
所有 REST 方法统一为"buildRequest + execute"两步，子类只关心如何构造请求和如何执行请求。

### `core/src/main/java/org/apache/iceberg/rest/RESTClient.java` (+6/-0 lines)

**修改目的**：在接口层面引入 AuthSession 绑定能力。

**工作逻辑**：
```java
import org.apache.iceberg.rest.auth.AuthSession;

/** Returns a REST client that authenticates requests using the given session. */
default RESTClient withAuthSession(AuthSession session) {
  return this;
}
```
默认实现返回 this（无操作），保证向后兼容；支持 AuthSession 的实现（如 HTTPClient）覆盖此方法返回绑定会话的新实例。

### `core/src/main/java/org/apache/iceberg/rest/HTTPClient.java` (+78/-225 lines, 大幅重构)

**修改目的**：让 `HTTPClient` 绑定 AuthSession，并继承 `BaseHTTPClient` 消除样板。

**工作逻辑**：
- 类签名改为 `public class HTTPClient extends BaseHTTPClient`。
- 字段重构：`String uri` 改为 `URI baseUri`；新增 `Map<String, String> baseHeaders`、`AuthSession authSession` 字段（原 baseHeaders 通过 HttpClientBuilder.setDefaultHeaders 设置，现改为在 buildRequest 时手动合并）。
- 新增私有构造函数 `HTTPClient(HTTPClient parent, AuthSession authSession)`，共享父实例的 baseUri/httpClient/mapper/baseHeaders，仅替换 authSession——这是 `withAuthSession` 创建子视图的实现，避免重新分配 HTTP 连接池等重资源。
- `withAuthSession` 实现：`return new HTTPClient(this, session);`，并对 null 入参做校验。
- `buildRequest` 实现：构造 `ImmutableHTTPRequest`，合并 headers（调用方 headers → 默认 Accept/Content-Type → baseHeaders putIfAbsent），最后调用 `authSession.authenticate(request)` 注入鉴权头。注意 baseHeaders 在 AuthSession 之前应用，确保 AuthSession 可覆盖。
  ```java
  ContentType mimeType = body instanceof Map
      ? ContentType.APPLICATION_FORM_URLENCODED : ContentType.APPLICATION_JSON;
  allHeaders.putIfAbsent(HttpHeaders.CONTENT_TYPE, mimeType.getMimeType());
  if (baseHeaders != null) { baseHeaders.forEach(allHeaders::putIfAbsent); }
  Preconditions.checkState(authSession != null, "Invalid auth session: null");
  return authSession.authenticate(builder.headers(HTTPHeaders.of(allHeaders)).build());
  ```
- `execute` 实现：从 `HTTPRequest` 取 method、requestUri、headers、encodedBody 构造 Apache HttpClient 请求并执行，处理响应、错误。原 `buildUri`、`addRequestHeaders`、`toJson`、`toFormEncoding` 等私有方法被移除（URI 构造和 body 编码移入 `HTTPRequest`）。
- `close()` 改为先关闭 authSession 再关闭 httpClient（finally 块保证 httpClient 总是关闭）。
- Builder：`uri` 字段改为 `URI`；`uri(String)` 用 `URI.create` 解析；新增 `uri(URI)` 重载；新增 `withAuthSession(AuthSession)` 方法；`build()` 把 authSession 传入构造函数。

### `core/src/main/java/org/apache/iceberg/rest/RESTSessionCatalog.java` (+9/-5 lines)

**修改目的**：把 catalog 级别 AuthSession 绑定到 client，替代手动传 headers。

**工作逻辑**：
- 初始化阶段：用 `DefaultAuthSession.of(HTTPHeaders.of(OAuth2Util.authHeaders(initToken)))` 构造 init session，`clientBuilder.apply(props).withAuthSession(initSession)` 创建 init client，后续 fetchToken 调用不再传 token headers（传 `configHeaders(props)` 即可）。
- 主 client 构造：`this.client = clientBuilder.apply(mergedProps).withAuthSession(catalogAuth);`，token 刷新后 `this.client = client.withAuthSession(catalogAuth);` 重新绑定更新后的 session。
- 原本 `initHeaders = RESTUtil.merge(configHeaders(props), OAuth2Util.authHeaders(initToken))` 简化为 `initHeaders = configHeaders(props)`（鉴权头由 session 注入）。

### `aws/src/main/java/org/apache/iceberg/aws/s3/VendedCredentialsProvider.java` (+9/-2 lines)

**修改目的**：用 AuthSession 绑定替代手动传 token headers。

**工作逻辑**：
- client 创建时构造 `DefaultAuthSession.of(HTTPHeaders.of(OAuth2Util.authHeaders(token)))` 并 `.withAuthSession(authSession)`。
- `loadCredentials` 调用时原本传 `OAuth2Util.authHeaders(token)` 改为传 `Map.of()`（鉴权由 session 处理）。

### `aws/src/main/java/org/apache/iceberg/aws/s3/signer/S3V4RestSignerClient.java` (+34/-12 lines)

**修改目的**：为 token 刷新场景使用"无鉴权"客户端，避免刷新请求被旧 token 污染；为签名请求绑定 authSession。

**工作逻辑**：
- 两处 token 刷新逻辑（access token 和 credential 流程）都改为先创建 `RESTClient refreshClient = httpClient().withAuthSession(AuthSession.EMPTY)`，再用 refreshClient 执行刷新请求。注释说明：刷新客户端必须使用空鉴权会话，避免干扰刷新后的新 token。
- 主签名请求 `post` 调用改为 `httpClient().withAuthSession(authSession()).post(..., Map.of(), ...)`，鉴权由 session 注入，不再手动传 `authSession().headers()`。

### `aws/src/test/java/org/apache/iceberg/aws/TestRESTSigV4Signer.java` (+2/-0 lines)

**修改目的**：适配测试，补充 `AuthSession.EMPTY` 相关 import 或小调整。

**工作逻辑**：测试侧的小幅适配（具体为新增 import 以引用 `AuthSession.EMPTY`）。

### `core/src/main/java/org/apache/iceberg/rest/RESTCatalogAdapter.java` (+59/-61 lines)

**修改目的**：适配新的 HTTPRequest 模型，RESTCatalogAdapter 作为内置 REST 服务端的适配器需要处理请求/响应对象的新结构。

**工作逻辑**：把原来基于 `Method` 和裸参数的请求处理改为基于 `HTTPRequest` 对象的处理，与 HTTPClient 的新接口对齐。

### `core/src/main/java/org/apache/iceberg/rest/RESTCatalogServlet.java` (+9/-5 lines)

**修改目的**：适配新的请求/响应模型。

**工作逻辑**：Servlet 层的请求转发逻辑适配 `HTTPRequest` / `HTTPResponse` 新结构。

### `gcp/src/main/java/org/apache/iceberg/gcp/gcs/OAuth2RefreshCredentialsHandler.java` (+8/-2 lines)

**修改目的**：用 AuthSession 绑定替代手动传 token headers。

**工作逻辑**：
- 新增 `AuthSession authSession` 字段，构造时用 `DefaultAuthSession.of(HTTPHeaders.of(OAuth2Util.authHeaders(token)))` 初始化。
- `httpClient()` 构建 HTTPClient 时 `.withAuthSession(authSession)`。
- `refreshCredentials` 调用时传 `Map.of()` 替代手动 headers。

### `core/src/test/java/org/apache/iceberg/rest/TestHTTPClient.java` (+14/-11 lines)

**修改目的**：适配 HTTPClient 新 API（AuthSession 绑定）。

**工作逻辑**：测试改为通过 `withAuthSession` 或 Builder 的 `withAuthSession` 绑定会话，而非手动传 headers。

### `core/src/test/java/org/apache/iceberg/rest/TestRESTCatalog.java` (+248/-625 lines, 大幅缩减)

**修改目的**：适配新模型，测试代码大幅精简。

**工作逻辑**：原本大量手动构造 headers 的测试改为通过 AuthSession 绑定，重复的 headers 处理代码被消除，测试更聚焦于业务逻辑。这是本提交中代码量减少最多的文件。

### `core/src/test/java/org/apache/iceberg/rest/TestRESTViewCatalog.java` (+26/-17 lines)

**修改目的**：适配新模型。

**工作逻辑**：View catalog 测试同步适配 AuthSession 绑定方式。

## 总结

这是 Auth Manager API 系列重构的关键一步，重构了 REST 客户端层的鉴权模型。核心变化是引入"AuthSession 绑定到 RESTClient"的设计：`RESTClient.withAuthSession` 返回绑定鉴权会话的客户端视图，`HTTPClient` 在构建请求时由 AuthSession 统一注入鉴权头，取代了原来调用方手动传 token headers 的分散模式。同时抽取 `BaseHTTPClient` 基类消除 REST 方法样板代码，`HTTPClient` 只需实现 `buildRequest` 和 `execute`。

这次重构涉及 13 个文件，代码净减少约 60 行（768 增 / 827 删），测试代码（`TestRESTCatalog`）大幅精简。改动为后续可插拔 Auth Manager API 铺平了道路，也让 token 刷新等场景的鉴权处理更清晰（用 `AuthSession.EMPTY` 避免刷新请求被旧 token 污染）。属于架构层面的重要改进，影响面较广但设计目标明确。
