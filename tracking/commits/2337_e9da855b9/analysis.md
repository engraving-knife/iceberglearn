# 提交 2337：Spark 3.5: Add procedure to compute partition stats (#13480)

## 提交信息

- **序号**：2337 / 4088
- **哈希**：e9da855b94488e5090c9b437da62735d3eac1485
- **短哈希**：e9da855b9
- **日期**：2025-07-11 08:06:56 +0200
- **作者**：Ajantha Bhat
- **提交说明**：Spark 3.5: Add procedure to compute partition stats (#13480)
- **PR/Issue**：#13480

## 总体目的

本提交为 Spark 3.5 模块新增了一个存储过程（procedure）`compute_partition_stats`，用于计算 Iceberg 表的分区统计信息。

Iceberg 的分区统计（Partition Statistics）是一种元数据文件，记录每个分区的统计信息（如文件数、记录数等），可用于查询优化。此前，Iceberg Core 模块已提供了 `ComputePartitionStats` action 来计算分区统计，但 Spark 用户需要通过编程方式调用。本提交将其封装为 Spark 存储过程，使用户可以通过 SQL `CALL` 语句方便地触发分区统计计算。

该过程支持增量计算：从上一个包含分区统计文件的快照开始，增量计算到指定快照（默认为当前快照），合并结果后写入新的分区统计文件。如果不存在先前的统计文件，则执行全量计算。计算完成后，分区统计文件会注册到表元数据中。

## 如何达成设计目的

整体设计思路是创建一个标准的 Spark 存储过程类，封装 `SparkActions.computePartitionStats()` action 的调用，并注册到过程注册表中。

关键设计点：
1. 过程接受两个参数：`table`（必选，表标识符）和 `snapshot_id`（可选，指定计算到哪个快照）。
2. 返回分区统计文件路径作为输出。
3. 通过 `modifyIcebergTable` 确保操作在表锁保护下执行。
4. 支持位置参数和命名参数两种调用方式。

## 修改详情

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/procedures/ComputePartitionStatsProcedure.java` (+118/-0 lines, 新文件)

**修改目的**：实现 `compute_partition_stats` 存储过程。

**工作逻辑**：

1. **参数定义**：`TABLE_PARAM`（必选 String）和 `SNAPSHOT_ID_PARAM`（可选 Long）。
2. **输出类型**：单列 `partition_statistics_file`（String），返回统计文件路径。
3. **`call` 方法**：解析输入参数获取表标识符和可选快照 ID，通过 `modifyIcebergTable` 执行操作。创建 `SparkActions.computePartitionStats(table)` action，如果指定了快照 ID 则设置 `action.snapshot(snapshotId)`，执行后将结果转换为输出行。
4. **`toOutputRows` 方法**：如果结果中统计文件不为 null，返回包含路径的行；否则返回空数组（空表场景）。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/procedures/SparkProcedures.java` (+1/-0 lines)

**修改目的**：注册新存储过程。

**工作逻辑**：在过程映射表中添加 `compute_partition_stats` -> `ComputePartitionStatsProcedure::builder`。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestComputePartitionStatsProcedure.java` (+121/-0 lines, 新文件)

**修改目的**：测试存储过程的正确性。

**工作逻辑**：包含 5 个测试用例：
1. `procedureOnEmptyTable`：空表调用应返回空结果。
2. `procedureWithPositionalArgs`：位置参数调用，验证统计文件路径、快照 ID 和文件存在性。
3. `procedureWithNamedArgs`：命名参数调用，指定 branch 快照 ID，验证统计基于指定快照而非最新快照。
4. `procedureWithInvalidSnapshotId`：无效快照 ID 应抛出 `IllegalArgumentException`。
5. `procedureWithInvalidTable`：不存在的表应抛出包含"Couldn't load table"的异常。

## 总结

本提交为 Spark 3.5 模块新增了 `compute_partition_stats` 存储过程，使 Spark 用户可以通过 SQL `CALL` 语句方便地计算 Iceberg 表的分区统计信息。该过程支持增量计算和指定快照，为查询优化提供了重要的元数据支持。
