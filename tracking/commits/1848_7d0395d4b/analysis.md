# 提交 1848：AWS: Update S3 async client configurations and docs for analytics-accelerator-s3 (#12503)

## 提交信息

- **序号**：1848 / 4088
- **哈希**：7d0395d4b6f8d690d70910fe2018a459582960e3
- **短哈希**：7d0395d4b
- **日期**：2025-03-13 14:03:01 -0700
- **作者**：Sanjay Marreddi
- **提交说明**：AWS: Update S3 async client configurations and docs for analytics-accelerator-s3 (#12503)
- **PR/Issue**：#12503

## 总体目的

本提交是提交 1844（集成 S3 analytics accelerator 库）的后续完善，主要解决了两个问题：(1) S3 异步客户端（`S3AsyncClient`/`S3AsyncClient.crtBuilder()`）创建时未应用用户配置的 region、credentials、endpoint 等配置，导致异步客户端使用默认配置而非用户的自定义配置；(2) 缺少 S3 CRT 客户端的 max-concurrency 配置，而 accelerator 库推荐使用更高的并发度以获得最佳性能；(3) 缺少 accelerator 库的配置文档。

在提交 1844 中，各 client factory 的 `s3Async()` 实现只是简单地 `S3AsyncClient.crtBuilder().build()` 或 `S3AsyncClient.builder().build()`，没有应用 `awsClientProperties` 中的 region/credentials 配置和 `s3FileIOProperties` 中的 endpoint 配置。这意味着当用户配置了自定义 region、凭证提供者或 S3 endpoint 时，异步客户端会忽略这些配置，导致连接失败或连接到错误的区域。

本提交通过在各 factory 的 `s3Async()` 中链式调用 `applyMutation` 应用配置，并在 `AwsClientProperties`/`S3FileIOProperties` 中新增面向 CRT builder 的配置方法来解决此问题。同时新增 `s3.crt.max-concurrency` 配置项（默认 500）和完整的 accelerator 配置文档。

## 如何达成设计目的

整体思路是"为 CRT builder 提供专用的配置方法 + 链式 applyMutation"。由于 AWS SDK 的 `S3AsyncClientBuilder` 和 `S3CrtAsyncClientBuilder` 是两个不同的 builder 接口（CRT builder 额外支持 maxConcurrency 等配置），需要为每种 builder 提供对应的配置方法。在 `AwsClientProps` 中新增 `applyClientRegionConfiguration(S3CrtAsyncClientBuilder)` 和 `applyClientCredentialConfigurations(S3CrtAsyncClientBuilder)` 方法（与现有的面向 `AwsClientBuilder` 的方法平行）。在 `S3FileIOProperties` 中新增 `applyEndpointConfigurations(S3AsyncClientBuilder)` 和 `applyEndpointConfigurations(S3CrtAsyncClientBuilder)` 两个重载方法，以及 `applyS3CrtConfigurations(S3CrtAsyncClientBuilder)` 方法设置 maxConcurrency。各 factory 的 `s3Async()` 通过链式 `applyMutation` 调用这些方法。

## 修改详情

### `aws/src/main/java/org/apache/iceberg/aws/AwsClientProperties.java` (修改)

**修改目的**：新增面向 S3 CRT builder 的 region 和 credentials 配置方法。

**工作逻辑**：

1. 新增 `applyClientRegionConfiguration(S3CrtAsyncClientBuilder builder)`：如果 `clientRegion != null`，调用 `builder.region(Region.of(clientRegion))`。与现有的面向 `AwsClientBuilder` 的方法逻辑相同，但泛型约束为 `S3CrtAsyncClientBuilder`。

2. 新增 `applyClientCredentialConfigurations(S3CrtAsyncClientBuilder builder)`：如果 `clientCredentialsProvider` 非空，调用 `builder.credentialsProvider(credentialsProvider(this.clientCredentialsProvider))`。与现有方法逻辑相同但面向 CRT builder。

3. 新增 `import software.amazon.awssdk.services.s3.S3CrtAsyncClientBuilder`。

### `aws/src/main/java/org/apache/iceberg/aws/s3/S3FileIOProperties.java` (修改)

**修改目的**：新增面向 async builder 的 endpoint 配置和 CRT max-concurrency 配置。

**工作逻辑**：

1. 新增常量 `S3_CRT_MAX_CONCURRENCY = "s3.crt.max-concurrency"` 和 `S3_CRT_MAX_CONCURRENCY_DEFAULT = 500`。注释说明 accelerator 库推荐使用更高并发度。

2. 新增字段 `s3CrtMaxConcurrency`，在构造函数中从 properties 解析（`PropertyUtil.propertyAsInt`）。

3. 新增 `s3CrtMaxConcurrency()` getter。

4. 新增 `applyEndpointConfigurations(S3AsyncClientBuilder builder)`：如果 endpoint 不为 null，调用 `builder.endpointOverride(URI.create(endpoint))`。

5. 新增 `applyEndpointConfigurations(S3CrtAsyncClientBuilder builder)`：同上，面向 CRT builder。

6. 新增 `applyS3CrtConfigurations(S3CrtAsyncClientBuilder builder)`：调用 `builder.maxConcurrency(s3CrtMaxConcurrency())` 设置最大并发度。

### `aws/src/main/java/org/apache/iceberg/aws/AssumeRoleAwsClientFactory.java` (修改)

**修改目的**：在 s3Async() 中应用完整的客户端配置。

**工作逻辑**：`s3Async()` 方法中，CRT 路径从 `S3AsyncClient.crtBuilder().applyMutation(this::applyAssumeRoleConfigurations).build()` 改为链式调用：`.applyMutation(this::applyAssumeRoleConfigurations).applyMutation(awsClientProperties::applyClientRegionConfiguration).applyMutation(awsClientProperties::applyClientCredentialConfigurations).applyMutation(s3FileIOProperties::applyEndpointConfigurations).applyMutation(s3FileIOProperties::applyS3CrtConfigurations).build()`。非 CRT 路径类似但不包含 `applyS3CrtConfigurations`。

### `aws/src/main/java/org/apache/iceberg/aws/AwsClientFactories.java` (修改)

**修改目的**：同上，在默认 factory 的 s3Async() 中应用配置。

**工作逻辑**：与 AssumeRoleAwsClientFactory 类似，在 `s3Async()` 中链式应用 region、credentials、endpoint、CRT 配置。

### `aws/src/main/java/org/apache/iceberg/aws/s3/DefaultS3FileIOAwsClientFactory.java` (修改)

**修改目的**：同上，在 S3FileIO 专用 factory 的 s3Async() 中应用配置。

**工作逻辑**：与上述两个 factory 一致。

### `aws/src/test/java/org/apache/iceberg/aws/s3/TestS3FileIOProperties.java` (修改)

**修改目的**：验证新增的配置方法。

**工作逻辑**：

1. `testS3FileIOPropertiesDefaultValues`：新增对 `s3CrtMaxConcurrency`、`isS3CRTEnabled`、`isS3AnalyticsAcceleratorEnabled` 默认值的断言。

2. `testS3FileIOProperties`：新增对上述属性在自定义 properties 下的断言。

3. `getTestProperties`：新增 `S3_CRT_MAX_CONCURRENCY=200`、`S3_CRT_ENABLED=false`、`S3_ANALYTICS_ACCELERATOR_ENABLED=true` 三个测试属性。

4. 新增 `testApplyS3CrtConfigurations` 测试：验证 `applyS3CrtConfigurations` 调用了 `builder.maxConcurrency`。

5. endpoint 配置测试中新增对 `S3AsyncClientBuilder` 和 `S3CrtAsyncClientBuilder` 的验证。

### `docs/docs/aws.md` (修改)

**修改目的**：新增 S3 Analytics Accelerator 的完整配置文档。

**工作逻辑**：新增 "S3 Analytics Accelerator" 章节，包含：
- 库的简介和启用方式（`s3.analytics-accelerator.enabled=true`）
- Spark SQL 启动示例命令
- Client Configuration 表格（`s3.crt.enabled` 默认 true、`s3.crt.max-concurrency` 默认 500）
- Logical IO Configuration 表格（12 个属性，包括 prefetch、metadata store、column access 等）
- Physical IO Configuration 表格（7 个属性，包括 block size、readahead、part size 等）
- Telemetry Configuration 表格（8 个属性，包括 level、logging、aggregations 等）
- Object Client Configuration 表格（useragentprefix）

## 小结

本提交完善了 S3 analytics accelerator 的客户端配置和文档，解决了提交 1844 中异步客户端未应用用户配置的问题。改动涉及 7 个文件，包括 3 个 factory 类、2 个 properties 类、1 个测试类和 1 个文档文件。回迁到 1.4.x 时需与提交 1844 一起回迁，确保异步客户端配置完整。`s3.crt.max-concurrency` 默认值 500 是 accelerator 库推荐的较高并发度，对于非 accelerator 场景的异步客户端也不会有负面影响（仅在 CRT builder 上设置）。
