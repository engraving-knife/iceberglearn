# 提交 4019：API: Guard against null in IN/NOT_IN predicates (#17014)

## 提交信息

- **序号**：4019 / 4088
- **哈希**：187da3ed86e3226318d601828037d5d57c42a659
- **短哈希**：187da3ed8
- **日期**：2026-07-12 13:08:40 -0600
- **作者**：Yuya Ebihara
- **提交说明**：API: Guard against null in IN/NOT_IN predicates (#17014)
- **PR/Issue**：#17014

## 总体目的

本提交修复 `IN`/`NOT_IN` 谓词在处理 null 值时的潜在 NPE（NullPointerException）和语义错误问题。

原实现中，`BoundSetPredicate.test`、`Evaluator.in`、`ResidualEvaluator.in`/`notIn` 直接调用 `literalSet.contains(value)`，而 `value` 可能是 null（当字段值为 null 时）。虽然大多数 `Set` 实现允许 `contains(null)`，但：
1. 若 `literalSet` 是不支持 null 的实现（如某些 ImmutableSet），会抛 NPE。
2. 语义上，SQL 中 `NULL IN (set)` 的结果是 `NULL`（未知），而非 `false`；`NULL NOT IN (set)` 也是 `NULL` 而非 `true`。原实现将 null 视为"不在集合中"，导致 `NOT_IN` 对 null 行返回 true（错误地包含 null 行）。

本提交统一在 IN/NOT_IN 判断前对 value 做 null 检查，使 `IN` 对 null 返回 false，`NOT_IN` 对 null 返回 true（与"null 不在集合中"的宽松语义一致，且避免 NPE）。

## 如何达成设计目的

在三个评估器中对 `value` 增加 null 守卫：
- `IN`：`value != null && literalSet.contains(value)`——null 时返回 false。
- `NOT_IN`：`value == null || !literalSet.contains(value)`——null 时返回 true。

并新增测试覆盖 null 值场景。

## 修改详情

### `api/src/main/java/org/apache/iceberg/expressions/BoundSetPredicate.java` (+2/-2 lines)

**修改目的**：在 `test(T value)` 中对 null 做守卫。

**工作逻辑**：
```java
case IN:
  return value != null && literalSet.contains(value);  // 原为 literalSet.contains(value)
case NOT_IN:
  return value == null || !literalSet.contains(value);  // 原为 !literalSet.contains(value)
```

### `api/src/main/java/org/apache/iceberg/expressions/Evaluator.java` (+2/-1 lines)

**修改目的**：在 `in` 评估中对 null 做守卫。

**工作逻辑**：
```java
public <T> Boolean in(Bound<T> valueExpr, Set<T> literalSet) {
  T value = valueExpr.eval(struct);
  return value != null && literalSet.contains(value);
}
```

### `api/src/main/java/org/apache/iceberg/expressions/ResidualEvaluator.java` (+4/-2 lines)

**修改目的**：在 `in`/`notIn` 残差评估中对 null 做守卫。

**工作逻辑**：
```java
public <T> Expression in(BoundReference<T> ref, Set<T> literalSet) {
  T value = ref.eval(struct);
  return value != null && literalSet.contains(value) ? alwaysTrue() : alwaysFalse();
}

public <T> Expression notIn(BoundReference<T> ref, Set<T> literalSet) {
  T value = ref.eval(struct);
  return value == null || !literalSet.contains(value) ? alwaysTrue() : alwaysFalse();
}
```

### `api/src/test/java/org/apache/iceberg/expressions/TestEvaluator.java` (+12/-0 lines)

**修改目的**：新增 Evaluator 对 null 值的 IN 测试。

### `api/src/test/java/org/apache/iceberg/expressions/TestPredicateBinding.java` (+24/-0 lines)

**修改目的**：新增 BoundSetPredicate 对 null 值的 IN/NOT_IN 测试。

### `api/src/test/java/org/apache/iceberg/transforms/TestResiduals.java` (+46/-0 lines)

**修改目的**：新增 ResidualEvaluator 对 null 值的 IN/NOT_IN 测试。

## 总结

本提交修复了 IN/NOT_IN 谓词在处理 null 字段值时的潜在 NPE 和语义问题，统一在三个评估器（BoundSetPredicate、Evaluator、ResidualEvaluator）中加入 null 守卫。`IN` 对 null 返回 false，`NOT_IN` 对 null 返回 true，行为更安全且符合"null 不在任何集合中"的直觉语义。改动虽小但涉及表达式评估的核心路径，配套补齐了三个评估器的测试覆盖。
