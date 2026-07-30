# 提交 2546：Spark 3.4, 3.5: Fix errorprone warnings (#13897)

## 提交信息

- **序号**：2546 / 4088
- **哈希**：32b86f34df7add9b6667e66d6110854df2186e78
- **短哈希**：32b86f34d
- **日期**：2025-08-22 11:40:11 +0200
- **作者**：Yuya Ebihara
- **提交说明**：Spark 3.4, 3.5: Fix errorprone warnings (#13897)
- **PR/Issue**：#13897

## 总体目的

#13896（提交 2545）修复了 Spark v4.0 模块的 ErrorProne 告警。本提交把相同的修复应用到 Spark v3.4 与 v3.5 模块，使三个 Spark 版本的代码质量一致，均通过 ErrorProne 静态检查。

修复内容与 #13896 完全对应：
- 补 `@Override` 注解（`MissingOverride`）。
- 为 `String.format` 显式传入 `Locale.ROOT`（`DefaultLocale`）。
- 把字符串拼接改为 `%s` 模板参数（`StringConcat`）。
- 删除未使用的方法、字段、参数、import（`UnusedVariable` 等）。
- `var` 改为显式 `boolean` 类型。
- Lambda 参数去括号。
- 日志调用补全异常参数。

所有修改均为等价重构，不改变运行时行为。由于 v3.4/3.5 与 v4.0 的代码结构基本一致，修复模式完全相同。

## 如何达成设计目的

- 在 `spark/v3.4` 与 `spark/v3.5` 两个模块下，对与 v4.0 对应的文件应用相同的 ErrorProne 修复。
- 每个版本的修复文件列表与 v4.0 基本一致（个别文件如 `ComputePartitionStatsProcedure`、`FastForwardBranchProcedure`、`SparkPlannedAvroReader` 在 v3.4/3.5 中不存在对应版本或无告警，故略有差异）。

## 修改详情

### 修改的文件类型（v3.4 与 v3.5 各约 28 个文件）

**修改目的**：批量修复 ErrorProne 告警。

**工作逻辑**（按告警类型）：
- **MissingOverride**：`SupportsFunctions`、`RemoveDanglingDeletesSparkAction`、`RewriteTablePathSparkAction`、`IcebergArrowColumnVector`（仅 3.5）、`PublishChangesProcedure`、`SetCurrentSnapshotProcedure`、`SparkCopyOnWriteScan`、`SparkStagedScan`、`StreamingOffset`、`StructInternalRow`、`NumDeletes`、`NumSplits` 等添加 `@Override`。
- **DefaultLocale**：`SparkTableUtil`、`RewriteDataFilesSparkAction`、`RewritePositionDeleteFilesSparkAction`、`TruncateFunction`、`StreamingOffset`、`StructInternalRow`、`SparkWrite` 等为 `String.format` 添加 `Locale.ROOT`。
- **StringConcat**：`SparkCatalog`、`SparkChangelogScan`、`SparkCleanupUtil`、`SparkMicroBatchStream`、`SparkPartitioningAwareScan`、`SparkPositionDeltaWrite`、`SparkScanBuilder`、`SparkWrite` 等把字符串拼接改为 `%s` 模板。
- **UnusedVariable/Method/Parameter**：
  - `DeleteOrphanFilesSparkAction` 删除 `uriComponentMatch` 私有方法与 `Strings` import，`LOG.warn` 补异常参数。
  - `VectorizedSparkOrcReaders` 删除 `batchOffsetInFile` 字段与构造参数。
  - `CreateChangelogViewProcedure` 把 `var` 改为 `boolean`。
- **Lambda**：`SparkShufflingFileRewriteRunner` 把 `(df) ->` 改为 `df ->`。

## 总结

将 #13896 对 Spark v4.0 的 ErrorProne 告警修复同步应用到 Spark v3.4 与 v3.5，对约 28 个文件/版本进行相同的等价重构（补 `@Override`、加 `Locale.ROOT`、拼接改模板、删未用代码、`var` 改类型、lambda 简化、补日志异常参数），使三个 Spark 版本代码质量一致，均通过 ErrorProne 检查。不改变运行时行为。
