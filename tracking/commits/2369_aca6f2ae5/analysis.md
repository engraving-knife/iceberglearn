# 提交 2369：Core: Add 'google' auth type to auth manager (#13564)

## 提交信息

- **序号**：2369 / 4088
- **哈希**：aca6f2ae5980e6be335ca0d027552217bcb98b7a
- **短哈希**：aca6f2ae5
- **日期**：2025-07-17 18:09:32 -1000
- **作者**：Yuya Ebihara
- **提交说明**：Core: Add 'google' auth type to auth manager (#13564)
- **PR/Issue**：#13564

## 总体目的

本提交为 Iceberg REST 客户端的认证管理器（AuthManager）添加了对 Google 认证类型的支持。在此之前，REST 客户端的认证管理器已经支持 NONE、BASIC、OAUTH2 和 SIGV4 四种认证类型，但缺少对 Google 云平台认证的原生支持。

通过本次修改，用户在使用 Iceberg REST 客户端连接需要 Google 认证的服务时，可以直接通过配置 `rest.auth.type=google` 来启用 GoogleAuthManager，而无需手动指定实现类的全限定名。这降低了配置复杂度，并与其他认证类型（如 sigv4）的使用方式保持一致。

## 如何达成设计目的

设计思路是通过扩展 AuthProperties 中预定义的认证类型常量，并在 AuthManagers 的加载逻辑中增加对应的 case 分支，将字符串 "google" 映射到 `GoogleAuthManager` 实现类。具体设计点如下：

1. 在 AuthProperties 中新增 `AUTH_TYPE_GOOGLE` 常量（值为 "google"）和 `AUTH_MANAGER_IMPL_GOOGLE` 常量（值为 `org.apache.iceberg.gcp.auth.GoogleAuthManager`）。
2. 在 AuthManagers.loadAuthManager 的 switch 语句中增加对 AUTH_TYPE_GOOGLE 的处理分支。
3. 添加测试用例验证通过类型字符串可以正确加载 GoogleAuthManager 实例。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/auth/AuthManagers.java` (+3/-0 lines)

**修改目的**：在认证管理器加载逻辑中增加对 google 类型的支持。

**工作逻辑**：在 loadAuthManager 方法的 switch 语句中，新增一个 case 分支，当认证类型为 `AUTH_TYPE_GOOGLE` 时，将 impl 变量设置为 `AUTH_MANAGER_IMPL_GOOGLE`，后续逻辑会根据该实现类全限定名通过反射加载对应的 AuthManager 实例。

### `core/src/main/java/org/apache/iceberg/rest/auth/AuthProperties.java` (+3/-0 lines)

**修改目的**：定义 google 认证类型及其对应实现类的常量。

**工作逻辑**：新增两个 public static final 常量：`AUTH_TYPE_GOOGLE`（值为 "google"）用于配置项的值匹配，`AUTH_MANAGER_IMPL_GOOGLE`（值为 "org.apache.iceberg.gcp.auth.GoogleAuthManager"）用于指定实现类的全限定名。这与现有的 SIGV4 等认证类型的定义模式完全一致。

### `gcp/src/test/java/org/apache/iceberg/gcp/auth/TestGoogleAuthManager.java` (+11/-0 lines)

**修改目的**：验证通过 AuthManagers.loadAuthManager 可以正确加载 GoogleAuthManager。

**工作逻辑**：新增 testLoadAuthManager 测试方法，调用 AuthManagers.loadAuthManager 方法，传入包含 `AuthProperties.AUTH_TYPE=google` 的属性映射，断言返回的 AuthManager 实例是 GoogleAuthManager 类型的实例。该测试确保类型字符串到实现类的映射正确工作。

## 总结

本提交是一个小型的功能增强，为 Iceberg REST 客户端的认证管理器添加了 Google 认证类型的便捷配置支持。修改量很小（仅 17 行新增），遵循了现有认证类型的扩展模式，使 Google 认证的使用方式与 SIGV4 等保持一致，提升了用户体验和配置的一致性。
