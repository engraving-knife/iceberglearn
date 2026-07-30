# 提交 1329：Flink: Maintenance - TableManager + ExpireSnapshots (#11144)

## 提交信息

- **序号**：1329 / 4088
- **哈希**：0669bcbad761cf9ac6d4847e8f5a510f4edc8e1f
- **短哈希**：0669bcbad
- **日期**：2024-11-04（Mon Nov 4 15:06:00 2024 +0100）
- **作者**：pvary <peter.vary.apache@gmail.com>
- **提交说明**：Flink: Maintenance - TableManager + ExpireSnapshots (#11144)
- **PR/Issue**：#11144

## 总体目的

Iceberg 表需要持续维护——过期快照（expire snapshots）、删除孤儿文件（orphan file deletion）、合并小 manifest 等。传统做法是用户手动定期触发这些维护操作（如通过 Spark procedure 或 Java API），存在两个痛点：一是容易遗忘导致快照堆积、存储成本上升；二是手动触发难以做到"按需"——要么过于频繁浪费资源，要么过于稀疏导致维护滞后。

Iceberg Flink 集成此前已有维护框架的"操作算子层"（`operator` 包下的 `MonitorSource`、`TriggerManager`、`TriggerEvaluator`、`LockRemover` 等），可以监控表变更、按阈值触发任务、用锁防止并发执行。但缺少一个**面向用户的 API 层**——用户无法方便地组装"监控 → 触发 → 执行维护 → 清理锁"的完整 Flink 拓扑，也没有现成的维护任务实现（如 ExpireSnapshots）可直接挂载。

本提交补齐这一缺口，主要交付三件事：

1. **`TableMaintenance` API 入口**：提供 `forTable()`（独立维护作业）与 `forChangeStream()`（嵌入 IcebergSink 后提交拓扑）两种入口，通过 Builder 模式让用户配置触发限流、锁检查间隔、并行度等参数，并挂载多个维护任务。`append()` 方法自动构建完整的 Flink 流式拓扑：MonitorSource → TriggerManager → WatermarkAssigner → 按任务过滤 → 各任务执行 → LockRemover；
2. **`MaintenanceTaskBuilder` 抽象 + `ExpireSnapshots` 首个实现**：定义维护任务的抽象基类（支持 `scheduleOnCommitCount`/`scheduleOnDataFileCount`/`scheduleOnDataFileSize`/`scheduleOnPosDeleteFileCount` 等多种触发条件），并实现首个具体任务 `ExpireSnapshots`——调用 Iceberg 的 `ExpireSnapshots` API 过期旧快照，通过 side output 把待删除文件路径发给 `DeleteFilesProcessor` 批量删除；
3. **包重构 + 指标增强**：把 `Trigger`、`TaskResult`、`TriggerLockFactory`、`JdbcLockFactory` 等面向用户的类型从 `operator` 包移到 `api` 包（区分公开 API 与内部算子）；指标分组重构为按 `tableName`/`taskName`/`taskIndex` 三级嵌套，便于在多表多任务场景下精确监控。

## 如何达成设计目的

整体采用"API 层 + 算子层"分层架构：

1. **API 层**（`org.apache.iceberg.flink.maintenance.api`，新增/移入）：
   - `TableMaintenance`：静态工厂 + Builder，负责组装拓扑；
   - `MaintenanceTaskBuilder<T>`：抽象基类，定义触发条件（委托 `TriggerEvaluator.Builder`）、并行度、uid 后缀等通用配置，子类实现 `append(DataStream<Trigger>)` 挂载具体执行算子；
   - `ExpireSnapshots`：首个具体任务，构建 `ExpireSnapshotsProcessor`（执行过期）+ `DeleteFilesProcessor`（批量删文件）两段算子；
   - `Trigger`/`TaskResult`/`TriggerLockFactory`/`JdbcLockFactory`：从 `operator` 包移入，作为 API 契约的一部分。`Trigger` 新增 `isRecovery` 标志与 `recovery()` 工厂方法，`taskId` 改为可空 `Integer`（recovery 触发无具体任务 id）。

2. **算子层**（`operator` 包，新增/修改）：
   - `ExpireSnapshotsProcessor`（新增）：`ProcessFunction<Trigger, TaskResult>`，收到 Trigger 后调用 `table.expireSnapshots()` 配置 `maxSnapshotAgeMs`/`numSnapshots`/`plannerPool`，执行过期；通过 `deleteFiles(Consumer)` 回调把待删文件路径收集到 side output（`DELETE_STREAM`），主输出返回 `TaskResult`（成功/失败 + 耗时）；
   - `DeleteFilesProcessor`（新增）：`OneInputStreamOperator<String, Void>`，缓冲文件路径到 `batchSize` 后调用 `SupportsBulkOperations.deleteFiles(batch)` 批量删除，记录成功/失败计数指标，watermark 到达时刷出剩余文件；
   - `TriggerManager`（修改）：改为 public，构造时立即 `tableLoader.open()` + `loadTable().name()` 取表名（避免运行时重复加载），指标分组从 `addGroup(GROUP_KEY, name)` 改为 `addGroup(GROUP_KEY).addGroup(TABLE_NAME_KEY, tableName)`；
   - `LockRemover`（修改）：构造增加 `tableName` 参数，指标按 `tableName`/`taskName`/`taskIndex` 三级分组；
   - `TableMaintenanceMetrics`（修改）：新增 `TABLE_NAME_KEY`/`TASK_NAME_KEY`/`TASK_INDEX_KEY` 与 `DELETE_FILE_FAILED_COUNTER`/`DELETE_FILE_SUCCEEDED_COUNTER`，`GROUP_KEY` 改为 `"maintenance"`。

3. **拓扑组装**（`TableMaintenance.Builder.append()`）：
   - 创建 `MonitorSource`（独立模式）或复用传入的 `changeStream`（嵌入模式）→ `reinterpretAsKeyedStream(unused -> true)` → `TriggerManager`（单并行，评估触发条件、加锁）→ `PunctuatedWatermarkStrategy`（按 Trigger.timestamp 发 watermark）；
   - 对每个 `MaintenanceTaskBuilder`，filter 出对应 `taskId` 的 Trigger → `builder.append()` 挂载执行算子 → union 所有任务结果；
   - 末尾 `LockRemover` 收集所有 `TaskResult`，释放锁。

## 修改详情

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/TableMaintenance.java`（新增，330 行）

**修改目的**：维护拓扑的入口与 Builder。

**工作逻辑**：
- `forChangeStream(changeStream, tableLoader, lockFactory)`：用于嵌入 IcebergSink 后提交拓扑（`@Internal`），changeStream 已由 sink 提供；
- `forTable(env, tableLoader, lockFactory)`：用于独立维护作业，内部创建 `MonitorSource` 监控表变更；
- `Builder` 配置项：`uidSuffix`（算子 uid 后缀，默认随机 UUID）、`slotSharingGroup`（槽共享组）、`rateLimit`（触发限流，默认 1 分钟）、`lockCheckDelay`（锁检查间隔，默认 30 秒）、`parallelism`（任务默认并行度）、`maxReadBack`（首次启动回溯快照数，默认 100）、`add(MaintenanceTaskBuilder)` 挂载任务；
- `append()`：组装拓扑（如"如何达成设计目的"所述），最终在末尾挂 `LockRemover`；
- `PunctuatedWatermarkStrategy`：内部类，按 `Trigger.timestamp()` 发 punctuated watermark，驱动下游基于 watermark 的批量刷出（如 `DeleteFilesProcessor` 在 watermark 到达时删除剩余文件）。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/MaintenanceTaskBuilder.java`（新增，227 行）

**修改目的**：维护任务抽象基类，定义触发条件与通用配置。

**工作逻辑**：`@Experimental` 注解标记为实验性 API。泛型自引用 `T extends MaintenanceTaskBuilder<?>` 支持链式调用。核心是持有一个 `TriggerEvaluator.Builder`，提供 7 种触发条件 setter：`scheduleOnCommitCount`、`scheduleOnDataFileCount`、`scheduleOnDataFileSize`、`scheduleOnPosDeleteFileCount`、`scheduleOnPosDeleteRecordCount`、`scheduleOnEqDeleteFileCount`、`scheduleOnEqDeleteRecordCount`。子类实现抽象方法 `append(DataStream<Trigger>)` 挂载执行算子并返回 `DataStream<TaskResult>`。基类还管理 `index`/`taskName`/`tableName`/`tableLoader`/`uidSuffix`/`slotSharingGroup`/`parallelism` 等运行时上下文（由 `TableMaintenance.Builder.append()` 在挂载时注入）。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/ExpireSnapshots.java`（新增，125 行）

**修改目的**：首个具体维护任务——过期快照。

**工作逻辑**：继承 `MaintenanceTaskBuilder<ExpireSnapshots.Builder>`，配置项：`maxSnapshotAge`（Duration）、`retainLast`（int，保留快照数）、`planningWorkerPoolSize`（规划线程池大小）、`deleteBatchSize`（删除批大小，默认 1000）。`append()` 实现：
1. `ExpireSnapshotsProcessor` 作为 `ProcessFunction` 处理 Trigger，执行过期，主输出 `TaskResult`，side output `DELETE_STREAM` 发待删文件路径；
2. 取 side output → `DeleteFilesProcessor`（批大小 `deleteBatchSize`）批量删除文件；
3. 返回 `TaskResult` 流。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/ExpireSnapshotsProcessor.java`（新增，129 行）

**修改目的**：执行 ExpireSnapshots 的算子。

**工作逻辑**：`ProcessFunction<Trigger, TaskResult>`。`open()` 时加载 `Table`，可选创建专用 `plannerPool`。`processElement()` 收到 Trigger 后：构建 `table.expireSnapshots()`，配置 `maxSnapshotAgeMs`/`retainLast`/`planWith(plannerPool)`，通过 `deleteFiles(Consumer)` 回调把文件路径收集到 `List<String>`，调用 `commit()` 执行过期；然后把收集的文件路径逐个发到 side output `DELETE_STREAM`，主输出收集器发 `TaskResult`（成功 + 耗时 + 删除文件数）。异常时发失败 `TaskResult`。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/DeleteFilesProcessor.java`（新增，130 行）

**修改目的**：批量删除文件的算子。

**工作逻辑**：`AbstractStreamOperator<Void>` + `OneInputStreamOperator<String, Void>`。要求 `FileIO` 实现 `SupportsBulkOperations`。`processElement()` 把文件路径加入 `filesToDelete` 缓冲集，达到 `batchSize` 时调用 `io.deleteFiles(filesToDelete)` 批量删除，记录 `deleteSucceeded`/`deleteFailed` 指标。`processWatermark()` 在 watermark 到达时刷出剩余未删文件（确保在 checkpoint/watermark 推进前完成删除）。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/Trigger.java`（从 operator 包移入 + 修改，65 行）

**修改目的**：触发事件模型，移入 API 包并增强。

**工作逻辑**：字段 `timestamp`(long)、`taskId`(Integer, 可空)、`isRecovery`(boolean)。工厂方法 `create(timestamp, taskId)` 创建正常触发，`recovery(timestamp)` 创建恢复触发（taskId=null, isRecovery=true）。恢复触发用于作业重启后从故障中恢复，触发所有任务重新评估。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/TaskResult.java`、`TriggerLockFactory.java`、`JdbcLockFactory.java`（从 operator 包移入）

**修改目的**：将面向用户的类型从内部算子包移到 API 包，仅 package 声明变更。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/TriggerManager.java`（修改）

**修改目的**：适配 API 层调用，改进指标分组。

- 改为 `public`（供 `TableMaintenance.Builder` 跨包构造）；
- 构造时 `tableLoader.open()` + `loadTable().name()` 缓存 `tableName`，替代持有 `TableLoader`（减少运行时重复加载）；
- 指标分组 `addGroup(GROUP_KEY, GROUP_VALUE_DEFAULT)` → `addGroup(GROUP_KEY).addGroup(TABLE_NAME_KEY, tableName)`，使指标按表名隔离；
- `triggerCounters` 从 stream/collect 改为显式 for 循环构建（与 index 对齐）。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/LockRemover.java`（修改）

**修改目的**：适配 API 层，指标三级分组。

- 构造增加 `tableName` 参数；
- 指标分组改为 `addGroup(GROUP_KEY).addGroup(TABLE_NAME_KEY, tableName).addGroup(TASK_NAME_KEY, name).addGroup(TASK_INDEX_KEY, index)`；
- 导入从 `operator` 包改为 `api` 包（`TaskResult`/`Trigger`/`TriggerLockFactory`）。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/TableMaintenanceMetrics.java`（修改）

**修改目的**：指标 key 重构。

- `GROUP_KEY` 从 `"maintenanceTask"` 改为 `"maintenance"`；
- 新增 `TABLE_NAME_KEY`/`TASK_NAME_KEY`/`TASK_INDEX_KEY` 用于三级分组；
- 新增 `DELETE_FILE_FAILED_COUNTER`/`DELETE_FILE_SUCCEEDED_COUNTER` 给 `DeleteFilesProcessor` 使用。

### 其余算子小改（`MonitorSource`、`TableChange`、`TriggerEvaluator`）

**修改目的**：适配 `api` 包类型导入。

### 测试文件（新增/修改，约 1500 行）

新增 API 测试：`TestTableMaintenance`（460 行，覆盖 `forTable`/`forChangeStream` 拓扑构建、uid/slotSharingGroup/parallelism 配置、多任务 union、LockRemover 挂载等）、`TestExpireSnapshots`（254 行，覆盖 maxSnapshotAge/retainLast/workerPool 配置与拓扑）、`TestMaintenanceE2E`（67 行，端到端验证）、`MaintenanceTaskTestBase` + `MaintenanceTaskInfraExtension`（测试基础设施）。

新增算子测试：`TestDeleteFilesProcessor`（116 行）、`TestExpireSnapshotsProcessor`（80 行）。

修改既有测试：`OperatorTestBase`、`TestLockRemover`、`TestMonitorSource`、`TestTriggerManager` 等适配 API 包移动与指标变更；删除 `ConstantsForTests`/`FlinkSqlExtension`/`FlinkStreamingTestUtils`（被新测试基础设施取代）。

## 小结

- **成效**：交付 Flink 维护框架的面向用户 API 层与首个具体维护任务（ExpireSnapshots），用户可通过 `TableMaintenance.forTable(...).add(ExpireSnapshots.builder()...).append()` 一行式部署持续运行的快照过期 Flink 作业，按 commit 数/文件数/文件大小等阈值自动触发，并通过锁机制防止并发执行。指标按 tableName/taskName/taskIndex 三级分组，支持多表多任务监控。同时把公开类型从 `operator` 包移到 `api` 包，明确 API 边界。
- **影响范围**：仅 `flink/v1.20` 模块，纯新增 API + 算子 + 测试，不修改既有 Flink sink/scan 逻辑。新增 2527 行、删除 552 行（主要是测试重构）。API 标注 `@Experimental`，表明尚在演进中。
- **回迁到 1.4.x 的注意事项**：本提交是 Flink 1.20 专属（`flink/v1.20/`），1.4.x 若支持 Flink 1.20 则可考虑回迁。需注意：1.4.x 上是否已有 `operator` 包下的维护算子（`MonitorSource`/`TriggerManager`/`LockRemover` 等）——本提交假设这些算子已存在并在此基础上构建 API 层，若 1.4.x 缺少这些前置算子则无法直接回迁。指标 key 变更（`GROUP_KEY` 从 `"maintenanceTask"` 改为 `"maintenance"`）会破坏既有监控仪表盘，回迁时需同步通知运维。`@Experimental` 注解意味着 API 不稳定，1.4.x 回迁后可能在后续版本再次变更。测试删除了 `FlinkSqlExtension` 等旧测试工具，1.4.x 若有其它测试依赖这些工具需评估影响。
