# 提交 2207：Flink: Backport IcebergSink RANGE distribution to Flink 1.19 and 2.0 (#13228)

## 提交信息

- **序号**：2207 / 4088
- **哈希**：73802f63c5d3c57349c92ff8112692ec4b67e897
- **短哈希**：73802f63c
- **日期**：2025-06-04 05:07:16 -0700
- **作者**：Rodrigo
- **提交说明**：Flink: Backport IcebergSink RANGE distribution to Flink 1.19 and 2.0 (#13228)
- **PR/Issue**：#13228（backport #12071）

## 总体目的

这个提交将 PR #12071（提交 2203，为 Flink v2 IcebergSink 移植 RANGE 分布模式）的改动 backport（回溯）到 Flink 1.19 和 2.0 两个版本对应的 Iceberg 模块。Iceberg 项目同时维护多个 Flink 版本的集成模块（flink/v1.19、flink/v1.20、flink/v2.0），PR #12071 最初只修改了 flink/v1.20 模块。为了保证各 Flink 版本的功能一致性，本提交将相同的 RANGE 分布模式支持同步到 flink/v1.19 和 flink/v2.0 模块。RANGE 分布模式使 sink 能够根据排序键收集数据统计并按范围重分区，改善写入文件的数据聚类和负载均衡。

## 如何达成设计目的

- 将 flink/v1.20 中 `IcebergSink.java` 的 RANGE 分布模式修改（移除拒绝检查、新增配置方法、重构分发逻辑为三个独立方法、实现 `distributeDataStreamByRangeDistributionMode`）同步到 flink/v1.19 和 flink/v2.0 的 `IcebergSink.java`。
- 将 flink/v1.20 中新增的 `TestFlinkIcebergSinkV2DistributionMode` 测试类同步到 flink/v1.19 和 flink/v2.0。
- 将 flink/v1.20 中 `TestIcebergSink.java` 移除已迁移测试的修改同步到 flink/v1.19 和 flink/v2.0。

## 修改详情

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/IcebergSink.java` (修改, +175/-76 lines)

**修改目的**：为 Flink 1.19 的 v2 IcebergSink 实现 RANGE 分布模式。

**工作逻辑**：与提交 2203（flink/v1.20）完全一致的修改：移除 RANGE 拒绝检查、新增 `rangeDistributionStatisticsType` 和 `rangeDistributionSortKeyBaseWeight` 配置方法、重构分发逻辑为三个独立方法、实现 `distributeDataStreamByRangeDistributionMode`（DataStatisticsOperatorFactory 收集统计 → RangePartitioner 范围分区 → flatMap 过滤记录）。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkIcebergSinkV2DistributionMode.java` (新增, +539/-0 lines)

**修改目的**：为 Flink 1.19 的 RANGE 分布模式提供测试覆盖。

**工作逻辑**：与 flink/v1.20 的测试类一致，覆盖 RANGE 分布的各种场景。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/sink/TestIcebergSink.java` (修改, +0/-70 lines)

**修改目的**：移除已迁移到新测试类的旧测试代码。

**工作逻辑**：移除与分布模式相关的旧测试代码。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/sink/IcebergSink.java` (修改, +175/-76 lines)

**修改目的**：为 Flink 2.0 的 v2 IcebergSink 实现 RANGE 分布模式。

**工作逻辑**：与 flink/v1.19/v1.20 完全一致的修改。

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkIcebergSinkV2DistributionMode.java` (新增, +539/-0 lines)

**修改目的**：为 Flink 2.0 的 RANGE 分布模式提供测试覆盖。

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/sink/TestIcebergSink.java` (修改, +0/-70 lines)

**修改目的**：移除已迁移到新测试类的旧测试代码。

## 总结

该提交将 RANGE 分布模式支持从 flink/v1.20 backport 到 flink/v1.19 和 flink/v2.0 模块，确保三个 Flink 版本的 IcebergSink 功能一致。修改内容与提交 2203 完全相同，只是目标模块不同。属于跨版本功能同步，无新的逻辑设计。
