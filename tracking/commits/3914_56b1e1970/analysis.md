# 提交 3914：Parquet: Fix variant metrics crash when value column has no stats (#16585)

## 提交信息

- **序号**：3914 / 4088
- **哈希**：56b1e19707913609a4596f9eb8cd03b7519e3ad1
- **短哈希**：56b1e1970
- **日期**：2026-06-20 13:27:26 -0700
- **作者**：Neelesh Salian
- **提交说明**：Parquet: Fix variant metrics crash when value column has no stats (#16585)
- **PR/Issue**：#16585

## 总体目的

修复当 Variant 值列没有统计信息时 Parquet metrics 计算崩溃的问题。在 shredded variant（分片变体）的 metrics 计算中，代码假设 value 列总是有统计信息（`valueResult` 非空），但当 Parquet 写入器禁用了列统计（通过 `ParquetProperties.builder().withStatisticsEnabled(false)`）或某些其他原因导致 value 列统计缺失时，`Iterables.getOnlyElement(valueResult)` 会抛出异常（NoSuchElementException 或 IllegalArgumentException）。

这在实际场景中可能发生：用户可能通过 Parquet 属性禁用某些列的统计信息以减少文件开销，但 Variant 列的 metrics 计算未考虑这种情况，导致写入失败。

## 如何达成设计目的

在 `ParquetMetrics` 中获取 value metrics 之前添加空结果检查：如果 `valueResult` 为空，则返回空列表（表示 typed bounds 无效），而不是尝试获取唯一元素。

## 修改详情

### `parquet/src/main/java/org/apache/iceberg/parquet/ParquetMetrics.java` (+5/-0 lines)

**修改目的**：修复 value 列无统计时的崩溃。

**工作逻辑**：
```java
if (Iterables.isEmpty(valueResult)) {
  // missing value stats invalidate typed bounds
  return ImmutableList.of();
}

ParquetVariantUtil.VariantMetrics valueMetrics = Iterables.getOnlyElement(valueResult);
```

在调用 `Iterables.getOnlyElement(valueResult)` 之前检查 `valueResult` 是否为空。如果为空，返回空列表，表示 typed bounds 无效（不生成），避免 `getOnlyElement` 抛出异常。这与"缺失统计意味着无法计算 typed bounds"的语义一致。

### `parquet/src/test/java/org/apache/iceberg/parquet/TestVariantMetrics.java` (+47/-0 lines)

**修改目的**：测试 value 列统计为空时的 metrics 计算。

**工作逻辑**：
新增 `testShreddedValueColumnWithEmptyStats` 测试：
1. 创建 InMemoryOutputFile
2. 构建 shredded variant schema
3. 使用 `ParquetProperties.builder().withStatisticsEnabled(false)` 禁用统计
4. 直接使用 `ParquetWriter` 构造器写入（因为 `Parquet.write()` 无法禁用 variant 子列统计）
5. 写入一条包含 variant 值的记录
6. 断言：
   - `metrics.recordCount()` 为 1
   - `metrics.lowerBounds()` 不包含字段 id 2（variant 列）
   - `metrics.upperBounds()` 不包含字段 id 2

测试验证了当 value 列统计被禁用时，metrics 计算不会崩溃，且正确地不生成 variant 列的 bounds。

## 总结

修复了 Parquet Variant metrics 计算在 value 列无统计信息时的崩溃问题。修复方式简洁：在获取 value metrics 前检查结果是否为空，为空时跳过 typed bounds 计算。测试通过禁用 Parquet 列统计验证了修复效果，确保用户可以安全地禁用 Variant 列的统计而不导致写入失败。
