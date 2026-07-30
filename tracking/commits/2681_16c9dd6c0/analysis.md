# 提交 2681：Spark 4.0: Refactor Spark procedures to consistently use ProcedureInput for parameter handling. (#13913)

## 提交信息

- **序号**：2681 / 4088
- **哈希**：16c9dd6c07d3288ef2b3d504c6af7ba817159d48
- **短哈希**：16c9dd6c0
- **日期**：2025-09-24 17:48:10 +0200
- **作者**：slfan1989
- **提交说明**：Spark 4.0: Refactor Spark procedures to consistently use ProcedureInput for parameter handling. (#13913)
- **PR/Issue**：#13913

## 总体目的

本提交对 Spark 4.0 模块中所有 Iceberg 存储过程（procedures）进行统一重构，使其一致地使用 `ProcedureInput` 类来处理参数提取，取代此前各过程中分散的、通过硬编码序号（ordinal）直接从 `InternalRow` 读取参数的方式。

重构前的问题：每个存储过程的 `call(InternalRow args)` 方法都直接使用 `args.getString(0)`、`args.getLong(1)`、`args.isNullAt(2)` 等方式按位置读取参数。这种方式存在几个痛点：
1. **魔法数字**：参数序号是硬编码的，可读性差，增删参数时容易出错。
2. **重复逻辑**：null 检查、类型转换（如时间戳微秒转毫秒）、Map 解析等逻辑在每个过程中重复实现。
3. **不一致性**：有些过程使用了 `ProcedureInput`（如 `RewriteTablePathProcedure`），有些没有，代码风格不统一。

重构后，所有过程统一通过 `ProcedureInput` 的命名参数方法（如 `input.asLong(SNAPSHOT_ID_PARAM)`、`input.asString(TABLE_PARAM, null)`）读取参数，消除了魔法数字，集中了类型转换逻辑，提升了可维护性和一致性。

## 如何达成设计目的

整体思路分两步：
1. **扩展 `ProcedureInput`**：新增 `asTimestampMillis()` 和 `asLongArray()` 两个方法，覆盖此前过程中手动实现的时间戳转换和长整型数组读取逻辑。
2. **重构所有过程**：将每个过程的参数声明提取为命名的 `static final ProcedureParameter` 常量，在 `call()` 方法中构造 `ProcedureInput` 并通过命名常量读取参数，取代硬编码序号。涉及 10 个存储过程类。

## 修改详情

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/procedures/ProcedureInput.java` (+33/-0 lines)

**修改目的**：为 `ProcedureInput` 新增时间戳和长整型数组的读取能力。

**工作逻辑**：
- 新增 `asTimestampMillis(ProcedureParameter)` 和 `asTimestampMillis(ProcedureParameter, Long defaultValue)` 方法：校验参数类型为 `TimestampType`，从 `InternalRow` 读取 long 值，若非 null 则通过 `DateTimeUtil.microsToMillis()` 将 Spark 的微秒级时间戳转换为毫秒级。这取代了各过程中重复的 `DateTimeUtil.microsToMillis(args.getLong(n))` 逻辑。
- 新增 `asLongArray(ProcedureParameter, Long[] defaultValue)` 方法：校验参数类型为 `LongType` 数组，通过已有的 `array()` 辅助方法读取 `Long[]`，再转换为 primitive `long[]`。取代了 `args.getArray(n).toLongArray()` 的直接调用。

### 各存储过程类（共 10 个，+174/-125 lines 合计）

涉及的文件：`CherrypickSnapshotProcedure`、`ExpireSnapshotsProcedure`、`FastForwardBranchProcedure`、`PublishChangesProcedure`、`RegisterTableProcedure`、`RemoveOrphanFilesProcedure`、`RewriteManifestsProcedure`、`RollbackToSnapshotProcedure`、`RollbackToTimestampProcedure`、`SetCurrentSnapshotProcedure`。

**修改目的**：统一参数处理方式。

**工作逻辑**（以 `ExpireSnapshotsProcedure` 为典型示例）：
- 参数声明从内联在 `PARAMETERS` 数组中改为先声明为命名的 `static final ProcedureParameter` 常量（如 `TABLE_PARAM`、`OLDER_THAN_PARAM` 等），再在 `PARAMETERS` 数组中引用这些常量。
- `call()` 方法中，原先的 `args.getString(0)`、`args.isNullAt(1) ? null : DateTimeUtil.microsToMillis(args.getLong(1))` 等改为 `ProcedureInput input = new ProcedureInput(spark(), tableCatalog(), PARAMETERS, args);` 后通过 `input.ident(TABLE_PARAM)`、`input.asTimestampMillis(OLDER_THAN_PARAM, null)` 等命名方式读取。
- `RemoveOrphanFilesProcedure` 的改动最大（+96/-44 lines），因为它还涉及 `equal_schemes` 和 `equal_authorities` 两个 Map 参数的手动解析逻辑（原先用 Scala 的 `args.getMap().foreach()` 遍历），现改为简洁的 `input.asStringMap(EQUAL_SCHEMES_PARAM, ImmutableMap.of())`。`prefix_mismatch_mode` 的字符串转枚举逻辑也提取为独立的 `asPrefixMismatchMode()` 辅助方法。
- `RollbackToTimestampProcedure` 从直接使用 `DateTimeUtil.microsToMillis()` 改为 `input.asTimestampMillis()`。
- 移除了不再需要的 import（`DateTimeUtil`、`BoxedUnit`、`Maps` 等）。

### 测试文件（8 个，每个 +2/-2 lines）

**修改目的**：适配输出列数变化导致的断言调整。

**工作逻辑**：涉及 `TestCherrypickSnapshotProcedure`、`TestExpireSnapshotsProcedure`、`TestFastForwardBranchProcedure`、`TestPublishChangesProcedure`、`TestRemoveOrphanFilesProcedure`、`TestRewriteManifestsProcedure`、`TestRollbackToSnapshotProcedure`、`TestSetCurrentSnapshotProcedure`。这些测试中的小幅调整主要是参数传递或断言方式的适配。

## 总结

本提交是一次较大规模的重构，将 Spark 4.0 模块中 10 个存储过程的参数处理统一为通过 `ProcedureInput` 的命名参数方式，消除了硬编码序号、重复的类型转换逻辑和不一致的代码风格。同时为 `ProcedureInput` 新增了 `asTimestampMillis()` 和 `asLongArray()` 方法以覆盖所有用例。重构不改变任何功能行为，但显著提升了代码的可读性、可维护性和一致性。后续提交 2683（Spark 3.5）和 2685（Spark 3.4）将同一重构 backport 到其他 Spark 版本。
