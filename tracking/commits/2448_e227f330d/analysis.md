# 提交 2448：Revert "Flink: disable flaky testRangeDistributionStatisticsMigration() (#13711)"

## 提交信息

- **序号**：2448 / 4088
- **哈希**：e227f330d2ae93c0ea0f938fd950df30bf5b138b
- **短哈希**：e227f330d
- **日期**：2025-08-04 09:22:03 -0700
- **作者**：Steven Wu
- **提交说明**：Revert "Flink: disable flaky testRangeDistributionStatisticsMigration() (#13711)"
- **PR/Issue**：#13711（reverts 提交 b7b56fd90，即本批次中的 2436）

## 总体目的

本提交 revert 了 2436（b7b56fd90）提交，即重新启用了此前被禁用的 `testRangeDistributionStatisticsMigration()` 测试方法。

2436 提交因该测试存在 flaky 问题（issue-11815）而临时禁用了三个 Flink 版本（1.19、1.20、2.0）下共 6 处 `testRangeDistributionStatisticsMigration()` 测试，以恢复 CI 稳定性。本 revert 提交移除了这些 `@Disabled` 注解和对应的 import，使测试重新执行。

revert 的原因可能是：flaky 问题已在其他地方修复，或者经过评估认为该测试的 flaky 程度可接受、不应长期禁用，又或者需要通过测试运行来进一步诊断问题。从提交时间看，2436（2025-07-30）和本 revert（2025-08-01，author date）相隔仅两天，说明禁用决策被较快推翻。

## 如何达成设计目的

通过 `git revert` 机制，精确地反向应用 2436 的改动：移除每个测试文件中的 `import org.junit.jupiter.api.Disabled;` 和方法上的 `@Disabled("issue-11815: flaky test")` 注解。

## 修改详情

### 6 个测试文件（各 +0/-2 lines）

以下 6 个文件各移除 1 行 import 和 1 行 `@Disabled` 注解：

- `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkIcebergSinkDistributionMode.java`
- `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkIcebergSinkV2DistributionMode.java`
- `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkIcebergSinkDistributionMode.java`
- `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkIcebergSinkV2DistributionMode.java`
- `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkIcebergSinkDistributionMode.java`
- `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkIcebergSinkV2DistributionMode.java`

**修改目的**：重新启用被禁用的 `testRangeDistributionStatisticsMigration()` 测试。

**工作逻辑**：每个文件移除 `import org.junit.jupiter.api.Disabled;`，并移除 `testRangeDistributionStatisticsMigration()` 方法上的 `@Disabled("issue-11815: flaky test")` 注解。测试方法本身的内容完全不变，恢复为正常可执行状态。

## 总结

本提交 revert 了 2436，重新启用了三个 Flink 版本下共 6 处 `testRangeDistributionStatisticsMigration()` 测试。2436 的临时禁用仅维持了两天即被推翻，说明 flaky 问题可能已被解决或禁用决策被重新评估。这是一个纯测试状态恢复提交，不涉及功能逻辑变更。
