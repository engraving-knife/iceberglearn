# 提交 2203：Flink: port range distribution to v2 iceberg sink (#12071)

## 提交信息

- **序号**：2203 / 4088
- **哈希**：931865ecaf40a827f9081dddb675bf1c95c05461
- **短哈希**：931865eca
- **日期**：2025-06-03 13:32:46 -0700
- **作者**：Rodrigo
- **提交说明**：Flink: port range distribution to v2 iceberg sink (#12071)
- **PR/Issue**：#12071

## 总体目的

这个提交为 Flink v2 Iceberg Sink（基于 Flink SinkV2 API 的 `IcebergSink`）引入了 RANGE 分布模式（distribution mode）的支持。在此之前的 v2 Sink 中，`distributionMode(RANGE)` 会被直接拒绝（抛出参数异常），只支持 NONE 和 HASH 两种模式。RANGE 分布模式的核心目的是：在写入数据前，根据数据的排序键（sort order）或分区键收集数据分布统计信息，然后按范围对数据进行重分区，使每个 writer 任务收到的数据在排序键上是有序且负载均衡的。这对于高基数排序键的场景特别有用，可以减少写出的数据文件数量、改善数据聚类，提升下游查询性能。本提交将已有的 v1 Sink 中的 range distribution 逻辑移植（port）到 v2 Sink，并新增了相关配置项和测试。

## 如何达成设计目的

- 在 `IcebergSink.Builder` 中移除了对 RANGE 模式的拒绝检查，新增 `rangeDistributionStatisticsType(StatisticsType)` 和 `rangeDistributionSortKeyBaseWeight(double)` 两个配置方法。
- 将原有 `distributeDataStream` 方法中庞大的 switch-case 逻辑重构为三个独立的私有方法：`distributeDataStreamByNoneDistributionMode`、`distributeDataStreamByHashDistributionMode`、`distributeDataStreamByRangeDistributionMode`，提升可读性。
- `distributeDataStreamByRangeDistributionMode` 方法实现 RANGE 分布：使用 `DataStatisticsOperatorFactory` 收集数据统计信息，通过 `RangePartitioner` 进行范围分区，再用 flatMap 过滤出实际记录。
- 处理 equality fields（主键）场景：RANGE 模式下若存在 equality fields，回退到 keyBy（保持向后兼容）。
- 新增 `operatorName` 辅助方法，为算子生成带 uidSuffix 的名称。
- 新增 `TestFlinkIcebergSinkV2DistributionMode` 测试类，覆盖 RANGE 分布的各种场景。

## 修改详情

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/IcebergSink.java` (修改, +175/-76 lines)

**修改目的**：为 v2 IcebergSink 实现 RANGE 分布模式。

**工作逻辑**：
- **Builder 配置**：移除 `distributionMode` 中 `Preconditions.checkArgument(!DistributionMode.RANGE.equals(mode), ...)` 的拒绝检查。新增 `rangeDistributionStatisticsType(StatisticsType)`：设置统计类型（Map/Sketch/Auto），写入 writeOptions。新增 `rangeDistributionSortKeyBaseWeight(double)`：设置排序键的基础权重，用于避免低流量排序键产生过多小文件，仅在 Map 统计类型下生效。
- **重构分发逻辑**：将原 `distributeDataStream` 的 switch-case 中 NONE/HASH/RANGE 三个分支提取为独立方法。
  - `distributeDataStreamByNoneDistributionMode`：无 equality fields 直接返回，有则 keyBy equality fields。
  - `distributeDataStreamByHashDistributionMode`：根据 equality fields 和分区情况选择 keyBy 或 partitionCustom（BucketPartitioner）。
  - `distributeDataStreamByRangeDistributionMode`：核心新逻辑——若有 equality fields 则回退 keyBy（向后兼容）；否则要求有 sort order 或 partition spec，若 sortOrder 未排序则从 partition spec 构造 sortOrder；通过 `DataStatisticsOperatorFactory` 创建统计收集算子（与输入同并行度以鼓励 chaining），再用 `RangePartitioner` 进行 partitionCustom，最后 flatMap 过滤掉统计记录只输出 RowData，设置 slot sharing group 和 writer parallelism。
- **辅助方法**：新增 `operatorName(String suffix)` 根据 uidSuffix 生成算子名称。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkIcebergSinkV2DistributionMode.java` (新增, +539/-0 lines)

**修改目的**：为 v2 Sink 的 RANGE 分布模式提供测试覆盖。

**工作逻辑**：新增测试类，覆盖 RANGE 分布模式的各种场景，包括不同统计类型（Map/Sketch/Auto）、有/无 equality fields、分区表与非分区表、sort key base weight 配置等。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/sink/TestIcebergSink.java` (修改, +0/-70 lines)

**修改目的**：移除已迁移到新测试类的旧测试代码。

**工作逻辑**：移除原 TestIcebergSink 中与分布模式相关的测试（约 70 行），这些测试已迁移到独立的 `TestFlinkIcebergSinkV2DistributionMode` 测试类中。

## 总结

该提交为 Flink v2 IcebergSink 移植了 RANGE 分布模式支持，使 v2 Sink 具备与 v1 Sink 同等的范围分区写入能力。通过收集数据统计信息并按范围重分区，改善写入文件的数据聚类和负载均衡。同时重构了分发逻辑提升可读性，并新增了完整测试。这是 Flink 集成的重要功能性增强。
