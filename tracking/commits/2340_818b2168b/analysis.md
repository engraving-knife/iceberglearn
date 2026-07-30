# 提交 2340：Spark 4.0: Add procedure to compute partition stats (#13523)

## 提交信息

- **序号**：2340 / 4088
- **哈希**：818b2168ba569df590666305456a53aad84c88a1
- **短哈希**：818b2168b
- **日期**：2025-07-11 09:02:44 +0200
- **作者**：Ajantha Bhat
- **提交说明**：Spark 4.0: Add procedure to compute partition stats (#13523)
- **PR/Issue**：#13523

## 总体目的

本提交为 Spark 4.0 模块新增 `compute_partition_stats` 存储过程，功能与提交 2337（Spark 3.5 版本）相同，用于计算 Iceberg 表的分区统计信息。这是将 Spark 3.5 的功能同步到 Spark 4.0 模块。

由于 Iceberg 同时维护 Spark 3.3、3.4、3.5 和 4.0 多个版本模块，新功能需要在各版本中分别实现。Spark 4.0 使用了较新的 Spark Procedure API（基于 `BoundProcedure` 接口），与 Spark 3.5 的旧版 API 有所不同，因此代码实现上有差异，但功能完全一致。

该过程支持增量计算分区统计：从上一个包含分区统计文件的快照增量计算到指定快照（默认当前快照），合并后写入新的分区统计文件并注册到表元数据。无先前统计文件时执行全量计算。

## 如何达成设计目的

设计思路与 Spark 3.5 版本（2337）一致，但适配 Spark 4.0 的新版 Procedure API。

关键设计点：
1. 使用 Spark 4.0 的 `BoundProcedure` 接口，实现 `bind(StructType)` 方法返回 `this`。
2. `call` 方法返回 `Iterator<Scan>` 而非 `InternalRow[]`，通过 `asScanIterator` 将输出行包装为 Scan 迭代器。
3. 使用 `requiredInParameter` / `optionalInParameter` 替代 `ProcedureParameter.required` / `ProcedureParameter.optional`。
4. 使用 `NAME` 常量和 `name()` 方法标识过程名称，注册表使用常量引用。

## 修改详情

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/procedures/ComputePartitionStatsProcedure.java` (+126/-0 lines, 新文件)

**修改目的**：实现 Spark 4.0 版本的 `compute_partition_stats` 存储过程。

**工作逻辑**：

1. **常量与参数**：`NAME = "compute_partition_stats"`；`TABLE_PARAM`（必选）和 `SNAPSHOT_ID_PARAM`（可选 Long），使用 `requiredInParameter` / `optionalInParameter` 创建。
2. **`bind` 方法**：实现 `BoundProcedure` 接口，返回 `this`（Spark 4.0 新 API 要求）。
3. **`call` 方法**：解析参数，通过 `modifyIcebergTable` 执行操作。创建 `SparkActions.computePartitionStats(table)` action，设置可选快照 ID，执行后通过 `asScanIterator(OUTPUT_TYPE, toOutputRows(result))` 将结果包装为 Scan 迭代器返回。
4. **`toOutputRows` 方法**：统计文件非 null 时返回包含路径的行，否则返回空数组。
5. **`name()` / `description()` 方法**：返回过程名称和描述。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/procedures/SparkProcedures.java` (+1/-0 lines)

**修改目的**：注册新存储过程。

**工作逻辑**：在过程映射表中添加 `ComputePartitionStatsProcedure.NAME` -> `ComputePartitionStatsProcedure::builder`。

### `spark/v4.0/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestComputePartitionStatsProcedure.java` (+121/-0 lines, 新文件)

**修改目的**：测试存储过程正确性。

**工作逻辑**：与 Spark 3.5 版本（2337）测试完全一致，包含 5 个测试用例：空表调用、位置参数、命名参数（含 branch 快照）、无效快照 ID、无效表名。验证统计文件路径、快照 ID 关联和文件存在性。

## 总结

本提交为 Spark 4.0 模块新增 `compute_partition_stats` 存储过程，功能与 Spark 3.5 版本（2337）一致，但适配了 Spark 4.0 的新版 Procedure API（`BoundProcedure` 接口、`Iterator<Scan>` 返回类型）。这确保了 Spark 4.0 用户也能通过 SQL `CALL` 语句计算分区统计信息。
