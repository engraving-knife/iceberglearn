# 提交 3577：API: Implement notStartsWith bounds check in StrictMetricsEvaluator (#15883)

## 提交信息

- **序号**：3577 / 4088
- **哈希**：1d5463f687db2a9a80e1a35ac09f36b7e4e8cbeb
- **短哈希**：1d5463f68
- **日期**：2026-04-23 16:05:26 -0500
- **作者**：Bharath Krishna
- **提交说明**：API: Implement notStartsWith bounds check in StrictMetricsEvaluator (#15883)
- **PR/Issue**：#15883

## 总体目的

该提交实现了 `StrictMetricsEvaluator` 中 `notStartsWith` 操作的列级边界（bounds）检查逻辑。`StrictMetricsEvaluator` 用于判断一个数据文件是否**一定**满足给定表达式（即文件中所有行都满足条件），如果返回 true 则可以安全地跳过该文件。之前 `notStartsWith` 方法只有一个 TODO 注释，直接返回 `ROWS_MIGHT_NOT_MATCH`，即不做任何优化判断。

该提交利用列的下界（lower bound）和上界（upper bound）来判断文件中的所有值是否都不以给定前缀开头。如果下界截断后大于前缀，或上界截断后小于前缀，则文件中所有值都不以该前缀开头（`ROWS_MUST_MATCH`），可以安全跳过。这对于查询优化中过滤不需要扫描的数据文件具有重要意义。

## 如何达成设计目的

核心逻辑是比较前缀与列的上下界（截断到前缀长度后）的大小关系：

1. 如果下界（截断到前缀长度）大于前缀，则所有值都大于前缀，不可能以前缀开头 → `ROWS_MUST_MATCH`。
2. 如果上界（截断到前缀长度）小于前缀，则所有值都小于前缀，不可能以前缀开头 → `ROWS_MUST_MATCH`。
3. 其他情况（边界与前缀重叠）→ `ROWS_MIGHT_NOT_MATCH`。

截断到前缀长度的目的是确保比较的一致性：例如前缀 "ab" 与下界 "abc" 比较，截断后 "ab" vs "ab" 相等，说明可能有值以 "ab" 开头。

## 修改详情

### `api/src/main/java/org/apache/iceberg/expressions/StrictMetricsEvaluator.java` (+34/-2 lines)

**修改目的**：实现 notStartsWith 的边界检查逻辑。

**工作逻辑**：
```java
public <T> Boolean notStartsWith(BoundReference<T> ref, Literal<T> lit) {
  int id = ref.fieldId();
  if (isNestedColumn(id)) {
    return ROWS_MIGHT_NOT_MATCH;
  }
  if (containsNullsOnly(id)) {
    return ROWS_MUST_MATCH; // 全 null 的列，所有行都"不以"任何前缀开头
  }
  String prefix = (String) lit.value();
  Comparator<CharSequence> comparator = Comparators.charSequences();
  
  if (lowerBounds != null && lowerBounds.containsKey(id)) {
    CharSequence lower = Conversions.fromByteBuffer(ref.type(), lowerBounds.get(id));
    int length = Math.min(prefix.length(), lower.length());
    if (comparator.compare(lower.subSequence(0, length), prefix) > 0) {
      return ROWS_MUST_MATCH; // 下界 > 前缀，所有值都大于前缀
    }
  }
  if (upperBounds != null && upperBounds.containsKey(id)) {
    CharSequence upper = Conversions.fromByteBuffer(ref.type(), upperBounds.get(id));
    int length = Math.min(prefix.length(), upper.length());
    if (comparator.compare(upper.subSequence(0, length), prefix) < 0) {
      return ROWS_MUST_MATCH; // 上界 < 前缀，所有值都小于前缀
    }
  }
  return ROWS_MIGHT_NOT_MATCH;
}
```

### `api/src/test/java/org/apache/iceberg/expressions/TestStrictMetricsEvaluator.java` (+150/-4 lines)

**修改目的**：全面测试 notStartsWith 的边界检查逻辑。

**工作逻辑**：
- Schema 中新增 `nested_string_col` 嵌套字符串列。
- 新增测试数据文件 `STRING_FILE`（bounds ["abc", "abd"]）和 `STRING_FILE_2`（bounds ["aa", "dC"]）。
- 新增测试用例覆盖：
  - `testNotStartsWithAllNulls`：全 null 列，应匹配（ROWS_MUST_MATCH）。
  - `testNotStartsWithBoundsAbovePrefix`：bounds 高于前缀，应匹配。
  - `testNotStartsWithBoundsBelowPrefix`：bounds 低于前缀，应匹配。
  - `testNotStartsWithBoundsOverlapPrefix`：bounds 与前缀重叠，不应匹配。
  - `testNotStartsWithWiderRange`：更宽范围的 bounds 测试。
  - `testNotStartsWithNoStats`：无统计信息，不应匹配。
  - `testNotStartsWithSomeNullsBoundsOutsidePrefix`：部分 null 且 bounds 在前缀范围外。
  - `testNotStartsWithPrefixLongerThanBounds`：前缀长于 bounds 的情况。
  - `testNotStartsWithEmptyPrefix`：空前缀（所有字符串都以空前缀开头，不应匹配）。
  - `testNotStartsWithExactBoundMatch`：bounds 恰好等于前缀。
  - `testNotStartsWithNestedColumn`：嵌套列（不匹配）。

## 总结

该提交补全了 `StrictMetricsEvaluator` 中 `notStartsWith` 操作的边界检查实现，之前该方法是一个未实现的 TODO。通过利用列的上下界统计信息，可以判断数据文件中所有值是否都不以给定前缀开头，从而在查询优化时安全跳过不需要的文件。测试覆盖全面，包括各种边界场景。
