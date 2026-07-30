# 提交 0051：Flink:backport PR to 1.16 #7360: Implement data statistics coordinator to aggregate data statistics from operator subtasks (#8747)

## 提交信息

- **序号**：0051 / 4088
- **哈希**：3e522e84f566ee1f195009decffc4b6b235fe262
- **短哈希**：3e522e84f
- **日期**：2023-10-15 15:57:07 -0700
- **作者**：gangy
- **提交说明**：Flink:backport PR to 1.16 #7360: Implement data statistics coordinator to aggregate data statistics from operator subtasks (#8747)
- **PR/Issue**：#8747（backport 自 #7360）

## 总体目的

本提交是上游 PR #7360 向 Flink 1.16 集成模块的 backport，目的是为 Iceberg 的 Flink sink 引入"数据统计协调器（Data Statistics Coordinator）"，从而支持下游基于全局数据分布的负载均衡写入。

在 Flink sink 场景中，"局部数据统计 + 全局聚合 + 自定义数据分布"是一类常见的优化手段：每个算子子任务（subtask）在本地收集数据分布统计信息（如按 key 的计数），但这些局部视图无法反映整体分布；只有把所有子任务的统计聚合起来，才能让自定义分区器（custom partitioner）按照全局数据分布来下推记录，避免数据倾斜并改善聚簇效果。本提交正是补齐这条链路上"协调器聚合"这一缺失环节：在 JobManager 侧运行一个 `OperatorCoordinator`，接收所有 subtask 上报的 `DataStatisticsEvent`，合并成 `AggregatedStatistics`，再回推给所有 subtask 用于刷新其 `globalStatistics`。

背景与动机上，这是 Iceberg Flink sink shuffle 系列功能的关键一环，配合 `DataStatisticsOperator` 实现"在 checkpoint 屏障对齐时刷新全局统计"，从而保证所有 subtask 在同一 checkpoint 上看到一致的全局视图。这对写入性能与数据布局质量都有直接收益，并为后续的 cluster-aware 写入奠定基础。

## 如何达成设计目的

整体设计采用 Flink `OperatorCoordinator` 机制，由四块组成：

1. **DataStatisticsCoordinator**：实现 `OperatorCoordinator` 接口，运行在协调器线程（单线程 `ExecutorService` + `CoordinatorExecutorThreadFactory`），所有外部回调（事件处理、checkpoint、subtask ready/reset/fail）都被 dispatch 到该线程串行执行，避免并发问题。
2. **AggregatedStatisticsTracker**：在 coordinator 内部维护"进行中的聚合状态"与"已上报 subtask 集合"，按 checkpoint 推进；当所有 subtask 都上报或达到阈值时产出 `AggregatedStatistics`。
3. **DataStatisticsCoordinatorProvider**：作为 `RecreateOnResetOperatorCoordinator.Provider`，供 JobGraph 创建/重建 coordinator 实例。
4. **DataStatisticsEvent / DataStatisticsUtil**：把事件改为传输序列化后的 `byte[]`（避免直接持有 `DataStatistics` 对象在 RPC 链路上不可序列化的问题），并补充序列化/反序列化工具方法（含 AggregatedStatistics 的 checkpoint 序列化）。

数据流：subtask 在 `snapshotState` 时通过 `operatorEventGateway.sendEventToCoordinator` 上报本地统计 → coordinator 的 `handleEventFromOperator` 在协调器线程内调用 `AggregatedStatisticsTracker.updateAndCheckCompletion` → 若完成则 `sendDataStatisticsToSubtasks` 向每个 subtask 的 `SubtaskGateway` 推送聚合事件 → subtask 在 `handleOperatorEvent` 中反序列化为 `globalStatistics`，并在 checkpoint 时通过 `output.collect` 把 `DataStatisticsOrRecord` 下发给下游 partitioner，使其在同一 barrier 刷新。

## 修改详情

### `flink/v1.16/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/DataStatisticsCoordinator.java`

**修改目的**：新增协调器实现，作为整个特性的核心。

**工作逻辑**：

- 泛型 `<D extends DataStatistics<D, S>, S>` 兼容不同统计实现（如 `MapDataStatistics`）。
- 持有 `coordinatorExecutor`（单线程）、`subtaskGateways`（管理每个 subtask 各 attempt 的 `SubtaskGateway`）、`aggregatedStatisticsTracker`（聚合跟踪器）和 `completedStatistics`（最近一次完成的聚合结果）。
- `callInCoordinatorThread` / `runInCoordinatorThread` 两套方法分别用于"阻塞等待执行"和"异步派发执行"，所有外部入口都通过它们进入协调器线程，从而保证线程安全。`runInCoordinatorThread(ThrowingRunnable, String)` 在异常时会调用 `operatorCoordinatorContext.failJob(t)` 触发作业 failover。
- `handleEventFromOperator`：将 `OperatorEvent` 强转为 `DataStatisticsEvent`，转交 `handleDataStatisticRequest`。
- `handleDataStatisticRequest`：调用 `aggregatedStatisticsTracker.updateAndCheckCompletion(subtask, event)`；若返回非空，则把 `completedStatistics` 更新为该结果，并调用 `sendDataStatisticsToSubtasks` 向所有 subtask 广播。
- `sendDataStatisticsToSubtasks`：在协调器线程内构造 `DataStatisticsEvent.create(checkpointId, globalDataStatistics, statisticsSerializer)`，循环 `parallelism` 次通过 `subtaskGateways.getSubtaskGateway(i).sendEvent` 下发。
- `checkpointCoordinator`：把 `completedStatistics` 用 `DataStatisticsUtil.serializeAggregatedStatistics` 序列化为 `byte[]` 并完成 `CompletableFuture<byte[]>`，从而把协调器状态纳入 checkpoint。`notifyCheckpointComplete` 留空。
- `resetToCheckpoint`：仅在未 start 时允许调用；若 `checkpointData != null`，反序列化恢复 `completedStatistics`。
- `subtaskReset` / `executionAttemptFailed` / `executionAttemptReady`：维护 `SubtaskGateways` 的注册/注销/重置，确保只有 ready 的 subtask 才会被下发事件。
- 内部类 `SubtaskGateways`：`Map<Integer, SubtaskGateway>[] gateways`，按 subtaskIndex 索引，每个槽位是一个 `attemptNumber -> gateway` 的 Map；`getSubtaskGateway` 用 `Iterables.getOnlyElement` 断言同一 subtask 同时只有一个活跃 attempt。
- 内部类 `CoordinatorExecutorThreadFactory`：实现 `ThreadFactory` 与 `UncaughtExceptionHandler`，提供 `isCurrentThreadCoordinatorThread()` 判定当前线程身份，便于在 `callInCoordinatorThread` 中决定是否直接执行还是提交任务。

### `flink/v1.16/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/AggregatedStatisticsTracker.java`

**修改目的**：在协调器内部按 checkpoint 跟踪聚合进度，决定何时产出完成结果。

**工作逻辑**：

- 字段：`operatorName`、`statisticsSerializer`、`parallelism`、`inProgressSubtaskSet`（已上报的 subtask 集合）、`inProgressStatistics`（当前进行中的聚合对象）。
- 常量 `ACCEPT_PARTIAL_AGGR_THRESHOLD = 90`：当新的 checkpoint 事件到达而旧 checkpoint 未收集满时，若旧 checkpoint 已收到的 subtask 比例 ≥ 90%，则视为"部分聚合可接受"，把旧 `inProgressStatistics` 当作完成结果返回；否则丢弃。
- `updateAndCheckCompletion` 主体逻辑：
  1. 若 `inProgressStatistics` 对应的 checkpointId 比当前事件的新，则忽略（旧事件迟到）。
  2. 若 `inProgressStatistics` 的 checkpointId 比当前事件的旧，则按阈值判定是否把旧统计作为完成结果返回，并清空进行中状态。
  3. 若 `inProgressStatistics == null`，则以当前 checkpointId 新建一个空的 `AggregatedStatistics`。
  4. `inProgressSubtaskSet.add(subtask)`：若添加成功（非重复），则反序列化事件负载并调用 `inProgressStatistics.mergeDataStatistic` 合并；重复则仅 debug 日志。
  5. 若 `inProgressSubtaskSet.size() == parallelism`，则把 `inProgressStatistics` 作为完成结果，并立刻用 `checkpointId + 1` 新建下一轮的空 `AggregatedStatistics`，清空 subtask 集合。
- 这样设计保证了：在所有 subtask 都上报的情况下立即产出；在 checkpoint 推进时通过阈值策略避免长时间阻塞，兼顾准确性与可用性。

### `flink/v1.16/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/AggregatedStatistics.java`

**修改目的**：表示一次 checkpoint 对应的"全局聚合统计"结果。

**工作逻辑**：

- 字段：`checkpointId` 与 `dataStatistics`（聚合后的 `DataStatistics` 实例）。
- 两个构造器：一个仅给 checkpointId，用 `statisticsSerializer.createInstance()` 创建空统计；另一个直接传入已聚合的 `DataStatistics`。
- `mergeDataStatistic`：校验事件 checkpointId 与自身一致后调用 `dataStatistics.merge(eventDataStatistics)` 完成合并。
- 实现 `Serializable`，便于在协调器状态中持久化。

### `flink/v1.16/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/DataStatisticsCoordinatorProvider.java`

**修改目的**：为 JobGraph 提供 coordinator 工厂，让 Flink 框架在作业提交与 reset 时创建/重建 coordinator。

**工作逻辑**：继承 `RecreateOnResetOperatorCoordinator.Provider`（即在 subtask reset 时直接重建 coordinator 实例），持有 `operatorName` 与 `statisticsSerializer`；`getCoordinator(Context)` 直接 `new DataStatisticsCoordinator<>(operatorName, context, statisticsSerializer)`。

### `flink/v1.16/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/DataStatisticsUtil.java`

**修改目的**：统一负责 `DataStatistics` 与 `AggregatedStatistics` 的序列化/反序列化。

**工作逻辑**：

- `serializeDataStatistics` / `deserializeDataStatistics`：使用 Flink 的 `TypeSerializer` 配合 `DataOutputSerializer` / `DataInputDeserializer` 完成 `DataStatistics` 与 `byte[]` 之间的转换，用于事件负载。
- `serializeAggregatedStatistics` / `deserializeAggregatedStatistics`：用于 coordinator 的 checkpoint 状态。格式为 `ObjectOutputStream` 写 `long checkpointId` + `int statisticsBytesLength` + 统计字节流；反序列化对称读回并构造 `AggregatedStatistics`。这样把 checkpointId 与聚合统计一起持久化，便于恢复。

### `flink/v1.16/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/DataStatisticsEvent.java`

**修改目的**：把事件负载从直接持有 `DataStatistics` 改为持有 `byte[]`，以适配 Flink OperatorEvent 的序列化要求并避免直接传输对象图。

**工作逻辑**：移除 `dataStatistics` 字段与 `toString`，新增 `statisticsBytes` 私有字段；提供静态 `create(checkpointId, dataStatistics, statisticsSerializer)` 工厂方法，内部调用 `DataStatisticsUtil.serializeDataStatistics` 序列化；`statisticsBytes()` 返回字节数组。

### `flink/v1.16/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/DataStatisticsOperator.java`

**修改目的**：让算子侧配合新协议——上报序列化后的统计、在收到协调器事件时反序列化、并在 checkpoint 时把全局统计下推给下游 partitioner。

**工作逻辑**：

- 新增 `operatorName` 字段并加到构造器与日志中，便于多 operator 场景区分。
- `handleOperatorEvent`：把收到的 `DataStatisticsEvent` 通过 `DataStatisticsUtil.deserializeDataStatistics` 反序列化为 `globalStatistics`，并通过 `output.collect` 把 `DataStatisticsOrRecord.fromDataStatistics(globalStatistics)` 发往下游。
- `snapshotState`：在 checkpoint 屏障到来时，若 `globalStatistics` 非空则先 `output.collect` 一次（让下游 partitioner 在同一 barrier 上刷新统计）；只有 subtask 0 把 `globalStatistics` 写入 `UnionListState`；调用 `operatorEventGateway.sendEventToCoordinator(DataStatisticsEvent.create(checkpointId, localStatistics, statisticsSerializer))` 把本地统计上报协调器；随后重建空的 `localStatistics`。这条逻辑实现了"在同一 checkpoint 上对齐全局统计刷新"的关键语义。

### `flink/v1.16/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/DataStatisticsOrRecord.java`

**修改目的**：小幅修订校验错误信息文案，使其更简洁。

### 测试文件

**修改目的**：覆盖新协调器、tracker、provider 的行为，并更新 `TestDataStatisticsOperator` 适配新的事件协议与 BinaryRowData 比较。

**工作逻辑**：

- `TestAggregatedStatistics`：测试 `mergeDataStatistic` 在 checkpointId 不匹配时的断言与正常合并行为。
- `TestAggregatedStatisticsTracker`：覆盖完整收集、部分收集、重复上报、checkpoint 跳变（含 90% 阈值）、跨 checkpoint 推进等场景。
- `TestDataStatisticsCoordinator`：使用 `ComponentMainThreadExecutor` 模拟协调器线程，验证事件处理、广播下发、checkpoint 序列化与恢复、subtask ready/reset/fail 等流程。
- `TestDataStatisticsCoordinatorProvider`：验证 provider 能正确创建 coordinator 并支持 reset 重建。
- `TestDataStatisticsOperator`：将事件构造改为 `DataStatisticsEvent.create`，使用 `BinaryRowData` 替代 `GenericRowData` 做比较（因为反序列化后回来的是 `BinaryRowData`），同时为 `DataStatisticsOperator` 新增 `operatorName` 参数。

## 小结

本提交为 Iceberg Flink sink 1.16 模块补齐了"数据统计协调器"这一关键环节，使各 subtask 的局部统计能在 JobManager 侧按 checkpoint 聚合并回推，从而支持下游自定义分区器基于全局数据分布进行负载均衡与数据聚簇，是 Iceberg 在 Flink 侧走向 cluster-aware 写入的基础设施。
