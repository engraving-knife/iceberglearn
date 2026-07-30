# 提交 2045：Spark: Use newArrayListWithExpectedSize in NDVSketchUtil

## 提交信息

- **序号**：2045 / 4088
- **哈希**：346414c0f500ec06f524517d368bdedd4744edf2
- **短哈希**：346414c0f
- **日期**：2025-04-28 08:25:18 +0200
- **作者**：jackylee
- **提交说明**：Spark: Use newArrayListWithExpectedSize in NDVSketchUtil (#12907)
- **PR/Issue**：#12907

## 总体目的

`NDVSketchUtil` 是 Iceberg Spark 模块中用于计算 NDV（Number of Distinct Values，去重值数量）草图的工具类。在 `createNDVSketches` 方法中，需要为每个列创建一个 `Blob` 对象并收集到 List 中。此前使用 `Lists.newArrayList()` 创建 ArrayList，初始容量为默认值（16），当列数量较大时需要多次扩容（每次扩容需要复制数组），造成不必要的性能开销。

本提交将 `Lists.newArrayList()` 替换为 `Lists.newArrayListWithExpectedSize(columns.size())`，预分配与列数量匹配的初始容量，避免扩容开销，优化内存使用和性能。

## 如何达成设计目的

使用 Guava 的 `Lists.newArrayListWithExpectedSize(int)` 方法，该方法会根据预期大小计算合适的初始容量（略大于预期大小以容纳少量额外元素），避免 ArrayList 在添加元素过程中的数组扩容和复制操作。

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/actions/NDVSketchUtil.java` (修改, +2/-1 lines)

**修改目的**：预分配 ArrayList 容量以避免扩容开销。

**工作逻辑**：
在 `createNDVSketches` 方法中，将 `List<Blob> blobs = Lists.newArrayList()` 改为 `List<Blob> blobs = Lists.newArrayListWithExpectedSize(columns.size())`。由于后续循环 `for (int i = 0; i < columns.size(); i++)` 会为每个列添加一个 Blob，预分配 `columns.size()` 大小的容量恰好满足需求，无需扩容。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/actions/NDVSketchUtil.java` (修改, +2/-1 lines)

**修改目的**：同上，对 Spark 3.5 模块做相同优化。

**工作逻辑**：
与 Spark 3.4 完全相同的修改。

## 总结

本提交对 Spark 3.4 和 3.5 模块中 `NDVSketchUtil` 的 ArrayList 创建方式进行微优化，使用 `newArrayListWithExpectedSize` 预分配容量，避免添加元素时的数组扩容开销。改动量小（每模块 2 行），属于性能微优化。
