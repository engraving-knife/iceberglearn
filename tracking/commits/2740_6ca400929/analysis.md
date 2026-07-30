# 提交 2740：Flink: Backport clear globalStatisticsState in init to avoid duplication

## 提交信息

- **序号**：2740 / 4088
- **哈希**：6ca4009297483eaf6a66c865bb0498d778000b1a
- **短哈希**：6ca400929
- **日期**：2025-10-13 13:25:24 +0200
- **作者**：GuoYu
- **提交说明**：Flink: Backport clear globalStatisticsState in init to avoid duplication
- **PR/Issue**：#14315（backport #14294）

## 总体目的

这是提交 2738（PR #14294）的 backport 提交。原始 PR #14294 修复了 Flink `DataStatisticsOperator` 在任务恢复时 `globalStatisticsState` 数据重复的问题，但原始提交只修改了 `flink/v2.1` 版本。

由于 Iceberg 同时维护多个 Flink 版本的集成模块（v1.20、v2.0、v2.1），同一 bug 修复需要应用到所有受支持的 Flink 版本。本提交将该修复 backport 到 `flink/v1.20` 和 `flink/v2.0` 两个版本，确保所有支持的 Flink 版本都能获得此 bug 修复。

## 如何达成设计目的

将 PR #14294 的修改分别应用到 `flink/v1.20` 和 `flink/v2.0` 模块：
1. 在两个版本的 `DataStatisticsOperator.java` 中，于 `initializeState` 方法恢复 `globalStatistics` 后添加 `globalStatisticsState.clear()` 调用
2. 新增 `@VisibleForTesting` 的 `globalStatisticsState()` getter 方法
3. 在两个版本的 `TestDataStatisticsOperator.java` 中添加验证 state 被清空的断言

修改内容与原始提交 2738 完全一致，只是目标模块不同。

## 修改详情

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/DataStatisticsOperator.java` (+8/-0 lines)

**修改目的**：为 Flink 1.20 版本添加 state 清除逻辑。

**工作逻辑**：在 `initializeState` 中恢复 `globalStatistics` 后调用 `globalStatisticsState.clear()`，新增 `globalStatisticsState()` getter 方法供测试使用。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/sink/shuffle/TestDataStatisticsOperator.java` (+6/-0 lines)

**修改目的**：为 Flink 1.20 版本添加测试验证。

**工作逻辑**：从 snapshot 恢复后断言 `globalStatisticsState` 为空。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/DataStatisticsOperator.java` (+8/-0 lines)

**修改目的**：为 Flink 2.0 版本添加 state 清除逻辑。

**工作逻辑**：与 v1.20 相同的修改。

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/sink/shuffle/TestDataStatisticsOperator.java` (+6/-0 lines)

**修改目的**：为 Flink 2.0 版本添加测试验证。

**工作逻辑**：与 v1.20 相同的测试修改。

## 总结

本提交是 PR #14294（提交 2738）的 backport，将 `globalStatisticsState` 清除修复应用到 Flink v1.20 和 v2.0 版本。原始修复针对 v2.1，此 backport 确保所有受支持的 Flink 版本（v1.20、v2.0、v2.1）都获得该 bug 修复。共修改 4 个文件（2 个模块 x 2 文件），修改内容与原始提交完全一致。
