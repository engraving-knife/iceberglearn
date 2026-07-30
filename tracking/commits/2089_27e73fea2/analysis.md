# 提交 2089：Flink: Backport Maintenance - RewriteDataFiles to Flink 2.0

## 提交信息

- **序号**：2089 / 4088
- **哈希**：27e73fea23dcf39185da0dba2e3b9e3ef5f3254b
- **短哈希**：27e73fea2
- **日期**：2025-05-06 22:52:15 -0700
- **作者**：Gyula Fora <g_fora@apple.com>
- **提交说明**：Flink: Backport Maintenance - RewriteDataFiles to Flink 2.0\n\nBackports #11497
- **PR/Issue**：#11497（被回退的原始 PR）

## 总体目的

本次提交将 Flink 1.x 上已有的 Iceberg 表数据文件重写（RewriteDataFiles）维护任务流式实现，回退移植（backport）到 Flink 2.0 的新维护框架中。Iceberg 的 Flink 集成在 1.4.x 分支同时维护 Flink 1.x 与 Flink 2.0 两套源码（位于 `flink/v1.20/` 与 `flink/v2.0/`），二者 API 已经发生较大差异（例如 Flink 2.0 使用 `OpenContext`、`ManualSource` 等新 API），需要将原 `#11497` 引入的连续数据文件压缩（compaction）能力在 Flink 2.0 上重新实现并接入新的 `TableMaintenance` / `MaintenanceTaskBuilder` 框架。

该功能的核心价值在于：在不依赖外部 Spark/批作业的前提下，通过常驻的 Flink 流式作业持续监控 Iceberg 表的触发事件（`Trigger`），自动对小文件进行 bin-pack 重写并提交，从而控制表中数据文件的数量与体积，避免读放大。本次回退移植与 Flink 1.x 版本保持功能等价，但代码结构、Operator 命名、监控指标复用等方面统一到 Flink 2.0 的新风格。

由于只是把同一能力迁移到新的 Flink 运行时，并不改变 Iceberg 核心的重写逻辑（仍复用 `BinPackRewriteFilePlanner`、`RewriteDataFilesCommitManager`、`RowDataTaskWriterFactory` 等），因此实现风险较低，主要工作量在于流水线编排与错误/指标处理。

## 如何达成设计目的

整体设计沿用 Flink 维护框架的"触发 → 计划 → 执行 → 提交 → 聚合"五段式流水线，关键组件与协作关系如下：

1. **`RewriteDataFiles`（API Builder）**：入口构建器，继承 `MaintenanceTaskBuilder`。在 `append(DataStream<Trigger>)` 中按顺序串联各算子，并暴露 `partialProgressEnabled`、`maxRewriteBytes`、`targetFileSizeBytes` 等参数，最终把所有算子的侧输出错误流汇聚到 `TaskResultAggregator`。
2. **`DataFileRewritePlanner`**：单并行度算子，消费 `Trigger`，使用 `BinPackRewriteFilePlanner` 生成 `RewriteFileGroup`，按 `maxRewriteBytes` 过滤超大组，并按 `partialProgressMaxCommits` 计算 `groupsPerCommit`，输出 `PlannedGroup`。
3. **`DataFileRewriteRunner`**：可并行算子，对每个 `PlannedGroup` 用 `RowDataFileScanTaskReader` 读取（考虑 delete 文件），用 `RowDataTaskWriterFactory` 重写为新的数据文件，输出 `ExecutedGroup`；失败时调用 `writer.abort()` 并通过侧输出上报异常。
4. **`DataFileRewriteCommitter`**：单并行度算子，使用内部 `FlinkRewriteDataFilesCommitManager`（继承 `RewriteDataFilesCommitManager`）的 `CommitService` 累积提交，并在 Watermark 到达时关闭提交服务完成本次触发周期；同时维护新增/删除数据文件数量与体积的监控指标。
5. **`TaskResultAggregator`**：双输入算子，主输入收 `Trigger`（记录起始时间并作为结束信号），次输入收各算子的异常（通过公共 `ERROR_STREAM` 侧输出标签），在 Watermark 时汇总出 `TaskResult`。
6. **`TableMaintenanceMetrics` / `LogUtil`**：抽取公共的 metric group 构建逻辑（`groupFor(...)`）与日志前缀，供新旧算子复用，避免重复样板代码。
7. **既有算子改造**：`DeleteFilesProcessor`、`LockRemover`、`TriggerManager` 等改为调用 `TableMaintenanceMetrics.groupFor(...)` 来构建指标分组，统一参数顺序（`tableName, taskName, taskIndex`），`ExpireSnapshotsProcessor` 中 `ThreadPools.newWorkerPool` 改为 `newFixedThreadPool`，以适配 Flink 2.0 的线程池 API。

## 修改详情

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/RewriteDataFiles.java` (新增, +234/-0 lines)

**修改目的**：提供数据文件重写任务的入口 Builder，串联整条流水线。

**工作逻辑**：
`Builder` 持有 `partialProgressEnabled`、`partialProgressMaxCommits`、`maxRewriteBytes` 以及一个 `rewriteOptions` Map，对应 `SizeBasedFileRewritePlanner` / `BinPackRewriteFilePlanner` 的各项阈值（目标文件大小、最小/最大输入文件大小、最小输入文件数、delete 文件阈值、`REWRITE_ALL`、`MAX_FILE_GROUP_SIZE_BYTES`）。`append(...)` 方法依次构建：
- `DataFileRewritePlanner`（`forceNonParallel`），输出 `PlannedGroup`；
- `.rebalance()` 后接入 `DataFileRewriteRunner`（按 `parallelism()` 设置并行度），输出 `ExecutedGroup`；
- `DataFileRewriteCommitter`（`forceNonParallel`），输出 `Trigger`（仅用于把 Watermark 透传给聚合器）；
- 将原始 `trigger` 与 committer 输出 `union` 后作为 `TaskResultAggregator` 的主输入，并把 planner/runner/committer 三者的 `ERROR_STREAM` 侧输出 `union` 后作为次输入，最终输出 `TaskResult`。
每个算子都设置了 `name`、`uid`、`slotSharingGroup`，便于 checkpoint 与监控。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/DataFileRewritePlanner.java` (新增, +206/-0 lines)

**修改目的**：根据 `Trigger` 触发一次重写计划生成。

**工作逻辑**：
`open` 时打开 `TableLoader` 并注册 `error` 计数器；`processElement` 中：
1. `SerializableTable.copyOf(tableLoader.loadTable())` 获取可序列化表对象；空快照直接返回。
2. 实例化 `BinPackRewriteFilePlanner`，用 `rewriterOptions` 初始化，调用 `plan()` 得到 `FileRewritePlan`。
3. 遍历 `plan.groups()`，累加 `inputFilesSizeInBytes()`，超过 `maxRewriteBytes` 的组跳过（仍继续尝试后续较小的组）。
4. 用 `IntMath.divide(groups.size(), partialProgressMaxCommits, CEILING)` 计算 `groupsPerCommit`，把每个组包装为 `PlannedGroup(table, groupsPerCommit, group)` 发出。
5. 任何异常都通过 `ctx.output(TaskResultAggregator.ERROR_STREAM, e)` 上报并递增错误计数器。`close` 关闭 `TableLoader`。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/DataFileRewriteRunner.java` (新增, +253/-0 lines)

**修改目的**：执行单个 `PlannedGroup` 的数据文件重写。

**工作逻辑**：
- `writerFor(value)`：根据表属性 `DEFAULT_FILE_FORMAT` 选择 `FileFormat`，使用 `RowDataTaskWriterFactory`（Flink 2.0 sink 包路径）创建 `TaskWriter<RowData>`，传入 `inputSplitSize()` 作为分区写入参数。
- `readerFor(value)`：用 `RowDataFileScanTaskReader`（考虑 schema 与 `DEFAULT_NAME_MAPPING`）+ `BaseCombinedScanTask` + 表的 `io()/encryption()` 构造 `DataIterator<RowData>`，保证读取时能正确处理等值删除文件。
- `processElement`：循环 `iterator.hasNext()` 把数据写入 writer，完成后 `writer.dataFiles()` 写入 `value.group().setOutputFiles(...)`，发出 `ExecutedGroup(snapshotId, groupsPerCommit, group)`。
- 异常处理分两层：内层（读/写过程）异常会调用 `abort(writer, ...)` 清理已写文件并上报；外层（创建 writer）异常只上报。两层都递增 `errorCounter` 并通过 `ERROR_STREAM` 侧输出。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/DataFileRewriteCommitter.java` (新增, +199/-0 lines)

**修改目的**：累积并提交重写结果，并在 Watermark 时收尾。

**工作逻辑**：
继承 `AbstractStreamOperator<Trigger>` + `OneInputStreamOperator<ExecutedGroup, Trigger>`。
- `open`：打开 `TableLoader`、加载表，注册 `error`、`addedDataFileNum/Size`、`removedDataFileNum/Size` 等计数器。
- `processElement`：若 `commitService == null`，先 `table.refresh()` 拿到最新快照，用 `FlinkRewriteDataFilesCommitManager(table, executedGroup.snapshotId(), timestamp)` 创建 `CommitService` 并 `start()`；然后 `commitService.offer(executedGroup.group())` 投递待提交组。
- `processWatermark`：调用 `commitService.close()` 触发剩余组的提交，然后置空 `commitService`，再 `super.processWatermark(mark)` 把水位线向下游透传（驱动 `TaskResultAggregator` 输出）。
- 内部类 `FlinkRewriteDataFilesCommitManager` 重写 `commitFileGroups`，在父类提交完成后调用 `updateMetrics` 累加新增/删除文件的个数与字节数。
- 异常路径均通过 `output.collect(TaskResultAggregator.ERROR_STREAM, ...)` 上报。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/TaskResultAggregator.java` (新增, +101/-0 lines)

**修改目的**：把一次 `Trigger` 周期内所有算子的结果/异常聚合成单个 `TaskResult`。

**工作逻辑**：
双输入算子。`processElement1` 记录 `Trigger.timestamp()` 作为 `startTime`；`processElement2` 把异常加入 `exceptions` 列表。`processWatermark` 时构造 `TaskResult(taskIndex, startTime, exceptions.isEmpty(), exceptions)` 发出，然后清空状态。定义了公共侧输出标签 `ERROR_STREAM`，供 planner/runner/committer 统一上报异常。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/TableMaintenanceMetrics.java` (修改, +30/-0 lines)

**修改目的**：抽取公共 metric group 构建逻辑，新增数据文件重写相关指标常量。

**工作逻辑**：
新增 `ERROR_COUNTER`、`ADDED_DATA_FILE_NUM_METRIC`、`ADDED_DATA_FILE_SIZE_METRIC`、`REMOVED_DATA_FILE_NUM_METRIC`、`REMOVED_DATA_FILE_SIZE_METRIC` 常量；新增三个重载的 `groupFor(...)` 静态方法，按 `maintenance > tableName > taskName > taskIndex` 层级构建 `MetricGroup`，避免各算子重复样板代码。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/LogUtil.java` (新增, +26/-0 lines)

**修改目的**：统一维护算子的日志前缀常量。

**工作逻辑**：定义 `MESSAGE_PREFIX`（SLF4J 占位符风格）与 `MESSAGE_FORMAT_PREFIX`（`String.format` 风格），供各算子在日志中使用统一的 `[For table {} with {}[{}] at {}]: ` 前缀。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/TableMaintenance.java` (修改, +2/-2 lines)

**修改目的**：适配 `MaintenanceTaskBuilder.append` 参数顺序变更，并修复任务名格式化。

**工作逻辑**：
- 调用 `builder.append(...)` 时把参数顺序改为 `(filtered, tableName, taskNames.get(taskIndex), taskIndex, ...)`，与新签名一致。
- `nameFor` 改用 `String.format(Locale.ROOT, "%s [%d]", ...)`，避免依赖默认 Locale，并把 `taskIndex` 直接按整数格式化。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/MaintenanceTaskBuilder.java` (修改, +2/-2 lines)

**修改目的**：调整 `append` 抽象方法的参数顺序。

**工作逻辑**：把 `(sourceStream, taskIndex, newTaskName, newTableName, ...)` 改为 `(sourceStream, newTableName, newTaskName, taskIndex, ...)`，统一"表名在前、任务名次之、索引最后"的约定，与各算子构造函数的参数顺序保持一致。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/ExpireSnapshots.java` (修改, +1/-1 lines)

**修改目的**：同步 `DeleteFilesProcessor` 构造参数顺序变更。

**工作逻辑**：将 `new DeleteFilesProcessor(index(), taskName(), tableLoader().loadTable(), deleteBatchSize)` 改为 `new DeleteFilesProcessor(tableLoader().loadTable(), taskName(), index(), deleteBatchSize)`。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/DeleteFilesProcessor.java` (修改, +7/-19 lines)

**修改目的**：调整构造参数顺序并复用统一的 metric group 构建。

**工作逻辑**：构造函数改为 `(Table table, String taskName, int taskIndex, int batchSize)`，`taskIndex` 类型由 `String` 改回 `int`；`open` 中改用 `TableMaintenanceMetrics.groupFor(getRuntimeContext(), tableName, taskName, taskIndex)` 一次性获取 `MetricGroup`，再分别取 `DELETE_FILE_FAILED_COUNTER` / `DELETE_FILE_SUCCEEDED_COUNTER`，删除了原来冗长的链式 `addGroup(...)` 调用。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/ExpireSnapshotsProcessor.java` (修改, +1/-1 lines)

**修改目的**：适配 Flink 2.0 的线程池 API。

**工作逻辑**：`ThreadPools.newWorkerPool(...)` 改为 `ThreadPools.newFixedThreadPool(...)`，因为 Flink 2.0 上 `ThreadPools` 的方法名已变更。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/LockRemover.java` (修改, +5/-21 lines)

**修改目的**：复用统一 metric group 构建，简化指标注册代码。

**工作逻辑**：循环中先用 `TableMaintenanceMetrics.groupFor(getRuntimeContext(), tableName, maintenanceTaskNames.get(taskIndex), taskIndex)` 取得 `taskMetricGroup`，再分别注册 `SUCCEEDED_TASK_COUNTER`、`FAILED_TASK_COUNTER`、`LAST_RUN_DURATION_MS` gauge，删除了三段重复的 `addGroup(...)` 链。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/TriggerManager.java` (修改, +5/-21 lines)

**修改目的**：复用统一 metric group 构建。

**工作逻辑**：`open` 中先用 `TableMaintenanceMetrics.groupFor(getRuntimeContext(), tableName)` 取得主组，注册 `RATE_LIMITER_TRIGGERED`、`CONCURRENT_RUN_THROTTLED`、`NOTHING_TO_TRIGGER`；循环中用 `groupFor(mainGroup, taskName, taskIndex)` 注册每个任务的 `TRIGGERED` 计数器，删除重复样板。

### 测试文件

新增/修改了大量测试，覆盖新算子的单元测试与端到端流程：
- `TestRewriteDataFiles.java`（+417）：端到端 Builder 测试，覆盖普通重写、partial progress、maxRewriteBytes 限制、失败场景。
- `TestDataFileRewritePlanner.java`（+193）：验证空表跳过、计划生成、`maxRewriteBytes` 过滤、异常上报。
- `TestDataFileRewriteRunner.java`（+355）：验证读/写、abort、异常上报、`ExecutedGroup` 输出。
- `TestDataFileRewriteCommitter.java`（+278）：验证 `CommitService` 启动/关闭、Watermark 收尾、指标累加、异常上报。
- `OperatorTestBase.java`（+113）、`RewriteUtil.java`（+83）：测试基础设施与工具方法。
- `MaintenanceTaskTestBase.java`（+29/-3）：新增 `runAndWaitForSuccess/Failure/Result` 重载，支持在成功运行后再触发一次失败场景，便于复用。
- `MaintenanceTaskInfraExtension.java`（+5/-3）：将字段赋值统一加上 `this.` 前缀，风格调整。
- `TestExpireSnapshots.java`（+13/-30）、`TestDeleteFilesProcessor.java`（+1/-2）、`TestMaintenanceE2E.java`（+13）：适配新 API 与参数顺序。

## 总结

本次提交把 Flink 1.x 上基于流式 `Trigger` 的 Iceberg 数据文件重写维护任务完整移植到 Flink 2.0，新增 4 个核心算子（Planner/Runner/Committer/Aggregator）与 1 个 Builder，并通过对 `TableMaintenanceMetrics`、`LogUtil` 的抽取统一了既有算子的指标注册与日志风格。功能上与原 PR `#11497` 等价：通过常驻 Flink 作业持续对小文件进行 bin-pack 压缩，支持 partial progress、字节数上限、错误隔离与丰富的监控指标，是 1.4.x 分支在 Flink 2.0 上补齐维护能力的关键一步。
