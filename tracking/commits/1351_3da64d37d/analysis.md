# 提交 1351：Flink: Port #11144 to v1.19 (#11473)

## 提交信息

- **序号**：1351 / 4088
- **哈希**：3da64d37d018b3fc7e78e7004ce63245b5b8a1c5
- **短哈希**：3da64d37d
- **日期**：2024-11-08（Fri Nov 8 07:29:44 2024 +0100）
- **作者**：pvary <peter.vary.apache@gmail.com>
- **提交说明**：Flink: Port #11144 to v1.19 (#11473)
- **PR/Issue**：#11473（端口来自 #11144）

## 总体目的

Iceberg 的 Flink 集成按 Flink 版本分目录维护（`flink/v1.18`、`flink/v1.19`、`flink/v1.20` 等），同一功能需要在每个支持的 Flink 版本目录下分别落地。PR #11144（提交 `0669bcbad` "Flink: Maintenance - TableManager + ExpireSnapshots"）在 Flink 1.20 目录下引入了一套完整的"表维护（Table Maintenance）"框架，提供基于流式拓扑自动触发快照过期、文件删除等维护任务的能力。

本提交把 #11144 的全部改动原样移植到 `flink/v1.19` 目录，使 Flink 1.19 用户也能使用同一套维护 API。移植内容与原 PR 完全一致（34 个文件、+2527/-552 行，逐文件 diff 为空，即内容字节级相同），仅目录前缀由 `flink/v1.20/flink/...` 改为 `flink/v1.19/flink/...`。

被移植的功能包括：
- 新增 `org.apache.iceberg.flink.maintenance.api` 包，提供面向用户的维护 API：`TableMaintenance`（拓扑入口）、`ExpireSnapshots`（快照过期任务构建器）、`MaintenanceTaskBuilder`（任务基类）、`Trigger`/`TriggerLockFactory`/`JdbcLockFactory`/`TaskResult`（从 `operator` 包移到 `api` 包）。
- 新增 `operator` 包中的 `DeleteFilesProcessor`、`ExpireSnapshotsProcessor` 两个算子，分别负责批量删除文件和过期快照。
- 重构现有算子（`LockRemover`、`MonitorSource`、`TriggerManager`、`TriggerEvaluator`、`TableChange`、`TableMaintenanceMetrics`）以适配新 API。
- 重写测试基础设施：新增 `MaintenanceTaskTestBase`、`MaintenanceTaskInfraExtension`、`TestExpireSnapshots`、`TestMaintenanceE2E`、`TestTableMaintenance`、`TestDeleteFilesProcessor`、`TestExpireSnapshotsProcessor` 等；删除过时的 `ConstantsForTests`、`FlinkSqlExtension`、`FlinkStreamingTestUtils`。

## 如何达成设计目的

- **逐文件复制**：从 `flink/v1.20/flink/src/.../maintenance/` 把每个文件原样复制到 `flink/v1.19/flink/src/.../maintenance/` 对应路径，文件名、包名、类名、实现逻辑完全相同（Flink 1.19 与 1.20 API 兼容，无需调整）。
- **包结构调整一致**：在 v1.19 目录下创建与 v1.20 相同的 `api`/`operator` 子包结构，把 `JdbcLockFactory`、`TaskResult`、`Trigger`、`TriggerLockFactory` 从 `operator` 包移到 `api` 包（保持与 v1.20 一致）。
- **测试基础设施同步**：在 v1.19 测试目录下复制 v1.20 的测试基类、扩展、工具类，删除 v1.20 中已废弃的测试工具类，确保两个 Flink 版本的测试覆盖与行为一致。

由于是纯端口，本提交不引入新的设计决策，所有设计细节见 #11144。

## 修改详情

下面按文件类别归纳（每个文件的内容与 v1.20 版本完全相同）。

### 新增 `api` 包（面向用户的维护 API）

- **`flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/TableMaintenance.java`**（330 行）：维护拓扑入口。提供 `forChangeStream(DataStream<TableChange>, TableLoader, TriggerLockFactory)`（用于已有 change stream，如 `IcebergSink.addPostCommitTopology`）和 `forTable(StreamExecutionEnvironment, TableLoader, TriggerLockFactory)`（独立维护作业）两个工厂方法。内部 `Builder` 收集多个 `MaintenanceTaskBuilder`，构建出"MonitorSource → TriggerManager（按 commit/文件数/大小等条件触发）→ 各 MaintenanceTask → LockRemover"的流式拓扑，支持 rate limit、lock check delay、parallelism、slot sharing group 等配置。

- **`api/ExpireSnapshots.java`**（125 行）：快照过期任务构建器。继承 `MaintenanceTaskBuilder`，配置 `maxSnapshotAge`、`retainLast`、`planningWorkerPoolSize`、`deleteBatchSize` 等，`append(...)` 时构建 `ExpireSnapshotsProcessor` + `DeleteFilesProcessor` 算子链。

- **`api/MaintenanceTaskBuilder.java`**（227 行）：抽象基类，定义维护任务的通用配置（index、taskName、tableName、tableLoader、uidSuffix、parallelism）和触发条件（`scheduleOnCommitCount`、`scheduleOnDataFileCount`、`scheduleOnDataFileSize`、`scheduleOnPosDeleteFileCount`、`scheduleOnPosDeleteRecordCount`、`scheduleOnEqDeleteFileCount`、`scheduleOnEqDeleteRecordCount`）。子类实现 `append(DataStream<Trigger>)` 返回 `DataStream<TaskResult>`。

- **`api/Trigger.java`**、**`api/TaskResult.java`**、**`api/TriggerLockFactory.java`**、**`api/JdbcLockFactory.java`**：从 `operator` 包移到 `api` 包，作为面向用户的公共 API。`Trigger` 表示触发信号；`TaskResult` 表示任务执行结果；`TriggerLockFactory` 定义锁工厂接口（`recoverLocks`、`createLock`、`recoverTaskLock`、`createTaskLock`）；`JdbcLockFactory` 是基于 JDBC 的实现。

### 新增 `operator` 算子

- **`operator/DeleteFilesProcessor.java`**（130 行）：按批次删除文件的算子，接收 `Set<String>` 文件路径，分批调用 `Table.io().deleteFile(...)`，输出删除结果。
- **`operator/ExpireSnapshotsProcessor.java`**（129 行）：调用 `ExpireSnapshots` API 过期快照，把要删除的文件分批发送给下游 `DeleteFilesProcessor`。

### 修改的 `operator` 算子

- **`operator/LockRemover.java`**、**`operator/MonitorSource.java`**、**`operator/TableChange.java`**、**`operator/TableMaintenanceMetrics.java`**、**`operator/TriggerEvaluator.java`**、**`operator/TriggerManager.java`**：调整以适配新的 `api` 包类型（如 `Trigger`、`TriggerLockFactory`、`TaskResult` 的包路径变更），并完善触发条件、锁管理、指标上报等逻辑。

### 测试基础设施

- **新增 `api/MaintenanceTaskInfraExtension.java`**（78 行）、**`api/MaintenanceTaskTestBase.java`**（64 行）：JUnit 5 扩展与测试基类，提供表、TableLoader、锁工厂、change stream 等测试 fixture。
- **新增 `api/TestExpireSnapshots.java`**（254 行）、**`api/TestMaintenanceE2E.java`**（67 行）、**`api/TestTableMaintenance.java`**（460 行）：覆盖 ExpireSnapshots 构建器、端到端维护流程、TableMaintenance 拓扑构建。
- **移动 `api/TestJdbcLockFactory.java`**、**`api/TestLockFactoryBase.java`**：从 `operator` 包移到 `api` 包。
- **修改 `operator/OperatorTestBase.java`**（+184/-... ）、**`operator/ManualSource.java`**、**`operator/CollectingSink.java`**、**`operator/MetricsReporterFactoryForTests.java`**：重构测试基座以支持新 API。
- **新增 `operator/TestDeleteFilesProcessor.java`**（116 行）、**`operator/TestExpireSnapshotsProcessor.java`**（80 行）：覆盖两个新算子。
- **修改 `operator/TestLockRemover.java`**、**`operator/TestMonitorSource.java`**、**`operator/TestTriggerManager.java`**：适配 API 变更。
- **删除 `operator/ConstantsForTests.java`**、**`operator/FlinkSqlExtension.java`**、**`operator/FlinkStreamingTestUtils.java`**：被新测试基础设施取代。

## 小结

- **成效**：Flink 1.19 现具备与 1.20 相同的 Table Maintenance 框架，用户可通过 `TableMaintenance.forTable(...)` 启动独立维护作业，或通过 `IcebergSink.addPostCommitTopology` 内嵌维护拓扑，自动按 commit 数/文件数/文件大小/删除文件数等条件触发快照过期与文件删除。API 与算子实现与 1.20 完全一致，便于跨版本维护与问题修复同步。
- **影响范围**：仅 `flink/v1.19` 模块，新增/移动 34 个文件，无 core/api/其他引擎模块变更。
- **回迁到 1.4.x 的注意事项**：
  - 本提交本身是把 #11144 从 v1.20 端口到 v1.19。1.4.x 若支持 Flink 1.19，可考虑回迁以获得维护框架能力；若不支持则不适用。
  - 这是较大的功能新增（+2527 行），回迁需评估 1.4.x 的 Flink 1.19 模块是否已具备 #11144 之前的 maintenance 算子基线（`MonitorSource`、`TriggerManager`、`LockRemover` 等），否则端口会缺少依赖。
  - 1.4.x 作为维护分支通常不引入新功能，**一般不回迁**此类大特性；若社区决定在 1.4.x 支持 Flink maintenance，应整体评估 #11144 及其前置 PR 的回迁成本。
  - 若仅需要 bug 修复，可关注后续针对 maintenance 框架的修复提交，按需选择性回迁。
