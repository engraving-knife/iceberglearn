# 提交 2835：Flink: Preserve row lineage in RewriteDataFiles (#14149)

## 提交信息

- **序号**：2835 / 4088
- **哈希**：4e68ff008002eeb323c0c90321f6fbf867b02aa2
- **短哈希**：4e68ff008
- **日期**：2025-11-06 12:24:14 +0100
- **作者**：GuoYu
- **提交说明**：Flink: Preserve row lineage in RewriteDataFiles (#14149)
- **PR/Issue**：#14149

## 总体目的

Iceberg V3 表支持 row lineage（行级别血缘），通过 `_row_id` 与 `_last_updated_sequence_number` 等元数据列唯一标识一行数据并跟踪其变更序列号。Flink 维护作业中的 `RewriteDataFiles`（数据文件压缩/重写）原本显式拒绝在开启 row lineage 的 V3+ 表上执行——`DataFileRewritePlanner.open` 中有 `Preconditions.checkArgument(!TableUtil.supportsRowLineage(table), "Flink does not support compaction on row lineage enabled tables (V3+)")`。

这意味着 V3 表无法享受 Flink 自动 compaction 的好处。该提交移除该限制，让 Flink 的 `DataFileRewriteRunner` 在重写数据文件时"保留 row lineage"——读取时把 `_row_id` 等元数据列投影出来，写入时使用包含 row lineage 列的 schema 写出新文件，从而在 compaction 后行的标识与序列号保持不变。

## 如何达成设计目的

1. **移除 V3 限制**：删除 `DataFileRewritePlanner.open` 中对 `supportsRowLineage` 的校验，让 V3 表也能进入 compaction 流程。
2. **读写时投影 row lineage 列**：在 `DataFileRewriteRunner.processElement` 中根据 `TableUtil.supportsRowLineage(value.table())` 计算 `preserveRowId`，并把它传给 `writerFor` 与 `readerFor`。
3. **写端**：`writerFor(value, preserveRowId)` 在 `preserveRowId` 为 true 时用 `MetadataColumns.schemaWithRowLineage(value.table().schema())` 作为写 schema，构造 `RowDataTaskWriterFactory` 时多传 `writeSchema` 与 `value.table().spec()`，让 writer 把 `_row_id` 等列一起写出。
4. **读端**：`readerFor(value, preserveRowId)` 在 `preserveRowId` 为 true 时用 `schemaWithRowLineage` 作为 `RowDataFileScanTaskReader` 的 `projectedSchema`，确保读出元数据列。
5. **简化 `RowDataFileScanTaskReader.open`**：原本根据 `partitionSchema` 是否为空决定 `idToConstant` 使用空 map 还是 `PartitionUtil.constantsMap`；改为直接调用 `PartitionUtil.constantsMap`（该方法内部会在无分区列时返回空 map），减少分支。
6. **测试基础设施**：`SimpleDataUtil` 新增带 `_row_id`/`_last_updated_sequence_number` 的 `SCHEMA3`、`RECORD3`、`createRecordWithRowId`，以及支持 `project(projectSchema)` 的 `assertTableRecords` 重载；`OperatorTestBase` 把 `createTable`/`createTableWithDelete`/`createPartitionedTable` 改为接受 `int formatVersion`，新增 `insert(Table, List<Record>)`、`insertPartitioned(Table, List<Record>, String)`、`update(..., int formatVersion)` 重载，`writePosDelete` 透传 `formatVersion`。
7. **新增/调整测试**：`TestRewriteDataFiles` 新增 `testRewriteUnpartitionedPreserveLineage`、`testRewriteTheSameFilePreserveLineage`、`testRewritePartitionedPreserveLineage` 三个用例，验证 V3 表 compaction 后 row lineage 列被保留；`TestDataFileRewritePlanner` 删除原 `testFailsOnV3Table`（因限制已移除）；`TestDataFileRewriteRunner` 新增 `testV3Table` 验证 V3 表带 delete 的 compaction 仍正确。

## 修改详情

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/DataFileRewritePlanner.java` (+0/-6 lines)

**修改目的**：移除对 V3+ row lineage 表的硬性拒绝。

**工作逻辑**：删除 `open` 中 `tableLoader.loadTable()` 与 `Preconditions.checkArgument(!TableUtil.supportsRowLineage(table), ...)`，以及对应的 `Table`、`TableUtil` 导入。Planner 不再需要在打开阶段校验 row lineage。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/DataFileRewriteRunner.java` (+28/-10 lines)

**修改目的**：在重写读/写阶段保留 row lineage 列。

**工作逻辑**：
- `processElement` 中计算 `preserveRowId = TableUtil.supportsRowLineage(value.table())`，传给 `writerFor` 与 `readerFor`。
- `writerFor(value, preserveRowId)`：`preserveRowId` 时 `writeSchema = MetadataColumns.schemaWithRowLineage(value.table().schema())`，否则用原 schema；构造 `RowDataTaskWriterFactory` 时改用 `value::table`（supplier）并多传 `writeSchema` 与 `value.table().spec()`，让工厂能写出 row lineage 列。
- `readerFor(value, preserveRowId)`：`preserveRowId` 时 `projectedSchema = schemaWithRowLineage(...)`，传给 `RowDataFileScanTaskReader`，确保读出 `_row_id` 等列。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/source/RowDataFileScanTaskReader.java` (+2/-7 lines)

**修改目的**：简化分区常量 map 的构造，去掉对 `partitionSchema` 是否为空的分支。

**工作逻辑**：原代码先 `TypeUtil.select(projectedSchema, task.spec().identitySourceIds())` 得到 `partitionSchema`，再判断空则用 `ImmutableMap.of()` 否则用 `PartitionUtil.constantsMap(...)`。改为直接 `Map<Integer, ?> idToConstant = PartitionUtil.constantsMap(task, RowDataUtil::convertConstant);`，`constantsMap` 内部已处理无分区列的情况，逻辑等价但更简洁，也避免 row lineage 列投影时分区选择逻辑的干扰。

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/SimpleDataUtil.java` (+27/-3 lines)

**修改目的**：为 V3 row lineage 测试提供 schema/record 工具与带投影的断言。

**工作逻辑**：
- 新增 `SCHEMA3`（id、data、`_row_id`、`_last_updated_sequence_number`）与 `RECORD3`。
- 新增 `createRecordWithRowId(id, data, rowId, lastUpdatedSequenceNumber)` 构造带 row lineage 列的 Record。
- `assertTableRecords` 新增重载，接受 `Schema projectSchema`，读取时用 `IcebergGenerics.read(table).useSnapshot(...).project(projectSchema).build()`，并以 `projectSchema.asStruct()` 构造比较集合。

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/maintenance/api/TestRewriteDataFiles.java` (+113/-0 lines)

**修改目的**：验证 V3 表 compaction 后 row lineage 列被正确保留。

**工作逻辑**：
- `testRewriteUnpartitionedPreserveLineage`：V3 非分区表插入 4 行，触发 compaction 合并为 1 文件，断言 `_row_id` 0..3 与 `_last_updated_sequence_number` 1..4 都保留。
- `testRewriteTheSameFilePreserveLineage`：验证同一文件中两行的 `_last_updated_sequence_number` 相同（来自同一次 commit）。
- `testRewritePartitionedPreserveLineage`：V3 分区表插入 4 行到两个分区，compaction 后每分区 1 文件，row lineage 列保留。

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/OperatorTestBase.java` (+67/-9 lines)

**修改目的**：让测试基座支持任意 format-version 与批量 insert/update。

**工作逻辑**：
- `createTable()`/`createTableWithDelete()`/`createPartitionedTable()` 改为接受 `int formatVersion`，保留无参重载默认 v2。
- 新增 `insert(Table, List<Record>)`、`insertPartitioned(Table, List<Record>, String)`、`update(..., int formatVersion)` 重载。
- `writePosDelete` 增加 `formatVersion` 参数并透传给 `FileHelpers.writePosDeleteFile`，以支持 V3 写出 position delete。

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/TestDataFileRewritePlanner.java` (+0/-13 lines)

**修改目的**：移除已失效的 V3 拒绝测试。

**工作逻辑**：删除 `testFailsOnV3Table`（原断言 `"Flink does not support compaction on row lineage enabled tables (V3+)"` 已不再成立），并清理相关导入。

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/TestDataFileRewriteRunner.java` (+20/-0 lines)

**修改目的**：验证 V3 表带 delete 的 compaction 仍正确。

**工作逻辑**：新增 `testV3Table`，对 V3 表做两次 `update`（含 eq + pos delete），plan + execute rewrite，断言重写后只剩期望记录 `(1, "c")` 且分区正确。

## 总结

该提交让 Flink 的 `RewriteDataFiles` 支持 V3 row lineage 表：移除了 planner 中的硬性拒绝，在读写两端通过 `MetadataColumns.schemaWithRowLineage` 投影保留 `_row_id` 等列，使 compaction 后行的标识与序列号保持不变。配套完善了测试基座（多 format-version、批量 insert/update、带投影断言）并新增 V3 非分区/分区/带 delete 的 compaction 测试。该功能在 2835 中被 backport 到 Flink 2.1 与 1.20。
