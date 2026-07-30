# 提交 1944：AWS: Fix Catalog URI within VendedCredentialsProvider (#12612)

## 提交信息

- **序号**：1944 / 4088
- **哈希**：e55f23858848bb848a1a16ae1ee045139ffe89fc
- **短哈希**：e55f23858
- **日期**：2025-04-01 11:55:39 +0200
- **作者**：Juichang Lu
- **提交说明**：AWS: Fix Catalog URI within VendedCredentialsProvider (#12612)
- **PR/Issue**：#12612

## 总体目的

此提交修复 AWS S3 的 `VendedCredentialsProvider` 在刷新 vended credentials（委托凭证）时的端点配置缺陷，与本次提交批次中 GCP（1934）和 Azure（1931）的同类修复属于同一系列。问题根因相同：`VendedCredentialsProvider` 在构建用于刷新凭证的 REST 客户端 `HTTPClient` 时，将 credentials endpoint（凭证刷新路径，如 `/v1/credentials`）同时作为 HTTP 客户端的 base URI。

这会导致两个问题：
1. base URI 与鉴权会话（`AuthSession`）的绑定逻辑要求 base URI 是 catalog 的根 endpoint（`CatalogProperties.URI`）。catalog 的 OAuth2 鉴权流程（如 token 刷新端点 `/v1/oauth/tokens`）需要相对 catalog 根解析，若用 credentials endpoint 作为 base，相对路径解析会出错。
2. 当 credentials endpoint 是相对路径时无法作为绝对 base URI，会抛出 `Failed to create request URI from base ...` 异常。

此外，`AwsClientProperties.credentialsProvider` 此前只向 provider 的属性 map 中选择性注入 `OAuth2Properties.TOKEN`（且用 `putIfAbsent`），这丢失了 catalog 鉴权所需的其他属性（如 `CatalogProperties.URI` 本身）。本提交改为将完整的 `allProperties` 全部注入，使 provider 能拿到 `CatalogProperties.URI` 等全部配置。

修复后，HTTP 客户端 base URI 使用 `CatalogProperties.URI`（catalog 根），credentials endpoint 仅作为 GET 请求路径，与 GCP/Azure 实现保持一致，并支持 credentials endpoint 为相对路径的场景。

## 如何达成设计目的

设计思路与 GCP 修复（1934）一致，分两处改动：
1. **`VendedCredentialsProvider`**：新增 `catalogEndpoint` 与 `credentialsEndpoint` 字段，构造时分别从 `CatalogProperties.URI` 与 `URI`（credentials.uri）读取并校验非空。`httpClient()` 中 `HTTPClient.builder(...).uri(...)` 的参数从 credentials endpoint 改为 `catalogEndpoint`；`fetchCredentials()` 的 GET 路径改用缓存的 `credentialsEndpoint` 字段。
2. **`AwsClientProperties`**：`credentialsProvider` 方法在启用刷新时，将 `allProperties` 全部 `putAll` 到 `clientCredentialsProviderProperties`（而非仅选择性注入 OAuth2 token），再覆盖 `VendedCredentialsProvider.URI`。这样 provider 能拿到 `CatalogProperties.URI` 等所有 catalog 配置用于构建 HTTP 客户端与鉴权。

测试侧同步在构造 provider 时传入 `CatalogProperties.URI`，并新增对 catalog endpoint 缺失的校验测试。

## 修改详情

### `aws/src/main/java/org/apache/iceberg/aws/s3/VendedCredentialsProvider.java` (修改, +13/-2 lines)

**修改目的**：将 HTTP 客户端 base URI 从 credentials endpoint 改为 catalog endpoint。

**工作逻辑**：
- 新增 import `org.apache.iceberg.CatalogProperties`。
- 新增字段 `catalogEndpoint`、`credentialsEndpoint`。
- 构造器中：将原校验 `Invalid URI: null` 改为 `Invalid credentials endpoint: null`；新增校验 `CatalogProperties.URI` 非空（`Invalid catalog endpoint: null`）；将两者分别赋值给字段。
- `httpClient()` 中：`HTTPClient.builder(properties).uri(properties.get(URI))` 改为 `.uri(catalogEndpoint)`，使 HTTP 客户端以 catalog 根为 base，鉴权会话相对路径解析正确。
- `fetchCredentials()` 中：GET 路径从 `properties.get(URI)` 改为 `credentialsEndpoint`。

### `aws/src/main/java/org/apache/iceberg/aws/AwsClientProperties.java` (修改, +7/-4 lines)

**修改目的**：将全部 catalog 属性注入 provider，而非仅注入 OAuth2 token。

**工作逻辑**：
- `credentialsProvider(accessKeyId, secretAccessKey, sessionToken)` 方法中：当启用刷新时，原逻辑仅 `put(URI, refreshCredentialsEndpoint)` 并选择性 `putIfAbsent(OAuth2Properties.TOKEN, token)`。改为先 `clientCredentialsProviderProperties.putAll(allProperties)`（注入全部属性，含 `CatalogProperties.URI`），再 `put(VendedCredentialsProvider.URI, refreshCredentialsEndpoint)`。
- 移除不再需要的 `Optional` 与 `OAuth2Properties` 导入。

### `aws/src/test/java/org/apache/iceberg/aws/AwsClientPropertiesTest.java` (修改, +47/-20 lines)

**修改目的**：适配新逻辑（注入全部属性）并补充 `CatalogProperties.URI` 配置。

**工作逻辑**：
- 各测试用例在构造 `AwsClientProperties` 时新增 `CatalogProperties.URI` 配置项。
- `refreshCredentialsEndpointWithOverridingOAuthToken`：期望的 provider properties 改为包含全部原始属性（`putAll(properties)`）加上 `credentials.uri`，而非仅 `credentials.uri` 与 token。验证 `client.credentials-provider.token` 仍能覆盖 `OAuth2Properties.TOKEN`（因为 `putAll(allProperties)` 先放入 token，再由更具体的 `client.credentials-provider.token` 在 `allProperties` 中已存在时按原 map 顺序——实际上测试验证了覆盖语义）。
- 相对路径测试用例期望 properties 中同时包含 `CatalogProperties.URI`、`credentials.uri`（解析后的绝对路径）、`REFRESH_CREDENTIALS_ENDPOINT`（原始相对路径）与 token。

### `aws/src/test/java/org/apache/iceberg/aws/s3/TestVendedCredentialsProvider.java` (修改, +54/-20 lines)

**修改目的**：适配新校验并补充 `CatalogProperties.URI`。

**工作逻辑**：
- 重命名常量 `URI` 为 `CREDENTIALS_URI`，新增 `CATALOG_URI`，构造共享 `PROPERTIES` map 同时含 `credentials.uri` 与 `CatalogProperties.URI`。
- `invalidOrMissingUri` 测试：新增缺少 `CatalogProperties.URI` 时抛 `Invalid catalog endpoint: null` 的用例；原"invalid uri"用例改为同时传 catalog URI，异常消息改为 `Failed to create request URI from base invalid catalog uri`（base 现在是 catalog URI）。
- 其余多个测试用例统一用 `PROPERTIES` 替代单独构造的 map。
- 三个使用 `S3FileIOProperties.ACCESS_KEY_ID` 等额外属性的测试用例新增 `CatalogProperties.URI` 配置。

## 总结

本次提交修复 AWS S3 `VendedCredentialsProvider` 的端点配置缺陷：将 HTTP 客户端 base URI 从 credentials endpoint 改为 catalog 根 endpoint（`CatalogProperties.URI`），使 catalog 鉴权会话能正确解析相对路径，并支持 credentials endpoint 为相对路径的场景。同时在 `AwsClientProperties` 中将全部 catalog 属性注入 provider（而非仅 OAuth2 token），确保 provider 拿到完整的 catalog 配置。该修复与 GCP（1934）、Azure（1931）的同类改动保持一致。测试同步适配并覆盖新校验场景。
