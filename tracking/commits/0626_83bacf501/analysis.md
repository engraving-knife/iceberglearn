# 提交 0626：Add Iceberg version to UserAgent in S3 requests

## 提交信息

- **序号**：0626 / 4088
- **哈希**：83bacf501e116c0923eb6e77bff4500cd3d8042f
- **短哈希**：83bacf501
- **日期**：2024-03-25 19:01:42 +0000
- **作者**：Csenger Geza <csengergeza@gmail.com>（合作者：Geza Csenger <gccsenge@amazon.com>）
- **提交说明**：Add Iceberg version to UserAgent in S3 requests (#9963)
- **PR/Issue**：#9963

## 总体目的

本提交的目的在于让 Iceberg 在向 S3 发起请求时，通过 `User-Agent` HTTP 头部携带 Iceberg 自身的版本信息。这样做的好处是：

1. **运维可观测性**：开发者和运维人员可以通过 S3 Access Logs（其中包含 user agent 字段）监控集群中实际部署并使用的 Iceberg 版本。
2. **版本追踪**：在大规模集群环境中，能够快速识别哪些作业使用了过旧或有问题的 Iceberg 版本，便于故障排查和升级规划。
3. **AWS 侧统计**：AWS 服务端可以基于 User-Agent 前缀统计和区分来自 Iceberg 的请求流量。

之前 Iceberg 的 S3FileIO 在构建 S3 客户端时，并未显式设置 User-Agent 前缀，导致 S3 端只能看到 AWS SDK 默认的 User-Agent 字符串，无法区分请求来自哪个 Iceberg 版本。

## 如何达成设计目的

设计思路是利用 AWS SDK 提供的 `overrideConfiguration` 机制，将 Iceberg 的版本信息以 `s3fileio/<iceberg-version>` 的格式注入到 S3 客户端的 `USER_AGENT_PREFIX` 高级选项中。具体实现路径：

1. 在 `S3FileIOProperties` 中新增一个静态常量 `S3_FILE_IO_USER_AGENT`，通过 `EnvironmentContext.get()` 获取当前 Iceberg 运行时版本，拼接成 `s3fileio/<version>` 字符串。
2. 新增 `applyUserAgentConfigurations` 方法，将上述字符串设置到 S3 客户端构建器的 `SdkAdvancedClientOption.USER_AGENT_PREFIX` 选项。
3. 在所有构建 S3 客户端的地方（`AwsClientFactories` 与 `DefaultS3FileIOAwsClientFactory`）调用该方法，确保所有 S3 客户端构建路径都加上版本标识。

`EnvironmentContext.get()` 是 Iceberg 核心模块提供的运行时版本上下文，会从 `iceberg-build.properties` 中读取构建时写入的版本号，因此可以动态反映当前 jar 包的真实版本，无需硬编码。

## 修改详情

### `aws/src/main/java/org/apache/iceberg/aws/AwsClientFactories.java`

**修改目的**：在 `AwsClientFactories` 构建 S3 客户端时，也应用 User-Agent 配置，确保通过该工厂创建的 S3 客户端带上 Iceberg 版本标识。

**工作逻辑**：在构建 `S3Client` 的 builder 链中追加一行 `.applyMutation(s3FileIOProperties::applyUserAgentConfigurations)`。这一行位于其他类似的配置方法（`applySignerConfiguration`、`applyS3AccessGrantsConfigurations`）之后，保持与既有配置调用风格一致。`applyMutation` 是 AWS SDK Builder 上的方法，接收一个 `Consumer<T>` 对 builder 做就地修改。

### `aws/src/main/java/org/apache/iceberg/aws/s3/DefaultS3FileIOAwsClientFactory.java`

**修改目的**：在 `DefaultS3FileIOAwsClientFactory`（默认 S3FileIO 客户端工厂）构建 S3 客户端时，也应用 User-Agent 配置。

**工作逻辑**：与 `AwsClientFactories` 中的修改完全一致，在 `s3Client()` 方法的 builder 链中追加 `.applyMutation(s3FileIOProperties::applyUserAgentConfigurations)`。该工厂是 `S3FileIOAwsClientFactory` 的默认实现，覆盖最常见的使用场景。

### `aws/src/main/java/org/apache/iceberg/aws/s3/S3FileIOProperties.java`

**修改目的**：核心实现 —— 定义 User-Agent 前缀常量并提供应用配置的方法。

**工作逻辑**：
- 引入 `org.apache.iceberg.EnvironmentContext`，用于获取运行时版本。
- 新增静态常量：
  ```java
  private static final String S3_FILE_IO_USER_AGENT = "s3fileio/" + EnvironmentContext.get();
  ```
  该常量在类加载时计算一次，值为类似 `s3fileio/1.4.0` 的字符串。采用 `s3fileio/` 前缀便于在 S3 日志中区分 Iceberg 的 S3FileIO 与其他 AWS SDK 客户端的流量。
- 新增方法：
  ```java
  public <T extends S3ClientBuilder> void applyUserAgentConfigurations(T builder) {
    builder.overrideConfiguration(
        c -> c.putAdvancedOption(SdkAdvancedClientOption.USER_AGENT_PREFIX, S3_FILE_IO_USER_AGENT));
  }
  ```
  该方法接收一个 `S3ClientBuilder`，通过 `overrideConfiguration` 接收一个 `Consumer<ClientOverrideConfiguration.Builder>`，在该 consumer 中调用 `putAdvancedOption` 把 `USER_AGENT_PREFIX` 设置为前面定义的常量。`USER_AGENT_PREFIX` 会被 AWS SDK 拼接到最终 User-Agent 字符串的最前面，从而在 S3 服务端日志中显眼地展示 Iceberg 标识。

### `aws/src/test/java/org/apache/iceberg/aws/s3/TestS3FileIOProperties.java`

**修改目的**：新增单元测试，验证 `applyUserAgentConfigurations` 方法确实会调用 builder 的 `overrideConfiguration`。

**工作逻辑**：使用 Mockito 模拟一个 `S3ClientBuilder`，调用 `applyUserAgentConfigurations` 后通过 `Mockito.verify` 断言 `overrideConfiguration` 被调用了一次（参数为任意 `Consumer`）。这是一个行为验证型测试，确认配置被正确应用到 builder 上。

## 小结

**成效**：此提交以极小的改动量（26 行新增、0 行删除）实现了 S3 请求的 Iceberg 版本可观测性，对运行时性能无影响，仅为 HTTP 头部添加一个前缀字符串。

**影响范围**：仅影响 `aws` 模块中通过 `AwsClientFactories` 和 `DefaultS3FileIOAwsClientFactory` 创建 S3 客户端的路径，所有使用 S3FileIO 的 Iceberg 作业（读写 S3 上的数据文件）都会带上新的 User-Agent。

**回迁到 1.4.x 的注意事项**：
- 该改动是纯增量的，不涉及既有行为变更，回迁风险极低。
- 需要确认 1.4.x 分支上 `S3FileIOProperties` 中已存在 `applySignerConfiguration`、`applyS3AccessGrantsConfigurations` 等方法（即 builder 链的上下文一致），否则插入位置需要调整。
- `EnvironmentContext.get()` 是 Iceberg 核心模块的稳定 API，1.4.x 上同样可用。
- 测试依赖 Mockito，1.4.x 上已有相同的测试基础设施，可直接回迁。
