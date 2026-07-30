# 提交 0322：Core: Remove statistics files in CatalogUtil:dropTableData (#9305)

## 提交信息

- **序号**：0322 / 4088
- **哈希**：3aa0fcd7cd3e0d1836395ca02ba473da71154fe4
- **短哈希**：3aa0fcd7c
- **日期**：2024-01-03 18:12:08 -0800
- **作者**：Hongyue/Steve Zhang
- **提交说明**：Core: Remove statistics files in CatalogUtil:dropTableData (#9305)
- **PR/Issue**：#9305

## 总体目的

这个提交修复了 Iceberg 在删除表数据（`CatalogUtil.dropTableData`）时遗漏清理统计文件（statistics files）的问题。当用户删除一张表时，Iceberg 会通过 `dropTableData` 方法清理该表在底层存储上的各类文件，包括数据文件、manifest 文件、manifest list、历史元数据文件以及当前的元数据文件本身。然而，本提交之前，Iceberg 表的统计文件（以 Puffin 格式存储，包含如 NDV、直方图等列级统计信息）并未被纳入清理范围，导致删表后这些统计文件成为孤儿文件残留在存储中。

统计文件是 Iceberg 表元数据的一部分。每次执行 `updateStatistics` 提交统计信息时，会生成一个 Puffin 文件并记录到表元数据的 `statistics-files` 列表中，同时表元数据版本号递增。在 `dropTableData` 的清理流程中，已有逻辑会清理元数据文件及其历史版本（通过 `metadata.previousFiles()`），但 `metadata.statisticsFiles()` 返回的统计文件路径列表被遗漏了。这是一个资源泄漏问题：在对象存储或 HDFS 上，这些孤儿统计文件会持续占用存储空间，且无法通过正常的删表流程回收，需要管理员手动介入清理。

本提交通过在 `dropTableData` 的删除序列中新增对统计文件的删除调用，使删表流程完整覆盖表产生的所有衍生文件。删除逻辑沿用已有的 `deleteFiles` 工具方法，与历史元数据文件的清理方式保持一致（带标签 "statistics" 且 `failOnError=true`），并放置在删除当前元数据文件之前，确保在元数据文件本身被删除前仍能从元数据中读取到统计文件路径。

## 如何达成设计目的

设计思路是在 `CatalogUtil.dropTableData` 方法中，于"删除历史元数据文件"之后、"删除当前元数据文件"之前，插入一段对 `metadata.statisticsFiles()` 的删除调用。利用 `Iterables.transform` 将 `StatisticsFile` 对象流转换为路径字符串流（`StatisticsFile::path`），再交给已有的 `deleteFiles(io, paths, label, failOnError)` 重载执行。这一位置选择是有意为之：当前元数据文件是清理流程中最后删除的，因为所有衍生文件路径都记录在元数据里，必须先读完所有路径再删除元数据本身。同时，配套测试新增了构造统计文件的辅助方法和断言，验证统计文件确实被纳入删除集合。

## 修改详情

### `core/src/main/java/org/apache/iceberg/CatalogUtil.java`

**修改目的**：在 `dropTableData` 的清理流程中增加对统计文件的删除，避免删表后遗留孤儿统计文件。

**工作逻辑**：在原有"删除 previous metadata"调用之后、"删除当前 metadata"调用之前，新增一个 `deleteFiles` 调用：`deleteFiles(io, Iterables.transform(metadata.statisticsFiles(), StatisticsFile::path), "statistics", true)`。其中 `metadata.statisticsFiles()` 返回表元数据中记录的所有 `StatisticsFile` 对象；`StatisticsFile::path` 提取每个统计文件的底层存储路径；标签字符串 `"statistics"` 用于日志标识被删除的文件类别；`true` 表示删除失败时抛错（与 manifest、manifest list、previous metadata 等元数据级文件的清理策略一致，区别于数据文件受 `gcEnabled` 控制的行为）。这一改动复用了既有基础设施，未引入新的删除路径或重试策略，保持了清理流程的一致性。

### `core/src/test/java/org/apache/iceberg/hadoop/TestCatalogUtilDropTable.java`

**修改目的**：为统计文件清理新增测试覆盖，验证删表时统计文件被正确删除。

**工作逻辑**：

1. 新增 `writeStatsFile` 私有辅助方法：使用 `Puffin.write(...).build()` 创建 `PuffinWriter`，写入一个 `Blob`（类型为 `"some-blob-type"`，关联 snapshot ID 和 sequence number，内容为 `"blob content"` 字节），调用 `puffinWriter.finish()` 完成写入，然后基于写入结果构造并返回一个 `GenericStatisticsFile`（包含 snapshotId、statsLocation、fileSize、footerSize 以及 blob 元数据）。这个辅助方法模拟了 Iceberg 实际生成统计文件的过程。

2. 测试方法 `dropTableDataDeletesExpectedFiles` 改为 `throws IOException`（因为新增的 `writeStatsFile` 调用需要）。在两次 append 提交后，调用 `writeStatsFile` 生成一个统计文件，并通过 `table.updateStatistics().setStatistics(...).commit()` 将其提交到表，使表元数据中包含统计文件记录。

3. 由于提交统计信息会额外产生一次元数据版本提交，`readMetadataVersion` 的预期版本号由 3 调整为 4；`metadataLocations` 的预期数量由 3 调整为 4（多了一个元数据版本文件）。

4. 新增 `statsLocations` 辅助方法：从 `TableMetadata.statisticsFiles()` 中提取所有统计文件路径到 Set。新增断言 `statsLocations` 大小为 1，验证统计文件确实被记录。

5. 在 mock `FileIO` 的 `deleteFile` 调用次数断言中，加入 `statsLocations.size()`，验证删表流程调用了对应数量的删除操作。新增断言 `deletedPaths.containsAll(statsLocations)`，验证被删除的路径集合包含所有统计文件路径。

## 小结

本提交通过在 `CatalogUtil.dropTableData` 的清理序列中新增对 `metadata.statisticsFiles()` 的删除调用，补齐了删表流程中对 Puffin 格式统计文件的清理，消除了孤儿文件导致的存储泄漏，并通过配套测试验证了该清理行为与既有的元数据级文件清理策略一致。
