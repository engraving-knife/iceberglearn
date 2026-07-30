# 提交 3322：Core: Support parallel execution when scanning entries in ManifestGroup (#15426)

## 提交信息

- **序号**：3322 / 4088
- **哈希**：86e9ebbffde79ae270c3d57d621e509f9142b27c
- **短哈希**：86e9ebbff
- **日期**：2026-02-27
- **作者**：Hongyue/Steve Zhang
- **提交说明**：Core: Support parallel execution when scanning entries in ManifestGroup (#15426)
- **PR/Issue**：#15426

## 总体目的

`ManifestGroup` 负责扫描一组 manifest 文件并产出 `ManifestEntry`/`DataFile` 等记录，是 Iceberg 表文件规划（planning）的核心组件。它支持通过 `planWith(ExecutorService)` 指定一个线程池以并行扫描多个 manifest，从而加速大表的 planning。`plan(...)` 任务规划方法已正确利用该执行器：它先用 `entries(BiFunction)` 收集每个 manifest 的任务可迭代对象，再在 `executorService != null` 时用 `ParallelIterable` 包装以并行消费。

问题在于：`ManifestGroup` 还提供了一个无参的 `entries()` 方法（返回 `CloseableIterable<ManifestEntry<DataFile>>`），它内部调用的是带 `BiFunction` 的重载并把结果 `CloseableIterable.concat(...)` 拼接——这一路径**完全忽略了 `executorService`**，无论是否设置过 `planWith(executorService)`，manifest 扫描都在调用线程内串行执行。`FindFiles` API 的快照扫描路径恰好使用这个无参 `entries()`，随后又对结果做 `CloseableIterable.transform(...)` 复制文件元数据。因此即便用户调用 `FindFiles.in(table)...planWith(executorService)`，实际的 manifest 读取与过滤仍串行进行，并行计划能力形同虚设，违背了 `planWith` 的预期语义。

本提交通过在 `ManifestGroup` 新增一个接收转换函数的 `entries(Function)` 重载——该重载在 `executorService != null` 时用 `ParallelIterable` 包装——并让 `FindFiles` 改用该重载，把"读取 manifest entry + 复制文件元数据"的转换下推到工作线程中执行，从而真正实现 FindFiles 路径的并行扫描。

## 如何达成设计目的

设计上不改动已有的无参 `entries()` 与 `entries(BiFunction)`（避免影响其他调用方），而是新增一个 `entries(Function<CloseableIterable<ManifestEntry<DataFile>>, CloseableIterable<T>> entryTransform)` 重载。它在内部复用 `entries(BiFunction)` 产出每个 manifest 对应的转换后可迭代对象，然后按 `executorService` 是否存在选择 `ParallelIterable`（并行）或 `CloseableIterable.concat`（串行）——与 `plan(...)` 中的并行处理逻辑保持一致。`FindFiles` 改为调用该重载，把原先外层的 `CloseableIterable.transform(entries, entry -> entry.file().copy(includeColumnStats))` 作为转换函数传入，使复制操作发生在并行扫描的工作线程内。Javadoc 明确提示：由于底层 `ManifestReader` 在迭代时复用 entry 对象，转换函数若需在迭代步之外保留数据必须做防御性拷贝——`FindFiles` 的 `entry.file().copy(...)` 正是如此，故是安全的。

## 修改详情

### `core/src/main/java/org/apache/iceberg/ManifestGroup.java` (+29/-0 lines)

**修改目的**：新增支持并行执行的转换式 entries 重载。

**工作逻辑**：
新增 import `java.util.function.Function`，并新增 `public <T> CloseableIterable<T> entries(Function<CloseableIterable<ManifestEntry<DataFile>>, CloseableIterable<T>> entryTransform)` 方法。该方法先用 `entries((manifest, entries) -> entryTransform.apply(entries))` 得到每个 manifest 转换后的 `CloseableIterable<T>` 集合（复用已有的 BiFunction 重载，保持 manifest 读取、过滤等逻辑不变）；随后判断 `executorService != null`：是则返回 `new ParallelIterable<>(iterables, executorService)` 在线程池中并行消费各 manifest 的转换结果，否则返回 `CloseableIterable.concat(iterables)` 串行拼接。这与 `plan(...)` 方法末尾的并行/串行分支逻辑一致，使 `entries` 路径首次具备并行能力。Javadoc 说明：转换函数对每个 manifest 的 entries 调用以产生输出，开启 `planWith` 时在 worker 线程中执行；并警示 `ManifestReader` 复用 entry 对象，需做防御性拷贝。

### `core/src/main/java/org/apache/iceberg/FindFiles.java` (+11/-13 lines)

**修改目的**：让 FindFiles 的快照扫描路径走新的并行 entries 重载。

**工作逻辑**：
原先先调用无参 `.entries()` 拿到 `CloseableIterable<ManifestEntry<DataFile>>`，再在外层包一层 `CloseableIterable.transform(entries, entry -> entry.file().copy(includeColumnStats))`——两步分离，且无参 `entries()` 不并行。改为直接链式调用 `.entries(entries -> CloseableIterable.transform(entries, entry -> entry.file().copy(includeColumnStats)))`，把同一个转换函数作为参数传给新的重载。由于该重载内部在 `executorService != null` 时使用 `ParallelIterable`，`FindFiles` 上游设置的 `planWith(executorService)` 现在能真正生效：每个 manifest 的扫描与 `file().copy(...)` 都在工作线程中执行。`copy(includeColumnStats)` 满足防御性拷贝要求，避免 entry 复用导致的并发问题。

### `core/src/test/java/org/apache/iceberg/TestFindFiles.java` (+21/-8 lines)

**修改目的**：验证 FindFiles 在设置 `planWith` 后确实并行执行。

**工作逻辑**：
`testPlanWith` 重写为更能体现并行性的形式：用两次独立 `newAppend` 提交（FILE_A/B 与 FILE_C/D），从而生成两个 manifest 以便跨 manifest 并行扫描。线程池改为带自定义 `ThreadFactory` 的 `newFixedThreadPool(2, ...)`，工厂用 `AtomicInteger planThreadsIndex` 计数并为线程命名 `plan-N`、设为 daemon。断言三处：返回的 `files` 实例为 `ParallelIterable`（证明走了并行分支）；路径集合等于四个文件；`planThreadsIndex.get() > 0`（证明确实有线程在工作池中创建并执行）。新增 `ParallelIterable` 与 `AtomicInteger` 的 import。

## 总结

本提交通过在 `ManifestGroup` 新增带转换函数且支持并行的 `entries(Function)` 重载，并让 `FindFiles` 改用该重载，修复了 FindFiles 扫描路径忽略 `planWith(executorService)` 而始终串行的问题。改动小而精准，使 manifest 扫描与文件元数据复制能在线程池中并行进行，对包含大量 manifest 的表的文件查找性能有实质提升，并通过断言返回类型与线程计数的方式验证了并行行为确实生效。
