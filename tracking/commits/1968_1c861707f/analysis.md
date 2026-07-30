# 提交 1968：Flink: Fix NPE in SketchUtil when numPartitions bigger than length of samples (#12703)

## 提交信息

- **序号**：1968 / 4088
- **哈希**：1c861707ff501307ca0813bb84c4fe36ed7c5f00
- **短哈希**：1c861707f
- **日期**：2025-04-07 12:04:08 +0200
- **作者**：GuoYu
- **提交说明**：Flink: Fix NPE in SketchUtil when numPartitions bigger than length of samples (#12703)
- **PR/Issue**：#12703

## 总体目的

在 Flink Iceberg Sink 的 RANGE 分布模式下，协调器（coordinator）会基于采样的键（samples）计算范围分区边界（range bounds），用于 range 分区器。`SketchUtil.rangeBounds` 方法会从排序后的 samples 中按步长 `step = ceil(samples.length / numPartitions)` 选出 `numPartitions - 1` 个候选边界。

问题在于：当 `numPartitions` 大于 `samples.length` 时，原实现用固定大小数组 `SortKey[] candidates = new SortKey[numCandidates]`（`numCandidates = numPartitions - 1`）预分配，但 while 循环受 `position < samples.length` 限制，实际选出的候选数 `numChosen` 可能小于 `numCandidates`。这导致数组末尾存在未填充的 `null` 元素，返回的 candidates 数组含有 null，后续使用时抛出 `NullPointerException`。

本提交修复该 NPE：改用 `List`（LinkedList）动态收集候选，循环结束后再把 List 转为数组返回，保证返回数组只包含实际选中的候选，不含 null。

## 如何达成设计目的

将固定大小数组改为动态集合：

1. 把 `SortKey[] candidates = new SortKey[numCandidates]` 改为 `List<SortKey> candidatesList = Lists.newLinkedList()`。
2. 去重判断 `candidate.equals(candidates[numChosen - 1])` 改为 `candidate.equals(candidatesList.get(candidatesList.size() - 1))`（取列表最后一个元素）。
3. 选中时 `candidates[numChosen] = candidate` 改为 `candidatesList.add(candidate)`。
4. 循环结束后 `SortKey[] candidates = candidatesList.toArray(new SortKey[0])` 转为数组返回。

这样无论 `numChosen` 是否达到 `numCandidates`，返回数组都只含实际选中的元素，消除 null 元素导致的 NPE。

同时新增回归测试 `testRangeBoundsNumPartitionsBiggerThanSortKeyCount`，验证当 `numPartitions=5` 但只有 3 个 sample（a/b/c）时，返回的边界为 `[a, b, c]` 且不含 null。

## 修改详情

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/SketchUtil.java` (修改, +10/-4 lines)

**修改目的**：修复 numPartitions 大于 samples 数量时返回数组含 null 导致的 NPE。

**工作逻辑**：
- 新增 `import java.util.List` 与 `Lists`。
- `rangeBounds` 方法中：
  - `SortKey[] candidates = new SortKey[numCandidates]` → `List<SortKey> candidatesList = Lists.newLinkedList()`。
  - 去重比较 `candidate.equals(candidates[numChosen - 1])` → `candidate.equals(candidatesList.get(candidatesList.size() - 1))`。
  - 赋值 `candidates[numChosen] = candidate` → `candidatesList.add(candidate)`。
  - 循环后新增 `SortKey[] candidates = candidatesList.toArray(new SortKey[0])` 再返回。
- step 与 position 计算逻辑保持不变。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/sink/shuffle/TestSketchUtil.java` (修改, +11/-0 lines)

**修改目的**：新增回归测试覆盖该 NPE 场景。

**工作逻辑**：新增 `testRangeBoundsNumPartitionsBiggerThanSortKeyCount`，调用 `SketchUtil.rangeBounds(5, SORT_ORDER_COMPARTOR, new SortKey[]{a, b, c})`，断言结果 `containsExactly(a, b, c)` 且 `doesNotContainNull()`。

## 总结

本提交修复 `SketchUtil.rangeBounds` 在 `numPartitions` 大于 samples 数量时，因预分配固定大小数组导致返回数组含未填充 null 元素而抛 NPE 的问题。核心改动是用动态 `LinkedList` 收集候选再转为数组，保证返回数组不含 null，并新增回归测试验证。
