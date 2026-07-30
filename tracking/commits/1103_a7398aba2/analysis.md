# 提交 1103：Flink: Fix duplicate data with upsert writer in case of aborted checkpoints (#10526)

## 提交信息

- **序号**：1103 / 4088
- **哈希**：a7398aba2143a80861692330726e2523e84d26c6
- **短哈希**：a7398aba2
- **日期**：2024-08-27（Tue Aug 27 01:05:16 2024 +0800）
- **作者**：Qishang Zhong <zhongqishang@gmail.com>
- **提交说明**：Flink: Fix duplicate data with upsert writer in case of aborted checkpoints (#10526)
- **PR/Issue**：#10526
- **影响模块**：flink v1.19 sink（同一改动同时也存在于 v1.18/v1.20 等其他 flink 版本目录，但本提交统计的 diff 落在 v1.19）

## 总体目的

Iceberg V2 表 + Flink upsert writer（带 equality delete）在 checkpoint 被中断（aborted checkpoint）场景下会出现**重复数据**。根因如下：

`IcebergStreamWriter` 在 `prepareSnapshotPreBarrier(checkpointId)` 时 `flush()` 出当前 writer 的 `WriteResult`（包含 data file + equality delete file）发给下游 `IcebergFilesCommitter`。原 committer 用单个 `List<WriteResult> writeResultsOfCurrentCkpt` 累积这些结果，只在 `snapshotState` 时把整个 list 写成一个 manifest，并 `clear()`。

当 checkpoint N 被中断（aborted），Flink 不会调用 `snapshotState(N)`（或调用后 `notifyCheckpointComplete(N)` 不会被触发），但 `IcebergStreamWriter.flush()` 已经发出 `WriteResult` 给 committer 并被加入 list。下一次 checkpoint N+1 的 `prepareSnapshotPreBarrier` 又会发出新 `WriteResult`，于是 committer 的 list 同时包含 N 和 N+1 的结果。当 `snapshotState(N+1)` 时，所有这些 WriteResult 被合并进**同一个 manifest、同一次 Iceberg commit、同一个 sequence number**。equality delete 只对**严格更小 sequence number** 的 data file 生效，同一 commit 内 delete 无法消除同批的 data file —— 于是同一行的多个版本（来自 N 和 N+1）都存活，形成重复数据。

本提交通过：

1. 引入 `FlinkWriteResult(checkpointId, WriteResult)` 包装类，让每个 `WriteResult` 携带它所属的 checkpointId；
2. `IcebergStreamWriter.flush(checkpointId)` 在 emit 时打上当前 checkpointId；`endInput` 时打上 `END_INPUT_CHECKPOINT_ID = Long.MAX_VALUE`；
3. `IcebergFilesCommitter` 改用 `Map<Long, List<WriteResult>> writeResultsSinceLastSnapshot` 按 checkpointId 分组；
4. `snapshotState` 时调用新方法 `writeToManifestUptoLatestCheckpoint`，对**每个 checkpointId 各写一个 manifest**，分别存到 `dataFilesPerCheckpoint` 里；
5. `commitUpToCheckpoint` 按顺序对每个 checkpointId 各做一次 Iceberg commit，从而保证每个 checkpoint 拥有独立的 sequence number，equality delete 能正确跨 checkpoint 生效。

## 如何达成设计目的

核心设计：**把"哪个 checkpoint 写出的文件"这一信息从隐式（靠 committer 的 list 清空时机）改成显式（写在每个 WriteResult 上）**。

- `IcebergStreamWriter` 在 `prepareSnapshotPreBarrier(checkpointId)` 和 `endInput()` 两处调用 `flush(long checkpointId)`，emit `new FlinkWriteResult(checkpointId, result)`。
- `IcebergFilesCommitter.processElement` 收到 `FlinkWriteResult` 后，按 `checkpointId` 放进 map 的对应 list。
- `snapshotState(checkpointId)`：
  - 调 `writeToManifestUptoLatestCheckpoint(checkpointId)`：
    - 如果 map 里没有 `checkpointId` 这一项（说明本 checkpoint 没有任何 WriteResult，例如空 checkpoint），仍然在 `dataFilesPerCheckpoint` 里 put 一个 `EMPTY_MANIFEST_DATA`，保证该 checkpoint 在 state 里有占位，commit 链不会跳号；
    - 遍历 map 中**所有** entry，对每个 `(ckptId, list)` 调 `writeToManifest(ckptId, list)`，写各自 manifest 到 `dataFilesPerCheckpoint`；
    - 清空 `writeResultsSinceLastSnapshot`。
- `notifyCheckpointComplete(checkpointId)` 触发 `commitUpToCheckpoint(dataFilesPerCheckpoint, ...)`,后者遍历 ≤ `checkpointId` 的所有 entry，**每个 checkpointId 各 commit 一次**（已有逻辑），即每个 checkpoint 是一次独立的 Iceberg commit，拥有独立 sequence number。

如此：N 中断后，N+1 完成时，`dataFilesPerCheckpoint` 里有 `{N: manifestN, N+1: manifestN+1}`，commit 时按顺序先 commit N 再 commit N+1，两次 Iceberg commit 拥有递增的 sequence number，N+1 的 equality delete 才能正确删掉 N 的旧版本数据。

## 修改详情

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/FlinkWriteResult.java`（新增）

**修改目的**：给 `WriteResult` 加上 checkpointId 标签。

**工作逻辑**：可序列化的简单值对象，持有 `long checkpointId` 与 `WriteResult writeResult`，提供 `checkpointId()` 与 `writeResult()` 两个 getter。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/IcebergStreamWriter.java`

**修改目的**：emit 时打 checkpointId。

**工作逻辑**：

- 类签名由 `OneInputStreamOperator<T, WriteResult>` 改为 `OneInputStreamOperator<T, FlinkWriteResult>`。
- 新增常量 `static final long END_INPUT_CHECKPOINT_ID = Long.MAX_VALUE;`，用于 `endInput` 路径，避免与真实 checkpointId 冲突。
- `flush()` 改签名为 `flush(long checkpointId)`，最后 `output.collect(new StreamRecord<>(new FlinkWriteResult(checkpointId, result)))`。
- `prepareSnapshotPreBarrier(checkpointId)` 调 `flush(checkpointId)`。
- `endInput()` 调 `flush(END_INPUT_CHECKPOINT_ID)`。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/IcebergFilesCommitter.java`

**修改目的**：按 checkpointId 分组管理 WriteResult，避免多 checkpoint 合并成一次 commit。

**工作逻辑**：

- 类签名由 `OneInputStreamOperator<WriteResult, Void>` 改为 `OneInputStreamOperator<FlinkWriteResult, Void>`。
- 字段 `List<WriteResult> writeResultsOfCurrentCkpt` 替换为 `Map<Long, List<WriteResult>> writeResultsSinceLastSnapshot`。
- `processElement`：拿到 `FlinkWriteResult`，按 `checkpointId()` `computeIfAbsent` 进 map 对应 list。
- `snapshotState(checkpointId)`：从原来的 `dataFilesPerCheckpoint.put(checkpointId, writeToManifest(checkpointId))` 改为 `writeToManifestUptoLatestCheckpoint(checkpointId)`；不再 `clear()` 单个 list（清空逻辑下移到新方法内）。
- 新增 `writeToManifestUptoLatestCheckpoint(long checkpointId)`：
  - 若 map 不含 `checkpointId`，往 `dataFilesPerCheckpoint` put `EMPTY_MANIFEST_DATA`（占位）；
  - 遍历 map 所有 entry，对每个 `(ckptId, list)` 调 `writeToManifest(ckptId, list)` 写入 `dataFilesPerCheckpoint`；
  - 末尾 `writeResultsSinceLastSnapshot.clear()`。
- `endInput()`：使用 `END_INPUT_CHECKPOINT_ID` 替代硬编码 `Long.MAX_VALUE`，并改调 `writeToManifestUptoLatestCheckpoint`，随后 `commitUpToCheckpoint`。
- `writeToManifest` 签名改为 `writeToManifest(long checkpointId, List<WriteResult> writeResults)`，参数化 list，移除原本从字段读取的逻辑与空检查（空检查上移到 `writeToManifestUptoLatestCheckpoint` 的占位分支）。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/FlinkSink.java`

**修改目的**：把流图里的类型从 `WriteResult` 改为 `FlinkWriteResult`。

**工作逻辑**：

- 移除 `import org.apache.iceberg.io.WriteResult;`。
- `appendWriter` 与 `appendCommitter` 的 `SingleOutputStreamOperator<WriteResult>` 全部改为 `SingleOutputStreamOperator<FlinkWriteResult>`；`transform` 的 `TypeInformation.of(WriteResult.class)` 改为 `TypeInformation.of(FlinkWriteResult.class)`。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/sink/TestCompressionSettings.java`

**修改目的**：跟随主代码类型变更。

**工作逻辑**：把 `OneInputStreamOperatorTestHarness<RowData, WriteResult>` 改为 `<RowData, FlinkWriteResult>`，并相应调整方法签名与 import。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/sink/TestIcebergFilesCommitter.java`

**修改目的**：跟随类型变更并新增针对 aborted checkpoint + upsert 的回归测试。

**工作逻辑**：

- 全文把 `OneInputStreamOperatorTestHarness<WriteResult, Void>` 改为 `<FlinkWriteResult, Void>`。
- 工具方法 `of(DataFile)` 改为 `of(long checkpointId, DataFile)`，返回 `new FlinkWriteResult(checkpointId, WriteResult.builder().addDataFiles(dataFile).build())`。
- 多处 `harness.processElement(of(dataFile), ...)` 调整为 `of(checkpointId, dataFile)`，并把 `checkpointId` 的自增时机调整到 element 之前（保证 element 与随后的 `snapshot(checkpointId)` 一致）。
- `testBoundedStream` 中把硬编码 `Long.MAX_VALUE` 替换为 `IcebergStreamWriter.END_INPUT_CHECKPOINT_ID`，提升可读性。
- **新增 `testCommitMultipleCheckpointsForV2Table`**：核心回归测试。模拟 upsert V2 表场景：同一行被连续 upsert 3 次（3 个 prepareSnapshotPreBarrier 各发一次 `FlinkWriteResult`，分别属于 checkpoint 11/12/13），但只做一次 `snapshot(13)` + `notifyOfCompletedCheckpoint(13)`。注释说明：修复前所有文件会合并进一个 Iceberg commit，导致同一行 3 个版本都存活（重复数据）；修复后会做 3 次独立 Iceberg commit（`assertThat(table.snapshots()).hasSize(3)`），最终表里只有最新版本（id=1 "aaa3", id=2 "bbb3"）。
- 其他既有测试用例中，element 自增 checkpoint 的位置做了对齐调整，以匹配新的"`element` 自带 checkpointId"模型。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/sink/TestIcebergStreamWriter.java`

**修改目的**：跟随类型变更。

**工作逻辑**：

- `OneInputStreamOperatorTestHarness<RowData, WriteResult>` 全部改为 `<RowData, FlinkWriteResult>`。
- 新增辅助方法 `private static List<WriteResult> getWriteResults(List<FlinkWriteResult> flinkWriteResults)`，用 stream 把 `FlinkWriteResult` 拆回 `WriteResult` 以便继续用 `WriteResult.builder().addAll(...)` 做断言。
- 多处 `WriteResult.builder().addAll(testHarness.extractOutputValues())` 改为先 `getWriteResults(...)` 再 addAll。

## 小结

- **成效**：彻底修复了 V2 表 + upsert writer 在 aborted checkpoint 场景下的重复数据 bug。每个 checkpoint 的写文件现会以独立 Iceberg commit 提交，equality delete 跨 checkpoint 正确生效。回归测试 `testCommitMultipleCheckpointsForV2Table` 覆盖该场景。
- **影响范围**：仅 flink sink 模块。改动了 writer → committer 之间的数据流类型（`WriteResult` → `FlinkWriteResult`），属于 operator 间内部协议变更，不影响用户 API。
- **回迁到 1.4.x 的注意事项**：
  - 这是数据正确性 bug 修复，**强烈建议回迁**到 1.4.x（前提是 1.4.x 的 Flink 集成也支持 V2 表 + upsert/equality-delete 写入）。
  - 回迁时需把 `FlinkWriteResult`、`IcebergStreamWriter`、`IcebergFilesCommitter`、`FlinkSink` 以及相关测试一起 cherry-pick；注意 1.4.x 可能同时维护 v1.17/v1.18/v1.19/v1.20 多个 flink 版本目录，需要逐目录同步。
  - 该改动属于内部 operator 协议变更，不涉及用户 API 兼容性；但从旧版本升级时，作业重启会从最近 checkpoint 恢复，state 中的 `dataFilesPerCheckpoint` 结构未变（仍是 `Map<Long, byte[]>`），可正常恢复。
  - 注意 `END_INPUT_CHECKPOINT_ID = Long.MAX_VALUE` 与原硬编码值一致，保证 bounded 作业 endInput 行为不变。
  - 1.4.x 若已有此 bug 的 hotfix 在飞，需避免重复回迁。
