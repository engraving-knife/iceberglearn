# 提交 2314：Spark 3.5: Add spark action to compute partition stats (#12450)

## 提交信息

- **序号**：2314 / 4088
- **哈希**：4b6852751606ec50d970535af801fac531cb304e
- **短哈希**：4b6852751
- **日期**：2025-07-03 15:39:51 -0600
- **作者**：Ajantha Bhat
- **提交说明**：Spark 3.5: Add spark action to compute partition stats (#12450)
- **PR/Issue**：#12450

## 总体目的

这个提交为 Spark 3.5 添加了一个新的 Action——计算分区统计信息（Compute Partition Stats）。分区统计信息是 Iceberg 表维护中的重要组成部分，它记录了每个分区的数据文件数、记录数、文件大小等指标，可用于查询优化和表维护决策。

在此之前，Iceberg 的 Core 模块已经有 `PartitionStatsHandler` 来计算和写入分区统计文件，但缺少通过 Spark Action API 暴露的入口。这个提交填补了这一空白，使得用户可以通过 `SparkActions.get().computePartitionStats(table)` 来触发分区统计的计算。

该 Action 支持增量计算——如果表已有分区统计文件，则从该快照增量计算到目标快照；否则进行全量计算。此外，当请求的快照已有对应的统计文件时，会直接返回已有文件（no-op），避免重复计算。

## 如何达成设计目的

整体设计分为三层：

1. **API 层**：定义 `ComputePartitionStats` 接口和 `ActionsProvider` 中的工厂方法
2. **Core 层**：提供 `BaseComputePartitionStats` 的 Immutable Result 实现和 `PartitionStatsHandler` 的 no-op 优化
3. **Spark 层**：实现 `ComputePartitionStatsSparkAction`，通过 Spark JobGroup 执行统计计算，并将结果注册到表元数据

## 修改详情

### `api/src/main/java/org/apache/iceberg/actions/ComputePartitionStats.java` (+43/-0 lines, 新文件)

**修改目的**：定义计算分区统计的 Action 接口。

**工作逻辑**：接口继承 `Action<ComputePartitionStats, Result>`，提供 `snapshot(long snapshotId)` 方法指定目标快照。内部 `Result` 接口返回 `PartitionStatisticsFile`（可能为 null 表示无统计收集）。

### `api/src/main/java/org/apache/iceberg/actions/ActionsProvider.java` (+6/-0 lines)

**修改目的**：在 ActionsProvider 中添加 `computePartitionStats` 工厂方法。

**工作逻辑**：新增 default 方法 `computePartitionStats(Table table)`，默认抛出 `UnsupportedOperationException`，由实现类（如 `SparkActions`）覆写。

### `core/src/main/java/org/apache/iceberg/actions/BaseComputePartitionStats.java` (+39/-0 lines, 新文件)

**修改目的**：提供 Immutable 的 Result 实现。

**工作逻辑**：使用 Immutables 框架定义 `Result` 接口，包含可空的 `PartitionStatisticsFile statisticsFile()` 字段。

### `core/src/main/java/org/apache/iceberg/PartitionStatsHandler.java` (+6/-0 lines)

**修改目的**：添加 no-op 优化，避免对已有统计文件的快照重复计算。

**工作逻辑**：在 `computeAndWriteStatsFile` 方法中，当请求的 `snapshotId` 与现有 `statisticsFile.snapshotId()` 相同时，直接返回已有统计文件并记录日志。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/actions/ComputePartitionStatsSparkAction.java` (+103/-0 lines, 新文件)

**修改目的**：实现 Spark 版本的分区统计计算 Action。

**工作逻辑**：
- 构造函数接收 `SparkSession` 和 `Table`，默认使用当前快照
- `snapshot(long)` 方法允许指定目标快照
- `execute()` 检查快照是否存在，使用 `withJobGroupInfo` 包装执行
- `doExecute()` 调用 `PartitionStatsHandler.computeAndWriteStatsFile` 计算统计，然后通过 `table.updatePartitionStatistics().setPartitionStatistics().commit()` 注册到表元数据
- 如果无统计文件生成（如空表），返回 `EMPTY_RESULT`

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/actions/SparkActions.java` (+6/-0 lines)

**修改目的**：在 SparkActions 中注册新的 Action。

**工作逻辑**：实现 `computePartitionStats(Table table)` 方法，返回 `new ComputePartitionStatsSparkAction(spark, table)`。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestComputePartitionStatsAction.java` (+309/-0 lines, 新文件)

**修改目的**：全面测试新的分区统计 Action。

**工作逻辑**：测试覆盖以下场景：
- 空表和空分支返回 null 统计文件
- 无效快照 ID 抛出异常
- 最新快照的统计计算（包含增量合并、DELETE 操作后的统计）
- 统计文件正确注册到表元数据
- 验证分区统计的各项指标（数据记录数、文件数、文件大小、位置删除记录数等）

## 总结

这是一个功能新增提交，为 Spark 3.5 添加了计算分区统计的 Action。设计层次清晰，从 API 接口到 Core 实现再到 Spark 集成，并附有全面的测试覆盖。该 Action 支持增量计算和 no-op 优化，对于表的查询优化和维护有重要价值。
