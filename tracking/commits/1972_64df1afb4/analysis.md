# 提交 1972：Flink: Backport fix NPE in SketchUtil when numPartitions bigger than length of samples (#12741)

## 提交信息

- **序号**：1972 / 4088
- **哈希**：64df1afb4b09486aca104cbc5bd017d7dcfb480b
- **短哈希**：64df1afb4
- **日期**：2025-04-07 15:21:47 +0200
- **作者**：GuoYu
- **提交说明**：Flink: Backport fix NPE in SketchUtil when numPartitions bigger than length of samples (#12741) / backports #12703
- **PR/Issue**：#12741（backports #12703）

## 总体目的

本提交是 #12703 的反向移植，修复 Flink sink shuffle 模块中 `SketchUtil.rangeBounds` 方法在"分区数（numPartitions）大于样本数量（samples.length）"时抛出 `NullPointerException` 的问题。

`rangeBounds` 方法用于从采样得到的 SortKey 数组中选出若干候选边界（candidates），将数据划分为 `numPartitions` 个范围。原实现预先按 `numCandidates = numPartitions - 1` 分配固定大小的 `SortKey[] candidates` 数组。当 `numPartitions` 大于 `samples.length` 时，由于实际能选出的候选数量受限于样本中的不同值数量，会少于 `numCandidates`，导致返回的数组中存在 `null` 元素，下游使用时触发 NPE。

修复方式是将固定大小数组改为使用动态增长的 `List` 收集候选值，循环结束后再转为数组返回，从而保证返回数组中不含 `null`。

## 如何达成设计目的

设计思路是用动态集合替代定长数组：

1. 将 `SortKey[] candidates = new SortKey[numCandidates]` 改为 `List<SortKey> candidatesList = Lists.newLinkedList()`。
2. 循环中比较上一个候选时，由 `candidates[numChosen - 1]` 改为 `candidatesList.get(candidatesList.size() - 1)`；添加候选时由 `candidates[numChosen] = candidate` 改为 `candidatesList.add(candidate)`。
3. 循环结束后通过 `candidatesList.toArray(new SortKey[0])` 转回数组返回，数组长度等于实际选中的候选数，不再有 `null` 填充。
4. 新增测试 `testRangeBoundsNumPartitionsBiggerThanSortKeyCount`，用 3 个样本、5 个分区的场景验证返回结果不含 null 且为期望的边界值。修改同时应用到 Flink 1.18 和 1.19 两个版本。

## 修改详情

### `flink/v1.18/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/SketchUtil.java` (修改, +7/-4 lines)

**修改目的**：修复 `rangeBounds` 在 numPartitions 大于样本数时返回含 null 数组导致的 NPE。

**工作逻辑**：
- 新增 `java.util.List` 和 `Lists` 的 import。
- 在 `rangeBounds` 中，候选收集容器由 `SortKey[] candidates`（定长 `numCandidates`）改为 `List<SortKey> candidatesList = Lists.newLinkedList()`。
- 去重比较改为取链表最后一个元素 `candidatesList.get(candidatesList.size() - 1)`；选中候选改为 `candidatesList.add(candidate)`。
- 循环结束后 `SortKey[] candidates = candidatesList.toArray(new SortKey[0])` 转换后返回。

### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/sink/shuffle/TestSketchUtil.java` (修改, +11/-0 lines)

**修改目的**：为该边界场景增加回归测试。

**工作逻辑**：新增 `testRangeBoundsNumPartitionsBiggerThanSortKeyCount`，以 `numPartitions=5`、3 个样本（a/b/c）调用 `rangeBounds`，断言结果恰好为 a/b/c 且不含 null（`.doesNotContainNull()`）。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/SketchUtil.java` (修改, +7/-4 lines)

**修改目的**：与 1.18 模块相同的修复。**工作逻辑**：同上。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/sink/shuffle/TestSketchUtil.java` (修改, +11/-0 lines)

**修改目的**：与 1.18 模块相同的测试。**工作逻辑**：同上。

## 总结

本提交修复了 Flink sink shuffle `SketchUtil.rangeBounds` 在分区数大于样本数时返回含 `null` 元素数组从而引发 NPE 的缺陷，方法是将定长候选数组替换为动态链表收集再转换。修复在 Flink 1.18 与 1.19 两个模块同步应用，并新增回归测试覆盖该边界场景。
