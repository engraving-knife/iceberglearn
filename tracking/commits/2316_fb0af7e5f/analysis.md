# 提交 2316：S3: Add LegacyMd5Plugin to S3 client builder (#12264)

## 提交信息

- **序号**：2316 / 4088
- **哈希**：fb0af7e5fcb45fe69a80ae8cb11208fb7b36a956
- **短哈希**：fb0af7e5f
- **日期**：2025-07-04 09:57:39 +0200
- **作者**：Yuya Ebihara
- **提交说明**：S3: Add LegacyMd5Plugin to S3 client builder (#12264)
- **PR/Issue**：#12264

## 总体目的

这个提交解决了 AWS SDK 2.30.0 引入的向后兼容性问题。从 AWS SDK 2.30.0 版本开始，S3 客户端引入了新的数据完整性保护机制（基于 CRC 的校验和），这一机制与旧版 S3 兼容存储（如旧版 MinIO）不兼容。当使用新版 SDK 访问不支持新完整性检查的 S3 兼容存储时，会因校验和不匹配而导致请求失败。

为了解决这一兼容性问题，AWS SDK 提供了 `LegacyMd5Plugin` 插件，该插件可以回退到传统的 MD5 校验和行为。本提交在 Iceberg 的 AWS 模块中添加了对该插件的可配置支持，默认不启用（`false`），仅当用户需要访问旧版 S3 兼容存储时才通过配置开启。

此外，提交还重构了 `AwsClientProperties` 中多个方法的泛型签名，使其更精确地匹配 AWS SDK 的 `AwsClientBuilder` 类型层次结构，并将插件应用到所有 S3 客户端创建路径。

## 如何达成设计目的

1. 新增配置项 `client.legacy-md5-plugin-enabled`（默认 false）
2. 在 `AwsClientProperties` 中实现 `applyLegacyMd5Plugin` 方法，根据配置决定是否添加 `LegacyMd5Plugin`
3. 在所有 S3 客户端构建路径（`AwsClientFactories`、`AssumeRoleAwsClientFactory`、`DefaultS3FileIOAwsClientFactory`、`LakeFormationAwsClientFactory`）中调用此方法
4. 重构泛型方法签名以兼容新 SDK 类型系统
5. 新增集成测试 `TestS3FileIOWithLegacyMinIO` 验证旧版 MinIO 兼容性

## 修改详情

### `aws/src/main/java/org/apache/iceberg/aws/AwsClientProperties.java` (+38/-5 lines)

**修改目的**：添加 LegacyMd5Plugin 配置和应用逻辑。

**工作逻辑**：
- 新增 `LEGACY_MD5_PLUGIN_ENABLED` 常量和 `legacyMd5pluginEnabled` 字段，默认 false
- 新增 `applyLegacyMd5Plugin` 方法：当配置启用时，通过 `builder.addPlugin(LegacyMd5Plugin.create())` 添加插件
- 重构 `applyClientRegionConfiguration`、`applyClientCredentialConfigurations`、`applyRetryConfigurations` 方法的泛型签名为 `<BuilderT extends AwsClientBuilder<BuilderT, ClientT>, ClientT>`，以匹配 S3 客户端构建器的类型层次（因为 `S3ClientBuilder` 继承自 `AwsClientBuilder` 但有额外的 `addPlugin` 方法）

### `aws/src/main/java/org/apache/iceberg/aws/AwsClientFactories.java` (+2/-0 lines)

**修改目的**：在基础 S3 客户端工厂中应用 LegacyMd5Plugin。

**工作逻辑**：在 `s3()` 和异步 `s3Async()` 方法的构建链中添加 `.applyMutation(awsClientProperties::applyLegacyMd5Plugin)`。

### `aws/src/main/java/org/apache/iceberg/aws/AssumeRoleAwsClientFactory.java` (+2/-0 lines)

**修改目的**：在 AssumeRole 场景的 S3 客户端中应用 LegacyMd5Plugin。

### `aws/src/main/java/org/apache/iceberg/aws/s3/DefaultS3FileIOAwsClientFactory.java` (+2/-0 lines)

**修改目的**：在默认 S3FileIO 客户端工厂中应用 LegacyMd5Plugin。

### `aws/src/main/java/org/apache/iceberg/aws/lakeformation/LakeFormationAwsClientFactory.java` (+1/-0 lines)

**修改目的**：在 Lake Formation 场景的 S3 客户端中应用 LegacyMd5Plugin。

### `aws/src/integration/java/org/apache/iceberg/aws/s3/MinioUtil.java` (+17/-4 lines)

**修改目的**：支持创建不同版本的 MinIO 容器和带 LegacyMd5 插件的 S3 客户端。

**工作逻辑**：
- 新增 `LATEST_TAG = "latest"` 和 `LEGACY_TAG = "RELEASE.2024-12-18T13-15-44Z"`（不支持强完整性检查的旧版）
- `createContainer` 方法重构为接受 tag 参数
- `createS3Client` 新增 `legacyMd5PluginEnabled` 参数

### `aws/src/integration/java/org/apache/iceberg/aws/s3/TestS3FileIO.java` (+15/-4 lines)

**修改目的**：重构测试以支持可配置的 MinIO 版本和 LegacyMd5 插件。

**工作逻辑**：
- 将静态 `MINIO` 容器改为实例方法 `createMinIOContainer()`，允许子类覆写
- 新增 `legacyMd5PluginEnabled()` 方法返回 false（子类可覆写）
- S3 客户端创建时传入 `legacyMd5PluginEnabled()` 参数

### `aws/src/integration/java/org/apache/iceberg/aws/s3/TestS3FileIOWithLegacyMinIO.java` (+37/-0 lines, 新文件)

**修改目的**：使用旧版 MinIO 测试 LegacyMd5 插件的兼容性。

**工作逻辑**：继承 `TestS3FileIO`，覆写 `createMinIOContainer` 使用 `LEGACY_TAG` 版本的 MinIO，覆写 `legacyMd5PluginEnabled()` 返回 true。

### `aws/src/integration/java/org/apache/iceberg/aws/s3/signer/TestS3RestSigner.java` (+2/-1 lines)

**修改目的**：适配 MinioUtil API 变更。

## 总结

这个提交解决了 AWS SDK 2.30.0+ 与旧版 S3 兼容存储的向后兼容性问题。通过可配置的 `LegacyMd5Plugin` 支持，用户可以在需要时回退到传统的 MD5 校验和行为。变更覆盖了所有 S3 客户端创建路径，并新增了针对旧版 MinIO 的集成测试。设计上保持了默认不启用、向后兼容的原则。
