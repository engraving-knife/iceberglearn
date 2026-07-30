# 提交 3582：API: Use column bounds to evaluate startsWith in StrictMetricsEvaluator (#15902)

## 提交信息

- **序号**：3582 / 4088
- **哈希**：57b7c2bd564e5dfa8ccb7cb9e6386e7e942bac0f
- **短哈希**：57b7c2bd5
- **日期**：2026-04-24 11:16:06 -0500
- **作者**：Bharath Krishna
- **提交说明**：API: Use column bounds to evaluate startsWith in StrictMetricsEvaluator (#15902)
- **PR/Issue**：#15902

## 总体目的

该提交实现了 `StrictMetricsEvaluator` 中 `startsWith` 操作的列级边界检查逻辑。与提交 3577（`notStartsWith`）类似，`StrictMetricsEvaluator` 用于判断数据文件是否**一定**满足给定表达式。之前 `startsWith` 方法直接返回 `ROWS_MIGHT_NOT_MATCH`，未利用列的上下界进行优化判断。

该提交利用列的下界和上界判断文件中所有值是否都以给定前缀开头。如果下界和上界（截断到前缀长度后）都以前缀开头，则文件中所有值都必须以前缀开头（`ROWS_MUST_MATCH`），可以安全地将该文件纳入查询结果而无需扫描。这补充了 `startsWith` 在严格评估（strict evaluation）方向的实现。

## 如何达成设计目的

核心逻辑是检查下界和上界是否都以前缀开头：

1. 如果列可能包含 null 值，返回 `ROWS_MIGHT_NOT_MATCH`（null 不以任何前缀开头）。
2. 如果下界长度小于前缀长度，返回 `ROWS_MIGHT_NOT_MATCH`（下界不可能以前缀开头）。
3. 如果下界的前缀长度部分等于前缀，且上界的前缀长度部分也等于前缀，则所有值都以前缀开头 → `ROWS_MUST_MATCH`。
4. 其他情况 → `ROWS_MIGHT_NOT_MATCH`。

## 修改详情

### `api/src/main/java/org/apache/iceberg/expressions/StrictMetricsEvaluator.java` (+36/-0 lines)

**修改目的**：实现 startsWith 的边界检查逻辑。

**工作逻辑**：
```java
public <T> Boolean startsWith(BoundReference<T> ref, Literal<T> lit) {
  int id = ref.fieldId();
  if (isNestedColumn(id)) {
    return ROWS_MIGHT_NOT_MATCH;
  }
  if (canContainNulls(id)) {
    return ROWS_MIGHT_NOT_MATCH; // null 不以任何前缀开头
  }
  if (lowerBounds != null && lowerBounds.containsKey(id)
      && upperBounds != null && upperBounds.containsKey(id)) {
    String prefix = (String) lit.value();
    Comparator<CharSequence> comparator = Comparators.charSequences();
    CharSequence lower = Conversions.fromByteBuffer(ref.type(), lowerBounds.get(id));
    CharSequence upper = Conversions.fromByteBuffer(ref.type(), upperBounds.get(id));
    if (lower.length() < prefix.length()) {
      return ROWS_MIGHT_NOT_MATCH;
    }
    if (comparator.compare(lower.subSequence(0, prefix.length()), prefix) == 0) {
      if (upper.length() < prefix.length()) {
        return ROWS_MIGHT_NOT_MATCH;
      }
      if (comparator.compare(upper.subSequence(0, prefix.length()), prefix) == 0) {
        return ROWS_MUST_MATCH; // 上下界都以前缀开头
      }
    }
  }
  return ROWS_MIGHT_NOT_MATCH;
}
```

### `api/src/test/java/org/apache/iceberg/expressions/TestStrictMetricsEvaluator.java` (+91/-0 lines)

**修改目的**：全面测试 startsWith 的边界检查逻辑。

**工作逻辑**：
新增测试用例：
- `testStartsWithBothBoundsMatchPrefix`：上下界都以前缀开头（"ab"），应匹配。
- `testStartsWithSingleCharPrefixBothBoundsMatch`：单字符前缀（"a"），上下界都匹配。
- `testStartsWithOnlyLowerBoundMatchesPrefix`：下界匹配但上界不匹配，不应匹配。
- `testStartsWithBoundsDoNotMatchPrefix`：上下界都不匹配，不应匹配。
- `testStartsWithWiderRange`：更宽范围 bounds 的测试。
- `testStartsWithNoStats`：无统计信息，不应匹配。
- `testStartsWithAllNulls`：全 null 列，不应匹配。
- `testStartsWithSomeNulls`：部分 null，不应匹配。
- `testStartsWithPrefixLongerThanBounds`：前缀长于 bounds，不应匹配。
- `testStartsWithEmptyPrefix`：空前缀（所有字符串都以空前缀开头），应匹配。
- `testStartsWithNestedColumn`：嵌套列，不应匹配。

## 总结

该提交补全了 `StrictMetricsEvaluator` 中 `startsWith` 操作的边界检查实现，与 3577 提交的 `notStartsWith` 实现形成对称。通过利用列的上下界统计信息，可以判断数据文件中所有值是否都以给定前缀开头，从而在查询优化时安全地将文件纳入结果。测试覆盖全面，包括各种边界场景。
