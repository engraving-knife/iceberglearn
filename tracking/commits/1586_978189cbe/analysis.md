# 提交 1586：AWS, Core, GCP: Support relative credential endpoint / pass OAuth2 token to credential provider (#11954)

## 提交信息

- **序号**：1586
- **哈希**：978189cbe33762aa5fe2a76c2028182564d1eec1
- **短哈希**：978189cbe
- **日期**：2025-01-15（Wed Jan 15 11:07:07 2025 +0100）
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：AWS, Core, GCP: Support relative credential endpoint / pass OAuth2 token to credential provider (#11954)
- **PR/Issue**：#11954

## 总体目的

本提交为 Iceberg 的凭据刷新机制带来两项相关增强，主要服务于"vended credentials"（凭据下发）场景，即 REST Catalog 或云存储（AWS S3、GCP GCS）在运行时通过一个 HTTP endpoint 周期性获取/刷新短期访问凭据：

1. **支持相对路径的凭据 endpoint**：此前 `refresh-credentials-endpoint`（AWS）和 `gcs-oauth2-refresh-credentials-endpoint`（GCP）必须填写完整的绝对 URL（`http://host/path`）。这在 catalog URI 已知的场景下显得冗余且易错。本次新增 `RESTUtil.resolveEndpoint(catalogUri, endpointPath)` 工具方法，允许 endpoint 写成相对路径（如 `/v1/credentials`），由 catalog 的 `uri` 拼接出完整 URL；若 endpoint 本身是绝对 URL（`http://`/`https://` 开头）则原样返回，保持向后兼容。

2. **将 OAuth2 token 透传给凭据 provider**：在 REST Catalog 启用了 OAuth2 认证的情况下，client 已持有一个 bearer token。当它再去访问 vended credentials endpoint 拉取短期凭据时，该 endpoint 通常也受同一认证体系保护，需要带上同一个 OAuth2 token。此前 `AwsClientProperties` 在构造 `VendedCredentialsProvider` 时没有把 token 传过去，导致刷新请求可能被拒绝。本次在拼装 `clientCredentialsProviderProperties` 时，把全局的 `OAuth2Properties.TOKEN` 通过 `putIfAbsent` 注入，允许用户用 `client.credentials-provider.token` 显式覆盖。

两项改动配合，使 REST Catalog + 云存储的"集中式凭据下发"部署更简洁、更易与已有认证体系集成。

## 如何达成设计目的

设计上把"相对路径解析"这一通用能力下沉到 `core` 模块的 `RESTUtil`，AWS 和 GCP 两个模块各自调用它解析自己的 endpoint，避免重复实现。OAuth2 token 透传则集中在 AWS 的 `AwsClientProperties`（GCP 未做 token 透传，可能后续补）。同时保留全部原始 properties（`allProperties`）以便在凭据构造阶段取 token。

### 修改详情

#### `core/src/main/java/org/apache/iceberg/rest/RESTUtil.java`

**修改目的**：新增 `resolveEndpoint` 静态工具方法，提供相对/绝对 endpoint 路径解析。

**工作逻辑**：
```java
public static String resolveEndpoint(String catalogUri, String endpointPath) {
  if (null == endpointPath) {
    return null;                          // 未配置 endpoint，返回 null
  }
  if (null == catalogUri                  // 无 catalog URI 可拼接
      || endpointPath.startsWith("http://")
      || endpointPath.startsWith("https://")) {
    return endpointPath;                  // endpoint 已是绝对 URL，原样返回
  }
  // 相对路径：catalogUri 去掉末尾斜杠 + （endpoint 前补 '/'）拼接
  return String.format("%s%s",
      RESTUtil.stripTrailingSlash(catalogUri),
      endpointPath.startsWith("/") ? endpointPath : "/" + endpointPath);
}
```
关键点：
- `endpointPath == null` 直接返回 null（保持"未配置"语义，调用方据此跳过刷新）。
- 绝对 URL（http/https 开头）即使提供了 catalogUri 也原样返回，保证向后兼容（用户写绝对路径仍可用）。
- 相对路径与 catalogUri 拼接前，先用 `stripTrailingSlash` 去掉 catalogUri 末尾的 `/`，并确保 endpoint 以 `/` 开头，避免出现双斜杠或缺失斜杠。例如 `http://host/v1` + `/creds` → `http://host/v1/creds`；`http://host/v1/` + `creds` → `http://host/v1/creds`。

#### `core/src/test/java/org/apache/iceberg/rest/TestRESTUtil.java`

**修改目的**：覆盖 `resolveEndpoint` 的各分支。

新增三个测试：
- `testNullEndpointPath`：endpoint 为 null 时返回 null。
- `testAbsoluteEndpointPath`：endpoint 是绝对 URL 时原样返回（catalogUri 带或不带尾斜杠均不影响）。
- `testRelativeEndpointPath`：覆盖 catalogUri 为 null（直接返回相对路径）、相对路径以 `/` 开头、catalogUri 带尾斜杠、相对路径不以 `/` 开头四种组合，验证拼接结果正确。

#### `aws/src/main/java/org/apache/iceberg/aws/AwsClientProperties.java`

**修改目的**：在 AWS 客户端属性层应用相对 endpoint 解析并透传 OAuth2 token。

**主要变更**：
1. 新增字段 `allProperties`（`Map<String,String>`），在构造函数中通过 `SerializableMap.copyOf(properties)` 保存全量属性副本。`SerializableMap.copyOf` 保证可序列化（AWS properties 需可序列化以便分布式环境传递）。
2. 无参构造函数中 `allProperties = null`。
3. `refreshCredentialsEndpoint` 的赋值改为：
   ```java
   this.refreshCredentialsEndpoint =
       RESTUtil.resolveEndpoint(
           properties.get(CatalogProperties.URI),
           properties.get(REFRESH_CREDENTIALS_ENDPOINT));
   ```
   即用 catalog `uri` 解析相对 endpoint。
4. 在 `credentialsProvider(...)` 方法的 vended 分支（`refreshCredentialsEnabled && endpoint 非空`）中，原本只把 endpoint 放进 `clientCredentialsProviderProperties`（key 为 `VendedCredentialsProvider.URI`）；现在追加：
   ```java
   Optional.ofNullable(allProperties.get(OAuth2Properties.TOKEN))
       .ifPresent(token ->
           clientCredentialsProviderProperties.putIfAbsent(OAuth2Properties.TOKEN, token));
   ```
   - 用 `putIfAbsent` 而非 `put`：若用户已通过 `client.credentials-provider.` 前缀显式配置了 `token`，则保留用户值，不被全局 token 覆盖。这给"针对凭据 provider 用不同 token"留了口子。
   - 只在 token 非空时才放入，避免塞入 null。

#### `aws/src/test/java/org/apache/iceberg/aws/AwsClientPropertiesTest.java`

**修改目的**：覆盖 token 透传与相对 endpoint 解析的行为。

新增三个测试：
1. `refreshCredentialsEndpointWithOAuthToken`：配置绝对 endpoint + 全局 `OAuth2Properties.TOKEN`，断言 `VendedCredentialsProvider` 的 properties 同时包含 `credentials.uri` 和 `token=oauth-token`。
2. `refreshCredentialsEndpointWithOverridingOAuthToken`：同时设置全局 token 和 `client.credentials-provider.token=specific-token`，断言最终 properties 中 token 是 `specific-token`（验证 `putIfAbsent` 的覆盖优先级——前缀配置先于全局 token 写入，故保留前者）。
3. `refreshCredentialsEndpointWithRelativePath`：配置 `CatalogProperties.URI=http://localhost:1234/v1` + 相对 endpoint `/relative/credentials/endpoint` + token，断言 `credentials.uri` 被解析为 `http://localhost:1234/v1/relative/credentials/endpoint`，且 token 正确透传。

#### `gcp/src/main/java/org/apache/iceberg/gcp/GCPProperties.java`

**修改目的**：让 GCP 的 OAuth2 刷新 endpoint 也支持相对路径。

**工作逻辑**：
```java
gcsOauth2RefreshCredentialsEndpoint =
    RESTUtil.resolveEndpoint(
        properties.get(CatalogProperties.URI),
        properties.get(GCS_OAUTH2_REFRESH_CREDENTIALS_ENDPOINT));
```
与 AWS 同样的处理，复用 `RESTUtil.resolveEndpoint`。注意 GCP 侧**未**做 OAuth2 token 透传（与 AWS 不同），仅做了相对路径解析——这可能与 GCP 的认证模型不同有关，或留待后续。

## 小结

- **成效**：
  - 用户现在可以用相对路径配置凭据刷新 endpoint（如 `/v1/credentials`），由 catalog URI 自动拼接，简化配置、减少出错。
  - AWS 的 `VendedCredentialsProvider` 现在会自动携带 REST Catalog 的 OAuth2 token 访问凭据 endpoint，使受 OAuth2 保护的凭据服务能正常工作；并允许通过 `client.credentials-provider.token` 显式覆盖。
  - 把通用解析逻辑下沉到 `core` 的 `RESTUtil`，AWS 与 GCP 共享，避免重复。
- **影响范围**：涉及 `aws`、`core`、`gcp` 三个模块。`core` 新增工具方法（纯新增，向后兼容）；`aws`/`gcp` 的 endpoint 解析行为变化是向后兼容的（绝对路径原样返回，仅相对路径新增拼接能力）；AWS 的 token 透传是新增行为，使用 `putIfAbsent` 不覆盖已有配置，向后兼容。
- **回迁到 1.4.x 的注意事项**：本提交是功能增强且向后兼容，**可回迁**到 1.4.x。回迁价值在于让 1.4.x 用户也能用相对 endpoint 和 OAuth2 token 透传。需注意 1.4.x 分支的 `AwsClientProperties`/`GCPProperties` 结构是否与 main 一致；若 1.4.x 尚无 `REFRESH_CREDENTIALS_ENDPOINT`/`VendedCredentialsProvider` 相关基础设施（这些是更早引入的），则需先确认前置提交已在 1.4.x。`RESTUtil.resolveEndpoint` 是纯新增方法，回迁无副作用。建议作为整体跟随回迁。
