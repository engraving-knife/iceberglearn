# 提交 1901：Spark 3.4: Rewrite data files with high delete ratio

## 提交信息

- **序号**：1901 / 4088
- **哈希**：980212e7000f7a62439e8876cc555a5066147df6
- **短哈希**：980212e70
- **日期**：2025-03-22 10:41:54 -0600
- **作者**：Eduard Tudenhoefner
- **提交说明**：Spark 3.4: Rewrite data files with high delete ratio

  this backports #11825 to Spark 3.4
- **PR/Issue**：backport #11825

## 总体目的

这个提交将"高删除比例文件重写"功能向后移植到 Spark 3.4。该功能允许 Iceberg 在数据文件关联的删除记录比例过高时，自动触发数据文件重写（compaction），以恢复查询性能。

在 Iceberg 中，当数据被删除或更新时，会生成 position delete 或 equality delete 文件来标记被删除的行。随着删除操作累积，查询时需要将数据文件与大量删除文件进行关联（apply deletes），这会显著降低查询性能。`RewriteDataFiles` action 用于合并小文件并应用删除，但需要判断哪些文件值得重写。

本提交引入了"文件级删除比例"（file-scoped delete ratio）的概念。当一个数据文件关联的删除记录数占其总记录数的比例超过某个阈值时，该文件会被标记为需要重写。此前 `SizeBasedDataRewriter` 主要基于文件大小来决定是否重写，现在新增了基于删除比例的判断维度。

## 如何达成设计目的

整体设计思路是在 `SizeBasedDataRewriter` 中新增 `DELETE_FILE_THRESHOLD`（删除文件阈值）配置项和对应的过滤逻辑，并添加基于删除比例的文件选择策略。当数据文件的删除记录比例超过阈值时，即使文件大小没有超过 `MIN_FILE_SIZE_BYTES` 阈值，也会被纳入重写范围。

## 修改详情

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/actions/TestSparkFileRewriter.java` (修改, +22 lines)

**修改目的**：新增高删除比例文件的重写测试。

**工作逻辑**：新增 `checkDataFilesWithHighFileScopedDeleteRatio` 方法。创建两个 mock task：
- `tooManyDeletesTask`：1000 行数据，100 条删除记录，删除比例 30%（超过阈值）
- `optimalTask`：1000 行数据，100 条删除记录，删除比例 29%（未超阈值）

配置 `DELETE_FILE_THRESHOLD=10` 和 `MIN_FILE_SIZE_BYTES=0`，验证 `planFileGroups` 只返回 1 个组，且组中包含 1 个文件（即高删除比例的文件被选中重写）。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteDataFilesAction.java` (修改, +391 lines)

**修改目的**：新增大量基于高删除比例重写的集成测试。

**工作逻辑**：扩展测试类，新增多个测试用例验证在不同删除比例场景下 `RewriteDataFiles` action 的行为，包括文件级删除比例计算、重写触发条件、重写后删除文件清理等。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteManifestsAction.java` (修改, +2/-1 lines)

**修改目的**：少量适配性修改。

### spark/v3.5 对应文件 (修改, +2/-1 lines)

**修改目的**：Spark 3.5 模块的同步适配。

## 总结

本提交将高删除比例数据文件重写功能 backport 到 Spark 3.4。通过新增基于文件级删除比例的文件选择策略，使 `RewriteDataFiles` action 能在删除记录累积影响性能时自动触发文件重写，恢复查询性能。主要变更在测试层面验证该功能。
