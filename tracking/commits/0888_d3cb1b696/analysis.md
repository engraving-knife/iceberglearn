# 提交 0888：Core: Fix ParallelIterable memory leak where queue continues to be populated even after iterator close (#9402)

## 提交信息

- **序号**：0888 / 4088
- **哈希**：d3cb1b696a1631a9cca4619f93252cd0a985fbfc
- **短哈希**：d3cb1b696
- **日期**：2024-07-01（Tue Jul 2 05:33:17 2024 +0800）
- **作者**：Helt <heltman@qq.com>
- **提交说明**：Core: Fix ParallelIterable memory leak where queue continues to be populated even after iterator close (#9402)
- **PR/Issue**：#9402

## 总体目的

`ParallelIterable` 是 Iceberg core 模块中用于并行迭代多个子 `Iterable` 的工具类。它通过一个线程池并发地从多个数据源读取数据，将结果放入一个共享的 `ConcurrentLinkedQueue`，消费侧从队列中取数据。这种"生产者-消费者"模式广泛用于 Iceberg 的并行扫描（如 `ParallelIterable` 驱动的 manifest 并发读取）。

该类存在一个内存泄漏 bug：当消费者调用 `CloseableIterator.close()` 提前终止迭代时（例如查询只需前 N 行就提前关闭迭代器），`close()` 方法会设置 `closed = true` 标志、`cancel(true)` 取消后台任务并 `queue.clear()` 清空队列。但问题在于，后台 worker 线程的 `for (T item : iterable) { queue.add(item); }` 循环**不检查 `closed` 标志**，而 `ConcurrentLinkedQueue.add()` 又不响应线程中断。因此 `cancel(true)` 发出的中断信号对正在执行 `queue.add()` 的 worker 无效，worker 会继续遍历其 `iterable` 并持续往队列里塞数据——即使队列刚被 `clear()` 清空，新的数据又源源不断地被加入，导致：

1. **内存泄漏**：被关闭的迭代器对应的队列持续被后台任务填充，数据无法被回收；
2. **CPU 浪费**：后台线程继续无意义地遍历与入队；
3. **资源未释放**：底层 iterable（如文件句柄）可能因 worker 未正常退出而延迟关闭。

本提交的目的是修复这个内存泄漏，使 worker 在迭代器关闭后尽快停止向队列填充数据。

## 如何达成设计目的

修复方式简洁直接：在 worker 任务的 `for (T item : iterable)` 循环体内、`queue.add(item)` 之前，插入对 `volatile boolean closed` 标志的检查——若 `closed` 已为 `true`，则 `return` 退出 worker 任务。

关键点在于 `closed` 字段已声明为 `volatile`（原代码即如此），保证 worker 线程能及时看到 `close()` 方法对其的写入。这样 `close()` 设置 `closed = true` 后，worker 在下一次循环迭代的检查点就会退出，不再继续 `queue.add()`。注释明确说明此举的原因："exit manually because `ConcurrentLinkedQueue` can't be interrupted"——即 `ConcurrentLinkedQueue` 的 `add` 操作不响应中断，必须手动检查标志位退出。

由于检查发生在每次循环开头、`add` 之前，最坏情况下 `close()` 后可能还多入队一个 item（检查与 add 之间的窗口），但这在功能上无害（队列随后会被 `clear()` 或不再被消费），远好于持续泄漏。

## 修改详情

### `core/src/main/java/org/apache/iceberg/util/ParallelIterable.java`

**修改目的**：修复 worker 任务在迭代器关闭后仍持续向队列填充数据的内存泄漏。

**工作逻辑**：在 `ParallelIterator` 构造器中定义的 worker `Runnable` 内，`for (T item : iterable)` 循环体首部增加 `closed` 检查：

```java
for (T item : iterable) {
    // exit manually because `ConcurrentLinkedQueue` can't be
    // interrupted
    if (closed) {
        return;
    }

    queue.add(item);
}
```

`closed` 是 `ParallelIterator` 的 `volatile` 实例字段，`close()` 方法会将其设为 `true`。worker 在每次准备入队前检查该标志，若已关闭则立即 `return` 退出任务，停止后续入队。原有的 `try-with-resources`（管理 iterable 的 close）、`IOException` 捕获逻辑不变。

### `core/src/test/java/org/apache/iceberg/util/TestParallelIterable.java`

**修改目的**：新增回归测试，验证迭代器提前关闭后队列不再被持续填充。

**工作逻辑**：新增 `closeMoreDataParallelIteratorWithoutCompleteIteration` 测试：

1. 创建单线程 `ExecutorService`。
2. 构造一个慢速 `Iterator<Integer>`：`hasNext` 在 number ≤ 1000 时返回 true 并自增，`next` 每次睡眠 10ms 控制生成速率。
3. 用 `Iterables.transform` 包装为含一个 `CloseableIterable<Integer>` 的列表，其 `iterator()` 返回 `CloseableIterator.withClose(integerIterator)`。
4. 创建 `ParallelIterable` 并获取 `CloseableIterator`。
5. 通过反射获取内部 `ParallelIterator` 的 `queue` 字段（`ConcurrentLinkedQueue`）。
6. 调用 `iterator.hasNext()` / `iterator.next()` 触发后台任务启动，用 Awaitility 等待队列被填充（`queueHasElements`）。
7. 调用 `iterator.close()`。
8. 用 Awaitility 等待最多 5 秒，断言队列最终为空（`"Queue is not empty after cleaning"`）。

若无此修复，worker 会持续以 ~10ms/item 的速率向队列入队 1000 个元素，`close()` 后队列不会被清空（`clear()` 后又被持续填充），5 秒内断言会失败。修复后 worker 在 `closed` 标志生效后立即退出，队列不再增长，`clear()` 后保持空。

## 小结

- **成效**：修复了 `ParallelIterable` 在迭代器提前关闭时后台 worker 仍持续向 `ConcurrentLinkedQueue` 填充数据导致的内存泄漏，worker 现在通过检查 `volatile closed` 标志及时退出；并补充了针对性的回归测试。
- **影响范围**：`core` 模块的 `ParallelIterable.java`（1 处 6 行新增）与 `TestParallelIterable.java`（1 个新测试方法）。`ParallelIterable` 是 Iceberg 并行扫描的基础设施，影响所有使用并行迭代的读取路径（如 manifest 并发读取、split 规划等），修复对提前关闭迭代器的场景（limit 查询、取消查询等）有实质收益。
- **回迁到 1.4.x 的注意事项**：**强烈建议回迁**。这是一个影响生产环境的内存泄漏 bug，1.4.x 分支若存在相同代码（`ParallelIterable` 的 worker 循环无 `closed` 检查），在任何提前关闭迭代器的场景都会泄漏。改动极小（加一个 `if (closed) return;`），风险极低，且不改变任何正常路径行为。测试可一并回迁以防止回归。
