# 提交 2293：AWS: Refactor S3FileIOProperties to use common builder interface (#13183)

## 提交信息

- **序号**：2293 / 4088
- **哈希**：9d8751d50fcff3eeba0749ec63d4e501ae8674fc
- **短哈希**：9d8751d50
- **日期**：2025-06-30 11:28:30 -0500
- **作者**：Devin Smith
- **提交说明**：AWS: Refactor S3FileIOProperties to use common builder interface (#13183)
- **PR/Issue**：#13183

## 总体目的

本提交对 `S3FileIOProperties` 进行重构，将 S3 同步客户端和异步客户端的配置方法统一到公共的构建器接口上。在重构之前，`applyEndpointConfigurations` 方法有两个重载版本：一个接受 `S3ClientBuilder`（同步客户端），另一个接受 `S3AsyncClientBuilder`（异步客户端）。这种重复导致代码冗余，且难以维护。

AWS SDK v2 提供了 `S3BaseClientBuilder` 接口，它是 `S3ClientBuilder` 和 `S3AsyncClientBuilder` 的共同父接口。通过使用这个公共接口，可以用一个方法替代两个重载方法，简化代码并提高可维护性。

## 如何达成设计目的

核心设计思路是利用 AWS SDK 的类型继承层次结构：

1. `S3BaseClientBuilder` 是 S3 同步和异步客户端构建器的公共父接口
2. 将两个独立的 `applyEndpointConfigurations` 重载方法合并为一个接受 `S3BaseClientBuilder<T, ?>` 泛型参数的方法
3. 更新相关文档引用，使其指向新的公共接口

这种重构在保持功能不变的前提下减少了代码重复，并使得同步和异步客户端的配置行为保持一致。

## 修改详情

### `aws/src/main/java/org/apache/iceberg/aws/s3/S3FileIOProperties.java` (+6/-19 lines)

**修改目的**：将两个 `applyEndpointConfigurations` 重载方法合并为一个使用公共构建器接口的方法。

**工作逻辑**：原先有两个方法：
- `public <T extends S3ClientBuilder> void applyEndpointConfigurations(T builder)` - 用于同步客户端
- `public <T extends S3AsyncClientBuilder> void applyEndpointConfigurations(T builder)` - 用于异步客户端

重构后合并为一个方法：
- `public <T extends S3BaseClientBuilder<T, ?>> void applyEndpointConfigurations(T builder)` - 同时支持同步和异步客户端

两个方法的实现逻辑完全相同（都是检查 endpoint 是否非空，然后调用 `builder.endpointOverride(URI.create(endpoint))`），因此合并是安全的。同时移除了不再需要的 `S3AsyncClientBuilder` 导入。

### `aws/src/main/java/org/apache/iceberg/aws/AwsClientFactories.java` (+4/-4 lines)

**修改目的**：更新文档引用和方法签名，使其指向新的公共构建器接口。

**工作逻辑**：在 `AwsClientFactories` 中，两处 `@deprecated` 注释的 JavaDoc 引用从 `S3ClientBuilder` 更新为 `S3BaseClientBuilder`，反映了方法签名的变化。同时更新了导入语句，将 `S3ClientBuilder` 的导入替换为 `S3BaseClientBuilder`。

## 总结

这是一个代码质量改进提交，通过利用 AWS SDK 的类型层次结构消除了 `S3FileIOProperties` 中的方法重复。重构使代码更简洁、更易维护，同时保持了完全的功能兼容性。这种简化对于后续扩展 S3 客户端配置功能时减少重复代码具有重要意义。
