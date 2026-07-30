# 提交 3539：Core: Optimize RoaringPositionBitmap.setRange with native range API (#15791)

## 提交信息

- **序号**：3539 / 4088
- **哈希**：44145262d2e4e8b79b1e681f8182af72f43ae66d
- **短哈希**：44145262d
- **日期**：2026-04-15 15:26:29 -0700
- **作者**：Sebastian Baunsgaard
- **提交说明**：Core: Optimize RoaringPositionBitmap.setRange with native range API (#15791)
- **PR/Issue**：#15791

## 总体目的

`RoaringPositionBitmap` 是 Iceberg 中用于跟踪删除位置（position deletes）的位图实现，内部使用 RoaringBitmap 的高 32 位作为 key（分桶），低 32 位作为桶内位图。`setRange(start, end)` 方法用于一次性设置一个连续范围内的所有位置。

此前的实现非常朴素：用 `for` 循环逐个调用 `set(pos)`：
```java
for (long pos = posStartInclusive; pos < posEndExclusive; pos++) {
  set(pos);
}
```
当范围很大（例如几百万个位置）时，逐个 set 性能极差，每次都要做 key 计算、桶查找、位图追加等操作。RoaringBitmap 原生提供了 `add(long from, long to)` 批量区间添加 API，远比逐个 add 高效。

本提交重写 `setRange`，利用 RoaringBitmap 的原生区间 API 进行批量添加，并正确处理跨 key（跨桶）的范围拆分。同时增加参数校验（start 不能大于 end、位置越界校验）和空范围短路。

## 如何达成设计目的

新实现的核心思路：
1. **参数校验**：`posStartInclusive <= posEndExclusive`，否则抛 `IllegalArgumentException`
2. **空范围短路**：若 start == end，直接返回
3. **越界校验**：对 start 和 end-1 调用 `validatePosition`
4. **计算 key 范围**：`startKey = key(start)`，`endKey = key(end-1)`，按需分配桶
5. **分三种情况**：
   - 单 key（startKey == endKey）：直接对该桶调用 `bitmaps[startKey].add(lowStart, lowEnd)`
   - 跨 key：首桶从 `firstLowStart` 到 `2^32`（桶末尾），中间桶全填 `0` 到 `2^32`，末桶从 `0` 到 `lastLowEnd`
   - （单 key 情况已覆盖首末桶相同的情况）

关键点：RoaringBitmap 的 `add(long, long)` 是原生批量区间操作，远快于逐个 add。跨桶时把范围拆分为「首桶尾部 + 中间整桶 + 末桶头部」三段，每段调用一次原生区间 API。

注意低 32 位用 `Integer.toUnsignedLong(pos32Bits(...))` 转为无符号 long，因为 RoaringBitmap 的低 32 位桶使用无符号 int 语义。

## 修改详情

### `core/src/main/java/org/apache/iceberg/deletes/RoaringPositionBitmap.java` (+33/-4 lines)

**修改目的**：用 RoaringBitmap 原生区间 API 重写 `setRange`，并增加校验。

**工作逻辑**：
```java
public void setRange(long posStartInclusive, long posEndExclusive) {
  Preconditions.checkArgument(
      posStartInclusive <= posEndExclusive,
      "Start position must not exceed end position: [%s, %s)",
      posStartInclusive, posEndExclusive);

  if (posStartInclusive == posEndExclusive) {
    return;
  }

  validatePosition(posStartInclusive);
  validatePosition(posEndExclusive - 1);

  int startKey = key(posStartInclusive);
  int endKey = key(posEndExclusive - 1);
  allocateBitmapsIfNeeded(endKey + 1);

  if (startKey == endKey) {
    long lowStart = Integer.toUnsignedLong(pos32Bits(posStartInclusive));
    long lowEnd = Integer.toUnsignedLong(pos32Bits(posEndExclusive - 1)) + 1;
    bitmaps[startKey].add(lowStart, lowEnd);
  } else {
    long firstLowStart = Integer.toUnsignedLong(pos32Bits(posStartInclusive));
    bitmaps[startKey].add(firstLowStart, 1L << 32);

    for (int key = startKey + 1; key < endKey; key++) {
      bitmaps[key].add(0L, 1L << 32);
    }

    long lastLowEnd = Integer.toUnsignedLong(pos32Bits(posEndExclusive - 1)) + 1;
    bitmaps[endKey].add(0L, lastLowEnd);
  }
}
```
- `1L << 32` 表示桶内低 32 位的上限（无符号 int 的上界）
- 末桶的 `lastLowEnd` 是 `pos32Bits(end-1) + 1`（因为 add 的 end 是 exclusive）
- Javadoc 也更新了：说明空范围行为和 `@throws IllegalArgumentException`

### `core/src/test/java/org/apache/iceberg/deletes/TestRoaringPositionBitmap.java` (+117/-2 lines)

**修改目的**：为新实现添加全面测试。

**工作逻辑**：新增多个测试用例：
- `testAddEmptyRange`：start==end 时空操作（重写原有测试，增加更多断言）
- `testSetRangeReversedThrows`：start > end 抛 `IllegalArgumentException`
- `testAddRangeLargeContiguous`：200,000 个连续位置的大范围，验证 cardinality 和边界
- `testAddRangeSpanningThreeKeys`：跨 3 个 key 的范围（从 key 0 末尾到 key 2 开头），验证中间 key 全覆盖
- `testAddRangeSinglePosition`：单位置范围 (42, 43)，与 `set(42)` 行为一致
- `testAddRangeAtKeyBoundary`：恰好一个完整 key 范围 (0, 2^32)，验证 cardinality=2^32 且只分配 1 个桶
- `testAddRangeSameKeyForEachExact`：桶内范围 (1000, 1200)，逐个位置精确验证
- `testAddRangeCrossKeyForEachExact`：跨 key 范围（key 0 末尾到 key 1 开头），逐个位置精确验证
- 在 `testPositionBounds` 中新增 `setRange` 的越界校验测试（-1 起点和 MAX_POSITION+2 终点）

## 总结

本提交将 `RoaringPositionBitmap.setRange` 从逐个 `set` 的 O(n) 慢实现重写为利用 RoaringBitmap 原生 `add(from, to)` 区间 API 的批量实现，正确处理跨桶范围拆分，并增加参数校验（start<=end、位置越界）和空范围短路。对于大范围位置删除场景（如 equality delete 应用后的 position 集合构建）有显著性能提升。配有全面的测试覆盖单桶、跨桶、边界、越界等场景。
