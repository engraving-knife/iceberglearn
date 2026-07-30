# 提交 0288：Core: Shutdown scheduler in Lock manager (#9150)

## 提交信息

- **序号**：0288 / 4088
- **哈希**：5fce7ecaa3094c19d86e9243cf0d2f0d8537c65f
- **短哈希**：5fce7ecaa
- **日期**：2023-12-19 08:58:12 +0100
- **作者**：gabry.wu
- **提交说明**：Core: Shutdown scheduler in Lock manager (#9150)
- **PR/Issue**：#9150

## 总体目的

Iceberg 的 `LockManagers.BaseLockManager` 内部维护了一个用于锁心跳（heartbeat）调度的 `ScheduledExecutorService scheduler`。该 scheduler 在 `scheduler()` 方法中按需惰性创建，底层通过 `MoreExecutors.getExitingScheduledExecutorService` 包装了一个由守护线程构成的 `ScheduledThreadPoolExecutor`，用于周期性地刷新 `InMemoryLockManager` 中各锁的过期时间。

问题在于：`BaseLockManager` 此前没有实现 `close()` 来关闭这个 scheduler，而 `InMemoryLockManager.close()` 只清理了 `HEARTBEATS` 与 `LOCKS` 两个静态 Map（取消心跳 future、清空映射），并未触及 scheduler 本身。虽然底层线程是守护线程，JVM 退出时会随之结束，但在长生命周期的应用（如长期运行的引擎或测试套件）中，scheduler 线程池不会随某个 `LockManager` 实例的关闭而停止，仍会驻留并占用线程资源。更重要的是，对于测试场景，多个测试用例反复创建并关闭 `InMemoryLockManager` 时，scheduler 不会被回收，已提交但尚未执行的调度任务也无法被显式取消，可能引发线程泄漏与测试间的状态污染。

本提交的动机就是补上 `BaseLockManager` 的 `close()` 实现，在关闭时主动 `shutdownNow()` 调度器、取消其中尚未结束的 `Future` 任务，并把 `scheduler` 引用置空，使锁管理器在显式关闭时能彻底释放调度线程资源。

## 如何达成设计目的

整体设计是在 `BaseLockManager` 中新增 `close()` 方法，把 scheduler 的关闭逻辑收敛到基类，子类只需在自身 `close()` 中清理自己的状态后调用 `super.close()` 即可。具体做法是：调用 `scheduler.shutdownNow()` 拿回尚未开始执行的任务列表，对其中实现了 `Future` 接口的任务逐一调用 `cancel(true)` 进行中断取消，最后把 `scheduler` 字段置为 `null`，以便后续如再被使用时可重新创建。`InMemoryLockManager.close()` 同步调整为 `throws Exception` 并在清理完静态 Map 后调用 `super.close()`，把 scheduler 关闭纳入自身关闭流程。

由于 `scheduler` 是 `BaseLockManager` 上的 `private static volatile` 字段（所有实例共享），关闭逻辑加上了 `if (scheduler != null)` 守卫，避免对 null 调用 `shutdownNow()` 抛 NPE，也避免多次 close 时的重复关闭。

## 修改详情

### `core/src/main/java/org/apache/iceberg/util/LockManagers.java`

**修改目的**：为 `BaseLockManager` 增加 scheduler 关闭逻辑，并让 `InMemoryLockManager.close()` 调用父类关闭。

**工作逻辑**：

- 新增 import：`java.util.List` 与 `java.util.concurrent.Future`，供 `shutdownNow()` 返回值类型与取消逻辑使用。
- 在 `BaseLockManager` 中新增 `close()` 方法（签名 `public void close() throws Exception`，覆盖 `LockManager` 接口声明）：
  - 先用 `if (scheduler != null)` 守卫，避免对未创建或已关闭的 scheduler 重复操作；
  - 调用 `List<Runnable> tasks = scheduler.shutdownNow()`，立即尝试停止所有正在执行的任务并返回尚未开始执行的任务列表（`shutdownNow()` 会尝试中断正在执行的任务）；
  - 对返回的每个 `task`，若其 `instanceof Future`，则强转为 `Future<?>` 并调用 `cancel(true)`，让等待中的调度任务也能被中断取消；
  - 最后 `scheduler = null`，把静态引用置空，使下次调用 `scheduler()` 时可按需重新创建。
- 调整 `InMemoryLockManager.close()`：
  - 方法签名由 `public void close()` 改为 `public void close() throws Exception`，与父类 `close()` 的 `throws Exception` 声明对齐；
  - 在原有清理逻辑（`HEARTBEATS.values().forEach(future -> future.cancel(false));`、`HEARTBEATS.clear();`、`LOCKS.clear();`）之后，新增 `super.close();` 调用，触发 `BaseLockManager` 中的 scheduler 关闭流程。

### `core/src/test/java/org/apache/iceberg/util/TestInMemoryLockManager.java`

**修改目的**：让测试的 `@AfterEach` 方法签名与 `close()` 新的 `throws Exception` 声明兼容。

**工作逻辑**：

把 `after()` 方法签名由 `public void after()` 改为 `public void after() throws Exception`，使其中调用的 `lockManager.close()` 抛出的受检异常能够向上传播，避免编译错误。这样测试在关闭锁管理器时会真正触发新增的 scheduler 关闭路径。

## 小结

本提交通过为 `BaseLockManager` 新增 `close()` 实现，在锁管理器关闭时主动 `shutdownNow()` 调度器、取消未执行的 `Future` 任务并置空 scheduler 引用，补上了此前缺失的调度线程生命周期管理；`InMemoryLockManager.close()` 同步调用 `super.close()`，把 scheduler 关闭纳入自身关闭流程，避免在长生命周期应用与测试套件中残留调度线程与已提交任务，消除潜在的线程泄漏与测试间状态污染。
