# 提交 3816：Core: Support pluggable executor service for manifest writing on SnapshotUpdate (#16108)

## 提交信息

- **序号**：3816 / 4088
- **哈希**：b88addf85253951ab4898967b62eee287826f7c9
- **短哈希**：b88addf85
- **日期**：2026-06-01 14:54:49 -0600
- **作者**：Hongyue/Steve Zhang <hongyue.apache@gmail.com>
- **提交说明**：Core: Support pluggable executor service for manifest writing on SnapshotUpdate (#16108)
- **PR/Issue**：#16108

## 总体目的

本提交为 Iceberg 的 `SnapshotUpdate` 提交流程新增"可插拔的 manifest 写入线程池"能力。在此之前，`SnapshotUpdate` 已经支持通过 `scanManifestsWith(ExecutorService)` 为扫描 manifest 提供自定义线程池，但 manifest 的写入（commit 时把数据文件分组写入多个 manifest 文件）一直使用 `SnapshotProducer` 内部通过 `ThreadPools.getWorkerPool()` 获取的默认共享 worker 池，并行度也固定为 `ThreadPools.WORKER_THREAD_POOL_SIZE`。

在大型生产部署中，commit 阶段的 manifest 写入可能成为瓶颈，且与其它操作共享同一个全局 worker 池容易造成资源争抢和隔离性不足。用户希望可以为 manifest 写入单独指定一个线程池和并行度，以便：
- 与扫描/其它任务隔离资源，避免相互影响；
- 根据表大小和集群资源灵活控制 manifest 写入的并行度（进而影响产生的 manifest 文件数量）；
- 在引擎（如 Spark/Flink）内复用引擎管理的线程池，统一资源治理。

本提交通过在 `SnapshotUpdate` 接口新增 `writeManifestsWith(ExecutorService, int)` 默认方法，并在 `SnapshotProducer` 中实现该能力，让 commit 时的 manifest 写入使用用户提供的线程池与并行度。

## 如何达成设计目的

设计上沿用 `scanManifestsWith` 的模式：在 `SnapshotUpdate` 接口新增一个默认抛 `UnsupportedOperationException` 的 `writeManifestsWith(ExecutorService, int)` 方法，由 `SnapshotProducer` 提供 concrete 实现。`SnapshotProducer` 新增 `writePool` 与 `writePoolParallelism` 两个字段（默认并行度为 `WORKER_THREAD_POOL_SIZE`），`writeManifestsWith` 校验非空与正数后保存。在 `writeManifests` 方法中，并行度从硬编码的 `ThreadPools.WORKER_THREAD_POOL_SIZE` 改为 `writePoolParallelism`，执行器从 `ThreadPools.getWorkerPool()` 改为延迟初始化的 `writePool()`（若用户未设置则回退到默认 worker 池）。这样既支持可插拔，又保持向后兼容。

## 修改详情

### `api/src/main/java/org/apache/iceberg/SnapshotUpdate.java` (+16/-0 lines)

**修改目的**：在公共 API 接口上声明新的 manifest 写入线程池设置方法。

**工作逻辑**：
新增默认方法 `writeManifestsWith(ExecutorService, int)`，默认抛 `UnsupportedOperationException`，Javadoc 说明 `parallelism` 控制 manifest writer 数量（进而影响 manifest 文件数），executor 提供执行线程：
```java
default ThisT writeManifestsWith(ExecutorService executorService, int parallelism) {
  throw new UnsupportedOperationException(
      this.getClass().getName() + " does not support writeManifestsWith");
}
```

### `core/src/main/java/org/apache/iceberg/SnapshotProducer.java` (+22/-2 lines)

**修改目的**：实现 `writeManifestsWith`，并让 manifest 写入使用用户提供的线程池与并行度。

**工作逻辑**：
- 新增字段 `writePool`、`writePoolParallelism`（默认 `ThreadPools.WORKER_THREAD_POOL_SIZE`）。
- 实现 `writeManifestsWith`，校验 executor 非空、parallelism > 0：
```java
@Override
public ThisT writeManifestsWith(ExecutorService executorService, int parallelism) {
  Preconditions.checkArgument(executorService != null, "Executor service cannot be null");
  Preconditions.checkArgument(
      parallelism > 0, "Parallelism must be greater than 0, but was: %s", parallelism);
  this.writePool = executorService;
  this.writePoolParallelism = parallelism;
  return self();
}
```
- 新增 `writePool()` 方法，延迟初始化：用户未设置时回退到 `ThreadPools.getWorkerPool()`：
```java
protected ExecutorService writePool() {
  if (writePool == null) {
    this.writePool = ThreadPools.getWorkerPool();
  }
  return writePool;
}
```
- `writeManifests` 从 `static` 改为实例方法，并行度改用 `writePoolParallelism`，执行器改用 `writePool()`：
```java
int parallelism = manifestWriterCount(writePoolParallelism, files.size());
...
.executeWith(writePool())
```

### `core/src/test/java/org/apache/iceberg/TestBase.java` (+24/-0 lines)

**修改目的**：为测试提供共用工具：`assertEmptyTable` 断言辅助、`newNamedExecutor` 创建带命名前缀的线程池便于断言线程是否被创建。

**工作逻辑**：
- `assertEmptyTable()`：断言表无 manifest、无当前快照、lastSequenceNumber=0。
- `newNamedExecutor(prefix, counter[, nThreads])`：创建固定大小线程池，自定义 ThreadFactory 设置线程名前缀（如 `commit-0`、`commit-1`），并用 `AtomicInteger` 计数，便于测试断言"线程确实由用户池创建"。

### `core/src/test/java/org/apache/iceberg/TestMergeAppend.java` (+66/-6 lines)

**修改目的**：验证新的 manifest 写入线程池能力，并改造原有依赖默认 worker 池大小的测试。

**工作逻辑**：
- 移除原 `testAppendWithManifestFileOrdering` 中对 `ThreadPools.WORKER_THREAD_POOL_SIZE >= 3` 的假设，改为显式 `append.writeManifestsWith(Executors.newFixedThreadPool(multiplier), multiplier)`，使测试不再依赖全局线程池大小。
- 新增 `testAppendWithWriteManifestsExecutor`：用命名线程池提交，断言 `commitThreadsIndex > 0`（证明 manifest 写入用了用户池），并验证快照与 manifest 内容正确。
- 新增 `testAppendWithSeparateScanAndWriteExecutors`：同时设置 `scanManifestsWith` 与 `writeManifestsWith`，分别用不同命名前缀，断言两个池都被使用，验证扫描与写入的线程池隔离。

### `core/src/test/java/org/apache/iceberg/TestSnapshotProducer.java` (+24/-0 lines)

**修改目的**：覆盖参数校验。

**工作逻辑**：
- `testWriteManifestsWithNullExecutorThrows`：`writeManifestsWith(null, 4)` 抛 `IllegalArgumentException("Executor service cannot be null")`。
- `testWriteManifestsWithInvalidParallelismThrows`：`parallelism=0` 与 `-1` 均抛 "Parallelism must be greater than 0"。

## 总结

本提交为 Iceberg commit 流程补齐了"manifest 写入线程池可插拔"的能力，与既有的 `scanManifestsWith` 形成对称设计。用户现在可以为 manifest 写入单独指定线程池与并行度，实现资源隔离与灵活控制 manifest 文件数量。改动保持向后兼容（未设置时回退到默认 worker 池），测试覆盖功能正确性与参数校验，并顺便消除了原有测试对全局线程池大小的依赖。这是 Iceberg 在大规模生产部署场景下资源治理能力的重要增强。
