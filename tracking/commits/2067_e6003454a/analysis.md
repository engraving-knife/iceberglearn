# 提交 2067：Parquet: Shred variant arrays when element type is uniform (#12933)

## 提交信息

- **序号**：2067 / 4088
- **哈希**：e6003454afff747071591c4f759f88f493ee22f8
- **短哈希**：e6003454a
- **日期**：2025-04-30 11:22:21 -0700
- **作者**：Aihua Xu
- **提交说明**：Parquet: Shred variant arrays when element type is uniform (#12933)
- **PR/Issue**：#12933

## 总体目的

这是对前一个提交（2056，#12847）Variant array shredding 实现的策略调整。#12847 中 `ParquetVariantUtil.array()` 选择 shredding 类型时采用"多数类型"策略：统计各元素类型出现次数，选出现次数最多的类型作为 shred 类型，元素类型不一致的元素会被回退到未 shred 的 `value` 列。这种策略在元素类型混合时仍会 shred 一部分元素，但语义上不直观且可能导致读回时部分元素落到 `value` 列、部分落到 `typed_value` 列，增加正确性风险与读侧复杂度。

本提交将 shredding 策略改为更保守、更明确的"全部一致才 shred"：仅当数组中所有非 null 元素的 shredding 类型完全相同时才 shred 该数组，否则不 shred（返回 null，整个数组写到 `value` 列）。这样 shred 与非 shred 的边界清晰，读侧只需处理两种互斥情形，正确性更易保证。

## 如何达成设计目的

修改 `ParquetVariantUtil.array()` 中的类型选择逻辑：
- 取第一个元素类型作为候选 `shredType`。
- 若 `shredType` 非 null 且所有元素类型都等于 `shredType`，则返回 `list(shredType)` 进行 shred。
- 否则返回 null（不 shred）。

移除了原先基于 `Collectors.groupingBy` + `max(Map.Entry.comparingByValue())` 的多数类型统计逻辑及相关 import（`Map`、`Function`、`Collectors`），代码更简洁。

测试侧把 `NESTED_ARRAY_BUFFER` 中第二个子数组的元素由 `("string", "iceberg")` 改为 `("apple", "banana")`，避免两个子数组完全相同——这能更真实地测试"全部一致才 shred"在嵌套数组场景下的行为（原先两个子数组完全相同，无法有效区分 shred 行为）。

## 修改详情

### `parquet/src/main/java/org/apache/iceberg/parquet/ParquetVariantUtil.java` (修改, +6/-15 lines)

**修改目的**：把 array shredding 策略从"多数类型"改为"全部一致"。

**工作逻辑**：
`array(VariantArray array, List<Type> elementResults)` 中：
- 元素结果为空仍返回 null。
- 取 `shredType = elementResults.get(0)`，若 `shredType != null` 且 `elementResults.stream().allMatch(type -> Objects.equals(type, shredType))` 则 `return list(shredType)`。
- 否则 `return null`（不 shred）。
- 移除原"多数类型"统计代码与相关 import。

### `parquet/src/test/java/org/apache/iceberg/parquet/TestVariantWriters.java` (修改, +1/-1 lines)

**修改目的**：调整嵌套数组测试数据，使其更有效地覆盖 shred 行为。

**工作逻辑**：
`NESTED_ARRAY_BUFFER` 的第二个子数组元素由 `Variants.of("string"), Variants.of("iceberg")` 改为 `Variants.of("apple"), Variants.of("banana")`，使两个子数组内容不同但类型相同（均为字符串），更真实地测试嵌套数组的 shredding。

## 总结

本提交把 Variant array 的 Parquet shredding 策略从"按多数类型 shred"改为"仅当所有元素类型完全一致才 shred"，使 shred 边界清晰、读侧更简单、正确性更易保证。代码同时更简洁（移除类型统计逻辑与 import）。测试侧调整嵌套数组数据以增强覆盖。
