# 提交 2011：Spark 3.5: Use ProcedureInput for MigrateTableProcedure. (#12782)

## 提交信息

- **序号**：2011 / 4088
- **哈希**：c81dda7325f8787d4ecc22dbe7b916e3681b0561
- **短哈希**：c81dda732
- **日期**：2025-04-17 08:03:23 +0200
- **作者**：slfan1989
- **提交说明**：Spark 3.5: Use ProcedureInput for MigrateTableProcedure. (#12782)
- **PR/Issue**：#12782

## 总体目的

这个提交与提交 2010 属于同一系列重构，将 Spark 3.5 中 `MigrateTableProcedure` 的参数处理方式从直接通过 `InternalRow` 索引读取改为使用 `ProcedureInput` 抽象类。

在原有实现中，`call(InternalRow args)` 方法通过硬编码索引读取参数（如 `args.getString(0)`、`args.getBoolean(2)` 等），参数定义与读取逻辑分散，null 检查和类型转换需要手动处理。特别是 `properties` 参数的读取，需要手动检查 null 后通过 Scala `foreach` 遍历 Map 来构建 Java HashMap，代码冗长且容易出错。

通过引入 `ProcedureInput`，可以按参数名统一读取值，自动处理 null 检查和类型转换，使代码更简洁、更安全。

## 如何达成设计目的

整体设计思路与提交 2010 完全一致：将过程参数提取为命名的静态常量（`ProcedureParameter`），在 `call` 方法中构造 `ProcedureInput` 对象，通过参数常量引用来读取值。关键组件包括 5 个参数常量（`TABLE_PARAM`、`PROPERTIES_PARAM`、`DROP_BACKUP_PARAM`、`BACKUP_TABLE_NAME_PARAM`、`PARALLELISM_PARAM`）和 `ProcedureInput` 提供的 `asString`、`asBoolean`、`asStringMap`、`asInt`、`isProvided` 方法。

## 修改详情

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/procedures/MigrateTableProcedure.java` (修改, +21/-23 lines)

**修改目的**：将参数处理从 `InternalRow` 索引访问重构为使用 `ProcedureInput`。

**工作逻辑**：

1. **参数常量提取**：将原来内联在 `PARAMETERS` 数组中的 5 个参数（`table`、`properties`、`drop_backup`、`backup_table_name`、`parallelism`）提取为独立的 `private static final ProcedureParameter` 常量，`PARAMETERS` 数组改为引用这些常量。

2. **call 方法重构**：
   - 创建 `ProcedureInput input = new ProcedureInput(spark(), tableCatalog(), PARAMETERS, args)`。
   - `tableName` 从 `args.getString(0)` 改为 `input.asString(TABLE_PARAM, null)`。
   - `properties` 从手动 null 检查 + Scala `foreach` 遍历构建 `HashMap` 简化为 `input.asStringMap(PROPERTIES_PARAM, ImmutableMap.of())`。
   - `dropBackup` 从 `args.isNullAt(2) ? false : args.getBoolean(2)` 改为 `input.asBoolean(DROP_BACKUP_PARAM, false)`，默认值处理内置于方法中。
   - `backupTableName` 从 `args.isNullAt(3) ? null : args.getString(3)` 改为 `input.asString(BACKUP_TABLE_NAME_PARAM, null)`。
   - `parallelism` 从 `args.isNullAt(4)` + `args.getInt(4)` 改为 `input.isProvided(PARALLELISM_PARAM)` + `input.asInt(PARALLELISM_PARAM)`。

3. **import 变更**：移除 `Maps` 和 `BoxedUnit` 的 import，新增 `ImmutableMap` 的 import。

## 总结

本提交是提交 2010 的姊妹提交，对 `MigrateTableProcedure` 进行了相同的 `ProcedureInput` 重构，消除了手动 null 检查和 Scala Map 遍历的样板代码，统一了 Spark 3.5 过程的参数处理模式，提升了代码一致性和可维护性。
