# 提交 3069：API, Spark: Optimize NOT IN and != predicate evaluation for fields containing a single-value (#14593)

## 提交信息

- **序号**：3069 / 4088
- **哈希**：bde85b0c347692cd1d2b322f6ab970583e8e3a77
- **短哈希**：bde85b0c3
- **日期**：2026-01-06
- **作者**：Joy Haldar
- **提交说明**：API, Spark: Optimize NOT IN and != predicate evaluation for fields containing a single-value (#14593)
- **PR/Issue**：#14593

## 总体目的

本提交优化 Iceberg 的 `InclusiveMetricsEvaluator`（包容性指标评估器）对 `NOT IN`（`notIn`）和 `!=`（`notEq`）谓词的文件裁剪能力。在此前的实现中，`notEq` 和 `notIn` 方法均直接返回 `ROWS_MIGHT_MATCH`，即从不裁剪文件。其注释解释了原因：列的上下界（lower/upper bounds）不一定是真实的 min/max 值，因此 `notEq(col, X)` 在 bounds 为 `(X, Y)` 时无法保证 X 一定存在于列中，不能据此裁剪。这一保守策略虽然安全，但在某些可以确定裁剪的场景下导致了不必要的文件读取。

本提交识别出一个可安全裁剪的特例：当一个数据文件的某列 min == max（即文件内该列所有行都是同一个值）、且该列无 null 值、无 NaN 值时，可以确定该文件中该列的唯一值。此时若该唯一值等于 `notEq` 的字面量，或包含在 `notIn` 的排除集合中，则可以安全地裁剪该文件（返回 `ROWS_CANNOT_MATCH`），因为文件中所有行的该列值都与谓词冲突。

这一优化对查询性能有实际意义：在数据按某列聚簇或排序的场景下，单个数据文件内某列往往只有单一值（例如按日期分区或按 ID 排序的文件），此时 `!=` 和 `NOT IN` 查询可以跳过大量不匹配的文件。

## 如何达成设计目的

在 `InclusiveMetricsEvaluator` 中新增私有方法 `uniqueValue(Bound<T> term)`，用于判断某列在当前数据文件中是否只含单一值并返回该值。该方法综合检查 null 计数、NaN 计数、上下界是否相等。随后在 `notEq` 和 `notIn` 方法开头调用 `uniqueValue`，若返回非 null 且与谓词字面量匹配则裁剪。配套新增了 API 层的单元测试和 Spark 多版本的集成测试来验证裁剪效果。

## 修改详情

### `api/src/main/java/org/apache/iceberg/expressions/InclusiveMetricsEvaluator.java` (+38/-0 lines)

**修改目的**：为 `notEq` 和 `notIn` 谓词新增基于单值列的文件裁剪逻辑。

**工作逻辑**：
- 新增私有方法 `uniqueValue(Bound<T> term)`：返回列的单值或 null。判断条件依次为：
  1. `mayContainNull(id)` 为 true 则返回 null（文件含 null，无法确定唯一值语义）；
  2. 获取 `lowerBound` 和 `upperBound`，任一为 null 或为 NaN 则返回 null；
  3. 若 `nanCounts` 中该列计数非 0 则返回 null（存在 NaN 行）；
  4. 若 `lower.equals(upper)` 不成立则返回 null（多值文件）；
  5. 否则返回 `lower` 作为该列唯一值。
- `notEq(Bound<T> term, Literal<T> lit)` 方法：在原注释后新增 `T value = uniqueValue(term);`，若 `value != null && lit.comparator().compare(value, lit.value()) == 0` 则返回 `ROWS_CANNOT_MATCH`（文件唯一值等于排除值，所有行都不匹配）。否则继续返回 `ROWS_MIGHT_MATCH`。
- `notIn(Bound<T> term, Set<T> literalSet)` 方法：同理新增 `T value = uniqueValue(term);`，若 `value != null && literalSet.contains(value)` 则返回 `ROWS_CANNOT_MATCH`。否则返回 `ROWS_MIGHT_MATCH`。

### `api/src/test/java/org/apache/iceberg/expressions/TestInclusiveMetricsEvaluator.java` (+168/-0 lines)

**修改目的**：为 `notEq` 和 `notIn` 的单值列裁剪新增全面的单元测试。

**工作逻辑**：
新增两个测试方法：
- `testNotEqWithSingleValue`：构造多组 `DataFile` 测试用例：(1) 值范围文件（lower="aaa", upper="zzz"）对 `notEqual("required", "aaa")` 应读取（多值无法裁剪）；(2) 单值文件（lower=upper="abc"）对 `notEqual("required", "abc")` 应不读取（裁剪）、对 `notEqual("required", "def")` 应读取；(3) 单值含 null 文件（valueCount=10, nullCount=2）对 `notEqual("required", "abc")` 应读取（含 null 不能裁剪）；(4) 单值含 NaN 文件（nanCount=2）对 `notEqual("no_nans", 5.0F)` 应读取；(5) 上下界为 NaN 的文件应读取。
- `testNotInWithSingleValue`：类似地覆盖值范围文件、单值文件（命中/不命中排除集）、含 null 文件、含 NaN 文件等场景。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkScan.java` (+3/-4 lines)

**修改目的**：更新 Spark v3.4 集成测试，反映 `notEq`/`notIn` 现在能裁剪单值文件。

**工作逻辑**：
三处原本断言 `scan.planInputPartitions()` 为 10（即不裁剪）的测试改为断言 5（裁剪一半文件），并移除了注释 `// notEq can't be answered using column bounds because they are not exact`。这些测试场景中表的数据文件被设计为单值文件，优化后可被裁剪。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkScan.java` (+3/-4 lines)

**修改目的**：对 Spark v3.5 做相同测试更新。

**工作逻辑**：与 v3.4 改动完全一致，三处断言从 10 改为 5。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkScan.java` (+3/-4 lines)

**修改目的**：对 Spark v4.0 做相同测试更新。

**工作逻辑**：与 v3.4 改动完全一致。

### `spark/v4.1/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkScan.java` (+3/-4 lines)

**修改目的**：对 Spark v4.1 做相同测试更新。

**工作逻辑**：与 v3.4 改动完全一致。

## 总结

本提交通过在 `InclusiveMetricsEvaluator` 中识别"单值列"（min == max 且无 null/NaN）这一特例，使 `!=` 和 `NOT IN` 谓词能够安全裁剪此类数据文件，弥补了此前这两个谓词从不裁剪的保守策略。在数据聚簇/排序场景下可显著减少文件扫描量，提升查询性能。改动覆盖 API 层核心逻辑与全部受支持的 Spark 版本测试。
