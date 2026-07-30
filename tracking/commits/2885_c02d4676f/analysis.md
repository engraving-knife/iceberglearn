# 提交 2885：Core: Classify RowDelta with data files only as APPEND (#14581)

## 提交信息

- **序号**：2885 / 4088
- **哈希**：c02d4676f38b834d6c672bb53abd34b07ebfddb9
- **短哈希**：c02d4676f
- **日期**：2025-11-18 07:57:54 +0100
- **作者**：Maximilian Michels
- **提交说明**：Core: Classify RowDelta with data files only as APPEND (#14581)
- **PR/Issue**：#14581

## 总体目的

`RowDelta` 是 Iceberg 中用于同时添加数据文件和删除文件的提交操作（如 Flink 的 upsert 场景）。`BaseRowDelta.operation()` 方法负责确定快照的操作类型（`DataOperations.APPEND`、`DELETE`、`OVERWRITE` 等），该类型记录在快照元数据中，供查询引擎和工具识别操作性质。

此前，当 RowDelta 只添加数据文件（`addRows`）而不添加删除文件（`addDeletes`）也不删除数据文件（`removeRows`）时，操作类型不会被分类为 `APPEND`。这种情况下，操作类型会落入默认分支，可能被标记为 `OVERWRITE` 或其他类型，这与实际语义不符——仅追加数据文件的提交应该是 `APPEND` 操作。

正确标记操作类型很重要，因为：
1. 引擎可能根据操作类型优化查询计划（如 `APPEND` 操作不会使之前的快照失效）。
2. 审计和监控工具依赖操作类型来分类和理解表变更。
3. 增量读取（incremental scan）依赖操作类型来识别新增数据。

## 如何达成设计目的

在 `BaseRowDelta.operation()` 方法的判断链中，在最前面新增一个条件：如果添加了数据文件且没有添加删除文件且没有删除数据文件，则返回 `DataOperations.APPEND`。这个条件被放在所有其他条件之前，确保纯追加场景优先匹配。

同时新增测试验证这一行为，并在已有测试中补充操作类型断言。

## 修改详情

### `core/src/main/java/org/apache/iceberg/BaseRowDelta.java` (+4/-0 lines)

**修改目的**：为纯数据文件追加的 RowDelta 添加 APPEND 操作分类。

**工作逻辑**：在 `operation()` 方法开头新增：
```java
if (addsDataFiles() && !addsDeleteFiles() && !deletesDataFiles()) {
  return DataOperations.APPEND;
}
```
该条件优先于已有的 `DELETE` 和 `OVERWRITE` 判断，确保仅追加数据文件时返回 `APPEND`。

### `core/src/test/java/org/apache/iceberg/TestRowDelta.java` (+47/-0 lines)

**修改目的**：验证纯追加操作分类为 APPEND，以及添加数据文件同时删除数据文件分类为 OVERWRITE。

**工作逻辑**：
- `addOnlyDataFilesProducesAppendOperation`：仅 `addRows(FILE_A).addRows(FILE_B)`，验证快照操作为 `APPEND`。
- `testAddRowsRemoveDataFile`：先添加 FILE_A，再 `addRows(FILE_B).removeRows(FILE_A)`，验证操作为 `OVERWRITE`（因为同时添加和删除数据文件）。
- 在多个已有测试中补充 `assertThat(...operation()).isEqualTo(DataOperations.OVERWRITE)` 断言，验证删除数据文件的场景操作类型正确。

## 总结

该提交修复了 `BaseRowDelta.operation()` 的操作分类逻辑，使仅添加数据文件的 RowDelta 提交被正确标记为 `APPEND` 操作。此前这类提交可能被错误标记为其他操作类型。正确的操作分类对查询优化、增量读取和审计都具有重要意义。后续提交 2885 会在 Flink DynamicIcebergSink 中添加对应测试，验证该修复的实际效果。
