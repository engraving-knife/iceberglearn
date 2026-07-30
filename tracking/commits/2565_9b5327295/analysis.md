# 提交 2565：Spark: (unit test) Order query result deterministically (#13891)

## 提交信息

- **序号**：2565 / 4088
- **哈希**：9b5327295d4c564ca441ff29a659cf401c2ab893
- **短哈希**：9b5327295
- **日期**：2025-08-27 10:11:13 -0700
- **作者**：hsiang-c
- **提交说明**：Spark: (unit test) Order query result deterministically (#13891)
- **PR/Issue**：#13891

## 总体目的

该提交修复了 Spark 测试中查询结果排序的非确定性（non-determinism）问题，使测试结果更加稳定。此前部分测试在 `ORDER BY` 子句中只按部分列排序，当排序列存在重复值时，其余列的顺序是不确定的，可能导致测试在多次运行中产生不同的结果，出现 flaky test（不稳定测试）。

具体问题：
1. `TestSparkDataWrite` 中使用 `orderBy("id")` 排序，但当多条记录的 `id` 相同时，`data` 列的顺序不确定，可能导致 `isEqualTo(expected)` 断言失败。
2. `TestStoragePartitionedJoins` 中使用 `ORDER BY t1.id, t1.<sourceColumnName>` 排序，但当这两列的组合存在重复时，`salary` 列的顺序不确定。

修复方案是在 `ORDER BY` 中添加额外的排序列，确保结果完全确定：
- `TestSparkDataWrite`：`orderBy("id")` 改为 `orderBy("id", "data")`。
- `TestStoragePartitionedJoins`：`ORDER BY t1.id, t1.<sourceColumnName>` 改为 `ORDER BY t1.id, t1.<sourceColumnName>, t1.salary`。

## 如何达成设计目的

- 在 Spark 3.4、3.5、4.0 三个版本的 `TestSparkDataWrite` 和 `TestStoragePartitionedJoins` 测试中，为 `ORDER BY` 子句添加额外的排序列。

## 修改详情

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkDataWrite.java` (+1/-1)

**修改目的**：使查询结果排序完全确定。

**工作逻辑**：将 `result.orderBy("id")` 改为 `result.orderBy("id", "data")`，确保当 id 重复时按 data 列进一步排序。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/sql/TestStoragePartitionedJoins.java` (+1/-1)

**修改目的**：使 SPJ 查询结果排序完全确定。

**工作逻辑**：在 `ORDER BY` 子句中追加 `t1.salary`，确保当 id 和 sourceColumn 组合重复时按 salary 列进一步排序。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkDataWrite.java` (+1/-1)

**修改目的**：同 v3.4 的修复。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/sql/TestStoragePartitionedJoins.java` (+1/-1)

**修改目的**：同 v3.4 的修复。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkDataWrite.java` (+1/-1)

**修改目的**：同 v3.4 的修复。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/sql/TestStoragePartitionedJoins.java` (+1/-1)

**修改目的**：同 v3.4 的修复。

## 总结

该提交通过在测试的 `ORDER BY` 子句中添加额外的排序列，解决了因排序列重复值导致的非确定性排序问题，消除了 flaky test。修复同时应用于 Spark 3.4、3.5 和 4.0 三个版本。
