# 提交 1844：AWS: Integrate S3 analytics accelerator library (#12299)

## 提交信息

- **序号**：1844 / 4088
- **哈希**：3dba6afb789a420373e44ac29ecdef866bd7ebee
- **短哈希**：3dba6afb7
- **日期**：2025-03-11 22:47:21 -0700
- **作者**：Sanjay Marreddi
- **提交说明**：AWS: Integrate S3 analytics accelerator library (#12299)
- **PR/Issue**：#12299

## 总体目的

本提交将 AWS 的 [Analytics Accelerator Library for Amazon S3](https://github.com/awslabs/analytics-accelerator-s3) 集成到 Iceberg 的 `S3FileIO` 中。这个开源库通过预取（prefetching）、元数据缓存、列级访问优化等技术加速从 S3 读取数据的速度，降低数据处理延迟和计算成本，特别适用于数据分析工作负载。

在此之前，`S3FileIO` 只使用同步的 `S3Client` 进行 S3 数据读写。集成 analytics accelerator 后，当启用该功能时（通过 `s3.analytics-accelerator.enabled=true`），`S3FileIO` 会同时创建一个 `S3AsyncClient`（异步客户端），输入流的读取通过 accelerator 库的 `S3SeekableInputStreamFactory` 进行，该工厂内部管理预取缓存和对象元数据，从而加速顺序和随机读取。

集成涉及多个层面：(1) `S3FileIO` 新增 `S3AsyncClient` 字段和懒加载逻辑，在 `newInputFile`/`newOutputFile` 时根据是否启用 accelerator 选择传入 async client；(2) 新增 `AnalyticsAcceleratorUtil` 工具类和 `AnalyticsAcceleratorInputStreamWrapper` 包装类，将 accelerator 的 `S3SeekableInputStream` 适配为 Iceberg 的 `SeekableInputStream`；(3) `AwsClientFactory`/`S3FileIOAwsClientFactory` 接口新增 `s3Async()` 方法，所有实现类（`AssumeRoleAwsClientFactory`、`AwsClientFactories`、`DefaultS3FileIOAwsClientFactory`）同步实现；(4) `S3FileIOProperties` 新增 accelerator 相关配置项；(5) 构建脚本新增 `analyticsaccelerator-s3` 依赖。

## 如何达成设计目的

整体设计思路是"可选启用 + 异步客户端 + 流工厂缓存"。accelerator 功能默认关闭（`S3_ANALYTICS_ACCELERATOR_ENABLED_DEFAULT = false`），启用后 `S3FileIO.shouldUseAsyncClient()` 返回 true，`newInputFile`/`newOutputFile` 走带有 async client 的路径。`AnalyticsAcceleratorUtil` 使用 Caffeine 缓存（以 `Pair<S3AsyncClient, S3FileIOProperties>` 为 key）缓存 `S3SeekableInputStreamFactory` 实例，避免为每次文件读取都创建新的工厂（工厂内部维护预取缓存和元数据存储，创建成本高）。`S3FileIO.close()` 时调用 `AnalyticsAcceleratorUtil.cleanupCache` 清理缓存。async client 的创建委托给 `AwsClientFactory.s3Async()`，默认使用 S3 CRT 客户端（`S3_CRT_ENABLED_DEFAULT = true`）。

## 修改详情

### `aws/src/main/java/org/apache/iceberg/aws/s3/S3FileIO.java` (修改, 75 lines)

**修改目的**：集成 async client 和 accelerator 路径。

**工作逻辑**：

1. 新增 `SerializableSupplier<S3AsyncClient> s3Async` 字段和 `transient volatile S3AsyncClient asyncClient` 字段。

2. 新增构造函数 `S3FileIO(SerializableSupplier<S3Client> s3, SerializableSupplier<S3AsyncClient> s3Async)` 和 `S3FileIO(SerializableSupplier<S3Client> s3, SerializableSupplier<S3AsyncClient> s3Async, S3FileIOProperties s3FileIOProperties)`。原有两个构造函数委托到新构造函数（s3Async 传 null）。

3. `newInputFile(path)`/`newInputFile(path, length)`/`newOutputFile(path)`：在原有逻辑前增加 `if (shouldUseAsyncClient())` 分支，调用带有 asyncClient 参数的 `S3InputFile.fromLocation`/`S3OutputFile.fromLocation` 重载。

4. 新增 `asyncClient()` 方法：双重检查锁懒加载 async client。

5. 新增 `shouldUseAsyncClient()` 私有方法：返回 `s3FileIOProperties.isS3AnalyticsAcceleratorEnabled()`。

6. `initialize(Map props)` 方法：如果 s3Async 为 null，从 client factory 初始化（检查 factory 是否实现 `S3FileIOAwsClientFactory` 或 `AwsClientFactory`，取 `s3Async` 方法引用）。

7. `close()` 方法：新增 async client 的关闭逻辑，先调用 `AnalyticsAcceleratorUtil.cleanupCache(asyncClient, s3FileIOProperties)` 清理缓存，再 `asyncClient.close()`。

### `aws/src/main/java/org/apache/iceberg/aws/s3/AnalyticsAcceleratorUtil.java` (新增, 90 lines)

**修改目的**：封装 accelerator 库的流创建逻辑，使用缓存管理流工厂。

**工作逻辑**：

1. 静态 `STREAM_FACTORY_CACHE`：Caffeine 缓存，以 `Pair<S3AsyncClient, S3FileIOProperties>` 为 key，`S3SeekableInputStreamFactory` 为 value，最大 100 个条目。

2. `newStream(S3InputFile inputFile)`：从 inputFile 取得 S3URI、对象元数据（HeadObjectResponse），构造 `OpenStreamInformation`；从缓存获取或创建 `S3SeekableInputStreamFactory`；调用 `factory.createStream(uri, openStreamInfo)` 创建 `S3SeekableInputStream`，包装为 `AnalyticsAcceleratorInputStreamWrapper` 返回。

3. `createNewFactory(Pair<S3AsyncClient, S3FileIOProperties> cacheKey)`：从 `S3FileIOProperties` 取得 accelerator 配置属性，构造 `ConnectorConfiguration`→`S3SeekableInputStreamConfiguration`+`ObjectClientConfiguration`→`S3SdkObjectClient`（包装 S3AsyncClient）→`S3SeekableInputStreamFactory`。

4. `cleanupCache(S3AsyncClient, S3FileIOProperties)`：使缓存中对应的条目失效。

### `aws/src/main/java/org/apache/iceberg/aws/s3/AnalyticsAcceleratorInputStreamWrapper.java` (新增, 63 lines)

**修改目的**：将 accelerator 的 `S3SeekableInputStream` 适配为 Iceberg 的 `SeekableInputStream`。

**工作逻辑**：继承 `SeekableInputStream`，持有 `S3SeekableInputStream delegate`，委托实现 `read()`/`read(byte[])`/`read(byte[], int, int)`/`seek(long)`/`getPos()`/`close()` 方法。

### `aws/src/main/java/org/apache/iceberg/aws/s3/S3FileIOProperties.java` (修改, 51 lines)

**修改目的**：新增 accelerator 和 CRT 相关配置项。

**工作逻辑**：新增常量 `S3_ANALYTICS_ACCELERATOR_ENABLED`（默认 false）、`S3_ANALYTICS_ACCELERATOR_PROPERTIES_PREFIX`（"s3.analytics-accelerator."）、`S3_CRT_ENABLED`（默认 true）。新增字段 `isS3AnalyticsAcceleratorEnabled`/`s3AnalyticsacceleratorProperties`/`isS3CRTEnabled`，在构造函数中从 properties 解析。新增对应的 getter 方法。

### `aws/src/main/java/org/apache/iceberg/aws/s3/BaseS3File.java` (修改, 13 lines)

**修改目的**：为 S3InputFile/S3OutputFile 的基类增加 async client 支持。

**工作逻辑**：新增 `S3AsyncClient asyncClient` 字段，构造函数增加该参数，新增 `asyncClient()` getter。

### `aws/src/main/java/org/apache/iceberg/aws/s3/S3InputFile.java` (修改, 40 lines) 和 `S3OutputFile.java` (修改, 26 lines)

**修改目的**：新增带 async client 的工厂方法，在读取输入流时使用 accelerator。

**工作逻辑**：`S3InputFile`/`S3OutputFile` 新增 `fromLocation` 重载方法接受 asyncClient 参数。`S3InputFile` 的流读取逻辑中，如果 asyncClient 不为 null 且 accelerator 启用，调用 `AnalyticsAcceleratorUtil.newStream(this)` 获取输入流。

### `aws/src/main/java/org/apache/iceberg/aws/AwsClientFactory.java` (修改, 8 lines) 和 `S3FileIOAwsClientFactory.java` (修改, 8 lines)

**修改目的**：在客户端工厂接口中新增 `s3Async()` 方法。

**工作逻辑**：两个接口新增 `S3AsyncClient s3Async()` 方法声明。

### `aws/src/main/java/org/apache/iceberg/aws/AssumeRoleAwsClientFactory.java` (修改, 55 lines)、`AwsClientFactories.java` (修改, 9 lines)、`DefaultS3FileIOAwsClientFactory.java` (修改, 9 lines)

**修改目的**：实现 `s3Async()` 方法。

**工作逻辑**：各工厂类实现 `s3Async()`，根据 `s3FileIOProperties.isS3CRTEnabled()` 选择使用 `S3AsyncClient.crtBuilder()` 或 `S3AsyncClient.builder()` 构建异步客户端。

### `aws/src/main/java/org/apache/iceberg/aws/s3/StaticClientFactory.java` (修改, 6 lines) 和 `aws/src/test/java/org/apache/iceberg/aws/TestAwsClientFactories.java` (修改, 6 lines)

**修改目的**：适配新增的 `s3Async()` 接口方法。

**工作逻辑**：`StaticClientFactory` 实现 `s3Async()` 返回预设的 client 或抛 `UnsupportedOperationException`。测试类更新以覆盖新方法。

### `build.gradle` (修改, 2 lines) 和 `gradle/libs.versions.toml` (修改, 2 lines) 和 `kafka-connect/build.gradle` (修改, 1 line)

**修改目的**：新增 analytics-accelerator-s3 依赖声明。

**工作逻辑**：`libs.versions.toml` 新增 `analyticsaccelerator = "1.0.0"` 版本和 `analyticsaccelerator-s3` 库定义。`build.gradle` 在 iceberg-aws 项目的 dependencies 中新增 `compileOnly(libs.analyticsaccelerator.s3)`。kafka-connect 构建文件也新增对应依赖。

### `aws/src/integration/java/org/apache/iceberg/aws/s3/TestS3FileIOIntegration.java` (修改, 56 lines)

**修改目的**：新增集成测试验证 accelerator 功能。

**工作逻辑**：新增集成测试用例，验证启用 accelerator 后文件读写正确。

## 小结

本提交将 AWS S3 Analytics Accelerator 库集成到 Iceberg S3FileIO 中，通过可选启用的异步客户端和流工厂缓存机制加速 S3 数据读取。改动涉及 18 个文件（501 行新增、19 行删除），是 AWS 模块的重要功能增强。回迁到 1.4.x 时需注意：(1) 需要新增 `analyticsaccelerator-s3` 依赖（1.0.0 版本）；(2) `AwsClientFactory`/`S3FileIOAwsClientFactory` 接口新增 `s3Async()` 方法是 ABI 变更，所有自定义实现类需同步实现；(3) accelerator 功能默认关闭，不影响现有行为。
