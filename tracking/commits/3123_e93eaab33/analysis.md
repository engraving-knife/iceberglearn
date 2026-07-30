# 提交 3123：Aliyun: Add RRSA support for OSS authentication (#14443)

## 提交信息

- **序号**：3123 / 4088
- **哈希**：e93eaab330581999d8fd8e4e6f5f885446cf28c3
- **短哈希**：e93eaab33
- **日期**：2026-01-17 08:59:18 -0800
- **作者**：zhaoyunjiong
- **提交说明**：Aliyun: Add RRSA support for OSS authentication (#14443)
- **PR/Issue**：#14443

## 总体目的

Iceberg 的 aliyun 模块用于在阿里云 OSS 上读写表数据。此前 `AliyunClientFactories` 创建 OSS 客户端时只支持两种凭据方式：长期 AK/SK（access key/secret key）或临时安全令牌（security token，STS）。这两种方式都要求把凭据硬编码或通过配置显式传入，在 Kubernetes 等容器环境中容易造成凭据泄露、轮换困难。

阿里云 ACK（容器服务 Kubernetes）提供了 RRSA（RAM Roles for Service Accounts）机制：通过 OIDC 信任让 Pod 关联的 ServiceAccount 假设特定的 RAM 角色，从而无凭据（免硬编码）地访问云服务 API。其工作依赖三个环境变量：`ALIBABA_CLOUD_OIDC_PROVIDER_ARN`、`ALIBABA_CLOUD_ROLE_ARN`、`ALIBABA_CLOUD_OIDC_TOKEN_FILE`，由 ACK 在 Pod 中自动注入。本提交为 Iceberg 的 aliyun OSS 客户端工厂增加 RRSA 支持：当检测到这些环境变量时，自动使用阿里云 SDK 的 `OIDCRoleArnCredentialProvider` 获取短期凭据并构造 OSS 客户端，无需用户配置 AK/SK。这使 Iceberg 能在 ACK RRSA 环境下安全、免密地访问 OSS，降低凭据泄露风险并与云原生工作负载对齐。

## 如何达成设计目的

在 `DefaultAliyunClientFactory.newOSSClient()` 中于原有凭据分支之前插入 RRSA 检测分支：先用 `isRrsaEnvironmentAvailable()` 检查三个环境变量是否齐全；若是，则构造 `OIDCRoleArnCredentialProvider`（内置缓存与自动刷新），并适配为一个 OSS `CredentialsProvider`，在 `getCredentials()` 时从 OIDC provider 取 `CredentialModel` 转换为 OSS 的 `BasicCredentials`（含 accessKeyId/accessKeySecret/securityToken/过期时间），最终用 `OSSClientBuilder().build(endpoint, ossCredProvider)` 创建客户端。若 RRSA 环境不可用则回退到原有 AK/SK 或 STS 逻辑。同时在版本目录与 build.gradle 中引入 `credentials-java` 与 `tea` 两个阿里云 SDK 依赖（`OIDCRoleArnCredentialProvider` 所在），并新增测试用 junit-pioneer 的 `@SetEnvironmentVariable` 模拟环境变量验证检测与客户端构造。

## 修改详情

### `aliyun/src/main/java/org/apache/iceberg/aliyun/AliyunClientFactories.java` (+75/-1 lines)

**修改目的**：在默认 OSS 客户端工厂中增加 RRSA 凭据分支。

**工作逻辑**：
新增导入 `CredentialModel`、`OIDCRoleArnCredentialProvider`、`BasicCredentials`、`Credentials`、`CredentialsProvider`，以及 slf4j Logger。`DefaultAliyunClientFactory` 增加 LOG。

新增 `isRrsaEnvironmentAvailable()`：读取环境变量 `ALIBABA_CLOUD_OIDC_PROVIDER_ARN`、`ALIBABA_CLOUD_ROLE_ARN`、`ALIBABA_CLOUD_OIDC_TOKEN_FILE`，三者均非空（用 `Strings.isNullOrEmpty`）时返回 true。Javadoc 解释 RRSA 全称（RAM Roles for Service Accounts）及其免硬编码凭据的优势，并附官方文档链接。

`newOSSClient()` 改造：取 `endpoint = aliyunProperties.ossEndpoint()`；若 `isRrsaEnvironmentAvailable()` 为真，则日志记录后构造 `OIDCRoleArnCredentialProvider.builder().build()`（注释说明其内置缓存与自动刷新）；再实现一个匿名 `CredentialsProvider`：`setCredentials` 空实现，`getCredentials()` 中调用 `oidcProvider.getCredentials()` 得到 `CredentialModel cred`，计算 `expirationSeconds = (cred.getExpiration() - now)/1000`（expiration>0 时），构造 `new BasicCredentials(accessKeyId, accessKeySecret, securityToken, expirationSeconds)` 缓存并返回；异常时包装为 `RuntimeException("Failed to get RRSA credentials")`。最终 `new OSSClientBuilder().build(endpoint, ossCredProvider)` 返回客户端；整体异常包装为 `RuntimeException("Failed to create RRSA OSS client")`。原 `if (Strings.isNullOrEmpty(aliyunProperties.securityToken()))` 改为 `else if`，保持 AK/SK 与 STS 分支不变。

### `build.gradle` (+3/-0 lines)

**修改目的**：为 aliyun 模块添加 RRSA 所需依赖与测试依赖。

**工作逻辑**：
在 `:iceberg-aliyun` 的 `dependencies` 中新增 `implementation libs.aliyun.credentials.java`（提供 `OIDCRoleArnCredentialProvider`）与 `implementation libs.aliyun.tea`（credentials-java 的传递依赖，显式声明）；测试依赖新增 `testImplementation libs.junit.pioneer`（提供 `@SetEnvironmentVariable` 注解用于环境变量测试）。

### `gradle/libs.versions.toml` (+6/-0 lines)

**修改目的**：在版本目录声明新增依赖的版本与别名。

**工作逻辑**：
`[versions]` 新增 `aliyun-credentials-java = "0.3.2"`、`aliyun-tea = "1.2.1"`、`junit-pioneer = "2.3.0"`。`[libraries]` 新增 `aliyun-credentials-java = { module = "com.aliyun:credentials-java", version.ref = "aliyun-credentials-java" }`、`aliyun-tea = { module = "com.aliyun:tea", version.ref = "aliyun-tea" }`、`junit-pioneer = { module = "org.junit-pioneer:junit-pioneer", version.ref = "junit-pioneer" }`。这些别名供 build.gradle 引用。

### `aliyun/src/test/java/org/apache/iceberg/aliyun/TestAliyunClientFactories.java` (+59/-0 lines)

**修改目的**：验证 RRSA 环境检测与客户端构造。

**工作逻辑**：
导入 `org.junitpioneer.jupiter.SetEnvironmentVariable`。新增两个测试：
- `testRRSAEnvironmentDetection`：用 `@SetEnvironmentVariable` 注解设置三个 RRSA 环境变量为伪造值，初始化工厂后断言 `isRrsaEnvironmentAvailable()` 为 true；调用 `newOSSClient()` 断言客户端非空；随后尝试 `client.doesBucketExist("test-bucket")`，因凭据为伪造值预期抛异常（若意外成功则抛 `AssertionError`），在 catch 中断言异常非空，`finally` 中 `client.shutdown()`。
- `testIsRrsaEnvironmentAvailableWithoutEnvVars`：不设环境变量，断言 `isRrsaEnvironmentAvailable()` 为 false（假设测试环境未设置 RRSA 变量）。

## 总结

本提交为 Iceberg 的阿里云 OSS 集成增加了 RRSA（RAM Roles for Service Accounts）免密认证支持，使其能在 ACK 容器环境中通过 OIDC 自动获取短期凭据访问 OSS，无需硬编码 AK/SK。实现通过检测三个环境变量、利用阿里云 SDK 的 `OIDCRoleArnCredentialProvider`（内置缓存与刷新）适配为 OSS `CredentialsProvider` 完成，并回退兼容原有凭据方式。配合 junit-pioneer 环境变量注解的测试验证了检测与构造逻辑，提升了在云原生部署下的安全性与易用性。
