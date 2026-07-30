# 提交 1206：AWS: Add configuration and set defaults for S3 retry behaviour (#11052)

## 提交信息

- **序号**：1206 / 4088
- **哈希**：09c737656d316ab6172e0d5ee6920869237e6fd1
- **短哈希**：09c737656
- **日期**：2024-10-01（Tue Oct 1 15:28:52 2024 -0700）
- **作者**：Ozan Okumusoglu <ookumuso@amazon.com>
- **提交说明**：AWS: Add configuration and set defaults for S3 retry behaviour (#11052)
- **PR/Issue**：#11052
- **共同作者**：Drew Schleit <aschleit@amazon.com>

## 总体目的

Iceberg 的 `S3FileIO` 在访问 S3 时使用 AWS SDK v2 的默认 `RetryPolicy`。但默认重试策略在大规模、高吞吐的 S3 工作负载下存在若干不足，导致工作负载在 S3 限流（throttling，HTTP 503）期间过早失败：

1. **默认重试次数不足**：AWS SDK v2 的 `RetryMode` 默认已切换为 `ADAPTIVE`/`STANDARD`，其重试次数和令牌桶行为对"持续限流等待 S3 自动扩容"的场景不够友好；旧的 `LEGACY` 模式允许限流异常用满所有配置的重试次数，更适合"持续退避重试直到 S3 扩容跟上"的场景，但 Iceberg 此前未显式设置 `RetryMode`。
2. **退避策略不可配**：默认退避策略的 base delay 和 max backoff 无法从 Iceberg 层调整，用户无法根据工作负载调优。
3. **已知 SDK 缺陷未规避**：
   - aws-sdk-java-v2 issue #5442：当解析 S3 错误 XML 响应时发生 socket 异常会抛 `XMLStreamException`，而该异常不在 SDK 默认可重试异常列表中，导致本应重试的请求直接失败。
   - aws-sdk-java-v2 issue #5414：503 响应会消耗令牌桶中的重试令牌，大量 503 会迅速耗尽令牌桶，导致后续非限流请求也无法重试，工作负载过早失败。
4. **缺少统一配置入口**：用户无法通过 Iceberg 的 catalog 属性方便地调整 S3 重试行为。

本提交通过在 `S3FileIOProperties` 中新增三个重试配置项（重试次数、最小等待、最大等待），并提供 `applyRetryConfigurations(S3ClientBuilder)` 方法，把上述定制化的 `RetryPolicy`（`LEGACY` 模式 + `EqualJitterBackoffStrategy` + `XMLStreamException` 可重试 + 503 不消耗令牌）应用到所有 S3 客户端构建路径，让用户可调参，并在文档中给出推荐值（高吞吐场景建议 `num-retries=32`）。

## 如何达成设计目的

整体设计分四步：

1. **配置项定义**：在 `S3FileIOProperties` 中新增三个公共静态常量（属性 key + 默认值）和对应的可变字段、getter/setter：
   - `s3.retry.num-retries`（默认 5）
   - `s3.retry.min-wait-ms`（默认 2000ms = 2s）
   - `s3.retry.max-wait-ms`（默认 20000ms = 20s）
   并在构造函数和 `Map` 构造函数中用 `PropertyUtil` 解析这些属性。
2. **重试策略组装**：新增 `applyRetryConfigurations(T extends S3ClientBuilder)` 方法，通过 `builder.overrideConfiguration(config -> config.retryPolicy(...))` 注入定制 `RetryPolicy`：
   - `RetryMode.LEGACY`：让限流异常能用满所有重试次数；
   - `numRetries(s3RetryNumRetries)`：用户可配的重试次数；
   - `throttlingBackoffStrategy(EqualJitterBackoffStrategy)`：等抖动退避，base/max 由配置决定；
   - `retryCondition(OrRetryCondition(defaultRetryCondition, RetryOnExceptionsCondition(XMLStreamException)))`：在默认可重试条件基础上追加 `XMLStreamException`，规避 SDK #5442；
   - `retryCapacityCondition(TokenBucketRetryCondition)`：令牌桶大小 500（SDK 默认），但把限流异常和 503 的令牌消耗成本设为 0，规避 SDK #5414。
3. **应用到所有 S3 客户端工厂**：在 `AwsClientFactories`、`AssumeRoleAwsClientFactory`、`LakeFormationAwsClientFactory`、`DefaultS3FileIOAwsClientFactory` 四个工厂的 S3 client 构建链上各加一行 `.applyMutation(s3FileIOProperties::applyRetryConfigurations)`，确保无论用户用哪种工厂都会生效。
4. **测试与文档**：新增 `testApplyRetryConfiguration` 验证配置能正确注入 `RetryPolicy.numRetries`；在 `docs/docs/aws.md` 新增 "S3 Retries" 章节说明配置项与推荐值。

## 修改详情

### `aws/src/main/java/org/apache/iceberg/aws/s3/S3FileIOProperties.java`（修改，+124 行）

**修改目的**：定义重试配置项、解析逻辑、getter/setter，以及核心的 `applyRetryConfigurations` 方法。

**工作逻辑**：

- 新增 import：`Duration`、`XMLStreamException`，以及 `software.amazon.awssdk.core.retry.*` 下的 `RetryMode`、`RetryPolicy`、`EqualJitterBackoffStrategy`、`OrRetryCondition`、`RetryCondition`、`RetryOnExceptionsCondition`、`TokenBucketRetryCondition`，外加 `SdkServiceException`。
- 新增 3 组属性常量与默认值：
  - `S3_RETRY_NUM_RETRIES = "s3.retry.num-retries"`，默认 `S3_RETRY_NUM_RETRIES_DEFAULT = 5`；
  - `S3_RETRY_MIN_WAIT_MS = "s3.retry.min-wait-ms"`，默认 `2000`；
  - `S3_RETRY_MAX_WAIT_MS = "s3.retry.max-wait-ms"`，默认 `20000`。
- 新增 3 个可变字段 `s3RetryNumRetries`、`s3RetryMinWaitMs`、`s3RetryMaxWaitMs`，在无参构造和 `Map` 构造中分别赋默认值 / 用 `PropertyUtil.propertyAsInt/AsLong` 解析。
- 新增 getter/setter：`s3RetryNumRetries()`/`setS3RetryNumRetries(int)` 等，以及便捷方法 `s3RetryTotalWaitMs()` 返回 `numRetries * maxWaitMs`（供调用方估算总等待上限）。
- 核心方法 `applyRetryConfigurations(T extends S3ClientBuilder)`：
  ```java
  builder.overrideConfiguration(config -> config.retryPolicy(
      RetryPolicy.builder(RetryMode.LEGACY)
          .numRetries(s3RetryNumRetries)
          .throttlingBackoffStrategy(EqualJitterBackoffStrategy.builder()
              .baseDelay(Duration.ofMillis(s3RetryMinWaitMs))
              .maxBackoffTime(Duration.ofMillis(s3RetryMaxWaitMs))
              .build())
          .retryCondition(OrRetryCondition.create(
              RetryCondition.defaultRetryCondition(),
              RetryOnExceptionsCondition.create(XMLStreamException.class)))
          .retryCapacityCondition(TokenBucketRetryCondition.builder()
              .tokenBucketSize(500)
              .exceptionCostFunction(e -> {
                if (e instanceof SdkServiceException) {
                  SdkServiceException ex = (SdkServiceException) e;
                  if (ex.isThrottlingException() || ex.statusCode() == 503) {
                    return 0;  // 限流/503 不消耗令牌
                  }
                }
                return 5;  // SDK 默认非限流消耗
              })
              .build())
          .build())));
  ```
  关键设计点：
  - 选 `LEGACY` 而非 `STANDARD`/`ADAPTIVE`：LEGACY 允许限流异常用满全部重试次数，适合"持续退避等 S3 扩容"场景；
  - `EqualJitterBackoffStrategy`：等抖动退避，避免大量客户端同步重试造成"惊群"；
  - `XMLStreamException` 追加为可重试：规避 SDK #5442（解析错误 XML 时 socket 异常）；
  - 限流/503 令牌消耗为 0：规避 SDK #5414（503 耗尽令牌桶），让限流期间非限流请求仍能重试。

### `aws/src/main/java/org/apache/iceberg/aws/AwsClientFactories.java`（修改，+1 行）

**修改目的**：在默认 `AwsClientFactory` 的 S3 client 构建链上应用重试配置。

**工作逻辑**：在 `S3Client.builder()` 链上加 `.applyMutation(s3FileIOProperties::applyRetryConfigurations)`，位于 `applyUserAgentConfigurations` 之后、`.build()` 之前。

### `aws/src/main/java/org/apache/iceberg/aws/AssumeRoleAwsClientFactory.java`（修改，+1 行）

**修改目的**：在 AssumeRole 工厂的 S3 client 构建链上应用重试配置。

**工作逻辑**：同上，加在 `applySignerConfiguration` 之后。

### `aws/src/main/java/org/apache/iceberg/aws/lakeformation/LakeFormationAwsClientFactory.java`（修改，+1 行）

**修改目的**：在 Lake Formation 工厂的 S3 client 构建链上应用重试配置。

**工作逻辑**：同上，加在 `applyServiceConfigurations` 之后、`credentialsProvider` 之前。

### `aws/src/main/java/org/apache/iceberg/aws/s3/DefaultS3FileIOAwsClientFactory.java`（修改，+1 行）

**修改目的**：在 `S3FileIO` 默认使用的 `DefaultS3FileIOAwsClientFactory` 的 S3 client 构建链上应用重试配置。

**工作逻辑**：同上，加在 `applyUserAgentConfigurations` 之后。

### `aws/src/test/java/org/apache/iceberg/aws/s3/TestS3FileIOProperties.java`（修改，+15 行）

**修改目的**：验证重试配置能正确注入到 `S3ClientBuilder` 的 `RetryPolicy`。

**工作逻辑**：`testApplyRetryConfiguration` 构造 `properties` 设置 `S3_RETRY_NUM_RETRIES=999`，创建 `S3FileIOProperties`，对一个真实的 `S3Client.builder()` 调用 `applyRetryConfigurations`，然后从 `builder.overrideConfiguration().retryPolicy()` 取出 `RetryPolicy` 断言 `numRetries() == 999`。

### `docs/docs/aws.md`（修改，+14 行）

**修改目的**：文档化新增的 S3 重试配置项。

**工作逻辑**：新增 "S3 Retries" 章节，说明限流场景应持续退避重试让 S3 自动扩容，推荐高吞吐工作负载 `num-retries=32`，并给出属性表格（属性名、默认值、说明）。

## 小结

- **成效**：为 `S3FileIO` 的所有 S3 客户端构建路径（默认工厂、AssumeRole、Lake Formation、DefaultS3FileIOAwsClientFactory）统一注入了可配置的定制 `RetryPolicy`，规避了两个已知 AWS SDK v2 缺陷（`XMLStreamException` 不可重试、503 消耗令牌），并让用户可通过 `s3.retry.num-retries`/`min-wait-ms`/`max-wait-ms` 三个属性调优重试行为。默认值（5 次、2-20s 退避）对一般工作负载合理，高吞吐限流场景可调到 32 次。
- **影响范围**：仅 `aws` 模块和文档。所有使用 `S3FileIO` 的用户在升级后会自动获得新的重试策略（默认值），行为变化是：限流/503 不再消耗令牌桶令牌、`XMLStreamException` 可重试、退避策略改为 `EqualJitterBackoffStrategy`、`RetryMode` 显式设为 `LEGACY`。对非限流场景几乎无副作用，但 `LEGACY` 模式与 SDK 默认的 `STANDARD`/`ADAPTIVE` 行为有差异，需注意。
- **回迁到 1.4.x 的注意事项**：
  1. 此提交对生产稳定性有直接价值（限流场景），值得回迁。回迁时需确认 1.4.x 的 `S3FileIOProperties` 结构与 main 一致（`applyMutation` 链式风格、`PropertyUtil` 工具类可用）。
  2. 四个工厂类的 `applyMutation` 链需逐个确认路径（`LakeFormationAwsClientFactory` 在 `aws/.../lakeformation/` 子包）。
  3. AWS SDK v2 版本需支持 `RetryPolicy.builder(RetryMode)`、`throttlingBackoffStrategy`、`retryCapacityCondition`、`exceptionCostFunction` 等 API（这些在 2.x 较早版本就存在，1.4.x 用的 SDK 版本应满足）。
  4. 默认 `RetryMode` 从 SDK 默认切换到显式 `LEGACY` 是行为变化，回迁后应在 release note 中提示用户：若工作负载依赖 `ADAPTIVE`/`STANDARD` 模式的令牌桶自适应行为，需评估是否需要额外提供切换 `RetryMode` 的配置（本提交未提供该开关）。
  5. 文档 `docs/docs/aws.md` 同步回迁即可。
