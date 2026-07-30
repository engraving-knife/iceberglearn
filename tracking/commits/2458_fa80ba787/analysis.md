# 提交 2458：Flink: Fix DynamicCommitter use ThreadPools deprecated method (#13728)

## 提交信息

- **序号**：2458 / 4088
- **哈希**：fa80ba787af776f516e772f27bf746756de93b70
- **短哈希**：fa80ba787
- **日期**：2025-08-05 12:19:10 +0200
- **作者**：GuoYu
- **提交说明**：Flink: Fix DynamicCommitter use ThreadPools deprecated method (#13728)
- **PR/Issue**：#13728

## 总体目的

该提交修复了 Flink `DynamicCommitter` 中使用的已弃用（deprecated）线程池创建方法，并修复了线程池资源未关闭的问题。

`DynamicCommitter` 是 Flink 动态 sink 中的提交器组件，负责将 Flink checkpoint 的数据提交到 Iceberg 表。此前它使用 `ThreadPools.newWorkerPool()` 方法创建工作线程池，该方法已被标记为弃用。此外，`close()` 方法的实现为空（`// do nothing`），创建的线程池在 committer 关闭时不会被 shutdown，导致线程池资源泄漏。

## 如何达成设计目的

通过两个修改解决上述问题：

1. **替换弃用方法**：将 `ThreadPools.newWorkerPool(...)` 替换为 `ThreadPools.newFixedThreadPool(...)`，后者是推荐使用的替代方法，功能等价但不被弃用。

2. **关闭线程池**：在 `close()` 方法中添加 `workerPool.shutdown()` 调用，确保 committer 关闭时线程池被正确关闭，避免线程泄漏。

## 修改详情

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicCommitter.java` (+3/-2 lines)

**修改目的**：替换弃用的线程池创建方法并修复资源泄漏。

**工作逻辑**：

1. 线程池创建方法替换：
```java
// 修改前
this.workerPool = ThreadPools.newWorkerPool("iceberg-committer-pool-" + sinkId, workerPoolSize);
// 修改后
this.workerPool =
    ThreadPools.newFixedThreadPool("iceberg-committer-pool-" + sinkId, workerPoolSize);
```

2. close 方法实现：
```java
// 修改前
@Override
public void close() throws IOException {
    // do nothing
}
// 修改后
@Override
public void close() throws IOException {
    workerPool.shutdown();
}
```

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicCommitter.java` (+3/-2 lines)

**修改目的**：对 Flink 1.20 版本应用相同的修复。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicCommitter.java` (+3/-2 lines)

**修改目的**：对 Flink 2.0 版本应用相同的修复。

## 总结

该提交修复了 Flink `DynamicCommitter` 中的两个问题：一是使用了已弃用的 `ThreadPools.newWorkerPool()` 方法，替换为推荐的 `ThreadPools.newFixedThreadPool()`；二是 `close()` 方法未关闭线程池导致资源泄漏，现在正确调用 `workerPool.shutdown()`。修改覆盖了 Flink 1.19、1.20 和 2.0 三个版本，是一个重要的资源管理修复。
