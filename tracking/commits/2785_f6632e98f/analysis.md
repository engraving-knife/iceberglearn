# 提交 2785：Flink: Backport add maxSleepTimeMs and retryPolicyName to ZkLockFactory to support multiple retry policies. (#14389)

## 提交信息

- **序号**：2785 / 4088
- **哈希**：f6632e98f8b603822b8e656b2b9bf7d9d03b35c8
- **短哈希**：f6632e98f
- **日期**：2025-10-22 10:25:48 +0200
- **作者**：slfan1989
- **提交说明**：Flink: Backport add maxSleepTimeMs and retryPolicyName to ZkLockFactory to support multiple retry policies. (#14389)
- **PR/Issue**：#14389（backport #14243）

## 总体目的

本提交是 PR #14243（提交 2777）向 Flink 1.20 和 Flink 2.0 版本的 backport。

2777 为 Flink 2.1 的 `ZkLockFactory` 增强了 ZooKeeper 重试策略配置，新增 `maxSleepTimeMs` 参数和 `retryPolicy` 参数，支持五种 Curator 重试策略（ONE_TIME、N_TIME、EXPONENTIAL_BACKOFF、BOUNDED_EXPONENTIAL_BACKOFF、UNTIL_ELAPSED）。本提交将相同的修改同步到 Flink 1.20 和 2.0 两个版本。

注意：此 backport 不包含 2777 中的 `.baseline/checkstyle/checkstyle-suppressions.xml` 和 `docs/docs/flink-maintenance.md` 修改，因为 checkstyle 配置是全局的（已在 2777 中完成），文档也无需重复修改。

## 如何达成设计目的

将 2777 的所有代码修改完整复制到 `flink/v1.20` 和 `flink/v2.0` 两个目录下。涉及的文件和修改与 2777 完全一致：

1. `LockConfig.java`：新增 `ZK_MAX_SLEEP_MS_OPTION` 和 `ZK_RETRY_POLICY_OPTION` 配置项及读取方法
2. `ZKRetryPolicies.java`（新文件）：定义五种重试策略枚举
3. `ZkLockFactory.java`：构造器新增 `retryPolicy` 和 `maxSleepTimeMs` 参数，新增 `createRetryPolicy()` 方法
4. `LockFactoryBuilder.java`：传递新增参数
5. `TestZkLockFactory.java`：新增重试策略创建和回退测试

## 修改详情

### `flink/v1.20/` 和 `flink/v2.0/` 下各 5 个文件（共 10 个文件，+408/-8 lines）

**修改目的**：将 2777 的 ZkLockFactory 重试策略增强同步到 Flink 1.20 和 2.0 版本。

**工作逻辑**：每个版本下的修改与 2777 中 Flink 2.1 版本的修改完全一致，详见 2777 的分析文档。主要修改包括：
- `LockConfig`：新增 maxSleepTimeMs 和 retryPolicy 配置选项
- `ZKRetryPolicies`：新增重试策略枚举
- `ZkLockFactory`：构造器扩展，新增 `createRetryPolicy()` 工厂方法
- `LockFactoryBuilder`：传递新参数
- `TestZkLockFactory`：新增参数化测试验证各策略创建和回退

## 总结

本提交是 2777（#14243）向 Flink 1.20 和 2.0 的 backport，确保三个支持的 Flink 版本都获得了 ZkLockFactory 重试策略增强。修改内容与原始 PR 的代码部分完全一致，仅目录路径不同。
