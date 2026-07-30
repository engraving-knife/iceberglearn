# 提交 2574：Flink: backport PR #13900 for adding unit test of skewness for range partitioner (#13943)

## 提交信息

- **序号**：2574 / 4088
- **哈希**：87cade9cfcf5256248e525256118a865bb1923c5
- **短哈希**：87cade9cf
- **日期**：2025-08-28 21:14:04 -0700
- **作者**：Steven Zhen Wu
- **提交说明**：Flink: backport PR #13900 for adding unit test of skewness for range partitioner (#13943)
- **PR/Issue**：#13943（backport of #13900）

## 总体目的

此次提交是 PR #13900 的 backport，将 Flink sink shuffle 范围分区器（Range Partitioner）的偏斜（skewness）单元测试同步到 Flink v1.19 和 v1.20 模块。Iceberg 的 Flink sink 使用范围分区器将数据按排序键分布到不同 subtask，以保证写入的数据局部有序。当数据分布存在长尾（long tail，即少量 key 占大量数据）时，范围分区器可能出现数据偏斜，导致某些 subtask 负载远高于其他。

PR #13900 的核心目标是：为范围分区器添加量化偏斜程度的单元测试，验证在长尾数据分布下 `MapRangePartitioner` 和 `SketchRangePartitioner` 的最大偏斜率是否在可接受范围内。为此，提交提取了测试数据分布生成的工具方法到独立的 `DataDistributionUtil`，新增 `TestRangePartitionerSkew` 参数化测试，并新增 `SketchRangePartitionerBenchmark` 基准测试。

backport 到 v1.19/v1.20 是为了确保旧版本也具备相同的测试覆盖和基准能力。

## 如何达成设计目的

- **提取工具类**：新建 `DataDistributionUtil`，将原本散落在 `MapRangePartitionerBenchmark` 中的数据分布生成方法（`longTailDistribution`、`randomString`、`binarySearchIndex`、`computeCumulativeWeights`、`mapStatisticsWithLongTailDistribution`、UUID 采样方法等）集中为可复用的静态工具方法，并新增 `decayFactor` 参数控制长尾衰减。
- **重构基准**：`MapRangePartitionerBenchmark` 改为调用 `DataDistributionUtil` 的方法，移除重复代码，调整并行度为 100。
- **新增基准**：新增 `SketchRangePartitionerBenchmark`，对基于 Sketch（DataSketches）的范围分区器进行性能基准测试。
- **新增偏斜测试**：`TestRangePartitionerSkew` 使用参数化测试（parallelism 8/32），生成长尾分布数据，按权重随机采样 key，统计各 subtask 的记录数，计算最大偏斜率 `(max - avg) / avg`，断言低于上界（0.1/0.15），验证分区均衡性。
- **工具测试**：`TestDataDistributionUtil` 对 `DataDistributionUtil` 的方法进行单元测试。

## 修改详情

### `flink/v1.19/flink/src/test/java/.../sink/shuffle/DataDistributionUtil.java` (新增, +178)

**修改目的**：提供数据分布生成与采样的可复用工具方法。

**工作逻辑**：
- `longTailDistribution(...)`：生成带衰减因子（decayFactor）的长尾权重分布，前段按衰减递减，后段为带抖动的长尾基础权重。
- `mapStatisticsWithLongTailDistribution(...)`：将权重分布映射为 `SortKey -> Long` 的统计 map。
- `binarySearchIndex(...)`：在累计权重 CDF 数组上二分查找目标权重对应的索引，用于按权重随机采样 key。
- `computeCumulativeWeights(...)`：计算累计权重数组。
- `reservoirSampleUUIDs(...)` / `rangeBoundSampleUUIDs(...)`：蓄水池采样 UUID 并生成范围边界，用于 Sketch 分区器测试。

### `flink/v1.19/flink/src/test/java/.../TestDataDistributionUtil.java` (新增, +49)

**修改目的**：对 `DataDistributionUtil` 工具方法进行单元测试。

### `flink/v1.19/flink/src/test/java/.../TestRangePartitionerSkew.java` (新增, +183)

**修改目的**：验证范围分区器在长尾分布下的偏斜率上界。

**工作逻辑**：
- `testMapStatisticsSkewWithLongTailDistribution`：参数化测试（parallelism=8, sampleSize=100000, maxSkewUpperBound=0.1；parallelism=32, sampleSize=400000, maxSkewUpperBound=0.15）。生成长尾分布，构建 `MapAssignment` 和 `MapRangePartitioner`，按权重随机采样 key 进行分区，统计各 subtask 记录数，计算最大偏斜率并断言低于上界。注释中提供了 100 次迭代的统计参考值。
- 同理有 Sketch 分区器的偏斜测试方法。

### `flink/v1.19/flink/src/jmh/java/.../MapRangePartitionerBenchmark.java` (+11/-61)

**修改目的**：重构基准测试，复用 `DataDistributionUtil`。

**工作逻辑**：移除内联的 `longTailDistribution`、`randomString`、`CHARS` 等重复代码，改为调用 `DataDistributionUtil` 对应方法；并行度从 2 调整为 100；使用 `SORT_ORDER` 常量统一排序顺序。

### `flink/v1.19/flink/src/jmh/java/.../SketchRangePartitionerBenchmark.java` (新增, +114)

**修改目的**：为 Sketch 范围分区器添加 JMH 基准测试。

**工作逻辑**：使用 UUID 采样和范围边界生成构建 `SketchRangePartitioner`，测量分区操作的吞吐/延迟。

### `flink/v1.20/...` 同名文件 (各 +178/+49/+183/+11-61/+114)

**修改目的**：将上述所有改动同步到 Flink v1.20 模块，内容与 v1.19 完全一致。

## 总结

此次 backport 提交为 Flink v1.19/v1.20 的范围分区器添加了偏斜度单元测试和基准测试。核心是将数据分布生成逻辑提取为 `DataDistributionUtil` 工具类，新增 `TestRangePartitionerSkew` 参数化测试验证长尾分布下 `MapRangePartitioner`/`SketchRangePartitioner` 的最大偏斜率低于上界，并新增 `SketchRangePartitionerBenchmark` 基准。这为范围分区器的均衡性提供了可量化的回归测试保障。
