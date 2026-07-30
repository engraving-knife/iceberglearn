# 提交 1934：GCP: Use catalog endpoint as base when refreshing OAuth2 token (#12638)

## 提交信息

- **序号**：1934 / 4088
- **哈希**：054eacd5d691012b725d1c16d96f1c728748a3cf
- **短哈希**：054eacd5d
- **日期**：2025-03-28 08:43:56 -0700
- **作者**：Eduard Tudenhoefner
- **提交说明**：GCP: Use catalog endpoint as base when refreshing OAuth2 token (#12638)
- **PR/Issue**：#12638

## 总体目的

此提交修复 GCP GCSFileIO 在刷新 OAuth2 token（vended credentials refresh）时的一个端点配置缺陷。此前 `OAuth2RefreshCredentialsHandler` 在构建用于刷新凭证的 REST 客户端 `HTTPClient` 时，将 `GCS_OAUTH2_REFRESH_CREDENTIALS_ENDPOINT`（凭证刷新端点）同时作为 HTTP 客户端的 base URI 与 GET 请求的路径。这有两个问题：

1. base URI 与鉴权会话（`AuthSession`）的绑定逻辑要求 base URI 是 catalog 的根 endpoint（即 `CatalogProperties.URI`），而非具体的 `/v1/credentials` 子路径。如果用 credentials endpoint 作为 base，catalog 的 OAuth2 鉴权流程（如 token 刷新端点 `/v1/oauth/tokens`）可能无法正确解析相对路径。
2. 当 credentials endpoint 是相对路径（如 `/v1/credentials`）时，无法作为 HTTP 客户端的绝对 base URI，会导致 `Failed to create request URI from base ...` 异常。

本提交将 base URI 与 credentials endpoint 解耦：用 `CatalogProperties.URI`（catalog 根 endpoint）作为 HTTP 客户端的 base URI 与鉴权会话的绑定 URI，而 `GCS_OAUTH2_REFRESH_CREDENTIALS_ENDPOINT` 仅作为 GET 请求的路径。这与本批提交中 ADLS（1931）、AWS（1944）等其他模块的实现保持一致，并使用 `RESTUtil.resolveEndpoint` 风格的解析思路。

## 如何达成设计目的

设计思路是在 `OAuth2RefreshCredentialsHandler` 中新增 `catalogEndpoint` 字段，从 `CatalogProperties.URI` 读取 catalog 根 endpoint，并在两个关键位置使用它：
- `httpClient()` 中构建 `HTTPClient` 时使用 `catalogEndpoint` 作为 base URI（而非 credentials endpoint），使 catalog 鉴权会话能正确解析相对路径。
- 构造时校验 `CatalogProperties.URI` 必须存在，否则抛出 `Invalid catalog endpoint: null`。

`credentialsEndpoint` 字段单独保存凭证刷新路径，仅用于 `refreshAccessToken` 中的 GET 请求路径。测试侧同步在构造 handler 时传入 `CatalogProperties.URI`，并新增对缺少 catalog endpoint 的校验测试。

## 修改详情

### `gcp/src/main/java/org/apache/iceberg/gcp/gcs/OAuth2RefreshCredentialsHandler.java` (修改, +16 lines)

**修改目的**：将 HTTP 客户端 base URI 从 credentials endpoint 改为 catalog endpoint，并新增校验。

**工作逻辑**：
- 新增 import `org.apache.iceberg.CatalogProperties`。
- 新增两个字段：`credentialsEndpoint`（凭证刷新路径）与 `catalogEndpoint`（catalog 根 URI，"will be used to refresh the OAuth2 token"）。
- 构造器中：在原有校验 `GCS_OAUTH2_REFRESH_CREDENTIALS_ENDPOINT` 非空之外，新增校验 `CatalogProperties.URI` 非空（`Invalid catalog endpoint: null`）；将两者分别赋值给对应字段。
- `refreshAccessToken` 中：GET 请求的路径由 `properties.get(GCPProperties.GCS_OAUTH2_REFRESH_CREDENTIALS_ENDPOINT)` 改为使用缓存的 `credentialsEndpoint` 字段（避免每次读取 map）。
- `httpClient()` 中：`HTTPClient.builder(properties).uri(...)` 的参数从 `properties.get(GCPProperties.GCS_OAUTH2_REFRESH_CREDENTIALS_ENDPOINT)` 改为 `catalogEndpoint`，使 HTTP 客户端以 catalog 根 endpoint 为 base，鉴权会话（`authManager.catalogSession`）的相对路径解析才能正确工作。

### `gcp/src/test/java/org/apache/iceberg/gcp/gcs/GCSFileIOTest.java` (修改, +3 lines)

**修改目的**：适配新校验，在使用 OAuth2 刷新凭证的测试中补充 `CatalogProperties.URI` 配置。

**工作逻辑**：在某测试用例初始化 `GCSFileIO` 时，向 `ImmutableMap` 中新增 `CatalogProperties.URI = "http://catalog-endpoint"`，使 handler 构造时能通过 `CatalogProperties.URI` 非空校验。

### `gcp/src/test/java/org/apache/iceberg/gcp/gcs/OAuth2RefreshCredentialsHandlerTest.java` (修改, +57/-27 lines)

**修改目的**：适配新校验并新增对 catalog endpoint 缺失的校验测试。

**工作逻辑**：
- 重命名常量 `URI` 为 `CREDENTIALS_URI`（`http://127.0.0.1:3333/v1/credentials`），新增 `CATALOG_URI`（`http://127.0.0.1:3333/v1/`），并构造一个共享的 `PROPERTIES` map 同时包含 credentials endpoint 与 catalog URI。
- `invalidOrMissingUri` 测试拆分为多个断言：
  - 仅传 catalog URI（缺 credentials endpoint）→ `Invalid credentials endpoint: null`。
  - 仅传 credentials endpoint（缺 catalog URI）→ `Invalid catalog endpoint: null`（新增用例）。
  - 两者都传但 credentials endpoint 为 `invalid uri` → 验证异常消息以 `Failed to create request URI from base <catalog-uri>invalid uri` 开头（因为 base 现在是 catalog URI，与路径拼接后才是完整请求 URI）。
- 其余多个测试用例（`invalidOrMissingUri`、`refreshCredentialsBadRequest`、`refreshCredentialsEmpty`、`refreshCredentialsWithoutGcsCredentials`、`refreshCredentialsWithoutExpiresAt`、`refreshCredentials`、`refreshCredentialsWithMultipleCredentials`）统一用 `PROPERTIES` 替代原先单独构造的 `ImmutableMap.of(GCS_OAUTH2_REFRESH_CREDENTIALS_ENDPOINT, URI)`，简化测试代码。

## 总结

本次提交修复 GCP OAuth2 凭证刷新机制的端点配置缺陷：将 HTTP 客户端的 base URI 从 credentials endpoint 改为 catalog 根 endpoint（`CatalogProperties.URI`），使 catalog 鉴权会话能正确解析相对路径，并支持 credentials endpoint 为相对路径的场景。新增对 `CatalogProperties.URI` 缺失的校验，测试同步适配并覆盖新校验场景。该改动使 GCP 实现与其他模块（ADLS/AWS）保持一致。
