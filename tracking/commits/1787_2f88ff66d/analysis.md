# 提交 1787：API, Core: Update inclusive metrics evaluator for extract and transforms (#12311)

## 提交信息

- **序号**：1787 / 4088
- **哈希**：2f88ff66d05269b04e3621fe067ccdab668f3191
- **短哈希**：2f88ff66d
- **日期**：2025-02-25 16:48:12 -0800
- **作者**：Ryan Blue
- **提交说明**：API, Core: Update inclusive metrics evaluator for extract and transforms (#12311)
- **PR/Issue**：#12311

## 总体目的

这个提交大幅重构了 `InclusiveMetricsEvaluator`（包容性指标评估器），使其能够处理包含转换（transforms）和提取（extract）操作的边界表达式。`InclusiveMetricsEvaluator` 是 Iceberg 中一个关键的查询优化组件，它通过评估数据文件的统计指标（如上下界、null 计数、NaN 计数等）来判断某个数据文件是否可能匹配给定的查询表达式，从而实现文件级的数据跳过（data skipping），减少不必要的数据扫描。

此前，`InclusiveMetricsEvaluator` 的访问器方法（如 `lt`、`gt`、`eq` 等）都接受 `BoundReference<T>` 参数，即只能处理直接字段引用。当表达式包含转换操作（如 `bucket16(x) = 0`）或提取操作（如从 Variant 类型中提取字段）时，这些表达式会被 `handleNonReference()` 方法捕获并直接返回 `ROWS_MIGHT_MATCH`，即不做任何文件过滤。

本次修改将访问器方法参数从 `BoundReference<T>` 改为 `Bound<T>`，使其能处理所有类型的边界项，包括：
- `BoundReference`：直接字段引用（原有功能）
- `BoundTransform`：带转换的引用（如 truncate、bucket 等保序转换）
- `BoundExtract`：从 Variant 类型中提取字段

对于保序转换（order-preserving transforms），评估器现在可以对转换后的边界值应用相同的比较逻辑来实现文件过滤。对于 Variant 字段提取，评估器可以从 Variant 类型的边界统计中提取特定字段的边界值。

此外，本提交还新增了 `VariantExpressionUtil` 工具类，用于将 Variant 值转换为 Iceberg 类型系统的值，以及修复了 `mayContainNull` 方法的逻辑。

## 如何达成设计目的

提交通过以下策略实现：

1. **访问器方法泛化**：将所有比较方法（`lt`、`ltEq`、`gt`、`gtEq`、`eq`、`in`、`isNull`、`notNull`、`isNaN`、`notNaN`、`startsWith`、`notStartsWith`）的参数从 `BoundReference<T>` 改为 `Bound<T>`，使其能接受任何 `Bound` 子类。

2. **边界值解析分发**：新增 `lowerBound(Bound<T>)` 和 `upperBound(Bound<T>)` 方法，根据 term 类型分发到不同的解析方法：`parseLowerBound/parseUpperBound`（直接引用）、`transformLowerBound/transformUpperBound`（转换）、`extractLowerBound/extractUpperBound`（提取）。

3. **保序转换支持**：对 `BoundTransform`，检查 `transform.preservesOrder()`，若保序则对原始边界值应用转换后比较。

4. **Variant 提取支持**：对 `BoundExtract`，从 Variant 类型的边界统计中解析出 VariantObject，再通过 `VariantExpressionUtil.castTo()` 转换为目标类型。

5. **新工具类**：创建 `VariantExpressionUtil`，提供 `castTo()` 方法将 Variant 值按目标 Type 转换。

6. **null 保持性分析**：新增 `isNonNullPreserving(Bound<?>)` 方法判断 term 是否保持非 null 性，用于 `isNull` 方法的逻辑。

7. **测试覆盖**：新增 `TestInclusiveMetricsEvaluatorWithExtract` 和 `TestInclusiveMetricsEvaluatorWithTransforms` 两个大型测试类。

## 修改详情

### `api/src/main/java/org/apache/iceberg/expressions/InclusiveMetricsEvaluator.java`（修改, +274/-199 lines）

**修改目的**：重构评估器以支持 transforms 和 extract 操作。

**工作逻辑**：
- **访问器基类变更**：`MetricsEvalVisitor` 从继承 `BoundExpressionVisitor<Boolean>` 改为继承 `ExpressionVisitors.BoundVisitor<Boolean>`，后者接受 `Bound<T>` 而非 `BoundReference<T>`。
- **移除 handleNonReference**：原先对非引用项直接返回 `ROWS_MIGHT_MATCH` 的逻辑被移除，改为在各访问器方法中处理。
- **方法签名变更**：所有比较方法参数从 `BoundReference<T> ref` 改为 `Bound<T> term`，使用 `term.ref().fieldId()` 获取字段 ID。
- **边界值解析**：新增 `lowerBound()`/`upperBound()` 分发方法，根据 term 类型（BoundReference/BoundTransform/BoundExtract）选择不同的解析策略。
- **保序转换**：`transformLowerBound/transformUpperBound` 检查 `transform.preservesOrder()`，对保序转换应用转换到原始边界值。
- **Variant 提取**：`extractLowerBound/extractUpperBound` 从 Variant 边界统计中解析 VariantObject，提取指定字段的值。
- **null 处理**：`mayContainNull()` 逻辑修正为 `nullCounts == null || !nullCounts.containsKey(id) || nullCounts.get(id) != 0`，正确处理字段不在 nullCounts 中的情况。
- **startsWith/notStartsWith**：从使用 `ByteBuffer` 比较改为使用 `CharSequence` 比较，更适用于字符串类型。
- **新增辅助方法**：`isNonNullPreserving()` 判断 term 是否将非 null 输入映射为非 null 输出。

### `api/src/main/java/org/apache/iceberg/expressions/VariantExpressionUtil.java`（新增, 118 lines）

**修改目的**：提供 Variant 值到 Iceberg 类型的转换工具。

**工作逻辑**：
- 定义 `NO_CONVERSION_NEEDED` 映射表，将 Iceberg Type 直接映射到 Variant PhysicalType（如 IntegerType -> INT32、LongType -> INT64 等）。
- `castTo(VariantValue value, Type type)` 方法：将 Variant 值转换为目标 Iceberg 类型。先检查是否需要转换（通过 NO_CONVERSION_NEEDED 映射），若类型匹配直接返回；否则通过 switch-case 处理需要类型提升的情况（如 INT8/INT16 -> Integer、INT8/INT16/INT32 -> Long、FLOAT -> Double 等），以及 FIXED、DECIMAL、BOOLEAN 等特殊类型。

### `api/src/test/java/org/apache/iceberg/expressions/TestInclusiveMetricsEvaluatorWithExtract.java`（新增, 678 lines）

**修改目的**：测试 Variant 字段提取的指标评估。

**工作逻辑**：针对 Variant 类型的 extract 操作进行全面的指标评估测试，验证各种比较操作符在 Variant 字段提取场景下的文件过滤行为。

### `api/src/test/java/org/apache/iceberg/expressions/TestInclusiveMetricsEvaluatorWithTransforms.java`（新增, 661 lines）

**修改目的**：测试保序转换的指标评估。

**工作逻辑**：针对 truncate、bucket 等保序转换进行全面的指标评估测试，验证转换后的比较操作能正确利用边界统计进行文件过滤。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkScan.java`（修改, +14/-9 lines）和 `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkScan.java`（修改, +14/-9 lines）

**修改目的**：适配评估器行为变更对测试的影响。

**工作逻辑**：由于评估器现在能处理转换表达式，某些之前返回 `ROWS_MIGHT_MATCH` 的场景现在可能返回 `ROWS_CANNOT_MATCH`，需要更新测试预期值。

## 小结

- **成效**：成功重构了 `InclusiveMetricsEvaluator`，使其能处理包含 transforms 和 extract 操作的边界表达式。对于保序转换（如 truncate），现在可以对转换后的值利用边界统计进行文件过滤。对于 Variant 字段提取，可以从 Variant 边界统计中提取特定字段的边界值进行比较。这显著提升了查询优化能力，减少了不必要的数据扫描。
- **影响范围**：涉及 API 模块的核心表达式评估逻辑，影响所有使用 `InclusiveMetricsEvaluator` 的查询路径。同时新增了 `VariantExpressionUtil` 工具类。影响 Spark v3.4/v3.5 的测试。
- **回迁到 1.4.x 的注意事项**：需要谨慎评估。此提交依赖于多个前置条件：(1) `BoundVisitor` 类需存在于 1.4.x 中；(2) `BoundTransform` 和 `BoundExtract` 类需存在；(3) Variant 类型支持（提交 1771）需已回迁；(4) `Transform.preservesOrder()` 方法需存在。如果这些前置条件不满足，此提交无法独立回迁。建议与 Variant 类型支持相关的提交一并评估回迁。如果 1.4.x 不支持 Variant 类型，可仅回迁 transforms 部分的改进。
