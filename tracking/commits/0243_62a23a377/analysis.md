# 提交 0243：Core: Fix null partitions in PartitionSet (#9248)

## 提交信息

- **序号**：0243 / 4088
- **哈希**：62a23a37758792b31e2590bd7d1aa46da1074512
- **短哈希**：62a23a377
- **日期**：2023-12-08 12:52:29 -0800
- **作者**：Anton Okolnychyi
- **提交说明**：Core: Fix null partitions in PartitionSet (#9248)
- **PR/Issue**：#9248

## 总体目的

`PartitionSet` 是 Iceberg core 中用于按 `(specId, StructLike)` 跟踪分区集合的工具类（继承 `AbstractSet<Pair<Integer, StructLike>>`），被广泛用于扫描规划、写入去重、提交冲突检测等场景。它把分区按 spec id 分桶，每个桶内用一个 `StructLikeSet` 存放分区值。

问题在于：对于 unpartitioned（无分区）spec，分区值是 `null`（或等价的空 `Row.of()`）。`PartitionSet` 的 `add(int, StructLike)` 方法本身能正确接受 null，因为 `StructLikeSet` 内部可以容纳 null。但是 `contains(Object o)` 和 `remove(Object o)` 这两个 `Set` 接口的标准方法在类型判断时使用了 `second instanceof StructLike`，而 `null instanceof StructLike` 在 Java 中恒为 `false`，导致当 `Pair` 的 second 为 null 时直接走到 `return false`，即"集合里明明有这个 null 分区，contains 却返回 false，remove 也删不掉"。

这会破坏所有依赖 `Set.contains` / `Set.remove` 的代码路径——例如 `containsAll`、`retainAll`、`removeAll`、集合相等性比较、以及通过 `keySet()` 复用 `PartitionSet` 的场景（如 `PartitionMap.keySet()` 返回的 `PartitionSet` 视图在做 equals 比较时会漏掉 null 分区）。本提交修正这个缺陷，使 unpartitioned 表的 null 分区也能被正确地包含、移除和比较。

## 如何达成设计目的

整体设计是把 `contains` 和 `remove` 中的类型守卫从 `second instanceof StructLike` 放宽为 `(second == null || second instanceof StructLike)`，让 null 分区也能通过类型判断进入 `contains(int, StructLike)` / `remove(int, StructLike)` 的内部实现（这两个方法内部委托给 `StructLikeSet.contains/remove`，本身已能处理 null）。同时在 `TestPartitionMap` 中补充 null 分区的等价性测试，并新增 `TestPartitionSet` 专门覆盖 contains / add / remove 对 null 分区的支持。

## 修改详情

### `core/src/main/java/org/apache/iceberg/util/PartitionSet.java`

**修改目的**：让 `contains(Object)` 和 `remove(Object)` 接受 null 分区。

**工作逻辑**：

`PartitionSet` 内部用 `Map<Integer, Set<StructLike>> partitionSetById` 分桶，每个桶是 `StructLikeSet`（能容纳 null）。`add(int, StructLike)` 直接 `partitionSet.add(struct)`，因此 null 分区可以正常加入。问题只在 `Set` 接口的 `contains(Object)` / `remove(Object)` 入口：

```java
// contains 修复前
if (first instanceof Integer && second instanceof StructLike) {
  return contains((Integer) first, (StructLike) second);
}
// 修复后
if (first instanceof Integer && (second == null || second instanceof StructLike)) {
  return contains((Integer) first, (StructLike) second);
}
```

`remove(Object)` 做完全相同的修改。修改后，当 `Pair.of(unpartitionedSpecId, null)` 传入 `contains` / `remove` 时，`second == null` 短路为 true，进入 `contains(specId, null)` / `remove(specId, null)`，进而委托给 `StructLikeSet`，与 `add` 的行为对齐。`add(Pair)` 路径不受影响，因为它只校验 `first != null`，对 `second` 不做类型判断。

值得注意的是 `contains(int specId, StructLike struct)` 与 `remove(int specId, StructLike struct)` 的内部实现没有改动——它们本就通过 `partitionSet.contains(struct)` / `partitionSet.remove(struct)` 处理，而 `StructLikeSet` 内部能正确处理 null。所以这次修复的边界非常清晰：仅修正两个 `Object` 入口方法的类型守卫。

### `core/src/test/java/org/apache/iceberg/util/TestPartitionMap.java`

**修改目的**：补充 `PartitionMap` 在含 null 分区时的相等性测试。

**工作逻辑**：

在 `map1` / `map2` 各自放入两条 `BY_DATA_SPEC` 分区（`Row.of("aaa")`、`Row.of("bbb")`）之后，新增一行 `map1.put(UNPARTITIONED_SPEC.specId(), null, "v3")` 与对应的 `map2.put(UNPARTITIONED_SPEC.specId(), null, "v3")`。随后断言 `map1.keySet()` 与 `map2.keySet()` 相等、`entrySet` 相等。`keySet()` 返回的正是 `PartitionSet` 视图，这个断言在修复前会失败（因为 `PartitionSet.contains` 不认 null），修复后通过。两个 map 一个用 `Row`、一个用 `CustomRow`，也顺带验证了不同 `StructLike` 实现下 null 分区的等价性。

### `core/src/test/java/org/apache/iceberg/util/TestPartitionSet.java`（新文件）

**修改目的**：为 `PartitionSet` 提供专门的单元测试，重点覆盖 null 分区。

**工作逻辑**：

- 定义了三种 spec：`UNPARTITIONED_SPEC`（specId=0）、`BY_DATA_SPEC`（specId=1，identity(data)）、`BY_DATA_CATEGORY_BUCKET_SPEC`（specId=3，identity(data) + bucket(category, 8)），构造 `SPECS` map 后 `PartitionSet.create(SPECS)`。
- `testGet`：向集合中加入 4 个分区——`(1, Row.of("a"))`、`(0, null)`、`(0, Row.of())`、`(3, CustomRow.of("a", 1))`，断言 `size() == 4`。然后用与加入时不同的 `StructLike` 实现（`Row` ↔ `CustomRow`）调用 `contains`，验证等价性：`contains(0, null)` 为 true、`contains(0, CustomRow.of())` 为 true（空 Row 与 null 在 unpartitioned spec 下视为相等）、`contains(1, CustomRow.of("a"))` 为 true、`contains(3, Row.of("a", 1))` 为 true。这覆盖了修复的核心场景：null 分区能被 contains 命中。
- `testRemove`：加入同样 4 个分区后，依次 `remove` 并断言每次返回 true，最后 `isEmpty()` 为 true。其中 `remove(0, null)` 与 `remove(0, CustomRow.of())` 直接验证了修复后的 remove 路径能处理 null 分区。

## 小结

本提交修复了 `PartitionSet.contains` / `remove` 因 `instanceof StructLike` 拒绝 null 导致的 unpartitioned 分区无法被包含、移除或比较的缺陷，使 `PartitionSet` 在含 null 分区场景下行为自洽，消除了扫描规划与提交路径上的潜在正确性风险。
