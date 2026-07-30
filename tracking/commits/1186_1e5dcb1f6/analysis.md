# 提交 1186：Core: Support merging in PositionDeleteIndex (#11208)

## 提交信息

- **序号**：1186 / 4088
- **哈希**：1e5dcb1f6b01128032a704258e1730c9936c9a07
- **短哈希**：1e5dcb1f6
- **日期**：2024-09-25（Wed Sep 25 16:22:42 2024 -0700）
- **作者**：Anton Okolnychyi <aokolnychyi@apache.org>
- **提交说明**：Core: Support merging in PositionDeleteIndex (#11208)
- **PR/Issue**：#11208

## 总体目的

`PositionDeleteIndex` 用于记录某个数据文件中被位置删除的行号集合。在 Iceberg 的删除应用流程中，经常需要把多个 `PositionDeleteIndex`（对应同一数据文件的多个 position delete 文件）合并成一个统一的索引，以便在过滤行时一次性判断。

此前合并逻辑位于 `PositionDeleteIndexUtil.merge(Iterable<? extends PositionDeleteIndex>)` 中，但该实现有一个**强约束**：要求所有待合并的索引都必须是 `BitmapPositionDeleteIndex` 实例，否则抛出 `IllegalArgumentException`（"Can merge only bitmap-based indexes"）。这种"只能合并位图实现"的限制阻碍了未来引入其它 `PositionDeleteIndex` 实现（例如基于排序数组、B 树等）的扩展。

本提交的目的是把"合并"能力下沉为 `PositionDeleteIndex` 接口本身的 `merge(PositionDeleteIndex)` 方法，让接口承担合并语义，并通过 `forEach`（在 #11202 中引入）支持异构实现之间的合并。这样：
- `BitmapPositionDeleteIndex` 之间可用最高效的位图 OR 合并；
- 位图与其它实现之间可通过 `forEach` 逐位 `delete` 合并；
- 任何 `PositionDeleteIndex` 都能参与合并，解除"必须位图"的硬约束。

## 如何达成设计目的

1. 在 `PositionDeleteIndex` 接口新增 `default void merge(PositionDeleteIndex that)`，默认实现 `that.forEach(this::delete)`——利用 #11202 引入的 `forEach` 把对方的所有位置逐个加入到本索引。这是通用回退路径，对任意支持 `forEach` 的实现都成立。
2. 在 `BitmapPositionDeleteIndex` 中覆写 `merge`：若对方也是 `BitmapPositionDeleteIndex`，走快速路径（委托给已有的 `merge(BitmapPositionDeleteIndex)`，即 Roaring64Bitmap 的位图 OR）；否则走通用回退 `that.forEach(this::delete)`。这实现了"同类型快速、异构类型兼容"的优化策略。
3. 简化 `PositionDeleteIndexUtil.merge(Iterable)`：原 17 行包含类型检查与 cast 的循环，被替换为单行 `indexes.forEach(result::merge)`。新实现不再做 `instanceof` 检查，所有索引都通过接口的 `merge` 方法处理。
4. 新增两个测试：`testMergeBitmapIndexWithNonEmpty` 验证两个非空位图索引合并后输出升序且并集正确；`testMergeBitmapIndexWithEmpty` 验证与非空索引合并 `PositionDeleteIndex.empty()` 后内容不变（空索引不应影响结果）。

## 修改详情

### `core/src/main/java/org/apache/iceberg/deletes/PositionDeleteIndex.java`

**修改目的**：在接口层声明 `merge` 能力。

**工作逻辑**：在 `delete(long posStart, long posEnd)` 之后新增方法：
```java
/**
 * Adds positions from the other index, modifying this index in place.
 *
 * @param that the other index to merge
 */
default void merge(PositionDeleteIndex that) {
  that.forEach(this::delete);
}
```
关键点：(1) 修改本索引（in place）；(2) 默认实现依赖 `forEach`，因此若 `that` 不支持 `forEach`（非空且未覆写），会抛 `UnsupportedOperationException`——这是合理的，因为无法遍历就无法合并。

### `core/src/main/java/org/apache/iceberg/deletes/BitmapPositionDeleteIndex.java`

**修改目的**：为位图实现提供高效合并路径。

**工作逻辑**：新增覆写：
```java
@Override
public void merge(PositionDeleteIndex that) {
  if (that instanceof BitmapPositionDeleteIndex) {
    merge((BitmapPositionDeleteIndex) that);
  } else {
    that.forEach(this::delete);
  }
}
```
其中 `merge(BitmapPositionDeleteIndex)` 是已有的私有/包级方法（基于 Roaring64Bitmap 的 OR 操作，提交前已存在）。这里做了"同类型走 OR、异构走 forEach 逐位 delete"的分发。

### `core/src/main/java/org/apache/iceberg/deletes/PositionDeleteIndexUtil.java`

**修改目的**：简化合并工具方法，去除类型硬约束。

**工作逻辑**：原实现：
```java
public static PositionDeleteIndex merge(Iterable<? extends PositionDeleteIndex> indexes) {
  BitmapPositionDeleteIndex result = new BitmapPositionDeleteIndex();
  for (PositionDeleteIndex index : indexes) {
    if (index.isNotEmpty()) {
      Preconditions.checkArgument(
          index instanceof BitmapPositionDeleteIndex,
          "Can merge only bitmap-based indexes, got %s",
          index.getClass().getName());
      result.merge((BitmapPositionDeleteIndex) index);
    }
  }
  return result;
}
```
新实现：
```java
public static PositionDeleteIndex merge(Iterable<? extends PositionDeleteIndex> indexes) {
  BitmapPositionDeleteIndex result = new BitmapPositionDeleteIndex();
  indexes.forEach(result::merge);
  return result;
}
```
变化：(1) 移除 `Preconditions` 的 import（不再需要）；(2) 不再检查 `instanceof BitmapPositionDeleteIndex`，统一通过接口 `merge` 处理；(3) 不再显式 `isNotEmpty()` 短路（空索引合并是 no-op，由 `forEach` 默认实现保证）。代码从 17 行缩减到 4 行。

### `core/src/test/java/org/apache/iceberg/deletes/TestBitmapPositionDeleteIndex.java`

**修改目的**：验证合并正确性。

**工作逻辑**：
- `testMergeBitmapIndexWithNonEmpty()`：构造两个 `BitmapPositionDeleteIndex`，分别插入跨容器边界的位置（容器 0/1 与容器 1/2），乱序插入。调用 `index1.merge(index2)`，断言 `index1` 的 `forEach` 输出按升序等于所有位置的并集。这同时验证了：(a) 位图 OR 合并的正确性；(b) 跨容器遍历的升序性。
- `testMergeBitmapIndexWithEmpty()`：构造一个含 4 个跨容器位置的位图索引，调用 `index.merge(PositionDeleteIndex.empty())`，断言输出不变。这验证了空索引合并的 no-op 行为以及接口默认 `forEach` 路径的安全性。

## 小结

- **成效**：`PositionDeleteIndex` 现可在接口层支持 `merge`，解除了 `PositionDeleteIndexUtil` 中"只能合并位图"的硬约束。位图实现间仍走高效 OR 路径，异构实现通过 `forEach` 兼容。`PositionDeleteIndexUtil.merge` 从 17 行简化到 4 行。
- **影响范围**：核心 deletes 包的 3 个 Java 源文件 + 1 个测试文件，新增约 60 行、删除约 13 行。属于接口能力扩展 + 内部实现简化，对外行为完全兼容（合并结果不变，只是放宽了对输入类型的限制）。
- **回迁到 1.4.x 的注意事项**：本提交依赖 #11202（`forEach`），**回迁时必须连同 #11202 一起带回**。回迁价值取决于 1.4.x 是否需要支持异构 `PositionDeleteIndex` 合并——如果 1.4.x 仍只使用位图实现，则合并的实际行为不变，回迁主要是代码清理与未来扩展性收益。建议作为一组（#11202 + #11208）一起回迁，单独带回本提交而缺 #11202 会导致编译失败（`forEach` 不存在）。回迁时还需注意 `PositionDeleteIndexUtil` 的简化是否影响 1.4.x 中其它调用方——本提交移除了 `Preconditions` 检查，意味着以前会抛 `IllegalArgumentException` 的非法输入（非位图索引）现在会静默走 `forEach` 路径或抛 `UnsupportedOperationException`，行为有轻微变化，但语义上更合理。
