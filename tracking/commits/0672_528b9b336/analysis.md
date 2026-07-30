# 提交 0672：Core: Allow configuring socket/connection timeout in HTTPClient (#10053)

## 提交信息
- **序号**：0672 / 4088
- **哈希**：528b9b336c1d6e8051b46ee48f0388769f5eda55
- **短哈希**：528b9b336
- **日期**：2024-04-11 03:31:41 -0700
- **作者**：Harish Chandrasekaran
- **提交说明**：Core: Allow configuring socket/connection timeout in HTTPClient (#10053)
- **PR/Issue**：#10053

## 总体目的

Iceberg 的 REST 客户端 `HTTPClient` 用于与 REST Catalog 服务端通信（例如 Iceberg REST Catalog、 Snowflake/Tabular 等托管 Catalog 场景）。在此提交前，`HTTPClient` 内部基于 Apache HttpClient 5 构建 `PoolingHttpClientConnectionManager`，仅可配置最大连接数（`rest.client.connection.max-total`）和单路由最大连接数（`rest.client.connections-per-route`），但**无法配置连接超时（connect timeout）与读取超时（socket timeout）**。

在实际生产部署中，REST Catalog 端可能存在网络抖动、慢响应、网关代理延迟等情况。如果没有连接超时与读取超时，客户端在连接建立阶段或读取响应阶段可能无限期阻塞，导致下游作业（Spark/Flink/Trino 等）的读写请求长时间挂起，无法快速失败、触发重试或上游熔断。这是一个严重的可观测性与稳定性短板。

本提交为 `HTTPClient` 增加两个新的可配置属性：
- `rest.client.connection-timeout-ms`：连接建立超时（毫秒），对应 HttpClient 的 `ConnectionConfig.connectTimeout`。
- `rest.client.socket-timeout-ms`：socket 读取超时（毫秒，即两次数据包之间的最大等待间隔），对应 `ConnectionConfig.socketTimeout`。

通过这两个属性，用户可以根据部署环境的网络特征灵活设置超时阈值，从而避免客户端因网络问题挂起、保障作业稳定性。

## 如何达成设计目的

整体设计思路是把"连接管理器（ConnectionManager）的构建"从 `HTTPClient` 构造器内部抽取出来，并将其改造为可配置化的工厂方法，使超时配置能够通过 `ConnectionConfig` 注入到 `PoolingHttpClientConnectionManager` 中。具体策略如下：

1. **重构构造器签名**：原 `HTTPClient` 构造器接收 `Map<String, String> properties` 并在内部直接构建 `PoolingHttpClientConnectionManager`（设置最大连接数等）。本提交将连接管理器的构建从构造器内部移除，改为由调用方（即 `Builder.build()`）通过新方法 `configureConnectionManager(properties)` 构建后作为参数注入构造器。这一依赖注入改造使得连接管理器的构建逻辑可被外部感知、可被测试单独验证，也为后续的代理服务器支持（PR #10052，本仓库提交 0673）打下了基础——代理相关的连接管理可在同一处扩展。

2. **新增配置读取与 `ConnectionConfig` 构造**：新增 `configureConnectionConfig(properties)` 方法读取两个新属性。如果两个属性都为空（未配置），返回 `null`，此时连接管理器构建过程不设置默认 `ConnectionConfig`，保持与改动前一致的默认行为；否则根据已配置项构造 `ConnectionConfig.Builder`，仅设置已配置的超时项，最终 `build()` 出 `ConnectionConfig`。

3. **将 `ConnectionConfig` 注入连接管理器**：在 `configureConnectionManager` 中通过 `setDefaultConnectionConfig(connectionConfig)` 把构造出的 `ConnectionConfig` 设为连接池的默认配置，所有从池中借出的连接都会应用此配置。

4. **使用 `PropertyUtil.propertyAsNullableLong`/`propertyAsNullableInt`** 而非 `propertyAsInt`：这是关键设计点。`propertyAsInt` 在属性不存在时返回默认值（int 类型默认 0），无法区分"未配置"与"显式配置为 0"。`propertyAsNullableLong/Int` 在属性缺失时返回 `null`，使代码能够区分"用户未配置"（保持默认）与"用户显式设置了一个值"（应用此值）。这一选择让新增的两个超时参数成为真正的"可选配置"，不配置时完全不改变原有行为。

5. **测试覆盖**：新增三类测试：超时值正确设置（`testSocketAndConnectionTimeoutSet`）、socket 超时实际触发 `SocketTimeoutException`（`testSocketTimeout`，用 mockserver 延迟 5 秒响应、客户端设 2 秒超时来验证）、以及非法值校验（`testInvalidTimeout`，参数化测试覆盖两个属性，验证非数字抛 `NumberFormatException`、负数抛 `IllegalArgumentException`）。参数化测试以单一方法覆盖两个新属性，避免重复代码。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/HTTPClient.java`

**修改目的**：抽取连接管理器构建逻辑、新增可配置的连接/socket 超时支持。

**工作逻辑**：

1. 新增两个常量（带 `@VisibleForTesting` 注解，便于测试引用）：
   ```java
   @VisibleForTesting
   static final String REST_CONNECTION_TIMEOUT_MS = "rest.client.connection-timeout-ms";
   @VisibleForTesting
   static final String REST_SOCKET_TIMEOUT_MS = "rest.client.socket-timeout-ms";
   ```

2. 新增 import：`java.util.concurrent.TimeUnit`、`org.apache.hc.client5.http.config.ConnectionConfig`。

3. **构造器签名变更**：在原参数列表 `(uri, baseHeaders, objectMapper, requestInterceptor, properties)` 末尾新增 `HttpClientConnectionManager connectionManager` 参数。原构造器内部约 11 行构建 `PoolingHttpClientConnectionManager` 的代码被整体删除，改为直接使用传入的 `connectionManager` 调用 `clientBuilder.setConnectionManager(connectionManager)`。其余逻辑（baseHeaders 注入、requestInterceptor、retryStrategy、auth 等）保持不变。

4. **新增 `configureConnectionManager(Map<String, String> properties)` 静态方法**：
   ```java
   static HttpClientConnectionManager configureConnectionManager(Map<String, String> properties) {
     PoolingHttpClientConnectionManagerBuilder connectionManagerBuilder =
         PoolingHttpClientConnectionManagerBuilder.create();
     ConnectionConfig connectionConfig = configureConnectionConfig(properties);
     if (connectionConfig != null) {
       connectionManagerBuilder.setDefaultConnectionConfig(connectionConfig);
     }
     return connectionManagerBuilder
         .useSystemProperties()
         .setMaxConnTotal(Integer.getInteger(REST_MAX_CONNECTIONS, REST_MAX_CONNECTIONS_DEFAULT))
         .setMaxConnPerRoute(
             PropertyUtil.propertyAsInt(
                 properties, REST_MAX_CONNECTIONS_PER_ROUTE, REST_MAX_CONNECTIONS_PER_ROUTE_DEFAULT))
         .build();
   }
   ```
   该方法保留了原有 `setMaxConnTotal`、`setMaxConnPerRoute`、`useSystemProperties` 三项配置，仅在 `connectionConfig != null` 时额外调用 `setDefaultConnectionConfig`。当用户未配置任何超时时，行为与改动前完全一致。

5. **新增 `configureConnectionConfig(Map<String, String> properties)` 静态方法**（`@VisibleForTesting`）：
   ```java
   @VisibleForTesting
   static ConnectionConfig configureConnectionConfig(Map<String, String> properties) {
     Long connectionTimeoutMillis =
         PropertyUtil.propertyAsNullableLong(properties, REST_CONNECTION_TIMEOUT_MS);
     Integer socketTimeoutMillis =
         PropertyUtil.propertyAsNullableInt(properties, REST_SOCKET_TIMEOUT_MS);

     if (connectionTimeoutMillis == null && socketTimeoutMillis == null) {
       return null;
     }

     ConnectionConfig.Builder connConfigBuilder = ConnectionConfig.custom();

     if (connectionTimeoutMillis != null) {
       connConfigBuilder.setConnectTimeout(connectionTimeoutMillis, TimeUnit.MILLISECONDS);
     }

     if (socketTimeoutMillis != null) {
       connConfigBuilder.setSocketTimeout(socketTimeoutMillis, TimeUnit.MILLISECONDS);
     }

     return connConfigBuilder.build();
   }
   ```
   该方法的核心：用 `propertyAsNullableLong/Int` 区分未配置（`null`）与显式配置；两个都未配置时返回 `null` 让调用方跳过 `setDefaultConnectionConfig`；只要任一项被配置就构造 `ConnectionConfig`，仅设置非空的项。这保证了"按需配置"的灵活性——用户可以只配 socket 超时、只配连接超时，或两者都配。

6. **`Builder.build()` 改造**：原本调用 `new HTTPClient(uri, baseHeaders, mapper, interceptor, properties)`，改为额外传入 `configureConnectionManager(properties)`：
   ```java
   return new HTTPClient(
       uri,
       baseHeaders,
       mapper,
       interceptor,
       properties,
       configureConnectionManager(properties));
   ```

### `core/src/test/java/org/apache/iceberg/rest/TestHTTPClient.java`

**修改目的**：验证新增超时配置功能的正确性与异常处理。

**工作逻辑**：

1. 新增 import：`java.net.SocketTimeoutException`、`java.util.concurrent.TimeUnit`、`org.apache.hc.client5.http.config.ConnectionConfig`、JUnit5 参数化测试相关类 `ParameterizedTest`、`ValueSource`。

2. **`testSocketAndConnectionTimeoutSet`**：构造 properties 同时设置 `rest.client.connection-timeout-ms=10` 与 `rest.client.socket-timeout-ms=10`，调用 `HTTPClient.configureConnectionConfig(properties)`，断言返回的 `ConnectionConfig` 非空，且其 `getConnectTimeout().getDuration()` 与 `getSocketTimeout().getDuration()` 分别等于 10。验证配置值正确传入 HttpClient 的 ConnectionConfig。

3. **`testSocketTimeout`**：仅设置 `rest.client.socket-timeout-ms=2000`（2 秒），在 mockserver 上为路径 `socket/timeout/path` 注册一个延迟 5 秒才返回的响应。构造 client 后发起 `HEAD` 请求，断言抛出的异常 cause 是 `SocketTimeoutException`，且消息为 `Read timed out`。这验证了 socket 超时确实在运行时生效——2 秒后客户端会主动中断读取并抛出异常，而不是等待完整的 5 秒。这种端到端验证比仅断言配置被设置更可信。

4. **`testInvalidTimeout`**（参数化测试）：使用 `@ValueSource(strings = {HTTPClient.REST_CONNECTION_TIMEOUT_MS, HTTPClient.REST_SOCKET_TIMEOUT_MS})` 让同一测试方法分别对两个属性进行验证：
   - 非数字值（如 `"invalidMs"`）：构造 builder 时应抛 `NumberFormatException`，消息为 `For input string: "invalidMs"`（这是因为 `PropertyUtil.propertyAsNullableLong/Int` 内部仍走 `Long.parseLong`/`Integer.parseInt`）。
   - 负值（`"-1"`）：应抛 `IllegalArgumentException`，消息为 `duration must not be negative: -1`（HttpClient 5 的 `ConnectionConfig` 校验不允许负数 duration）。

   参数化测试用同一逻辑覆盖两个属性，避免代码重复，确保两个新属性在异常处理上行为一致。

## 小结

- **成效**：成功为 `HTTPClient` 增加了可配置的连接超时与 socket 超时能力。通过引入 `ConnectionConfig` 抽象并把连接管理器构建从构造器抽离到工厂方法，超时配置得以通过 properties 注入到连接池默认配置中。代码保持了"未配置即不改变默认行为"的向后兼容性。
- **影响范围**：`core` 模块的 `HTTPClient`，以及所有通过 REST Catalog 访问 Iceberg 的下游引擎（Spark/Flink/Trino 等）现在均可通过 `rest.client.connection-timeout-ms` 与 `rest.client.socket-timeout-ms` 两个新属性控制网络超时。该改动同时为后续 PR #10052（代理支持，提交 0673）的连接管理器可注入化改造做好了准备。
- **回迁到 1.4.x 的注意事项**：1.4.x 分支的 `HTTPClient.java` 与本提交前的 main 分支版本结构一致（构造器内部构建连接管理器），可直接 cherry-pick 本提交。需注意：
  1. 依赖的 `PropertyUtil.propertyAsNullableLong`/`propertyAsNullableInt` 方法必须存在于 1.4.x 中（若不存在需先回迁这些工具方法）。
  2. 构造器签名变更会破坏任何直接调用 `new HTTPClient(...)` 的下游代码——但 `HTTPClient` 构造器是 `private`，仅由内部 `Builder.build()` 调用，因此实际影响极小。
  3. 若计划同时回迁 0673（代理支持），建议本提交与 0673 一起回迁，因为 0673 在本提交的构造器签名基础上进一步增加了 `proxy`/`proxyCredsProvider` 参数，单独回迁 0673 需要本提交的连接管理器抽取作为前置。
