# 提交 2013：Spark3.4: Backport ProcedureInput for MigrateTableProcedure And SnapshotTableProcedure. (#12837)

## 提交信息

- **序号**：2013 / 4088
- **哈希**：8348decfd5b2ffeb907f5a0f9a4c36e2404f0c65
- **短哈希**：8348decfd
- **日期**：2025-04-18 10:06:58 -0700
- **作者**：slfan1989
- **提交说明**：Spark3.4: Backport ProcedureInput for MigrateTableProcedure And SnapshotTableProcedure. (#12837)
- **PR/Issue**：#12837

## 总体目的

这个提交是将提交 2010（#12783）和提交 2011（#12782）中在 Spark 3.5 完成的 `ProcedureInput` 重构回移植（backport）到 Spark 3.4 版本。Spark 3.4 中的 `MigrateTableProcedure` 和 `SnapshotTableProcedure` 存在与 Spark 3.5 相同的问题：通过硬编码的 `InternalRow` 索引读取参数，需要手动处理 null 检查和 Scala Map 遍历。

为了保持不同 Spark 版本之间的代码一致性，本提交对 Spark 3.4 的这两个过程进行了完全相同的重构，使用 `ProcedureInput` 抽象层替代直接的索引访问。

## 如何达成设计目的

与提交 2010/2011 完全相同的设计思路：将过程参数提取为命名的静态常量（`ProcedureParameter`），在 `call` 方法中构造 `ProcedureInput` 对象，通过参数常量引用来读取值。改动涉及 `MigrateTableProcedure` 和 `SnapshotTableProcedure` 两个文件，每个文件的重构方式与对应的 Spark 3.5 版本完全一致。

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/procedures/MigrateTableProcedure.java` (修改, +21/-23 lines)

**修改目的**：将 `MigrateTableProcedure` 的参数处理从 `InternalRow` 索引访问重构为使用 `ProcedureInput`。

**工作逻辑**：
与提交 2011 中的 Spark 3.5 版本完全一致。提取 5 个参数常量（`TABLE_PARAM`、`PROPERTIES_PARAM`、`DROP_BACKUP_PARAM`、`BACKUP_TABLE_NAME_PARAM`、`PARALLELISM_PARAM`），在 `call` 方法中使用 `ProcedureInput` 的 `asString`、`asBoolean`、`asStringMap`、`asInt`、`isProvided` 方法读取参数值，移除了 `Maps` 和 `BoxedUnit` 的 import。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/procedures/SnapshotTableProcedure.java` (修改, +25/-24 lines)

**修改目的**：将 `SnapshotTableProcedure` 的参数处理从 `InternalRow` 索引访问重构为使用 `ProcedureInput`。

**工作逻辑**：
与提交 2010 中的 Spark 3.5 版本完全一致。提取 5 个参数常量（`SOURCE_TABLE_PARAM`、`TABLE_PARAM`、`LOCATION_PARAM`、`PROPERTIES_PARAM`、`PARALLELISM_PARAM`），在 `call` 方法中使用 `ProcedureInput` 读取参数值，移除了 `Maps` 和 `BoxedUnit` 的 import。

## 总结

本提交是提交 2010 和 2011 的 Spark 3.4 回移植，确保两个 Spark 版本的 `MigrateTableProcedure` 和 `SnapshotTableProcedure` 保持一致的参数处理方式。代码变更与 Spark 3.5 版本完全相同，统一使用 `ProcedureInput` 抽象层。
