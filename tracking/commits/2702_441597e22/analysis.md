# 提交 2702：Flink: Backport #14182: Ensure DynamicCommitter Idempotence in the presence of failures (#14213)

## 提交信息

- **序号**：2702 / 4088
- **哈希**：441597e22ef3ec1ea03fd837cbc1e5dffce899a4
- **短哈希**：441597e22
- **日期**：2025-09-29 08:55:34 -0700
- **作者**：Maximilian Michels
- **提交说明**：Flink: Backport #14182: Ensure DynamicCommitter Idempotence in the presence of failures (#14213)
- **PR/Issue**：#14213，回自 #14182（即 2698）

## 总体目的

本提交是 PR #14182（即 2698，作用于 `flink/v2.1`）的 backport，将同一 DynamicCommitter 幂等性修复同步到 `flink/v1.20` 与 `flink/v2.0` 两条维护分支。

原始 PR #14182 修复了 Flink Dynamic Iceberg Sink 的 `DynamicCommitter` 在失败重试下缺乏幂等性的问题：旧实现把单个 checkpoint 的多个 `WriteResult` 拆成多次独立 Iceberg 提交，失败重试时已成功部分会被重复提交，破坏 exactly-once。修复将同一 checkpoint 的所有 WriteResult 合并为一次原子提交（overwrite 场景进一步跨 checkpoint 合并），使基于 checkpoint-id 的去重正确生效。

由于 `flink/v1.20` 与 `flink/v2.0` 维护分支上的 `DynamicCommitter`、`TestDynamicCommitter`、`TestDynamicIcebergSink` 与 `flink/v2.1` 上的对应文件基线一致，backport 的改动内容与 2698 完全相同。

## 如何达成设计目的

与 2698 相同，修改点为：

1. `commitReplacePartitionsTxn`（overwrite 场景）：将 `ReplacePartitions` 创建提到循环外，循环内仅 `addFile`，循环结束后统一 `commitOperation(..., pendingResults.lastKey())`。
2. `commitDeltaTxn`（delta 场景）：每个 checkpoint 创建一个 `RowDelta`，内层仅 `addRows`/`addDeletes`，外层末尾统一 `commitOperation(..., checkpointId)`。
3. 测试增强：新增 `testTableBranchAtomicCommitForAppendOnlyData` 与 `testTableBranchAtomicCommitWithFailures`；将 `CommitHook`/`FailBeforeAndAfterCommit`/`CommitHookEnabledDynamicCommitter` 提取到 `TestDynamicCommitter` 复用；`FailBeforeAndAfterCommit` 增加 `duringCommit` 失败模拟；端到端测试重试次数从 2 调到 3。

## 修改详情

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicCommitter.java` (+30/-25 lines)

**修改目的**：将同一 checkpoint 的多次提交合并为单次原子提交。

**工作逻辑**：与 2698 中 `flink/v2.1` 同名文件改动完全一致——`commitReplacePartitionsTxn` 把 `ReplacePartitions` 提到循环外统一提交一次（checkpoint-id 取 `pendingResults.lastKey()`）；`commitDeltaTxn` 每 checkpoint 创建一个 `RowDelta`，内层仅 `addRows`/`addDeletes`，外层统一 `commitOperation`。注释更新为说明 Iceberg 表无序、每 checkpoint 独立变更可一起提交。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestDynamicCommitter.java` (+392/-43 lines)

**修改目的**：新增合并提交与失败重试幂等性测试，集中 CommitHook 基础设施。

**工作逻辑**：与 2698 中 `flink/v2.1` 同名文件改动一致——新增 `DATA_FILE_2`/`DELETE_FILE` 夹具；新增 `testTableBranchAtomicCommitForAppendOnlyData`（多分支多 checkpoint append-only 合并提交验证）与 `testTableBranchAtomicCommitWithFailures`（三阶段失败重试后快照状态正确、无重复数据）；引入 `CommitHook` 接口、增强 `FailBeforeAndAfterCommit`（含 `duringCommit`）、`CommitHookEnabledDynamicCommitter`；多处 `(Map) ImmutableMap.builder()` 改为带泛型形式。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestDynamicIcebergSink.java` (+12/-92 lines)

**修改目的**：复用 `TestDynamicCommitter` 的 CommitHook 基础设施，移除重复定义。

**工作逻辑**：与 2698 中 `flink/v2.1` 同名文件改动一致——删除重复的 `CommitHook`/`FailBeforeAndAfterCommit`/`CommitHookEnabledDynamicCommitter`，改 import 引用 `TestDynamicCommitter.*`；重试次数 2→3；新增 `failedDuringCommit` 断言；清理未使用 import。

### `flink/v2.0/...` 同名文件（+556/-204 lines 合计）

**修改目的**：在 Flink v2.0 维护分支上同步应用与 v1.20 完全相同的改动。

**工作逻辑**：三个文件（`DynamicCommitter`、`TestDynamicCommitter`、`TestDynamicIcebergSink`）的改动与 v1.20 完全一致。

## 总结

本 backport 将 2698（PR #14182）的 DynamicCommitter 幂等性修复同步到 Flink v1.20 与 v2.0 两条维护分支，改动内容与原 PR 完全一致。这样三条 Flink 维护分支（v1.20、v2.0、v2.1）都获得了该 exactly-once 修复，避免失败重试导致数据文件重复提交。2698 与 2701 是同一功能在不同 Flink 版本的实现/backport 关系。
