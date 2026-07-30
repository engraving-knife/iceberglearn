# 提交 4024：Flink: Remove converted equality deletes for same-branch conversion (#17189)

## 提交信息

- **序号**：4024 / 4088
- **哈希**：9b64b317c6f91de8bdaf4bc6cd9aab15945dbd37
- **短哈希**：9b64b317c
- **日期**：2026-07-13 17:12:30 +0200
- **作者**：Maximilian Michels
- **提交说明**：Flink: Remove converted equality deletes for same-branch conversion (#17189)
- **PR/Issue**：#17189

## 总体目的

本提交修复 `ConvertEqualityDeletes` 在"同分支就地转换"（staging branch == target branch）场景下的一个遗漏：转换器将 equality delete 解析为 deletion vector（DV）并提交后，原本的 equality delete 文件仍然留在分支上，没有被移除。

这会导致读者在读取时既加载和应用 equality delete，又加载和应用 DV，造成：
1. 读取性能浪费——equality delete 和 DV 做重复的删除工作。
2. 潜在的正确性问题——重复应用删除可能产生意外。

本提交在同分支转换场景下，让 committer 通过 `rowDelta.removeDeletes(eqDeleteFile)` 移除已转换为 DV 的 equality delete 文件，使读者停止加载它们。同时新增 `removedEqDeleteNum` 指标统计移除的 equality delete 数量。对于不同分支转换（staging != target），equality delete 保留在 staging 分支不变（原行为）。

## 如何达成设计目的

1. `EqualityConvertPlan` record 新增 `eqDeleteFiles` 字段，携带本周期解析的 equality delete 文件列表（同分支时非空，不同分支时为空）。
2. `EqualityConvertPlanner` 在构建 plan 时传入 `inputs.eqDeleteFiles()`。
3. `EqualityConvertCommitter.applyCommit` 中：
   - 同分支（`stagingOnTargetBranch`）：遍历 `planResult.eqDeleteFiles()` 调用 `rowDelta.removeDeletes(eqDeleteFile)` 移除已转换的 equality delete。
   - 不同分支：保持原行为（`addStagingDeletes` 把 staging DV 加到 target）。
4. 新增 `removedEqDeleteNum` Counter 指标，日志中增加移除数量信息。
5. 测试覆盖同分支移除、不同分支保留、指标统计等场景。

## 修改详情

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/EqualityConvertCommitter.java` (+20/-6 lines)

**修改目的**：同分支转换时移除 equality delete，新增指标。

**工作逻辑**：
- 新增 `REMOVED_EQ_DELETE_NUM_METRIC` 常量和 `removedEqDeleteNumCounter`。
- `applyCommit` 中：
  ```java
  if (stagingOnTargetBranch) {
    // 就地转换：equality delete 已在 target 分支，其行已被 DV 覆盖，移除它们
    for (DeleteFile eqDeleteFile : planResult.eqDeleteFiles()) {
      rowDelta.removeDeletes(eqDeleteFile);
    }
  } else {
    // 不同分支：staging 上的 DV 加到 target
    addStagingDeletes(rowDelta, referencedDataFiles, planResult.stagingDVFiles());
  }
  ```
- `removedEqDeletes = stagingOnTargetBranch ? planResult.eqDeleteFiles().size() : 0`，更新计数器和日志。
- 新增 `@VisibleForTesting long removedEqDeleteNum()` getter。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/EqualityConvertPlan.java` (+5/-0 lines)

**修改目的**：plan record 新增 `eqDeleteFiles` 字段。

**工作逻辑**：
```java
public record EqualityConvertPlan(
    List<DataFile> dataFiles,
    List<DeleteFile> stagingDVFiles,
    List<DeleteFile> eqDeleteFiles,  // 新增
    long stagingSnapshotId,
    ...)
```
`noOp` 工厂方法用 `ImmutableList.of()` 填充。Javadoc 说明：同分支时由 committer 移除，不同分支时为空（eq delete 保留在 staging）。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/EqualityConvertPlanner.java` (+1/-0 lines)

**修改目的**：构建 plan 时传入 eqDeleteFiles。

**工作逻辑**：
```java
new EqualityConvertPlan(
    inputs.newDataFiles(),
    inputs.stagingDVFiles(),
    inputs.eqDeleteFiles(),  // 新增
    stagingSnapshot.snapshotId(),
    ...)
```

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/maintenance/api/TestConvertEqualityDeletesE2E.java` (+26/-0 lines)

**修改目的**：E2E 测试验证同分支转换后 equality delete 被移除。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/TestEqualityConvertCommitter.java` (+109/-0 lines)

**修改目的**：单元测试 committer 的移除逻辑和指标。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/TestEqualityConvertDVWriter.java` (+13/-6 lines)

**修改目的**：适配 plan 新增字段。

## 总结

本提交修复了 `ConvertEqualityDeletes` 同分支就地转换场景下 equality delete 未被移除的问题，通过 `rowDelta.removeDeletes` 在提交 DV 时同时移除已转换的 equality delete 文件，避免读者重复应用删除。区分同分支（移除 eq delete）和不同分支（保留 staging 上的 eq delete）两种语义，并新增 `removedEqDeleteNum` 指标。这提升了就地转换模式的正确性和读取效率。该提交随后在 4026 被 backport。
