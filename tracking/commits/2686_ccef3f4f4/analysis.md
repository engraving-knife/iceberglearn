# 提交 2686：Spark 3.4: Refactor Spark procedures to consistently use ProcedureInput for parameter handling. (#14185)

## 提交信息

- **序号**：2686 / 4088
- **哈希**：ccef3f4f46726188ecbb8849698c047f4fca94ac
- **短哈希**：ccef3f4f4
- **日期**：2025-09-25 17:12:18 -0700
- **作者**：slfan1989
- **提交说明**：Spark 3.4: Refactor Spark procedures to consistently use ProcedureInput for parameter handling. (#14185)
- **PR/Issue**：#14185（backport 自 #13913）

## 总体目的

本提交是提交 2680（PR #13913，Spark 4.0 ProcedureInput 重构）在 Spark 3.4 模块上的 backport，与提交 2683（Spark 3.5）是同一重构的姊妹提交。目标完全相同：将所有 Spark 3.4 存储过程的参数处理统一为通过 `ProcedureInput` 的命名参数方式，消除硬编码序号和重复逻辑。

与 Spark 3.5 版本（2683）一样，Spark 3.4 模块的 `BaseProcedure` 此前也缺少 `requiredInParameter()` 和 `optionalInParameter()` 辅助方法，需要一并补齐。

## 如何达成设计目的

与提交 2683（Spark 3.5）完全相同的设计：
1. 扩展 `ProcedureInput`，新增 `asTimestampMillis()` 和 `asLongArray()` 方法。
2. 在 `BaseProcedure` 中新增 `requiredInParameter()` 和 `optionalInParameter()` 静态辅助方法。
3. 重构 10 个存储过程类，统一参数处理方式。

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/procedures/BaseProcedure.java` (+9/-0 lines)

**修改目的**：为 Spark 3.4 的 BaseProcedure 新增参数声明辅助方法。

**工作逻辑**：与提交 2683 完全相同。新增 `requiredInParameter(String, DataType)` 和 `optionalInParameter(String, DataType)` 两个静态方法，委托给 `ProcedureParameter.required()` / `ProcedureParameter.optional()`，新增 `ProcedureParameter` import。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/procedures/ProcedureInput.java` (+33/-0 lines)

**修改目的**：为 `ProcedureInput` 新增时间戳和长整型数组读取能力。

**工作逻辑**：与提交 2680/2683 完全相同。新增 `asTimestampMillis()` 两个重载和 `asLongArray()` 方法。

### 各存储过程类（共 10 个）

**修改目的**：统一参数处理方式。

**工作逻辑**：与提交 2680/2683 中对应文件完全一致。涉及 `CherrypickSnapshotProcedure`、`ExpireSnapshotsProcedure`、`FastForwardBranchProcedure`、`PublishChangesProcedure`、`RegisterTableProcedure`、`RemoveOrphanFilesProcedure`、`RewriteManifestsProcedure`、`RollbackToSnapshotProcedure`、`RollbackToTimestampProcedure`、`SetCurrentSnapshotProcedure`。

### 测试文件（8 个，每个 +2/-2 lines）

**修改目的**：适配输出列数变化导致的断言调整。与提交 2680/2683 相同的 8 个测试文件。

## 总结

本提交是提交 2680（Spark 4.0 ProcedureInput 重构）在 Spark 3.4 模块的 backport，与提交 2683（Spark 3.5）构成同一重构在三个 Spark 版本的完整覆盖。三个版本的改动完全对应，Spark 3.4 和 3.5 比 4.0 多了一处 `BaseProcedure` 辅助方法的补齐。重构不改变功能行为，统一了代码风格，提升了可维护性。
