# 提交 2568：Flink: add unit test to check skewness across tasks for range partitioner (#13900)

## 提交信息

- **序号**：2568 / 4088
- **哈希**：1128040efeec7d3190f528d266e07342dec67e0a
- **短哈希**：1128040ef
- **日期**：2025-08-28 13:11:56 -0700
- **作者**：Steven Zhen Wu
- **提交说明**：Flink: add unit test to check skewness across tasks for range partitioner (#13900)
- **PR/Issue**：#13900

## 总体目的

该提交为 Flink 的 range partitioner（范围分区器）新增了单元测试，用于验证跨任务的数据倾斜（skewness）情况。Iceberg 的 Flink sink 使用 range partitioner 将数据按排序键分配到不同任务，以实现数据的局部排序和分桶。当数据分布不均匀（如长尾分布）时，某些任务可能会分到远多于其他任务的数据，导致数据倾斜和性能下降。

该提交新增的测试模拟长尾分布（long-tail distribution）的数据，验证 `MapRangePartitioner` 和 `SketchRangePartitioner` 两种范围分区器在给定并行度下的最大倾斜率是否在可接受范围内。通过参数化测试，覆盖不同并行度（8 和 32）和样本量（100K 和 400K）的组合。

此外，该提交还提取了测试数据生成工具类 `DataDistributionUtil`，从 benchmark 代码中复用，并新增了 `SketchRangePartitionerBenchmark` 基准测试。

## 如何达成设计目的

- 新增 `DataDistributionUtil` 工具类，提供长尾分布数据生成、二分搜索、累积权重计算等方法，供测试和 benchmark 共用。
- 新增 `TestRangePartitionerSkew` 测试类，使用参数化测试验证 MapRangePartitioner 的倾斜率。
- 新增 `TestDataDistributionUtil` 测试类，验证 `DataDistributionUtil` 工具方法的正确性。
- 新增 `SketchRangePartitionerBenchmark` 基准测试。
- 重构 `MapRangePartitionerBenchmark`，使用 `DataDistributionUtil` 替代内联的数据生成代码。

## 修改详情

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/sink/shuffle/DataDistributionUtil.java` (+178/-0)

**修改目的**：新增数据分布工具类。

**工作逻辑**：提供以下工具方法：
- `randomString()`：生成随机字符串。
- `binarySearchIndex()`：在累积权重数组中二分查找目标权重对应的索引。
- `longTailDistribution()`：生成长尾分布的权重映射（先衰减后扁平化），模拟真实数据分布。
- `mapStatisticsWithLongTailDistribution()`：将权重映射转换为 SortKey 到权重的统计映射。
- `computeCumulativeWeights()`：计算累积权重数组。

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/sink/shuffle/TestDataDistributionUtil.java` (+49/-0)

**修改目的**：验证 DataDistributionUtil 工具方法的正确性。

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/sink/shuffle/TestRangePartitionerSkew.java` (+183/-0)

**修改目的**：新增范围分区器倾斜测试。

**工作逻辑**：`testMapStatisticsSkewWithLongTailDistribution` 参数化测试使用 `DataDistributionUtil.longTailDistribution` 生成长尾分布数据，构建 `MapAssignment` 和 `MapRangePartitioner`，模拟大量数据分配到各任务，计算每个任务分到的数据量和最大倾斜率（maxSkew），验证 maxSkew 不超过设定的上界（如 0.1 和 0.15）。测试注释中记录了 100 次迭代的统计结果（mean/min/max）作为上界设定的依据。

### `flink/v2.0/flink/src/jmh/java/org/apache/iceberg/flink/sink/shuffle/MapRangePartitionerBenchmark.java` (+21/-104)

**修改目的**：重构 benchmark 使用 DataDistributionUtil。

**工作逻辑**：移除内联的数据生成方法（`longTailDistribution`、`randomString` 等），改为调用 `DataDistributionUtil` 中的对应方法。将并行度从 2 改为 100，使用更真实的数据规模。添加 `decayFactor` 参数到 `longTailDistribution` 调用。

### `flink/v2.0/flink/src/jmh/java/org/apache/iceberg/flink/sink/shuffle/SketchRangePartitionerBenchmark.java` (+114/-0)

**修改目的**：新增 SketchRangePartitioner 基准测试。

**工作逻辑**：与 `MapRangePartitionerBenchmark` 类似的结构，但测试 `SketchRangePartitioner` 的性能。使用 `DataDistributionUtil` 生成测试数据。

## 总结

该提交为 Flink range partitioner 新增了完整的倾斜测试基础设施，包括数据分布工具类 `DataDistributionUtil`、倾斜验证测试 `TestRangePartitionerSkew`、工具方法测试 `TestDataDistributionUtil`，以及 `SketchRangePartitionerBenchmark` 基准测试。同时重构了 `MapRangePartitionerBenchmark` 以复用工具类。新增 544 行代码，涉及 5 个文件。
