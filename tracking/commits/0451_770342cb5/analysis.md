# 提交 0451：Spark 3.4: Use ProcedureInput for RewriteDataFiles (#8583)

## 提交信息

- **序号**：0451
- **完整哈希**：770342cb5319f177657439075e0f0b4bcba58632
- **短哈希**：770342cb5
- **日期**：2024-02-02 11:01:25 -0800
- **作者**：Hongyue/Steve Zhang <steveiszhy@gmail.com>
- **提交说明**：Spark 3.4: Use ProcedureInput for RewriteDataFiles (#8583)
- **关联 PR**：#8583
- **修改文件**：4 个（spark 3.4 与 3.5 各 2 个），共 42 行新增、64 行删除

## 总体目的

本提交属于 Iceberg Spark 过程（procedure）参数处理方式的统一改造。在此之前，`RewriteDataFilesProcedure` 在 `call(InternalRow args)` 中通过位置索引（`args.getString(0)`、`args.isNullAt(1)` 等）直接读取过程输入参数，这种写法存在多方面问题：参数语义与位置紧耦合、对 map 类型（options）需要手写 Scala `foreach` 遍历、对 null 处理散落各处、错误提示也不够友好。

社区引入了一个统一的 `ProcedureInput` 抽象，用于在 `BaseProcedure` 与具体过程实现之间充当参数访问门面。`ProcedureInput` 封装了 `SparkSession`、`TableCatalog`、`ProcedureParameter[]` 与 `InternalRow`，提供 `ident(...)`、`asString(...)`、`asStringMap(...)` 等类型安全的访问方法。本提交将 `RewriteDataFilesProcedure`（Spark 3.4 与 3.5 两个版本同时）迁移到这一新抽象，作为系列改造中的一个具体落地。

改造带来的直接收益包括：参数读取逻辑集中化、对 `options` 这个 map 参数的处理从手写 Scala `foreach` + `BoxedUnit` 简化为单次 `input.asStringMap(OPTIONS_PARAM, ImmutableMap.of())` 调用、空标识符错误信息由 "Cannot handle an empty identifier for argument table" 调整为更规范的 "Cannot handle an empty identifier for parameter 'table'"（说明 `ProcedureInput` 在参数名引用上使用 `parameter` 而非 `argument` 术语）。

由于该过程在 Spark 3.4 与 3.5 模块下的实现完全一致，本提交同时对两份代码做相同修改，保持两个版本同步演进，避免后续维护漂移。

## 如何达成设计目的

实现路径相对直接：在每个版本的 `RewriteDataFilesProcedure.java` 中，首先把原先内联在 `PARAMETERS` 数组里的五个 `ProcedureParameter` 提取为命名常量（`TABLE_PARAM`、`STRATEGY_PARAM` 等），以便后续通过引用传给 `ProcedureInput` 的各类型化方法；然后在 `call()` 中构造 `ProcedureInput input = new ProcedureInput(spark(), tableCatalog(), PARAMETERS, args)`，并用 `input.ident(TABLE_PARAM)`、`input.asString(...)`、`input.asStringMap(...)` 取代原先按位置索引读取；最后删除已不再需要的 `checkAndApplyOptions(InternalRow, RewriteDataFiles)` 私有方法（该方法之前用于把 `args.getMap(3)` 转成 `Map<String,String>`），把 `options` 的应用直接前置到 `actions().rewriteDataFiles(table).options(options)` 调用链上。测试侧仅需调整一条断言文本以匹配新的错误信息。

## 修改详情

### spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestRewriteDataFilesProcedure.java

**修改目的**：适配 `ProcedureInput` 改造后空标识符错误信息文案的变化。

**工作逻辑**：在测试空表标识符场景时，原断言期望 `"Cannot handle an empty identifier for argument table"`，改为期望 `"Cannot handle an empty identifier for parameter 'table'"`。新文案以单引号包裹参数名，并把 `argument` 改为 `parameter`，反映 `ProcedureInput` 在校验时使用的术语统一为 `parameter`。

### spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/procedures/RewriteDataFilesProcedure.java

**修改目的**：把 `RewriteDataFilesProcedure` 迁移到 `ProcedureInput` 抽象，简化参数读取与 options 处理。

**工作逻辑**：

1. 导入调整：去掉 `Maps` 与 `scala.runtime.BoxedUnit`，新增 `ImmutableMap`。`Maps` 原本用于在 `checkAndApplyOptions` 中新建可变 Map；`BoxedUnit` 原本用于 Scala `foreach` lambda 的返回值；两者都因 `checkAndApplyOptions` 被删除而不再需要。`ImmutableMap` 作为 `asStringMap` 的默认值常量。

2. 参数定义重构：将原内联在 `PARAMETERS` 数组中的五个 `ProcedureParameter` 提取为类级命名常量 `TABLE_PARAM`、`STRATEGY_PARAM`、`SORT_ORDER_PARAM`、`OPTIONS_PARAM`、`WHERE_PARAM`。`PARAMETERS` 数组改为引用这些常量。这样既可作为过程签名定义，又可在 `call()` 中作为 `ProcedureInput` 类型化访问方法的入参引用。

3. `call()` 方法重写：原实现按位置索引读取参数（`args.getString(0)`、`args.isNullAt(1) ? null : args.getString(1)` 等），并在 lambda 内才读取 strategy/sortOrderString/options/where。新实现先构造 `ProcedureInput input = new ProcedureInput(spark(), tableCatalog(), PARAMETERS, args)`，然后一次性取出所有参数：`input.ident(TABLE_PARAM)` 得到 `Identifier`，`input.asString(STRATEGY_PARAM, null)` 得到 strategy，`input.asString(SORT_ORDER_PARAM, null)` 得到 sortOrderString，`input.asStringMap(OPTIONS_PARAM, ImmutableMap.of())` 得到 options，`input.asString(WHERE_PARAM, null)` 得到 where。options 直接在 `actions().rewriteDataFiles(table).options(options)` 调用上应用，不再走单独的 `checkAndApplyOptions` 分支。

4. 删除 `checkAndApplyOptions` 方法：该方法原本通过 `args.getMap(3).foreach(...)` 把 Scala Map 转成 Java `HashMap`，再用 `action.options(options)` 应用。新实现由 `ProcedureInput.asStringMap` 直接给出 `Map<String,String>`，无需该辅助方法。

5. `checkAndApplyStrategy`、`checkAndApplyFilter` 等其余方法保持不变。

### spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestRewriteDataFilesProcedure.java

**修改目的**：与 3.4 测试同步，适配新错误信息文案。

**工作逻辑**：与 3.4 版本完全相同的断言文案调整。

### spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/procedures/RewriteDataFilesProcedure.java

**修改目的**：与 3.4 主代码同步迁移到 `ProcedureInput`。

**工作逻辑**：与 3.4 版本完全相同的重构（命名常量提取、`call()` 改写、`checkAndApplyOptions` 删除、导入调整）。Spark 3.4 与 3.5 在该过程的实现保持一致。

## 小结

本提交是 Spark 过程参数处理统一化改造在 `RewriteDataFilesProcedure` 上的落地，覆盖 Spark 3.4 与 3.5 两个模块。通过引入 `ProcedureInput` 门面，把原先按位置索引、手写 Scala Map 遍历、散落 null 检查的参数读取方式，替换为类型安全的 `ident/asString/asStringMap` 调用，同时删除冗余的 `checkAndApplyOptions` 方法与 `BoxedUnit`/`Maps` 导入。附带收益是错误信息文案规范化（`argument` → `parameter`，参数名加单引号）。修改属于重构性质，不改变过程对外行为，仅调整测试断言文案以匹配新的错误信息。
