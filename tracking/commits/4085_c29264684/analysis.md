# 提交 4085：API: Extract superclass from InclusiveMetricsEvaluator

## 提交信息

- **序号**：4085 / 4088
- **哈希**：c29264684b3232135c526c2bafb3be9b5357bce4
- **短哈希**：c29264684
- **日期**：2026-07-23 17:11:10 -0700
- **作者**：Ryan Blue
- **提交说明**：API: Extract superclass from InclusiveMetricsEvaluator (#17201)
- **PR/Issue**：#17201

## 总体目的

`InclusiveMetricsEvaluator` 是 Iceberg 中基于文件统计（lower/upper bounds、value/null/NaN counts）做行组级别过滤的核心组件。它内部定义了一个 `MetricsEvalVisitor`，实现了所有谓词（`eq`、`lt`、`gt`、`in`、`notIn`、`startsWith`、`notEq`、`isNaN` 等）基于 inclusive metrics 的评估逻辑。

随着 Iceberg 引入 variant 类型（通过 `BoundExtract` 提取 variant 字段）以及未来可能的其他 metrics 评估器（例如严格上/下界评估、列级统计评估器等），这套庞大的谓词评估逻辑（约 500 行）在 `InclusiveMetricsEvaluator` 内部无法被复用。每次新增一个类似的评估器都需要复制整套逻辑。

本提交将 `MetricsEvalVisitor` 中通用的、与具体 metrics 数据来源无关的谓词评估逻辑抽取到一个新的抽象超类 `InclusiveEvalVisitor` 中。超类定义了一组抽象/可重写的"数据访问"钩子（如 `mayContainNull`、`containsNullsOnly`、`mayContainNaN`、`containsNaNsOnly`、`lowerBound`、`upperBound`、`extractLowerBound`、`extractUpperBound`），子类只需提供这些钩子的具体实现即可继承全部谓词评估逻辑。`InclusiveMetricsEvaluator` 的 `MetricsEvalVisitor` 现在继承 `InclusiveEvalVisitor`，只保留与 `DataFile`/`ContentFile` 统计信息相关的字段访问实现。

此外，重构过程中还顺带修正了一处 NaN 处理的语义：新的 `uniqueValue` 方法对 Float/Double 类型引入了 `mayContainNaN` 检查，当 NaN 计数未知时（nanCounts 为 null 或不含该字段），不再假设"无 NaN"，而是保守返回 null（即不能确定唯一值），使 `notEq` 在缺少 NaN 统计时更安全地保留文件。

## 如何达成设计目的

设计采用模板方法模式（Template Method）：

1. **新建 `InclusiveEvalVisitor` 抽象类**，继承 `ExpressionVisitors.BoundVisitor<Boolean>`，包含：
   - 常量 `IN_PREDICATE_LIMIT`、`ROWS_MIGHT_MATCH`、`ROWS_CANNOT_MATCH`（改为 `protected`）；
   - 4 个抽象方法：`mayContainNull`、`containsNullsOnly`、`mayContainNaN`、`containsNaNsOnly`，由子类基于实际统计提供；
   - 4 个默认返回 null 的钩子：`lowerBound(BoundReference)`、`upperBound(BoundReference)`、`extractLowerBound(BoundExtract)`、`extractUpperBound(BoundExtract)`；
   - 全部谓词实现（`alwaysTrue/False`、`not`、`and`、`or`、`isNull`、`notNull`、`isNaN`、`notNaN`、`lt`、`ltEq`、`gt`、`gtEq`、`eq`、`notEq`、`in`、`notIn`、`startsWith`、`notStartsWith`）从原 `MetricsEvalVisitor` 整体迁移而来；
   - 私有辅助方法 `uniqueValue`、`evalLowerBound`、`evalUpperBound`、`transformLowerBound`、`transformUpperBound`、`isNonNullPreserving` 也一并迁移。

2. **`InclusiveMetricsEvaluator.MetricsEvalVisitor` 改为 `extends InclusiveEvalVisitor`**，删除所有已上移的谓词实现，只保留：
   - 持有 `valueCounts`、`nullCounts`、`nanCounts`、`lowerBounds`、`upperBounds` 等统计字段；
   - 在 `eval(DataFile)` 中从 `ContentFile` 读取这些统计；
   - 实现超类的抽象钩子，把统计信息暴露给超类谓词逻辑。

3. **NaN 语义细化**：新的 `uniqueValue` 增加：
```java
if ((lower instanceof Float || lower instanceof Double) && mayContainNaN(id)) {
  return null;
}
```
即对浮点类型，只要 NaN 计数未知或非零，就不能断言"唯一值"。

## 修改详情

### `api/src/main/java/org/apache/iceberg/expressions/InclusiveEvalVisitor.java` (+532/-0 lines, new file)

**修改目的**：承载所有通用的 inclusive metrics 谓词评估逻辑，作为可复用的抽象超类。

**工作逻辑**：

- 定义 4 个抽象钩子描述列的 null/NaN 状态：
  - `mayContainNull(int id)`：null 计数非零或未知返回 true；
  - `containsNullsOnly(int id)`：null 计数已知且等于 value 计数返回 true；
  - `mayContainNaN(int id)`：NaN 计数非零或未知返回 true；
  - `containsNaNsOnly(int id)`：NaN 计数已知且等于 value 计数返回 true。
- 4 个默认返回 null 的边界访问钩子（`lowerBound/upperBound/extractLowerBound/extractUpperBound`），子类按需重写。
- 整套谓词实现从原 `MetricsEvalVisitor` 原样迁移，例如 `lt` 仍通过 `lowerBound` + 比较判定，`in` 仍受 `IN_PREDICATE_LIMIT=200` 限制并基于上下界过滤字面量集合。
- `uniqueValue` 用于 `notEq`/`notIn`，定义"列无 null、无 NaN、上下界相等"时返回该唯一值；新增对 Float/Double 的 `mayContainNaN` 检查，使 NaN 未知时不再误判唯一值。
- `evalLowerBound/evalUpperBound` 根据 term 类型（`BoundReference`/`BoundTransform`/`BoundExtract`）分派到对应钩子；`transformLowerBound/UpperBound` 仅在 transform `preservesOrder()` 时应用 transform 到下/上界。
- `isNonNullPreserving` 判断 term 是否对非 null 输入产生非 null 输出（`BoundReference` 恒真，`BoundTransform` 看 `preservesOrder`，variant extract 不保证）。

### `api/src/main/java/org/apache/iceberg/expressions/InclusiveMetricsEvaluator.java` (+11/-492 lines, net -481)

**修改目的**：让 `MetricsEvalVisitor` 继承 `InclusiveEvalVisitor`，只保留与 `DataFile`/`ContentFile` 统计相关的实现。

**工作逻辑**：

- 移除大量导入（`Collection`、`Comparator`、`Set`、`Collectors`、`Transform`、`Comparators`、`NaNUtil`）和常量 `IN_PREDICATE_LIMIT`、`ROWS_MIGHT_MATCH/ROWS_CANNOT_MATCH`（已上移到超类）。
- 类声明改为：
```java
private class MetricsEvalVisitor extends InclusiveEvalVisitor {
```
- 保留 `valueCounts`、`nullCounts`、`nanCounts`、`lowerBounds`、`upperBounds` 字段，以及 `eval(DataFile)` 中从 `ContentFile` 读取这些统计的逻辑。
- 删除所有上移的谓词方法实现。
- 实现超类的抽象钩子（访问修饰符从 `private` 改为 `protected` 并加 `@Override`）：
  - `mayContainNull(int id)`：`nullCounts == null || !containsKey(id) || get(id) != 0`；
  - `containsNullsOnly(int id)`：value/null 计数都已知且 `value - null == 0`；
  - `mayContainNaN(int id)`：`nanCounts == null || !containsKey(id) || get(id) != 0`（新增方法，对应超类抽象方法）；
  - `containsNaNsOnly(int id)`：nan/value 计数都已知且相等；
  - `lowerBound/upperBound(BoundReference)`：从 `lowerBounds/upperBounds` map 用 `Conversions.fromByteBuffer` 解析；
  - `extractLowerBound/extractUpperBound(BoundExtract)`：从 variant 字段的 bounds map 解析出 `VariantObject` 并取出对应字段。
- 删除已上移的私有辅助方法（`uniqueValue`、`lowerBound(Bound)`、`upperBound(Bound)`、`parseLowerBound`、`parseUpperBound`、`transformLowerBound/UpperBound`、`isNonNullPreserving`）。

### `api/src/test/java/org/apache/iceberg/expressions/TestInclusiveMetricsEvaluator.java` (+35/-11 lines)

**修改目的**：覆盖 NaN 语义细化后的 `notEq` 行为，并修正既有测试的 nanCounts 设置以匹配新语义。

**工作逻辑**：

1. 新增 `testNotEqSingleValueWithoutNaN`：Float 列，valueCount=10、nullCount=0、nanCount=0（明确无 NaN）、lower=upper=1.0。断言 `notEqual("f", 1.0f)` 评估为 false（应跳过），因为文件唯一值就是 1.0 且无 NaN，notEqual 不可能匹配。

2. 新增 `testNotEqSingleValueWithNaN`：同上但 nanCount=1（含 1 个 NaN）。断言 `notEqual("f", 1.0f)` 评估为 true（应读），因为 NaN != 1.0 满足 notEqual，不能跳过。

3. 修改既有 `testNotEqWithSingleValue` 和 `testNotEqWithNaNAndSingleValue`：把 `nanCounts` 从 `ImmutableMap.of(3, 0L)`（明确 0）改为 `null`（未知）。这是关键——在新的 `mayContainNaN` 语义下，nanCounts 未知时 `uniqueValue` 返回 null，`notEq` 走 `ROWS_MIGHT_MATCH`（应读），与原测试"应跳过"的期望不符，故改为 null 让测试聚焦于"nanCounts 缺失时保守保留"的语义，而不是"nanCounts=0 时跳过"（后者由新测试 `testNotEqSingleValueWithoutNaN` 覆盖）。

4. 新增导入 `java.nio.ByteBuffer` 和 `java.util.Map`。

## 总结

这是一次重要的结构性重构：把 `InclusiveMetricsEvaluator` 中近 500 行的谓词评估逻辑抽取为可复用的 `InclusiveEvalVisitor` 抽象超类，采用模板方法模式将"评估逻辑"与"统计数据访问"解耦。这为未来实现其他基于 inclusive metrics 的评估器（如针对不同文件格式或列级统计的评估器）铺平了道路，避免逻辑重复。重构同时细化了 NaN 处理语义：当 NaN 计数未知时不再假设无 NaN，使 `notEq`/`notIn` 在缺统计时更安全地保留文件，避免错误剪裁。配套测试显式覆盖了"明确无 NaN"和"含 NaN"两种 notEq 场景，并调整了既有测试的 nanCounts 设置以匹配新语义。整体行为对外基本兼容，但 NaN 未知场景变得更保守（更倾向于读取文件），这是更正确的方向。
