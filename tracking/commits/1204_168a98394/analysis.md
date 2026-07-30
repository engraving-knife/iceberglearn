# 提交 1204：ThreadPools introduce newExitingWorkerPool and newFixedThreadPool for clearer semantics (#11073)

## 提交信息

- **序号**：1204 / 4088
- **哈希**：168a9839425f9d3e2518ed345b7847ecb7cabb89
- **短哈希**：168a98394
- **日期**：2024-10-01（Wed Oct 2 00:38:32 2024 +0800）
- **作者**：fengjiajie <fengjiajie@sensorsdata.cn>
- **提交说明**：ThreadPools introduce newExitingWorkerPool and newFixedThreadPool for clearer semantics (#11073)
- **PR/Issue**：#11073

## 总体目的

Iceberg 的 `ThreadPools` 工具类原本只提供了一个静态工厂 `newWorkerPool(namePrefix, poolSize)`，它内部调用 Guava 的 `MoreExecutors.getExitingExecutorService(...)` 把 `Executors.newFixedThreadPool` 包装成"在 JVM 退出时自动终止"的执行器服务。问题在于：`getExitingExecutorService` 会注册一个 JVM shutdown hook，并且这个 shutdown hook 即使在调用方显式 `shutdown()` 之后也不会被移除。

这意味着：

1. **语义模糊**：方法名 `newWorkerPool` 既不体现"会注册 shutdown hook"的语义，也不告诉调用方该池子是"长生命周期"还是"短生命周期"。
2. **shutdown hook 累积**：对于短生命周期的池子（例如每次查询、每次 commit 创建后立即 `shutdown()`），如果反复调用 `newWorkerPool`，会在 JVM 中累积越来越多的 shutdown hook，导致内存泄漏与 JVM 退出时额外的清理负担，在长跑的 JVM（如 Flink/Spark 任务、Kafka Connect worker）中尤其明显。

本提交通过把 `newWorkerPool` 拆成两个语义清晰的方法来解决：

- `newExitingWorkerPool(namePrefix, poolSize)`：保留 shutdown hook 包装，适用于长生命周期、需要在 JVM 退出时自动清理的池子（如 FileIO 的删除池、静态全局 worker 池）。
- `newFixedThreadPool(namePrefix, poolSize)`：不注册 shutdown hook，纯粹返回一个 daemon 线程的固定大小池，调用方需自行管理生命周期（`shutdown()`），适用于短生命周期池。

原 `newWorkerPool` 标记为 `@Deprecated`（计划 2.0.0 移除），内部直接委托给 `newExitingWorkerPool` 以保持向后兼容。

同时本提交把所有"短生命周期"调用方从 `newWorkerPool` 切到 `newFixedThreadPool`，并修正了 `IcebergCommitter#close()` 中漏掉的 `workerPool.shutdown()` 调用（这是之前会泄漏线程的直接证据）。

## 如何达成设计目的

整体思路是"语义拆分 + 调用方校正"：

1. 在 `ThreadPools` 中新增 `newExitingWorkerPool` 和 `newFixedThreadPool` 两个清晰命名的工厂方法，并把原 `newWorkerPool` 标记 `@Deprecated`，加上详细 javadoc 说明 shutdown hook 累积风险，引导调用方迁移。
2. 全局静态 `WORKER_POOL`、`DELETE_WORKER_POOL` 以及 `S3FileIO`/`HadoopFileIO` 中"随 FileIO 生命周期存在"的删除池，这些是真正的长生命周期池，迁移到 `newExitingWorkerPool`，保留 shutdown hook 兜底。
3. 其余按查询/任务/commit 粒度创建的池子（Flink source/sink、Spark 表迁移、MR InputFormat、Kafka Connect Coordinator、benchmark、BaseDistributedDataScan 的 monitor 池、TableMigrationUtil 的 migration service 等）属于短生命周期，迁移到 `newFixedThreadPool`，由调用方负责 `shutdown()`。
4. 顺手补齐 `IcebergCommitter#close()` 中缺失的 `workerPool.shutdown()`，避免使用 `newFixedThreadPool` 后线程泄漏。
5. 在 `TableMigrationUtil.listPartition`、`SparkTableUtil.importSparkTable/importSparkPartitions` 的 javadoc 中明确"传入的 ExecutorService 会被本方法内部 shutdown"的契约，让调用方清楚责任边界。

## 修改详情

### `core/src/main/java/org/apache/iceberg/util/ThreadPools.java`（修改，+47 行）

**修改目的**：拆分 `newWorkerPool` 的双重语义，新增两个清晰命名的方法。

**工作逻辑**：

- 静态全局池 `WORKER_POOL`、`DELETE_WORKER_POOL` 从 `newWorkerPool(...)` 改为 `newExitingWorkerPool(...)`，行为不变（仍是 exiting pool）。
- 原 `newWorkerPool(String)`、`newWorkerPool(String, int)` 标记 `@Deprecated`（will be removed in 2.0.0），javadoc 详细说明：
  - 该方法会通过 `MoreExecutors.getExitingExecutorService` 注册 JVM shutdown hook；
  - 即使显式 `shutdown()` 也不会移除 shutdown hook，反复调用短生命周期池会导致 hook 累积；
  - 引导长生命周期池用 `newExitingWorkerPool`、短生命周期池用 `newFixedThreadPool`。
  - 两个旧方法体改为委托 `newExitingWorkerPool`，保持向后兼容。
- 新增 `newExitingWorkerPool(String namePrefix, int poolSize)`：javadoc 说明"适用于长生命周期的池，会在 JVM 退出时自动清理"；内部 `MoreExecutors.getExitingExecutorService((ThreadPoolExecutor) newFixedThreadPool(namePrefix, poolSize))`，即先建固定池再加 exiting 包装。
- 新增 `newFixedThreadPool(String namePrefix, int poolSize)`：javadoc 简短说明"创建使用 daemon 线程的固定大小池"；内部直接 `Executors.newFixedThreadPool(poolSize, newDaemonThreadFactory(namePrefix))`，无 shutdown hook。

### `core/src/main/java/org/apache/iceberg/BaseDistributedDataScan.java`（修改，+8 行）

**修改目的**：把 `newMonitorPool()` 创建的"远程规划监控池"从 `newWorkerPool` 切到 `newFixedThreadPool`，并在 javadoc 中提示调用方负责关闭。

**工作逻辑**：监控池是按 scan 粒度创建的短生命周期池，调用方（`BaseDistributedDataScan` 内部）会自行 `shutdown()`，不需要 JVM shutdown hook 兜底。注释从单行改为 javadoc 块，明确"Callers are responsible for shutting down the returned executor service when it is no longer needed"。

### `core/src/main/java/org/apache/iceberg/hadoop/HadoopFileIO.java`（修改，+1 行）

**修改目的**：`HadoopFileIO` 的删除执行器服务从 `newWorkerPool` 切到 `newExitingWorkerPool`。

**工作逻辑**：`HadoopFileIO` 的 `executorService` 是随 FileIO 实例缓存的，而 FileIO 实例通常与表/引擎生命周期绑定，属于长生命周期池。保留 shutdown hook 可在 JVM 退出时兜底清理，避免 FileIO 未显式关闭时线程泄漏。

### `aws/src/main/java/org/apache/iceberg/aws/s3/S3FileIO.java`（修改，+1 行）

**修改目的**：与 `HadoopFileIO` 同理，`S3FileIO` 的删除执行器从 `newWorkerPool` 切到 `newExitingWorkerPool`。

**工作逻辑**：`S3FileIO` 的 `executorService` 同样是长生命周期（按 `deleteThreads()` 配置），保留 exiting 行为。

### `core/src/jmh/java/org/apache/iceberg/metrics/CountersBenchmark.java`（修改，+1 行）

**修改目的**：benchmark 中临时创建的池子切到 `newFixedThreadPool`。

**工作逻辑**：benchmark 会在 try-with-resources 风格中自行关闭池子，不需要 shutdown hook。

### `data/src/main/java/org/apache/iceberg/data/TableMigrationUtil.java`（修改，+13 行）

**修改目的**：表迁移用的 `migrationService` 切到 `newFixedThreadPool`，并完善 `listPartition`/`migrationService` 的 javadoc。

**工作逻辑**：

- `migrationService(int parallelism)` 返回的池子由调用方持有并在迁移结束后关闭，属于短生命周期，切到 `newFixedThreadPool`。
- `listPartition` 的 `parallelism` 和 `service` 参数 javadoc 补充说明：若 `service` 为 null 则在当前线程执行；若非 null 则该方法内部会负责 `shutdown`。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/IcebergCommitter.java`（修改，+2 行）

**修改目的**：Flink sink committer 的 worker 池切到 `newFixedThreadPool`，并补齐 `close()` 中的 `workerPool.shutdown()`。

**工作逻辑**：

- 构造函数中 `ThreadPools.newWorkerPool(...)` 改为 `newFixedThreadPool(...)`，因为 committer 是按 sink 子任务粒度创建、随 committer 生命周期关闭的，不需要 JVM shutdown hook。
- 关键修复：`close()` 方法原本只关闭 `tableLoader`，未关闭 `workerPool`，这次补上 `workerPool.shutdown()`。这是之前 `newWorkerPool`（带 shutdown hook）掩盖的 bug——切到不带 shutdown hook 的 `newFixedThreadPool` 后必须显式关闭，否则线程泄漏。注释中 worker 池命名带 `table.name()` + `sinkId`，本就会随每次 sink 创建新池，不补 shutdown 会累积。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/IcebergFilesCommitter.java`（修改，+1 行）

**修改目的**：旧版 Flink sink committer 的 worker 池切到 `newFixedThreadPool`。

**工作逻辑**：与 `IcebergCommitter` 同理，按 operator ID 命名的池子随 operator 生命周期管理。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/source/FlinkInputFormat.java`（修改，+1 行）

**修改目的**：Flink source input format 的 plan worker 池切到 `newFixedThreadPool`。

**工作逻辑**：池子在 `configure` 中创建，紧跟着 try-with-resources 风格使用并关闭，是短生命周期。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/source/IcebergSource.java`（修改，+1 行）

**修改目的**：新版 Flink `IcebergSource` 的 plan worker 池切到 `newFixedThreadPool`。

**工作逻辑**：同上，按 plan 粒度创建并关闭。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/source/StreamingMonitorFunction.java`（修改，+1 行）

**修改目的**：流式 source monitor 的 worker 池切到 `newFixedThreadPool`。

**工作逻辑**：随 operator 生命周期管理。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/source/enumerator/ContinuousSplitPlannerImpl.java`（修改，+1 行）

**修改目的**：连续 split planner 的非共享 worker 池切到 `newFixedThreadPool`。

**工作逻辑**：当 `isSharedPool=false` 时按 enumerator 粒度创建独立池，属短生命周期。

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/channel/Coordinator.java`（修改，+1 行）

**修改目的**：Kafka Connect 的 commit 执行器切到 `newFixedThreadPool`。

**工作逻辑**：`Coordinator` 随 connect 任务生命周期管理，commit 池属短生命周期。

### `mr/src/main/java/org/apache/iceberg/mr/mapreduce/IcebergInputFormat.java`（修改，+1 行）

**修改目的**：MR InputFormat 的 plan worker 池切到 `newFixedThreadPool`。

**工作逻辑**：按作业切片规划粒度创建并关闭。

### `mr/src/test/java/org/apache/iceberg/mr/TestIcebergInputFormats.java`（修改，+4 行）

**修改目的**：测试中创建的两个临时 worker 池切到 `newFixedThreadPool`。

**工作逻辑**：测试在 try 块中使用并关闭池子。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkTableUtil.java`（修改，+6 行）

**修改目的**：完善 `importSparkTable`/`importSparkPartitions` 的 javadoc，明确传入的 `service` 参数的生命周期契约。

**工作逻辑**：三处 javadoc 把 `@param service executor service to use for file reading` 扩展为说明："If null, file reading will be performed on the current thread. If non-null, the provided ExecutorService will be shutdown within this method after file reading is complete."（其中一处因笔误多了一个 `*`）。这与 `TableMigrationUtil.listPartition` 的契约对齐——调用方传入的池子会被方法内部 shutdown，调用方无需（也不应）再自行关闭。

## 小结

- **成效**：通过拆分 `ThreadPools` 工厂方法，把"是否注册 JVM shutdown hook"这一隐式行为显式化，调用方可根据池子生命周期选择 `newExitingWorkerPool`（长生命周期，JVM 退出兜底）或 `newFixedThreadPool`（短生命周期，自行管理）。同时修正了 `IcebergCommitter#close()` 漏关 worker 池的 bug，避免了 Flink sink 长跑场景下的线程泄漏。原 `newWorkerPool` 保留为 `@Deprecated` 委托，向后兼容。
- **影响范围**：跨 `core`、`aws`、`data`、`flink/v1.20`、`kafka-connect`、`mr`、`spark/v3.5` 多个模块，但每个文件改动都很小（多为单行替换 + javadoc 补充）。行为上：长生命周期池（FileIO 删除池、全局 worker 池）保持原 exiting 行为不变；短生命周期池不再注册 shutdown hook，需调用方显式 shutdown（已在 `IcebergCommitter` 中补齐）。
- **回迁到 1.4.x 的注意事项**：本提交是 1.4.x 回迁价值较高的修复——`IcebergCommitter#close()` 漏 `workerPool.shutdown()` 的 bug 在 1.4.x 上同样存在，回迁此 PR 可顺带修复。回迁时需注意：
  1. 1.4.x 分支的 Flink 模块路径可能是 `flink/v1.19` 或 `flink/v1.20`，需对应调整；
  2. 1.4.x 上若已有其他地方依赖 `newWorkerPool` 注册 shutdown hook 的副作用（例如某处创建池子后既不显式 shutdown 又指望 JVM 退出兜底），切到 `newFixedThreadPool` 后会泄漏，需逐处确认调用方是否正确关闭；
  3. `IcebergCommitter.close()` 补 `workerPool.shutdown()` 是必须的，不能漏；
  4. `@Deprecated` 注解和 2.0.0 移除计划是 main 分支的路线图，回迁时保留即可，不影响 1.4.x 使用。
