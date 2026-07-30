# 提交 0964：Core: Limit ParallelIterable memory consumption by yielding in tasks (#10691)

## 提交信息

- **序号**：0964 / 4088
- **哈希**：7831a8dfc3a2de546ca069f4fc1e7afd03777554
- **短哈希**：7831a8dfc
- **日期**：2024-07-22 13:08:09 -0700
- **作者**：Piotr Findeisen
- **提交说明**：Core: Limit ParallelIterable memory consumption by yielding in tasks (#10691)
- **PR/Issue**：#10691

## 总体目的

`ParallelIterable` 是 Iceberg core 模块用于并行迭代多个输入 iterable 的工具类，广泛应用于文件规划（file planning）等场景。它通过线程池并发运行多个任务，每个任务负责把对应 iterable 中的元素全部塞进一个共享的 `ConcurrentLinkedQueue`，消费方从队列中拉取。原实现中提交的任务数为 `2 * WORKER_THREAD_POOL_SIZE`（默认约为 2 倍 CPU 核数），且每个任务会一次性把自己的 iterable 全部消费完后再退出，没有任何反压机制。

问题在于：当某个输入 iterable 体积较大、消费方又消费得较慢时，生产任务会把所有产出都堆到无界队列 `queue` 里，造成无界内存增长，极端情况下会触发 OOM。由于这是文件规划阶段使用的工具，一个表的 data/delete 文件可能非常多，单条 `DataFile` 序列化后约 500 字节，大量文件同时入队很容易耗尽堆内存。本提交的目标就是给 `ParallelIterable` 的内部队列加上容量上限，超出上限时让生产任务"yield（让出）"，等消费方赶上之后再恢复执行，从而把内存占用控制在可预期的范围内。

## 如何达成设计目的

核心思路是把原来"一个 Runnable 跑完整个 iterable"改造成"可中断、可恢复的 Task"：

1. 引入 `Task<T>` 类，实现 `Supplier<Optional<Task<T>>>` 与 `Closeable`。`Task.get()` 不会一次性消费整个 iterable，而是在每次往队列里加入元素之前检查 `queue.size() >= approximateMaxQueueSize`，达到上限就返回 `Optional.of(this)`，即"自己作为后续要继续执行的任务（continuation）"；如果正常消费完所有元素，则返回 `Optional.empty()` 表示无需再调度。
2. 任务的提交改用 `CompletableFuture.supplyAsync`，返回类型由 `Future<?>` 改为 `CompletableFuture<Optional<Task<T>>>`，方便在任务完成时拿到 continuation。
3. `ParallelIterator` 维护一个 `yieldedTasks` 双端队列：当某个任务返回非空 continuation 时，`checkTasks()` 会把它加入 `yieldedTasks`；后续 `submitNextTask()` 优先从 `yieldedTasks` 取出 continuation 重新提交（优先恢复被打断的任务），其次才从原始 `tasks` 中取新任务。
4. 消费侧 `hasNext()` 由原来的"队列空才尝试调度"改为"队列大小低于 `maxQueueSize / 2` 时才尝试调度"，避免任务刚被恢复就立刻又 yield，造成调度抖动；同时也避免队列已接近满时还启动新任务。
5. `closed` 由 `volatile boolean` 改为 `AtomicBoolean`，便于 `Task` 引用并安全检查关闭状态；`close()` 流程重写以正确关闭所有 continuation 任务、清理队列。
6. 新增构造函数 `ParallelIterable(iterables, workerPool, approximateMaxQueueSize)`，默认值 `DEFAULT_MAX_QUEUE_SIZE = 30_000`（按每条 ~500 字节估算约 14.3 MB）。

## 修改详情

### `core/src/main/java/org/apache/iceberg/util/ParallelIterable.java`

**修改目的**：为 `ParallelIterable` 增加队列容量上限与任务让出/恢复机制，限制内存消耗。

**工作逻辑**：

- 新增 `DEFAULT_MAX_QUEUE_SIZE = 30_000` 常量与字段 `approximateMaxQueueSize`，并添加 SLF4J `LOG`。原有构造函数委托给新构造函数，新构造函数对 `iterables`、`workerPool` 做 `Preconditions.checkNotNull` 校验。
- `iterator()` 把 `approximateMaxQueueSize` 传给 `ParallelIterator`。
- `ParallelIterator` 内部：
  - `tasks` 由 `Iterator<Runnable>` 改为 `Iterator<Task<T>>`，构造时通过 `Iterables.transform` 把每个 iterable 包成 `Task`。
  - 新增 `Deque<Task<T>> yieldedTasks` 保存被让出的 continuation。
  - `taskFutures` 类型由 `Future<?>[]` 改为 `CompletableFuture<Optional<Task<T>>>[]`。
  - `closed` 改为 `AtomicBoolean`。
  - `close()` 用 `Closer` 收集所有 `yieldedTasks` 中的任务统一关闭；遍历 `taskFutures` 调用 `cancel(true)`，并通过 `thenAccept` 把 continuation（如果任务正好返回了非空结果）也关闭；最后清理 `queue`，异常包装为 `UncheckedIOException`。
  - `checkTasks()` 加 `synchronized` 与 `Preconditions.checkState(!closed.get(), ...)`；当某个 future 完成时，调用 `taskFutures[i].get()` 拿到 `Optional<Task<T>>`，若非空则 `yieldedTasks.addLast(continuation)`；返回条件由 `!closed && (tasks.hasNext() || hasRunningTask)` 改为基于 `closed.get()`。
  - `submitNextTask()` 改为优先从 `yieldedTasks` 取出 continuation 用 `CompletableFuture.supplyAsync(yieldedTasks.removeFirst(), workerPool)` 提交；否则若有新 task 则提交；否则返回 `null`。
  - `hasNext()`：新增低水位线 `queueLowWaterMark = maxQueueSize / 2`，当 `queue.size() > queueLowWaterMark` 时直接返回 `true`，不再每次都触发 `checkTasks`；其余流程不变。原注释被重写以解释新的反压策略。
- 新增内部类 `Task<T> implements Supplier<Optional<Task<T>>>, Closeable`：
  - 字段：`input`、`queue`、`closed`、`approximateMaxQueueSize`、`iterator`（懒初始化）。
  - `get()`：首次调用时初始化 `iterator = input.iterator()`，循环 `while (iterator.hasNext())`：若 `queue.size() >= approximateMaxQueueSize` 立即 `return Optional.of(this)` 让出；否则取出下一个元素，若 `closed.get()` 则 break，否则加入队列。任何异常会先尝试 `close()`（自抑制除外），再抛出。正常结束时调用 `close()` 并返回 `Optional.empty()`。
  - `close()`：将 `iterator` 置 null，若 `input instanceof Closeable` 则关闭 input。

### `core/src/test/java/org/apache/iceberg/util/TestParallelIterable.java`

**修改目的**：新增针对队列上限行为的回归测试。

**工作逻辑**：新增 `limitQueueSize` 测试，构造 3 个各 100 个元素的 iterable，`maxQueueSize` 设为 20，使用 `Executors.newCachedThreadPool`。通过反射拿到 `ParallelIterator` 内部的 `queue` 字段，在迭代过程中断言 `queue.size() <= maxQueueSize + iterables.size()`（容许小幅度超额，因为多个任务并发入队），同时用 `Multiset` 收集消费到的值，最后断言与期望的多重集合（每个 0..99 出现 3 次）相等。这样既验证了内存上限被遵守，也验证了 yield/resume 不会丢失元素、不会重复元素。

## 小结

- **成效**：`ParallelIterable` 在面对大 iterable + 慢消费方时不再无界堆积内存，新增的 `Task` 让出/恢复机制保证队列长度受限于 `approximateMaxQueueSize`；默认上限 30_000 约 14 MB，兼顾内存安全与吞吐；新增的 `limitQueueSize` 测试覆盖了该反压行为。
- **影响范围**：仅 core 模块的两个文件：`ParallelIterable.java`（主类，重写约 200 行）和 `TestParallelIterable.java`（新增测试）；外部调用方默认走旧构造函数，行为对慢消费方是改善，对快消费方几乎无影响。
- **回迁到 1.4.x 的注意事项**：本提交修复的是潜在的 OOM 风险，**适合回迁到 1.4.x**，尤其适合那些会扫描大量文件的用户场景。但需注意：1.4.x 上若有下游代码自定义继承了 `ParallelIterator` 或依赖 `taskFutures` 的具体类型（`Future<?>`），改造为 `CompletableFuture<Optional<Task<T>>>` 会破坏二进制兼容性；`checkTasks` / `hasNext` 加 `synchronized` 可能影响并发吞吐，需在分支上重新跑并发压测；`DEFAULT_MAX_QUEUE_SIZE = 30_000` 是否合适要结合 1.4.x 上的实际工作负载评估，必要时可显式传入更大的值。建议回迁时连带把测试一并搬过去以验证反压逻辑。
