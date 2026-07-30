# 提交 2367：API, Core: Avoid boxing of integer in evaluators / simplify ManifestReader (#13589)

## 提交信息

- **序号**：2367 / 4088
- **哈希**：71629c12d2c609ae2473defdb5954e149f9f72e8
- **短哈希**：71629c12d
- **日期**：2025-07-17 12:54:23 -0600
- **作者**：Eduard Tudenhoefner
- **提交说明**：API, Core: Avoid boxing of integer in evaluators / simplify ManifestReader (#13589)
- **PR/Issue**：#13589

## 总体目的

这个提交通过两个方面的优化来提升 Iceberg 核心路径的性能和代码简洁性：一是避免在表达式评估器中对整数字段 ID 进行不必要的装箱（boxing），二是简化 `ManifestReader` 中冗余的 null 检查和条件分支。

背景：在 Java 中，`Integer`（包装类型）和 `int`（原始类型）之间存在自动装箱/拆箱开销。在 Iceberg 的元数据评估器（`InclusiveMetricsEvaluator` 和 `StrictMetricsEvaluator`）中，大量使用 `Integer id = term.ref().fieldId()` 来获取字段 ID，然后传递给 `containsNullsOnly(id)`、`containsNaNsOnly(id)` 等方法。`fieldId()` 返回 `int`，赋值给 `Integer` 会触发自动装箱，在高频调用的评估路径上产生不必要的对象分配和 GC 压力。将 `Integer` 改为 `int` 可消除这一开销。

同时，`ManifestReader` 中存在多处冗余的 null 检查和条件分支——例如 `rowFilter != null && rowFilter != Expressions.alwaysTrue()` 中的 null 检查，以及 evaluator 构建时对 null filter 的特殊处理。由于 `rowFilter` 初始化为 `Expressions.alwaysTrue()`（非 null），这些 null 检查是多余的，可以简化。

## 如何达成设计目的

1. **避免整数装箱**：将两个评估器中所有 `Integer id = ...fieldId()` 声明改为 `int id = ...fieldId()`。
2. **删除注释代码**：移除 `InclusiveMetricsEvaluator` 中被注释掉的 `isNullPreserving` 方法。
3. **简化 ManifestReader**：利用 `alwaysTrue()` 非空的特性，移除冗余的 null 检查和条件分支，统一 evaluator 构建逻辑。

## 修改详情

### `api/src/main/java/org/apache/iceberg/expressions/InclusiveMetricsEvaluator.java` (+30/-48 lines)

**修改目的**：避免整数装箱并清理注释代码。

**工作逻辑**：将所有 `Integer id = term.ref().fieldId()` 和 `Integer id = ref.fieldId()` 声明改为 `int id = ...`，涉及约 16 处改动，覆盖 `isNull`、`notNull`、`isNaN`、`notNaN`、`lt`、`ltEq`、`gt`、`gtEq`、`eq`、`in`、`notIn`、`startsWith`、`notStartsWith`、`parseLowerBound`、`parseUpperBound`、`extractLowerBound`、`extractUpperBound` 等方法。这些方法获取字段 ID 后传递给 `containsNullsOnly(id)`、`containsNaNsOnly(id)`、`mayContainNull(id)`、`lowerBounds.containsKey(id)` 等方法——这些方法参数为 `int`，此前因 `id` 为 `Integer` 会在传参时自动拆箱，改为 `int` 后消除装箱/拆箱往返。此外，删除了一段被注释掉的 `isNullPreserving(Bound<?> term)` 方法（约 16 行注释代码），该方法已不再使用。

### `api/src/main/java/org/apache/iceberg/expressions/StrictMetricsEvaluator.java` (+8/-8 lines)

**修改目的**：避免整数装箱。

**工作逻辑**：将 `lt`、`ltEq`、`gt`、`gtEq`、`eq`、`notEq`、`in`、`notIn` 共 8 个方法中的 `Integer id = ref.fieldId()` 改为 `int id = ref.fieldId()`。这些方法获取字段 ID 后传递给 `isNestedColumn(id)`、`lowerBounds.containsKey(id)`、`upperBounds.containsKey(id)` 等方法。

### `core/src/main/java/org/apache/iceberg/ManifestReader.java` (+4/-14 lines)

**修改目的**：简化冗余的 null 检查和条件分支。

**工作逻辑**：
- `hasRowFilter()` 和 `hasPartitionFilter()`：将 `rowFilter != null && rowFilter != Expressions.alwaysTrue()` 简化为 `rowFilter != alwaysTrue()`，移除 null 检查（因 `rowFilter` 和 `partFilter` 初始化为 `alwaysTrue()` 非 null）。
- `lazyEvaluator()`：移除对 `finalPartFilter` 的 null 判断分支，直接用 `finalPartFilter` 构建评估器（因 `Expressions.and(projected, partFilter)` 结果非 null）。
- `metricsEvaluator()`：移除对 `rowFilter` 的 null 判断分支，直接用 `rowFilter` 构建评估器（因 `rowFilter` 非 null）。
- `requireStatsProjection()`：将 `rowFilter != Expressions.alwaysTrue()` 改为 `rowFilter != alwaysTrue()`，使用静态导入的 `alwaysTrue()` 保持一致。

## 总结

该提交通过将评估器中的 `Integer` 字段 ID 改为 `int` 消除自动装箱开销，并简化 `ManifestReader` 中冗余的 null 检查和条件分支。这是一次面向性能和代码清晰度的优化，不影响功能行为，主要作用于元数据评估这一高频调用路径。
