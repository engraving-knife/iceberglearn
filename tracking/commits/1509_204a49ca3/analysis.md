# 提交序号 1509 短哈希 204a49ca3 分析

## 提交信息
- 哈希：204a49ca3c9666eb1d0b225ae73cc96ebb9c1c6d
- 日期：2024-12-18
- 作者：Karol Sobczak <napewnotrafi@gmail.com>
- 消息：Use try-with-resources in TestParallelIterable (#11810)

## 总体目的

本提交对 `TestParallelIterable` 测试类进行重构，确保测试中创建的 `ExecutorService` 能够被正确关闭，避免线程泄漏。修改前，多个测试方法在方法体中间创建了 `ExecutorService`（线程池），但只在方法末尾调用 `shutdown()` 或 `shutdownNow()`。这种写法存在风险：如果测试方法中间的断言失败或抛出异常，`shutdown` 调用就不会被执行，导致线程池中创建的线程无法被回收，造成线程泄漏。在 CI 环境下，多个测试连续运行时，泄漏的线程会累积，可能影响后续测试的稳定性或导致资源耗尽。

尽管提交标题提到"try-with-resources"，实际实现采用的是 `try { ... } finally { executor.shutdown(); }` 模式。这与 try-with-resources 的目标一致——确保资源（此处为线程池）在无论是否发生异常的情况下都能被释放。之所以用 try-finally 而非 try-with-resources，是因为 `ExecutorService` 接口本身没有实现 `AutoCloseable`（在 Java 19+ 才有），所以用 finally 块来保证关闭。

这是一个测试健壮性改进，不改变任何产品代码或测试的断言逻辑，只是把原有的线性代码包进 try-finally 以保证资源清理。

## 如何达成设计目的

本提交通过将 `TestParallelIterable` 中 4 个测试方法的 `ExecutorService` 使用代码包裹在 `try { ... } finally { executor.shutdown(); }` 中来达成目的。每个方法的修改模式一致：把创建线程池之后的业务逻辑放入 try 块，在 finally 块中调用 `executor.shutdown()`。

### 修改详情

#### core/src/test/java/org/apache/iceberg/util/TestParallelIterable.java

该文件测试 `ParallelIterable`（并行可迭代器）的行为。修改涉及 4 个测试方法：

1. `closeParallelIteratorWithoutCompleteIteration()`：创建单线程固定线程池，测试在未完成迭代时关闭并行迭代器后队列被清空。修改将业务逻辑包入 try，finally 中 `executor.shutdown()`。

2. `closeMoreDataParallelIteratorWithoutCompleteIteration()`：类似场景，但数据源会产生超过 1000 个元素（带 sleep 控制速率），测试提前关闭后队列清空。同样包入 try-finally。

3. `limitQueueSize()`：使用缓存线程池，测试并行迭代器的内部队列大小不超过设定的 maxQueueSize + iterables.size()。修改前在方法末尾调用 `iterator.close()` 和 `executor.shutdownNow()`；修改后将 `iterator.close()` 移入 try 块末尾，finally 中调用 `executor.shutdown()`。注意这里从 `shutdownNow()` 改为 `shutdown()`——这是因为 try 块内已完成迭代并关闭了迭代器，线程池中的任务应已自然结束，用 `shutdown()`（有序关闭）更合适，避免强制中断。

4. `queueSizeOne()`：与 `limitQueueSize` 类似，但 maxQueueSize=1，测试极端小队列场景。修改模式同上。

整体上，每个方法的业务逻辑（断言、迭代、关闭迭代器）保持不变，只是结构调整：把线程池创建之后的所有操作放入 try 块，把 `executor.shutdown()` 放入 finally 块。这样无论 try 块中是否抛出断言异常，线程池都会被关闭。同时方法3、4中把原来的 `shutdownNow()` 统一改为 `shutdown()`，因为此时迭代已完成，无需强制中断。

## 小结

本提交是一个测试资源管理改进：通过将 `TestParallelIterable` 中 4 个测试方法的 `ExecutorService` 使用代码包裹在 try-finally 中，确保线程池在测试失败或异常时也能被正确关闭，避免线程泄漏。这提升了测试套件的健壮性，减少因资源泄漏导致的 CI 不稳定。修改不涉及任何产品代码或测试断言逻辑的变更，纯粹是资源清理模式的规范化。方法3、4中顺带将 `shutdownNow()` 改为 `shutdown()`，因为迭代已完成时有序关闭更恰当。
