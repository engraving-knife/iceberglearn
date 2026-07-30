# 提交 2647：Core: Add CountNull Aggregation Support (#13981)

## 提交信息

- **序号**：2647 / 4088
- **哈希**：ade12635baf7345e6a367899ff61f73a971aeaff
- **短哈希**：ade12635b
- **日期**：2025-09-17 09:44:24 -0700
- **作者**：jackylee
- **提交说明**：Core: Add CountNull Aggregation Support (#13981)
- **PR/Issue**：#13981

## 总体目的

Iceberg 的表达式系统支持聚合函数（Aggregate），此前已支持 Max、Min、Count（非空值计数）、CountStar（全行计数）。但在实际数据分析中，统计某字段为 null 的行数（即 CountNull）是一个常见需求，例如用于数据质量检查、空值率分析等。此前 Iceberg 没有内置的 CountNull 聚合，使用者无法通过表达式系统直接表达"统计某字段为 null 的记录数"。

本提交新增 `CountNull` 聚合函数，用于统计指定字段为 null 的记录数。它复用了已有的 `CountAggregate` 框架，在数据文件级别可利用文件统计信息中的 `nullValueCounts` 进行加速（无需逐行扫描）。

## 如何达成设计目的

1. 在 `Expression.Operation` 枚举中新增 `COUNT_NULL` 操作。
2. 新建 `CountNull<T>` 类继承 `CountAggregate<T>`，实现：
   - `countFor(StructLike row)`：逐行评估字段值，为 null 返回 1 否则 0。
   - `countFor(DataFile file)`：从文件的 `nullValueCounts` 统计中读取该字段的空值计数。
   - `hasValue(DataFile file)`：检查文件是否包含该字段的空值计数。
3. 在 `Expressions` 工厂类中新增 `countNull(String name)` 方法创建未绑定聚合。
4. 在 `UnboundAggregate.bind()` 中为 `COUNT_NULL` 操作创建 `CountNull` 实例。
5. 在 `Aggregate` 和 `BoundAggregate` 的 `toString()` 中为 `COUNT_NULL` 输出 `count_if(field is null)` 形式。
6. `BoundAggregate.type()` 对 `COUNT_NULL` 返回 `LongType`。
7. 更新测试覆盖 CountNull 的绑定和求值。

## 修改详情

### `api/src/main/java/org/apache/iceberg/expressions/CountNull.java` (新建, +48 lines)

**修改目的**：实现 CountNull 聚合。

**工作逻辑**：继承 `CountAggregate<T>`，构造时获取字段 ID。`countFor(StructLike)` 通过 `term().eval(row)` 判断是否为 null，返回 1L 或 0L。`countFor(DataFile)` 从 `file.nullValueCounts()` 读取该字段的空值计数（默认 0L）。`hasValue(DataFile)` 检查 `nullValueCounts` 是否包含该字段。

### `api/src/main/java/org/apache/iceberg/expressions/Expression.java` (+1/-0 lines)

**修改目的**：新增操作枚举值。

**工作逻辑**：在 `Operation` 枚举中添加 `COUNT_NULL`。

### `api/src/main/java/org/apache/iceberg/expressions/Expressions.java` (+4/-0 lines)

**修改目的**：提供创建 CountNull 聚合的工厂方法。

**工作逻辑**：新增 `countNull(String name)` 返回 `new UnboundAggregate<>(Operation.COUNT_NULL, ref(name))`。

### `api/src/main/java/org/apache/iceberg/expressions/UnboundAggregate.java` (+2/-0 lines)

**修改目的**：绑定 COUNT_NULL 操作。

**工作逻辑**：在 `bind` 的 switch 中为 `COUNT_NULL` 创建 `new CountNull<>(boundTerm)`。

### `api/src/main/java/org/apache/iceberg/expressions/Aggregate.java` (+4/-2 lines)

**修改目的**：支持 COUNT_NULL 的字符串表示和文档更新。

**工作逻辑**：类 Javadoc 更新支持的聚合列表。`toString()` 中为 `COUNT_NULL` 返回 `count_if(term is null)`。

### `api/src/main/java/org/apache/iceberg/expressions/BoundAggregate.java` (+3/-1 lines)

**修改目的**：支持 COUNT_NULL 的类型和描述。

**工作逻辑**：`type()` 对 `COUNT_NULL` 返回 `LongType`。`toString()` 输出 `count_if(term is null)`。

### `api/src/test/java/org/apache/iceberg/expressions/TestAggregateBinding.java` (+4/-2 lines)

**修改目的**：测试 CountNull 绑定。

**工作逻辑**：在测试列表中加入 `Expressions.countNull("x")`，验证其能正确绑定到 schema。

### `api/src/test/java/org/apache/iceberg/expressions/TestAggregateEvaluator.java` (+15/-5 lines)

**修改目的**：测试 CountNull 求值结果。

**工作逻辑**：在多处测试中加入 `countNull` 聚合，验证：
- id 字段（无空值）：countNull = 30L。
- all_nulls 字段（全空）：countNull = 90L。
- some_nulls/no_stats 字段（无统计或部分空）：聚合无效时返回 null。

## 总结

本提交新增了 CountNull 聚合函数，使 Iceberg 表达式系统支持统计某字段为 null 的记录数。它复用 CountAggregate 框架，可利用数据文件的 nullValueCounts 统计信息加速计算。这补齐了聚合函数的能力，便于数据质量分析等场景使用。
