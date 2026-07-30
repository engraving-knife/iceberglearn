# 提交 2526：Azure: Support access token authentication via the new `adls.token` property (#13825)

## 提交信息

- **序号**：2526 / 4088
- **哈希**：ede5b5a2faa074bc8054f0a29efbe53dde3f3f3e
- **短哈希**：ede5b5a2f
- **日期**：2025-08-19 08:35:41 +0200
- **作者**：Kevin Liu
- **提交说明**：Azure: Support access token authentication via the new `adls.token` property (#13825)
- **PR/Issue**：#13825

## 总体目的

此提交为 Iceberg 的 Azure ADLS（Azure Data Lake Storage）集成添加了直接使用访问令牌（access token）进行认证的能力，通过新的 `adls.token` 配置属性实现。

此前，Iceberg 的 Azure 集成支持以下认证方式：
1. **SAS 令牌**（`adls.sas-token.*`）：基于共享访问签名的认证
2. **共享密钥**（`adls.auth.shared-key.account.name/key`）：基于账户名和密钥的认证
3. **默认 Azure 凭证**（DefaultAzureCredential）：Azure SDK 的默认凭证链，会自动尝试多种认证方式（环境变量、托管身份、Azure CLI 等）

然而，在某些场景下用户已经持有一个有效的访问令牌（例如从其他服务获取的 OAuth 令牌），希望直接使用该令牌进行认证，而不需要依赖 SAS 令牌、共享密钥或默认凭证链。这种场景在跨服务认证、短期令牌使用和集成测试中尤为常见。

## 如何达成设计目的

设计方案在 `AzureProperties` 中添加 `adls.token` 配置属性，当设置该属性时，创建一个自定义的 `TokenCredential` 实现来包装该令牌，并传递给 Azure Storage 客户端构建器。

具体设计要点：
1. 定义 `ADLS_TOKEN = "adls.token"` 配置键常量
2. 在构造函数中从属性映射中读取 `token` 值
3. 在 `applyClientConfiguration()` 方法的认证链中，在共享密钥认证之后、默认凭证之前，添加令牌认证分支
4. 创建匿名 `TokenCredential` 实现，其 `getToken()` 方法返回一个包含用户提供令牌的 `AccessToken`
5. 假设令牌有效期为 1 小时（从当前 UTC 时间起算），这是一个简化的假设

## 修改详情

### `azure/src/main/java/org/apache/iceberg/azure/AzureProperties.java` (+21/-0 lines)

**修改目的**：添加 `adls.token` 属性支持和 TokenCredential 认证逻辑。

**工作逻辑**：
1. **新增导入**：`AccessToken`、`TokenCredential`、`TokenRequestContext`、`OffsetDateTime`、`ZoneOffset`、`Mono`
2. **新增常量**：`ADLS_TOKEN = "adls.token"`
3. **新增字段**：`private String token`
4. **构造函数修改**：从属性映射中读取 `ADLS_TOKEN` 值到 `token` 字段
5. **认证链修改**：在 `applyClientConfiguration()` 方法中，当 SAS 令牌和共享密钥都未设置时，检查 `token` 是否非空。如果非空，创建匿名 `TokenCredential` 实现：
   - `getToken()` 方法返回 `Mono.just(new AccessToken(token, OffsetDateTime.now(ZoneOffset.UTC).plusHours(1)))`
   - 即返回一个包含用户提供的令牌、过期时间设为当前时间后 1 小时的 `AccessToken`
   - 将此 `TokenCredential` 传递给 `builder.credential()`

### `azure/src/test/java/org/apache/iceberg/azure/TestAzureProperties.java` (+29/-0 lines)

**修改目的**：添加 `testAdlsToken` 测试用例验证令牌认证功能。

**工作逻辑**：
1. 创建 `AzureProperties` 实例，配置 `adls.token` 属性为测试令牌值
2. 调用 `applyClientConfiguration()` 并使用 `ArgumentCaptor` 捕获传递给客户端构建器的 `TokenCredential`
3. 调用捕获的 `TokenCredential.getToken()` 验证返回的令牌与输入一致
4. 验证其他认证方式（SAS、共享密钥、默认凭证）未被使用

## 总结

此提交为 Azure ADLS 集成添加了直接使用访问令牌进行认证的能力，填补了认证方式的空白。这在用户已持有有效令牌的场景下提供了更简单直接的认证路径，避免了依赖默认凭证链或配置 SAS/共享密钥的复杂性。需要注意的是，令牌有效期固定假设为 1 小时，在令牌即将过期或需要刷新的场景下可能需要额外的处理逻辑。
