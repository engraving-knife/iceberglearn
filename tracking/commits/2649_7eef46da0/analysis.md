# 提交 2649：Spark 3.4, 3.5: Backport Add Support for PartitionStatistics Files in RewriteTablePath. (#14032)

## 提交信息

- **序号**：2649 / 4088
- **哈希**：7eef46da0d00edb79b20d24cb7806f6ea8da4000
- **短哈希**：7eef46da0
- **日期**：2025-09-17 10:15:26 -0700
- **作者**：slfan1989
- **提交说明**：Spark 3.4, 3.5: Backport Add Support for PartitionStatistics Files in RewriteTablePath. (#14032)
- **PR/Issue**：#14032（backport 到 Spark 3.4 和 3.5）

## 总体目的

这是一个 backport 提交，将"RewriteTablePath 支持分区统计文件"功能从主分支移植到 Spark 3.4 和 3.5 模块。`RewriteTablePathSparkAction` 用于重写表的文件路径（例如将表从一个存储位置迁移到另一个位置），它会重写数据文件、manifest、manifest list、统计文件等所有引用路径。

此前，`RewriteTablePathSparkAction` 在执行路径重写时，会检查表是否包含分区统计文件（partition statistics files），如果包含则直接抛出异常拒绝执行（"Partition statistics files are not supported yet."）。这意味着一旦表上计算了分区统计，就无法再使用 RewriteTablePath 进行路径迁移。

本提交移除了该限制，使 RewriteTablePath 在重写路径时也正确处理分区统计文件：将旧路径的分区统计文件复制到新路径，并更新元数据中的引用。

## 如何达成设计目的

1. 移除 `RewriteTablePathSparkAction` 中对分区统计文件存在性检查并抛异常的逻辑。
2. 新增 `partitionStatsFileCopyPlan` 方法，构建分区统计文件从旧路径到新路径的复制计划，并校验重写前后文件数量和大小一致。
3. 在 `copyPlan` 中将分区统计文件的复制计划加入总复制计划，使重写流程覆盖这些文件。
4. 更新测试 `TestRewriteTablePathsAction`，覆盖含分区统计文件的路径重写场景。

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteTablePathSparkAction.java` (+24/-6 lines)（v3.5 同样修改）

**修改目的**：支持分区统计文件的路径重写。

**工作逻辑**：
- 移除原先的 `Preconditions.checkArgument(endMetadata.partitionStatisticsFiles() == null || ...isEmpty(), "Partition statistics files are not supported yet.")` 检查。
- 在 `copyPlan` 方法中，新增将 `metadata.partitionStatisticsFiles()` 与 `newTableMetadata.partitionStatisticsFiles()` 传入 `partitionStatsFileCopyPlan` 并加入复制计划。
- 新增 `partitionStatsFileCopyPlan(beforeStats, afterStats)`：若 before 为空返回空集；校验 before/after 数量一致、各文件大小一致；返回 `(before.path, after.path)` 配对集合，表示需要复制的文件路径映射。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteTablePathsAction.java` (+118/-46 lines)（v3.5 同样修改）

**修改目的**：测试含分区统计文件的路径重写。

**工作逻辑**：更新测试以覆盖表含分区统计文件时执行 RewriteTablePath 的场景，验证重写后分区统计文件路径已更新、文件内容正确复制。

## 总结

本提交将"RewriteTablePath 支持分区统计文件"功能 backport 到 Spark 3.4 和 3.5。此前一旦表有分区统计文件就无法进行路径重写，现在该限制被移除，重写流程会正确复制分区统计文件并更新引用路径。修改包含复制计划构建和前后一致性校验，配套测试覆盖了含分区统计文件的路径重写场景。这与提交 2642（计算分区统计的 backport）形成配套，使 Spark 3.4/3.5 同时具备分区统计的计算和路径迁移能力。
