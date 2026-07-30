# 提交 0330：Core: Remove partition statistics files during purge table (#9409)

## 提交信息

- **序号**：0330 / 4088
- **哈希**：b7e3e21bbb53814c3f7418ba55de0c1d5eea57dd
- **短哈希**：b7e3e21bb
- **日期**：2024-01-04 13:27:57 -0800
- **作者**：Ajantha Bhat
- **提交说明**：Core: Remove partition statistics files during purge table (#9409)
- **PR/Issue**：#9409

## 总体目的

Iceberg 的 `CatalogUtil.dropTable(io, catalog, identifier, purge)` 提供了"purge 模式"的删表能力——除了删除 catalog 中表的元数据，还会按表最新的 `TableMetadata` 中记录的文件位置，递归删除数据文件、manifest、manifest list、metadata json、statistics files 等所有遗留物，使表被彻底从存储层清理掉。这是一个对运维很关键的能力，因为单纯删 catalog 引用会留下大量"孤儿"文件，长期累积既占存储成本也增加误用风险。

此前 `CatalogUtil` 的 purge 路径已经处理了 statistics files（即 `metadata.statisticsFiles()`，对应 Puffin 格式的统计文件，存放 NDV、分位数等列级统计），但是 Iceberg 在更早的版本里又新增了一类"分区统计文件"（partition statistics files，`metadata.partitionStatisticsFiles()`，对应 `PartitionStatisticsFile` 接口）——这类文件存放每个分区的统计信息（如分区行数、文件数等），与列级 statistics files 是分开维护的。purge 路径没有同步更新，导致执行 `dropTable(purge=true)` 时分区统计文件不会被删除，从而成为孤儿文件。这在大量使用分区表、且频繁重建/删除表的场景下会持续累积存储成本，也违背了"purge"语义所承诺的"彻底清理"。

本提交通过在 `CatalogUtil.dropTable` 的 purge 流程中新增一段 `deleteFiles(io, transform(partitionStatisticsFiles, PartitionStatisticsFile::path), "partition statistics", true)`，与既有的 statistics files 删除对称地处理分区统计文件，使 purge 真正彻底。同时扩展 `TestCatalogUtilDropTable` 测试，让被删的表先写一个 partition statistics 文件，断言该文件出现在实际被删除的路径列表中，覆盖这一行为。

## 如何达成设计目的

设计思路是与既有 statistics files 的删除路径完全对称：在 `CatalogUtil.dropTableMetadata` 中已有的"删 statistics files"调用之后，紧跟一个"删 partition statistics files"调用，复用同一个 `deleteFiles` 私有工具方法（它接受 `Iterable<String>` 路径、一个描述性标签、以及 `deleteFiles` 是否在文件不存在时静默忽略的布尔）。把 `metadata.partitionStatisticsFiles()` 通过 `Iterables.transform(..., PartitionStatisticsFile::path)` 转成路径 Iterable 即可。测试侧则用 `PositionOutputStream` 写一个空文件模拟分区统计文件，通过 `table.updatePartitionStatistics().setPartitionStatistics(...).commit()` 提交到表元数据，再触发 purge 并用 Mockito 的 `ArgumentCaptor` 收集所有被删除的路径，断言 partition stats 路径在其中。同时把多处辅助方法从实例方法改为 `static`，这是测试代码风格的小整理（不影响行为）。

## 修改详情

### `core/src/main/java/org/apache/iceberg/CatalogUtil.java`

**修改目的**：在 purge 表时新增对分区统计文件的删除，避免孤儿文件。

**工作逻辑**：在 `dropTableMetadata`（实际执行 purge 文件清理的方法）中，紧接已有的删 `metadata.statisticsFiles()` 的 `deleteFiles(...)` 调用之后，新增一段：
```java
deleteFiles(
    io,
    Iterables.transform(metadata.partitionStatisticsFiles(), PartitionStatisticsFile::path),
    "partition statistics",
    true);
```
- `metadata.partitionStatisticsFiles()` 返回 `List<PartitionStatisticsFile>`，是表当前版本元数据中记录的所有分区统计文件。
- `Iterables.transform(..., PartitionStatisticsFile::path)` 把每个 `PartitionStatisticsFile` 映射为其存储路径字符串。
- 第三个参数 `"partition statistics"` 是传给 `deleteFiles` 的标签，用于在日志/异常信息中描述这一批文件。
- 第四个参数 `true` 与 statistics files 删除保持一致，表示"文件不存在时静默忽略"——purge 是尽力而为，某些文件可能已经被并发删除或手工清理，不应让整个 purge 因为单个文件缺失而失败。

这段代码紧跟 statistics files 删除之后、删 metadata json 之前，与既有 `deleteFiles` 调用模式完全对称，是这次修复的核心。注意它依赖 `TableMetadata.partitionStatisticsFiles()` 这一 API（更早提交已加），本提交只是消费它。

### `core/src/test/java/org/apache/iceberg/hadoop/TestCatalogUtilDropTable.java`

**修改目的**：扩展测试以覆盖"purge 表时删除 partition statistics 文件"的新行为，并顺带把辅助方法改为 static。

**工作逻辑**：
- 新增 import：`java.io.UncheckedIOException`、`ImmutableGenericPartitionStatisticsFile`、`PartitionStatisticsFile`、`PositionOutputStream`。
- 在 `dropTable` 测试用例（验证 purge 模式删表）中：
  - 在创建 statistics file 并 `updateStatistics().setStatistics(...).commit()` 之后，新增一段：调用 `writePartitionStatsFile(snapshotId, tableLocation + "/metadata/" + UUID.randomUUID() + ".stats", table.io())` 生成一个分区统计文件，再 `table.updatePartitionStatistics().setPartitionStatistics(partitionStatisticsFile).commit()` 把它提交到表元数据。因为新增了一次 commit，后续读 metadata 的版本号从 4 改为 5（`readMetadataVersion(5)`）。
  - 新增 `partitionStatsLocations(tableMetadata)` 收集分区统计文件路径集合，断言 `partitionStatsLocations` 大小为 1 且 `containsExactly(partitionStatisticsFile.path())`。
  - 元数据文件位置数断言从 4 改为 5（因为多了一次 commit，多了一个 metadata json 文件）。
  - 在 Mockito `ArgumentCaptor` 验证删除次数时，把 `partitionStatsLocations.size()` 加入期望删除总数。
  - 新增 `Assertions.assertThat(deletedPaths).as("should contain all created partition stats files").containsAll(partitionStatsLocations);`，确保实际被删除的路径列表包含分区统计文件路径。
  - 顺手把 `statsLocations` 的断言从 `hasSize(1)` 改为 `containsExactly(statisticsFile.path())`，让断言更精确；把 `as("should contain all created statistic")` 修正为 `as("should contain all created statistics")`（语法小修）。
- 新增私有静态方法 `writePartitionStatsFile(long snapshotId, String statsLocation, FileIO fileIO)`：用 `fileIO.newOutputFile(statsLocation).create()` 创建一个空文件（`PositionOutputStream` 立即 close），再用 `ImmutableGenericPartitionStatisticsFile.builder().snapshotId(snapshotId).fileSizeInBytes(42L).path(statsLocation).build()` 构造一个 `PartitionStatisticsFile` 实例（`fileSizeInBytes` 用一个任意值 42L 即可，purge 只关心 path）。捕获 `IOException` 转 `UncheckedIOException` 抛出。
- 新增私有静态方法 `partitionStatsLocations(TableMetadata tableMetadata)`：`tableMetadata.partitionStatisticsFiles().stream().map(PartitionStatisticsFile::path).collect(Collectors.toSet())`。
- 把已有的 `manifestListLocations`/`manifestLocations`/`dataLocations`/`metadataLocations`/`statsLocations`/`writeStatsFile` 全部从实例方法改为 `static` 方法（去掉对实例字段的访问后更符合"工具方法"语义），这是测试代码风格整理，与本次功能修复并行完成。

## 小结

这个提交补齐了 `CatalogUtil.dropTable(purge=true)` 路径上对 partition statistics 文件的清理——此前这类文件会在 purge 后成为孤儿，与 purge 的"彻底清理"语义不符。修复方式与既有 statistics files 删除完全对称：在 `CatalogUtil.dropTableMetadata` 中新增一段 `deleteFiles(metadata.partitionStatisticsFiles() -> path)`，并扩展 `TestCatalogUtilDropTable` 用 Mockito `ArgumentCaptor` 验证该文件被实际删除。改动小而精准，但是消除了一个真实的存储泄漏隐患，对频繁重建分区表的场景尤其有价值。
