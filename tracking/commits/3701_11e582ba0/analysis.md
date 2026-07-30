# 提交 3701：Spark: Backport aggregate pushdown tests with NaN's (#16316)

## 提交信息

- **序号**：3701 / 4088
- **哈希**：11e582ba0d191dae181466e2897cbc4500b002a4
- **短哈希**：11e582ba0
- **日期**：2026-05-13 08:47:57 -0700
- **作者**：Vrishabh
- **提交说明**：Spark: Backport aggregate pushdown tests with NaN's (#16316)
- **PR/Issue**：#16316

## 总体目的

这个提交将包含 NaN 值的聚合下推测试回移植到 Spark 3.4、3.5 和 4.0 模块。此测试验证当数据包含 NaN（Not a Number）值时，聚合下推（aggregate pushdown）行为的正确性。

NaN 是浮点数中的一个特殊值，其比较语义与普通数值不同（NaN != NaN）。在聚合操作（如 min、max、count）中，NaN 的处理需要特别小心。当数据文件中包含 NaN 值时，聚合下推不应该将 min/max 操作下推到 Iceberg 层（基于文件统计信息），因为这可能导致不正确的结果。测试验证了：
1. 数据文件正确记录了 nan_value_count、lower_bound 和 upper_bound
2. 聚合操作不被下推（通过 EXPLAIN 验证）
3. 查询结果正确处理了 NaN 值

## 如何达成设计目的

通过在三个 Spark 版本模块（v3.4、v3.5、v4.0）的 `TestAggregatePushDown.java` 中添加相同的测试方法 `testNanWithLowerAndUpperBoundMetrics`，验证 NaN 场景下的聚合下推行为。

## 修改详情

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/sql/TestAggregatePushDown.java` (+45 lines)
### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/sql/TestAggregatePushDown.java` (+45 lines)
### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/sql/TestAggregatePushDown.java` (+45 lines)

**修改目的**：添加 NaN 聚合下推测试。

**工作逻辑**：

测试步骤：

1. **创建表并插入含 NaN 的数据**：
```sql
CREATE TABLE %s (id int, data float) USING iceberg PARTITIONED BY (id)
INSERT INTO %s VALUES (1, float('nan')), (1, float('nan')), (1, 10.0),
  (2, 2), (2, float('nan')), (3, float('nan')), (3, 1)
```

2. **验证文件统计信息**：查询 `readable_metrics.data` 验证每个数据文件都包含 nan_value_count > 0、非 null 的 lower_bound 和 upper_bound。

3. **验证聚合不下推**：
```java
String select = "SELECT count(*), max(data), min(data), count(data) FROM %s";
List<Object[]> explain = sql("EXPLAIN " + select, tableName);
boolean explainContainsPushDownAggregates =
    explainString.contains("max(data)") || explainString.contains("min(data)") || ...;
assertThat(explainContainsPushDownAggregates).isFalse();
```
通过 EXPLAIN 检查查询计划中不包含被下推的聚合表达式。

4. **验证查询结果正确**：
```java
expected.add(new Object[] {7L, Float.NaN, 1.0F, 7L});
```
验证 count(*)=7, max(data)=NaN, min(data)=1.0, count(data)=7。

## 总结

这是一个测试回移植提交，为 Spark 3.4/3.5/4.0 添加 NaN 值场景下的聚合下推测试。该测试确保当数据包含 NaN 时，聚合操作不被错误下推，且查询结果正确处理 NaN 的特殊语义。这对于保证浮点数数据类型的查询正确性具有重要意义。
