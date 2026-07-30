# 提交 2730：Make KMS endpoint configurable via kms.endpoint AWS property

## 提交信息

- **序号**：2730 / 4088
- **哈希**：77bc1d08ecd46b1d62a3fb51ad7d1935788f30be
- **短哈希**：77bc1d08e
- **日期**：2025-10-11 10:16:29 -0700
- **作者**：Thomas Powell
- **提交说明**：Make KMS endpoint configurable via kms.endpoint AWS property
- **PR/Issue**：#14246

## 总体目的

在 Iceberg 的 AWS 集成模块中，KMS（Key Management Service）用于表密钥的加密与解密。此前的实现中，S3、Glue、DynamoDB 等服务都支持通过配置项自定义端点（endpoint），以便连接到兼容服务（例如 LocalStack、MinIO 等本地测试环境，或私有部署的兼容服务）。然而 KMS 客户端缺少类似的端点配置能力，这导致用户无法将 Iceberg 的 KMS 密钥管理指向非 AWS 官方的 KMS 兼容服务。

本提交的目的是补齐这一缺失的能力，新增 `kms.endpoint` 配置项，使用户可以为 KMS 客户端指定自定义端点。这在以下场景中尤其有用：本地开发与测试环境（使用 KMS 兼容的 mock 服务）、私有云或混合云部署中使用自建 KMS 兼容服务，以及需要通过代理访问 KMS 的企业网络环境。

该改动保持了与现有 S3/Glue/DynamoDB 端点配置一致的设计风格，使整体 AWS 客户端配置 API 风格统一。

## 如何达成设计目的

主要设计思路是参照已有的 Glue、DynamoDB 端点配置模式，为 KMS 客户端添加同等的端点覆盖能力：

1. **新增配置常量** `KMS_ENDPOINT = "kms.endpoint"`：与 `glue.endpoint`、`dynamodb.endpoint` 等命名风格一致
2. **在 `AwsProperties` 中持有 `kmsEndpoint` 字段**：从 properties 中读取并存储
3. **新增 `applyKmsEndpointConfigurations(KmsClientBuilder)` 方法**：复用已有的 `configureEndpoint` 工具方法，将端点应用到 `KmsClientBuilder`
4. **在 `AwsClientFactories.kms()` 中应用该配置**：通过 `applyMutation(awsProperties::applyKmsEndpointConfigurations)` 注入到 KMS 客户端构建流程
5. **更新过期方法文档**：将 `configureEndpoint` 的 @Deprecated 注释补充 KMS 的指引

## 修改详情

### `aws/src/main/java/org/apache/iceberg/aws/AwsProperties.java` (+29/-0 lines)

**修改目的**：新增 KMS 端点配置常量、字段、读取逻辑与应用方法。

**工作逻辑**：
- 新增 `KMS_ENDPOINT` 常量，附带 Javadoc 说明可用于连接任何 KMS 兼容服务
- 新增实例字段 `kmsEndpoint`，在无参构造函数中初始化为 `null`，在带 properties 的构造函数中通过 `properties.get(KMS_ENDPOINT)` 读取
- 新增 `applyKmsEndpointConfigurations(T builder)` 方法，接收 `KmsClientBuilder` 类型参数，内部调用已有的 `configureEndpoint(builder, kmsEndpoint)` 应用端点（当 kmsEndpoint 为 null 时 `configureEndpoint` 不做任何操作）
- 新增 `kmsEndpoint()` getter 方法

### `aws/src/main/java/org/apache/iceberg/aws/AwsClientFactories.java` (+7/-2 lines)

**修改目的**：在 KMS 客户端构建流程中注入端点配置。

**工作逻辑**：
- 在 `kms()` 方法中，于 `applyClientRegionConfiguration` 与 `applyHttpClientConfigurations` 之后、`applyClientCredentialConfigurations` 之前，加入 `applyMutation(awsProperties::applyKmsEndpointConfigurations)`，确保端点覆盖在客户端构建过程中被应用
- 导入 `KmsClientBuilder` 类
- 更新 `configureEndpoint` 方法的 @Deprecated Javadoc，补充指向新的 `applyKmsEndpointConfigurations` 方法

### `aws/src/integration/java/org/apache/iceberg/aws/TestDefaultAwsClientFactory.java` (+13/-0 lines)

**修改目的**：添加集成测试验证 KMS 端点覆盖生效。

**工作逻辑**：新增 `testKmsEndpointOverride` 测试，设置 `kms.endpoint` 为 `https://unknown:1234`，构建 KmsClient 并调用 `listKeys()`，断言抛出 `SdkClientException` 且消息包含 "Unable to execute HTTP request: unknown"——这与现有 S3/Glue 端点测试模式一致，通过尝试连接一个不存在的端点来验证端点配置确实被应用。

## 总结

本提交为 Iceberg AWS 模块的 KMS 客户端补齐了端点可配置能力，使 KMS 与 S3/Glue/DynamoDB 在端点配置上保持一致。改动小而聚焦，复用了已有的 `configureEndpoint` 工具方法，并提供了对应的集成测试。这对需要在非 AWS 官方 KMS 环境（本地测试、私有云等）中使用 Iceberg 加密功能的用户来说是必要的改进。
