# 提交 3211：Core: Avro: Row Lineage Column (ROW_ID) is not populated correctly in case the Data File doesn't have them (#15187)

## 提交信息

- **序号**：3211 / 4088
- **哈希**：232af57377bcd796c7d69d57854b39f996dfd9d0
- **短哈希**：232af5737
- **日期**：2026-02-06
- **作者**：Ayush Saxena
- **提交说明**：Core: Avro: Row Lineage Column (ROW_ID) is not populated correctly in case the Data File doesn't have them (#15187)
- **PR/Issue**：#15187

## 总体目的

Iceberg 的行级血缘（row lineage）通过 `ROW_ID` 元数据列暴露每行唯一标识。读取 Avro 数据文件时，Avro 读取器 `ValueReaders` 的读计划构建逻辑分两条路径：当数据文件**物理上包含**某列时走 `addFileFieldReadersToPlan`/`fileFieldReader`；当文件**不包含**该列（例如文件写于血缘特性启用之前，或该列是合成元数据列）时走 `addMissingFileReadersToPlan`/`createMissingFieldReader`。

对于行血缘列 `ROW_ID`，Iceberg 会为每个文件分配一个基准行 id（base row id），作为常量通过 `idToConstant` 传入，行 id 据此按行号递增（base+0, base+1, base+2 …）。问题正出在"文件不含该列"的路径上：`createMissingFieldReader` 检测到 `ROW_ID` 有常量时，原代码返回的是 `ValueReaders.constant(constant)`——一个对所有行都返回**同一个**基准行 id 的常量读取器。于是该文件里每一行的 `ROW_ID` 都被错误地赋为同一个值（如 1000），而非逐行递增，直接破坏行血缘的唯一性，使基于 `ROW_ID` 的行级更新/删除定位失效。

本提交在 `createMissingFieldReader` 中为 `ROW_ID` 增加特判：当该列缺失但有常量（基准行 id）时，返回 `ValueReaders.rowIds((Long) constant, null)`。`rowIds(baseRowId, idReader)` 在 `baseRowId` 非空时构造 `RowIdReader`，因其 `idReader` 为 `null`（文件无该列、无逐行 id 读取器），`RowIdReader` 会基于 `baseRowId` 与行号（position）自行计算逐行递增的 id，从而与"文件含该列"路径（该路径已正确地用 `rowIds(firstRowId, fieldReader)` 包裹文件读取器）行为保持一致。

## 如何达成设计目的

修复集中在 `ValueReaders.createMissingFieldReader` 一处特判，使缺失列路径与存在列路径统一走 `RowIdReader` 计算逐行 id。改动文件两个：`ValueReaders.java` 为核心修复（仅 3 行新增），`TestPlannedDataReader.java` 新增两个测试并重构读取辅助方法以支持多行读取。设计上让缺失列与存在列两条路径对 `ROW_ID` 的处理方式对齐，消除"常量读取器返回同一值"的缺陷。

## 修改详情

### `core/src/main/java/org/apache/iceberg/avro/ValueReaders.java` (+3/-0)

**修改目的**：修复数据文件不含 `ROW_ID` 列时行 id 被错误地置为同一常量的问题。

**工作逻辑**：
在 `createMissingFieldReader(...)` 中，`if (constant != null)` 分支内、原 `return ValueReaders.constant(constant);` 之前新增特判：

```
if (fieldId == MetadataColumns.ROW_ID.fieldId()) {
  return ValueReaders.rowIds((Long) constant, null);
}
```

当缺失的字段是 `ROW_ID` 且其常量（基准行 id）非空时，改用 `ValueReaders.rowIds((Long) constant, null)`。查阅同文件 `rowIds(Long baseRowId, ValueReader<?> idReader)`：`baseRowId != null` 时返回 `new RowIdReader(baseRowId, (ValueReader<Long>) idReader)`，此处 `idReader` 为 `null`，`RowIdReader` 据此按 `baseRowId + 行号` 逐行生成 id（1000, 1001, 1002 …）。这与存在列路径 `fileFieldReader` 中对 `ROW_ID` 调用 `ValueReaders.rowIds(firstRowId, fieldReader)`（以文件逐行读取器为 `idReader`）的处理方式对齐，确保两条路径下 `ROW_ID` 都正确递增。对 `LAST_UPDATED_SEQUENCE_NUMBER` 等其它常量列仍走原有 `constant(constant)` 逻辑，不受影响。

### `core/src/test/java/org/apache/iceberg/data/avro/TestPlannedDataReader.java` (+137/-2)

**修改目的**：验证修复后文件不含 `ROW_ID` 列时行 id 正确递增，并覆盖混合（部分行有值、部分为 null）场景。

**工作逻辑**：
新增 `testRowLineageInjectedWithPlannedReader()`：Avro 文件 schema 仅含 `data` 字段（无 `ROW_ID`、无 `LAST_UPDATED_SEQUENCE_NUMBER` 列）；`idToConstant` 给出 `ROW_ID=1000L`、`LAST_UPDATED_SEQ=5L`；写入两条记录 `"a"`、`"b"`。断言第一条 `ROW_ID=1000`、第二条 `ROW_ID=1001`（逐行递增，正是修复点——修复前两条都会是 1000），两条的 `LAST_UPDATED_SEQ` 均为常量 5。该测试直接命中 `createMissingFieldReader` 的修复路径。

新增 `testMixedRowLineageValues()`：Avro 文件 schema 含 `data`、`ROW_ID`、`LAST_UPDATED_SEQUENCE_NUMBER` 三列（后两者 optional）；`idToConstant` 给出 `ROW_ID=1000L`、`LAST_UPDATED_SEQ=10L`。第一条记录显式写入 `ROW_ID=555`、`seq=7`；第二条记录这两列为 `null`。断言第一条保留显式值（555、7）；第二条因值为 null 走注入：`ROW_ID=1001`（基准 1000 + 行号 1，因第一条已使 position 递增到 1）、`seq=10`（常量）。该测试覆盖"文件含该列但部分行为 null"的存在列路径，确保修复未回归既有行为，并验证 `RowIdReader`/`LastUpdatedSeqReader` 在显式值与 null 之间的正确取舍。

辅助方法 `readRecord(...)` 被重构为基于新增的 `readRecords(...)`：`readRecords` 支持把多条 `GenericRecord` 顺序写入并顺序读出，使上述多行测试成为可能；`readRecord` 则以单元素列表复用之。

## 总结

本提交修复了 Avro 读取器在数据文件不含行血缘列 `ROW_ID` 时、因误用常量读取器导致所有行被赋予同一基准行 id 的缺陷。通过在缺失列路径特判为 `ValueReaders.rowIds((Long) constant, null)`，使其与存在列路径一致地走 `RowIdReader` 计算逐行递增 id，恢复了行级血缘在跨文件/旧文件场景下的唯一性与正确性，对依赖 `ROW_ID` 的行级更新删除至关重要。
