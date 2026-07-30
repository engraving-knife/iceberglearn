# 提交 1949：Spark 3.4: Fix RewriteDataFiles with partial progress enabled and max-failed-commits larger than total-file-group (#12701)

## 提交信息

- **序号**：1949 / 4088
- **哈希**：ee0190d00a24dae34d3e9fe4f168ef95bf924bd9
- **短哈希**：ee0190d00
- **日期**：2025-04-02 08:12:21 +0200
- **作者**：Manu Zhang
- **提交说明**：Spark 3.4: Fix RewriteDataFiles with partial progress enabled and max-failed-commits larger than total-file-group (#12701)
- **PR/Issue**：#12701

## 总体目的

在 Spark 3.4 的 `RewriteDataFilesSparkAction` 中，当启用部分进度（partial progress）并且 `max-failed-commits`（PARTIAL_PROGRESS_MAX_FAILED_COMMITS）的值大于实际文件组（file group）总数时，会计算出错误的"失败提交数"，从而错误地抛出异常。

问题的根因在于：原有代码使用 `maxCommits`（即 PARTIAL_PROGRESS_MAX_COMMITS 用户配置值）减去 `succeededCommits()` 来计算失败提交数 `failedCommits`。但是每个文件组最多只会产生一次提交，因此实际可能发生的最大提交数不会超过文件组总数 `totalGroupCount()`。当用户把 `maxCommits` 设置得比文件组总数还大（例如本例中文件组只有 10 个，但 maxCommits 配为 20），就会把"未触发的提交"也计为"失败提交"，从而超过 `maxFailedCommits` 阈值并抛出异常，即使所有真实提交都成功了。

本提交通过将 `maxCommits` 与 `totalGroupCount()` 取较小值，得到实际的总提交数 `totalCommits`，再据此计算 `failedCommits`，修复了这个错误判断。

## 如何达成设计目的

修改集中在 `RewriteDataFilesSparkAction` 的提交阶段统计逻辑：

1. 计算 `totalCommits = Math.min(ctx.totalGroupCount(), maxCommits)`，保证总提交数不超过文件组总数，避免把不可能发生的提交算作失败。
2. 用 `failedCommits = totalCommits - commitService.succeededCommits()` 计算真实失败数。
3. 保留后续对 `failedCommits > 0 && failedCommits <= maxFailedCommits` 的告警/抛错判断逻辑不变。

同时新增测试用例覆盖该场景：构造 20 个文件、分 10 个文件组、`PARTIAL_PROGRESS_MAX_COMMITS=20`、`PARTIAL_PROGRESS_MAX_FAILED_COMMITS=0`，验证重写成功且数据不变、快照数为 11（10 个文件组提交 + 1 个原始快照）。

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteDataFilesSparkAction.java` (修改, +2/-1 lines)

**修改目的**：修正失败提交数的计算，避免在 maxCommits 大于文件组总数时误判。

**工作逻辑**：
```java
int totalCommits = Math.min(ctx.totalGroupCount(), maxCommits);
int failedCommits = totalCommits - commitService.succeededCommits();
```
将原本直接用 `maxCommits` 作为分母改为先与文件组总数取最小值，从而保证只统计实际可能发生的提交。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteDataFilesAction.java` (修改, +28/-0 lines)

**修改目的**：新增回归测试覆盖该 bug 场景。

**工作逻辑**：新增测试方法 `testParallelPartialProgressWithMaxFailedCommitsLargerThanTotalFileGroup`，创建 20 条数据的表，设置 `MAX_FILE_GROUP_SIZE_BYTES` 使其分成 10 个文件组，并配置 `MAX_CONCURRENT_FILE_GROUP_REWRITES=3`、`PARTIAL_PROGRESS_ENABLED=true`、`PARTIAL_PROGRESS_MAX_COMMITS=20`（大于实际文件组数 10）、`PARTIAL_PROGRESS_MAX_FAILED_COMMITS=0`。执行重写后验证数据未改变、快照数为 11、无孤儿文件、缓存干净。

## 总结

本提交修复了 Spark 3.4 中 `RewriteDataFilesSparkAction` 在 partial progress 模式下，当用户配置的 `max-failed-commits`（实际为 max-commits）大于文件组总数时，错误地认为有大量失败提交并抛出异常的 bug。核心改动是用 `Math.min(totalGroupCount, maxCommits)` 限制总提交数，使失败提交数的统计基于实际可能的提交上限。
