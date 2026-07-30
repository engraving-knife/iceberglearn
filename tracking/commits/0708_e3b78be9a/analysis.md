# 提交 0708：AWS: Make sure Signer + User Agent config are both applied

## 提交信息
- **序号**：0708 / 4088
- **哈希**：e3b78be9ac32533500136e1a32d7f02efc03bef8
- **短哈希**：e3b78be9a
- **日期**：2024-04-22
- **作者**：Eduard Tudenhoefner
- **提交说明**：AWS: Make sure Signer + User Agent config are both applied (#10198)
- **PR/Issue**：#10198

## 总体目的

`S3FileIOProperties` 是 Iceberg AWS 模块中负责配置 S3 客户端的核心类，包含两个独立的配置方法：
- `applySignerConfiguration(T builder)`：在启用远程签名（`isRemoteSigningEnabled`）时，将自定义的 `S3V4RestSignerClient` 作为 S3 客户端的 Signer 注入到 `ClientOverrideConfiguration` 的 `SIGNER` 高级选项中。
- `applyUserAgentConfigurations(T builder)`：将 `s3fileio` 前缀注入到 `USER_AGENT_PREFIX` 高级选项中，用于在 S3 请求的 User-Agent 头中标识请求来源为 Iceberg S3FileIO，便于服务端统计与排障。

这两个方法在 S3 客户端构建过程中可能被先后调用。**本次提交要解决的 Bug 是：当两者都被调用时，只有后执行的方法的配置会生效，先执行的方法的配置会被覆盖丢失。** 也就是说，远程签名和 User Agent 前缀无法同时存在于最终的 `ClientOverrideConfiguration` 中，这会导致要么远程签名失效（请求无法被正确签名），要么 User Agent 标识缺失（服务端无法识别 Iceberg 流量）。

## 如何达成设计目的

**Bug 根因分析**：

原代码两个方法都使用 `SdkClientBuilder` 的 `overrideConfiguration(Consumer<ClientOverrideConfiguration.Builder>)` 重载。该 Consumer 重载在 AWS SDK v2 中的语义是：**创建一个全新的空 `ClientOverrideConfiguration.Builder`，应用 Consumer 中的 mutation，然后构建一个新的 `ClientOverrideConfiguration` 并整体替换**。它不是在已有配置上累加，而是每次都从空 builder 开始。

因此，当两次调用 `overrideConfiguration(Consumer)` 时：
1. 第一次调用（如 signer）构建了一个只含 `SIGNER` 的配置并设置到 builder。
2. 第二次调用（如 user agent）又从空 builder 构建，只含 `USER_AGENT_PREFIX`，**整体替换**了第一次的配置，导致 `SIGNER` 丢失。

**修复策略**：

改用 `overrideConfiguration(ClientOverrideConfiguration)` 重载（传完整配置对象而非 Consumer），并在构建新配置时**先获取已有配置作为基础**：

```java
ClientOverrideConfiguration.Builder configBuilder =
    null != builder.overrideConfiguration()
        ? builder.overrideConfiguration().toBuilder()
        : ClientOverrideConfiguration.builder();
```

- 若 builder 上已有 `ClientOverrideConfiguration`（即前一个方法已设置），则用 `toBuilder()` 将其转换为可变 builder，保留所有已设置的选项。
- 若尚无配置（`builder.overrideConfiguration()` 返回 null），则创建新的空 builder。
- 然后在此基础上 `putAdvancedOption(...)` 追加当前选项，最后 `.build()` 并整体设置。

这样无论两个方法的调用顺序如何，先设置的选项都会被 `toBuilder()` 保留，后设置的选项在其基础上追加，最终两者的配置都生效。

## 修改详情

### `aws/src/main/java/org/apache/iceberg/aws/s3/S3FileIOProperties.java`
**修改目的**：修复 `applySignerConfiguration` 和 `applyUserAgentConfigurations` 互相覆盖配置的 Bug。

**工作逻辑**：

1. 新增 import：`software.amazon.awssdk.core.client.config.ClientOverrideConfiguration`。

2. **`applySignerConfiguration(T builder)`** 改动（仅在 `isRemoteSigningEnabled` 分支内）：
   - 原：`builder.overrideConfiguration(c -> c.putAdvancedOption(SIGNER, S3V4RestSignerClient.create(allProperties)));`
   - 新：先取已有配置转 builder（或新建空 builder），再 `putAdvancedOption(SIGNER, ...)`，最后 `.build()` 传入 `overrideConfiguration(ClientOverrideConfiguration)`。
   - 保留 `isRemoteSigningEnabled` 外层判断不变。

3. **`applyUserAgentConfigurations(T builder)`** 改动：
   - 原：`builder.overrideConfiguration(c -> c.putAdvancedOption(USER_AGENT_PREFIX, S3_FILE_IO_USER_AGENT));`
   - 新：同样的"取已有配置 → toBuilder → putAdvancedOption → build"模式。
   - 注意此方法是无条件执行的（没有 if 守卫），因此总是会在已有配置基础上追加 User Agent。

两个方法的修复模式完全对称，确保无论调用顺序如何，`SIGNER` 与 `USER_AGENT_PREFIX` 都能并存于最终配置中。

### `aws/src/test/java/org/apache/iceberg/aws/TestS3FileIOProperties.java`
**修改目的**：新增端到端测试，验证 signer 与 user agent 同时生效。

**工作逻辑**：新增 `s3RemoteSigningEnabledWithUserAgent()` 测试：
- 构造同时启用 `REMOTE_SIGNING_ENABLED=true` 和 `URI` 的属性。
- 依次调用 `applySignerConfiguration(builder)` 和 `applyUserAgentConfigurations(builder)`。
- 断言 builder 的 `overrideConfiguration()` 中 `USER_AGENT_PREFIX` 存在且以 `"s3fileio"` 开头。
- 断言 `SIGNER` 存在且为 `S3V4RestSignerClient` 实例，且其 `baseSignerUri()` 等于配置的 URI，`properties()` 等于传入属性。
- 该测试在修复前会失败（signer 被覆盖），修复后通过。

### `aws/src/test/java/org/apache/iceberg/aws/s3/TestS3FileIOProperties.java`
**修改目的**：更新现有 mock 测试以匹配新的方法签名，并补充 User Agent 测试的验证。

**工作逻辑**：
1. 新增 imports：`CatalogProperties`、`ImmutableMap`、`ClientOverrideConfiguration`；移除 `Consumer` import。
2. **`testApplySignerConfiguration()`**：
   - 将 `Maps.newHashMap()` 改为 `ImmutableMap.of(REMOTE_SIGNING_ENABLED, "true", CatalogProperties.URI, "http://localhost:12345")`（补充 URI，因为 `S3V4RestSignerClient` 现在需要）。
   - 验证方式从 `verify(builder).overrideConfiguration(any(Consumer.class))` 改为 `verify(builder).overrideConfiguration(any(ClientOverrideConfiguration.class))`，匹配新的方法重载。
3. **`testApplyUserAgentConfigurations()`**（文件末尾）：同样将 verify 的参数类型从 `Consumer.class` 改为 `ClientOverrideConfiguration.class`。

## 小结
- **成效**：成功修复 Signer 与 User Agent 配置互相覆盖的 Bug，两者现在可同时生效。新增的端到端测试直接覆盖了该场景，回归保护到位。
- **影响范围**：AWS 模块 `S3FileIOProperties` 的 S3 客户端配置逻辑，影响所有使用 S3FileIO 且同时启用远程签名与 User Agent 的部署场景（较常见）。
- **回迁到 1.4.x 的注意事项**：此为 Bug 修复，建议回迁。需确认 1.4.x 分支的 AWS SDK 版本支持 `ClientOverrideConfiguration.toBuilder()` 和 `overrideConfiguration(ClientOverrideConfiguration)` 重载（AWS SDK v2 较新版本均支持）。测试中 `S3V4RestSignerClient.baseSignerUri()` 等 API 需在 1.4.x 中存在。若 1.4.x 的 `S3FileIOProperties` 结构与 main 差异较大，需手动适配而非直接 cherry-pick。
