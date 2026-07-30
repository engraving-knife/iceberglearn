# 提交 2643：Spark 3.4: Backport: Add procedure and action to compute partition stats. (#14034)

## 提交信息

- **序号**：2643 / 4088
- **哈希**：3bbdee97b0f33794df091c3292de8d54a05dcc1e
- **短哈希**：3bbdee97b
- **日期**：2025-09-16 09:08:14 -0700
- **作者**：slfan1989
- **提交说明**：Spark 3.4: Backport: Add procedure and action to compute partition stats. (#14034)
- **PR/Issue**：#14034（backport 到 Spark 3.4）

## 总体目的

这是一个 backport 提交，将"计算分区统计"（compute partition stats）功能从主分支移植到 Spark 3.4 模块。分区统计文件（PartitionStatisticsFile）记录了各分区的文件数、记录数、删除记录数等汇总信息，可用于查询优化（例如帮助查询规划器跳过不含数据的分区），提升查询性能。

该功能在主分支已通过 core 模块的 `PartitionStatsHandler` 实现，本提交为 Spark 3.4 提供了对应的 Spark Action（`ComputePartitionStatsSparkAction`）和存储过程（`ComputePartitionStatsProcedure`），使用户可以通过 `CALL catalog.system.compute_partition_stats('table')` SQL 过程或编程式 Action API 触发分区统计的计算与注册。

## 如何达成设计目的

1. 新增 `ComputePartitionStatsSparkAction`：实现 `ComputePartitionStats` 接口，负责调用 core 的 `PartitionStatsHandler.computeAndWriteStatsFile` 增量计算分区统计并写入文件，然后通过 `table.updatePartitionStatistics()` 将统计文件注册到表元数据。
2. 在 `SparkActions` 中注册该 Action 工厂方法 `computePartitionStats(Table)`。
3. 新增 `ComputePartitionStatsProcedure`：封装为 Spark 存储过程，接受 `table`（必填）和 `snapshot_id`（可选）参数，返回统计文件路径。
4. 在 `SparkProcedures` 中注册 `compute_partition_stats` 过程名。
5. 新增 Action 测试和过程测试，覆盖空表、位置参数、命名参数、无效快照、无效表、增量计算等场景。

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/actions/ComputePartitionStatsSparkAction.java` (新建, +103 lines)

**修改目的**：实现 Spark 版分区统计计算 Action。

**工作逻辑**：继承 `BaseSparkAction`，实现 `ComputePartitionStats` 接口。构造时默认使用表当前快照。`snapshot(long)` 方法允许指定目标快照（校验快照存在）。`execute()` 在 Spark JobGroup 中执行 `doExecute()`：调用 `PartitionStatsHandler.computeAndWriteStatsFile(table, snapshot.snapshotId())` 增量计算（从上一个有分区统计的快照到目标快照），若结果为空返回空结果，否则通过 `table.updatePartitionStatistics().setPartitionStatistics(statisticsFile).commit()` 注册到元数据，返回含统计文件的结果。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/actions/SparkActions.java` (+6/-0 lines)

**修改目的**：注册 Action 工厂方法。

**工作逻辑**：新增 `computePartitionStats(Table)` 方法，返回 `new ComputePartitionStatsSparkAction(spark, table)`。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/procedures/ComputePartitionStatsProcedure.java` (新建, +118 lines)

**修改目的**：实现 Spark 存储过程封装。

**工作逻辑**：定义参数 `table`（必填 String）和 `snapshot_id`（可选 Long），输出类型为单列 `partition_statistics_file`（String）。`call()` 解析参数后，通过 `actions().computePartitionStats(table)` 创建 Action，若指定了 snapshot_id 则调用 `action.snapshot(snapshotId)`，执行后返回统计文件路径行。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/procedures/SparkProcedures.java` (+1/-0 lines)

**修改目的**：注册存储过程名。

**工作逻辑**：在过程映射中添加 `mapBuilder.put("compute_partition_stats", ComputePartitionStatsProcedure::builder)`。

### `spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestComputePartitionStatsProcedure.java` (新建, +121 lines)

**修改目的**：测试存储过程。

**工作逻辑**：覆盖空表（返回空结果）、位置参数调用（验证统计文件路径、快照 ID、文件存在）、命名参数调用（指定分支快照）、无效快照 ID（抛异常含"Snapshot not found"）、无效表（抛异常含"Couldn't load table"）。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/actions/TestComputePartitionStatsAction.java` (新建, +309 lines)

**修改目的**：测试 Action 的分区统计计算正确性。

**工作逻辑**：验证计算后的分区统计文件内容（分区值、文件数、记录数、删除记录数等）与预期一致，覆盖增量计算、多分区等场景。

## 总结

本提交将"计算分区统计"功能 backport 到 Spark 3.4 模块，提供了 Spark Action 和存储过程两种使用方式。用户可通过 `CALL catalog.system.compute_partition_stats('table')` 计算并注册分区统计文件，用于查询优化。实现基于 core 模块的 `PartitionStatsHandler`，支持增量计算和指定快照。配套测试覆盖了主要场景。
