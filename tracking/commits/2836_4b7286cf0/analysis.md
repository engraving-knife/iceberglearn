# 提交 2836：Flink: Backport Preserve row lineage in RewriteDataFiles to Flink 2.1 and 1.20 (#14520)

## 提交信息

- **序号**：2836 / 4088
- **哈希**：4b7286cf0f0e8ce48104835ba747941883ac1570
- **短哈希**：4b7286cf0
- **日期**：2025-11-06 14:27:51 +0100
- **作者**：GuoYu
- **提交说明**：Flink: Backport Preserve row lineage in RewriteDataFiles to Flink 2.1 and 1.20 (#14520)
- **PR/Issue**：#14520

## 总体目的

提交 2834（PR #14149）为 Flink 2.0 的 `RewriteDataFiles` 实现了 V3 row lineage 表的 compaction 支持。但 Iceberg 同时维护 Flink 1.20 与 2.1 两个版本分支，这两个分支同样存在"V3+ row lineage 表无法 compaction"的限制与相同实现结构。

本提交把 2834 的修改原样 backport 到 Flink 1.20 与 2.1 两个分支，使三个 Flink 版本（1.20、2.0、2.1）在 V3 row lineage 表 compaction 行为上保持一致。修改内容、设计思路与 2834 完全相同，只是路径前缀不同（`flink/v1.20/...` 与 `flink/v2.1/...`）。

## 如何达成设计目的

对 Flink 1.20 与 2.1 各自复制 2834 的全部修改：

1. `DataFileRewritePlanner`：删除 `open` 中对 `supportsRowLineage` 的校验与相关导入。
2. `DataFileRewriteRunner`：在 `processElement` 计算 `preserveRowId`，`writerFor`/`readerFor` 在保留 row lineage 时使用 `MetadataColumns.schemaWithRowLineage` 作为写/读 schema，并构造带 `writeSchema`/`spec` 的 `RowDataTaskWriterFactory`。
3. `RowDataFileScanTaskReader`：简化 `idToConstant` 构造为直接调用 `PartitionUtil.constantsMap`。
4. 测试侧：`SimpleDataUtil` 增加 `SCHEMA3`/`RECORD3`/`createRecordWithRowId`/带投影的 `assertTableRecords`；`OperatorTestBase` 把建表方法改为接受 `int formatVersion` 并新增批量 insert/insertPartitioned/update 重载，`writePosDelete` 透传 `formatVersion`；`TestRewriteDataFiles` 新增三个 V3 preserve-lineage 用例；`TestDataFileRewritePlanner` 删除 `testFailsOnV3Table`；`TestDataFileRewriteRunner` 新增 `testV3Table`。

注：Flink 1.20/2.1 的 `DataFileRewritePlanner.open` 仍是 `open(Configuration parameters)` 签名（Flink 2.0 已演进为 `open(OpenContext context)`），但删除校验的逻辑改动一致。

## 修改详情

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/DataFileRewritePlanner.java` (+0/-6 lines)

**修改目的**：移除 V3 row lineage 表的 compaction 拒绝校验。与 2834 中 Flink 2.0 改动一致。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/DataFileRewriteRunner.java` (+28/-10 lines)

**修改目的**：读写时保留 row lineage 列。逻辑与 2834 相同：`preserveRowId` 控制 `writerFor`/`readerFor` 的 schema 投影。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/source/RowDataFileScanTaskReader.java` (+2/-7 lines)

**修改目的**：简化分区常量 map 构造，直接用 `PartitionUtil.constantsMap`。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/SimpleDataUtil.java` (+27/-3 lines)

**修改目的**：新增 V3 row lineage schema/record 工具与带投影断言。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/maintenance/api/TestRewriteDataFiles.java` (+113/-0 lines)

**修改目的**：新增三个 V3 preserve-lineage compaction 测试用例。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/OperatorTestBase.java` (+67/-9 lines)

**修改目的**：测试基座支持任意 format-version 与批量 insert/update。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/TestDataFileRewritePlanner.java` (+0/-13 lines)

**修改目的**：删除已失效的 `testFailsOnV3Table`。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/TestDataFileRewriteRunner.java` (+20/-0 lines)

**修改目的**：新增 V3 表带 delete 的 compaction 测试。

### `flink/v2.1/...`（同样 8 个文件，+244/-47 lines 合计）

**修改目的**：对 Flink 2.1 分支应用与 1.20 完全相同的 backport。每个文件改动量与 1.20 一致。

## 总结

该提交是 2834 的 backport，把 Flink 2.0 上"V3 row lineage 表 compaction 保留行血缘"的能力同步到 Flink 1.20 与 2.1 两个分支，实现三个 Flink 版本行为一致。所有代码与测试改动与 2834 一一对应，差异仅在路径前缀与 `open` 方法签名（Flink 1.20/2.1 仍用 `Configuration` 参数版本）。这是一个跨版本一致性维护的机械 backport。
