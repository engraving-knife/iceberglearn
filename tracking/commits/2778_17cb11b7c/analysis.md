# 提交 2778：Flink: Add maxSleepTimeMs and retryPolicyName to ZkLockFactory to support multiple retry policies (#14243)

## 提交信息

- **序号**：2778 / 4088
- **哈希**：17cb11b7c503fe39a9905a6142f829e0c8a80306
- **短哈希**：17cb11b7c
- **日期**：2025-10-21 15:04:24 +0200
- **作者**：slfan1989
- **提交说明**：Flink: Add maxSleepTimeMs and retryPolicyName to ZkLockFactory to support multiple retry policies (#14243)
- **PR/Issue**：#14243

## 总体目的

本提交增强 Flink 维护 API 中 `ZkLockFactory` 的 ZooKeeper 重试策略配置灵活性。

在此之前，`ZkLockFactory` 只支持一种重试策略——`ExponentialBackoffRetry`（基于 baseSleepTimeMs 和 maxRetries）。然而不同的部署场景对 ZooKeeper 客户端重试行为有不同的需求：有些场景需要固定次数重试，有些需要有限指数退避（有最大睡眠时间上限），有些则需要基于时间上限的重试。

本提交通过两个方面的增强来解决：
1. **新增 `maxSleepTimeMs` 参数**：为指数退避策略设置最大睡眠时间上限，避免重试间隔无限增长。这对 `BoundedExponentialBackoffRetry` 和 `RetryUntilElapsed` 策略尤其重要。
2. **新增 `retryPolicy` 参数**：支持用户从多种 Curator 重试策略中选择，包括 ONE_TIME、N_TIME、EXPONENTIAL_BACKOFF、BOUNDED_EXPONENTIAL_BACKOFF、UNTIL_ELAPSED。

## 如何达成设计目的

1. **新增 `ZKRetryPolicies` 枚举**：定义五种支持的重试策略类型，对应 Curator 的不同 RetryPolicy 实现。

2. **`LockConfig` 新增配置项**：新增 `ZK_MAX_SLEEP_MS_OPTION`（默认 10000ms）和 `ZK_RETRY_POLICY_OPTION`（默认 EXPONENTIAL_BACKOFF）两个配置选项，以及对应的 `zkMaxSleepMs()` 和 `zkRetryPolicy()` 读取方法。

3. **`ZkLockFactory` 构造器扩展**：新增 `retryPolicy` 和 `maxSleepTimeMs` 参数。新增 `createRetryPolicy()` 方法，根据 `retryPolicy` 枚举值创建对应的 Curator RetryPolicy 实例。验证 `maxSleepTimeMs >= baseSleepTimeMs`。

4. **`LockFactoryBuilder` 适配**：在构建 `ZkLockFactory` 时传入新的 `retryPolicy` 和 `maxSleepTimeMs` 参数。

5. **文档更新**：在 `flink-maintenance.md` 中补充两个新配置项的说明。

6. **测试**：新增参数化测试验证每种重试策略创建正确的 RetryPolicy 类型；新增测试验证无效/缺失的重试策略名称回退到默认值。

## 修改详情

### `.baseline/checkstyle/checkstyle-suppressions.xml` (+1/-0 lines)

**修改目的**：允许测试类 `TestZkLockFactory` 使用 Flink shaded Curator 依赖。

**工作逻辑**：新增 suppress 规则，对 `TestZkLockFactory` 豁免 `BanShadedClasses` 检查，因为测试需要直接引用 Curator 的 RetryPolicy 类进行类型验证。

### `docs/docs/flink-maintenance.md` (+2/-0 lines)

**修改目的**：补充文档说明新增的两个 ZooKeeper 配置项。

**工作逻辑**：在配置表格中新增 `max-sleep-ms`（最大重试睡眠时间，默认 10000ms）和 `retry-policy`（重试策略名称，支持 ONE_TIME/N_TIME/BOUNDED_EXPONENTIAL_BACKOFF/UNTIL_ELAPSED/EXPONENTIAL_BACKOFF，默认 EXPONENTIAL_BACKOFF）。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/LockConfig.java` (+42/-0 lines)

**修改目的**：新增 maxSleepTimeMs 和 retryPolicy 配置选项及读取方法。

**工作逻辑**：在 `ZkLockConfig` 内部类中新增 `ZK_MAX_SLEEP_MS_OPTION`（int 类型，默认 10000）和 `ZK_RETRY_POLICY_OPTION`（enum 类型 ZKRetryPolicies，默认 EXPONENTIAL_BACKOFF）两个 ConfigOption。在 `LockConfig` 中新增 `zkMaxSleepMs()` 和 `zkRetryPolicy()` 方法，通过 confParser 解析配置。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/ZKRetryPolicies.java` (+36 lines, 新文件)

**修改目的**：定义支持的重试策略枚举。

**工作逻辑**：定义五种枚举值：ONE_TIME（重试一次）、N_TIME（固定次数）、EXPONENTIAL_BACKOFF（指数退避）、BOUNDED_EXPONENTIAL_BACKOFF（有上限的指数退避）、UNTIL_ELAPSED（基于时间上限）。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/ZkLockFactory.java` (+50/-4 lines)

**修改目的**：支持多种重试策略和最大睡眠时间。

**工作逻辑**：新增 `retryPolicy` 和 `maxSleepTimeMs` 字段及构造器参数。构造器中验证 `maxSleepTimeMs >= baseSleepTimeMs`。新增 `@VisibleForTesting RetryPolicy createRetryPolicy()` 方法，根据 retryPolicy 枚举创建对应的 Curator RetryPolicy：ONE_TIME → RetryOneTime，N_TIME → RetryNTimes，BOUNDED_EXPONENTIAL_BACKOFF → BoundedExponentialBackoffRetry，UNTIL_ELAPSED → RetryUntilElapsed，EXPONENTIAL_BACKOFF → ExponentialBackoffRetry。`buildClient()` 中用 `createRetryPolicy()` 替代硬编码的 `new ExponentialBackoffRetry(...)`。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/LockFactoryBuilder.java` (+4/-1 lines)

**修改目的**：传递新增的 retryPolicy 和 maxSleepTimeMs 参数。

**工作逻辑**：在构建 `ZkLockFactory` 时追加 `lockConfig.zkRetryPolicy()` 和 `lockConfig.zkMaxSleepMs()` 参数。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/maintenance/api/TestZkLockFactory.java` (+76/-1 lines)

**修改目的**：测试多种重试策略的创建和无效配置的回退。

**工作逻辑**：
- 更新 `lockFactory()` 方法适配新构造器参数（传入 `ZKRetryPolicies.EXPONENTIAL_BACKOFF` 和 `2000`）。
- 新增 `retryPolicyProvider()` 参数源，提供五种策略与对应 RetryPolicy 类的映射。
- 新增 `testRetryPolicyCreationAndType` 参数化测试，验证每种枚举创建正确类型的 RetryPolicy。
- 新增 `testInvalidOrMissingRetryPolicyFallsBackToDefault` 测试，验证 null/空字符串/无效策略名称都回退到 EXPONENTIAL_BACKOFF。

## 总结

本提交显著增强了 Flink ZkLockFactory 的重试策略灵活性，从单一的 ExponentialBackoffRetry 扩展为支持五种 Curator 重试策略。新增的 `maxSleepTimeMs` 参数为有界退避策略提供了睡眠时间上限控制。通过枚举配置和工厂方法模式，用户可以方便地根据部署场景选择合适的重试行为。测试覆盖了所有策略类型的正确创建和无效配置的安全回退。注意此提交针对 Flink 2.1 版本，2784 是同一功能向 Flink 1.20/2.0 的 backport。
