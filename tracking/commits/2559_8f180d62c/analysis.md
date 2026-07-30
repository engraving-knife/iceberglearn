# 提交 2559：Spark 3.4, 3.5: Backport #13881 to fix source location in stats file copy plan in RewriteTablePathSparkAction

## 提交信息

- **序号**：2559 / 4088
- **哈希**：8f180d62cd7ed95a645e0c4436d96cdb24683b29
- **短哈希**：8f180d62c
- **日期**：2025-08-25 07:00:12 -0700
- **作者**：Anurag Mantripragada
- **提交说明**：Spark 3.4, 3.5: Backport #13881 to fix source location in stats file copy plan in RewriteTablePathSparkAction
- **PR/Issue**：backport #13881

## 总体目的

该提交将 PR #13881 的修复反向移植（backport）到 Spark 3.4 和 3.5 版本，修复了 `RewriteTablePathSparkAction` 中统计文件（statistics file）复制计划的源路径错误问题。

`RewriteTablePathSparkAction` 用于重写表的文件路径（例如从源存储迁移到目标存储），它会生成一个文件复制计划（file list），其中包含源路径到目标路径的映射。在处理统计文件时，原代码错误地将源路径设置为暂存目录（staging directory）的路径，而不是原始源表中的实际路径。这导致文件复制计划中的源路径指向了错误的暂存位置，实际执行复制时会因为找不到文件而失败。

修复将源路径改回使用统计文件的原始路径（`before.path()`），而不是通过 `RewriteTablePathUtil.stagingPath()` 计算的暂存路径，确保文件复制计划能正确找到统计文件。

## 如何达成设计目的

- 在 `genStatsFileCopyPlan`（或类似方法）中，将统计文件复制对的源路径从 `RewriteTablePathUtil.stagingPath(before.path(), sourcePrefix, stagingDir)` 改为直接使用 `before.path()`。
- 新增测试 `testStatisticsFileSourcePath` 验证统计文件复制计划中的源路径指向原始源表位置而非暂存目录。

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteTablePathSparkAction.java` (+1/-4)

**修改目的**：修复统计文件复制计划的源路径。

**工作逻辑**：原代码通过 `RewriteTablePathUtil.stagingPath(before.path(), sourcePrefix, stagingDir)` 计算暂存路径作为源路径，修改后直接使用 `before.path()` 作为源路径，确保指向统计文件在源表中的实际位置。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteTablePathsAction.java` (+50/-0)

**修改目的**：新增测试验证统计文件源路径正确性。

**工作逻辑**：`testStatisticsFileSourcePath` 测试创建源表并执行 `computeTableStats` 生成统计文件，然后执行路径重写，读取文件复制计划，查找 `.stats` 文件条目，验证源路径以源表位置开头、包含 `/metadata/`、不包含 `staging`，且目标路径以目标表位置开头。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteTablePathSparkAction.java` (+1/-4)

**修改目的**：同 v3.4 的修复，为 Spark 3.5 应用相同修改。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteTablePathsAction.java` (+50/-0)

**修改目的**：同 v3.4 的测试，为 Spark 3.5 应用相同测试。

## 总结

该提交修复了 `RewriteTablePathSparkAction` 中统计文件复制计划源路径错误的 bug，将源路径从暂存目录改为原始路径，并补充了对应的测试用例。修复同时应用于 Spark 3.4 和 3.5 两个版本。
