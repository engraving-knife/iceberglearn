# 提交 0897：Core: Assume issued_token_type is access_token to fully comply with RFC 6749 (#10314)

## 提交信息

- **序号**：0897 / 4088
- **哈希**：4048987d2f667449bcb62a442177e4c0b36a3c36
- **短哈希**：4048987d2
- **日期**：2024-07-04（Thu Jul 4 14:06:57 2024 +0200）
- **作者**：Alexandre Dutra <adutra@users.noreply.github.com>
- **提交说明**：Core: Assume issued_token_type is access_token to fully comply with RFC 6749 (#10314)
- **PR/Issue**：#10314

## 总体目的

Iceberg REST Catalog 在通过 OAuth2 获取访问 token 时，会解析 token 响应中的 `issued_token_type` 字段，并用它来标识所获 token 的类型（如 `urn:ietf:params:oauth:token-type:access_token`、`id_token` 等）。这一字段在 RFC 8693（Token Exchange）中是**必填**的，因此 Iceberg REST 客户端原本直接使用 `response.issuedTokenType()` 的返回值，未做 null 处理。

然而实际世界中并非所有认证服务器都完整实现了 RFC 8693。对于只实现 RFC 6749（OAuth 2.0 Authorization Framework）的认证服务器——例如只支持 client credentials grant（RFC 6749 §4.4）的服务器——其 token 响应中**不包含** `issued_token_type` 字段（参见 RFC 6749 §4.4.3 的响应示例）。当 Iceberg REST 客户端向这类服务器请求 token 时，`response.issuedTokenType()` 会返回 null，被原样设置到 `AuthConfig.tokenType(null)`，可能导致后续的 token 类型判断、token refresh、token exchange 等逻辑出现非预期行为。

本提交的目的是让 Iceberg REST 客户端在 token 响应缺少 `issued_token_type` 时，默认假定其为 `access_token` 类型（`urn:ietf:params:oauth:token-type:access_token`），从而兼容只实现 RFC 6749 的认证服务器，同时不破坏与 RFC 8693 服务器的交互。

## 如何达成设计目的

实现方式非常局部：在 `OAuth2Util.fromTokenResponse(...)`（带 `credential` 参数的私有重载）中，从 `response` 取出 `issuedTokenType` 后做一次 null 检查，若为 null 则赋值为 `OAuth2Properties.ACCESS_TOKEN_TYPE`，再传给 `AuthConfig.tokenType(...)`。这样无论服务器是否返回 `issued_token_type`，最终 `AuthSession` 的 `tokenType` 都不会是 null。

同时在测试辅助类 `RESTCatalogAdapter` 中，将 `client_credentials` grant 的模拟响应去掉 `withIssuedTokenType(...)`，使测试用例能覆盖"服务器未返回 issued_token_type"的新场景；而 `token-exchange` grant 的模拟响应仍保留 `withIssuedTokenType(...)`，因为 RFC 8693 要求 token exchange 响应必须包含该字段。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/auth/OAuth2Util.java`

**修改目的**：在 `fromTokenResponse` 中对 `issuedTokenType` 为 null 的情况做兜底处理，默认为 `access_token` 类型。

**工作逻辑**：在 `fromTokenResponse(RESTClient, ScheduledExecutorService, OAuthTokenResponse, long, AuthSession, String)` 方法中，构建 `AuthSession` 之前，先取出 `response.issuedTokenType()`，若为 null 则赋值为 `OAuth2Properties.ACCESS_TOKEN_TYPE`（即 `urn:ietf:params:oauth:token-type:access_token`）。新增的代码如下：

```java
// issued_token_type is required in RFC 8693 but not in RFC 6749,
// thus assume type is access_token for compatibility with RFC 6749.
// See https://datatracker.ietf.org/doc/html/rfc6749#section-4.4.3
// for an example of a response that does not include the issued token type.
String issuedTokenType = response.issuedTokenType();
if (issuedTokenType == null) {
  issuedTokenType = OAuth2Properties.ACCESS_TOKEN_TYPE;
}
AuthSession session =
    new AuthSession(
        parent.headers(),
        AuthConfig.builder()
            .from(parent.config())
            .token(response.token())
            .tokenType(issuedTokenType)   // 原来是 response.issuedTokenType()
            .credential(credential)
            .build());
```

注释中明确说明了 RFC 8693 与 RFC 6749 在 `issued_token_type` 字段上的差异，并引用了 RFC 6749 §4.4.3 的示例链接。这样修改后，`tokenType` 永远不会是 null，后续依赖 tokenType 的逻辑（如 token refresh、token exchange）不再受 null 影响。

### `core/src/test/java/org/apache/iceberg/rest/RESTCatalogAdapter.java`

**修改目的**：让测试用的 mock OAuth 服务器在 `client_credentials` grant 响应中不返回 `issued_token_type`，以验证新的兜底逻辑。

**工作逻辑**：`RESTCatalogAdapter.handleOAuthRequest` 中，`client_credentials` 分支原本返回：

```java
return OAuthTokenResponse.builder()
    .withToken("client-credentials-token:sub=" + request.get("client_id"))
    .withIssuedTokenType("urn:ietf:params:oauth:token-type:access_token")  // 删除
    .withTokenType("Bearer")
    .build();
```

修改后删除 `.withIssuedTokenType(...)` 一行，模拟 RFC 6749 兼容（但不支持 RFC 8693）的认证服务器行为。`token-exchange` 分支保持不变（仍包含 `withIssuedTokenType`），因为 RFC 8693 要求 token exchange 响应必须包含该字段。

这样，所有依赖 `RESTCatalogAdapter` 的测试（如 `TestRESTCatalog` 中的 `testTableAuth` 等）在走 client credentials 流程时，都会触发 `OAuth2Util.fromTokenResponse` 的新兜底逻辑，验证 `issuedTokenType` 为 null 时默认为 `access_token` 的行为。

## 小结

- **成效**：使 Iceberg REST Catalog 客户端兼容只实现 RFC 6749（不实现 RFC 8693）的认证服务器。当 token 响应中缺少 `issued_token_type` 时，默认假定为 `access_token` 类型，避免 `tokenType` 为 null 导致的后续问题。测试同步更新以覆盖新场景。
- **影响范围**：2 个文件，`OAuth2Util.java` 新增 8 行（含注释与 null 兜底逻辑），`RESTCatalogAdapter.java` 删除 1 行。改动非常局部，只影响 `fromTokenResponse` 的 tokenType 赋值逻辑，不影响其他 OAuth 流程（如 token exchange 仍走原有逻辑）。
- **回迁到 1.4.x 的注意事项**：该提交是兼容性修复，**适合回迁到 1.4.x**。理由：
  1. 1.4.x 的 `OAuth2Util.fromTokenResponse` 应该有同样的"直接使用 `response.issuedTokenType()` 不做 null 检查"问题，遇到只支持 RFC 6749 的认证服务器时会出现 `tokenType` 为 null；
  2. `OAuth2Properties.ACCESS_TOKEN_TYPE` 常量在 1.4.x 中应已存在，可直接复用；
  3. 测试辅助类 `RESTCatalogAdapter` 在 1.4.x 中应同样存在，可同步删除 `client_credentials` 分支的 `withIssuedTokenType`；
  4. 改动小、风险低、收益明确（扩展认证服务器兼容性），建议优先回迁；
  5. 回迁时需确认 1.4.x 中 `fromTokenResponse` 的方法签名与 main 一致（带 `credential` 参数的私有重载），若 1.4.x 已有自定义改动，需手动合并；
  6. 注意此修复与 0894（#10345，REST 安全修复）是同一作者在同一时间段提交的 OAuth 相关改动，若 1.4.x 已回迁 0894，建议一并回迁本提交以保持 OAuth 模块的一致性。
