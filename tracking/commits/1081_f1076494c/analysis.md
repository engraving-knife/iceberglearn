# 提交 1081：Check for minimal queue size in ParallelIterable

## 提交信息

- **序号**：1081 / 4088
- **哈希**：f1076494c807447484b5a154b242611e98e3b582
- **短哈希**：f1076494c
- **日期**：2024-08-22（Thu Aug 22 00:06:05 2024 +0200）
- **作者**：Piotr Findeisen <piotr.findeisen@gmail.com>
- **提交说明**：Check for minimal queue size in ParallelIterable (#10977)
- **PR/Issue**：#10977

## 总体目的

`ParallelIterable` 的 `ParallelIterator` 构造函数接收一个 `maxQueueSize` 参数，用于限制内部队列的最大容量（控制内存消耗）。该参数会传给每个 `Task`，`Task` 在生产元素时会根据 `queue.size() >= maxQueueSize` 决定是否让步（yield）。

如果调用方传入 `maxQueueSize <= 0`，会导致 `Task` 的让步判断逻辑失效（队列大小永远 >= 0，任务可能立即让步不生产任何元素，或者行为未定义），最终表现为迭代器卡死、无元素产出等难以排查的问题。

本提交的目的：在 `ParallelIterator` 构造函数中增加 `Preconditions.checkArgument(maxQueueSize > 0, "Max queue size must be greater than 0")` 校验，让非法参数在构造阶段就 fail-fast 抛出明确异常，而不是让后续迭代行为出现隐蔽的错误。同时新增一个 `maxQueueSize=1` 边界场景的测试，验证最小合法值下迭代器仍能正确工作。

## 如何达成设计目的

1. **参数校验**：在 `ParallelIterator` 构造函数中，紧接 `maxQueueSize` 赋值之前，加一行 `Preconditions.checkArgument(maxQueueSize > 0, "Max queue size must be greater than 0")`。这样任何 `<= 0` 的传入都会立即抛 `IllegalArgumentException`，错误信息明确指向问题原因。

2. **边界测试**：新增 `queueSizeOne` 测试方法，用 `maxQueueSize=1` 构造 `ParallelIterable`（3 个各 100 个元素的 iterable），验证：
   - 所有 300 个元素（3×100，每个值出现 3 次）都能被正确消费；
   - 消费过程中队列大小始终 `<= 1 + iterables.size()`（即 `<= 4`），确认在最小队列容量下并行任务不会让队列无限膨胀。

## 修改详情

### `core/src/main/java/org/apache/iceberg/util/ParallelIterable.java`

**修改目的**：在 `ParallelIterator` 构造函数中对 `maxQueueSize` 做合法性校验，确保大于 0。

**工作逻辑**：

在 `ParallelIterator` 的私有构造函数中，`this.maxQueueSize = maxQueueSize` 赋值之前新增一行校验：
```diff
       this.workerPool = workerPool;
+      Preconditions.checkArgument(maxQueueSize > 0, "Max queue size must be greater than 0");
       this.maxQueueSize = maxQueueSize;
```

`ParallelIterator` 的构造由 `ParallelIterable.iterator()` 触发，`maxQueueSize` 来自 `ParallelIterable` 的 `approximateMaxQueueSize` 字段（默认 `DEFAULT_MAX_QUEUE_SIZE`，也可通过 `ParallelIterable(iterables, workerPool, approximateMaxQueueSize)` 构造函数传入）。校验放在 `ParallelIterator` 构造函数而非 `ParallelIterable` 构造函数，是因为 `ParallelIterable` 构造时只是存储参数，真正使用是在 `iterator()` 创建 `ParallelIterator` 时——在消费点校验能更准确地定位问题。

### `core/src/test/java/org/apache/iceberg/util/TestParallelIterable.java`

**修改目的**：新增 `maxQueueSize=1` 边界场景测试，验证最小合法队列容量下迭代器正确性。

**工作逻辑**：

新增 `queueSizeOne` 测试方法：
- 构造 3 个各 100 元素（0~99）的 iterable；
- 期望结果：0~99 每个值出现 3 次，共 300 个元素（`ImmutableMultiset`）；
- 用 `new ParallelIterable<>(iterables, executor, 1)` 构造，`maxQueueSize=1`；
- 消费循环中断言 `iterator.queueSize() <= 1 + iterables.size()`（即 `<= 4`）：
  - 队列本身容量限制为 1，但由于 `ConcurrentLinkedQueue` 是无界的，且多个 `Task` 并行生产时存在竞态（多个任务同时检查 `queue.size() < maxQueueSize` 通过后再 add），队列可能短暂超过 1；
  - `1 + iterables.size()`（= 4）是一个宽松上界，包容并行竞态带来的暂时性超出；
- 最后断言消费到的 multiset 与期望一致，`iterator.close()` + `executor.shutdownNow()` 清理资源。

## 小结

- **成效**：为 `ParallelIterable` 增加了 `maxQueueSize > 0` 的 fail-fast 校验，避免非法参数导致后续迭代行为异常难排查；补充了最小队列容量（`maxQueueSize=1`）的边界测试，确认极限场景下功能正确。
- **影响范围**：1 行生产代码（参数校验）+ 35 行测试代码。无行为变化（之前传 0 或负数是未定义行为，现在改为明确抛异常）。`ParallelIterable` 是 core 工具类，所有调用方在传入非法 `maxQueueSize` 时会立即收到清晰错误。
- **回迁到 1.4.x 的注意事项**：可安全回迁，纯防御性增强。回迁前需确认：1）1.4.x 的 `ParallelIterable` 是否已有 `maxQueueSize` 参数和 `ParallelIterator` 构造函数（如果 1.4.x 还没有 PR #10691 引入的 maxQueueSize 机制，则本提交不适用）；2）测试中用到的 `iterator.queueSize()` 方法是否在 1.4.x 上可用（该方法应来自与提交 1078 相关的 `ParallelIterator` 重构）；3）如果 1.4.x 上 `ParallelIterable` 结构差异较大，校验位置可能需要调整，但核心思路（在 maxQueueSize 被使用前校验 > 0）通用。
