# 提交 3599：Core: Fix RejectedExecutionException in InMemoryLockManager when multiple catalogs share default lock manager (#15862)

## 提交信息

- **序号**：3599 / 4088
- **哈希**：1b733ed22c8f47965184a48017c5941014def4cf
- **短哈希**：1b733ed22
- **日期**：2026-04-27 13:53:38 +0200
- **作者**：Manu Zhang
- **提交说明**：Core: Fix RejectedExecutionException in InMemoryLockManager when multiple catalogs share default lock manager (#15862)
- **PR/Issue**：#15862

## 总体目的

这个提交修复了当多个 catalog 共享默认锁管理器时，`InMemoryLockManager` 抛出 `RejectedExecutionException` 的 bug。

Iceberg 的 `LockManagers.BaseLockManager` 中有一个静态共享的 `ScheduledExecutorService`（即 `scheduler()`），用于锁的心跳机制。这个调度器是所有锁管理器实例共享的静态资源。然而，`InMemoryLockManager` 的 `close()` 方法之前会调用 `scheduler.shutdownNow()` 来关闭这个共享的调度器，并将其设为 `null`。

当多个 catalog 共享同一个默认锁管理器时，一个 catalog 关闭会导致共享调度器被关闭，而其他 catalog 仍然在使用它。当其他 catalog 尝试提交心跳任务时，由于调度器已被关闭，就会抛出 `RejectedExecutionException`。这是一个典型的资源生命周期管理错误：实例不应该关闭它不拥有的共享资源。

## 如何达成设计目的

修复方案是让 `InMemoryLockManager.close()` 不再关闭共享的调度器。具体做法是：
1. 移除 `close()` 方法中关闭调度器的逻辑。
2. 添加注释说明调度器是共享的静态资源，不应由单个实例关闭。
3. 调度器使用 daemon 线程，并通过 `MoreExecutors.getExitingScheduledExecutorService` 注册了 shutdown hook，会在 JVM 退出时自动终止，因此不需要手动关闭。

同时在 `scheduler()` 方法上添加了 JavaDoc，明确说明调用者不应关闭这个调度器。

## 修改详情

### `core/src/main/java/org/apache/iceberg/util/LockManagers.java` (+9/-12 lines)

**修改目的**：修复共享调度器被错误关闭导致的 `RejectedExecutionException`。

**工作逻辑**：

1. **为 `scheduler()` 方法添加文档说明**：
```java
/**
 * Returns the shared scheduler for lock heartbeats.
 *
 * <p>Callers must not shut down this scheduler. It is shared across lock manager instances.
 */
public ScheduledExecutorService scheduler() {
```
明确告知调用者此调度器是共享的，不应被关闭。

2. **修改 `close()` 方法，移除关闭调度器的逻辑**：
```java
@Override
public void close() throws Exception {
  // The scheduler is a shared static resource used across all BaseLockManager instances.
  // Individual instances must not shut it down, as other instances may still be using it.
  // The scheduler uses daemon threads and will be terminated at JVM exit by the shutdown
  // hook registered via MoreExecutors.getExitingScheduledExecutorService.
}
```
之前的代码会执行 `scheduler.shutdownNow()` 并取消所有待执行的任务，然后将 `scheduler` 设为 `null`。修复后，`close()` 方法不再做任何操作，调度器的生命周期由 JVM shutdown hook 管理。

3. **移除不再需要的 import**：
```java
-import java.util.List;
-import java.util.concurrent.Future;
```
由于不再需要遍历和取消任务，`List` 和 `Future` 的 import 被移除。

## 总结

这个提交修复了一个重要的并发 bug：当多个 catalog 共享默认锁管理器时，一个 catalog 的关闭会导致其他 catalog 的锁心跳机制失效。修复方案正确地识别了调度器是共享资源这一事实，将其生命周期管理交由 JVM shutdown hook 处理，而不是由单个实例负责。这是一个典型的"谁创建谁销毁"原则的应用，修复后锁管理器的资源管理更加健壮。
