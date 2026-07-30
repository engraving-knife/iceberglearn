# 提交 2010：Spark 3.5: Use ProcedureInput for SnapshotTableProcedure. (#12783)

## 提交信息

- **序号**：2010 / 4088
- **哈希**：5d97ad645c74a1cbcc003e4f33d43c9032588f09
- **短哈希**：5d97ad645
- **日期**：2025-04-17 08:00:39 +0200
- **作者**：slfan1989
- **提交说明**：Spark 3.5: Use ProcedureInput for SnapshotTableProcedure. (#12783)
- **PR/Issue**：#12783

## 总体目的

这个提交将 Spark 3.5 中 `SnapshotTableProcedure` 的参数处理方式从直接通过 `InternalRow` 索引读取改为使用 `ProcedureInput` 抽象类。这是 Iceberg Spark 过程（Procedure）参数处理统一化重构的一部分。

在原有实现中，`call(InternalRow args)` 方法通过硬编码的索引（如 `args.getString(0)`、`args.getString(1)` 等）来读取过程参数，这种方式存在几个问题：一是参数顺序与索引强耦合，参数定义与读取逻辑分散，难以维护；二是处理 null 值和类型转换需要手动编写大量样板代码（如通过 `args.getMap(3).foreach(...)` 遍历 Scala Map 来构建 Java Map）；三是不同过程之间的参数读取方式不一致，容易出错。

`ProcedureInput` 提供了一个统一的参数访问层，通过 `ProcedureParameter` 引用来按名称读取参数值，自动处理 null 检查和类型转换，使过程代码更简洁、更安全、更易维护。

## 如何达成设计目的

整体设计思路是将过程参数定义为命名的静态常量（`ProcedureParameter`），然后在 `call` 方法中构造 `ProcedureInput` 对象，通过参数常量引用来读取值。关键组件包括：
- 将每个 `ProcedureParameter` 提取为 `private static final` 常量，便于在 `PARAMETERS` 数组和 `call` 方法中统一引用。
- 在 `call` 方法中创建 `ProcedureInput` 实例，使用 `asString`、`asInt`、`asStringMap`、`isProvided` 等方法按参数名读取值，替代原来的索引访问。

## 修改详情

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/procedures/SnapshotTableProcedure.java` (修改, +25/-24 lines)

**修改目的**：将参数处理从 `InternalRow` 索引访问重构为使用 `ProcedureInput`。

**工作逻辑**：

1. **参数常量提取**：将原来内联在 `PARAMETERS` 数组中的 5 个参数（`source_table`、`table`、`location`、`properties`、`parallelism`）提取为独立的 `private static final ProcedureParameter` 常量（`SOURCE_TABLE_PARAM`、`TABLE_PARAM`、`LOCATION_PARAM`、`PROPERTIES_PARAM`、`PARALLELISM_PARAM`），`PARAMETERS` 数组改为引用这些常量。

2. **call 方法重构**：
   - 创建 `ProcedureInput input = new ProcedureInput(spark(), tableCatalog(), PARAMETERS, args)`，作为统一的参数访问入口。
   - `source` 从 `args.getString(0)` 改为 `input.asString(SOURCE_TABLE_PARAM, null)`。
   - `dest` 从 `args.getString(1)` 改为 `input.asString(TABLE_PARAM, null)`。
   - `snapshotLocation` 从 `args.isNullAt(2) ? null : args.getString(2)` 改为 `input.asString(LOCATION_PARAM, null)`，null 处理由 `ProcedureInput` 内部完成。
   - `properties` 的读取变化最大：原来需要手动检查 `args.isNullAt(3)` 并通过 Scala `foreach` 遍历 Map 构建可变 `HashMap`，现在简化为 `input.asStringMap(PROPERTIES_PARAM, ImmutableMap.of())`，一行代码完成，且使用不可变 Map。
   - `parallelism` 的处理从 `args.isNullAt(4)` + `args.getInt(4)` 改为 `input.isProvided(PARALLELISM_PARAM)` + `input.asInt(PARALLELISM_PARAM)`，语义更清晰。

3. **import 变更**：移除了 `Maps` 和 `BoxedUnit`（Scala 相关）的 import，新增 `ImmutableMap` 的 import，减少了与 Scala 类型的直接耦合。

## 总结

本提交通过引入 `ProcedureInput` 抽象层，将 `SnapshotTableProcedure` 的参数处理从脆弱的索引访问重构为按名称的类型安全访问，消除了手动 null 检查和 Scala Map 遍历的样板代码，提升了代码的可读性和可维护性。这是 Spark 过程参数处理统一化重构的组成部分。
