# 提交 2504：Core: Allow disabling token exchange as refresh (#13809)

## 提交信息

- **序号**：2504 / 4088
- **哈希**：af82d34e78d82690892127d46b7f2937b29f362f
- **短哈希**：af82d34e7
- **日期**：2025-08-15 08:14:04 -0700
- **作者**：Daniel Weeks
- **提交说明**：Core: Allow disabling token exchange as refresh (#13809)
- **PR/Issue**：#13809

## 总体目的

本提交为 Iceberg REST Catalog 的 OAuth2 认证流程新增了一个配置项 `token-exchange-enabled`，允许用户禁用 token exchange 作为令牌刷新方式，转而直接使用 client credential flow 获取新令牌。

在 Iceberg REST Catalog 的 OAuth2 认证中，令牌刷新有两种方式：
1. **Token Exchange**：使用当前令牌作为 subject token，通过 RFC 8693 token exchange 获取新令牌。这是默认的首选方式。
2. **Client Credentials**：使用客户端凭证（credential）直接获取新令牌。

某些身份提供商（IDP）不支持 token exchange 流程。在这些环境中，原本的代码会尝试 token exchange 并失败，然后才能 fallback 到 client credential flow。这不仅增加了无意义的网络请求和错误处理，还可能因为错误响应导致刷新失败。

本提交新增 `token-exchange-enabled` 配置（默认为 `true` 保持兼容），当设置为 `false` 时，刷新流程直接跳过 token exchange，使用 client credential flow。这为不支持 token exchange 的 IDP 提供了更优雅的解决方案。

## 如何达成设计目的

关键设计点：

1. **新增配置项**：在 `OAuth2Properties` 中定义 `TOKEN_EXCHANGE_ENABLED` 和默认值 `true`。
2. **AuthConfig 支持**：在 `AuthConfig` 接口中新增 `exchangeEnabled()` 默认方法，从属性中读取配置。
3. **条件化刷新逻辑**：在 `OAuth2Util.refreshToken()` 中，根据 `config.exchangeEnabled()` 选择执行 token exchange 还是 client credential flow。
4. **重构刷新逻辑**：将原先分散的参数（scope、oauth2ServerUri 等）统一通过 `AuthConfig` 传递，简化方法签名。
5. **调整 expired token 逻辑**：`refreshExpiredToken` 也根据 exchangeEnabled 选择刷新方式，当 credential 为 null 时直接返回 null。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/auth/OAuth2Properties.java` (+9/-0 lines)

**修改目的**：定义新的配置项。

**工作逻辑**：新增 `TOKEN_EXCHANGE_ENABLED = "token-exchange-enabled"` 和 `TOKEN_EXCHANGE_ENABLED_DEFAULT = true`，并添加 Javadoc 说明此配置用于不支持 token exchange 的 IDP。

### `core/src/main/java/org/apache/iceberg/rest/auth/AuthConfig.java` (+7/-2 lines)

**修改目的**：在 AuthConfig 中暴露 exchangeEnabled 配置。

**工作逻辑**：
- `keepRefreshed()` 的默认值改为引用 `OAuth2Properties.TOKEN_REFRESH_ENABLED_DEFAULT` 常量（保持行为不变，只是统一常量引用）。
- 新增 `exchangeEnabled()` 默认方法，返回 `OAuth2Properties.TOKEN_EXCHANGE_ENABLED_DEFAULT`。
- 在 `fromProperties()` 静态工厂方法中，从属性 Map 读取 `token-exchange-enabled` 配置。

### `core/src/main/java/org/apache/iceberg/rest/auth/OAuth2Util.java` (+30/-39 lines)

**修改目的**：根据 exchangeEnabled 配置选择刷新策略。

**工作逻辑**：
- `refreshToken` 方法签名简化，将多个参数（scope、oauth2ServerUri）替换为 `AuthConfig config` 参数。
- 当 `config.exchangeEnabled()` 为 true 时，执行原有的 token exchange 请求逻辑。
- 当 `config.exchangeEnabled()` 为 false 时，如果 credential 存在，直接调用 `fetchToken` 使用 client credential flow；否则返回 null。
- `refreshExpiredToken` 方法逻辑调整：当 credential 为 null 时直接返回 null；当 exchangeEnabled 为 true 时使用 basic auth + token exchange；当 exchangeEnabled 为 false 时直接使用 client credential flow。

### `core/src/test/java/org/apache/iceberg/rest/TestRESTCatalog.java` (+143/-0 lines)

**修改目的**：测试禁用 token exchange 后的刷新行为。

**工作逻辑**：新增测试验证当 `token-exchange-enabled` 设置为 false 时，令牌刷新使用 client credential flow 而非 token exchange。

### `core/src/test/java/org/apache/iceberg/rest/auth/TestOAuth2Util.java` (+45/-0 lines)

**修改目的**：单元测试 OAuth2Util 的刷新逻辑。

## 总结

本提交为 Iceberg REST Catalog 的 OAuth2 认证提供了更灵活的配置，解决了部分 IDP 不支持 token exchange 的问题。通过新增配置项，用户可以直接禁用 token exchange 流程，避免不必要的错误请求。代码重构将分散的参数统一到 AuthConfig 中，提升了代码可维护性。这是一个面向兼容性和可靠性的改进。
