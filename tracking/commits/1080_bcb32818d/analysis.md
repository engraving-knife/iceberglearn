# 提交 1080：Drop ParallelIterable's queue low water mark

## 提交信息

- **序号**：1080 / 4088
- **哈希**：bcb32818dab866a81539e04ae807c3a14e0e625c
- **短哈希**：bcb32818d
- **日期**：2024-08-22（Thu Aug 22 00:04:57 2024 +0200）
- **作者**：Piotr Findeisen <piotr.findeisen@gmail.com>
- **提交说明**：Drop ParallelIterable's queue low water mark (#10978)
- **PR/Issue**：#10978

## 总体目的

`ParallelIterable` 是 Iceberg core 中用于并行迭代多个输入源的工具类，广泛用于 manifest 读取、数据文件扫描等场景。它的 `ParallelIterator` 内部维护一个有界队列（`ConcurrentLinkedQueue`，大小受 `maxQueueSize` 限制），多个生产者任务（worker）并行地把各输入 iterable 的元素填入队列，消费者从队列取元素。

在更早的提交 `7831a8dfc`（PR #10691，"Limit ParallelIterable memory consumption by yielding in tasks"）中，为了控制内存消耗，引入了"queue low water mark"（低水位线 = `maxQueueSize / 2`）机制：在 `hasNext()` 方法的快路径中，只有当队列元素数大于低水位线时才直接返回 true，否则落入 `checkTasks()` 循环——这意味着当队列消耗到一半时就会提前触发新任务提交/continuation 重启，保持队列不被清空。

这个低水位线虽然有助于减少消费者等待，但带来了一个意外的副作用：**Trino Iceberg connector 在执行 LIMIT 查询时，manifest 读取量显著增加**。原因是 LIMIT 查询只需消费少量行就足够，但低水位线导致队列还没空就提前预取了更多 manifest（生产者比消费者快时，队列半空就触发新一轮任务提交），这些预取的 manifest 对 LIMIT 结果无用，纯属 I/O 浪费。

本提交的目的：回退低水位线机制，恢复为"队列完全为空时才提交新任务"的原始行为，消除 LIMIT 查询中多余的 manifest 读取，降低 I/O 开销。

## 如何达成设计目的

直接在 `ParallelIterator.hasNext()` 的快路径判断中，把 `queue.size() > queueLowWaterMark`（低水位线判断）改为 `!queue.isEmpty()`（队列非空判断），并删除 `queueLowWaterMark` 局部变量。同时更新注释，移除关于"too late (when queue is already emptied)"的描述（因为新行为就是等队列空了才触发，注释需要与新语义一致）。

这样改动后：
- 队列只要还有任何元素，`hasNext()` 就直接返回 true，不调用 `checkTasks()`，不提交新任务；
- 只有队列被消费者取空后，才进入 `checkTasks()` 循环提交新任务/重启 continuation；
- 对 LIMIT 场景：消费者取够行数后停止调用 `hasNext()`，此时即使队列半空也不会触发后续 manifest 预取，避免了多余 I/O。

## 修改详情

### `core/src/main/java/org/apache/iceberg/util/ParallelIterable.java`

**修改目的**：移除 `hasNext()` 快路径中的低水位线判断，改为队列非空即返回，避免提前预取导致 LIMIT 查询多读 manifest。

**工作逻辑**：

`hasNext()` 方法的开头有一段快路径——在调用 `checkTasks()` 之前先检查队列是否有足够元素可以直接返回。改动如下：

```diff
       // If the consumer is processing records more slowly than the producers, the producers will
       // eventually fill the queue and yield, returning continuations. Continuations and new tasks
       // are started by checkTasks(). The check here prevents us from restarting continuations or
-      // starting new tasks too early (when queue is almost full) or too late (when queue is already
-      // emptied). Restarting too early would lead to tasks yielding very quickly (CPU waste on
-      // scheduling). Restarting too late would mean the consumer may need to wait for the tasks
-      // to produce new items. A consumer slower than producers shouldn't need to wait.
-      int queueLowWaterMark = maxQueueSize / 2;
-      if (queue.size() > queueLowWaterMark) {
+      // starting new tasks before the queue is emptied. Restarting too early would lead to tasks
+      // yielding very quickly (CPU waste on scheduling).
+      if (!queue.isEmpty()) {
         return true;
       }
```

- 旧逻辑：`queueLowWaterMark = maxQueueSize / 2`，仅当队列元素数超过半满时才快路径返回；队列消耗到一半时就落入 `checkTasks()` 提前预取；
- 新逻辑：`!queue.isEmpty()`，只要队列非空就快路径返回；只有队列完全空了才触发 `checkTasks()` 提交新任务；
- 注释同步更新：删除了"too late (when queue is already emptied)"和"A consumer slower than producers shouldn't need to wait"的描述，因为新行为接受"队列空了才预取"这一折中。

后续 `hasNext()` 的 `checkTasks()` 循环逻辑不变：队列空时循环调用 `checkTasks()` 提交/检查任务，直到队列有元素或所有任务完成。

## 小结

- **成效**：回退了 PR #10691 引入的低水位线预取策略，恢复了"队列空才预取"的原始行为，直接解决了 Trino Iceberg connector LIMIT 查询 manifest 读取量增加的回归问题。这是一个用"稍慢的预取"换"更少 I/O"的权衡，对 LIMIT 场景明显划算。
- **影响范围**：仅 `ParallelIterable` 一个文件 6 行改动（3 增 6 删）。`ParallelIterable` 是 core 模块的基础工具类，所有使用它的路径（manifest 读取、数据扫描等）的预取时机都会变化——消费慢于生产时，队列会先被取空再触发新任务，理论上消费者等待时间可能微增，但实践中生产者通常足够快，影响可忽略。
- **回迁到 1.4.x 的注意事项**：建议回迁，特别是如果 1.4.x 也合入了 PR #10691（低水位线引入）。回迁前需确认 1.4.x 的 `ParallelIterable` 是否已有 `queueLowWaterMark` 逻辑；如果有，本提交可直接回退；如果 1.4.x 从未合入 #10691，则 1.4.x 本来就是"队列空才预取"的旧行为，无需回迁本提交。另外需注意：本提交与紧随其后的提交 1081（"Check for minimal queue size in ParallelIterable"）是配套的——1081 在本提交的基础上又加了一个"队列达最小值才预取"的细化检查，回迁时应两个一起评估。
