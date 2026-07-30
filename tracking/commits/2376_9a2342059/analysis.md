# 提交 2376：Spark: Fix flaky testParallelPartialProgressWithMaxFailedCommitsLargerThanTotalFileGroup (#13598)

## 提交信息

- **序号**：2376 / 4088
- **哈希**：9a23420592e2b8be5f792c8e6eb32a64e92e4088
- **短哈希**：9a2342059
- **日期**：2025-07-21 12:34:35 +0200
- **作者**：Manu Zhang
- **提交说明**：Spark: Fix flaky testParallelPartialProgressWithMaxFailedCommitsLargerThanTotalFileGroup (#13598)
- **PR/Issue**：#13598

## 总体目的

本提交修复了一个不稳定的（flaky）测试用例 `testParallelPartialProgressWithMaxFailedCommitsLargerThanTotalFileGroup`。该测试用例验证在最大失败提交数大于总文件组数时，数据文件重写（RewriteDataFiles）操作的行为。

原有测试断言表在重写后应该恰好有 11 个快照（snapshots），但实际上由于在并行部分进度模式下可能发生随机的提交失败（random commit failure），实际快照数量可能不是恰好 11 个。当发生 1 次随机提交失败时，会产生额外的重试提交，导致快照数量增加。这种不精确的断言导致测试在某些情况下随机失败，影响 CI 流程的稳定性。

## 如何达成设计目的

设计思路是将严格相等断言放宽为范围断言，以容忍可能的随机提交失败。关键设计点如下：

1. **刷新表状态**：在断言前先调用 `table.refresh()` 确保获取最新的表元数据状态。
2. **放宽断言**：将 `shouldHaveSnapshots(table, 11)`（恰好 11 个快照）改为 `assertThat(table.snapshots()).hasSizeGreaterThanOrEqualTo(10)`（至少 10 个快照），容忍 1 次随机提交失败。
3. **添加描述性说明**：使用 `as()` 方法添加断言失败时的描述信息，并添加注释说明容忍 1 次随机提交失败的原因。

## 修改详情

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteDataFilesAction.java` (+5/-1 lines)

**修改目的**：修复 Spark 3.4 版本中的 flaky 测试。

**工作逻辑**：将原有的 `shouldHaveSnapshots(table, 11)` 替换为：先调用 `table.refresh()` 刷新表状态，然后使用 AssertJ 的 `assertThat` 断言 `table.snapshots()` 的大小 `hasSizeGreaterThanOrEqualTo(10)`，添加了描述信息和注释说明容忍 1 次随机提交失败。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteDataFilesAction.java` (+5/-1 lines)

**修改目的**：修复 Spark 3.5 版本中的相同 flaky 测试，修改内容与 3.4 版本完全相同。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteDataFilesAction.java` (+5/-1 lines)

**修改目的**：修复 Spark 4.0 版本中的相同 flaky 测试，修改内容与 3.4 版本完全相同。

## 总结

本提交修复了一个 flaky 测试，将严格的快照数量相等断言（恰好 11 个）改为范围断言（至少 10 个），以容忍并行部分进度模式下可能发生的 1 次随机提交失败。修改涉及 Spark 3.4、3.5 和 4.0 三个版本的测试文件，每个文件都进行了相同的修改。这是一个测试健壮性改进，不影响生产代码逻辑，但显著提升了 CI 流程的稳定性。
