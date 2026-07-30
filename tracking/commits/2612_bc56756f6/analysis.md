# 提交 2612：Spark 4.0: Add Support for PartitionStatistics Files in RewriteTablePath. (#13956)

## 提交信息

- **序号**：2612 / 4088
- **哈希**：bc56756f651c66350ad92949f9d1d3f37aeddf10
- **短哈希**：bc56756f6
- **日期**：2025-09-08 08:30:49 -0700
- **作者**：slfan1989
- **提交说明**：Spark 4.0: Add Support for PartitionStatistics Files in RewriteTablePath.
- **PR/Issue**：#13956

## 总体目的

Iceberg 的 `RewriteTablePath` 动作用于将表的文件路径从一个位置前缀重写为另一个位置前缀（典型场景是表迁移/搬迁）。此前该动作已能正确处理元数据日志、统计文件（statisticsFiles）、manifest 等的路径重写，但对**分区统计文件（PartitionStatisticsFile）**尚未支持——代码中留有 `// TODO: update partition statistics file paths` 注释，且在 Spark 4.0 实现里显式抛出 `IllegalArgumentException("Partition statistics files are not supported yet.")` 阻止带分区统计文件的表执行路径重写。

分区统计文件是 Iceberg 较新引入的特性（记录每个分区的统计信息，用于查询优化）。随着该特性被越来越多地使用，原有的"直接报错"限制会导致带分区统计的表无法进行路径重写，成为功能缺口。本提交补齐这一能力，使 `RewriteTablePath` 在 Spark 4.0 下能正确重写分区统计文件路径并将其纳入文件复制计划。

## 如何达成设计目的

整体设计分两层：

1. **Core 层（`RewriteTablePathUtil`）**：新增 `updatePathInPartitionStatisticsFiles` 方法，将每个 `PartitionStatisticsFile` 的 `path` 通过既有的 `newPath(...)` 工具方法做前缀替换，并保持 `snapshotId`、`fileSizeInBytes` 不变，生成新的 `ImmutableGenericPartitionStatisticsFile`。在构建新 `TableMetadata` 时，用该方法替换原来的"原样透传"逻辑与 TODO 注释。

2. **Spark 4.0 Action 层（`RewriteTablePathSparkAction`）**：移除阻止执行的 `Preconditions.checkArgument` 校验；新增 `partitionStatsFileCopyPlan` 方法，将重写前后的分区统计文件配对（校验数量与大小一致），加入文件复制计划，使这些文件在路径重写时被一并复制到目标位置。

3. **测试层**：将原先仅验证"抛异常"的测试改为真正执行路径重写并验证分区统计文件被正确重写与复制；并重构公共辅助方法（`checkFileNum` 重载、`createMetastoreTable` 支持分区列、`findAndAssertFileInFileList` 提取通用断言）。

## 修改详情

### `core/src/main/java/org/apache/iceberg/RewriteTablePathUtil.java` (+27/-2 lines)

**修改目的**：在 Core 层实现分区统计文件路径的重写。

**工作逻辑**：
- 在 `TableMetadata.buildFrom` 调用处，将原来直接透传的 `metadata.partitionStatisticsFiles()` 替换为 `updatePathInPartitionStatisticsFiles(metadata.partitionStatisticsFiles(), sourcePrefix, targetPrefix)`，并移除 TODO 注释。
- 新增私有方法 `updatePathInPartitionStatisticsFiles`：遍历分区统计文件列表，对每个文件调用 `newPath(existing.path(), sourcePrefix, targetPrefix)` 进行前缀替换，并用 `ImmutableGenericPartitionStatisticsFile.builder()` 重建对象，保留 `snapshotId` 与 `fileSizeInBytes`。该方法与已有的 `updatePathInStatisticsFiles`（处理普通 statistics 文件）形成对称实现。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteTablePathSparkAction.java` (+27/-5 lines)

**修改目的**：在 Spark 4.0 Action 中移除限制并纳入分区统计文件的复制计划。

**工作逻辑**：
- 导入 `PartitionStatisticsFile`。
- 删除原先在 `endMetadata` 校验处阻止带分区统计文件执行的重写逻辑的 `Preconditions.checkArgument(... "Partition statistics files are not supported yet.")`。
- 在文件复制计划构建处，新增对 `partitionStatsFileCopyPlan` 的调用，将结果 `addAll` 到复制计划集合中。
- 新增 `partitionStatsFileCopyPlan` 方法：接收重写前后的分区统计文件列表，先处理空集合快速返回；然后校验前后文件数量一致、对应文件大小一致（保证只是路径变化而非内容变化），最后将每对 `(before.path(), after.path())` 作为 `Pair` 加入结果集，与 `statsFileCopyPlan` 形成对称实现。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteTablePathsAction.java` (+143/-61 lines)

**修改目的**：将原先验证"抛异常"的测试改为正向验证分区统计文件路径重写，并重构公共测试辅助方法。

**工作逻辑**：
- `testPartitionStatisticFile` 重命名为 `testTableWithManyPartitionStatisticFile`：通过循环 10 次插入数据并调用 `actions().computePartitionStats(sourceTable).execute()` 生成 10 个分区统计文件，然后执行路径重写，并用 `checkFileNum` 校验各类文件数量（含 partitionFileCount=iterations），用 `findAndAssertFileInFileList` 校验 `partition-stats` 文件的源/目标路径正确。
- 提取 `findAndAssertFileInFileList` 通用方法：在文件列表中按文件标识（如 `.stats` 或 `partition-stats`）查找并断言源路径指向源表、不含 staging、目标路径指向目标表，替代原先内联的重复断言逻辑。
- `checkFileNum` 增加新的重载以支持 `partitionFileCount` 参数，并在断言中加入对 `partition-stats` 文件数量的校验。
- `createMetastoreTable` 增加 `partitionColumn` 参数支持创建分区表，并将建表 SQL 生成逻辑抽取为 `generateCreateTableSQL` 方法，使用 `StringBuilder` 拼接，支持可选的 `PARTITIONED BY`、`LOCATION`、`TBLPROPERTIES` 子句。

## 总结

本提交补齐了 RewriteTablePath 对分区统计文件（PartitionStatisticsFile）的支持，移除了此前"不支持"的硬性限制，使带分区统计的表也能正确进行路径重写与文件复制。改动覆盖 Core 工具层、Spark 4.0 Action 层与测试层，并顺势重构了测试辅助方法以提升可复用性。这是 Iceberg 表迁移能力的一项功能完善。
