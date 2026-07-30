# 提交 3482：Core: Fix NPE of generateRandomMetrics in FileGenerationUtil (#15748)

## 提交信息

- **序号**：3482 / 4088
- **哈希**：b086b4cbe96bd6121e1bb03630241fad84344966
- **短哈希**：b086b4cbe9
- **日期**：2026-03-30 11:26:56 -0700
- **作者**：Hongyue/Steve Zhang
- **提交说明**：Core: Fix NPE of generateRandomMetrics in FileGenerationUtil (#15748)
- **PR/Issue**：#15748

## 总体目的

修复 `FileGenerationUtil.generateBounds` 方法中的空指针异常（NPE）。该方法在生成随机 metrics 时调用 `generateBound` 生成边界值，但在某些 metrics mode（如 `none`、`counts`）下 `generateBound` 可能返回 `null`。原代码在生成两个值后立即使用 `Comparators.forType(type)` 创建比较器并调用 `cmp.compare(value1, value2)`，当任一值为 null 时会抛出 NPE。

## 如何达成设计目的

1. 在生成 `value1` 和 `value2` 后，先检查是否为 null。如果任一为 null，直接返回 `Pair.of(null, null)`（即无边界信息）。
2. 将 `Comparator` 的创建移到 null 检查之后，避免不必要的创建和潜在的 NPE。
3. 新增参数化测试覆盖所有 metrics mode（`none`、`counts`、`truncate(16)`、`full`），确保不再抛出 NPE。

## 修改详情

### `core/src/test/java/org/apache/iceberg/FileGenerationUtil.java` (+6/-1 lines)

**修改目的**：修复 `generateBounds` 方法的 NPE。

**工作逻辑**：
```java
private static Pair<ByteBuffer, ByteBuffer> generateBounds(PrimitiveType type, MetricsMode mode) {
-  Comparator<Object> cmp = Comparators.forType(type);
  Object value1 = generateBound(type, mode);
  Object value2 = generateBound(type, mode);
+
+  if (value1 == null || value2 == null) {
+    return Pair.of(null, null);
+  }
+  Comparator<Object> cmp = Comparators.forType(type);
  if (cmp.compare(value1, value2) > 0) {
    ...
```
将 Comparator 创建移到 null 检查之后；若任一值为 null，返回 null 边界对。

### `core/src/test/java/org/apache/iceberg/TestFileGenerationUtil.java` (+19 lines)

**修改目的**：新增参数化测试覆盖所有 metrics mode。

**工作逻辑**：
```java
@ParameterizedTest
@ValueSource(strings = {"none", "counts", "truncate(16)", "full"})
void testBoundsForAllMetricsModes(String metricsMode) {
  MetricsConfig metricsConfig = MetricsConfig.fromProperties(
      ImmutableMap.of(TableProperties.DEFAULT_WRITE_METRICS_MODE, metricsMode));
  Metrics metrics = FileGenerationUtil.generateRandomMetrics(
      SCHEMA, metricsConfig, ImmutableMap.of(), ImmutableMap.of());
  checkBounds(metrics, metricsConfig);
}
```
使用 `@ValueSource` 参数化测试，遍历四种 metrics mode 调用 `generateRandomMetrics` 并验证 bounds。在修复前，`none` 和 `counts` 模式会触发 NPE。

## 总结

修复了 `FileGenerationUtil.generateBounds` 在 `none`/`counts` metrics mode 下因 `generateBound` 返回 null 而导致的 NPE。修复方案是在比较前检查 null 并返回无边界结果，同时将 Comparator 创建延后。新增参数化测试覆盖所有 metrics mode 以防回归。
