# 提交 3486：Spark: Remove Spark 2 test assumptions for write projection (#15823)

## 提交信息

- **序号**：3486 / 4088
- **哈希**：3ff0d97b10489a1bf90c40750008e0ec8cc7b7ff
- **短哈希**：3ff0d97b10
- **日期**：2026-03-30 23:02:37 -0700
- **作者**：Ruobing Wang
- **提交说明**：Spark: Remove Spark 2 test assumptions for write projection (#15823)
- **PR/Issue**：#15823

## 总体目的

移除 Spark 测试中针对 Spark 2 的条件假设和死代码。由于 Iceberg 项目已不再支持 Spark 2，测试中所有 `spark.version().startsWith("2")` 的条件分支已成为死代码。这些代码包括：
1. `TestIcebergSourceTablesBase` 中针对 Spark 2 跳过的部分列投影断言。
2. `TestPartitionPruning` 中 Spark 2 特有的时间戳过滤条件。
3. `TestSparkDataWrite` 中仅 Spark 2 才执行的 `testWriteProjection` 和 `testWriteProjectionWithMiddle` 测试。

## 如何达成设计目的

1. 在 `TestIcebergSourceTablesBase` 中移除 `if (!spark.version().startsWith("2"))` 条件包裹，直接执行断言。
2. 在 `TestPartitionPruning` 中移除 Spark 2 的 `to_timestamp` 分支，直接使用字符串比较。
3. 在 Spark 3.4/3.5/4.0 的 `TestSparkDataWrite` 中完全删除 `testWriteProjection` 和 `testWriteProjectionWithMiddle` 两个测试（它们用 `assumeThat` 跳过非 Spark 2 版本，在这些版本中永远不会执行）。
4. 在 Spark 4.1 的 `TestSparkDataWrite` 中保留这两个测试但移除 `assumeThat` 假设，使其在 Spark 4.1 中正常执行。

## 修改详情

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/source/TestIcebergSourceTablesBase.java` (+9/-14 lines)

**修改目的**：移除 Spark 2 条件分支，直接执行部分列投影断言。

**工作逻辑**：移除 `if (!spark.version().startsWith("2"))` 包裹，直接执行 `assertThatThrownBy(...)` 断言验证 "Cannot project a partial list element struct"。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/source/TestPartitionPruning.java` (+3/-6 lines)

**修改目的**：移除 Spark 2 特有的时间戳过滤条件。

**工作逻辑**：`filterCond` 直接使用 `"timestamp >= '2020-02-03T01:00:00'"`，移除 Spark 2 的 `to_timestamp` 分支。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkDataWrite.java` (+0/-82 lines)

**修改目的**：删除仅 Spark 2 执行的死测试代码。

**工作逻辑**：完全移除 `testWriteProjection` 和 `testWriteProjectionWithMiddle` 两个测试方法（它们通过 `assumeThat(spark.version()).startsWith("2")` 跳过非 Spark 2），以及 `assumeThat` 的 import。

### `spark/v3.5/spark/src/test/java/.../TestIcebergSourceTablesBase.java` (+9/-14 lines)

**修改目的**：同 3.4 版本。

### `spark/v3.5/spark/src/test/java/.../TestPartitionPruning.java` (+3/-6 lines)

**修改目的**：同 3.4 版本。

### `spark/v3.5/spark/src/test/java/.../TestSparkDataWrite.java` (+0/-82 lines)

**修改目的**：同 3.4 版本，删除死测试代码。

### `spark/v4.0/spark/src/test/java/.../TestIcebergSourceTablesBase.java` (+9/-14 lines)

**修改目的**：同 3.4 版本。

### `spark/v4.0/spark/src/test/java/.../TestPartitionPruning.java` (+3/-6 lines)

**修改目的**：同 3.4 版本。

### `spark/v4.0/spark/src/test/java/.../TestSparkDataWrite.java` (+0/-82 lines)

**修改目的**：同 3.4 版本，删除死测试代码。

### `spark/v4.1/spark/src/test/java/.../TestIcebergSourceTablesBase.java` (+9/-14 lines)

**修改目的**：同 3.4 版本。

### `spark/v4.1/spark/src/test/java/.../TestPartitionPruning.java` (+3/-6 lines)

**修改目的**：同 3.4 版本。

### `spark/v4.1/spark/src/test/java/.../TestSparkDataWrite.java` (+0/-9 lines)

**修改目的**：保留 write projection 测试但移除 Spark 2 假设。

**工作逻辑**：`testWriteProjection` 和 `testWriteProjectionWithMiddle` 中移除 `assumeThat(spark.version()).startsWith("2")` 假设，使测试在 Spark 4.1 中正常执行。这表明 Spark 4.1 支持写入投影（不同于 Spark 3.x）。同时移除 `assumeThat` 的 import。

## 总结

代码清理提交，移除不再支持的 Spark 2 相关测试条件分支和死代码。在 Spark 3.4/3.5/4.0 中删除了仅 Spark 2 执行的测试方法；在 Spark 4.1 中保留写入投影测试但移除了跳过假设。同时简化了分区裁剪和表投影测试中的 Spark 2 条件分支。
