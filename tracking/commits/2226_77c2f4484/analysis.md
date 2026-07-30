# 提交 2226：Flink, Spark: Backport ThreadPools introduce newExitingWorkerPool and newFixedThreadPool for clearer semantics to Flink 1.19 and Spark 3.4 (#13265)

## 提交信息

- **序号**：2226 / 4088
- **哈希**：77c2f4484ef169872192eda20bf82c8d859d7104
- **短哈希**：77c2f4484
- **日期**：2025-06-10 17:00:51 +0800
- **作者**：GuoYu
- **提交说明**：Flink, Spark: Backport ThreadPools introduce newExitingWorkerPool and newFixedThreadPool for clearer semantics to Flink 1.19 and Spark 3.4 (#13265) backports #11073
- **PR/Issue**：#13265（backport #11073）

## 总体目的

这个提交是 PR #11073 的反向移植，将 ThreadPools 方法的语义清晰化改动同步到 Flink 1.19 和 Spark 3.4 模块。原 PR 在 core 模块的 `ThreadPools` 工具类中引入了 `newExitingWorkerPool` 和 `newFixedThreadPool` 两个方法名，替代原来语义不明确的 `newWorkerPool` 方法。`newWorkerPool` 这个名称无法清晰表达创建的是哪种类型的线程池（退出型还是固定型），容易导致调用者误用。本 backport 将 Flink 1.19 和 Spark 3.4 模块中对 `ThreadPools.newWorkerPool` 的调用更新为 `ThreadPools.newFixedThreadPool`，同时在 IcebergCommitter 的 close 方法中补充了 `workerPool.shutdown()` 调用确保线程池正确关闭，并更新了 SparkTableUtil 的 javadoc 说明 ExecutorService 的生命周期管理。这提升了代码的可读性和线程池使用的正确性。

## 如何达成设计目的

- 将 Flink 1.19 模块中所有 `ThreadPools.newWorkerPool(...)` 调用替换为 `ThreadPools.newFixedThreadPool(...)`，涉及 6 个文件。
- 在 `IcebergCommitter.close()` 方法中新增 `workerPool.shutdown()` 调用，确保 committer 关闭时线程池被正确关闭。
- 更新 Spark 3.4 模块 `SparkTableUtil` 中 6 处 javadoc，明确说明 ExecutorService 参数的生命周期行为（null 时在当前线程执行，非 null 时方法内部会 shutdown）。

## 修改详情

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/IcebergCommitter.java` (修改, +2/-1 lines)

**修改目的**：使用语义更清晰的线程池方法并确保正确关闭。

**工作逻辑**：
- 将 `ThreadPools.newWorkerPool(...)` 改为 `ThreadPools.newFixedThreadPool(...)`。
- 在 `close()` 方法中新增 `workerPool.shutdown()`，确保 committer 关闭时线程池资源被释放。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/IcebergFilesCommitter.java` (修改, +1/-1 line)

**修改目的**：使用语义更清晰的线程池方法。

**工作逻辑**：将 `ThreadPools.newWorkerPool(...)` 改为 `ThreadPools.newFixedThreadPool(...)`。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/source/FlinkInputFormat.java` (修改, +1/-1 line)

**修改目的**：使用语义更清晰的线程池方法。

**工作逻辑**：将 `ThreadPools.newWorkerPool(...)` 改为 `ThreadPools.newFixedThreadPool(...)`。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/source/IcebergSource.java` (修改, +1/-1 line)

**修改目的**：使用语义更清晰的线程池方法。

**工作逻辑**：将 `ThreadPools.newWorkerPool(...)` 改为 `ThreadPools.newFixedThreadPool(...)`。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/source/StreamingMonitorFunction.java` (修改, +1/-1 line)

**修改目的**：使用语义更清晰的线程池方法。

**工作逻辑**：将 `ThreadPools.newWorkerPool(...)` 改为 `ThreadPools.newFixedThreadPool(...)`。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/source/enumerator/ContinuousSplitPlannerImpl.java` (修改, +1/-1 line)

**修改目的**：使用语义更清晰的线程池方法。

**工作逻辑**：将 `ThreadPools.newWorkerPool(...)` 改为 `ThreadPools.newFixedThreadPool(...)`。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/SparkTableUtil.java` (修改, +15/-5 lines)

**修改目的**：更新 javadoc 说明 ExecutorService 参数的生命周期。

**工作逻辑**：更新 6 处 `importSparkTable` 和 `importSparkPartitions` 方法的 javadoc，将 `@param service executor service to use for file reading` 扩展为详细说明：null 时在当前线程执行文件读取，非 null 时方法内部会在文件读取完成后 shutdown 提供的 ExecutorService。这明确了调用者对 ExecutorService 生命周期的责任。

## 总结

该提交是 PR #11073 的 backport，将 Flink 1.19 和 Spark 3.4 模块中的 `ThreadPools.newWorkerPool` 调用更新为语义更明确的 `ThreadPools.newFixedThreadPool`，同时在 IcebergCommitter 中补充线程池关闭逻辑，并更新 SparkTableUtil 的 javadoc 明确 ExecutorService 生命周期。改动提升了代码可读性和线程池资源管理的正确性。
