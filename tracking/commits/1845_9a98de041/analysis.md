# 提交 1845：AWS: Don't fetch credential from endpoint if properties contain a valid credential (#12504)

## 提交信息

- **序号**：1845 / 4088
- **哈希**：9a98de04103755ad4833ac6c3918c39022d54a99
- **短哈希**：9a98de041
- **日期**：2025-03-13 06:23:56 +0100
- **作者**：Eduard Tudenhoefner
- **提交说明**：AWS: Don't fetch credential from endpoint if properties contain a valid credential (#12504)
- **PR/Issue**：#12504

## 总体目的

本提交优化了 `VendedCredentialsProvider` 的凭证获取逻辑，使其优先使用 properties 中已有的有效凭证，仅在凭证不完整或已过期时才回退到从 refresh endpoint 获取新凭证。

`VendedCredentialsProvider` 是 Iceberg AWS 模块中用于从 REST Catalog 的 vended credentials（通过表属性下发的临时 S3 凭证）获取 AWS 凭证的提供者。在此之前，`credentialCache` 的 `CachedSupplier` 直接使用 `this::refreshCredential` 作为加载函数，这意味着每次缓存失效时都会调用 `refreshCredential()` 向 REST Catalog 的 `/v1/credentials` 端点发起新的 HTTP 请求。然而，当表首次加载时，properties 中已经包含了完整的凭证信息（access key、secret key、session token、过期时间），这些凭证在有效期内不需要重新获取。

本提交的改进避免了在凭证仍然有效时不必要的 HTTP 请求，减少了网络开销和 REST Catalog 的负载，同时也降低了因网络问题导致凭证获取失败的风险。

## 如何达成设计目的

整体思路是将 `CachedSupplier` 的加载函数从直接调用 `refreshCredential()` 改为先尝试 `credentialFromProperties()`，如果返回空则回退到 `refreshCredential()`。`credentialFromProperties()` 从 properties 中读取凭证的四个字段（access key id、secret access key、session token、token expires at millis），如果任一字段为空则返回 `Optional.empty()`；然后检查凭证是否即将过期（过期前 5 分钟视为即将过期），如果已过 prefetch 时间也返回空；否则构造 `RefreshResult<AwsCredentials>` 返回。这样 `CachedSupplier` 在缓存失效时会优先使用 properties 中的有效凭证，只有当凭证不完整或即将过期时才真正调用 refresh endpoint。

## 修改详情

### `aws/src/main/java/org/apache/iceberg/aws/s3/VendedCredentialsProvider.java` (修改)

**修改目的**：优先使用 properties 中的凭证，减少不必要的 endpoint 调用。

**工作逻辑**：

1. `credentialCache` 的 `CachedSupplier` 构建从 `CachedSupplier.builder(this::refreshCredential)` 改为 `CachedSupplier.builder(() -> credentialFromProperties().orElseGet(this::refreshCredential))`。这样缓存加载时先尝试从 properties 获取凭证，失败则回退到 refresh endpoint。

2. 新增 `credentialFromProperties()` 私有方法：
   - 从 `properties` 中读取 `S3FileIOProperties.ACCESS_KEY_ID`、`SECRET_ACCESS_KEY`、`SESSION_TOKEN`、`SESSION_TOKEN_EXPIRES_AT_MS` 四个字段。
   - 如果任一字段为 null 或空（使用 `Strings.isNullOrEmpty`），返回 `Optional.empty()`。
   - 解析过期时间 `expiresAt = Instant.ofEpochMilli(Long.parseLong(tokenExpiresAtMillis))`，计算 prefetch 时间 `prefetchAt = expiresAt.minus(5, ChronoUnit.MINUTES)`。
   - 如果当前时间已过 prefetchAt（凭证即将过期），返回 `Optional.empty()`。
   - 否则构造 `AwsSessionCredentials`（含 accessKeyId/secretAccessKey/sessionToken/expirationTime），包装为 `RefreshResult`（含 staleTime=expiresAt, prefetchTime=prefetchAt）返回。

3. 新增 `import java.util.Optional` 和 `import org.apache.iceberg.relocated.com.google.common.base.Strings`。

### `aws/src/test/java/org/apache/iceberg/aws/s3/TestVendedCredentialsProvider.java` (修改, 170 lines)

**修改目的**：验证三种场景下的凭证获取行为。

**工作逻辑**：新增三个测试用例：

1. `nonExpiredTokenInProperties`：properties 中包含未过期凭证（10 小时后过期），验证 `resolveCredentials()` 返回 properties 中的凭证（而非 endpoint 返回的凭证），且多次调用不重复请求 endpoint。通过 `mockServer.verify(mockRequest, VerificationTimes.never())` 验证 endpoint 从未被调用。

2. `expiredTokenInProperties`：properties 中包含已过期凭证（1 小时前过期），验证 `resolveCredentials()` 返回 endpoint 返回的凭证，且 endpoint 被调用一次。通过 `mockServer.verify(mockRequest, VerificationTimes.once())` 验证。

3. `invalidTokenInProperties`：properties 中缺少过期时间字段（其他字段存在），验证 `resolveCredentials()` 回退到 endpoint 获取凭证，endpoint 被调用一次。

## 小结

本提交优化了 `VendedCredentialsProvider` 的凭证获取策略，优先使用 properties 中已有的有效凭证，减少了不必要的 HTTP 请求。改动涉及 2 个文件（主代码和测试），逻辑清晰且向后兼容。回迁到 1.4.x 时需注意：`S3FileIOProperties.SESSION_TOKEN_EXPIRES_AT_MS` 属性需存在；该优化不影响凭证获取的正确性，只是减少了网络请求。5 分钟的 prefetch 时间窗口是合理的默认值，与 AWS SDK 的凭证预取策略一致。
