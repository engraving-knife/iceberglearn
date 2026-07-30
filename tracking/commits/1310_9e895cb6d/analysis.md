# 提交 1310：AWS: Refresh vended credentials (#11389)

## 提交信息

- **序号**：1310 / 4088
- **哈希**：9e895cb6dff9dcd2a117a4f5e197f0235047ff54
- **短哈希**：9e895cb6d
- **日期**：2024-10-30（Wed Oct 30 10:29:45 2024 +0100）
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：AWS: Refresh vended credentials
- **PR/Issue**：#11389

## 总体目的

与 1308（GCS: Refresh vended credentials）对应，本提交为 AWS S3FileIO 增加 vended credentials 自动刷新能力。

Iceberg 的 vended credentials 机制：REST catalog / 服务端下发短期凭证给客户端，客户端用这些凭证直接访问对象存储。AWS 场景下，`S3FileIO` 此前通过 `AwsClientProperties.credentialsProvider(accessKeyId, secretAccessKey, sessionToken)` 接收一组静态 AWS 凭证（access key + secret key + session token），并包装为 `StaticCredentialsProvider`。这种实现：

1. **不支持过期刷新**：session token 通常寿命较短（分钟到小时级），过期后 S3 访问会因 403 失败；
2. **不调用 REST catalog 的 `/v1/credentials` 端点**：即便配置了 REST catalog 也无法主动续约。

本提交新增 `VendedCredentialsProvider`，实现 AWS SDK 的 `AwsCredentialsProvider` 接口。当配置了 `client.refresh-credentials-endpoint` 且未禁用刷新时，`AwsClientProperties` 会优先返回 `VendedCredentialsProvider` 而非 `StaticCredentialsProvider`。后者内部用 AWS SDK 提供的 `CachedSupplier` 缓存当前凭证，并在过期前 5 分钟主动 prefetch、过期后强制刷新，每次刷新都回调 REST catalog 的 `GET /v1/credentials` 拉取新的 S3 凭证。

这与 GCS（1308）、Azure 等模块的 vended credentials 刷新机制对齐，使 S3FileIO 在长任务（如大型 Spark/Flink 作业）中能持续访问 S3 而不中断。

## 如何达成设计目的

通过四层结构实现：

1. **配置层 `AwsClientProperties`**：新增两个配置项
   - `client.refresh-credentials-endpoint`：REST catalog 上拉取新凭证的 URI（绝对或相对）；
   - `client.refresh-credentials-enabled`：是否启用刷新，默认 `true`，允许调用方在已知 token 寿命足够时关闭刷新。
   在 `credentialsProvider(accessKeyId, secretAccessKey, sessionToken)` 方法开头检查这两个配置，若启用且 endpoint 非空，则把 endpoint 注入 `clientCredentialsProviderProperties`（key 为 `VendedCredentialsProvider.URI = "credentials.uri"`），并通过 `credentialsProvider(VendedCredentialsProvider.class.getName())` 走动态加载路径实例化 `VendedCredentialsProvider`。
2. **配置层 `S3FileIOProperties`**：新增包级私有常量 `SESSION_TOKEN_EXPIRES_AT_MS = "s3.session-token-expires-at-ms"`，作为 REST 响应中 S3 凭证过期时间戳的键名。仅 `VendedCredentialsProvider` 使用。
3. **凭证提供者 `VendedCredentialsProvider`**：实现 `AwsCredentialsProvider` 与 `SdkAutoCloseable`，内部用 `CachedSupplier<AwsCredentials>` 缓存与刷新。每次 `resolveCredentials()` 返回缓存值；缓存基于 `RefreshResult` 的 `staleTime`（过期时间）与 `prefetchTime`（过期前 5 分钟）自动决定是否刷新。
4. **测试**：
   - `AwsClientPropertiesTest` 新增 2 个用例：验证启用刷新时返回 `VendedCredentialsProvider`、禁用刷新时返回 `StaticCredentialsProvider`。
   - `TestVendedCredentialsProvider` 用 MockServer 模拟 REST catalog，覆盖正常刷新、过期、缺字段、多凭证、URI 非法等分支。

## 修改详情

### `aws/src/main/java/org/apache/iceberg/aws/AwsClientProperties.java`（修改，+34/-5 行）

**修改目的**：新增 refresh-credentials-endpoint / refresh-credentials-enabled 配置项，并在 `credentialsProvider(...)` 优先返回 `VendedCredentialsProvider`。

**工作逻辑**：

- 新增 import `VendedCredentialsProvider`；
- 新增两个 public 常量：
  - `REFRESH_CREDENTIALS_ENDPOINT = "client.refresh-credentials-endpoint"`，javadoc 指出设置后会用 `VendedCredentialsProvider` 拉取并刷新 vended credentials；
  - `REFRESH_CREDENTIALS_ENABLED = "client.refresh-credentials-enabled"`，默认 true。
- 新增字段 `private final String refreshCredentialsEndpoint;` 与 `private final boolean refreshCredentialsEnabled;`：
  - 无参构造器中分别赋 `null` 与 `true`；
  - `Map` 构造器中：`refreshCredentialsEndpoint = properties.get(REFRESH_CREDENTIALS_ENDPOINT)`；`refreshCredentialsEnabled = PropertyUtil.propertyAsBoolean(properties, REFRESH_CREDENTIALS_ENABLED, true)`。
- 在 `credentialsProvider(String accessKeyId, String secretAccessKey, String sessionToken)` 方法**最前面**插入：
  ```java
  if (refreshCredentialsEnabled && !Strings.isNullOrEmpty(refreshCredentialsEndpoint)) {
    clientCredentialsProviderProperties.put(VendedCredentialsProvider.URI, refreshCredentialsEndpoint);
    return credentialsProvider(VendedCredentialsProvider.class.getName());
  }
  ```
  即：若启用刷新且 endpoint 已配置，把 endpoint 注入 provider properties 并通过 `credentialsProvider(String className)` 动态加载 `VendedCredentialsProvider`（该重载会调用 `VendedCredentialsProvider.create(Map<String, String>)`）。后续的 accessKey/secretKey/sessionToken 静态凭证分支仅在未启用刷新时才执行。
- 更新方法 javadoc，新增对 `refreshCredentialsEndpoint` 优先级的说明。

### `aws/src/main/java/org/apache/iceberg/aws/s3/S3FileIOProperties.java`（修改，+7 行）

**修改目的**：新增 `SESSION_TOKEN_EXPIRES_AT_MS` 常量供 `VendedCredentialsProvider` 读取过期时间。

**工作逻辑**：

```java
/**
 * Configure the expiration time in millis of the static session token used to access S3FileIO.
 * This expiration time is currently only used in {@link VendedCredentialsProvider} for refreshing
 * vended credentials.
 */
static final String SESSION_TOKEN_EXPIRES_AT_MS = "s3.session-token-expires-at-ms";
```

包级私有（无 `public`），仅同包的 `VendedCredentialsProvider` 与测试可访问。javadoc 明确该常量当前仅被 `VendedCredentialsProvider` 使用。

### `aws/src/main/java/org/apache/iceberg/aws/s3/VendedCredentialsProvider.java`（新增，138 行）

**修改目的**：实现 AWS SDK 的 `AwsCredentialsProvider`，按需拉取并缓存 S3 vended credentials。

**工作逻辑**：

- `public class VendedCredentialsProvider implements AwsCredentialsProvider, SdkAutoCloseable`；
- 常量：`public static final String URI = "credentials.uri";`（在 `AwsClientProperties` 中作为 properties key 注入 endpoint）；
- 字段：
  - `private volatile HTTPClient client;`：懒加载的 REST HTTP 客户端，`volatile` 保证双重检查锁定的可见性；
  - `private final Map<String, String> properties;`：完整 properties 副本（含 `credentials.uri`、`OAuth2Properties.TOKEN` 等）；
  - `private final CachedSupplier<AwsCredentials> credentialCache;`：AWS SDK 提供的缓存供应商。
- 私有构造 + 静态工厂 `create(Map<String, String> properties)`：
  - 校验 `properties != null`（否则 `IllegalArgumentException("Invalid properties: null")`）；
  - 校验 `properties.get(URI) != null`（否则 `IllegalArgumentException("Invalid URI: null")`）；
  - `credentialCache = CachedSupplier.builder(this::refreshCredential).cachedValueName(...).build()`，把刷新逻辑委托给 `refreshCredential` 方法。
- `resolveCredentials()`：`return credentialCache.get();`——`CachedSupplier` 内部根据 `RefreshResult.staleTime` 与 `prefetchTime` 决定是否调用 `refreshCredential` 重新拉取。
- `close()`：`IoUtils.closeQuietly(client, null)` 关闭 HTTP 客户端，`credentialCache.close()` 释放缓存资源（实现 `SdkAutoCloseable` 便于在 try-with-resources 中使用）。
- `httpClient()`：双重检查锁定懒加载 `HTTPClient`：
  ```java
  if (null == client) {
    synchronized (this) {
      if (null == client) {
        client = HTTPClient.builder(properties).uri(properties.get(URI)).build();
      }
    }
  }
  return client;
  ```
  `volatile` + synchronized + 双重检查保证多线程下只创建一个 client。
- `fetchCredentials()`：
  ```java
  return httpClient().get(
      properties.get(URI),                        // 请求路径
      null,                                        // 无 query 参数
      LoadCredentialsResponse.class,               // 响应类型
      OAuth2Util.authHeaders(properties.get(OAuth2Properties.TOKEN)),  // Authorization: Bearer <catalog token>
      ErrorHandlers.defaultErrorHandler());
  ```
  与 GCS 的 `OAuth2RefreshCredentialsHandler.refreshAccessToken` 完全对称。
- `refreshCredential()`（核心，返回 `RefreshResult<AwsCredentials>`）：
  1. 调 `fetchCredentials()` 拉取 `LoadCredentialsResponse`；
  2. 用 `response.credentials().stream().filter(c -> c.prefix().startsWith("s3")).collect(Collectors.toList())` 筛选 S3 凭证（prefix 以 `s3` 开头，兼容 `s3`、`s3://bucket`、`s3://custom-uri/...` 等形式）；
  3. 校验：
     - 列表非空，否则 `IllegalStateException("Invalid S3 Credentials: empty")`；
     - 列表大小为 1，否则 `IllegalStateException("Invalid S3 Credentials: only one S3 credential should exist")`；
  4. 取唯一凭证 `s3Credential`，用 `checkCredential` 校验其 `config` 同时包含 `s3.access-key-id`、`s3.secret-access-key`、`s3.session-token`、`s3.session-token-expires-at-ms` 四个键，否则抛 `IllegalStateException("Invalid S3 Credentials: %s not set")`；
  5. 取出 accessKeyId、secretAccessKey、sessionToken、tokenExpiresAtMillis，构造 `Instant expiresAt = Instant.ofEpochMilli(Long.parseLong(tokenExpiresAtMillis))`，`Instant prefetchAt = expiresAt.minus(5, ChronoUnit.MINUTES)`（过期前 5 分钟主动 prefetch）；
  6. 构造 `AwsSessionCredentials.builder().accessKeyId(...).secretAccessKey(...).sessionToken(...).expirationTime(expiresAt).build()`，注意这里把 `AwsSessionCredentials` 强转为 `AwsCredentials`（前者是后者的子接口，带 sessionToken 与 expirationTime）；
  7. 返回 `RefreshResult.builder((AwsCredentials) awsSessionCreds).staleTime(expiresAt).prefetchTime(prefetchAt).build()`：
     - `staleTime` = 过期时间，到点后缓存值视为 stale，下次 `get` 强制刷新；
     - `prefetchTime` = 过期前 5 分钟，`CachedSupplier` 在后台异步 prefetch，避免请求时阻塞。
- `checkCredential(Credential credential, String property)`：校验 `credential.config().containsKey(property)`，否则抛 `IllegalStateException`。

### `aws/src/test/java/org/apache/iceberg/aws/AwsClientPropertiesTest.java`（修改，+29 行）

**修改目的**：验证 `AwsClientProperties.credentialsProvider` 在两种配置下的返回类型。

**工作逻辑**：

- `refreshCredentialsEndpoint`：只配置 `client.refresh-credentials-endpoint=http://localhost:1234/v1/credentials`，调用 `credentialsProvider("key", "secret", "token")`，断言返回 `instanceof VendedCredentialsProvider`。
- `refreshCredentialsEndpointSetButRefreshDisabled`：同时配置 `client.refresh-credentials-enabled=false` 与 endpoint，断言返回 `instanceof StaticCredentialsProvider`（而非 `VendedCredentialsProvider`），证明 `enabled` 开关生效。

### `aws/src/test/java/org/apache/iceberg/aws/s3/TestVendedCredentialsProvider.java`（新增，323 行）

**修改目的**：用 MockServer 模拟 REST catalog，全面覆盖 `VendedCredentialsProvider` 行为。

**工作逻辑**：

- 静态启动 `ClientAndServer mockServer = startClientAndServer(3232)`，URI = `http://127.0.0.1:3232/v1/credentials`。
- 测试用例：
  - `invalidOrMissingUri`：`create(null)` 抛 `IllegalArgumentException("Invalid properties: null")`；`create(emptyMap)` 抛 `IllegalArgumentException("Invalid URI: null")`；`create(uri="invalid uri").resolveCredentials()` 抛 `RESTException("Failed to create request URI from base invalid uri")`。
  - `noS3Credentials`：返回空 `LoadCredentialsResponse`，断言抛 `IllegalStateException("Invalid S3 Credentials: empty")`。
  - `accessKeyIdAndSecretAccessKeyWithoutToken`：返回 prefix=`s3` 但 config 缺 `s3.session-token`，断言抛 `IllegalStateException("Invalid S3 Credentials: s3.session-token not set")`。
  - `expirationNotSet`：返回 prefix=`s3` 但 config 缺 `s3.session-token-expires-at-ms`，断言抛 `IllegalStateException("Invalid S3 Credentials: s3.session-token-expires-at-ms not set")`。
  - `nonExpiredToken`：返回完整凭证，过期时间为 1 小时后：
    - 第一次 `resolveCredentials()` 返回 `AwsSessionCredentials`，验证 `accessKeyId/secretAccessKey/sessionToken/expirationTime` 与 mock 数据一致；
    - 连续调用 5 次 `resolveCredentials()`，断言返回**同一对象**（`isSameAs`），证明 `CachedSupplier` 未刷新；
    - `mockServer.verify(mockRequest, VerificationTimes.once())` 验证 HTTP 只调用了 1 次。
  - `expiredToken`：返回完整凭证，过期时间为 1 分钟前（已过期）：
    - 第一次 `resolveCredentials()` 返回凭证 A；
    - 第二次 `resolveCredentials()` 返回凭证 B，`isNotSameAs(A)`，证明已强制刷新；
    - 验证 B 的字段仍与 mock 一致；
    - `mockServer.verify(mockRequest, VerificationTimes.exactly(2))` 验证 HTTP 调用了 2 次。
  - `multipleS3Credentials`：返回三条凭证，prefix 分别为 `gcs`、`s3://custom-uri/longest-prefix`、`s3://custom-uri/long`，断言抛 `IllegalStateException("Invalid S3 Credentials: only one S3 credential should exist")`——验证 `startsWith("s3")` 会捕获所有 s3 前缀的凭证，迫使 REST catalog 必须保证 S3 凭证唯一。
- 辅助 `verifyCredentials(AwsCredentials, Credential)`：断言返回的是 `AwsSessionCredentials`，且四个字段与 mock 数据一致，`expirationTime` 是 `Optional` 且其 epochMilli 与 mock 配置一致。

## 小结

- **成效**：`S3FileIO` 在配置 `client.refresh-credentials-endpoint` 后，通过 `VendedCredentialsProvider` 自动回调 REST catalog 拉取新 S3 凭证，凭证在过期前 5 分钟 prefetch、过期后强制刷新，避免长任务因 session token 过期而中断。与 GCS（1308）、Azure 等模块的 vended credentials 刷新机制对齐。配套 323 行 MockServer 测试覆盖了缓存命中、过期强制刷新、缺字段、多凭证、URI 非法等场景。
- **影响范围**：仅 `iceberg-aws` 模块。生产代码新增 1 个类（`VendedCredentialsProvider`）、修改 2 个类（`AwsClientProperties` 加配置项与优先分支、`S3FileIOProperties` 加常量）；测试新增 1 个类、修改 1 个类。无对 API 模块的修改，无格式变更。向后兼容：未配置 endpoint 时 `credentialsProvider(...)` 行为与之前完全一致。
- **回迁到 1.4.x 的注意事项**：
  1. 依赖 AWS SDK 提供的 `CachedSupplier`、`RefreshResult`、`AwsSessionCredentials.builder().expirationTime(...)` 等 API，需确认 1.4.x 的 AWS SDK BOM 版本（main 当时为 2.29.x）支持这些 API。`CachedSupplier` 与 `RefreshResult` 在 AWS SDK 2.x 较新版本中可用，1.4.x 若 SDK 版本较旧可能需要先升级 SDK；
  2. 依赖 `LoadCredentialsResponse`、`Credential`、`ImmutableCredential`、`ImmutableLoadCredentialsResponse`、`LoadCredentialsResponseParser`、`HTTPClient`、`OAuth2Util`、`OAuth2Properties`、`ErrorHandlers` 等 REST 模型类，1.4.x 上若已有 REST catalog 模块则这些类应当存在；
  3. `libs.mockserver.netty` 与 `libs.mockserver.client.java` 依赖需在 1.4.x 的 `gradle/libs.versions.toml` 中存在；
  4. 配置项命名 `client.refresh-credentials-endpoint` 与 `client.refresh-credentials-enabled` 是 AWS 模块级通用配置（不带 `s3.` 前缀），未来可被其他 AWS 服务（如 DynamoDB）复用，回迁后需在文档中登记；
  5. `SESSION_TOKEN_EXPIRES_AT_MS` 在 `S3FileIOProperties` 中是包级私有，仅供 `VendedCredentialsProvider` 使用，与原 `SESSION_TOKEN = "s3.session-token"` 公共常量形成对照（前者不暴露是因为只在 vended 场景用）；
  6. 该刷新机制仅适用于 vended credentials 场景，不影响使用 InstanceProfile、AssumeRole 等其他 AWS 凭证提供者的部署；
  7. 与 1308（GCS）设计高度对称（同样的 endpoint + enabled 双配置、同样的 `LoadCredentialsResponse` 解析、同样的"唯一凭证"校验），回迁时建议一并回迁保持一致体验。
