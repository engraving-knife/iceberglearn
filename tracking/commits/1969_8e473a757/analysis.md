# 提交 1969：Core: Lazy init workerPool in RemoveSnapshots and SnapshotProducer (#12427)

## 提交信息

- **序号**：1969 / 4088
- **哈希**：8e473a7575a3298dcfedce329d1275db5a892e1c
- **短哈希**：8e473a757
- **日期**：2025-04-07 12:08:09 +0200
- **作者**：Bodor Laszlo
- **提交说明**：Core: Lazy init workerPool in RemoveSnapshots and SnapshotProducer (#12427)
- **PR/Issue**：#12427

## 总体目的

该提交旨在解决 `RemoveSnapshots`（快照过期删除）和 `SnapshotProducer`（快照提交生产者）中 workerPool（工作线程池）过早初始化导致的资源浪费与潜在线程泄漏问题。

在原有实现中，`RemoveSnapshots` 的 `planExecutorService` 和 `SnapshotProducer` 的 `workerPool` 在字段声明时即通过 `ThreadPools.getWorkerPool()` 直接初始化。这意味着即使用户后续通过 `planWith()` / `scanExecutor()` / `executeWith()` 等方法传入自定义线程池，默认线程池也已经被创建。如果默认线程池是非守护线程或未正确关闭，就会造成无谓的资源占用，甚至影响测试与应用关闭流程。

通过将初始化改为延迟（lazy）方式——仅在线程池真正被使用时才创建默认池——可以避免在用户已经提供自定义执行器的情况下仍然创建无用线程池的问题，提升资源利用效率并降低线程泄漏风险。

## 如何达成设计目的

设计思路是将"字段初始化即创建线程池"改为"首次访问时按需创建"：

1. 将字段声明从直接赋值 `ThreadPools.getWorkerPool()` 改为仅声明为 `null`。
2. 新增/修改对应的访问方法（`planExecutorService()` / `workerPool()`），在方法内部判断字段是否为 null，若为 null 则调用 `ThreadPools.getWorkerPool()` 进行初始化，然后返回。
3. 将原本直接引用字段（如 `planExecutorService`、`workerPool`）的使用点改为调用访问方法（`planExecutorService()`、`workerPool()`），保证所有使用路径都经过延迟初始化逻辑。
4. 新增测试 `testExpireSnapshotsWithExecutor`，验证当用户通过 `planWith()` 传入自定义线程池时，确实使用的是用户提供的池（通过统计创建的线程数来断言）。

## 修改详情

### `core/src/main/java/org/apache/iceberg/RemoveSnapshots.java` (修改, +13/-3 lines)

**修改目的**：将 `planExecutorService` 改为延迟初始化。

**工作逻辑**：
- 字段 `planExecutorService` 由 `ThreadPools.getWorkerPool()` 直接初始化改为仅声明为 `null`。
- 新增 `protected ExecutorService planExecutorService()` 方法：若 `planExecutorService` 为 null，则赋值为 `ThreadPools.getWorkerPool()`，再返回。这样保证只有真正需要使用且用户未通过 `planWith()` 设置过执行器时，才会创建默认工作池。
- 在两处使用点（`idsToRetain` 的 `Tasks.foreach` 执行，以及 `FileCleanupStrategy` 构造时传入）将 `planExecutorService` 字段引用改为 `planExecutorService()` 方法调用。

### `core/src/main/java/org/apache/iceberg/SnapshotProducer.java` (修改, +7/-3 lines)

**修改目的**：将 `workerPool` 改为延迟初始化。

**工作逻辑**：
- 字段 `workerPool` 由 `ThreadPools.getWorkerPool()` 直接初始化改为仅声明为 `null`。
- 修改已有的 `workerPool()` 访问方法：原实现直接返回 `this.workerPool`；新实现先判断 `workerPool == null`，若为 null 则赋值 `ThreadPools.getWorkerPool()`，再返回。
- 将 `Tasks.range(...).executeWith(workerPool)` 改为 `executeWith(workerPool())`，使执行时走延迟初始化路径。

### `core/src/test/java/org/apache/iceberg/TestRemoveSnapshots.java` (修改, +27/-0 lines)

**修改目的**：验证延迟初始化在用户提供自定义线程池时确实生效。

**工作逻辑**：
- 新增 `testExpireSnapshotsWithExecutor` 测试用例。
- 使用 `AtomicInteger scanThreadsIndex` 统计通过自定义 `ThreadFactory` 创建的线程数量。
- 通过 `planWith()` 传入一个固定大小为 1 的线程池，线程池使用自定义 ThreadFactory 创建并命名线程（"scan-N"），设置为守护线程。
- 执行两次 append 文件并 commit，再调用 `expireOlderThan` 过期快照并 commit。
- 断言 `scanThreadsIndex.get() > 0`，即确认在过期快照过程中确实使用了用户提供的线程池（创建了线程），而非默认线程池。

## 总结

本提交通过将 `RemoveSnapshots` 和 `SnapshotProducer` 中的工作线程池改为延迟初始化，避免了用户已提供自定义执行器时仍然创建默认线程池的资源浪费与潜在线程泄漏问题。修改集中在访问方法的按需创建逻辑以及使用点的统一替换，并新增测试验证自定义线程池被正确使用。
