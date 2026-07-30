# 提交 2319：Spark 4.0: Add spark action to compute partition stats (#13478)

## 提交信息

- **序号**：2319 / 4088
- **哈希**：e5274d278abd0f2d9abbb2fd8b0d32f128e0835a
- **短哈希**：e5274d278
- **日期**：2025-07-07 09:52:14 +0200
- **作者**：Ajantha Bhat
- **提交说明**：Spark 4.0: Add spark action to compute partition stats (#13478)
- **PR/Issue**：#13478

## 总体目的

这个提交是提交 2314（Spark 3.5: Add spark action to compute partition stats）在 Spark 4.0 模块上的对应实现。由于 Iceberg 项目同时维护 Spark 3.4、3.5 和 4.0 三个版本模块，同一功能需要在不同版本模块中分别实现。

该提交为 Spark 4.0 添加了 `ComputePartitionStatsSparkAction`，使得 Spark 4.0 用户也能通过 `SparkActions.get().computePartitionStats(table)` 来计算和写入分区统计信息。功能与 Spark 3.5 版本完全一致：支持增量计算、全量计算和 no-op 优化。

由于 API 接口（`ComputePartitionStats`）和 Core 层实现（`PartitionStatsHandler`、`BaseComputePartitionStats`）已在提交 2314 中完成，本提交仅需添加 Spark 4.0 特定的实现文件和测试。

## 如何达成设计目的

复制 Spark 3.5 的实现到 Spark 4.0 模块，包括：
1. `ComputePartitionStatsSparkAction` — Spark Action 实现
2. `SparkActions` — 注册新 Action
3. `TestComputePartitionStatsAction` — 测试用例

## 修改详情

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/actions/ComputePartitionStatsSparkAction.java` (+103/-0 lines, 新文件)

**修改目的**：实现 Spark 4.0 版本的分区统计计算 Action。

**工作逻辑**：与 Spark 3.5 版本完全一致。接收 `SparkSession` 和 `Table`，默认使用当前快照，可通过 `snapshot(long)` 指定目标快照。`execute()` 通过 `withJobGroupInfo` 包装执行，调用 `PartitionStatsHandler.computeAndWriteStatsFile` 计算统计，然后注册到表元数据。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/actions/SparkActions.java` (+6/-0 lines)

**修改目的**：在 Spark 4.0 的 SparkActions 中注册新 Action。

**工作逻辑**：实现 `computePartitionStats(Table table)` 方法，返回 `new ComputePartitionStatsSparkAction(spark, table)`。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/actions/TestComputePartitionStatsAction.java` (+309/-0 lines, 新文件)

**修改目的**：为 Spark 4.0 版本添加分区统计 Action 的测试。

**工作逻辑**：测试覆盖与 Spark 3.5 版本相同的场景：空表、空分支、无效快照、最新快照统计计算、增量合并、DELETE 操作后统计等。

## 总结

这是提交 2314 的 Spark 4.0 对应实现，代码与 Spark 3.5 版本完全一致。该提交完善了分区统计 Action 在不同 Spark 版本间的支持，确保 Spark 4.0 用户也能使用此功能。
