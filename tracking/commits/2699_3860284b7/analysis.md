# 提交 2699：Flink: Ensure DynamicCommitter Idempotence in the presence of failures (#14182)

## 提交信息

- **序号**：2699 / 4088
- **哈希**：3860284b763a3a744e9ebb3b58278c4ba91f1f5d
- **短哈希**：3860284b7
- **日期**：2025-09-29 14:29:10 +0200
- **作者**：Maximilian Michels
- **提交说明**：Flink: Ensure DynamicCommitter Idempotence in the presence of failures (#14182)
- **PR/Issue**：#14182

## 总体目的

本提交修复 Flink Dynamic Iceberg Sink 中 `DynamicCommitter` 在失败场景下缺乏幂等性的问题。DynamicCommitter 负责将 Flink checkpoint 产生的 `WriteResult`（含数据文件与删除文件）提交到 Iceberg 表。

此前的实现存在两个关键缺陷：

1. **粒度过细的多次提交**：对于同一 checkpoint 内的多个 `WriteResult`，旧代码会为每个 `WriteResult` 单独创建一个 `RowDelta`（delta 场景）或 `ReplacePartitions`（overwrite 场景）并各自提交。这意味着一个 checkpoint 的数据被拆分成多个 Iceberg 提交（多个 snapshot）。

2. **失败重试导致重复数据**：Iceberg 通过 snapshot 属性 `flink.max-committed-checkpoint-id`、`flink.job-id`、`flink.operator-id` 来识别某个 checkpoint 是否已提交，以实现幂等去重。但该去重粒度是 checkpoint 级别。当旧代码把一个 checkpoint 拆成多次提交后，若中途失败（部分 WriteResult 已提交、部分未提交），重试时会重新提交整个 checkpoint 的所有 WriteResult——已成功提交的部分会被重复提交，导致数据文件重复写入表中，破坏 exactly-once 语义。

修复思路是：将同一 checkpoint 内的所有 `WriteResult` 合并到一次 Iceberg 提交中（一个 `RowDelta`/`ReplacePartitions` → 一次 `commit()` → 一个 snapshot）。这样每个 checkpoint 对应一个原子提交，要么全部成功要么全部不成功，重试时基于 checkpoint-id 的去重能正确生效，避免重复数据。

对于 overwrite（`replacePartitions`）场景，由于 Iceberg 表无序，进一步将所有 pending checkpoint 的数据合并为一次 `ReplacePartitions` 提交（一个 snapshot），用 `pendingResults.lastKey()` 作为 checkpoint-id 标记。

## 如何达成设计目的

主要修改点：

1. **`commitReplacePartitionsTxn`（overwrite 场景）**：将原先循环内每个 `WriteResult` 各创建一个 `ReplacePartitions` 并单独提交，改为在循环外创建一个 `ReplacePartitions`，循环内仅 `addFile`，循环结束后统一调用一次 `commitOperation`，checkpoint-id 取 `pendingResults.lastKey()`。注释说明"Iceberg 表无序，append 顺序无关，故合并为一个 snapshot"。

2. **`commitDeltaTxn`（delta 场景）**：将原先循环内每个 `WriteResult` 各创建一个 `RowDelta` 并单独提交，改为每个 checkpoint（外层循环）创建一个 `RowDelta`，内层循环仅 `addRows`/`addDeletes`，外层循环末尾统一调用一次 `commitOperation`。注释说明：每个 Flink checkpoint 包含一组可一起提交的独立变更；跨 checkpoint 合并 append-only 数据技术上可行但为简化暂不实现；多 pending checkpoint 罕见（仅在极短 checkpoint 间隔或并发 checkpoint 启用时出现）。delta 场景不跨 checkpoint 合并，是为了保留 equality-delete 对前序 checkpoint 数据文件的正确语义。

3. **测试增强**：在 `TestDynamicCommitter` 中新增 `testTableBranchAtomicCommitForAppendOnlyData` 和 `testTableBranchAtomicCommitWithFailures` 两个测试，验证多 checkpoint/多分支的合并提交行为，以及在 commit 前/中/后三阶段失败后重试最终仍得到正确快照状态。同时将 `CommitHook`、`FailBeforeAndAfterCommit`、`CommitHookEnabledDynamicCommitter` 从 `TestDynamicIcebergSink` 提取到 `TestDynamicCommitter` 中复用，并增强 `FailBeforeAndAfterCommit` 增加 `duringCommit` 失败模拟与重试次数从 2 调到 3 以覆盖三阶段失败。

## 修改详情

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicCommitter.java` (+30/-25 lines)

**修改目的**：将同一 checkpoint 的多次提交合并为单次原子提交，保证失败重试幂等。

**工作逻辑**：

- `commitReplacePartitionsTxn`：把 `ReplacePartitions dynamicOverwrite = table.newReplacePartitions()...` 提到循环外；循环内仅对每个 `WriteResult` 调 `addFile`；循环结束后统一 `commitOperation(..., pendingResults.lastKey())`。同时删除原注释中关于"不能合并提交"的说明，替换为"Iceberg 表无序，顺序无关，合并为一个 snapshot"。
- `commitDeltaTxn`：外层按 checkpoint 迭代，每个 checkpoint 内创建一个 `RowDelta`，内层对每个 `WriteResult` 调 `addRows`/`addDeletes`，外层末尾统一 `commitOperation(..., checkpointId)`。保留 equality-delete 语义相关注释，新增说明"每个 checkpoint 的独立变更可一起提交；跨 checkpoint 合并 append-only 可行但暂不实现"。

`commitOperation` 本身未改：仍设置 `MAX_COMMITMITTED_CHECKPOINT_ID`/`FLINK_JOB_ID`/`OPERATOR_ID` 并 `toBranch(branch)` 后 `commit()`。由于现在每个 checkpoint 只产生一次提交，失败重试时基于 checkpoint-id 的去重能正确判定是否已提交，从而幂等。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestDynamicCommitter.java` (+392/-43 lines)

**修改目的**：新增覆盖合并提交与失败重试幂等性的测试，并将 CommitHook 相关测试基础设施集中到本类。

**工作逻辑**：
- 新增 `DATA_FILE_2` 与 `DELETE_FILE` 静态测试夹具。
- 新增 `testTableBranchAtomicCommitForAppendOnlyData`：构造 3 个 committable（跨 2 个 checkpoint、2 个分支），append-only 数据，验证 commit 后产生 2 个 snapshot（branch1 合并 checkpoint1 的两个 WriteResult 为一个 snapshot，branch2 为另一个 snapshot），并断言 summary 中 `added-data-files`、`added-records`、`flink.max-committed-checkpoint-id` 等正确。
- 新增 `testTableBranchAtomicCommitWithFailures`：构造 3 个 committable（checkpoint1 含 1 数据文件；checkpoint2 含 1 删除文件 + 1 数据文件），使用 `CommitHookEnabledDynamicCommitter` + `FailBeforeAndAfterCommit` 依次在 before/during/after commit 阶段失败，第四次重试成功；断言最终 2 个 snapshot 的 summary 正确（checkpoint2 的 snapshot 合并了数据文件与删除文件，`total-delete-files=1`、`total-position-deletes=24`、`total-records=84`），证明失败重试未产生重复数据。
- 引入 `CommitHook` 接口、`FailBeforeAndAfterCommit`（新增 `failedDuringCommit` 状态与 `duringCommit` 失败逻辑）、`CommitHookEnabledDynamicCommitter`（重写 `commit` 与 `commitOperation` 注入 hook）。
- 多处将旧的 `(Map) ImmutableMap.builder()...build()` 改为带泛型的 `ImmutableMap.<String, String>builder()...build()`，消除原始类型警告。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestDynamicIcebergSink.java` (+12/-92 lines)

**修改目的**：复用 `TestDynamicCommitter` 中提取的 CommitHook 基础设施，移除本类中的重复定义。

**工作逻辑**：
- 删除本类中重复的 `CommitHook` 接口、`FailBeforeAndAfterCommit`、`CommitHookEnabledDynamicCommitter` 定义；
- import 改为引用 `TestDynamicCommitter.CommitHook` 与 `TestDynamicCommitter.FailBeforeAndAfterCommit`；
- `CommitHookEnabledDynamicCommitter` 改用 `TestDynamicCommitter.CommitHookEnabledDynamicCommitter`；
- `FailBeforeAndAfterCommit` 增加 `failedDuringCommit` 后，端到端测试的固定延迟重试次数从 2 调到 3，以覆盖三阶段失败；新增对 `failedDuringCommit` 的断言；
- 移除未使用的 import（`Collection`、`SnapshotUpdate`、`CommitSummary`）。

## 总结

本提交修复了 Flink Dynamic Iceberg Sink 的 `DynamicCommitter` 在失败重试下缺乏幂等性的问题。根因是旧实现把单个 checkpoint 的多个 `WriteResult` 拆成多次独立 Iceberg 提交，失败重试时已成功部分会被重复提交，破坏 exactly-once。修复将同一 checkpoint 的所有 WriteResult 合并为一次原子提交（overwrite 场景进一步跨 checkpoint 合并），使基于 checkpoint-id 的去重正确生效。配套测试覆盖了多分支合并提交与三阶段失败重试场景，验证最终快照状态正确、无重复数据。本提交是 2701 backport 的原始 PR，二者为同一功能在不同 Flink 版本的实现/backport。
