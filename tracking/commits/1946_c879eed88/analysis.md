# 提交 1946：Spark 3.5: Fix RewriteDataFiles with partial progress enabled and max-failed-commits larger than total-file-group (#12120)

## 提交信息

- **序号**：1946 / 4088
- **哈希**：c879eed88669aa5439b841a1105bc4e796992eb5
- **短哈希**：c879eed88
- **日期**：2025-04-01 09:48:52 -0500
- **作者**：Manu Zhang
- **提交说明**：Spark 3.5: Fix RewriteDataFiles with partial progress enabled and max-failed-commits larger than total-file-group (#12120)
- **PR/Issue**：#12120

## 总体目的

此提交修复 Spark 3.5 的 `RewriteDataFilesSparkAction` 在启用部分进度（partial progress）时，当配置的 `max-commits`（最大提交数）大于实际文件分组总数时，错误计算失败提交数导致重写任务误判失败的 bug。

Iceberg 的 `RewriteDataFiles` 操作在启用 `partial-progress-enabled` 时，允许部分文件分组提交失败但仍视为整体成功（只要失败数不超过 `partial-progress-max-failed-commits` 阈值）。原代码计算失败提交数的逻辑为：

```java
int failedCommits = maxCommits - commitService.succeededCommits();
```

其中 `maxCommits` 是用户配置的 `PARTIAL_PROGRESS_MAX_COMMITS`（期望的最大提交数），`succeededCommits()` 是实际成功提交数。

问题在于：实际的提交数不可能超过文件分组总数（每个文件分组最多一次提交），即 `ctx.totalGroupCount()`。当用户配置的 `maxCommits` 大于实际文件分组数时（例如配置 20 但只有 10 个文件组），即使所有 10 个分组都成功提交，`failedCommits = 20 - 10 = 10`，会被误判为 10 个失败提交。若 `maxFailedCommits` 阈值小于该误算值（如设为 0），整个重写会错误地抛出失败，而实际上没有任何提交失败。

本提交将 `totalCommits` 上限为 `Math.min(ctx.totalGroupCount(), maxCommits)`，再计算 `failedCommits = totalCommits - succeededCommits()`，确保失败数不超过实际可能的提交数，修复误判。

## 如何达成设计目的

设计思路是认识到"实际提交数 = min(文件分组数, 配置的最大提交数)"，因为每个文件分组最多产生一次提交。修复将 `failedCommits` 的基数从配置的 `maxCommits` 改为实际的 `totalCommits`（取两者较小值），使失败数计算基于实际可能提交数而非配置值。这避免了当 `maxCommits` 配置过大于实际文件组数时，把"未发生的提交"误算为"失败的提交"。

## 修改详情

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteDataFilesSparkAction.java` (修改, +3/-1 lines)

**修改目的**：修复失败提交数的计算。

**工作逻辑**：
- 原：`int failedCommits = maxCommits - commitService.succeededCommits();`
- 改：先 `int totalCommits = Math.min(ctx.totalGroupCount(), maxCommits);`（实际可能提交数 = 文件分组总数与配置最大提交数的较小值），再 `int failedCommits = totalCommits - commitService.succeededCommits();`。
- 后续 `if (failedCommits > 0 && failedCommits <= maxFailedCommits)` 的阈值判断逻辑不变，但现在 `failedCommits` 基于实际提交数计算，不会因 `maxCommits` 配置过大而产生虚假失败数。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteDataFilesAction.java` (修改, +28 lines)

**修改目的**：新增针对该 bug 的回归测试。

**工作逻辑**：新增 `testParallelPartialProgressWithMaxFailedCommitsLargerThanTotalFileGroup` 测试：
- 创建 20 个文件的表，设置 `MAX_FILE_GROUP_SIZE_BYTES` 使文件分为 10 个分组（每组 2 文件）。
- 配置 `MAX_CONCURRENT_FILE_GROUP_REWRITES=3`、`PARTIAL_PROGRESS_ENABLED=true`、`PARTIAL_PROGRESS_MAX_COMMITS=20`（大于实际 10 个分组）、`PARTIAL_PROGRESS_MAX_FAILED_COMMITS=0`（不允许任何失败）。
- 注释说明：实际最多 10 次提交（每组一次），`MAX_COMMITS=20` 不会导致虚假失败。
- 执行重写，验证数据未改变、有 11 个快照（1 原始 + 10 个重写提交）、无孤儿文件、缓存已清理。
- 若无修复，此测试会因 `failedCommits = 20 - 10 = 10 > 0` 而失败。

## 总结

本次提交修复 Spark 3.5 `RewriteDataFilesSparkAction` 在 partial progress 模式下，当配置的 `max-commits` 大于实际文件分组总数时，误将"未发生的提交"计为失败提交而导致重写误判失败的 bug。修复将失败数计算的基数从配置的 `maxCommits` 改为 `Math.min(totalGroupCount, maxCommits)`（实际可能提交数）。新增回归测试覆盖该场景（10 个文件组、max-commits=20、max-failed-commits=0 应成功）。
