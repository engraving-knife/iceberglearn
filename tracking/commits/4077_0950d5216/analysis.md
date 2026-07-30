# 提交 4077：Parquet: Fix initial-default rows dropped when filtering on the defaulted column

## 提交信息

- **序号**：4077 / 4088
- **哈希**：0950d5216dba185d499fa91efa90eacd93b9485b
- **短哈希**：0950d5216
- **日期**：2026-07-20 09:53:38 -0600
- **作者**：Christian Bush
- **提交说明**：Parquet: Fix initial-default rows dropped when filtering on the defaulted column (#16692)
- **PR/Issue**：#16692

## 总体目的

Iceberg 支持 schema 演进中的"列初始默认值"（initial-default）特性：当文件不含某个列（旧文件）时，读取该列时返回其定义的初始默认值。但 `ParquetMetricsRowGroupFilter` 在做 row group 级别的过滤时，如果某个 row group 完全不包含这个列（列缺省），它会被当作"读取为 null"来评估。这导致了一个严重的正确性 bug：

例如，schema 上加了列 `c`，初始默认值是 `'US'`。一个旧文件根本没有 `c` 列，按理该文件中的行读取 `c` 应得到 `'US'`。但如果查询条件是 `c = 'US'` 或 `c IS NOT NULL`，原过滤逻辑会把这个 row group 当作"全部 null"来评估，于是 `c = 'US'` 返回 false、`c IS NOT NULL` 返回 false，row group 被错误剪掉，导致应返回的行丢失。反之，对 `c = 'CA'` 这种本应剪掉的 row group，旧逻辑却可能错误保留（因为 null 被认为不等于 'CA'，会被判为 ROWS_CANNOT_MATCH，看似正确，但语义混乱）。

本提交修复 `ParquetMetricsRowGroupFilter`，在评估过滤条件时区分两种情况：当列存在于 schema 但文件中缺省且有 initial-default 时，使用默认值评估谓词；当列为嵌套字段时，由于父 struct 可能为 null，值可能是默认值或 null，于是只要默认值或 null 任一能匹配谓词就保留 row group。

## 如何达成设计目的

实现思路是新增一个 `predicate(BoundPredicate<T> pred)` 的重载钩子，作为 `BoundPredicateVisitor` 的兜底入口。在该钩子中：

1. 从谓词的 term 取出 `BoundReference`，得到字段 ID；
2. 通过 `schema.findField(id)` 查找字段，若字段存在且 `field.initialDefault() != null` 且该字段没有 value counts（即文件中无该列的统计信息，意味着列缺省）；
3. 调用 `pred.test((T) field.initialDefault())` 评估默认值是否满足谓词；
4. 对嵌套字段（`schema.asStruct().field(id) == null` 表示不在顶层），由于父 struct 可能为 null 让该字段读为 null，所以还需要评估 null 是否匹配（`matchesNull(pred)` 调用基类的 `super.predicate(pred)`，复用现有的"列缺省即 null"逻辑）；
5. 顶层字段则直接根据默认值是否匹配返回 `ROWS_MIGHT_MATCH` 或 `ROWS_CANNOT_MATCH`。

该实现不改变既有 `isNull`、`equal` 等具体谓词处理逻辑（它们仍按"列缺省即 null"语义），只是在更上层的 `predicate(...)` 钩子中拦截了有 initial-default 的字段场景。

## 修改详情

### `parquet/src/main/java/org/apache/iceberg/parquet/ParquetMetricsRowGroupFilter.java` (+30/-0 lines)

**修改目的**：在 `MetricsVisitor` 内部类中新增 `predicate(BoundPredicate<T>)` 钩子，专门处理文件中缺省但有 initial-default 的列。

**工作逻辑**：

```java
@Override
@SuppressWarnings("unchecked")
public <T> Boolean predicate(BoundPredicate<T> pred) {
  if (pred.term() instanceof BoundReference) {
    int id = ((BoundReference<T>) pred.term()).fieldId();
    Types.NestedField field = schema.findField(id);
    if (field != null && field.initialDefault() != null && !valueCounts.containsKey(id)) {
      boolean matchesDefault = pred.test((T) field.initialDefault());

      boolean isNestedField = schema.asStruct().field(id) == null;
      if (isNestedField) {
        return matchesDefault || matchesNull(pred);
      }

      return matchesDefault ? ROWS_MIGHT_MATCH : ROWS_CANNOT_MATCH;
    }
  }

  return super.predicate(pred);
}

private <T> boolean matchesNull(BoundPredicate<T> pred) {
  return super.predicate(pred);
}
```

关键点：
- `!valueCounts.containsKey(id)` 用于判断该字段在文件中是否真正缺省（没有列统计）；
- 嵌套字段通过 `schema.asStruct().field(id) == null` 判定（顶层 struct 的 field 不含该 id 说明它在更深层）；
- 嵌套场景返回 `matchesDefault || matchesNull`，因为父 struct 为 null 时该字段读为 null；
- 顶层场景返回二元结果，因为值唯一确定就是 default。

新增导入：`BoundPredicate` 和 `Types`。

### `data/src/test/java/org/apache/iceberg/data/TestMetricsRowGroupFilter.java` (+174/-0 lines)

**修改目的**：在 metrics row group filter 单元测试层面验证修复行为，覆盖多种数据类型和场景。

**工作逻辑**：新增 5 个测试方法和 1 个辅助方法：

1. **`testColumnNotInFileWithInitialDefault`**：测试 String 类型缺省列，default `'US'`。对一组"应匹配"的表达式（`equal "US"`、`notNull`、`notEqual "CA"`、`in`、`notIn`、`>=`、`<`、`startsWith "U"`、`notStartsWith "X"`）断言 `shouldRead` 为 true；对一组"不应匹配"的表达式断言为 false。
2. **`testDateColumnNotInFileWithInitialDefault`**：测试 Date 类型，default day 42。
3. **`testDoubleColumnNotInFileWithInitialDefault`**：测试 Double 类型，default 12.5，覆盖 `notNaN` 应读、`isNaN` 应跳过。
4. **`testNestedColumnMayReadAsDefaultOrNull`**：测试嵌套字段 `location.country` default `'US'`。验证：
   - `equal 'US'` 应读（默认值匹配）；
   - `notNull` 应读（默认值非 null）；
   - `equal 'CA'` 应跳过（既非默认 'US' 也非 null）；
   - `isNull` 应读（父 struct 可能为 null）。
5. **`testPresentColumnWithoutStatsIsNotEvaluatedAgainstDefault`**：关键回归测试，验证字段在文件中确实存在但没有统计信息时（id=2 复用 fixture 中的 no_stats 列），不应该用 default 评估，而应继续走原有"无统计则保守读"逻辑——保证修复不会对"列存在但缺统计"场景产生副作用。
6. **`shouldReadWithSchema`** 辅助方法：用指定 schema 构造 `ParquetMetricsRowGroupFilter` 并对 fixture 的 parquet schema 和 row group metadata 调用 `shouldRead`。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/sql/TestFilterPushDown.java` (+78/-0 lines)

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/sql/TestFilterPushDown.java` (+78/-0 lines)

### `spark/v4.1/spark/src/test/java/org/apache/iceberg/spark/sql/TestFilterPushDown.java` (+78/-0 lines)

**修改目的**：在 Spark 端到端集成测试层面验证修复在真实 SQL 查询中正确生效，三个 Spark 版本（3.5/4.0/4.1）均同步补充相同测试。

**工作逻辑**：在每个版本的 `TestFilterPushDown` 中新增两个测试：

1. **`testFilterPushdownOnInitialDefaultColumnAbsentFromFile`**：
   - 建表 `(id, name)`，先 insert 一行；
   - 通过 schema 演进 `addColumn("c", StringType, lit("US"))` 加列并指定初始默认值 `'US'`；
   - 再 insert 两行带真实 `c` 值（'US' 和 'CA'）；
   - 验证 `c = 'US'` 查询返回旧行（读为 default 'US'）和第二行（实际 'US'），第三行 'CA' 被剪掉；
   - 验证 `upper(c) = 'US'` 由于 Spark 不能下推 upper，但仍下推 `c IS NOT NULL`；
   - 验证 `c IS NULL` 返回空（旧行读为 default 'US' 非 null）；
   - 验证 `c = 'CA'` 只返回第三行。

2. **`testFilterPushdownOnNestedInitialDefaultColumnAbsentFromFile`**：
   - 建表 `(id, loc STRUCT<city>)`，insert 包含 NULL loc 的行；
   - schema 演进加嵌套列 `loc.country` default `'US'`；
   - 验证 `loc.country = 'US'` 返回 loc 非 null 的旧行（读 default 'US'）；loc 为 null 的行被剪掉（因为 null.country 为 null，不匹配）；
   - 加新行后验证 `loc.country = 'CA'` 只返回新行（旧行 row group 被剪掉，因为默认 'US' 和 null 都不匹配 'CA'）。

## 总结

这是一个重要的正确性修复，解决了 Iceberg 在 schema 演进（添加带 initial-default 的列）后，Parquet row group 过滤器错误地把缺省列当作 null 处理，导致该列默认值与过滤条件相关的查询结果不正确（应匹配的行被剪掉）的问题。修复在 `ParquetMetricsRowGroupFilter` 的 `predicate(BoundPredicate)` 钩子中统一处理 initial-default 评估，并细致区分了顶层字段与嵌套字段（嵌套字段需考虑父 struct 为 null 的情况）。配套的单元测试和 Spark 集成测试覆盖了多种数据类型、嵌套场景以及关键的回归场景（列存在但缺统计不应误用 default），保障了修复的稳健性。
