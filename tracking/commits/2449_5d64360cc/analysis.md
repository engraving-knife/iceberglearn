# 提交 2449：Flink: fix testRangeDistributionStatisticsMigration flakiness by adjusting the setup

## 提交信息

- **序号**：2449 / 4088
- **哈希**：5d64360cc80f829cbd9330aefb772c7f09575bb1
- **短哈希**：5d64360cc
- **日期**：2025-08-04 09:22:03 -0700
- **作者**：Steven Wu
- **提交说明**：Flink: fix testRangeDistributionStatisticsMigration flakiness by adjusting the setup
- **PR/Issue**：无明确 PR 编号（提交信息中未包含）

## 总体目的

该提交修复了 Flink Sink 中 `testRangeDistributionStatisticsMigration` 测试的 flaky（不稳定）问题。该测试验证的是在 RANGE 分布模式下，当数据量超过阈值（`OPERATOR_SKETCH_SWITCH_THRESHOLD` = 10,000）时，统计信息能够正确地从一种模式迁移到另一种模式（sketch 切换）。

原有测试设置存在不稳定性：测试只有 4 个 checkpoint，且从 checkpoint 1 开始就发射 11,000 条记录。这种设置可能导致统计迁移的时机不确定，因为第一个 checkpoint 的数据量较小（1,000 条），随后立即进入大数据量阶段，统计迁移的触发条件和验证窗口过于紧凑，容易受运行环境影响而出现偶发失败。

## 如何达成设计目的

通过调整测试的设置参数来消除不稳定性：

1. **增加 checkpoint 数量**：从 4 个增加到 6 个，为统计迁移提供更充裕的验证窗口。
2. **延后大数据量发射时机**：将发射 11,000 条记录的起始 checkpoint 从 `checkpointId < 1`（即第 2 个 checkpoint 开始）改为 `checkpointId < 2`（即第 3 个 checkpoint 开始），这样前两个 checkpoint 都发射小数据量（1,000 条），为统计状态建立更稳定的基础。

这些修改让测试有更多的小数据量 checkpoint 来建立稳定的初始统计状态，然后再触发大数据量迁移，从而减少偶发性失败。

## 修改详情

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkIcebergSinkDistributionMode.java` (+2/-2 lines)

**修改目的**：调整 V1.19 版本的分布模式测试参数以消除 flakiness。

**工作逻辑**：
- `numOfCheckpoints` 从 `4` 改为 `6`
- `maxId` 的判断条件从 `checkpointId < 1` 改为 `checkpointId < 2`

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkIcebergSinkV2DistributionMode.java` (+2/-2 lines)

**修改目的**：对 V2 Sink 的分布模式测试应用相同的修复。

**工作逻辑**：与上述 V1 版本相同的参数调整。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkIcebergSinkDistributionMode.java` (+2/-2 lines)

**修改目的**：对 Flink 1.20 版本应用相同的修复。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkIcebergSinkV2DistributionMode.java` (+2/-2 lines)

**修改目的**：对 Flink 1.20 V2 Sink 应用相同的修复。

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkIcebergSinkDistributionMode.java` (+2/-2 lines)

**修改目的**：对 Flink 2.0 版本应用相同的修复。

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkIcebergSinkV2DistributionMode.java` (+2/-2 lines)

**修改目的**：对 Flink 2.0 V2 Sink 应用相同的修复。

## 总结

这是一个纯测试修复提交，通过增加 checkpoint 数量和延后大数据量发射时机来消除 `testRangeDistributionStatisticsMigration` 测试的偶发性失败。修改覆盖了 Flink 1.19、1.20 和 2.0 三个版本的 V1 和 V2 Sink 测试，确保所有支持的 Flink 版本都能稳定通过该测试。该提交不涉及生产代码的修改，对实际功能无影响。
