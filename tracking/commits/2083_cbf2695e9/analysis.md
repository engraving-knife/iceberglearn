# 提交 2083：Flink: Maintenance - RewriteDataFiles

## 提交信息

- **序号**：2083 / 4088
- **哈希**：cbf2695e951847eca46ba732cd2135876b483cdb
- **短哈希**：cbf2695e9
- **日期**：2025-05-06 10:05:31 +0200
- **作者**：pvary
- **提交说明**：Flink: Maintenance - RewriteDataFiles (#11497)
- **PR/Issue**：#11497

## 总体目的

Iceberg 的 Flink 集成此前已有维护任务（Maintenance）框架的雏形，包括 `ExpireSnapshots`（快照过期）等任务，以及通用的 `TriggerManager`（触发管理器）和 `MaintenanceTaskBuilder`（任务构建器基类）。这些任务以 Flink DataStream 流式作业的方式持续运行，由 `Trigger` 事件驱动单次执行。

数据文件压缩（RewriteDataFiles / Compaction）是 Iceberg 表维护的核心操作之一——将大量小文件合并为大文件以提升查询性能。此前 Spark 和其他引擎已有 `RewriteDataFiles` Action 实现，但 Flink 缺少基于流式维护框架的数据文件重写能力。本提交为 Flink 维护框架新增 `RewriteDataFiles` 任务，使 Flink 用户能够像 `ExpireSnapshots` 一样，通过流式作业持续自动地压缩数据文件。

该实现复用了 Iceberg Core 的 `BinPackRewriteFilePlanner` 和 `SizeBasedFileRewritePlanner` 等规划逻辑，将其包装为 Flink 算子，构建一个完整的 Plan → Rewrite → Commit → Aggregate 流水线，支持部分进度提交（partial progress）、并行重写、错误处理和监控指标。

## 如何达成设计目的

整体设计采用 Flink DataStream 流水线模式，每个 `Trigger` 事件触发一次完整的重写迭代。关键组件及协作关系如下：

- **`RewriteDataFiles.Builder`**（API 层入口）：继承 `MaintenanceTaskBuilder`，配置重写参数（目标文件大小、最小/最大文件大小、部分进度等），通过 `append()` 方法构建 DataStream 流水线。
- **`DataFileRewritePlanner`**（规划算子）：接收 `Trigger`，加载表快照，使用 `BinPackRewriteFilePlanner` 规划需要重写的文件分组（`PlannedGroup`），输出到下游。支持 `maxRewriteBytes` 限制单次重写字节量。
- **`DataFileRewriteRunner`**（执行算子）：接收 `PlannedGroup`，实际读取数据文件并重写为新文件，输出 `ExecutedGroup`。通过 `rebalance` 分发到并行度 >1 的算子实现并行重写。
- **`DataFileRewriteCommitter`**（提交算子）：接收 `ExecutedGroup`，使用 `RewriteDataFilesCommitManager` 提交重写结果到表元数据，支持部分进度提交（partial progress）。输出 `Trigger`（作为完成的信号）。
- **`TaskResultAggregator`**（聚合算子）：双流算子，输入 1 是原始 Trigger 流（提供 start epoch 和完成水印），输入 2 是错误侧输出流，聚合后输出 `TaskResult`。
- **`TableMaintenanceMetrics`**：提供监控指标（错误计数器等）。
- **`LogUtil`**：统一的日志格式化工具。

流水线流向：`Trigger → Planner (forceNonParallel) → rebalance → Runner (parallel) → Committer (forceNonParallel) → Aggregator (forceNonParallel) → TaskResult`。

## 修改详情

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/RewriteDataFiles.java` (新增, +234/-0 lines)

**修改目的**：提供 RewriteDataFiles 任务的 API 入口和 Builder。

**工作逻辑**：
- 定义任务名常量（PLANNER_TASK_NAME、REWRITE_TASK_NAME、COMMIT_TASK_NAME、AGGREGATOR_TASK_NAME）。
- `Builder` 继承 `MaintenanceTaskBuilder`，提供配置方法：`partialProgressEnabled`、`partialProgressMaxCommits`、`maxRewriteBytes`、`targetFileSizeBytes`、`minFileSizeBytes`、`maxFileSizeBytes`、`minInputFiles`、`deleteFileThreshold`、`rewriteAll`、`maxFileGroupSizeBytes`。这些方法将参数写入 `rewriteOptions` Map，对应 `SizeBasedFileRewritePlanner` 和 `BinPackRewriteFilePlanner` 的配置键。
- `append(DataStream<Trigger> trigger)` 方法构建流水线：
  1. Planner 算子：`forceNonParallel`，输出 `PlannedGroup`。
  2. Runner 算子：`rebalance` 后 `setParallelism(parallelism())`，输出 `ExecutedGroup`。
  3. Committer 算子：`forceNonParallel`，通过 `transform` 添加，输出 `Trigger`。
  4. Aggregator 算子：将原始 trigger 与 committer 输出 union 后，connect 错误侧输出流，`forceNonParallel`，输出 `TaskResult`。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/DataFileRewritePlanner.java` (新增, +206/-0 lines)

**修改目的**：规划需要重写的文件分组。

**工作逻辑**：
- 继承 `ProcessFunction<Trigger, PlannedGroup>`。
- `open()` 时打开 tableLoader 并注册错误计数器。
- `processElement()` 时：加载 `SerializableTable` 副本；若表无快照则跳过；使用 `BinPackRewriteFilePlanner` 配置 `rewriteOptions`；扫描数据文件和删除文件构建 `RewriteFileGroup`；按 `maxRewriteBytes` 限制切分分组；将分组分为 `partialProgressMaxCommits` 个批次输出为 `PlannedGroup`（包含 group id 和 epoch）。
- 定义内部类 `PlannedGroup`（含 rewriteGroupId、epoch、RewriteFileGroup）。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/DataFileRewriteRunner.java` (新增, +253/-0 lines)

**修改目的**：执行实际的数据文件重写。

**工作逻辑**：
- 继承 `ProcessFunction<PlannedGroup, ExecutedGroup>`。
- `open()` 时加载表并注册指标。
- `processElement()` 时：使用 `BinPackRewriteFilePlanner` 重新规划分组（确保删除文件是最新的）；执行重写（读取旧文件、写入新文件）；输出 `ExecutedGroup`（含 rewriteGroupId、epoch、重写结果列表）。错误通过侧输出流发出。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/DataFileRewriteCommitter.java` (新增, +199/-0 lines)

**修改目的**：提交重写结果到表元数据。

**工作逻辑**：
- 继承 `AbstractStreamOperator` 实现 `OneInputStreamOperator<ExecutedGroup, Trigger>`。
- `open()` 时加载表、创建 `RewriteDataFilesCommitManager`、注册指标。
- `processElement()` 时：收集同一批次的执行结果；当收集到足够数量（`partialProgressMaxCommits`）时提交；使用 commit manager 替换旧文件为新文件；提交后输出 `Trigger`（作为完成信号）并前推 watermark。错误通过侧输出流发出。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/TaskResultAggregator.java` (新增, +101/-0 lines)

**修改目的**：聚合任务执行结果，处理错误并输出 TaskResult。

**工作逻辑**：
- 实现 `TwoInputStreamOperator<Trigger, Trigger, TaskResult>`。
- 输入 1（Trigger 流）：提供任务的 start epoch，并在收到 watermark 时标记任务完成。
- 输入 2（错误侧输出流）：收集执行过程中的错误。
- 在任务完成时输出 `TaskResult`（包含 epoch、成功/失败状态、错误信息列表）。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/TableMaintenanceMetrics.java` (新增, +30/-0 lines)

**修改目的**：提供维护任务的 Flink 指标注册工具。

**工作逻辑**：提供 `groupFor(runtimeContext, tableName, taskName, taskIndex)` 方法，返回按表名/任务名/任务索引组织的 `MetricGroup`；定义 `ERROR_COUNTER` 常量。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/LogUtil.java` (新增, +26/-0 lines)

**修改目的**：提供统一的日志消息前缀格式化工具。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/MaintenanceTaskBuilder.java` (修改, +2/-2 lines)

**修改目的**：调整 `append` 方法参数顺序，将 `tableName` 移到 `taskName` 之前。

**工作逻辑**：将 `append` 签名从 `(sourceStream, taskIndex, newTaskName, newTableName, ...)` 改为 `(sourceStream, newTableName, newTaskName, taskIndex, ...)`，使参数顺序更符合逻辑。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/TableMaintenance.java` (修改, +4/-3 lines)

**修改目的**：适配 `MaintenanceTaskBuilder.append` 参数顺序变更，并修复格式化。

**工作逻辑**：调用处参数顺序同步调整；`nameFor` 方法添加 `Locale.ROOT` 参数并使用 `%d` 格式化 taskIndex。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/ExpireSnapshots.java` (修改, +1/-1 lines)

**修改目的**：适配参数顺序变更。

### 其他算子修改（`DeleteFilesProcessor`、`ExpireSnapshotsProcessor`、`LockRemover`、`TriggerManager`，共约 90 行变更）

**修改目的**：统一日志格式、适配指标和错误处理改进。

### 测试文件（新增/修改，约 1700 行）

**新增测试**：
- `TestRewriteDataFiles.java`（+417）：端到端测试 RewriteDataFiles 任务。
- `OperatorTestBase.java`（+113）：算子测试基类。
- `RewriteUtil.java`（+83）：测试工具类。
- `TestDataFileRewriteCommitter.java`（+278）：测试提交算子。
- `TestDataFileRewritePlanner.java`（+193）：测试规划算子。
- `TestDataFileRewriteRunner.java`（+355）：测试执行算子。

**修改测试**：`MaintenanceTaskTestBase`、`TestExpireSnapshots`、`TestMaintenanceE2E`、`MaintenanceTaskInfraExtension`、`TestDeleteFilesProcessor` 适配参数变更和新功能。

## 总结

本提交为 Flink 维护框架新增数据文件重写（RewriteDataFiles / Compaction）任务，构建为 Flink DataStream 流水线：`Trigger → Planner → Runner (并行) → Committer → Aggregator → TaskResult`。复用 Iceberg Core 的 `BinPackRewriteFilePlanner` 规划逻辑，支持部分进度提交、并行重写、错误侧输出和监控指标。新增 4 个核心算子（Planner、Runner、Committer、Aggregator）和 API Builder，配套完善的单元测试和端到端测试，总计约 2587 行新增代码。同时调整了 `MaintenanceTaskBuilder` 的参数顺序以保持一致性。
