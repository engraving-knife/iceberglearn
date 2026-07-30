# 提交 3310：Flink: TableMaintenance Support Coordinator Lock (#15151)

## 提交信息

- **序号**：3310 / 4088
- **哈希**：9534c9b3adc29d127ecc541ce131f49fd72f1980
- **短哈希**：9534c9b3a
- **日期**：2026-02-24
- **作者**：GuoYu
- **提交说明**：Flink: TableMaintenance Support Coordinator Lock (#15151)
- **PR/Issue**：#15151

## 总体目的

Iceberg 的 Flink 表维护（TableMaintenance）功能负责自动触发数据压缩、快照过期等维护任务。此前维护流程通过 `TriggerLockFactory`（一种基于外部锁的机制，如 JDBC 锁）来防止维护任务的并发执行——TriggerManager 在触发任务前需先获取锁，LockRemover 在任务完成后释放锁。这种方式要求用户额外配置和维护一套外部锁基础设施，增加了部署复杂度和运维成本。

本提交为 Flink TableMaintenance 引入了一种基于 Flink OperatorCoordinator 事件的内置协调锁（Coordinator Lock）机制，作为 `TriggerLockFactory` 的替代方案。当用户不提供 `lockFactory` 时，系统自动使用协调锁：TriggerManagerOperator 在触发任务时自行持有锁（记录 `lockTime`），任务完成后 LockRemoverOperator 通过 OperatorEvent 将锁释放事件经由 Coordinator 转发回 TriggerManagerOperator，后者据此清除锁状态。这消除了对外部锁的依赖，使维护功能可以开箱即用，特别适合嵌入到 IcebergSink 的 post-commit topology 场景。

## 如何达成设计目的

整体设计采用 Flink 的 OperatorCoordinator + OperatorEvent 机制实现算子间通信。核心思路是：将原来的 `TriggerManager`（KeyedProcessFunction）和 `LockRemover`（普通算子）替换为基于 `CoordinatedOperatorFactory` 的算子（`TriggerManagerOperator` 和 `LockRemoverOperator`），各自配备对应的 Coordinator（`TriggerManagerCoordinator` 和 `LockRemoverCoordinator`）。共享基类 `BaseCoordinator` 统一管理线程池、SubtaskGateway 和锁事件的注册/转发逻辑。锁的生命周期完全通过内存中的 OperatorEvent 传递，无需外部存储。`TableMaintenance` API 新增不传 `lockFactory` 的重载方法，标记旧的 `lockFactory` 版本为 `@Deprecated`。

涉及的主要文件分为四组：API 层（`TableMaintenance.java`）、Coordinator 层（`BaseCoordinator`、`TriggerManagerCoordinator`、`LockRemoverCoordinator`）、Operator 层（`TriggerManagerOperator`、`LockRemoverOperator` 及其 Factory、事件类）、工具/测试（`TriggerUtil`、多个测试类）。

## 修改详情

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/TableMaintenance.java` (+90/-42 lines)

**修改目的**：新增不依赖外部锁的 API 重载，并根据是否有 lockFactory 选择不同的算子拓扑。

**工作逻辑**：
- 新增 `forChangeStream(DataStream, TableLoader)` 和 `forTable(StreamExecutionEnvironment, TableLoader)` 两个不传 `lockFactory` 的重载方法，内部传 `null` 表示使用协调锁。
- 旧的带 `lockFactory` 参数的方法标记为 `@Deprecated`（since 1.12.0，will be removed in 2.0.0），并将 `lockFactory` 参数改为 `@Nullable`，移除了 `Preconditions.checkNotNull(lockFactory, ...)` 校验。
- `append()` 方法中根据 `lockFactory` 是否为 null 走两条路径：`lockFactory == null` 时使用 `TriggerManagerOperatorFactory` + `LockRemoverOperatorFactory`（协调锁）；否则使用原来的 `TriggerManager`（KeyedProcessFunction）+ `LockRemover`（外部锁）。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/BaseCoordinator.java` (+306 lines, 新文件)

**修改目的**：提供 OperatorCoordinator 的通用基类，管理线程、SubtaskGateway 和锁事件注册/转发。

**工作逻辑**：
`BaseCoordinator` 是抽象类，实现 `OperatorCoordinator` 接口。核心机制：
- 使用静态 `LOCK_RELEASE_CONSUMERS` map（lockId -> Consumer<LockReleaseEvent>）和 `PENDING_RELEASE_EVENTS` 列表实现跨 Coordinator 的锁事件传递。由于 TriggerManagerCoordinator 和 LockRemoverCoordinator 是不同的 Coordinator 实例，但共享同一个 `BaseCoordinator` 类的静态字段，因此 LockRemoverCoordinator 收到的释放事件可以通过静态 map 找到 TriggerManagerCoordinator 注册的 consumer 并转发。
- `registerLock(LockRegisterEvent)`：将 lockId 和一个向 SubtaskGateway 发送 `LockReleaseEvent` 的 consumer 注册到 map 中；同时检查是否有积压的 pending 事件并处理。
- `handleReleaseLock(LockReleaseEvent)`：若已有注册的 consumer 则直接调用，否则暂存到 pending 列表（解决事件到达顺序问题）。
- 管理 `SubtaskGateways` 内部 record，跟踪 subtask 的注册/注销/重置。
- 使用 `CoordinatorExecutorThreadFactory` 创建单线程执行器，确保所有 Coordinator 操作在专用线程中执行，异常时调用 `context.failJob(t)`。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/LockRegisterEvent.java` (+47 lines, 新文件)

**修改目的**：定义 TriggerManagerOperator 向 TriggerManagerCoordinator 注册锁释放回调的事件。

**工作逻辑**：实现 `OperatorEvent`，携带 `lockId`（即 tableName）。在 `TriggerManagerOperator.initializeState` 中通过 `operatorEventGateway.sendEventToCoordinator(new LockRegisterEvent(tableName))` 发送。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/LockReleaseEvent.java` (+56 lines, 新文件)

**修改目的**：定义 LockRemoverOperator 向 LockRemoverCoordinator 通知锁释放的事件。

**工作逻辑**：实现 `OperatorEvent`，携带 `lockId` 和 `timestamp`（watermark 时间戳）。timestamp 用于 TriggerManagerOperator 判断是否为当前持有的锁（防止过期释放事件误清当前锁）。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/LockRemoverCoordinator.java` (+60 lines, 新文件)

**修改目的**：处理 LockRemoverOperator 发来的锁释放事件。

**工作逻辑**：继承 `BaseCoordinator`，`handleEventFromOperator` 中将 `LockReleaseEvent` 交给 `handleReleaseLock` 处理，后者通过静态 map 找到 TriggerManagerCoordinator 注册的 consumer 并转发事件。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/LockRemoverOperator.java` (+112 lines, 新文件)

**修改目的**：替代原 `LockRemover`，在任务完成后通过事件释放协调锁。

**工作逻辑**：继承 `AbstractStreamOperator<Void>`，实现 `OneInputStreamOperator<TaskResult, Void>` 和 `OperatorEventHandler`。在 `processElement` 中处理 `TaskResult`，更新成功/失败计数器和运行时长指标。关键在 `processWatermark` 中：向 Coordinator 发送 `LockReleaseEvent(tableName, mark.getTimestamp())`，将 watermark 时间戳作为锁释放信号传递给 TriggerManagerOperator。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/LockRemoverOperatorFactory.java` (+86 lines, 新文件)

**修改目的**：创建 LockRemoverOperator 并提供其 Coordinator。

**工作逻辑**：实现 `CoordinatedOperatorFactory<Void>`，`getCoordinatorProvider` 返回 `LockRemoverCoordinatorProvider`（基于 `RecreateOnResetOperatorCoordinator.Provider`），`createStreamOperator` 中创建 `OperatorEventGateway` 并构造 `LockRemoverOperator`，注册为事件处理器。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/TriggerManagerCoordinator.java` (+59 lines, 新文件)

**修改目的**：处理 TriggerManagerOperator 发来的锁注册事件。

**工作逻辑**：继承 `BaseCoordinator`，`handleEventFromOperator` 中将 `LockRegisterEvent` 交给 `registerLock` 处理，注册对应 lockId 的释放回调 consumer。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/TriggerManagerOperatorFactory.java` (+110 lines, 新文件)

**修改目的**：创建 TriggerManagerOperator 并提供其 Coordinator。

**工作逻辑**：类似 `LockRemoverOperatorFactory`，实现 `CoordinatedOperatorFactory<Trigger>`，构造 `TriggerManagerOperator` 并注册事件处理器，Coordinator 为 `TriggerManagerCoordinator`。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/TriggerManagerOperator.java` (+322 lines, 新文件)

**修改目的**：替代原 `TriggerManager`（KeyedProcessFunction），使用协调锁管理维护任务触发。

**工作逻辑**：
- 继承 `AbstractStreamOperator<Trigger>`，实现 `OneInputStreamOperator<TableChange, Trigger>`、`OperatorEventHandler`、`ProcessingTimeCallback`。
- `initializeState` 中通过 `operatorEventGateway.sendEventToCoordinator(new LockRegisterEvent(tableName))` 注册锁释放回调；若状态恢复，设置 `lockTime` 和 `shouldRestoreTasks`，发送 `Trigger.recovery` 恢复锁。
- `handleOperatorEvent` 处理 `LockReleaseEvent`：若 `event.timestamp() >= lockTime` 则清除 `lockTime`（释放锁）。
- `checkAndFire` 中：若 `shouldRestoreTasks` 且 `lockTime != null` 则等待恢复锁释放；通过 `TriggerUtil.nextTrigger` 查找待触发任务；若 `lockTime == null` 则获取锁（设置 `lockTime = current`）并输出 `Trigger`；若锁已持有则延迟重试。
- 状态管理通过 `ListState` 持久化 `nextEvaluationTime`、`accumulatedChanges`、`lastTriggerTimes`。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/TriggerManager.java` (+3/-20 lines)

**修改目的**：将 `nextTrigger` 逻辑抽取到 `TriggerUtil` 工具类。

**工作逻辑**：原 `TriggerManager` 中的私有静态方法 `nextTrigger` 被移除，改为调用 `TriggerUtil.nextTrigger(...)`，实现代码复用。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/TriggerUtil.java` (+46 lines, 新文件)

**修改目的**：抽取任务触发选择逻辑为共享工具方法。

**工作逻辑**：`nextTrigger` 从 `startPos` 开始轮询所有 `TriggerEvaluator`，找到第一个满足触发条件的任务索引；轮询一圈无结果返回 null。保证任务调度的公平性（防止饥饿）。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/maintenance/api/TestMaintenanceE2E.java` (+39 lines)

**修改目的**：新增使用协调锁的端到端测试。

**工作逻辑**：`testE2eUseCoordinator` 调用 `TableMaintenance.forTable(env, tableLoader())`（不传 lockFactory），配置 ExpireSnapshots 和 RewriteDataFiles 任务，执行异步作业验证拓扑可正常实例化。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/maintenance/api/TestTableMaintenanceCoordinationLock.java` (+344 lines, 新文件)

**修改目的**：协调锁功能的集成测试。

**工作逻辑**：测试协调锁的完整生命周期，包括锁注册、释放、恢复等场景。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/CoordinatorTestBase.java` (+43 lines, 新文件)

**修改目的**：Coordinator 测试的基类，提供通用测试设施。

**工作逻辑**：继承 `OperatorTestBase`，定义 `LOCK_REGISTER_EVENT`、`LOCK_RELEASE_EVENT` 等常量和 `setAllTasksReady` 辅助方法。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/TestLockRemoveCoordinator.java` (+69 lines, 新文件)

**修改目的**：测试 LockRemoverCoordinator 的事件处理。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/TestLockRemoverOperation.java` (+207 lines, 新文件)

**修改目的**：测试 LockRemoverOperator 的行为。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/TestTriggerManagerCoordinator.java` (+102 lines, 新文件)

**修改目的**：测试 TriggerManagerCoordinator 的锁注册事件处理。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/TestTriggerManagerOperator.java` (+668 lines, 新文件)

**修改目的**：全面测试 TriggerManagerOperator 的触发、锁管理、状态恢复等行为。

## 总结

本提交为 Flink TableMaintenance 引入了基于 Flink OperatorCoordinator 事件的内置协调锁机制，作为外部 `TriggerLockFactory` 的替代方案。通过 `BaseCoordinator` 共享静态字段实现跨算子锁事件传递，TriggerManagerOperator 在内存中管理锁状态，LockRemoverOperator 通过 watermark 触发锁释放信号。这使维护功能可以无需外部锁基础设施即可开箱即用，特别适合 IcebergSink post-commit topology 场景，同时保留了对旧 lockFactory 的向后兼容。
