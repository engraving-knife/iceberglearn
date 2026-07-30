# 提交 2206：Spark: Fix flaky testParallelPartialProgressWithMaxFailedCommitsLargerThanTotalFileGroup (#13208)

## 提交信息

- **序号**：2206 / 4088
- **哈希**：5b62fecde3158cf43f90ad40af40091d2d13b6c3
- **短哈希**：5b62fecde
- **日期**：2025-06-04 18:08:52 +0800
- **作者**：Manu Zhang
- **提交说明**：Spark: Fix flaky testParallelPartialProgressWithMaxFailedCommitsLargerThanTotalFileGroup (#13208)
- **PR/Issue**：#13208

## 总体目的

这个提交修复了一个不稳定的（flaky）Spark 测试 `testParallelPartialProgressWithMaxFailedCommitsLargerThanTotalFileGroup`。该测试验证在 partial progress 模式下，当 max-commits 大于文件组数量时的行为。测试原本将 `PARTIAL_PROGRESS_MAX_FAILED_COMMITS` 设置为 0，即不允许任何提交失败。然而在实际 CI 环境中，由于随机因素（如并发提交冲突、资源竞争等），偶尔会出现提交失败的情况，导致 max-failed-commits=0 时测试因意外失败而 flaky。本提交通过将 max-failed-commits 从 0 改为 1，容忍一次随机提交失败，从而消除测试的 flakiness。同时将测试方法名改为更准确的 `testParallelPartialProgressWithMaxCommitsLargerThanTotalGroupCount`，因为测试的核心是验证 max-commits 大于文件组数量的场景，而非 max-failed-commits。

## 如何达成设计目的

- 在三个 Spark 版本（v3.4、v3.5、v4.0）的 `TestRewriteDataFilesAction` 测试类中，将 `PARTIAL_PROGRESS_MAX_FAILED_COMMITS` 选项值从 `"0"` 改为 `"1"`，容忍一次随机提交失败。
- 添加注释说明"Setting max-failed-commits to 1 to tolerate random commit failure"。
- 将测试方法名从 `testParallelPartialProgressWithMaxFailedCommitsLargerThanTotalFileGroup` 重命名为 `testParallelPartialProgressWithMaxCommitsLargerThanTotalGroupCount`，更准确反映测试意图。

## 修改详情

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteDataFilesAction.java` (修改, +3/-2 lines)

**修改目的**：修复 flaky 测试。

**工作逻辑**：将测试方法名重命名为 `testParallelPartialProgressWithMaxCommitsLargerThanTotalGroupCount`，将 `.option(RewriteDataFiles.PARTIAL_PROGRESS_MAX_FAILED_COMMITS, "0")` 改为 `.option(RewriteDataFiles.PARTIAL_PROGRESS_MAX_FAILED_COMMITS, "1")`，并添加注释说明容忍随机提交失败。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteDataFilesAction.java` (修改, +3/-2 lines)

**修改目的**：同上，修复 v3.5 版本的 flaky 测试。

**工作逻辑**：与 v3.4 相同的修改。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteDataFilesAction.java` (修改, +3/-2 lines)

**修改目的**：同上，修复 v4.0 版本的 flaky 测试。

**工作逻辑**：与 v3.4 相同的修改。

## 总结

该提交修复了一个 flaky 测试，通过将 `PARTIAL_PROGRESS_MAX_FAILED_COMMITS` 从 0 调整为 1 来容忍 CI 环境中偶尔出现的随机提交失败，并将测试方法名改为更准确的描述。修改覆盖三个 Spark 版本（3.4/3.5/4.0），属于测试稳定性改进，不影响生产代码。
