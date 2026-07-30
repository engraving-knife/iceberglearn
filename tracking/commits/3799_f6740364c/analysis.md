# 提交 3799：Core: Fix optionalOAuthParams dropped during non-exchange token refresh (#16022) (#16023)

## 提交信息

- **序号**：3799 / 4088
- **哈希**：f6740364c46afb811fd57c8ca965555600f644bc
- **短哈希**：f6740364c
- **日期**：2026-05-30 12:30:21 -0700
- **作者**：Bharath Krishna <bmurali@roku.com>
- **提交说明**：Core: Fix optionalOAuthParams dropped during non-exchange token refresh (#16022) (#16023)
- **PR/Issue**：#16022（原始 issue/PR），#16023（修复 PR）

## 总体目的

本提交修复 Iceberg REST 客户端 OAuth2 鉴权流程中的一个缺陷：在"非 token exchange"路径下刷新 token 时，配置中携带的 `optionalOAuthParams`（例如 `audience`、`scope` 等额外参数）被错误地丢弃，未传递给 token 端点。这会导致刷新后的 token 缺少必要的声明（如 audience），进而可能被下游服务拒绝，造成鉴权失败或访问异常。

具体场景是：当 `exchangeEnabled=false`（即不通过 token exchange 方式刷新，而是直接用 client credentials 向 OAuth2 服务器申请新 token）时，代码在两处调用 `fetchToken` 时硬编码传入 `ImmutableMap.of()`（空 map）作为可选参数，而不是使用会话配置中的 `optionalOAuthParams`。本提交将这两处改为传入 `optionalOAuthParams`，确保刷新请求与首次请求保持一致的参数集。

该修复对部署中使用自定义 OAuth2 端点（需要 `audience` 等参数）的 REST catalog 用户尤为重要，否则每次 token 过期后刷新都会失败。

## 如何达成设计目的

修复方式非常直接：将两处 `fetchToken(...)` 调用中的 `ImmutableMap.of()` 替换为对应的 `optionalOAuthParams` 变量。一处在 `OAuth2Util` 的静态方法中（用于普通 `AuthConfig` 刷新），另一处在内部 `AuthSession` 类的 `refresh` 方法中。同时新增两个单元测试覆盖"token 已过期"和"token 未过期但走非 exchange 刷新"两种场景，验证刷新请求的表单数据中确实包含 `audience` 与 `scope`。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/auth/OAuth2Util.java` (+2/-2 lines)

**修改目的**：在非 exchange 路径下刷新 token 时，把 `optionalOAuthParams` 真正传递给 `fetchToken`，而不是传空 map。

**工作逻辑**：
- 第一处位于静态刷新方法中，将 `ImmutableMap.of()` 改为 `optionalOAuthParams`：
```java
return fetchToken(
    client, Map.of(), credential(), scope(), oauth2ServerUri(), optionalOAuthParams());
```
- 第二处位于 `AuthSession.refresh` 的非 exchange 分支，同样把 `ImmutableMap.of()` 改为 `optionalOAuthParams`：
```java
return fetchToken(
    client, Map.of(), credential(), scope(), oauth2ServerUri(), optionalOAuthParams);
```
这样刷新请求就会带上配置中指定的额外 OAuth 参数，与首次获取 token 的行为一致。

### `core/src/test/java/org/apache/iceberg/rest/auth/TestOAuth2Util.java` (+48/-0 lines)

**修改目的**：新增测试验证非 exchange 刷新路径下 `optionalOAuthParams` 被正确包含在请求中。

**工作逻辑**：
- `refreshExpiredTokenShouldIncludeOptionalOAuthParams`：模拟 token 已过期（`expiresAtMillis = now - 10_000`）的场景。
- `refreshCurrentTokenNonExchangeShouldIncludeOptionalOAuthParams`：模拟 token 未过期（`now + 300_000`）但 `exchangeEnabled=false` 的场景。
- 共用的 `assertRefreshIncludesOptionalOAuthParams` 方法构造一个包含 `audience=https://my-catalog.example.com` 和 `scope=catalog` 的 `AuthConfig`，mock `RESTClient.postForm` 返回一个刷新后的 token 响应，然后调用 `session.refresh(client)`，最后用 `argThat` 验证提交的表单数据中 `grant_type=client_credentials`、`audience` 与 `scope` 均符合预期：
```java
Mockito.verify(client)
    .postForm(
        any(),
        argThat(
            formData ->
                CLIENT_CREDENTIALS.equals(formData.get(GRANT_TYPE))
                    && audience.equals(formData.get("audience"))
                    && "catalog".equals(formData.get("scope"))),
        Mockito.eq(OAuthTokenResponse.class),
        anyMap(),
        any());
```

## 总结

本提交修复了一个影响 REST catalog OAuth2 刷新流程的实际缺陷：非 exchange 路径下 `optionalOAuthParams` 被丢弃。修复极其简洁（仅两行改动），但配合充分的单元测试确保回归被覆盖。对于依赖 `audience` 等自定义参数的部署，此修复解决了 token 刷新后鉴权失败的问题，提升了 Iceberg REST 客户端在复杂 OAuth2 环境下的兼容性。
