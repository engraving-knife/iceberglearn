# 提交 2436：Flink: disable flaky testRangeDistributionStatisticsMigration() (#13711)

## 提交信息

- **序号**：2436 / 4088
- **哈希**：b7b56fd90c2178a5cc1a6b4314b63446b1c1c0ec
- **短哈希**：b7b56fd90
- **日期**：2025-07-30 15:08:37 -0700
- **作者**：Steven Zhen Wu
- **提交说明**：Flink: disable flaky testRangeDistributionStatisticsMigration() (#13711)
- **PR/Issue**：#13711

## 总体目的

本提交在三个 Flink 版本（1.19、1.20、2.0）的 6 个测试文件中，临时禁用了不稳定的（flaky）测试方法 `testRangeDistributionStatisticsMigration()`。该测试用于验证 Flink Sink 在 RANGE 分布模式下从 Map 统计到 Sketch 统计的迁移逻辑，但由于存在已知的 flaky 问题（issue-11815），会导致 CI 构建间歇性失败，影响开发效率。

禁用 flaky 测试是一种常见的临时措施，目的是先恢复 CI 的稳定性，待后续修复根本原因后再重新启用。每个被禁用的方法都添加了 `@Disabled("issue-11815: flaky test")` 注解，明确标注了关联的 issue 编号，便于追踪。

注意：提交 2448 随后 revert 了本提交，重新启用了该测试。

## 如何达成设计目的

在每个测试类的 `testRangeDistributionStatisticsMigration` 方法上添加 JUnit 5 的 `@Disabled` 注解，并 import `org.junit.jupiter.api.Disabled`。

## 修改详情

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkIcebergSinkDistributionMode.java` (+2/-0 lines)

**修改目的**：禁用 v1.19 Sink V1 的 flaky 测试。

**工作逻辑**：新增 `import org.junit.jupiter.api.Disabled;`，在 `testRangeDistributionStatisticsMigration()` 方法上添加 `@Disabled("issue-11815: flaky test")` 注解。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkIcebergSinkV2DistributionMode.java` (+2/-0 lines)

**修改目的**：禁用 v1.19 Sink V2 的 flaky 测试。

**工作逻辑**：同上，添加 import 和 `@Disabled` 注解。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkIcebergSinkDistributionMode.java` (+2/-0 lines)

**修改目的**：禁用 v1.20 Sink V1 的 flaky 测试。

**工作逻辑**：同上。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkIcebergSinkV2DistributionMode.java` (+2/-0 lines)

**修改目的**：禁用 v1.20 Sink V2 的 flaky 测试。

**工作逻辑**：同上。

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkIcebergSinkDistributionMode.java` (+2/-0 lines)

**修改目的**：禁用 v2.0 Sink V1 的 flaky 测试。

**工作逻辑**：同上。

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkIcebergSinkV2DistributionMode.java` (+2/-0 lines)

**修改目的**：禁用 v2.0 Sink V2 的 flaky 测试。

**工作逻辑**：同上。

## 总结

本提交临时禁用了三个 Flink 版本下共 6 处 flaky 测试 `testRangeDistributionStatisticsMigration()`，以恢复 CI 稳定性。每个禁用都标注了 issue-11815 以便追踪。需要注意的是，该提交随后被 2448 revert，测试被重新启用。
