# 提交 2684：Spark 3.5: Refactor Spark procedures to consistently use ProcedureInput for parameter handling. (#14179)

## 提交信息

- **序号**：2684 / 4088
- **哈希**：fbb7136fa9c0605ed2553053606276857c306f99
- **短哈希**：fbb7136fa
- **日期**：2025-09-25 10:27:30 -0700
- **作者**：slfan1989
- **提交说明**：Spark 3.5: Refactor Spark procedures to consistently use ProcedureInput for parameter handling. (#14179)
- **PR/Issue**：#14179（backport 自 #13913）

## 总体目的

本提交是提交 2680（PR #13913，Spark 4.0 ProcedureInput 重构）在 Spark 3.5 模块上的 backport。两者目标完全相同：将所有 Spark 存储过程的参数处理统一为通过 `ProcedureInput` 的命名参数方式，消除硬编码序号、重复的类型转换逻辑和不一致的代码风格。

与 Spark 4.0 版本的一个差异是：Spark 3.5 模块的 `BaseProcedure` 类此前缺少 `requiredInParameter()` 和 `optionalInParameter()` 辅助方法（Spark 4.0 已有），因此本提交还需要在 `BaseProcedure` 中新增这两个方法，使各过程能够使用命名常量声明参数。

## 如何达成设计目的

与提交 2680 完全相同的设计：
1. 扩展 `ProcedureInput`，新增 `asTimestampMillis()` 和 `asLongArray()` 方法。
2. 在 `BaseProcedure` 中新增 `requiredInParameter()` 和 `optionalInParameter()` 静态辅助方法（Spark 3.5 特有改动）。
3. 重构 10 个存储过程类，将参数声明提取为命名常量，在 `call()` 中通过 `ProcedureInput` 读取参数。

## 修改详情

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/procedures/BaseProcedure.java` (+9/-0 lines)

**修改目的**：为 Spark 3.5 的 BaseProcedure 新增参数声明辅助方法。

**工作逻辑**：新增 `requiredInParameter(String name, DataType dataType)` 和 `optionalInParameter(String name, DataType dataType)` 两个静态方法，分别委托给 `ProcedureParameter.required()` 和 `ProcedureParameter.optional()`。同时新增 `ProcedureParameter` 的 import。这两个方法使各过程可以将参数声明为命名的 `static final ProcedureParameter` 常量，而非内联在 PARAMETERS 数组中。Spark 4.0 模块已有这两个方法，此处是补齐差异。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/procedures/ProcedureInput.java` (+33/-0 lines)

**修改目的**：为 `ProcedureInput` 新增时间戳和长整型数组读取能力。

**工作逻辑**：与提交 2680 完全相同。新增 `asTimestampMillis(ProcedureParameter)` 和 `asTimestampMillis(ProcedureParameter, Long)` 方法（含微秒到毫秒转换），新增 `asLongArray(ProcedureParameter, Long[])` 方法。

### 各存储过程类（共 10 个）

**修改目的**：统一参数处理方式。

**工作逻辑**：与提交 2680 中 Spark 4.0 的对应文件完全一致。涉及的文件和改动模式相同：`CherrypickSnapshotProcedure`、`ExpireSnapshotsProcedure`、`FastForwardBranchProcedure`、`PublishChangesProcedure`、`RegisterTableProcedure`、`RemoveOrphanFilesProcedure`、`RewriteManifestsProcedure`、`RollbackToSnapshotProcedure`、`RollbackToTimestampProcedure`、`SetCurrentSnapshotProcedure`。每个过程将参数声明改为命名常量，`call()` 方法改为通过 `ProcedureInput` 读取参数。

### 测试文件（8 个，每个 +2/-2 lines）

**修改目的**：适配输出列数变化导致的断言调整。

**工作逻辑**：与提交 2680 相同的 8 个测试文件小幅调整。

## 总结

本提交是提交 2680（Spark 4.0 ProcedureInput 重构）在 Spark 3.5 模块的 backport。相比 Spark 4.0 版本，多了一处 `BaseProcedure` 的辅助方法补齐（因 Spark 3.5 此前缺少这些方法）。重构不改变功能行为，统一了参数处理风格。与提交 2685（Spark 3.4）构成同一重构在三个 Spark 版本的完整覆盖。
