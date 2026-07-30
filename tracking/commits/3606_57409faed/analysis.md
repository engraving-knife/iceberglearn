# 提交 3606：Core: Fix child AuthSession inheriting parent's expiresAtMillis (#15999)

## 提交信息

- **序号**：3606 / 4088
- **哈希**：57409faedb184eb3b410a9cbbab52a4e5c0b6f0a
- **短哈希**：57409faed
- **日期**：2026-04-27 23:49:07 -0700
- **作者**：Bharath Krishna
- **提交说明**：Core: Fix child AuthSession inheriting parent's expiresAtMillis (#15999)
- **PR/Issue**：#15999

## 总体目的

这个提交修复了 REST Catalog 认证会话中，子 AuthSession 错误继承父会话过期时间的问题。

Iceberg REST Catalog 支持嵌套的认证会话（AuthSession），子会话从父会话派生。当子会话从 token 响应或访问令牌创建时，应该使用子 token 自身的过期时间（`expiresAtMillis`），而不是继承父会话的过期时间。

之前在创建子会话时，没有调用 `expiresAtMillis()` 设置子 token 的过期时间，导致子会话可能使用了父会话的过期时间。这可能导致两种问题：1）父会话过期时间比子 token 长，子 token 已过期但会话仍认为有效；2）父会话过期时间比子 token 短，子 token 仍有效但会话提前过期。两者都会导致认证行为不正确。

## 如何达成设计目的

在 `OAuth2Util` 中创建子会话的三个位置添加 `.expiresAtMillis(OAuth2Util.expiresAtMillis(token))` 调用，从子 token 中提取过期时间并设置到子会话上。`OAuth2Util.expiresAtMillis()` 方法会解析 JWT token 中的 `exp` claim 来获取过期时间。对于非 JWT 的 opaque token，该方法返回 null，子会话不会有过期时间。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/auth/OAuth2Util.java` (+3/-0 lines)

**修改目的**：在三个创建子会话的位置添加过期时间设置。

**工作逻辑**：

1. **`fromTokenResponse` 方法**（约 529 行）：从 token 响应创建子会话时，添加：
```java
.expiresAtMillis(OAuth2Util.expiresAtMillis(response.token()))
```

2. **`fromAccessToken` 方法**（约 619 行）：从访问令牌创建子会话时，添加：
```java
.expiresAtMillis(OAuth2Util.expiresAtMillis(token))
```

3. **`refresh` 方法**（约 701 行）：刷新令牌后创建新会话时，添加：
```java
.expiresAtMillis(OAuth2Util.expiresAtMillis(response.token()))
```

三处修改都是将子 token 的过期时间从 JWT 的 `exp` claim 中提取出来，设置到子会话的配置中，而不是让子会话继承父会话的过期时间。

### `core/src/test/java/org/apache/iceberg/rest/auth/TestOAuth2Util.java` (+97/-0 lines)

**修改目的**：添加测试验证子会话使用正确的过期时间。

**工作逻辑**：

新增 4 个测试：

1. **`fromTokenResponseUsesChildTokenExpiry`**：验证从 token 响应创建的子会话使用子 token 的过期时间（300 秒），而不是父会话的过期时间（7200 秒）。

2. **`fromTokenResponseOpaqueTokenDoesNotInheritParentExpiry`**：验证当子 token 是 opaque token（非 JWT）时，子会话的 `expiresAtMillis` 为 null，不会继承父会话的过期时间。

3. **`fromAccessTokenUsesChildTokenExpiry`**：验证从访问令牌创建的子会话使用子 token 的过期时间。

4. **`refreshUsesRefreshedTokenExpiry`**：验证刷新令牌后，会话使用新令牌的过期时间（500 秒），而不是原始令牌的过期时间（7200 秒）。

辅助方法：
- `parentSession(expSeconds)`：创建父会话，使用 JWT token。
- `childTokenResponse(token, expiresInSeconds)`：创建 token 响应对象。
- `tokenWithExp(expSeconds)`：创建包含 `exp` claim 的 JWT token。

## 总结

这个提交修复了 REST Catalog 认证会话中子会话过期时间不正确的 bug。修复确保每个子会话使用自身 token 的过期时间，而不是错误地继承父会话的过期时间。这对于正确的 token 刷新和认证生命周期管理至关重要，避免了 token 过期判断错误导致的认证失败或使用过期 token 的安全问题。
