# 提交 1957：Flink: Backport avoid RANGE mode broken chain when write parallelism changes (#12080)

## 提交信息

- **序号**：1957 / 4088
- **哈希**：cb3f331e478cbbf3cea852b56f0eace28043124e
- **短哈希**：cb3f331e4
- **日期**：2025-04-03 14:42:45 +0200
- **作者**：big face cat
- **提交说明**：Flink: Backport avoid RANGE mode broken chain when write parallelism changes (#12080)
  - Backports #11702
  - Co-authored-by: huyuanfeng
- **PR/Issue**：#12080（回填 #11702）

## 总体目的

在 Flink Iceberg Sink 使用 RANGE 分布模式（`write.distribution-mode=range`）时，数据流会先经过一个 range 分区算子（`partitionCustom(new RangePartitioner(...), r -> r)`），随后通过 `.filter(StatisticsOrRecord::hasRecord).map(StatisticsOrRecord::record)` 把带统计信息的记录还原为纯 `RowData`，再交给下游 writer 算子。

问题在于：当 sink 的 writer 并行度与上游 range 分区算子的并行度不一致时，`filter` + `map` 这两个算子无法和下游 writer 算子组成 operator chain（链化），导致"断链"。断链会带来额外的网络 shuffle/序列化开销，并可能改变算子分布行为，影响 RANGE 模式下按排序键分布到 writer 的预期。

本提交（回填 #11702 至 Flink 1.18/1.19）的目的就是修复这个断链问题，确保 RANGE 模式下 range 分区之后的算子能和 writer 链化在一起。

## 如何达成设计目的

核心改动是把 `filter` + `map` 两步合并为一个 `flatMap`，并显式设置该算子的并行度等于 `writerParallelism`，从而促进它与下游 writer 算子的 operator chaining。

具体在 `FlinkSink` 的 RANGE 分支：
- 原代码：`partitionCustom(...).filter(StatisticsOrRecord::hasRecord).map(StatisticsOrRecord::record)`
- 新代码：`partitionCustom(...).flatMap((FlatMapFunction<StatisticsOrRecord, RowData>) (sor, out) -> { if (sor.hasRecord()) out.collect(sor.record()); }).setParallelism(writerParallelism).returns(RowData.class)`

通过 `setParallelism(writerParallelism)` 让该算子并行度与 writer 一致，满足 Flink operator chaining 对并行度一致的要求，从而避免断链。使用 `flatMap` 而非 `filter+map` 是因为合并后能作为单一算子参与链化，且需要显式 `returns(RowData.class)` 标注类型信息（lambda 类型擦除）。

同时更新测试 `TestFlinkIcebergSinkDistributionMode` 以反映新的算子链化预期。

## 修改详情

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/FlinkSink.java` (修改, +14/-2 lines)

**修改目的**：修复 RANGE 模式下并行度变化导致的断链。

**工作逻辑**：
在 RANGE 分布模式的 `partitionCustom` 之后，将：
```java
.filter(StatisticsOrRecord::hasRecord)
.map(StatisticsOrRecord::record)
```
替换为：
```java
.flatMap(
    (FlatMapFunction<StatisticsOrRecord, RowData>)
        (statisticsOrRecord, out) -> {
          if (statisticsOrRecord.hasRecord()) {
            out.collect(statisticsOrRecord.record());
          }
        })
// Set the parallelism same as writerParallelism to
// promote operator chaining with the downstream writer operator
.setParallelism(writerParallelism)
.returns(RowData.class);
```
新增 `FlatMapFunction` 的 import。`setParallelism(writerParallelism)` 保证与下游 writer 并行度一致以促成链化。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkIcebergSinkDistributionMode.java` (修改, +21/-20 lines)

**修改目的**：适配断链修复后的算子链化预期。

**工作逻辑**：调整 RANGE 模式相关测试断言，反映 filter+map 合并为 flatMap 且并行度与 writer 一致后的算子链/分区行为。

### `flink/v1.18/flink/src/main/java/org/apache/iceberg/flink/sink/FlinkSink.java` (修改, +14/-2 lines)

**修改目的**：同 v1.19，修复 Flink 1.18 的同样问题。

**工作逻辑**：与 v1.19 相同的 flatMap + setParallelism(writerParallelism) 改动。

### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkIcebergSinkDistributionMode.java` (修改, +21/-20 lines)

**修改目的**：同 v1.19，适配 Flink 1.18 测试断言。

**工作逻辑**：与 v1.19 测试相同的调整。

## 总结

本提交（回填 #11702 至 Flink 1.18/1.19）修复 RANGE 分布模式下当 writer 并行度与上游不一致时的 operator chain 断链问题。核心是把 range 分区后的 `filter+map` 合并为单个 `flatMap`，并通过 `setParallelism(writerParallelism)` 使其与下游 writer 并行度一致以促成链化，同时更新两个 Flink 版本的测试断言。
