# 提交 1120：Flink: Backport PR #10526 to v1.18 and v1.20 (#11018)

## 提交信息

- **序号**：1120 / 4088
- **哈希**：e8c614878dc8f5fd24ec1bbdbd325a7a5d9bfd3f
- **短哈希**：e8c614878
- **日期**：2024-08-31（Sat Aug 31 02:53:47 2024 +0800）
- **作者**：Qishang Zhong <zhongqishang@gmail.com>
- **提交说明**：Flink: Backport PR #10526 to v1.18 and v1.20 (#11018)
- **PR/Issue**：#11018（回迁自 #10526）

## 总体目的

本提交把 main 分支上 PR #10526 的修复回迁到 Flink 1.18 与 1.20 两个版本模块（Flink 1.19 已在 main 上随 #10526 修复）。该修复解决 Flink Iceberg Sink 在 V2 表 upsert 场景下因 checkpoint 失败重试导致的"重复行"数据正确性 bug。

**Bug 背景**：Flink Iceberg Sink 的写入流程是 `IcebergStreamWriter`（每个并行 writer）→ `IcebergFilesCommitter`（单并行度 committer）。Writer 在 `prepareSnapshotPreBarrier(checkpointId)` 时 flush 当前 writer 产出 `WriteResult` 发给 committer；committer 在 `snapshotState(checkpointId)` 时把累积的 `WriteResult` 写入 manifest 并提交到 Iceberg 表。

**问题**：原 `IcebergFilesCommitter` 用 `List<WriteResult> writeResultsOfCurrentCkpt` 缓冲"自上次 snapshot 以来收到的所有 WriteResult"，但**这个缓冲不区分 WriteResult 来自哪个 checkpoint**。考虑以下场景（V2 表 upsert，使用 equality delete）：

1. Checkpoint N 触发，writer 发出 `WriteResult_N`（含 data file + delete file，delete 了 row X）；
2. Checkpoint N 因非 Iceberg 原因失败（如网络抖动），未完成提交；
3. 上游重发，同一行 X 在 checkpoint N+1 又被 update（产生新的 `WriteResult_{N+1}` 含新 data file + 新 delete file）；
4. Checkpoint N+1 成功，committer 在 `snapshotState(N+1)` 时把 `writeResultsOfCurrentCkpt`（含 `WriteResult_N` 与 `WriteResult_{N+1}`）**合并成一次 Iceberg commit**。

由于两次 WriteResult 的 delete file 都基于"上一次 commit 后的状态"生成，但被合并成单次 commit 后，delete file 之间互相不可见（Iceberg 单次 commit 内的 delete 不会作用于同 commit 内的 data file），导致 row X 的旧版本未被删除，**产生重复行**。

**修复思路**：让 committer 知道每个 WriteResult 属于哪个 checkpoint，在 `snapshotState(checkpointId)` 时**按 checkpoint 分组分别写 manifest**，从而保留每次 checkpoint 的独立 commit 边界。即使 checkpoint N 失败、N+1 成功，committer 也会为 N 与 N+1 分别生成 manifest 条目，让 Iceberg 在 commit 时按 checkpoint 顺序应用 delete，避免重复行。

## 如何达成设计目的

通过引入一个新的包装类型 `FlinkWriteResult`，把 `checkpointId` 与原 `WriteResult` 绑定，让 `checkpointId` 信息从 writer 流向 committer；committer 据此按 checkpoint 分组写 manifest。具体改动覆盖 Flink 1.18 与 1.20 两个模块，每个模块 7 个文件（4 主代码 + 3 测试）：

1. **新增 `FlinkWriteResult`**：`Serializable` 包装类，含 `checkpointId` 与 `writeResult`。
2. **`IcebergStreamWriter`**：输出类型从 `WriteResult` 改为 `FlinkWriteResult`；`flush()` 接收 `checkpointId` 参数，emit 时包装成 `FlinkWriteResult(checkpointId, result)`；`prepareSnapshotPreBarrier` 传当前 `checkpointId`，`endInput` 传 `END_INPUT_CHECKPOINT_ID = Long.MAX_VALUE`。
3. **`IcebergFilesCommitter`**：输入类型从 `WriteResult` 改为 `FlinkWriteResult`；缓冲从 `List<WriteResult>` 改为 `Map<Long, List<WriteResult>>`（按 checkpointId 分组）；`snapshotState` 调用新方法 `writeToManifestUptoLatestCheckpoint(checkpointId)`，遍历 map 为每个 checkpoint 写一个 manifest；`writeToManifest` 签名改为接收 `List<WriteResult>` 参数。
4. **`FlinkSink`**：把 `SingleOutputStreamOperator<WriteResult>` 改为 `SingleOutputStreamOperator<FlinkWriteResult>`，相应调整 `appendWriter` 与 `appendCommitter` 签名。
5. **测试**：`TestIcebergFilesCommitter`、`TestIcebergStreamWriter`、`TestCompressionSettings` 调整 harness 类型与 `of(...)` 工厂方法，新增 `testCommitMultipleCheckpointsForV2Table` 测试覆盖上述 bug 场景。

两份代码（v1.18 与 v1.20）改动完全一致。

## 修改详情

### `flink/v1.18/flink/src/main/java/org/apache/iceberg/flink/sink/FlinkWriteResult.java`（新文件，40 行）

**修改目的**：定义 `WriteResult` + `checkpointId` 的包装类型。

**工作逻辑**：

```java
public class FlinkWriteResult implements Serializable {
  private final long checkpointId;
  private final WriteResult writeResult;

  public FlinkWriteResult(long checkpointId, WriteResult writeResult) { ... }
  public long checkpointId() { return checkpointId; }
  public WriteResult writeResult() { return writeResult; }
}
```

实现 `Serializable` 以支持 Flink 算子状态序列化；不可变值对象。

### `flink/v1.18/flink/src/main/java/org/apache/iceberg/flink/sink/IcebergStreamWriter.java`

**修改目的**：emit 时携带 checkpointId。

**工作逻辑**：

1. 类签名从 `AbstractStreamOperator<WriteResult>` / `OneInputStreamOperator<T, WriteResult>` 改为 `AbstractStreamOperator<FlinkWriteResult>` / `OneInputStreamOperator<T, FlinkWriteResult>`。
2. 新增常量 `static final long END_INPUT_CHECKPOINT_ID = Long.MAX_VALUE;`，作为 `endInput` 时 emit 的 checkpointId。
3. `prepareSnapshotPreBarrier(long checkpointId)` 改为调用 `flush(checkpointId)`（原 `flush()`）。
4. `endInput()` 改为调用 `flush(END_INPUT_CHECKPOINT_ID)`。
5. `flush()` 签名改为 `flush(long checkpointId)`，emit 时：

```java
output.collect(new StreamRecord<>(new FlinkWriteResult(checkpointId, result)));
```

### `flink/v1.18/flink/src/main/java/org/apache/iceberg/flink/sink/IcebergFilesCommitter.java`

**修改目的**：按 checkpoint 分组写 manifest，避免合并多 checkpoint 的 WriteResult。

**工作逻辑**：

1. 类签名从 `OneInputStreamOperator<WriteResult, Void>` 改为 `OneInputStreamOperator<FlinkWriteResult, Void>`。
2. 缓冲字段从 `List<WriteResult> writeResultsOfCurrentCkpt` 改为 `Map<Long, List<WriteResult>> writeResultsSinceLastSnapshot`。
3. `processElement(StreamRecord<FlinkWriteResult> element)`：

```java
FlinkWriteResult flinkWriteResult = element.getValue();
List<WriteResult> writeResults =
    writeResultsSinceLastSnapshot.computeIfAbsent(
        flinkWriteResult.checkpointId(), k -> Lists.newArrayList());
writeResults.add(flinkWriteResult.writeResult());
```

按 checkpointId 分组累积。

4. `snapshotState(checkpointId)` 中原 `dataFilesPerCheckpoint.put(checkpointId, writeToManifest(checkpointId));` 改为 `writeToManifestUptoLatestCheckpoint(checkpointId);`，移除 `writeResultsOfCurrentCkpt.clear()`。

5. 新增 `writeToManifestUptoLatestCheckpoint(long checkpointId)`：

```java
if (!writeResultsSinceLastSnapshot.containsKey(checkpointId)) {
  dataFilesPerCheckpoint.put(checkpointId, EMPTY_MANIFEST_DATA);
}
for (Map.Entry<Long, List<WriteResult>> writeResultsOfCheckpoint :
    writeResultsSinceLastSnapshot.entrySet()) {
  dataFilesPerCheckpoint.put(
      writeResultsOfCheckpoint.getKey(),
      writeToManifest(writeResultsOfCheckpoint.getKey(), writeResultsOfCheckpoint.getValue()));
}
writeResultsSinceLastSnapshot.clear();
```

- 若当前 checkpoint 无 WriteResult（如所有 writer 空闲），写入 `EMPTY_MANIFEST_DATA` 占位；
- 遍历 map，为每个 checkpointId 单独写一个 manifest，存入 `dataFilesPerCheckpoint`（key=checkpointId, value=manifest bytes）；
- 清空缓冲。

6. `endInput()`：原 `currentCheckpointId = Long.MAX_VALUE` 改为 `currentCheckpointId = IcebergStreamWriter.END_INPUT_CHECKPOINT_ID`，调用 `writeToManifestUptoLatestCheckpoint(currentCheckpointId)`。

7. `writeToManifest` 签名从 `writeToManifest(long checkpointId)` 改为 `writeToManifest(long checkpointId, List<WriteResult> writeResults)`，移除内部 `if (writeResultsOfCurrentCkpt.isEmpty()) return EMPTY_MANIFEST_DATA;` 检查（已由调用方处理）。

### `flink/v1.18/flink/src/main/java/org/apache/iceberg/flink/sink/FlinkSink.java`

**修改目的**：把 writer 流输出类型改为 `FlinkWriteResult`。

**工作逻辑**：

1. 移除 `import org.apache.iceberg.io.WriteResult;`。
2. `SingleOutputStreamOperator<WriteResult> writerStream` 改为 `SingleOutputStreamOperator<FlinkWriteResult>`（出现在 `appendWriter` 返回类型、`appendCommitter` 参数类型、内部变量声明）。
3. `TypeInformation.of(WriteResult.class)` 改为 `TypeInformation.of(FlinkWriteResult.class)`。

### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/sink/TestIcebergFilesCommitter.java`

**修改目的**：调整测试 harness 类型 + 新增 V2 表多 checkpoint 合并场景测试。

**工作逻辑**：

1. 所有 `OneInputStreamOperatorTestHarness<WriteResult, Void>` 改为 `OneInputStreamOperatorTestHarness<FlinkWriteResult, Void>`。
2. 工厂方法 `of(DataFile)` 改为 `of(long checkpointId, DataFile)`：

```java
private FlinkWriteResult of(long checkpointId, DataFile dataFile) {
  return new FlinkWriteResult(checkpointId, WriteResult.builder().addDataFiles(dataFile).build());
}
```

3. 所有调用 `of(dataFile)` 处改为 `of(checkpointId, dataFile)`，把 checkpointId 显式传入；同时把原本 `++checkpointId` 在 `processElement` 与 `snapshot` 两处分别前置/后置的不一致统一为：先 `++checkpointId` 再 `processElement`，最后 `snapshot(checkpointId, ...)`。
4. `createStreamSink` 返回类型与 `TestOperatorFactory` 实现的 `OneInputStreamOperatorFactory` 泛型改为 `FlinkWriteResult`。
5. **新增 `testCommitMultipleCheckpointsForV2Table` 测试**（核心 bug 验证）：

```java
// 模拟 V2 表 upsert 场景：3 次 checkpoint 都含 insert + equality delete
for (int i = 1; i <= 3; i++) {
  insert1 = SimpleDataUtil.createInsert(1, "aaa" + i);
  insert2 = SimpleDataUtil.createInsert(2, "bbb" + i);
  DataFile dataFile = writeDataFile("data-file-" + i, ImmutableList.of(insert1, insert2));
  DeleteFile deleteFile = writeEqDeleteFile(appenderFactory, "delete-file-" + i, ImmutableList.of(insert1, insert2));
  harness.processElement(
      new FlinkWriteResult(++checkpoint,
          WriteResult.builder().addDataFiles(dataFile).addDeleteFiles(deleteFile).build()),
      ++timestamp);
}
harness.snapshot(checkpoint, ++timestamp);
harness.notifyOfCompletedCheckpoint(checkpoint);
SimpleDataUtil.assertTableRows(table, ImmutableList.of(insert1, insert2), branch);
assertMaxCommittedCheckpointId(jobId, operatorId, checkpoint);
assertFlinkManifests(0);
assertThat(table.snapshots()).hasSize(3);
```

关键断言 `assertThat(table.snapshots()).hasSize(3)`：3 次 checkpoint 应产生 3 个独立 Iceberg snapshot（而非合并成 1 个），证明每个 checkpoint 单独 commit。修复前会合并成 1 个 commit 并产生重复行。

### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/sink/TestIcebergStreamWriter.java`

**修改目的**：调整 harness 类型与 `extractOutputValues` 处理。

**工作逻辑**：

1. 所有 `OneInputStreamOperatorTestHarness<RowData, WriteResult>` 改为 `OneInputStreamOperatorTestHarness<RowData, FlinkWriteResult>`。
2. 新增辅助方法：

```java
private static List<WriteResult> getWriteResults(List<FlinkWriteResult> flinkWriteResults) {
  return flinkWriteResults.stream()
      .map(FlinkWriteResult::writeResult)
      .collect(Collectors.toList());
}
```

3. 所有 `WriteResult.builder().addAll(testHarness.extractOutputValues()).build()` 改为 `WriteResult.builder().addAll(getWriteResults(testHarness.extractOutputValues())).build()`，从 `FlinkWriteResult` 列表中解包出 `WriteResult`。

### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/sink/TestCompressionSettings.java`

**修改目的**：调整 harness 类型。

**工作逻辑**：`OneInputStreamOperatorTestHarness<RowData, WriteResult>` 改为 `OneInputStreamOperatorTestHarness<RowData, FlinkWriteResult>`（出现在 `createIcebergStreamWriter` 返回类型与 `appenderProperties` 内 try-with-resources）。

### Flink 1.20 模块（7 个文件）

`flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/` 下的 `FlinkSink.java`、`FlinkWriteResult.java`（新文件）、`IcebergFilesCommitter.java`、`IcebergStreamWriter.java`，以及 `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/sink/` 下的 `TestCompressionSettings.java`、`TestIcebergFilesCommitter.java`、`TestIcebergStreamWriter.java`，改动内容与上述 v1.18 完全一致（同一份代码回迁到 v1.20）。

## 小结

- **成效**：修复 Flink Iceberg Sink 在 V2 表 upsert + checkpoint 失败重试场景下的"重复行"数据正确性 bug。通过引入 `FlinkWriteResult` 包装类型把 `checkpointId` 从 writer 传到 committer，committer 按 checkpoint 分组写 manifest，保留每次 checkpoint 的独立 commit 边界，避免多 checkpoint 的 WriteResult 被合并成单次 Iceberg commit 而导致 delete 不可见。新增 `testCommitMultipleCheckpointsForV2Table` 测试覆盖该场景，断言 3 次 checkpoint 产生 3 个独立 snapshot。Flink 1.18 与 1.20 两个模块同步回迁。
- **影响范围**：14 个文件、598 增 250 删，覆盖 Flink 1.18 与 1.20 的 sink 主代码（4 文件/模块）与测试（3 文件/模块）；改动涉及 sink 写入路径的核心数据流类型变更（`WriteResult` → `FlinkWriteResult`），但语义保持兼容（仅追加 checkpointId，不改变 WriteResult 内容）。
- **回迁到 1.4.x 的注意事项**：这是数据正确性 bug 修复，**应优先考虑回迁到 1.4.x**（若 1.4.x 的 Flink 模块覆盖 1.18/1.20 版本且存在同一 bug）。回迁时需注意：
  1. **Flink 版本覆盖**：确认 1.4.x 维护哪些 Flink 版本模块（1.4.x 可能覆盖 1.17/1.18/1.19/1.20 中的部分子集）；本提交仅回迁到 1.18 与 1.20，1.17 若存在同样 bug 需单独处理。
  2. **API 兼容性**：`FlinkWriteResult` 是新公共类，但 sink 内部使用，对用户 API 无影响；`IcebergStreamWriter.END_INPUT_CHECKPOINT_ID` 是新 package-private 常量。
  3. **状态兼容性**：`writeResultsOfCurrentCkpt`（List）改为 `writeResultsSinceLastSnapshot`（Map），是算子内部缓冲字段（非 checkpointed state），无状态迁移问题；`dataFilesPerCheckpoint` 的 key/value 语义不变（仍是 checkpointId → manifest bytes），从旧版本升级无兼容性破坏。
  4. **测试依赖**：`testCommitMultipleCheckpointsForV2Table` 依赖 `writeEqDeleteFile`、`FlinkAppenderFactory`、`SimpleDataUtil.createInsert` 等辅助方法，回迁测试时需确认 1.4.x 已具备这些工具。
  5. **配套回迁**：本修复与 main 上 PR #10526 同源，1.4.x 若已含 #10526 对 1.19 的修复，则仅需为 1.18/1.20 补回迁；若 1.4.x 完全未含该修复，则需一次性回迁 1.18/1.19/1.20 三个模块。
  整体属于高优先级数据正确性修复，建议回迁并运行 `TestIcebergFilesCommitter#testCommitMultipleCheckpointsForV2Table` 验证。
