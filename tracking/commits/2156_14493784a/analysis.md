# 提交 2156：AWS: Support multiple storage credential prefixes (#12799)

## 提交信息

- **序号**：2156 / 4088
- **哈希**：14493784a6d59d7d1dfee84fe619fe349518e537
- **短哈希**：14493784a
- **日期**：2025-05-22 18:11:45 +0200
- **作者**：Eduard Tudenhoefner
- **提交说明**：AWS: Support multiple storage credential prefixes (#12799)
- **PR/Issue**：#12799

## 总体目的

此前 `S3FileIO` 仅支持单一的 S3 存储凭证（storage credential），在 `storageCredentialConfig()` 方法中甚至明确断言"最多只能有一个 S3 凭证"。这限制了 Iceberg 在需要跨多个 S3 存储桶或不同 AWS 账户访问数据的场景下的使用能力。例如，当一个表的数据文件分布在不同 S3 路径前缀下，且每个前缀需要不同的凭证时，原有的单凭证设计就无法满足需求。该提交重构了 `S3FileIO`，引入 `PrefixedS3Client` 抽象，使其能够根据存储路径的前缀匹配，使用不同的 S3 客户端（拥有不同凭证和配置）来执行 I/O 操作，从而支持多存储凭证前缀的场景。

## 如何达成设计目的

- 新增 `PrefixedS3Client` 类，封装一个存储前缀及其对应的 S3 同步/异步客户端和 `S3FileIOProperties`，使用双重检查锁实现客户端的惰性初始化，并在关闭时清理 analytics accelerator 缓存。
- 重构 `S3FileIO`：将原来的单一 `client` 和 `asyncClient` 字段替换为 `Map<String, PrefixedS3Client> clientByPrefix`，以存储前缀为键维护多个客户端。
- 新增 `clientForStoragePath(String)` 方法，根据存储路径匹配最长前缀，返回对应的 `PrefixedS3Client`；默认使用根前缀 `"s3"` 对应的客户端。
- 在 `initialize` 方法中，为每个 `StorageCredential`（其前缀以 "s3" 开头）创建独立的 `PrefixedS3Client`，将凭证配置合并到属性中。
- 修改 `S3InputFile` 和 `S3OutputFile` 的工厂方法，新增接受 `PrefixedS3Client` 的重载，旧的工厂方法标记为 `@Deprecated`。
- 移除 `storageCredentialConfig()` 方法和 `shouldUseAsyncClient()` 方法，相关逻辑下沉到 `PrefixedS3Client` 和新的工厂方法中。

## 修改详情

### `aws/src/main/java/org/apache/iceberg/aws/s3/PrefixedS3Client.java` (新增, +121 lines)

**修改目的**：封装存储前缀与对应 S3 客户端的映射关系，实现多凭证支持的核心抽象。

**工作逻辑**：
- 持有 `storagePrefix`、`s3FileIOProperties`、以及 S3 同步/异步客户端的 `SerializableSupplier`。
- 构造函数接收前缀、属性 map 和可选的客户端 supplier。若 supplier 为空，则通过 `S3FileIOAwsClientFactories.initialize` 创建客户端；对同步客户端还支持预加载。
- `s3()` 和 `s3Async()` 方法使用双重检查锁（double-checked locking）实现客户端的惰性初始化。
- `close()` 方法关闭同步客户端，并在关闭异步客户端前调用 `AnalyticsAcceleratorUtil.cleanupCache` 清理缓存。

### `aws/src/main/java/org/apache/iceberg/aws/s3/S3FileIO.java` (修改, +210/-143 lines 中的主要部分)

**修改目的**：重构 S3FileIO 以支持基于路径前缀的多客户端选择。

**工作逻辑**：
- 新增 `ROOT_PREFIX = "s3"` 常量和 `clientByPrefix` 字段，替换原有的 `client`、`asyncClient`、`s3FileIOProperties` 字段。
- `newInputFile`、`newOutputFile` 等方法改为调用 `clientForStoragePath(path)` 获取对应客户端后委托给 `S3InputFile`/`S3OutputFile` 的新工厂方法。
- `deleteFile`、`deleteFiles`、`tagFileToDelete`、`deleteBatch`、`listPrefix`、`recoverFile` 等方法均改为从 `PrefixedS3Client` 获取 `S3FileIOProperties` 和 S3 客户端，而非使用单一的全局属性和客户端。
- `clientForStoragePath` 方法遍历所有注册的前缀，选择与路径匹配的最长前缀对应的客户端（最长前缀匹配策略）。
- `clientByPrefix()` 方法惰性初始化前缀到客户端的映射：先放入根前缀对应的默认客户端，再将每个存储凭证按前缀创建独立客户端。
- `close()` 方法改为遍历关闭所有 `PrefixedS3Client`。
- `initialize` 方法简化：不再在初始化时直接创建客户端，而是延迟到 `clientByPrefix()` 被首次调用时。
- 移除了 `storageCredentialConfig()` 方法（原来限制只能有一个 S3 凭证）和 `shouldUseAsyncClient()` 方法。

### `aws/src/main/java/org/apache/iceberg/aws/s3/S3FileIOProperties.java` (修改, +4 lines)

**修改目的**：暴露内部属性 map 供 `PrefixedS3Client` 使用。

**工作逻辑**：新增 `properties()` 方法返回 `allProperties`，使 `PrefixedS3Client` 能基于合并凭证后的属性创建 `S3FileIOProperties`。

### `aws/src/main/java/org/apache/iceberg/aws/s3/S3InputFile.java` (修改, +44 lines)

**修改目的**：新增接受 `PrefixedS3Client` 的工厂方法，废弃旧的工厂方法。

**工作逻辑**：新增包级 `fromLocation(String, PrefixedS3Client, MetricsContext)` 和 `fromLocation(String, long, PrefixedS3Client, MetricsContext)` 方法，内部根据 `PrefixedS3Client` 的属性判断是否启用 analytics accelerator 来决定是否传入异步客户端。旧的接受 `S3Client`/`S3AsyncClient`/`S3FileIOProperties` 参数的工厂方法标记为 `@Deprecated`（自 1.10.0 起，1.11.0 移除）。

### `aws/src/main/java/org/apache/iceberg/aws/s3/S3OutputFile.java` (修改, +24 lines)

**修改目的**：同 S3InputFile，新增接受 `PrefixedS3Client` 的工厂方法，废弃旧方法。

**工作逻辑**：新增包级 `fromLocation(String, PrefixedS3Client, MetricsContext)` 方法，逻辑与 S3InputFile 类似。旧的工厂方法标记为 `@Deprecated`。

### `aws/src/test/java/org/apache/iceberg/aws/s3/TestS3FileIO.java` (修改, +227/-部分 lines)

**修改目的**：更新和扩展测试以覆盖多凭证前缀场景。

### `aws/src/test/java/org/apache/iceberg/aws/s3/TestPrefixedS3Client.java` (新增, +60 lines)

**修改目的**：为新增的 `PrefixedS3Client` 类编写单元测试。

## 总结

这是一个重要的架构重构提交，使 `S3FileIO` 从单凭证模型升级到多凭证模型，支持基于存储路径前缀的差异化凭证和配置。通过引入 `PrefixedS3Client` 抽象和最长前缀匹配策略，Iceberg 现在可以同时访问需要不同 AWS 凭证的多个 S3 存储位置，显著扩展了多租户和跨账户场景下的适用性。
