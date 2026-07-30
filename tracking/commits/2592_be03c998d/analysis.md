# 提交 2592：Core: Use safeContainsKey to avoid NPE for CountNonNull (#13980)

## 提交信息

- **序号**：2592 / 4088
- **哈希**：be03c998d96d0d1fae13aa8c53d6c7c87e2d60ba
- **短哈希**：be03c998d
- **日期**：2025-09-03 17:50:03 -0700
- **作者**：jackylee
- **提交说明**：Core: Use safeContainsKey to avoid NPE for CountNonNull (#13980)
- **PR/Issue**：#13980

## 总体目的

本次提交修复了 `CountNonNull` 聚合评估器中的一个空指针异常（NPE）缺陷。

在 Iceberg 中，`CountNonNull` 用于统计某列非空值的数量。它在判断数据文件是否包含某字段的统计信息时，会检查 `file.valueCounts()` 和 `file.nullValueCounts()` 两个映射。原代码中，对 `valueCounts` 使用了 `safeContainsKey` 方法（该方法会处理 null 映射），但对 `nullValueCounts` 却直接调用了 `file.nullValueCounts().containsKey(fieldId)`。

问题在于，当数据文件的 `nullValueCounts()` 返回 null 时（即该文件没有记录空值统计信息），直接调用 `.containsKey()` 会抛出 NullPointerException。这种情况在实际场景中并不罕见——并非所有写入器都会为所有列生成完整的空值统计信息。

## 如何达成设计目的

将 `file.nullValueCounts().containsKey(fieldId)` 替换为 `safeContainsKey(file.nullValueCounts(), fieldId)`，与同一方法中对 `valueCounts` 的处理方式保持一致。`safeContainsKey` 是一个工具方法，内部会先判断映射是否为 null，如果为 null 则返回 false，否则才调用 `containsKey`。

同时新增了单元测试 `testMissingSomeStats`，模拟一个 `nullValueCounts` 和 `nanValueCounts` 都为 null 的数据文件，验证 `CountNonNull` 在这种情况下不会抛出异常，而是正确地将该聚合器标记为无效并返回 null 结果。

## 修改详情

### `api/src/main/java/org/apache/iceberg/expressions/CountNonNull.java` (+1/-1 lines)

**修改目的**：修复空指针异常。

**工作逻辑**：在 `hasValue(DataFile file)` 方法中，将：
```java
return safeContainsKey(file.valueCounts(), fieldId)
    && file.nullValueCounts().containsKey(fieldId);
```
改为：
```java
return safeContainsKey(file.valueCounts(), fieldId)
    && safeContainsKey(file.nullValueCounts(), fieldId);
```
这样当 `nullValueCounts()` 返回 null 时，`safeContainsKey` 会安全地返回 false，而不是抛出 NPE。

### `api/src/test/java/org/apache/iceberg/expressions/TestAggregateEvaluator.java` (+34/-0 lines)

**修改目的**：添加回归测试覆盖空值统计缺失的场景。

**工作逻辑**：
1. 新增测试数据 `MISSING_SOME_STATS`，模拟一个文件，其 `nullValueCounts` 和 `nanValueCounts` 都为 null，但 `valueCounts` 和 bounds 有部分值。
2. 新增测试方法 `testMissingSomeStats`，对该数据执行 `countStar()`、`count("some_nulls")`、`max("some_nulls")`、`min("some_nulls")` 聚合。
3. 验证 `allAggregatorsValid()` 返回 false（因为统计信息不完整）。
4. 验证结果为 `{20L, null, null, null}`，即 countStar 返回 20（来自 valueCounts），而依赖空值统计的 count/min/max 返回 null。

## 总结

这是一个典型的防御性编程修复。原代码对两个类似的映射检查使用了不一致的方法，导致其中一个可能 NPE。修复使两者保持一致，并添加了回归测试。这类 bug 在生产环境中可能导致查询失败，因为并非所有数据文件都保证有完整的空值统计。
