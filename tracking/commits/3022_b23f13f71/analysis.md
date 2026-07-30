# 提交 3022：Core: Address Race Condition in ScanTaskIterable (#14824)

## 提交信息

- **序号**：3022 / 4088
- **哈希**：b23f13f71e7aa39b728ba0af5aa7e157f20e498b
- **短哈希**：b23f13f71
- **日期**：2025-12-16
- **作者**：Prashant Singh
- **提交说明**：Core: Address Race Condition in ScanTaskIterable (#14824)
- **PR/Issue**：#14824

## 总体目的

本提交修复了 REST Catalog 扫描任务迭代器 `ScanTaskIterable` 中的竞态条件（race condition），并完善了异常传播机制。`ScanTaskIterable` 是 Iceberg REST Catalog 中的一个核心组件，用于在异步扫描计划模型下消费扫描任务。它采用生产者-消费者模式：多个工作线程（`PlanTaskWorker`）并发地从 REST 服务端拉取扫描任务并放入阻塞队列，消费端迭代器从队列中取出任务返回给调用方。

原有实现存在多个并发缺陷。第一，终止判断存在竞态：旧代码使用 `isDone()` 方法判断是否结束——检查 `taskQueue.isEmpty() && planTasks.isEmpty() && activeWorkers.get() == 0 && initialFileScanTasks.isEmpty()`。这四个条件在并发环境下可能在不同时刻为真，导致判断不可靠。例如，某个工作线程刚把任务放入队列但还未递减 `activeWorkers` 计数器时，另一个检查可能错误地认为所有工作已完成。第二，异常会丢失：当工作线程抛出异常时，异常在 `catch` 块中被包装为 `RuntimeException` 重新抛出，但由于执行在线程池中，该异常不会被消费端感知，导致迭代器可能永远阻塞在 `taskQueue.poll()` 上等待永远不会到来的任务。第三，阻塞调用可能导致死锁：旧代码使用 `taskQueue.put()` 无限阻塞地添加任务，如果消费端已停止消费（如提前 `close()`），工作线程会永久阻塞。

本提交通过引入"毒丸"（poison pill）模式、异常传播机制和超时机制彻底解决这些问题。提交说明列出了三个关键改进：实现毒丸终止（生产一个 dummy task 标记所有生产者已完成）、向迭代器消费者添加异常传播、在异常发生时终止以避免数据丢失并通过 RTE 传播异常。此外，本提交还重新启用了因竞态条件而被临时禁用的 `scanPlanningWithBatchScan` 测试（issue-14823）。

## 如何达成设计目的

设计思路是将多线程协作的终止信号从"推断式"（检查多个队列和计数器的空状态）改为"显式信号式"（投递 poison pill）。当最后一个工作线程退出时，显式向队列中投递一个 `DUMMY_TASK` 作为毒丸，消费端取到该任务时即知道不会再有新任务。异常处理方面，引入 `AtomicReference<RuntimeException> failure` 捕获工作线程的异常，消费端在每次 `hasNext()` 检查时读取并传播该异常。所有阻塞操作从 `put()` 改为带超时的 `offer()`，配合 `shutdown` 标志确保线程能及时退出。工作线程数从按任务数动态调整改为固定使用完整线程池大小，简化了 worker 生命周期管理并消除了旧代码中工作线程相互 re-spawn 的复杂逻辑。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/ScanTaskIterable.java` (+87/-48 lines)

**修改目的**：修复竞态条件，引入毒丸终止机制、异常传播和超时阻塞。

**工作逻辑**：

1. **毒丸常量与异常引用**：新增 `DUMMY_TASK`（一个以 null 参数构造的 `BaseFileScanTask`）作为终止信号，以及 `AtomicReference<RuntimeException> failure` 用于捕获并传播工作线程异常。同时移除了旧的 `WORKER_POOL_SIZE` 常量（原为 `WORKER_THREAD_POOL_SIZE / 4`）。

2. **空任务场景处理**：当没有初始 plan tasks 且 initialFileScanTasks 为空时，原先直接 `return`（不启动工作线程），消费端依赖 `isDone()` 判断结束。现在改为显式 `taskQueue.add(DUMMY_TASK)` 投递毒丸，确保消费端能通过取到毒丸而正常终止。

3. **工作线程数固定化**：`submitFixedWorkers()` 不再根据 `planTasks.size()` 动态计算工作线程数，而是固定提交 `ThreadPools.WORKER_THREAD_POOL_SIZE` 个工作线程。这消除了旧代码中"按任务数决定线程数"和"工作线程互相 re-spawn"的复杂逻辑，简化了线程生命周期管理。

4. **`PlanTaskWorker.run()` 改造**：
   - 循环条件增加 `!Thread.currentThread().isInterrupted()` 检查。
   - `InterruptedException` 处理：设置 `failure` 并触发 `shutdown`。
   - 通用 `Exception` 处理：不再直接 throw（会在线程池中丢失），改为 `failure.compareAndSet(null, ...)` 捕获异常并 `shutdown.set(true)` 终止其他线程。
   - `finally` 块调用新的 `handleWorkerExit()` 替代旧的递减+re-spawn 逻辑。

5. **`offerWithTimeout(FileScanTask)`**：新增私有方法，使用 `taskQueue.offer(task, QUEUE_POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS)` 替代 `taskQueue.put()`。在 offer 失败时循环重试，但每次重试前检查 `shutdown` 标志，确保能在收到关闭信号时及时退出，避免无限阻塞。

6. **`handleWorkerExit()`**：当 `activeWorkers` 递减到 0（即最后一个工作线程退出）时，判断是否有剩余工作：若无剩余工作或正在关闭，调用 `signalCompletion()` 投递毒丸；若有剩余工作但所有工作线程已退出（异常情况），设置 `failure` 并触发 `shutdown`。这取代了旧代码中"如果 remaining==0 但 planTasks 非空则 re-spawn 新工作线程"的脆弱逻辑。

7. **`signalCompletion()`**：通过 `offerWithTimeout(DUMMY_TASK)` 投递毒丸，若线程被中断则仅设置 `shutdown` 而不重抛（因为这是终止信号，失败不影响正确性）。

8. **`offerInitialFileScanTasks()`**：重构为使用 `offerWithTimeout()` 替代 `put()`，并增加 `isInterrupted()` 检查。

9. **`processPlanTask()`**：将 `taskQueue.put(task)` 改为 `offerWithTimeout(task)`，并在 offer 失败时提前 return。

10. **消费端 `hasNext()` 改造**：
    - 移除 `isDone()` 判断，改为循环 `poll` + 检查 `shutdown`。
    - 取到 `DUMMY_TASK` 时：清空 `nextTask`，设置 `shutdown=true`，跳出循环。
    - 取到正常任务时：设置 `hasNext=true`，跳出循环。
    - 循环结束后检查 `failure.get()`：若工作线程捕获了异常，则向消费端抛出该异常，确保错误不丢失。
    - 移除了旧的 `isDone()` 方法。

### `core/src/test/java/org/apache/iceberg/rest/TestScanTaskIterable.java` (+605/-0 lines)

**修改目的**：为修复后的 `ScanTaskIterable` 添加全面的并发测试。

**工作逻辑**：
新建 605 行测试文件，包含 16 个测试方法，覆盖：

- **基本功能**：`iterableWithNestedPlanTasks`（嵌套 plan tasks）、`iterableWithDeeplyNestedPlanTasks`（深层嵌套）、`chainedPlanTasks`（链式 plan tasks）验证正常的多级 plan task 消费。
- **迭代器契约**：`iteratorNextWithoutHasNext`（直接调用 next 而不先 hasNext）、`iteratorMultipleHasNextCallsIdempotent`（多次 hasNext 幂等）验证迭代器语义正确性。
- **异常传播**：`workerFailurePropagatesException`（工作线程失败传播异常）、`workerExceptionDoesNotBlockOtherTasks`（异常不阻塞其他任务）、`multipleWorkerFailuresOnlySignalOnce`（多次失败仅信号一次）、`workerExceptionWithFullQueueDoesNotHangOtherWorkers`（队列满时异常不阻塞）验证异常场景的正确处理。
- **并发场景**：`concurrentWorkersProcessingTasks`（并发工作线程处理）、`multipleWorkersWithMixedNestedPlanTasks`（多工作线程混合嵌套任务）、`initialFileScanTasksWithConcurrentPlanTasks`（初始任务与并发 plan tasks 共存）验证多线程协作正确性。
- **资源管理**：`slowProducerFastConsumer`（慢生产快消费）、`closeWhileWorkersAreRunning`（工作线程运行时关闭）、`closeWithFullQueueDoesNotHangWorkers`（队列满时关闭不阻塞）验证关闭和资源释放场景。

测试使用 Mockito mock RESTClient，构造可控的 plan task 响应链，使用 `CountDownLatch` 和 `Executors` 模拟并发场景。

### `core/src/test/java/org/apache/iceberg/rest/TestRESTScanPlanning.java` (+0/-1 lines)

**修改目的**：重新启用此前因竞态条件被临时禁用的测试。

**工作逻辑**：
移除 `scanPlanningWithBatchScan` 测试方法上的 `@Disabled("Temporarily disabled: Fix tracked via issue-14823")` 注解。该测试此前因 issue-14823 跟踪的竞态条件被禁用，本提交修复了该竞态条件，因此测试可以重新启用。

## 总结

本提交通过引入毒丸模式、异常传播机制和超时阻塞，彻底修复了 `ScanTaskIterable` 中的竞态条件，解决了消费端可能永久阻塞、工作线程异常丢失、关闭时死锁等多个并发缺陷。新增的 605 行测试全面覆盖了正常、异常和并发场景，确保修复的正确性和回归保护。重新启用被禁用的 BatchScan 测试也验证了修复的实际效果。这一改动对于 REST Catalog 在高并发场景下的稳定性具有重要意义。
