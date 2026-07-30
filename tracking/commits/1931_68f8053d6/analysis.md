# 提交 1931：Azure: Support vended credentials refresh in ADLSFileIO. (#11577)

## 提交信息

- **序号**：1931 / 4088
- **哈希**：68f8053d67aaae7c7cf7b13045e2ed4146fa45f9
- **短哈希**：68f8053d6
- **日期**：2025-03-28 07:32:16 +0100
- **作者**：ChaladiMohanVamsi
- **提交说明**：Azure: Support vended credentials refresh in ADLSFileIO. (#11577)
- **PR/Issue**：#11577

## 总体目的

Iceberg 的 REST Catalog 模型支持向引擎下发"vended credentials"（委托凭证），即 catalog 在加载表时附带一份短期凭证（如 S3/ADLS 的 SAS token），使计算引擎能够无需自身配置即可访问底层存储。但短期凭证存在过期问题：一旦 SAS token 过期，原本 FileIO 就无法继续读写数据。

此前 ADLSFileIO 仅在初始化时接收一次 vended credentials（通过 `adls.sas-token.<account>` 配置），缺乏在运行期间向 catalog 重新拉取新凭证并刷新的能力。对于长时间运行的任务（如大型 Flink/Spark 作业），token 一旦过期，整个作业就会失败。S3 等其他模块早已支持通过 `VendedCredentialsProvider` 在 token 接近过期时自动从 catalog endpoint 拉取新凭证；本提交为 Azure ADLS Gen2 补齐了同等能力。

具体来说，本提交新增 `VendedAdlsCredentialProvider`，它会在 token 即将过期时（由 Azure SDK 的 `SimpleTokenCache` 自动判断）向 catalog 配置的 `adls.refresh-credentials-endpoint` 发起 GET 请求拉取 `LoadCredentialsResponse`，从中提取对应存储账户的 ADLS SAS token 及其过期时间，构造新的 `AccessToken` 并缓存。同时新增 `VendedAzureSasCredentialPolicy` 作为 Azure HTTP pipeline 的一环，在每次请求前确保使用最新 SAS token。这使得长时间运行的 ADLS 作业能在 token 过期时无感刷新，提升作业稳定性。

## 如何达成设计目的

整体设计围绕"凭证拉取 + 缓存 + 自动注入 HTTP 请求"三个环节展开，关键组件协作如下：

1. **配置层（AzureProperties）**：新增 `adls.refresh-credentials-endpoint`（拉取凭证的 REST 路径，会与 catalog URI 拼接为完整 endpoint）与 `adls.refresh-credentials-enabled`（是否启用刷新，默认 true）两个配置项。当启用且 endpoint 非空时，`vendedAdlsCredentialProvider()` 工厂方法会创建一个 `VendedAdlsCredentialProvider` 实例。同时 `applyClientConfiguration` 在启用刷新时跳过原有的静态 SAS token / shared key / default credential 注入逻辑，改由 pipeline policy 动态注入。

2. **凭证提供器（VendedAdlsCredentialProvider）**：核心组件。它持有 catalog endpoint、credentials endpoint 与全部属性。对每个存储账户使用 Azure SDK 的 `SimpleTokenCache` 缓存 token，`SimpleTokenCache` 会在 token 临近过期时自动触发刷新回调 `sasTokenForAccount`，该回调向 catalog 发起 GET `/credentials` 请求，从返回的 `LoadCredentialsResponse.credentials` 中过滤出该账户对应的 ADLS 凭证，提取 `adls.sas-token.<account>` 与 `adls.sas-token-expires-at-ms.<account>` 两个字段构造 `AccessToken`。REST 客户端使用 Iceberg 的 `AuthManager`/`AuthSession` 机制完成 catalog 自身的鉴权。

3. **HTTP Pipeline Policy（VendedAzureSasCredentialPolicy）**：实现 Azure SDK 的 `HttpPipelinePolicy` 接口，包装内部的 `AzureSasCredentialPolicy`。在每次同步/异步请求发出前调用 `maybeUpdateCredential()`，从 provider 取回当前账户最新 SAS token，更新到 `AzureSasCredential`，使每条发往 ADLS 的请求都携带有效 token。

4. **ADLSFileIO 集成**：`newClient` 在构建 `DataLakeFileSystemClientBuilder` 时，若存在 vended provider，则用存储账户 host 名构造 `VendedAzureSasCredentialPolicy` 并通过 `clientBuilder.addPolicy` 加入 pipeline；`initialize` 时从 `AzureProperties` 取得 provider 并保存为字段；`close` 时关闭 provider 以释放 REST 客户端、auth 会话等资源。

## 修改详情

### `azure/src/main/java/org/apache/iceberg/azure/AzureProperties.java` (修改, +55/-8 lines 大致)

**修改目的**：新增凭证刷新相关配置项与 provider 工厂方法，并调整 client 配置逻辑以在启用刷新时让位给 pipeline policy。

**工作逻辑**：
- 新增常量 `ADLS_SAS_TOKEN_EXPIRES_AT_MS_PREFIX`（`adls.sas-token-expires-at-ms.`，用于从 vended credentials 中读取过期时间）、`ADLS_REFRESH_CREDENTIALS_ENDPOINT`（`adls.refresh-credentials-endpoint`）、`ADLS_REFRESH_CREDENTIALS_ENABLED`（`adls.refresh-credentials-enabled`）。
- 新增字段 `adlsRefreshCredentialsEndpoint`、`adlsRefreshCredentialsEnabled`、`allProperties`（保存全部配置的序列化副本，供构造 provider 时使用）。
- `configure` 方法中：通过 `RESTUtil.resolveEndpoint(catalogUri, refreshEndpoint)` 解析完整 endpoint（支持相对路径与 catalog URI 拼接）；读取 `adls-refresh-credentials-enabled`（默认 true）；保存 `allProperties`。
- 新增 `vendedAdlsCredentialProvider()`：当启用且 endpoint 非空时，拷贝 allProperties 并注入 `VendedAdlsCredentialProvider.URI = credentials.uri`（即完整 endpoint），构造 provider 并返回 Optional。
- `applyClientConfiguration` 改为：当未启用刷新或 endpoint 为空时，走原有逻辑（静态 SAS token / shared key / default credential）；否则跳过静态凭证注入（凭证由 pipeline policy 动态提供）。connection string 逻辑仍在最后应用以保持参数优先级。

### `azure/src/main/java/org/apache/iceberg/azure/adlsv2/ADLSFileIO.java` (修改, +17 lines)

**修改目的**：将 vended provider 集成到 FileIO 生命周期。

**工作逻辑**：
- 新增字段 `vendedAdlsCredentialProvider`。
- `newClient` 中：通过 `Optional.ofNullable(vendedAdlsCredentialProvider).map(p -> new VendedAzureSasCredentialPolicy(location.host(), p)).ifPresent(clientBuilder::addPolicy)` 将 policy 注入 pipeline（在 `applyClientConfiguration` 之前）。
- `initialize` 中：调用 `azureProperties.vendedAdlsCredentialProvider().ifPresent(provider -> this.vendedAdlsCredentialProvider = provider)`。
- 重写 `close()`：先关闭 `vendedAdlsCredentialProvider`（释放 REST 客户端/auth 资源），再调用父接口 `close()`。

### `azure/src/main/java/org/apache/iceberg/azure/adlsv2/VendedAdlsCredentialProvider.java` (新增, +167 lines)

**修改目的**：实现凭证拉取、缓存与刷新的核心逻辑。

**工作逻辑**：
- 实现 `Serializable, AutoCloseable`。持有 `properties`、`credentialsEndpoint`、`catalogEndpoint`，以及若干 `transient volatile` 字段（`sasCredentialByAccount`、`client`、`authManager`、`authSession`）以支持序列化后在 executor 端重建。
- `credentialForAccount(storageAccount)`：对每个存储账户用 `sasCredentialByAccount().computeIfAbsent` 创建一个 `SimpleTokenCache`，`SimpleTokenCache` 会在 token 临近过期时自动调用回调 `() -> Mono.fromSupplier(() -> sasTokenForAccount(storageAccount))` 拉取新 token；最终 `getToken().map(AccessToken::getToken).block()` 返回当前有效的 SAS token 字符串。
- `sasTokenForAccount(storageAccount)`：调用 `fetchCredentials()` 获取 `LoadCredentialsResponse`，从 `credentials` 列表中过滤 `prefix().contains(storageAccount)` 的项，校验"每个存储账户只有一个 ADLS 凭证"，校验 `adls.sas-token.<account>` 与 `adls.sas-token-expires-at-ms.<account>` 两个字段存在，然后构造 `AccessToken(sasToken, expiresAt)`。
- `httpClient()`：双重检查锁懒初始化 REST 客户端。通过 `AuthManagers.loadAuthManager("adls-credentials-refresh", properties)` 加载 catalog 鉴权管理器，构建 `HTTPClient`（URI = catalogEndpoint），创建 catalog session，并用 `withAuthSession` 包装使请求自动携带 catalog 鉴权。
- `fetchCredentials()`：向 `credentialsEndpoint` 发起 GET 请求，返回 `LoadCredentialsResponse`。
- `close()`：用 `CloseableGroup` 关闭 `authSession`、`authManager`、`client`，并 `setSuppressCloseFailure(true)` 抑制关闭失败异常。

### `azure/src/main/java/org/apache/iceberg/azure/adlsv2/VendedAzureSasCredentialPolicy.java` (新增, +66 lines)

**修改目的**：将动态刷新的 SAS token 注入每条 ADLS HTTP 请求。

**工作逻辑**：
- 实现 `HttpPipelinePolicy`。持有 `account`、`vendedAdlsCredentialProvider`、`azureSasCredential`、`azureSasCredentialPolicy`（内部包装 Azure SDK 原生的 `AzureSasCredentialPolicy`）。
- `process`（异步）/`processSync`（同步）：在请求转发前先调用 `maybeUpdateCredential()`，再委托内部 `AzureSasCredentialPolicy` 处理（它会把 SAS token 作为查询参数附加到请求 URL）。
- `maybeUpdateCredential()`：从 provider 取回当前账户的 SAS token；若 `azureSasCredential` 尚未初始化则创建 `AzureSasCredential` 与对应的 `AzureSasCredentialPolicy(credential, false)`；否则调用 `azureSasCredential.update(sasToken)` 更新 token。每次请求都重新取一次，由 `SimpleTokenCache` 内部保证只在需要时真正发起刷新请求。

### `azure/src/test/java/org/apache/iceberg/azure/AzurePropertiesTest.java` (修改, +46 lines)

**修改目的**：为新增的配置项与 provider 工厂方法补充单元测试。

**工作逻辑**：验证 `vendedAdlsCredentialProvider()` 在不同配置组合下（启用/禁用、endpoint 为空/非空、endpoint 相对/绝对路径）的行为，以及 `applyClientConfiguration` 在启用刷新时不再注入静态凭证。

### `azure/src/test/java/org/apache/iceberg/azure/adlsv2/BaseVendedCredentialsTest.java` (新增, +49 lines)

**修改目的**：提供基于 MockServer 的测试基类。

**工作逻辑**：使用 `mockserver` 在 `127.0.0.1` 的动态端口启动 mock HTTP 服务器，作为 catalog endpoint 与 credentials endpoint 的替身；`@BeforeAll` 启动、`@AfterAll` 停止、`@BeforeEach` reset。

### `azure/src/test/java/org/apache/iceberg/azure/adlsv2/VendedAdlsCredentialProviderTest.java` (新增, +309 lines)

**修改目的**：对凭证提供器进行端到端测试。

**工作逻辑**：通过 MockServer 模拟 catalog 的 `/v1/credentials` 响应，验证 `VendedAdlsCredentialProvider` 能正确解析 ADLS 凭证、提取 SAS token 与过期时间、缓存并在过期后刷新；覆盖正常刷新、缺少字段、多个凭证匹配、账户未找到等异常场景。

### `azure/src/test/java/org/apache/iceberg/azure/adlsv2/VendedAzureSasCredentialPolicyTest.java` (新增, +96 lines)

**修改目的**：测试 pipeline policy 行为。

**工作逻辑**：验证 `VendedAzureSasCredentialPolicy` 在首次请求时创建 `AzureSasCredential` 与内部 policy，在后续请求时更新 token，并确保请求 URL 被附加了正确的 SAS token 查询参数。

### `build.gradle` (修改, +2 lines)

**修改目的**：为 iceberg-azure 模块添加 mockserver 测试依赖。

**工作逻辑**：在 `:iceberg-azure` 的 dependencies 中新增 `testImplementation libs.mockserver.netty` 与 `testImplementation libs.mockserver.client.java`，供 vended credentials 测试用作 mock catalog 服务器。

## 总结

本提交为 Azure ADLS Gen2 的 FileIO 引入了 vended credentials 自动刷新能力，与 S3 等模块对齐。核心新增 `VendedAdlsCredentialProvider`（基于 Azure SDK `SimpleTokenCache` 在 token 临近过期时自动向 catalog REST endpoint 拉取新 SAS token）与 `VendedAzureSasCredentialPolicy`（作为 HTTP pipeline policy 在每条请求前注入最新 token），并通过 `AzureProperties` 的新配置项（`adls.refresh-credentials-endpoint`、`adls.refresh-credentials-enabled`）控制开关。这使得长时间运行的 Iceberg on Azure 作业能在 SAS token 过期后无感续期，显著提升稳定性。配套提供了基于 MockServer 的端到端测试。
