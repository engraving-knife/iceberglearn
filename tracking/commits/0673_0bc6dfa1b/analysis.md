# 提交 0673：Core: Extend HTTPClient Builder to allow setting a proxy server (#10052)

## 提交信息
- **序号**：0673 / 4088
- **哈希**：0bc6dfa1bd55c8abb4891f1eeadb6fbc89878c92
- **短哈希**：0bc6dfa1b
- **日期**：2024-04-11 13:58:44 -0700
- **作者**：Harish Chandrasekaran
- **提交说明**：Core: Extend HTTPClient Builder to allow setting a proxy server (#10052)
- **PR/Issue**：#10052

## 总体目的

在企业级网络部署中，客户端通常无法直接访问外部 Internet 资源，必须通过代理（forward proxy）转发流量。例如：当 Spark/Flink 等引擎部署在公司内网，而 Iceberg REST Catalog 部署在外部（如云上托管 Catalog）时，引擎 → Catalog 的 REST 请求必须经过企业正向代理才能通行。此外，部分代理还要求 Basic Auth 认证（用户名/密码）。

在此提交前，Iceberg 的 `HTTPClient`（基于 Apache HttpClient 5）**完全没有代理配置能力**——既不能指定代理主机端口，也不能配置代理认证。这意味着需要经过代理访问 REST Catalog 的用户根本无法使用 Iceberg REST 客户端，需要自行 fork 代码或在外部维护 patch，严重限制了 Iceberg REST Catalog 在企业环境中的可用性。

本提交为 `HTTPClient.Builder` 增加两个新的构建方法：
- `withProxy(String hostname, int port)`：设置正向代理服务器的主机名和端口。
- `withProxyCredentialsProvider(CredentialsProvider credentialsProvider)`：可选地为代理配置认证凭证提供者（典型场景是 `BasicCredentialsProvider`，承载用户名密码）。

从而使 `HTTPClient` 可以满足企业网络中"必须经过代理 + 可选代理认证"的访问需求。

## 如何达成设计目的

整体设计思路是在 `HTTPClient.Builder` 中暴露两个新的 fluent API（链式 builder 模式与 Iceberg 现有 `withHeader`/`withUri` 等保持一致），并把代理配置在 `HTTPClient` 构造器中通过 HttpClient 5 的 `setProxy` 和 `setDefaultCredentialsProvider` 应用到底层 builder。

具体策略：

1. **构造器签名扩展**：在 0672 提交（PR #10053）已注入 `HttpClientConnectionManager` 的基础上，本提交再向 `HTTPClient` 构造器新增两个参数：`HttpHost proxy` 与 `CredentialsProvider proxyCredsProvider`。这两个参数由 `Builder.build()` 传入。

2. **代理与凭证应用顺序**：在构造器中，先判断 `proxy != null`；若是，则当 `proxyCredsProvider != null` 时调用 `clientBuilder.setDefaultCredentialsProvider(proxyCredsProvider)`，最后调用 `clientBuilder.setProxy(proxy)`。这种"先凭证后代理"的顺序与 HttpClient 5 的预期一致：代理认证需要 `CredentialsProvider` 在 builder 上先注册，再设置代理目标，HttpClient 在路由经过代理时会自动从 provider 中按 `AuthScope` 取出对应凭证。

3. **校验代理凭证必须有代理**：在 `Builder.build()` 中，若 `proxyCredentialsProvider != null` 但 `proxy == null`，抛出 `NullPointerException`，消息为 `"Invalid http client proxy for proxy credentials provider: null"`。这避免用户配置了代理凭证却忘了配置代理本身导致运行时路由异常的困惑。注意这是单向校验——允许配置代理但不配凭证（即代理无认证场景）。

4. **`withProxy` 校验 hostname 非空**：`withProxy` 中用 `Preconditions.checkNotNull(hostname, "Invalid hostname for http client proxy: null")` 校验，避免误传 null hostname。`withProxyCredentialsProvider` 同样校验 `credentialsProvider` 非空。

5. **不通过 properties 配置而是 Builder API**：与 0672（通过 `rest.client.xxx-ms` properties 配置超时）不同，代理配置通过 Builder API 显式设置，而非通过 properties map。这是有意为之——代理凭证（`CredentialsProvider`）本身就是一个 Java 对象，不适合序列化为字符串 properties；且代理配置通常由部署框架（如 Spark 的 `SparkSession`、Catalog 加载器）在编程式构造 `HTTPClient` 时注入，而非用户在 catalog properties 文件中填写。Builder API 提供了更强的类型安全。

6. **测试覆盖**：新增 4 个测试：
   - `testProxyServer`：启动 mockserver 作为代理，构造带代理的 client，发起 HEAD 请求，验证代理收到请求一次。这验证代理路由实际生效。
   - `testProxyCredentialProviderWithoutProxyServer`：只配置凭证不配置代理，应抛 NPE，验证校验逻辑。
   - `testProxyServerWithNullHostname`：`withProxy(null, port)` 应抛 NPE。
   - `testProxyAuthenticationFailure`：用 mockserver 配置代理认证（用户名 `test-username`/密码 `test-password`），客户端使用错误密码 `invalid-password`，验证返回 `407 Proxy Authentication Required`。这覆盖了代理认证的实际场景。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/HTTPClient.java`

**修改目的**：在 `HTTPClient` 与其 `Builder` 中增加代理服务器与代理认证凭证的配置能力。

**工作逻辑**：

1. 新增 import：`org.apache.hc.client5.http.auth.CredentialsProvider`、`org.apache.hc.core5.http.HttpHost`。`CredentialsProvider` 是 HttpClient 5 中承载认证凭证的接口，`HttpHost` 表示"主机:端口"。

2. **构造器签名扩展**：在 0672 提交的 `(uri, baseHeaders, objectMapper, requestInterceptor, properties, connectionManager)` 基础上，插入 `HttpHost proxy`、`CredentialsProvider proxyCredsProvider` 两个参数（位于 `uri` 与 `baseHeaders` 之间）：
   ```java
   private HTTPClient(
       String uri,
       HttpHost proxy,
       CredentialsProvider proxyCredsProvider,
       Map<String, String> baseHeaders,
       ObjectMapper objectMapper,
       HttpRequestInterceptor requestInterceptor,
       Map<String, String> properties,
       HttpClientConnectionManager connectionManager) {
   ```

3. **构造器中应用代理与凭证**：在 `clientBuilder.setRetryStrategy(...)` 之后、`clientBuilder.build()` 之前新增：
   ```java
   if (proxy != null) {
     if (proxyCredsProvider != null) {
       clientBuilder.setDefaultCredentialsProvider(proxyCredsProvider);
     }
     clientBuilder.setProxy(proxy);
   }
   ```
   这段逻辑在构造器尾部、`httpClient = clientBuilder.build()` 之前执行。先注册凭证后设置代理，保证 HttpClient 构建完成时认证链路完整。

4. **`Builder` 新增字段与方法**：
   ```java
   private HttpHost proxy;
   private CredentialsProvider proxyCredentialsProvider;

   public Builder withProxy(String hostname, int port) {
     Preconditions.checkNotNull(hostname, "Invalid hostname for http client proxy: null");
     this.proxy = new HttpHost(hostname, port);
     return this;
   }

   public Builder withProxyCredentialsProvider(CredentialsProvider credentialsProvider) {
     Preconditions.checkNotNull(
         credentialsProvider, "Invalid credentials provider for http client proxy: null");
     this.proxyCredentialsProvider = credentialsProvider;
     return this;
   }
   ```
   两个方法都返回 `this`，符合 fluent builder 模式。`HttpHost(hostname, port)` 直接构造，不指定 scheme（默认 http）。

5. **`Builder.build()` 增加校验与参数传递**：
   ```java
   if (this.proxyCredentialsProvider != null) {
     Preconditions.checkNotNull(
         proxy, "Invalid http client proxy for proxy credentials provider: null");
   }

   return new HTTPClient(
       uri,
       proxy,
       proxyCredentialsProvider,
       baseHeaders,
       mapper,
       interceptor,
       properties,
       configureConnectionManager(properties));
   ```
   关键设计：仅在 `proxyCredentialsProvider != null` 时校验 `proxy` 不能为 null。这允许"代理但无认证"和"无代理无认证"两种合法情况，仅拒绝"有认证但无代理"这种逻辑矛盾的配置。

### `core/src/test/java/org/apache/iceberg/rest/TestHTTPClient.java`

**修改目的**：全面验证代理配置功能的正向路径、认证失败路径及校验逻辑。

**工作逻辑**：

1. 新增 import：`org.apache.hc.client5.http.auth.AuthScope`、`UsernamePasswordCredentials`、`org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider`、`org.apache.hc.core5.http.HttpHost`、`HttpStatus`、`org.mockserver.configuration.Configuration`、`org.mockserver.verify.VerificationTimes`。

2. **`testProxyServer`**：
   ```java
   int proxyPort = 1070;
   try (ClientAndServer proxyServer = startClientAndServer(proxyPort);
       RESTClient clientWithProxy =
           HTTPClient.builder(ImmutableMap.of())
               .uri(URI)
               .withProxy("localhost", proxyPort)
               .build()) {
     String path = "v1/config";
     HttpRequest mockRequest = request("/" + path).withMethod("HEAD");
     HttpResponse mockResponse = response().withStatusCode(200);
     proxyServer.when(mockRequest).respond(mockResponse);
     clientWithProxy.head(path, ImmutableMap.of(), (onError) -> {});
     proxyServer.verify(mockRequest, VerificationTimes.exactly(1));
   }
   ```
   关键点：mockserver 在此既充当代理又充当被代理目标（mockserver 本身可作为正向代理监听）。验证 `proxyServer.verify(mockRequest, exactly(1))` 确认请求确实经过了代理端口。这比仅断言 builder 字段被设置更能证明运行时行为正确。

3. **`testProxyCredentialProviderWithoutProxyServer`**：构造 builder 时只调 `withProxyCredentialsProvider(new BasicCredentialsProvider())` 不调 `withProxy`，断言 build 时抛出 NPE，消息为 `"Invalid http client proxy for proxy credentials provider: null"`。

4. **`testProxyServerWithNullHostname`**：调 `withProxy(null, 1070)`，断言抛 NPE，消息为 `"Invalid hostname for http client proxy: null"`。

5. **`testProxyAuthenticationFailure`**：
   ```java
   HttpHost proxy = new HttpHost(proxyHostName, proxyPort);
   BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
   credentialsProvider.setCredentials(
       new AuthScope(proxy),
       new UsernamePasswordCredentials(authorizedUsername, invalidPassword.toCharArray()));

   try (ClientAndServer proxyServer =
           startClientAndServer(
               new Configuration()
                   .proxyAuthenticationUsername(authorizedUsername)
                   .proxyAuthenticationPassword(authorizedPassword),
               proxyPort);
       RESTClient clientWithProxy =
           HTTPClient.builder(ImmutableMap.of())
               .uri(URI)
               .withProxy(proxyHostName, proxyPort)
               .withProxyCredentialsProvider(credentialsProvider)
               .build()) {
     // 发起 GET 请求
     Assertions.assertThatThrownBy(
             () -> clientWithProxy.get("v1/config", Item.class, ImmutableMap.of(), onError))
         .isInstanceOf(RuntimeException.class)
         .hasMessage("Proxy Authentication Required - 407");
   }
   ```
   关键设计：mockserver 通过 `Configuration.proxyAuthenticationUsername/Password` 配置代理认证，客户端用错误密码，期望收到 407。这验证了代理认证链路：客户端把 `UsernamePasswordCredentials` 绑定到 `AuthScope(proxy)`（即"对此代理服务器使用这套凭证"），HttpClient 在收到 407 后会用此凭证重试，仍然失败，最终把 407 通过 `ErrorHandler` 抛出为 RuntimeException。这覆盖了真实生产中"代理认证失败"的最常见场景。

## 小结

- **成效**：成功为 `HTTPClient` 增加了正向代理与代理认证配置能力。通过 Builder fluent API（`withProxy` + `withProxyCredentialsProvider`）暴露配置入口，在构造器中通过 HttpClient 5 的 `setProxy`/`setDefaultCredentialsProvider` 应用到底层 builder。校验逻辑保证了"配置凭证必须配代理"的合理性。
- **影响范围**：`core` 模块的 `HTTPClient`，所有通过 REST Catalog 访问 Iceberg 的引擎现在可经由企业正向代理访问 Catalog。该改动直接依赖 0672 提交（PR #10053）抽取出的 `configureConnectionManager` 与扩展后的构造器签名。
- **回迁到 1.4.x 的注意事项**：本提交**强依赖** 0672 提交（PR #10053）作为前置——0673 在 0672 改造过的构造器签名（带 `HttpClientConnectionManager connectionManager` 末尾参数）基础上进一步扩展。回迁到 1.4.x 时：
  1. 必须先回迁 0672（PR #10053），再回迁 0673。
  2. 注意 `HttpHost`、`CredentialsProvider`、`BasicCredentialsProvider`、`UsernamePasswordCredentials`、`AuthScope` 均来自 Apache HttpClient 5，1.4.x 已使用该版本，无依赖问题。
  3. 测试中 `mockserver.configuration.Configuration.proxyAuthenticationUsername/Password` API 在某些 mockserver 版本中可能签名不同，回迁时需确认 1.4.x 使用的 mockserver 版本支持这些方法。
  4. 本提交未提供通过 properties 配置代理的能力（仅 Builder API），如果 1.4.x 用户需要在 catalog properties 文件中配置代理（而非编程式），需要另行扩展。
