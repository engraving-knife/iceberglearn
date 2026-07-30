# 提交 1308：GCS: Refresh vended credentials (#11282)

## 提交信息

- **序号**：1308 / 4088
- **哈希**：5359bea71bceaad35897ac82573d73e3c7e47f71
- **短哈希**：5359bea71
- **日期**：2024-10-30（Wed Oct 30 07:09:42 2024 +0100）
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：GCS: Refresh vended credentials
- **PR/Issue**：#11282

## 总体目的

Iceberg 的 vended credentials 机制：由 REST catalog / 服务端下发短期凭证（"vend" 给客户端），客户端用这些凭证直接访问对象存储。这些凭证通常有较短的有效期（分钟级到小时级），过期前必须刷新，否则文件 IO 会因 401/403 失败。

对 GCS 而言，`GCSFileIO` 此前通过 `GCPProperties` 接收一对静态凭证：

- `gcs.oauth2.token`：OAuth2 access token 字符串；
- `gcs.oauth2.token-expires-at`：token 过期时间戳（毫秒）。

然后用 `OAuth2Credentials.create(accessToken)` 包装成不带头部刷新能力的 `OAuth2Credentials`，传给 `StorageOptions`。这种实现只能使用最初下发的 token，**到期后不会自动刷新**——长任务、长会话场景下 GCS 访问会因 token 过期而失败。

本提交为 GCSFileIO 增加 token 自动刷新能力：当配置了 REST credentials 端点（`gcs.oauth2.refresh-credentials-endpoint`）且未显式禁用刷新时，把 `OAuth2Credentials` 升级为 `OAuth2CredentialsWithRefresh`，并注入一个自定义的 `OAuth2RefreshCredentialsHandler`。后者在 token 过期时回调 REST catalog 的 `GET /v1/credentials` 接口，解析 `LoadCredentialsResponse`，取 `prefix` 以 `gs` 开头的那个 `Credential`，重新构造 `AccessToken` 给 GCS 客户端。

这与 AWS S3FileIO、Azure ADLSFileIO、阿里云 OSS 等已有的 vended credentials 刷新机制对齐，使 GCSFileIO 也能在长任务中持续访问 GCS。

## 如何达成设计目的

通过四层结构实现：

1. **配置层 `GCPProperties`**：新增两个配置项
   - `gcs.oauth2.refresh-credentials-endpoint`：REST catalog 上拉取新凭证的相对路径（如 `/v1/credentials`）；
   - `gcs.oauth2.refresh-credentials-enabled`：是否启用刷新，默认 `true`，允许调用方在已知 token 寿命足够长时关闭刷新。
2. **刷新处理器 `OAuth2RefreshCredentialsHandler`**：实现 `OAuth2CredentialsWithRefresh.OAuth2RefreshHandler`，在 GCS 客户端检测到 token 过期时被回调，发起 HTTP GET 拉取新凭证并解析。
3. **`GCSFileIO` 集成**：在构造 `Storage` 时，若启用刷新且配置了端点，则用 `OAuth2CredentialsWithRefresh.newBuilder().setAccessToken(...).setRefreshHandler(...).build()`，否则维持原 `OAuth2Credentials.create(accessToken)` 行为。
4. **测试**：用 MockServer 启动本地 HTTP 服务，模拟 REST catalog 的 `/v1/credentials` 响应，覆盖成功刷新、空凭证、缺字段、多 GCS 凭证、4xx 错误、URI 非法等分支。

## 修改详情

### `build.gradle`（修改，+3 行）

**修改目的**：为 `iceberg-gcp` 模块测试引入 MockServer 与 iceberg-core testArtifacts。

**工作逻辑**：

- `testImplementation project(path: ':iceberg-core', configuration: 'testArtifacts')`：复用 core 模块的测试工具（如 `AssertJ` 扩展、`PropertyUtil` 测试辅助等）；
- `testImplementation libs.mockserver.netty`：MockServer 的 Netty 实现，启动本地 HTTP mock；
- `testImplementation libs.mockserver.client.java`：MockServer 的 Java 客户端，用于设置期望请求/响应并验证。

### `gcp/src/main/java/org/apache/iceberg/gcp/GCPProperties.java`（修改，+20 行）

**修改目的**：新增 refresh-credentials-endpoint 与 refresh-credentials-enabled 两个配置项的解析与访问器。

**工作逻辑**：

- 新增两个 public 常量（配置键名）：
  - `GCS_OAUTH2_REFRESH_CREDENTIALS_ENDPOINT = "gcs.oauth2.refresh-credentials-endpoint"`
  - `GCS_OAUTH2_REFRESH_CREDENTIALS_ENABLED = "gcs.oauth2.refresh-credentials-enabled"`，javadoc 说明默认 true。
- 新增字段：`String gcsOauth2RefreshCredentialsEndpoint;`、`boolean gcsOauth2RefreshCredentialsEnabled;`。
- 在构造函数 `GCPProperties(Map<String, String> properties)` 中解析：
  - `gcsOauth2RefreshCredentialsEndpoint = properties.get(GCS_OAUTH2_REFRESH_CREDENTIALS_ENDPOINT);`（可能为 null）；
  - `gcsOauth2RefreshCredentialsEnabled = PropertyUtil.propertyAsBoolean(properties, GCS_OAUTH2_REFRESH_CREDENTIALS_ENABLED, true);`（默认 true）。
- 新增访问器：
  - `Optional<String> oauth2RefreshCredentialsEndpoint()`：返回 `Optional.ofNullable(...)`，让调用方区分"未配置"与"配置为空串"；
  - `boolean oauth2RefreshCredentialsEnabled()`：返回布尔值。

### `gcp/src/main/java/org/apache/iceberg/gcp/gcs/GCSFileIO.java`（修改，+11/-1 行）

**修改目的**：在 token + expiresAt 已配置的分支内，根据是否启用刷新 + 是否配置端点，选择 `OAuth2CredentialsWithRefresh` 或 `OAuth2Credentials`。

**工作逻辑**：

原代码在 `gcsOAuth2Token != null` 分支里：

```java
AccessToken accessToken = new AccessToken(token, gcpProperties.oauth2TokenExpiresAt().orElse(null));
builder.setCredentials(OAuth2Credentials.create(accessToken));
```

修改后：

```java
AccessToken accessToken = new AccessToken(token, gcpProperties.oauth2TokenExpiresAt().orElse(null));
if (gcpProperties.oauth2RefreshCredentialsEnabled()
    && gcpProperties.oauth2RefreshCredentialsEndpoint().isPresent()) {
  builder.setCredentials(
      OAuth2CredentialsWithRefresh.newBuilder()
          .setAccessToken(accessToken)
          .setRefreshHandler(OAuth2RefreshCredentialsHandler.create(properties))
          .build());
} else {
  builder.setCredentials(OAuth2Credentials.create(accessToken));
}
```

- 仅当同时满足"启用刷新"且"配置了端点"时才走刷新路径，保证向后兼容（未配置端点时行为不变）；
- `OAuth2RefreshCredentialsHandler.create(properties)` 把整个 `properties` Map 传给 handler，因为 handler 后续要从中读取 `OAuth2Properties.TOKEN`（用于鉴权访问 REST catalog）和 `GCS_OAUTH2_REFRESH_CREDENTIALS_ENDPOINT`（HTTP 请求 URI）；
- `OAuth2CredentialsWithRefresh` 是 google-auth-library-oauth2-http 提供的类，会在 `refresh()` 时调用注入的 `OAuth2RefreshHandler.refreshAccessToken()`，GCS Storage 客户端内部会在 token 过期前自动触发。

### `gcp/src/main/java/org/apache/iceberg/gcp/gcs/OAuth2RefreshCredentialsHandler.java`（新增，99 行）

**修改目的**：实现 GCS token 刷新逻辑：调用 REST catalog 的 `/v1/credentials` 拉取新 GCS 凭证。

**工作逻辑**：

- `public class OAuth2RefreshCredentialsHandler implements OAuth2CredentialsWithRefresh.OAuth2RefreshHandler`；
- 字段 `Map<String, String> properties`；
- 私有构造 + 静态工厂 `create(Map<String, String> properties)`，构造时校验 `properties.get(GCS_OAUTH2_REFRESH_CREDENTIALS_ENDPOINT) != null`，否则抛 `IllegalArgumentException("Invalid credentials endpoint: null")`。
- 核心方法 `refreshAccessToken()`：
  1. 用 `httpClient()` 创建 `RESTClient`（基于 `HTTPClient.builder(properties).uri(endpoint).build()`），try-with-resources 保证关闭；
  2. 调用 `client.get(endpoint, null, LoadCredentialsResponse.class, OAuth2Util.authHeaders(token), ErrorHandlers.defaultErrorHandler())`，其中：
     - 第一个 `endpoint` 是请求路径（相对于 client 的 base URI）；
     - `null` 表示无 query 参数；
     - `OAuth2Util.authHeaders(properties.get(OAuth2Properties.TOKEN))` 把现有的 catalog bearer token 加到 Authorization 头，鉴权访问 REST catalog；
     - `LoadCredentialsResponse.class` 是 Iceberg REST 模型定义的响应类型，包含一个 `credentials` 列表（每个 `Credential` 有 `prefix` 与 `config` Map）。
  3. 用 `response.credentials().stream().filter(c -> c.prefix().startsWith("gs")).collect(Collectors.toList())` 筛选 GCS 凭证（prefix 以 `gs` 开头，兼容 `gs`、`gs://bucket`、`gs://custom-prefix` 等形式）。
  4. 校验：
     - 列表非空，否则抛 `IllegalStateException("Invalid GCS Credentials: empty")`；
     - 列表大小为 1，否则抛 `IllegalStateException("Invalid GCS Credentials: only one GCS credential should exist")`——这保证 REST catalog 不会返回多条相互冲突的 GCS 凭证。
  5. 取唯一的 `Credential gcsCredential`，用 `checkCredential` 校验其 `config` 同时包含 `gcs.oauth2.token` 与 `gcs.oauth2.token-expires-at`，否则抛 `IllegalStateException("Invalid GCS Credentials: %s not set")`。
  6. 取出 token 字符串与 expiresAt 毫秒时间戳，构造 `new AccessToken(token, new Date(Long.parseLong(expiresAt)))` 返回。
- 私有方法 `httpClient()`：`HTTPClient.builder(properties).uri(endpoint).build()`，复用 Iceberg 的 REST HTTP 客户端基础设施。

### `gcp/src/test/java/org/apache/iceberg/gcp/GCPPropertiesTest.java`（修改，+30 行）

**修改目的**：覆盖新增的两个配置项的解析。

**工作逻辑**：

- `refreshCredentialsEndpointSet`：只配置 endpoint，断言 `oauth2RefreshCredentialsEnabled()` 默认为 true、`oauth2RefreshCredentialsEndpoint()` 包含 `/v1/credentials`。
- `refreshCredentialsEndpointSetButRefreshDisabled`：同时配置 endpoint 与 `enabled=false`，断言 enabled 为 false、endpoint 仍可读出。验证两者解耦。

### `gcp/src/test/java/org/apache/iceberg/gcp/gcs/GCSFileIOTest.java`（修改，+47 行）

**修改目的**：验证 `GCSFileIO` 在两种配置下生成的 `Storage` 凭证类型正确。

**工作逻辑**：

- `refreshCredentialsEndpointSet`：初始化 `GCSFileIO` 时配置 token、过期时间（5 分钟后）、endpoint `/v1/credentials`，调用 `fileIO.client()` 取得 `Storage`，断言 `client.getOptions().getCredentials() isInstanceOf(OAuth2CredentialsWithRefresh.class`——证明走刷新分支。
- `refreshCredentialsEndpointSetButRefreshDisabled`：在上一例基础上额外配置 `enabled=false`，断言 `getCredentials() isInstanceOf(OAuth2Credentials.class`——证明回落到非刷新分支。注意 `OAuth2CredentialsWithRefresh` 是 `OAuth2Credentials` 的子类，所以这里用 `isInstanceOf(OAuth2Credentials.class)` 而非 `isNotInstanceOf(OAuth2CredentialsWithRefresh.class)`，更精确地表达"非刷新实现"。

### `gcp/src/test/java/org/apache/iceberg/gcp/gcs/OAuth2RefreshCredentialsHandlerTest.java`（新增，264 行）

**修改目的**：用 MockServer 起本地 HTTP 服务模拟 REST catalog，覆盖 `OAuth2RefreshCredentialsHandler` 的全部路径。

**工作逻辑**：

- 静态启动 `ClientAndServer mockServer = startClientAndServer(3333)`，URI = `http://127.0.0.1:3333/v1/credentials`，`@BeforeEach` 重置 mock。
- 测试用例：
  - `invalidOrMissingUri`：`create(emptyMap)` 抛 `IllegalArgumentException("Invalid credentials endpoint: null")`；`create(endpoint="invalid uri").refreshAccessToken()` 抛 `RESTException("Failed to create request URI from base invalid uri")`。
  - `badRequest`：mock 返回 400，断言抛 `BadRequestException("Malformed request...")`。
  - `noGcsCredentialInResponse`：mock 返回空的 `LoadCredentialsResponse`，断言抛 `IllegalStateException("Invalid GCS Credentials: empty")`。
  - `noGcsToken`：返回 prefix=`gs` 但 config 缺 `gcs.oauth2.token`（只有 expires-at），断言抛 `IllegalStateException("Invalid GCS Credentials: gcs.oauth2.token not set")`。
  - `tokenWithoutExpiration`：返回 prefix=`gs` 但 config 缺 `gcs.oauth2.token-expires-at`（只有 token），断言抛 `IllegalStateException("Invalid GCS Credentials: gcs.oauth2.token-expires-at not set")`。
  - `tokenWithExpiration`：返回完整凭证，断言：
    - 第一次 `refreshAccessToken()` 返回的 `AccessToken` 的 `tokenValue` 与 `expirationTime` 与 mock 数据一致；
    - 第二次调用返回新对象（`isNotSameAs`）；
    - `mockServer.verify(mockRequest, VerificationTimes.exactly(2))` 验证 HTTP 调用了 2 次。
  - `multipleGcsCredentials`：返回三个 prefix 都以 `gs` 开头的凭证（`gs`、`gs://my-custom-prefix/xyz/long-prefix`、`gs://my-custom-prefix/xyz`），断言抛 `IllegalStateException("Invalid GCS Credentials: only one GCS credential should exist")`——验证 `startsWith("gs")` 的前缀匹配会捕获所有以 gs 开头的凭证，迫使 REST catalog 必须保证 GCS 凭证唯一。

## 小结

- **成效**：`GCSFileIO` 在配置 `gcs.oauth2.refresh-credentials-endpoint` 后，token 过期时通过 `OAuth2RefreshCredentialsHandler` 自动回调 REST catalog 拉取新凭证，不再因短期 token 失效而中断长任务。与 S3/Azure 等模块的 vended credentials 刷新机制对齐。配套 264 行 MockServer 测试覆盖了正常刷新与各类异常响应。
- **影响范围**：仅 `iceberg-gcp` 模块。生产代码新增 1 个类（`OAuth2RefreshCredentialsHandler`）、修改 2 个类（`GCPProperties` 加配置项、`GCSFileIO` 加分支）；测试新增 1 个类、修改 2 个类。无对 API 模块的修改，无格式变更。向后兼容：未配置 endpoint 时行为与之前完全一致。
- **回迁到 1.4.x 的注意事项**：
  1. 依赖 `LoadCredentialsResponse`、`Credential`、`ImmutableCredential`、`ImmutableLoadCredentialsResponse`、`LoadCredentialsResponseParser`、`HTTPClient`、`OAuth2Util`、`OAuth2Properties`、`ErrorHandlers` 等 REST 模型类，1.4.x 上若已有 REST catalog 模块则这些类应当存在，需确认包路径一致；
  2. 依赖 `google-auth-library-oauth2-http` 提供的 `OAuth2CredentialsWithRefresh`，需确认 1.4.x 的 GCP 依赖版本中该类已可用（Google Auth 库 0.20.0+ 提供）；
  3. `libs.mockserver.netty` 与 `libs.mockserver.client.java` 依赖需在 1.4.x 的 `gradle/libs.versions.toml` 中存在，否则需先添加；
  4. 配置项命名遵循 `gcs.oauth2.refresh-credentials-endpoint` 与 `gcs.oauth2.refresh-credentials-enabled`，回迁后需在 1.4.x 的文档与 `GCPProperties` javadoc 中同步登记；
  5. `OAuth2RefreshCredentialsHandler` 假设 REST catalog 返回的 `Credential.config` 中以字符串形式存放 `gcs.oauth2.token` 与 `gcs.oauth2.token-expires-at` 两个键，这与 main 分支 REST 协议一致，1.4.x 若 REST 协议版本不同需对齐；
  6. 该刷新机制仅适用于 vended credentials 场景，不影响使用 GCP 默认应用凭证（ADC）或服务账号密钥的部署。
