# 提交 2637：Azure: Don't fetch credential from endpoint if properties contain a valid credential (#13966)

## 提交信息

- **序号**：2637 / 4088
- **哈希**：088efad2327b617a697d02dd9bfd8cf652269dd4
- **短哈希**：088efad23
- **日期**：2025-09-15 10:15:34 +0200
- **作者**：smaheshwar-pltr
- **提交说明**：Azure: Don't fetch credential from endpoint if properties contain a valid credential (#13966)
- **PR/Issue**：#13966

## 总体目的

`VendedAdlsCredentialProvider` 用于为 Azure ADLSv2 存储账户获取 SAS token。此前，无论 catalog 属性中是否已经包含了有效的 SAS token，该 provider 都会向凭据端点（`/v1/credentials`）发起 HTTP 请求来获取 token。这造成了不必要的网络调用：当属性中已携带有效且未过期的 token 时，完全可以直接复用，避免每次都去请求端点。

本提交优化了凭据获取逻辑：优先从 catalog 属性中读取已配置的 SAS token 及其过期时间，如果该 token 有效且距过期时间超过 5 分钟，则直接使用；否则才回退到从端点获取新 token。这减少了不必要的网络请求，提升了性能和可靠性。

## 如何达成设计目的

1. 在 `VendedAdlsCredentialProvider.sasTokenForAccount(String)` 中，先尝试从属性中提取 token（`sasTokenFromProperties`），若返回 `Optional.empty()` 则再调用 `fetchSasToken` 从端点获取。
2. `sasTokenFromProperties` 从属性中读取 `adls.sas-token.<account>` 和 `adls.sas-token-expires-at-ms.<account>`，若两者都存在且 token 未进入"预取窗口"（过期前 5 分钟），则返回该 token；否则返回 empty 以触发端点获取。
3. 新增三个测试覆盖：属性中有效 token（不命中端点）、属性中过期 token（命中端点一次）、属性中无效 token（缺少过期时间，命中端点一次）。

## 修改详情

### `azure/src/main/java/org/apache/iceberg/azure/adlsv2/VendedAdlsCredentialProvider.java` (+28/-0 lines)

**修改目的**：优先复用属性中有效 token，避免不必要的端点请求。

**工作逻辑**：
- `sasTokenForAccount` 改为：`return sasTokenFromProperties(storageAccount).orElseGet(() -> fetchSasToken(storageAccount));`
- 新增 `sasTokenFromProperties`：读取属性中的 SAS token 和过期时间（毫秒）。若任一为空返回 `Optional.empty()`。计算 `prefetchAt = expiresAt - 5分钟`，若当前时间已过 `prefetchAt`（即距过期不足 5 分钟），返回 empty。否则用 token 和过期时间构造 `AccessToken` 返回。
- 新增 `fetchSasToken`：原 `sasTokenForAccount` 的逻辑，向 `/v1/credentials` 端点请求并从响应中筛选对应账户的凭据。

### `azure/src/test/java/org/apache/iceberg/azure/adlsv2/TestVendedAdlsCredentialProvider.java` (+133/-0 lines)

**修改目的**：验证属性中 token 的复用与回退逻辑。

**工作逻辑**：新增三个测试：
- `nonExpiredSasTokenInProperties`：属性中有 10 小时后过期的有效 token，断言返回该 token 且多次解析不重复请求端点（`VerificationTimes.never()`）。
- `expiredSasTokenInProperties`：属性中的 token 已过期 1 分钟，断言回退到端点获取刷新的 token，端点被命中一次。
- `invalidSasTokenInProperties`：属性中有 token 但缺少过期时间，断言回退到端点，端点被命中一次。

## 总结

本提交优化了 Azure ADLSv2 凭据获取流程，优先复用 catalog 属性中已配置的有效 SAS token，仅在 token 缺失、无效或即将过期时才请求凭据端点。这减少了不必要的网络调用，提升了性能与稳定性。测试覆盖了有效、过期、无效三种场景，逻辑清晰可靠。
