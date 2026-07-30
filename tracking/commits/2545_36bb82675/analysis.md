# 提交 2545：Spark: Fix errorprone warnings (#13896)

## 提交信息

- **序号**：2545 / 4088
- **哈希**：36bb82675ff68ac0ed059d4db62550d30aa35760
- **短哈希**：36bb82675
- **日期**：2025-08-22 06:46:12 +0200
- **作者**：Yuya Ebihara
- **提交说明**：Spark: Fix errorprone warnings (#13896)
- **PR/Issue**：#13896

## 总体目的

ErrorProne 是 Google 的 Java 静态分析工具，用于在编译期捕获常见 bug 与代码质量问题。Iceberg 的 Spark v4.0 模块在 ErrorProne 检查中存在多处告警，例如：
- `String.format`/`Preconditions.check` 使用字符串拼接而非 `%s` 模板参数（`StringConcat` 告警）。
- `String.format` 未显式指定 `Locale`（`DefaultLocale` 告警，可能导致不同 locale 下格式化结果不一致）。
- 实现/重写父类接口方法时缺少 `@Override` 注解（`MissingOverride` 告警）。
- 未使用的私有方法、字段、import、参数（`UnusedVariable`/`UnusedMethod`/`UnusedParameter` 告警）。
- `var` 使用过于宽泛，应改为显式类型（部分 ErrorProne 配置建议）。
- Lambda 参数多余括号等代码风格问题。

本提交批量修复 Spark v4.0 模块下 31 个文件的 ErrorProne 告警，使代码通过更严格的静态检查，提升代码质量与一致性。修改均为等价重构，不改变运行时行为。

## 如何达成设计目的

按告警类型分类处理：
- **`MissingOverride`**：在重写父类/接口方法处添加 `@Override` 注解。涉及 `SupportsFunctions`、`RemoveDanglingDeletesSparkAction`、`RewriteTablePathSparkAction`、`SparkPlannedAvroReader`、`IcebergArrowColumnVector`、`ComputePartitionStatsProcedure`、`FastForwardBranchProcedure`、`PublishChangesProcedure`、`SetCurrentSnapshotProcedure`、`SparkCopyOnWriteScan`、`SparkStagedScan`、`StreamingOffset`、`StructInternalRow`、`NumDeletes`、`NumSplits` 等。
- **`DefaultLocale`**：为 `String.format` 调用显式传入 `Locale.ROOT`，避免 locale 相关格式化差异。涉及 `SparkTableUtil`、`RewriteDataFilesSparkAction`、`RewritePositionDeleteFilesSparkAction`、`TruncateFunction`、`StreamingOffset`、`StructInternalRow` 等。
- **`StringConcat`**：把 `Preconditions.check(... "msg " + var)` 改为 `Preconditions.check(... "msg %s", var)`，或把日志/异常消息的字符串拼接改为 `%s` 模板。涉及 `SparkCatalog`、`SparkCleanupUtil`、`SparkPartitioningAwareScan`、`SparkPositionDeltaWrite`、`SparkScanBuilder`、`SparkMicroBatchStream` 等。
- **`UnusedVariable`/`UnusedMethod`/`UnusedParameter`**：
  - `DeleteOrphanFilesSparkAction` 删除未使用的 `uriComponentMatch` 私有方法与 `Strings` import。
  - `VectorizedSparkOrcReaders` 删除未使用的 `batchOffsetInFile` 字段与对应构造参数，简化 `PrimitiveOrcColumnVector`。
  - `CreateChangelogViewProcedure` 把 `var delimited` 改为 `boolean delimited`。
- **`LambdaParentheses`**：`SparkShufflingFileRewriteRunner` 把 `(df) -> ...` 简化为 `df -> ...`。
- **日志参数**：`DeleteOrphanFilesSparkAction` 的 `LOG.warn` 把异常 `e` 作为额外参数传入（之前未传，导致异常栈丢失）。
- 其他若干小修复。

## 修改详情

以下仅列举典型文件，完整 31 个文件的修改均为上述分类的等价重构。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/SparkCatalog.java` (+2/-1)

**修改目的**：修复 `StringConcat`。

**工作逻辑**：`ValidationException.check` 的消息从 `"Cannot find matching snapshot ID or reference name for version " + version` 改为 `"... for version %s", version`。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/SparkTableUtil.java` (+4/-1)

**修改目的**：修复 `DefaultLocale`。

**工作逻辑**：`String.format` 增加 `Locale.ROOT` 并格式化多行参数。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/SupportsFunctions.java` (+2)

**修改目的**：修复 `MissingOverride`。

**工作逻辑**：为 `listFunctions` 与 `loadFunction` 添加 `@Override`。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/actions/DeleteOrphanFilesSparkAction.java` (+2/-6)

**修改目的**：移除未使用方法、补全日志异常参数。

**工作逻辑**：删除 `uriComponentMatch` 私有方法与 `Strings` import；`LOG.warn` 调用追加异常 `e` 作为参数。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/data/vectorized/VectorizedSparkOrcReaders.java` (+3/-8)

**修改目的**：移除未使用字段与参数。

**工作逻辑**：`PrimitiveOrcColumnVector` 删除 `batchOffsetInFile` 字段、构造参数及对应传递。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/source/SparkWrite.java` (+34/-15)

**修改目的**：批量修复 `StringConcat`/`MissingOverride` 等。

**工作逻辑**：多处 `String.format` 增加 `Locale.ROOT`，消息拼接改为 `%s` 模板，补 `@Override`。

### 其他 25 个文件

均按上述分类进行等价重构：补 `@Override`、加 `Locale.ROOT`、字符串拼接改模板、删未使用代码、`var` 改显式类型、简化 lambda 括号等。

## 总结

批量修复 Spark v4.0 模块 31 个文件的 ErrorProne 告警，包括补 `@Override`、为 `String.format` 添加 `Locale.ROOT`、字符串拼接改 `%s` 模板、删除未使用方法/字段/参数、`var` 改显式类型、简化 lambda、补全日志异常参数等。所有修改均为等价重构，不改变运行时行为，使代码通过更严格的静态检查。
