# 提交 2739：Flink: Clear globalStatisticsState in init to avoid duplication

## 提交信息

- **序号**：2739 / 4088
- **哈希**：59344e83dfa61ce5bd834d5a6de9a51dc04b9b6b
- **短哈希**：59344e83d
- **日期**：2025-10-13 11:51:08 +0200
- **作者**：GuoYu
- **提交说明**：Flink: Clear globalStatisticsState in init to avoid duplication
- **PR/Issue**：#14294

## 总体目的

在 Iceberg 的 Flink 集成中，`DataStatisticsOperator` 是用于数据统计和 shuffle 的算子，它维护一个 `globalStatisticsState`（Flink 的 ListState）来持久化全局统计信息。当 Flink 任务从 savepoint 或 checkpoint 恢复时，该状态会被还原。

问题在于：在算子初始化（`initializeState`）阶段，还原的 `globalStatisticsState` 不会被清除。后续当算子从 coordinator 接收到新的统计信息并写入 state 时，新数据会追加到已还原的旧数据之后，导致 state 中出现重复的统计信息。这种重复可能导致数据处理的正确性问题，例如 shuffle 分区决策基于过时或重复的统计信息。

本提交的目的是在初始化阶段，从 state 恢复了 `globalStatistics` 后立即清除 `globalStatisticsState`，确保后续写入的新统计信息不会与旧的还原数据产生重复。

## 如何达成设计目的

设计思路简单直接：在 `initializeState` 方法中，从 `globalStatisticsState` 恢复 `globalStatistics` 后，立即调用 `globalStatisticsState.clear()` 清空 state。这样：
1. 恢复的统计信息已被读取到内存中的 `globalStatistics` 变量，用于初始处理
2. state 被清空后，后续从 coordinator 接收的新统计信息写入 state 时不会与旧数据混合
3. 算子仍会向 coordinator 请求新的统计信息（这是已有逻辑），获取到新信息后更新内存和 state

此外，新增了一个 `@VisibleForTesting` 的 `globalStatisticsState()` getter 方法，使测试能够验证 state 在恢复后确实被清空。

## 修改详情

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/DataStatisticsOperator.java` (+8/-0 lines)

**修改目的**：在初始化阶段清除 globalStatisticsState 以避免数据重复。

**工作逻辑**：
- 在 `initializeState` 方法中，从 state 恢复 `globalStatistics` 之后、请求新统计信息之前，加入 `globalStatisticsState.clear()` 调用。注释说明这是为了确保 state 为空
- 新增 `@VisibleForTesting` 注解的 `globalStatisticsState()` 方法，返回 `ListState<GlobalStatistics>`，供测试代码验证 state 状态

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/sink/shuffle/TestDataStatisticsOperator.java` (+6/-0 lines)

**修改目的**：验证从 savepoint 恢复后 globalStatisticsState 被正确清空。

**工作逻辑**：在测试中，从 snapshot 恢复状态后，通过新增的 `globalStatisticsState()` getter 获取 state 的可迭代对象，断言其为空（`assertThat(globalStatisticsIterable).isEmpty()`）。注释说明恢复 savepoint 时应确保 `globalStatisticsState` 已被完全清理。

## 总结

本提交修复了 Flink `DataStatisticsOperator` 在任务恢复时 state 数据重复的问题。通过在初始化阶段清除已还原的 `globalStatisticsState`，确保后续新统计信息不会与旧数据混合。该修改针对 Flink v2.1 版本，对应的 backport 提交为 2739（PR #14315）。
