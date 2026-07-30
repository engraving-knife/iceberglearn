# 提交 0188：Core: Remove synchronization from BitmapPositionDeleteIndex (#9119)

## 提交信息

- **序号**：0188 / 4088
- **哈希**：42614cc8d6527284cb45800b5b37e6a98c466ef6
- **短哈希**：42614cc8d
- **日期**：2023-11-21 08:25:56 +0100
- **作者**：Anton Okolnychyi
- **提交说明**：Core: Remove synchronization from BitmapPositionDeleteIndex (#9119)
- **PR/Issue**：#9119

## 总体目的

本提交移除 [`BitmapPositionDeleteIndex.delete(long position)`](../../../core/src/main/java/org/apache/iceberg/deletes/BitmapPositionDeleteIndex.java) 上的 `synchronized` 关键字。该方法在 0125 提交（PR #8805，"Core: Use ParallelIterable in Deletes::toPositionIndex"）中被加锁——当时为支持多 position delete 文件并行合并引入了 `ParallelIterable`，作者出于防御性考虑给 `delete` 加了 `synchronized`，因为底层 `Roaring64Bitmap.add(long)` 非线程安全。

加锁的初衷是合理的：0125 引入并行后，理论上 `delete` 可能被多个 worker 线程并发调用。但实际并非如此。`ParallelIterable` 采用"多生产者-单消费者"模型：worker 线程只把元素 `queue.add(item)` 放入 `ConcurrentLinkedQueue`，并不直接调用下游消费者；真正的消费（`forEach(positionDeleteIndex::delete)`）发生在调用 `Deletes.toPositionIndex(CloseableIterable<Long>)` 的那个线程上，且 `ParallelIterator.hasNext()`/`next()` 本身已是 `synchronized`，串化了消费端的拉取。因此 `positionDeleteIndex::delete` 始终只被单个线程调用，`synchronized` 是无效的防御，反而带来可测量的开销——position delete 文件可能含数百万条位置，每条都触发一次 monitor enter/exit，在热路径上是纯损耗。

此外，`BitmapPositionDeleteIndex` 的类级契约本就声明"非线程安全，通常在单个读取任务内构建并使用"，且 `delete(long, long)` 区间重载、`isDeleted`、`isEmpty` 都未同步。移除 `delete(long)` 上的 `synchronized` 让该方法与同类其他方法及类契约保持一致，并在不牺牲正确性的前提下恢复该热路径的性能。这是对 0125 防御性加锁的一次回退性优化，体现了"先正确后性能、移除无谓锁"的工程演进。

## 如何达成设计目的

整体设计就是"删一行修饰符"：把 `public synchronized void delete(long position)` 改回 `public void delete(long position)`，方法体 `roaring64Bitmap.add(position)` 不变。其安全性依据是 `ParallelIterable` 的并发模型：生产者只入队、消费者单线程拉取，因此 `delete` 不会被并发调用。无需引入新的线程安全机制（如改用并发安全的位图），因为单线程访问下 `Roaring64Bitmap` 已足够。

## 修改详情

### `core/src/main/java/org/apache/iceberg/deletes/BitmapPositionDeleteIndex.java`

**修改目的**：移除 `delete(long position)` 的 `synchronized`，消除单消费者路径上的无效锁开销。

**工作逻辑**：

修改前（0125 引入）：

```java
@Override
public synchronized void delete(long position) {
  roaring64Bitmap.add(position);
}
```

修改后：

```java
@Override
public void delete(long position) {
  roaring64Bitmap.add(position);
}
```

正确性论证基于调用链分析：

1. `Deletes.toPositionIndex(CloseableIterable<Long> posDeletes)` 通过 `deletes.forEach(positionDeleteIndex::delete)` 消费位置写入位图。
2. 当 `posDeletes` 是 `ParallelIterable` 时（多 delete 文件 + 非 null worker 池），其 `iterator()` 返回 `ParallelIterator`。
3. `ParallelIterator` 内部：worker 线程执行 `for (T item : iterable) { queue.add(item); }`——只入队，不调 `delete`；`hasNext()`/`next()` 均 `synchronized`，由调用 `forEach` 的单线程串行拉取 `queue.poll()` 后再喂给 `positionDeleteIndex::delete`。
4. 因此 `delete` 的调用方始终是 `forEach` 所在线程，单线程访问，`synchronized` 多余。

性能上，position delete 文件单文件可含百万级位置，原 `synchronized` 在每个位置上引入 monitor enter/exit 与可能的锁竞争检测（即便无竞争也有 inflate/deflate 开销），热路径上累积开销显著。移除后该路径恢复到 0125 之前的无锁性能，同时保留 0125 引入的并行 I/O+解码收益。

与同类其他方法的一致性：`delete(long posStart, long posEnd)`（区间写入，调用 `Roaring64Bitmap.add(long, long)`）、`isDeleted(long)`（`roaring64Bitmap.contains`）、`isEmpty()`（`roaring64Bitmap.isEmpty`）本就未同步，因为它们不在并行构建路径上被调用。移除 `delete(long)` 的锁后，整个类的方法同步策略统一为"均不同步，依赖单线程使用契约"，与类 Javadoc 声明一致。

## 小结

通过移除 0125 防御性添加的 `synchronized`，消除 `BitmapPositionDeleteIndex.delete` 在单消费者路径上的无效锁开销，使位图写入热路径恢复无锁性能，同时保持与类内其他方法及"非线程安全"类契约的一致性。
