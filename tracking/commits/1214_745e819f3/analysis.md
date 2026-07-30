# 提交 1214：AWS: Make sure overridden configurations are applied (#11274)

## 提交信息

- **序号**：1214 / 4088
- **哈希**：745e819f372fe9ba6bebf2f7edaa27197cd0dd0b
- **短哈希**：745e819f3
- **日期**：2024-10-07（Mon Oct 7 14:48:53 2024 +0800）
- **作者**：hsiang-c <137842490+hsiang-c@users.noreply.github.com>
- **提交说明**：AWS: Make sure overridden configurations are applied (#11274)
- **PR/Issue**：#11274

## 总体目的

`S3FileIOProperties.applyRetryConfigurations(S3ClientBuilder)` 方法负责把 Iceberg 配置中的 S3 重试策略（throttled exception 的指数退避重试）应用到 AWS SDK 的 `S3ClientBuilder` 上。原实现使用 `builder.overrideConfiguration(config -> config.retryPolicy(...))` 形式，即传入一个 `Consumer<ClientOverrideConfiguration.Builder>` 给 `overrideConfiguration`。

问题在于 AWS SDK 的 `overrideConfiguration(Consumer)` 重载会**用一个新的 `ClientOverrideConfiguration` 完全替换** builder 上既有的 override configuration。当调用方此前已通过 `applySignerConfiguration`、`applyUserAgentConfigurations` 等方法设置了其他 override 项（如 signer、user agent），随后调用 `applyRetryConfigurations` 时，这些已设置的配置会被覆盖丢失。

本提交修复该问题：在应用重试策略前，先把 builder 上既有的 `ClientOverrideConfiguration` 取出并转换为可变 builder（若不存在则新建空 builder），在其基础上追加 `retryPolicy`，再整体回设给 `S3ClientBuilder`，从而保留此前所有的 override 配置。

## 如何达成设计目的

将 `applyRetryConfigurations` 的实现从"传入 Consumer 让 SDK 内部新建并替换"改为"显式基于既有配置构造新 override configuration 后整体设置"。具体：

1. 调用 `builder.overrideConfiguration()` 获取既有的 `ClientOverrideConfiguration`（可能为 `null`）。
2. 若非 `null`，调用其 `toBuilder()` 得到可变 builder，保留所有既有项；否则调用 `ClientOverrideConfiguration.builder()` 新建空 builder。
3. 在该 builder 上追加 `retryPolicy(...)`（保持原有的指数退避策略逻辑不变）。
4. 调用 `builder.overrideConfiguration(configBuilder.build())`（接收 `ClientOverrideConfiguration` 对象的重载）整体回设。

这样所有此前已设置的 override 项（signer、user agent 等）都会被保留，仅追加 retry policy。

## 修改详情

### `aws/src/main/java/org/apache/iceberg/aws/s3/S3FileIOProperties.java`

**修改目的**：修复 `applyRetryConfigurations` 覆盖既有 override 配置的 bug。

**工作逻辑**：原实现为：

```java
builder.overrideConfiguration(
    config ->
        config.retryPolicy(...));
```

新实现为：

```java
ClientOverrideConfiguration.Builder configBuilder =
    null != builder.overrideConfiguration()
        ? builder.overrideConfiguration().toBuilder()
        : ClientOverrideConfiguration.builder();

builder.overrideConfiguration(
    configBuilder
        .retryPolicy(/* 原有指数退避策略，未改动 */)
        .build());
```

`retryPolicy(...)` 内部的重试策略内容（LEGACY 模式、numRetries=5、throttled exception 处理等）完全未变，仅改变其挂载方式。同时把 Consumer 风格的链式调用改为先构造 `ClientOverrideConfiguration.Builder` 再 `.build()` 的风格。

### `aws/src/test/java/org/apache/iceberg/aws/TestS3FileIOProperties.java`

**修改目的**：扩展测试以覆盖"signer + user agent + retry policy 三者并存"的场景。

**工作逻辑**：

1. 引入 `software.amazon.awssdk.core.retry.RetryPolicy`。
2. 将原测试方法 `s3RemoteSigningEnabledWithUserAgent` 重命名为 `s3RemoteSigningEnabledWithUserAgentAndRetryPolicy`，反映其现在覆盖三项配置。
3. 在测试体中，依次调用 `applySignerConfiguration`、`applyUserAgentConfigurations`、`applyRetryConfigurations`，模拟真实使用顺序。
4. 在原有断言（user agent 存在、signer 是 `S3V4RestSignerClient`、signer 的 uri 与 properties 正确）的基础上，新增断言：`builder.overrideConfiguration().retryPolicy()` 返回 `Optional<RetryPolicy>` 且 present、类型为 `RetryPolicy`。这验证了在调用 `applyRetryConfigurations` 之后，retry policy 确实存在于 builder 的 override configuration 中——而修复前因覆盖丢失，此断言会失败（同时 user agent 也可能丢失）。

## 小结

- **成效**：修复了 S3 client 构建过程中 retry policy 配置会覆盖此前 signer/user agent 等 override 配置的 bug，保证多模块配置叠加时互不丢失。
- **影响范围**：仅 `S3FileIOProperties.applyRetryConfigurations` 一个方法的实现与对应测试，无 API 签名变更，无外部行为变化（除修复覆盖丢失外）。
- **回迁到 1.4.x 的注意事项**：这是一个纯 bug 修复，影响所有使用 `S3FileIO` 且同时配置了 signer/user agent 与 retry policy 的用户。若 1.4.x 用户在 1.4.x 版本上遇到"配置了 retry 后 signer/user agent 失效"的问题，**应回迁此修复**。回迁风险低，改动局部且无 API 兼容性问题。需注意 1.4.x 上 AWS SDK 版本是否支持 `ClientOverrideConfiguration.toBuilder()`（该 API 在 1.4.x 使用的 SDK 版本范围内应已存在，但回迁时建议验证）。
