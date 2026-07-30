# 提交 2183：AWS: Configure Default s3Async credentials the same as s3 (#13132)

## 提交信息

- **序号**：2183 / 4088
- **哈希**：a8b42450d17df355c05554b38e4821f2de1d8eab
- **短哈希**：a8b42450d
- **日期**：2025-05-29 10:51:14 -0700
- **作者**：Devin Smith
- **提交说明**：AWS: Configure Default s3Async credentials the same as s3 (#13132)
- **PR/Issue**：#13132

## 总体目的

此提交修复了 S3 异步客户端（s3Async）的凭证配置不一致问题。在原来的实现中，同步 S3 客户端使用 `s3FileIOProperties.applyCredentialConfigurations` 来配置凭证，该方法会考虑远程签名（remote signing）等 S3 特定的配置（如使用匿名凭证）。但异步 S3 客户端却直接使用 `awsClientProperties::applyClientCredentialConfigurations` 配置凭证，绕过了 S3 特定的凭证逻辑。这导致在启用远程签名等场景下，异步客户端的凭证配置与同步客户端不一致，可能导致认证失败。此提交使异步客户端的凭证配置与同步客户端保持一致。

## 如何达成设计目的

- 在 `AwsClientFactories` 中，将 S3 异步客户端（包括 CRT 和非 CRT 两种）的凭证配置从 `awsClientProperties::applyClientCredentialConfigurations` 改为 `s3FileIOProperties.applyCredentialConfigurations(awsClientProperties, b)`
- 重构 `S3FileIOProperties.applyCredentialConfigurations` 方法，提取公共的凭证提供者逻辑，并添加对 `S3CrtAsyncClientBuilder` 的重载支持

## 修改详情

### `aws/src/main/java/org/apache/iceberg/aws/AwsClientFactories.java` (修改, +6/-2 lines)

**修改目的**：使 S3 异步客户端使用与同步客户端相同的凭证配置方式。

**工作逻辑**：在创建 S3AsyncClient（CRT builder 和标准 builder）时，将 `.applyMutation(awsClientProperties::applyClientCredentialConfigurations)` 替换为 `.applyMutation(b -> s3FileIOProperties.applyCredentialConfigurations(awsClientProperties, b))`，使异步客户端也走 S3 特定的凭证配置逻辑。

### `aws/src/main/java/org/apache/iceberg/aws/s3/S3FileIOProperties.java` (修改, +15/-5 lines)

**修改目的**：支持异步客户端构建器的凭证配置并提取公共逻辑。

**工作逻辑**：
- 添加 `AwsCredentialsProvider` 和 `S3BaseClientBuilder` 的 import
- 将原 `applyCredentialConfigurations` 方法签名从 `<T extends S3ClientBuilder>` 改为 `<T extends S3BaseClientBuilder<T, ?>>`，使其能同时支持同步和异步客户端构建器（S3BaseClientBuilder 是 S3ClientBuilder 和 S3AsyncClientBuilder 的共同父接口）
- 新增 `applyCredentialConfigurations` 方法重载，接受 `S3CrtAsyncClientBuilder`（CRT 异步客户端构建器），因为 CRT builder 不继承 S3BaseClientBuilder
- 提取 `getCredentialsProvider` 私有方法，返回根据 `isRemoteSigningEnabled` 判断的凭证提供者（远程签名时返回匿名凭证，否则返回 AwsClientProperties 配置的凭证），供两个重载方法共用

## 总结

此提交修复了 S3 异步客户端凭证配置与同步客户端不一致的问题。通过使异步客户端也使用 `s3FileIOProperties.applyCredentialConfigurations` 配置凭证，确保了远程签名等 S3 特定场景下凭证配置的一致性。重构了方法签名以支持不同类型的客户端构建器，并提取了公共的凭证提供者逻辑。
