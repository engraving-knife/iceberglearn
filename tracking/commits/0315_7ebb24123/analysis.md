# 提交 0315：Core, Spark: Correct the delete record count for PartitionTable (#9389)

## 提交信息

- **序号**：0315
- **哈希**：7ebb241232e7a88f30257fa657f44cfe268c8a95
- **短哈希**：7ebb24123
- **日期**：2024-01-02 10:24:32 -0600
- **作者**：Xianyang Liu
- **提交说明**：Core, Spark: Correct the delete record count for PartitionTable (#9389)
- **PR/Issue**：#9389

## 总体目的

本提交修复了 `PartitionsTable`（partitions 元数据表）在汇总每个分区的 position delete 与 equality delete 记录数时的累积 bug。原代码在遍历分区的 delete 文件时，对 `posDeleteRecordCount` 与 `eqDeleteRecordCount` 使用了赋值操作 `=` 而非累加 `+=`，导致当一个分区有多个 position delete 文件或多个 equality delete 文件时，最终记录数只反映最后一个文件的 recordCount，而非所有 delete 文件 recordCount 的总和。`PartitionsTable` 是 Iceberg 暴露给用户的元数据表之一（通过 `SELECT * FROM <table>.partitions` 查询），其每行汇总一个分区的统计信息（数据文件数、数据记录数、数据文件大小、position/equality delete 文件数与记录数、spec_id、最近更新时间与快照 ID 等），用户依赖这些统计做容量规划与数据质量监控——记录数被低估会误导下游决策。

修复非常精准：将 `case POSITION_DELETES` 分支的 `this.posDeleteRecordCount = file.recordCount()` 改为 `this.posDeleteRecordCount += file.recordCount()`；`case EQUALITY_DELETES` 分支的 `this.eqDeleteRecordCount = file.recordCount()` 改为 `this.eqDeleteRecordCount += file.recordCount()`。两行修改即完成核心 bug 修复。注意同分支内的 `posDeleteFileCount`/`eqDeleteFileCount` 本就用了 `+= 1` 累加（文件数一直是正确的），只有 record count 写错了。

为验证修复，提交同步增强了 Spark 3.3/3.4/3.5 三个版本的 `TestIcebergSourceTablesBase` 测试：原本每个分区只写 1 条数据记录、1 个 position delete 文件、1 个 equality delete 文件（这种场景下 `=` 与 `+=` 行为无差异，无法暴露 bug）；改为每个分区写 3 条数据记录、为同一分区写 2 个 position delete 文件（pos 0 与 pos 1）和 2 个 equality delete 文件（data 值 "d" 与 "f"），从而能在断言中验证 `position_delete_record_count`/`equality_delete_record_count` 正确累加为 2。

## 如何达成设计目的

修复通过将赋值改为累加实现，使每个分区的 delete 记录数正确反映该分区所有 delete 文件的 recordCount 之和。测试增强通过扩展 `writePosDeleteFile`/`writeEqDeleteFile` 辅助方法为可参数化版本（`writePosDeleteFile(table, pos)` 接受删除位置、`writeEqDeleteFile(table, dataValue)` 接受删除值），并在测试中分别调用两次生成两个 delete 文件提交到同一 `RowDelta`，使断言能区分"单文件 1 条"与"双文件累加 2 条"两种情况，从而捕获原赋值 bug。

## 修改详情

### `core/src/main/java/org/apache/iceberg/PartitionsTable.java`

**修改目的**：修复 partitions 元数据表汇总 delete 记录数时的累加 bug。

**工作逻辑**：在 `PartitionsTable` 内部聚合分区的 `Partition` 数据类遍历文件的部分（约 312-320 行），switch 分支 `case POSITION_DELETES` 中 `this.posDeleteRecordCount = file.recordCount()` 改为 `this.posDeleteRecordCount += file.recordCount()`；`case EQUALITY_DELETES` 中 `this.eqDeleteRecordCount = file.recordCount()` 改为 `this.eqDeleteRecordCount += file.recordCount()`。其余逻辑（`posDeleteFileCount += 1`、`eqDeleteFileCount += 1`、`specId` 赋值）保持不变。这是两行最小修复。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestIcebergSourceTablesBase.java`（及 3.3/3.4 同名文件）

**修改目的**：增强 partitions 元数据表测试，使其能暴露 delete 记录数累加 bug。

**工作逻辑**：
- **数据量扩展**：原 `df1`/`df2` 各 1 条记录扩展为各 3 条（`df1`: `(1,"a"), (1,"b"), (1,"c")` 属分区 id=1；`df2`: `(2,"d"), (2,"e"), (2,"f")` 属分区 id=2），相应断言 `record_count` 从 `1L` 改为 `3L`。
- **Position delete 多文件**：原 `writePosDeleteFile(table)` 单次调用改为 `writePosDeleteFile(table, 0)` + `writePosDeleteFile(table, 1)` 两次调用，两个 delete 文件通过 `table.newRowDelta().addDeletes(deleteFile1).addDeletes(deleteFile2).commit()` 提交到同一快照。断言对应分区 `position_delete_record_count` 从 `1L` 改为 `2L`、`position_delete_file_count` 从 `1` 改为 `2`。
- **Equality delete 多文件**：原 `writeEqDeleteFile(table)` 单次调用改为 `writeEqDeleteFile(table, "d")` + `writeEqDeleteFile(table, "f")` 两次调用，两个 delete 文件提交到同一 `RowDelta`。断言对应分区 `equality_delete_record_count` 从 `1L` 改为 `2L`、`equality_delete_file_count` 从 `1` 改为 `2`。
- **辅助方法改造**：
  - `writePosDeleteFile(Table table)` 重载为 `writePosDeleteFile(Table table, long pos)`，将原硬编码的 `delete.set(dataFile.path(), 0L, null)` 改为 `delete.set(dataFile.path(), pos, null)`，原无参版本作为委托保留（默认 pos=0）。该方法仍以 `Iterables.getFirst(table.currentSnapshot().addedDataFiles(table.io()), null)` 取首个数据文件，并用其 `specId` 与 `partition` 作为 delete 文件的分区。
  - `writeEqDeleteFile(Table table)` 改为 `writeEqDeleteFile(Table table, String dataValue)`：原 select `"id"` 字段并 `delete.copy("id", 1)` 改为 select `"data"` 字段并 `delete.copy("data", dataValue)`；该方法显式以 `org.apache.iceberg.TestHelpers.Row.of(1)` 指定分区 id=1，使 equality delete 文件归属分区 1（与 position delete 落在分区 2 区分开，使两个分区的 delete 计数独立验证）。
- 三个 Spark 版本（3.3/3.4/3.5）的 `TestIcebergSourceTablesBase` 同步应用相同改动（46-47 行变更），保持版本间测试一致。

## 小结

本次提交修复了 `PartitionsTable` 分区级 delete 记录数汇总的累加 bug——两行核心改动（`=` → `+=`），却影响用户可见的元数据准确性。配套测试增强通过"每分区多 delete 文件"场景使 bug 可被自动化测试捕获，避免回归。测试还顺便将每分区数据量从 1 条扩展到 3 条、equality delete 字段从 id 改为 data，提升了测试的覆盖度与真实性。修复同步应用于 Spark 3.3/3.4/3.5 三个版本，无破坏性 API 变更，属于纯 bug 修复与测试加固。本次净增 35 行（89 增 / 54 删）。
