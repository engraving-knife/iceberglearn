# 提交 0052：Flink:backport PR to 1.15 #7360: Implement data statistics coordinator to aggregate data statistics from operator subtasks (#8749)

## 提交信息

- **序号**：0052 / 4088
- **哈希**：6d3b0b78420a7e1104e4626799ad0c414146a9fb
- **短哈希**：6d3b0b784
- **日期**：2023-10-15 15:59:54 -0700
- **作者**：gangy
- **提交说明**：Flink:backport PR to 1.15 #7360: Implement data statistics coordinator to aggregate data statistics from operator subtasks (#8749)
- **PR/Issue**：#8749（backport 自 #7360）

## 总体目的

本提交是上游 PR #7360 向 Flink 1.15 集成模块的 backport，与同一天提交的 0051（1.16 版本）功能等价：为 Iceberg Flink sink 引入"数据统计协调器（Data Statistics Coordinator）"，在 JobManager 侧聚合各 subtask 上报的局部统计，并把全局聚合结果回推给所有 subtask，从而让下游自定义分区器基于全局数据分布进行负载均衡与聚簇写入。

背景与动机与 0051 完全一致：每个 `DataStatisticsOperator` 子任务只能看到局部数据分布，无法独立决策合理的分发策略；只有协调器把所有 subtask 的统计合并后回推，才能保证所有 subtask 在同一 checkpoint 上看到一致的全局视图，避免数据倾斜。Iceberg 同时维护 1.15 与 1.16 两条 Flink 集成分支，因此需要分别 backport。两个版本的差异主要源于 Flink 1.15 与 1.16 的 `OperatorCoordinator` API 不同（1.16 引入了 `attemptNumber` 维度与 `executionAttemptReady/Failed` 接口），所以 1.15 版本在协调器实现上做了对应的适配简化。

## 如何达成设计目的

整体设计与 0051 相同，由 `DataStatisticsCoordinator` + `AggregatedStatisticsTracker` + `DataStatisticsCoordinatorProvider` + `DataStatisticsEvent`/`DataStatisticsUtil` 四块组成。差异集中体现在 `DataStatisticsCoordinator`：

- 1.15 版本的 `OperatorCoordinator` 接口里 `handleEventFromOperator`、`subtaskReady`、`subtaskFailed` 都不带 `attemptNumber`，因此协调器内部不再需要按 attempt 维度管理 gateway，而是直接用一个 `SubtaskGateway[]` 数组按 subtask 下标存取；
- 不再需要 0051 中的内部类 `SubtaskGateways`（用 `Map<Integer, SubtaskGateway>[]` 容纳多 attempt）；
- `subtaskReset` 在 1.15 上仅打日志即可（gateway 由框架在 subtaskReady 时重新设置），不需要主动清空；
- `subtaskFailed`（对应 1.16 的 `executionAttemptFailed`）将对应下标的 gateway 置 null；
- `subtaskReady`（对应 1.16 的 `executionAttemptReady`）将 gateway 写入对应下标。

其余文件（`AggregatedStatistics`、`AggregatedStatisticsTracker`、`DataStatisticsCoordinatorProvider`、`DataStatisticsEvent`、`DataStatisticsUtil`、`DataStatisticsOperator`、`DataStatisticsOrRecord`）与 0051 在内容上完全一致（经 diff 验证），仅所在路径由 `flink/v1.16/` 改为 `flink/v1.15/`。

## 修改详情

### `flink/v1.15/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/DataStatisticsCoordinator.java`

**修改目的**：新增适配 Flink 1.15 `OperatorCoordinator` API 的协调器实现。

**工作逻辑**（与 0051 的差异点）：

- 字段：`subtaskGateways` 直接声明为 `OperatorCoordinator.SubtaskGateway[]`，长度等于 `currentParallelism()`；不再有内部类 `SubtaskGateways`。
- 构造器：`this.subtaskGateways = new OperatorCoordinator.SubtaskGateway[operatorCoordinatorContext.currentParallelism()];`。
- `handleEventFromOperator(int subtask, OperatorEvent event)`：1.15 签名没有 `attemptNumber`，方法体内仍 `runInCoordinatorThread` 调用 `handleDataStatisticRequest`。
- `sendDataStatisticsToSubtasks`：循环 `subtaskGateways[i].sendEvent(dataStatisticsEvent)`，直接按下标取 gateway，无需 `getSubtaskGateway` 校验。
- `subtaskFailed(int subtask, @Nullable Throwable reason)`：1.15 旧接口，把 `subtaskGateways[subtask] = null`。
- `subtaskReset(int subtask, long checkpointId)`：仅打日志，不做 gateway 清理（1.15 由框架后续 `subtaskReady` 重新设置）。
- `subtaskReady(int subtask, SubtaskGateway gateway)`：1.15 旧接口，`subtaskGateways[subtask] = gateway`；不再有 `Preconditions.checkArgument(attemptNumber == gateway.getExecution().getAttemptNumber())` 的校验。
- 其余协调器线程模型（`callInCoordinatorThread` / `runInCoordinatorThread` / `CoordinatorExecutorThreadFactory`）、`handleDataStatisticRequest`、`checkpointCoordinator`、`resetToCheckpoint` 等与 0051 完全一致。

### `flink/v1.15/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/AggregatedStatisticsTracker.java`

**修改目的**：与 0051 完全相同（按 checkpoint 跟踪聚合进度，含 90% 部分聚合阈值）。

**工作逻辑**：见 0051 同名文件分析，内容逐字一致。

### `flink/v1.15/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/AggregatedStatistics.java`

**修改目的**：与 0051 完全相同（表示某 checkpoint 的全局聚合结果，提供 `mergeDataStatistic`）。

### `flink/v1.15/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/DataStatisticsCoordinatorProvider.java`

**修改目的**：与 0051 完全相同（继承 `RecreateOnResetOperatorCoordinator.Provider`，提供 coordinator 工厂）。

### `flink/v1.15/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/DataStatisticsUtil.java`

**修改目的**：与 0051 完全相同（`DataStatistics` 与 `AggregatedStatistics` 的序列化/反序列化工具）。

### `flink/v1.15/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/DataStatisticsEvent.java`

**修改目的**：与 0051 完全相同（事件负载改为 `byte[]`，提供 `create` 工厂方法）。

### `flink/v1.15/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/DataStatisticsOperator.java`

**修改目的**：与 0051 完全相同（配合新协议上报序列化统计、收到协调器事件反序列化、checkpoint 时下推全局统计给下游 partitioner）。

### `flink/v1.15/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/DataStatisticsOrRecord.java`

**修改目的**：与 0051 完全相同（小幅修订校验错误信息文案）。

### 测试文件

**修改目的**：覆盖协调器、tracker、provider 的行为，并更新 `TestDataStatisticsOperator` 适配新事件协议。

**工作逻辑**：

- `TestAggregatedStatistics`、`TestAggregatedStatisticsTracker`、`TestDataStatisticsCoordinatorProvider`、`TestDataStatisticsOperator` 与 0051 同名文件内容一致。
- `TestDataStatisticsCoordinator` 与 0051 的差异点对应 1.15 API：
  - 调用 `handleEventFromOperator(int subtask, OperatorEvent event)`（不带 attemptNumber）；
  - 调用 `subtaskFailed(int subtask, Throwable reason)`（对应 1.16 的 `executionAttemptFailed`）；
  - 调用 `subtaskReady(int subtask, SubtaskGateway gateway)` 并使用 `receivingTasks.createGatewayForSubtask(i)`（不带 attemptNumber），对应 1.16 的 `executionAttemptReady` + `createGatewayForSubtask(i, 0)`。
  - 这些差异精确反映了 1.15 / 1.16 的 API 演进。

## 小结

本提交把数据统计协调器能力同步到 Flink 1.15 集成模块，与 0051 共同覆盖 Iceberg 在 1.15/1.16 两条分支上的 cluster-aware 写入基础设施；除协调器因 Flink API 差异做适配简化外，其余实现与 0051 完全一致。
