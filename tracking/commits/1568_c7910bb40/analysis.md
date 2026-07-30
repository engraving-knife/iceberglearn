# 提交 1568：Core: Fix possible deadlock in ParallelIterable (#11781)

## 提交信息

- **序号**：1568
- **哈希**：c7910bb401f7f7fd09010bede0d80f5d2164afd5
- **短哈希**：c7910bb40
- **日期**：2025-01-11（Sat Jan 11 17:13:18 2025 +0100）
- **作者**：Karol Sobczak <napewnotrafi@gmail.com>
- **提交说明**：Core: Fix possible deadlock in ParallelIterable (#11781)
- **PR/Issue**：#11781
- **关联 Issue**：#11768

## 总体目的

`ParallelIterable` 是 Iceberg core 中用于并行迭代多个子迭代器（典型场景：`ManifestGroup#plan` 并行读取多个 manifest）的工具。它通过一个固定大小的 worker pool 并行运行 `Task`，每个 `Task` 把子迭代器的产出投入到有界队列 `queue`（大小由 `approximateMaxQueueSize` 控制）中供消费方拉取。当队列满时，Task 会"yield"——返回 `Optional.of(this)` 把自己挂起，等下次循环再被重新提交继续。

线上观察到在高并发/高负载场景下集群出现死锁，根因是：

- 每个 `Task` 持有 `ManifestReader`，而 `ManifestReader` 持有一个 S3 连接池中的连接。
- 当队列满、Task 被 yield 挂起时，**它仍然持有那条 S3 连接**。
- 如果同时另一个 `ParallelIterable`（或同一 Iterable 的另一个 Task）需要 S3 连接却被 worker pool 阻塞（worker pool 全被同样在等连接的 Task 占住），就形成循环等待：挂起的 Task 等 worker，worker 等 S3 连接，连接被挂起的 Task 持有。

提交消息给出最小复现场景：`S3 connection pool size=1`、`approximateMaxQueueSize=1`、`workerPoolSize=1` 时两个 ParallelIterable 互相阻塞。

本提交通过两处改动打破死锁：**(1) 一旦 Task 被启动就让它跑完，不在中途 yield（避免持有连接的 Task 被挂起）；(2) 队列满时不再向 worker 提交新 Task（避免堆积无谓的等待）。** 牺牲是队列大小可能短暂超过 `approximateMaxQueueSize`（仍是近似上界，非严格），但仍是有限的。

## 如何达成设计目的

### 修改详情

#### `core/src/main/java/org/apache/iceberg/util/ParallelIterable.java`

**修改目的**：消除"持有连接的 Task 被 yield 挂起"这一死锁根源，并避免队列已满时仍提交新 Task。

**关键变更 1：把队列满检查从 `iterator.hasNext()` 循环内提前到方法入口。**

原 `Task.get()`：
```java
if (iterator == null) {
  iterator = input.iterator();
}
while (iterator.hasNext()) {
  if (queue.size() >= approximateMaxQueueSize) {
    return Optional.of(this);   // ← yield 时 iterator 已实例化，可能正持有连接
  }
  T next = iterator.next();
  ...
}
```

新 `Task.get()`：
```java
if (queue.size() >= approximateMaxQueueSize) {
  // Yield when queue is over the size limit. ...
  // Tasks might hold references (via iterator) to constrained resources
  // (e.g. pooled connections). Hence, tasks should yield only when
  // iterator is not instantiated. Otherwise, there could be
  // a deadlock when yielded tasks are waiting to be executed while
  // currently executed tasks are waiting for the resources that are held
  // by the yielded tasks.
  return Optional.of(this);
}

if (iterator == null) {
  iterator = input.iterator();
}

while (iterator.hasNext()) {
  T next = iterator.next();
  ...
}
```

这样 Task 只会在 `iterator` 还未实例化（即尚未持有任何底层资源）时才 yield；一旦开始迭代就跑到队列满或迭代结束，不会在中途挂起。注释明确解释了为何这样改。

**关键变更 2：在 `ParallelIterator` 中新增字段 `private final int maxQueueSize;`，并在构造时记录。**（用于提交新 Task 前的判断；原本只持有 `approximateMaxQueueSize`，但该字段在 `Task` 内部使用，ParallelIterator 层需要单独保存。）

**关键变更 3：处理完一个 Task future 后，仅在队列未满时才提交下一个 Task。**

原代码（`taskFutures[i]` 处理循环内）：
```java
taskFutures[i] = submitNextTask();
```

新代码：
```java
// submit a new task if there is space in the queue
if (queue.size() < maxQueueSize) {
  taskFutures[i] = submitNextTask();
}
```

并配合 `taskFutures[i] = null;`（在拿到 continuation 后立即清空槽位），避免误判"还有 future 在跑"。这样当队列已满时不再向 worker pool 提交新 Task，避免无谓的 worker 占用与连接争抢。

**关键变更 4（提交消息中 "Do not submit a task when there is no space in the queue"）。** 与变更 3 一致，把"队列空了就提交"的简单策略改为"队列有空位才提交"。

#### `core/src/test/java/org/apache/iceberg/util/TestParallelIterable.java`

**修改目的**：增加死锁回归测试，并放宽原有队列大小断言。

**关键变更**：
- `limitQueueSize`：把 executor 从 `newCachedThreadPool` 改为 `newSingleThreadExecutor`（更接近死锁触发条件），并把队列大小断言从 `isLessThanOrEqualTo(maxQueueSize + iterables.size())` 放宽到 `isLessThanOrEqualTo(100)`（因修复后队列可能短暂超限，但仍是有限上界）。
- 删除原 `queueSizeOne` 测试（其严格断言不再成立）。
- 新增 `@Timeout(10) noDeadlock()` 测试：用单线程 `ExecutorService` + 一个 `Semaphore(1)` 模拟"受约束的资源池（如 S3 连接池 size=1）"。构造两个 `ParallelIterable`（各含一个 iterable，迭代时 acquire/release semaphore），各自调用 `iterator().next()` 取一个元素。在修复前，这会在单线程 + 信号量=1 的组合下死锁；修复后可在 10 秒内完成。`testIterable` 辅助方法构造在 `iterator()` 时 `open.run()`（acquire）、`close()` 时 `close.run()`（release）的 `CloseableIterable`，模拟"迭代器持有受限资源"的真实场景。
- 新增 `RunnableWithException` 函数式接口以简化异常传播。

## 小结

- **成效**：消除了 `ParallelIterable` 在"Task 持有受限资源（连接）+ 队列满 yield + worker pool 阻塞"组合下的死锁。修复后 Task 一旦开始就跑完，不会带着连接被挂起；队列满时不再提交新 Task。代价是队列大小可能短暂超过 `approximateMaxQueueSize`，但仍有限。这对 S3 等连接池受限的存储上的高并发 manifest 读取稳定性意义重大。
- **影响范围**：仅 `core` 模块的 `ParallelIterable.java`（一个文件、几处局部改动）与测试。`ParallelIterable` 是 `ManifestGroup` 等读取规划路径的核心，改动影响所有引擎的并发读取。
- **回迁到 1.4.x 的注意事项**：这是一个稳定性 bug 修复，影响高并发场景下的死锁。1.4.x 若包含相同的 `ParallelIterable` 实现，强烈建议回迁以避免线上死锁。回迁范围小（单文件局部逻辑调整 + 测试），冲突风险低。**强烈建议回迁**。
