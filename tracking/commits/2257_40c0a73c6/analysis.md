# 提交 2257：GCP: Add Google Authentication Support (#13212)

## 提交信息

- **序号**：2257 / 4088
- **哈希**：40c0a73c6513711b2a58bb159cf8c562fe56c826
- **短哈希**：40c0a73c6
- **日期**：2025-06-19 08:12:07 -0700
- **作者**：Talat UYARER
- **提交说明**：GCP: Add Google Authentication Support
- **PR/Issue**：#13212

## 总体目的

本提交为 Iceberg GCP 模块添加了 Google 认证支持，实现了 `AuthManager` 和 `AuthSession` 接口，使得使用 GCP 服务（如 GCS）的 Iceberg 客户端能够通过 Google Credentials 进行身份认证。

在 Iceberg 的 REST 认证架构中，`AuthManager` 负责管理认证会话的生命周期，`AuthSession` 负责为每个 HTTP 请求添加认证头。此前 GCP 模块缺少专门的认证管理器，用户需要通过其他方式（如环境变量或 GCS 客户端内置的认证）来处理认证。本提交引入了 `GoogleAuthManager` 和 `GoogleAuthSession`，使得 GCP 认证可以统一集成到 Iceberg 的 REST 认证框架中，支持通过服务账号密钥文件或 Application Default Credentials (ADC) 进行认证。

## 如何达成设计目的

- 新增 `GoogleAuthManager` 实现 `AuthManager` 接口，负责加载 Google Credentials 并管理会话生命周期。
- 新增 `GoogleAuthSession` 实现 `AuthSession` 接口，负责将 Google OAuth2 访问令牌添加到 HTTP 请求的 Authorization 头。
- 支持两种凭证加载方式：通过 `gcp.auth.credentials-path` 属性指定服务账号 JSON 密钥文件，或使用 Application Default Credentials。
- 支持通过 `gcp.auth.scopes` 属性自定义 OAuth scopes，默认为 `https://www.googleapis.com/auth/cloud-platform`。
- 新增完整的单元测试覆盖认证管理器和会话的各种场景。

## 修改详情

### `gcp/src/main/java/org/apache/iceberg/gcp/auth/GoogleAuthManager.java` (新增, +146/0 lines)

**修改目的**：实现 Google 认证管理器，管理 Google Credentials 的加载和会话创建。

**工作逻辑**：`GoogleAuthManager` 实现 `AuthManager` 接口。核心方法 `initialize()` 在首次调用时加载凭证：如果配置了 `gcp.auth.credentials-path`，则从指定路径读取服务账号 JSON 密钥文件并创建带 scope 的 `GoogleCredentials`；否则使用 `GoogleCredentials.getApplicationDefault()` 获取 ADC 并创建带 scope 的凭证。`catalogSession()` 方法返回基于已加载凭证的 `GoogleAuthSession`。`contextualSession()` 和 `tableSession()` 直接返回父会话，因为 GCP 令牌通常不需要按上下文或表区分。`close()` 为空操作，因为 `GoogleCredentials` 的生命周期不由此管理器管理。使用 `initialized` 标志确保凭证只加载一次。

### `gcp/src/main/java/org/apache/iceberg/gcp/auth/GoogleAuthSession.java` (新增, +94/0 lines)

**修改目的**：实现 Google 认证会话，为 HTTP 请求添加 OAuth2 Bearer 令牌。

**工作逻辑**：`GoogleAuthSession` 实现 `AuthSession` 接口，持有 `GoogleCredentials` 实例。`authenticate()` 方法首先调用 `credentials.refreshIfExpired()` 确保令牌有效，然后获取 `AccessToken`，将 `"Bearer " + tokenValue` 添加到 HTTP 请求的 `Authorization` 头中。使用 `putIfAbsent` 语义避免覆盖已有的 Authorization 头。如果令牌获取失败则抛出 `IllegalStateException`，IO 异常则包装为 `UncheckedIOException`。`close()` 为空操作。

### `build.gradle` (修改, +1/0 lines)

**修改目的**：为 GCP 模块测试添加 Mockito JUnit Jupiter 依赖。

**工作逻辑**：在 `:iceberg-gcp` 项目的依赖配置中添加 `testImplementation libs.mockito.junit.jupiter`，支持测试中使用 Mockito 框架进行模拟。

### `gcp/src/test/java/org/apache/iceberg/gcp/auth/TestGoogleAuthManager.java` (新增, +193/0 lines)

**修改目的**：测试 GoogleAuthManager 的凭证加载和会话管理逻辑。

### `gcp/src/test/java/org/apache/iceberg/gcp/auth/TestGoogleAuthSession.java` (新增, +168/0 lines)

**修改目的**：测试 GoogleAuthSession 的请求认证和令牌刷新逻辑。

## 总结

本提交为 Iceberg GCP 模块引入了完整的 Google 认证支持，包括 `GoogleAuthManager`（凭证加载和会话管理）和 `GoogleAuthSession`（HTTP 请求认证）。支持服务账号密钥文件和 Application Default Credentials 两种方式，可自定义 OAuth scopes。这使得 GCP 用户能够将认证统一集成到 Iceberg 的 REST 认证框架中。共新增约 600 行代码（含测试），是 GCP 模块认证能力的重要补充。
