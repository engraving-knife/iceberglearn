# 提交 0272：Spark: Fix flaky tests which concurrently modify HashSet (#9294)

## 提交信息

- **序号**：0272 / 4088
- **哈希**：5e62e478d8eda41fc46f441717e4a43e33f2cba1
- **短哈希**：5e62e478d
- **日期**：2023-12-14
- **作者**：Manu Zhang
- **提交说明**：Spark: Fix flaky tests which concurrently modify HashSet (#9294)
- **PR/Issue**：#9294

## 总体目的

这是一处针对 flaky 测试（偶发失败测试）的修复。Iceberg 在 `TestRemoveSnapshots` 及 Spark 各版本下的 `TestExpireSnapshotsAction`、`TestRemoveOrphanFilesAction` 测试里，都会构造一个 `deletedFiles` 集合，并通过 `removeSnapshots(...).deleteWith(s -> { ...; deletedFiles.add(s); })` 回调把过期/删除过程中被删的文件路径收集起来。这个回调并不是在测试主线程里同步执行，而是由 `expireOlderThan(...)` 调度到一个固定大小的线程池（`Executors.newFixedThreadPool(4, ...)`）中并发执行——也就是说有多个 worker 线程同时向 `deletedFiles` 写入。

问题在于：原代码用 `Set<String> deletedFiles = Sets.newHashSet();` 创建集合，而 Guava 的 `Sets.newHashSet()` 底层就是一个普通的 `java.util.HashSet`，它不是线程安全的。当多个线程并发 `add` 时，`HashSet` 内部的 `HashMap` 可能出现扩容竞争、哈希桶链表/树损坏、元素丢失，甚至触发 `ConcurrentModificationException`、空指针、CPU 100% 死循环等问题，从而表现为测试偶发失败（flaky）。修复动机就是消除这种由并发写非线程安全集合导致的 flaky 测试，让这些测试在并发执行下稳定通过。

值得注意的是，同一作用域内的另一个集合 `deleteThreads`（以及 `planThreadsIndex` 等计数器）本就用 `ConcurrentHashMap.newKeySet()` / `AtomicInteger` 做了线程安全处理，唯独 `deletedFiles` 漏了，因此本提交是把遗漏的那处补齐，使其与既有写法保持一致。

## 如何达成设计目的

设计思路非常直接：把并发写入的 `Set` 从非线程安全的 `Sets.newHashSet()`（Guava，底层 `HashSet`）替换为线程安全的 `ConcurrentHashMap.newKeySet()`。`ConcurrentHashMap.newKeySet()` 返回一个由 `ConcurrentHashMap` 支撑的 `Set` 视图，所有写操作（`add`/`remove`）走 `ConcurrentHashMap` 的分段/`CAS` 并发控制，可安全地被多线程同时修改，且不会丢失元素。这样既解决了并发写导致的 flaky 问题，又保持了原有测试断言的语义（去重收集被删文件路径），同时与紧邻的 `deleteThreads` 写法对齐，改动量极小（每个文件仅一行）。

## 修改详情

### `core/src/test/java/org/apache/iceberg/TestRemoveSnapshots.java`

**修改目的**：修复 core 模块过期快照测试中 `deletedFiles` 集合并发写不安全的问题。

**工作逻辑**：
将 `Set<String> deletedFiles = Sets.newHashSet();` 改为 `Set<String> deletedFiles = ConcurrentHashMap.newKeySet();`。该集合在 `removeSnapshots(table).executeDeleteWith(... 固定 4 线程池 ...).deleteWith(s -> { ...; deletedFiles.add(s); }).commit()` 中被多个 delete worker 线程并发写入，换用 `ConcurrentHashMap.newKeySet()` 后写入操作具备并发安全性。同作用域的 `deleteThreads` 本就用 `ConcurrentHashMap.newKeySet()`，此处补齐以保持一致。

### `spark/v3.3/spark/src/test/java/org/apache/iceberg/spark/actions/TestExpireSnapshotsAction.java`

**修改目的**：修复 Spark 3.3 下 expire snapshots action 测试的并发写集合问题。

**工作逻辑**：
将 `Set<String> deletedFiles = Sets.newHashSet();` 改为 `ConcurrentHashMap.newKeySet();`，该集合在多线程 delete 回调中被并发 `add`，换用线程安全集合以消除 flaky。

### `spark/v3.3/spark/src/test/java/org/apache/iceberg/spark/actions/TestRemoveOrphanFilesAction.java`

**修改目的**：修复 Spark 3.3 下 remove orphan files action 测试的并发写集合问题。

**工作逻辑**：
同上，`Set<String> deletedFiles` 由 `Sets.newHashSet()` 改为 `ConcurrentHashMap.newKeySet()`，供并发 delete worker 写入。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/actions/TestExpireSnapshotsAction.java`

**修改目的**：修复 Spark 3.4 下 expire snapshots action 测试的并发写集合问题。

**工作逻辑**：
与 Spark 3.3 同名文件相同，`deletedFiles` 改用 `ConcurrentHashMap.newKeySet()`。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/actions/TestRemoveOrphanFilesAction.java`

**修改目的**：修复 Spark 3.4 下 remove orphan files action 测试的并发写集合问题。

**工作逻辑**：
`deletedFiles` 由 `Sets.newHashSet()` 改为 `ConcurrentHashMap.newKeySet()`。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestExpireSnapshotsAction.java`

**修改目的**：修复 Spark 3.5 下 expire snapshots action 测试的并发写集合问题。

**工作逻辑**：
`deletedFiles` 由 `Sets.newHashSet()` 改为 `ConcurrentHashMap.newKeySet()`。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestRemoveOrphanFilesAction.java`

**修改目的**：修复 Spark 3.5 下 remove orphan files action 测试的并发写集合问题。

**工作逻辑**：
`deletedFiles` 由 `Sets.newHashSet()` 改为 `ConcurrentHashMap.newKeySet()`。

## 小结

该提交通过把 7 个测试文件中由多线程并发写入的 `deletedFiles` 集合从非线程安全的 `Sets.newHashSet()` 替换为线程安全的 `ConcurrentHashMap.newKeySet()`，消除了 expire snapshots / remove orphan files 测试因并发修改 `HashSet` 而偶发失败的问题，并与同作用域已有的 `deleteThreads` 写法保持一致。
