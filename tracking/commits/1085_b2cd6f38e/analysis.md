# 提交 1085：Flink: Maintenance - TriggerManager (#10484)

## 提交信息

- **序号**：1085 / 4088
- **哈希**：b2cd6f38edc91d40c21272db6b090cdc03a985ba
- **短哈希**：b2cd6f38e
- **日期**：2024-08-22 16:19:31 +0200
- **作者**：pvary
- **提交说明**：Flink: Maintenance - TriggerManager (#10484)
- **PR/Issue**：#10484

## 总体目的

Iceberg 表在持续写入时会产生大量小文件、删除文件、过期快照等问题，需要周期性执行 maintenance 任务（如 `RewriteDataFiles`、`RewriteManifests`、`ExpireSnapshots` 等）。Flink 作为流式写入 Iceberg 的主要引擎之一，需要一个自动化的、随 Flink job 一起运行的 maintenance 调度机制，避免用户手动定时触发。本提交是 Flink Table Maintenance 系列功能的核心一环：引入 `TriggerManager` 算子，它消费 `MonitorSource` 产出的 `TableChange` 事件（表自上次以来的文件数/大小/commit 数变化），根据可配置的触发条件评估是否需要触发某个 maintenance 任务，并在获取到锁的前提下向下游发出 `Trigger` 消息启动任务。

本提交还一并引入了支撑 `TriggerManager` 运行的全套基础设施：触发条件评估器 `TriggerEvaluator`、锁工厂接口 `TriggerLockFactory` 及其 JDBC 实现 `JdbcLockFactory`、锁对象 `Trigger`、维护指标常量 `TableMaintenanceMetrics`，以及 `TableChange` 的 Builder 改造与大量测试（`TestTriggerManager`、`TestJdbcLockFactory`、`TestLockFactoryBase`、`OperatorTestBase` 增强等）。这是 main 分支上 Flink v1.19 模块的维护功能落地，最终目标是为 Flink 用户提供"配置即用"的 Iceberg 表自动维护能力。

## 如何达成设计目的

整体设计围绕"事件驱动 + 锁互斥 + 状态可恢复"展开：

1. **`TriggerManager` 作为 `KeyedProcessFunction<Boolean, TableChange, Trigger>`**：接收 `TableChange` 事件，累计变化，借助 Flink 的 timer 机制做速率限制（`minFireDelayMs`）和锁重试（`lockCheckDelayMs`）。它是 `CheckpointedFunction`，把 `nextEvaluationTime`、`accumulatedChanges`、`lastTriggerTimes` 存入托管状态，支持故障恢复。
2. **`TriggerEvaluator`**：用 Builder 组合多个触发条件（commit 数、文件数、文件大小、删除文件数、超时），任一条件满足即触发；条件以 `Predicate` 列表保存，`anyMatch` 求值。
3. **`TriggerLockFactory` + `JdbcLockFactory`**：提供"维护锁"与"恢复锁"两种锁。`JdbcLockFactory` 用一张 JDBC 表（`flink_maintenance_lock`，主键为 `LOCK_TYPE`+`LOCK_ID`）实现互斥：`tryLock` 插入一行，`isHeld` 查询，`unlock` 按 `INSTANCE_ID` 删除（避免误删他人持有的锁）。恢复锁用于 job 重启后等待上一次触发的任务完成。
4. **`Trigger`**：携带时间戳、`SerializableTable`、`taskId` 的触发消息；另有 `Trigger.recovery(timestamp)` 用于恢复场景。
5. **公平调度**：`TriggerManager` 用 `startsFrom` 记录上次触发的任务位置，避免靠前的任务"饿死"靠后的任务；无任务可触发时从 0 开始。
6. **指标**：通过 `TableMaintenanceMetrics` 暴露 `rateLimiterTriggered`、`concurrentRunThrottled`、`nothingToTrigger`、每个任务的 `triggered` 计数。

## 修改详情

### `flink/v1.19/build.gradle`

**修改目的**：为 Flink v1.19 测试引入 sqlite jdbc 依赖。

**工作逻辑**：在 `testImplementation` 中新增 `libs.sqlite.jdbc`，供 `JdbcLockFactory` 测试（`TestJdbcLockFactory`）使用内存 sqlite 数据库验证锁逻辑。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/TriggerManager.java`（新增，339 行）

**修改目的**：维护任务的核心调度算子。

**工作逻辑**：
- 继承 `KeyedProcessFunction<Boolean, TableChange, Trigger>` 并实现 `CheckpointedFunction`。
- `open()`：注册各类 metric counter 与三组状态（`nextEvaluationTimeState` ValueState、`accumulatedChangesState` ListState、`lastTriggerTimesState` ListState），并打开 `tableLoader`。
- `initializeState()`：打开锁工厂，创建维护锁与恢复锁；若是从 checkpoint 恢复，置 `shouldRestoreTasks=true`。
- `processElement(change)`：先 `init`（懒初始化，从状态恢复或建空列表；恢复时拿恢复锁并发 `Trigger.recovery`）；把新 `change` merge 进每个任务的累计变化；若当前没有排队的评估则立即 `checkAndFire`，否则计数 `rateLimiterTriggered`。
- `onTimer`：清空 `nextEvaluationTime`，调用 `checkAndFire`。
- `checkAndFire`：恢复场景下等恢复锁释放；用 `nextTrigger` 轮转找到一个满足条件的任务；若无则计数 `nothingToTrigger` 并重置 `startsFrom`；若有则 `lock.tryLock()`，成功则发出 `Trigger.create`、清空该任务累计变化、记录触发时间、调度下一次评估（`minFireDelayMs` 后），失败则计数 `concurrentRunThrottled` 并在 `lockCheckDelayMs` 后重试。
- `snapshotState()`：把三组瞬态状态写回托管状态。
- `nextTrigger`：从 `startPos` 轮转一圈，返回第一个满足 evaluator 的任务下标，保证公平。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/Trigger.java`（新增，72 行）

**修改目的**：触发消息载体。

**工作逻辑**：不可变值对象，`create(timestamp, table, taskId)` 生成普通触发，`recovery(timestamp)` 生成恢复触发（table/taskId 为 null，`isRecovery()` 为 true）。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/TriggerEvaluator.java`（新增，128 行）

**修改目的**：评估是否应触发某任务。

**工作逻辑**：Builder 可配置 `commitNumber`、`fileNumber`、`fileSize`、`deleteFileNumber`、`timeout`；`build()` 把每个非空配置转为一个 `Predicate`（满足即触发），用 `anyMatch` 求值。`check(event, lastTimeMs, currentTimeMs)` 对单个任务的累计变化与上次触发时间求值。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/TriggerLockFactory.java`（新增，63 行）

**修改目的**：锁工厂接口，标注 `@Experimental`。

**工作逻辑**：定义 `open()`、`createLock()`（维护锁）、`createRecoveryLock()`（恢复锁）、内部 `Lock` 接口（`tryLock()`/`isHeld()`/`unlock()`），`Closeable` + `Serializable`。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/JdbcLockFactory.java`（新增，321 行）

**修改目的**：基于 JDBC 的锁工厂实现。

**工作逻辑**：
- `open()`：建 `JdbcClientPool`（大小 1），按 `INIT_LOCK_TABLES_PROPERTY` 决定是否建表 `flink_maintenance_lock`（主键 `LOCK_TYPE`+`LOCK_ID`，含 `INSTANCE_ID`）。
- `createLock()`/`createRecoveryLock()`：分别用 `Type.MAINTENANCE`/`Type.RECOVERY` 构造 `Lock`。
- `Lock.tryLock()`：先 `isHeld` 判重，再 `INSERT` 一行（`INSTANCE_ID` 为随机 UUID）；若 INSERT 抛 SQL 异常则回查 `instanceId()` 判断是否其实成功，处理并发竞争。
- `Lock.isHeld()`：`SELECT INSTANCE_ID` 是否存在。
- `Lock.unlock()`：先查当前 `INSTANCE_ID`，再 `DELETE WHERE ... AND INSTANCE_ID=?`，避免误删他人锁（防止 unlock/tryLock 并发时删错）。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/TableMaintenanceMetrics.java`（新增，34 行）

**修改目的**：集中定义维护算子用到的 metric 名常量。

**工作逻辑**：定义 `GROUP_KEY`/`GROUP_VALUE_DEFAULT`，以及 `RATE_LIMITER_TRIGGERED`、`CONCURRENT_RUN_THROTTLED`、`TRIGGERED`、`NOTHING_TO_TRIGGER` 常量；私有构造禁止实例化。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/TableChange.java`

**修改目的**：为 `TableChange` 增加 Builder，便于测试构造；收紧构造器可见性。

**工作逻辑**：原构造器由包级改为 `private`；新增 `builder()` 静态方法与 `Builder` 内部类（链式设置 `dataFileNum`/`deleteFileNum`/`dataFileSize`/`deleteFileSize`/`commitNum`，默认 0）。其余 getter、`merge`、`empty`、`equals/hashCode` 不变。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/MonitorSource.java`

**修改目的**：可见性收紧。

**工作逻辑**：`MonitorSource` 类与构造器由 `public` 改为包级（`class`/`MonitorSource(...)`），因为该 source 仅由 maintenance 内部使用，无需对外暴露。

### 测试文件（`flink/v1.19/flink/src/test/...`）

**修改目的**：覆盖 `TriggerManager` 与锁工厂。

**工作逻辑**：
- `TestTriggerManager.java`（622 行）：覆盖正常触发、速率限制、锁竞争重试、恢复场景、公平调度、多任务等。
- `TestJdbcLockFactory.java`/`TestLockFactoryBase.java`：用 sqlite 验证 `JdbcLockFactory` 的 tryLock/isHeld/unlock 与并发行为。
- `OperatorTestBase.java`：增强为锁工厂测试提供基类（共享 jdbc pool、初始化表）。
- `ConstantsForTests.java`/`MetricsReporterFactoryForTests.java` 及 SPI 注册文件：为测试提供常量与自定义 metric reporter。
- `TestMonitorSource.java`：适配 `TableChange` Builder 改造。

## 小结

- **成效**：在 Flink v1.19 上落地 `TriggerManager` 维护调度算子及其全套支撑（评估器、JDBC 锁工厂、指标、状态恢复），为 Flink 自动维护 Iceberg 表提供事件驱动、互斥、可恢复的核心调度能力。
- **影响范围**：`flink/v1.19` 模块，新增 7 个主代码文件与多个测试文件，约 2081 行新增；改造 `TableChange`、`MonitorSource` 可见性；引入 sqlite 测试依赖。属于新功能，不破坏现有 API。
- **回迁到 1.4.x 的注意事项**：不建议回迁。这是 main 分支上的新功能（Flink Maintenance 框架），依赖 `flink/v1.19` 模块结构与 `MonitorSource`/`TableChange` 等已有维护基础设施，1.4.x 分支并不具备该 maintenance operator 包。强行回迁需先回迁整套 maintenance 框架前置提交，工作量大且与 1.4.x 维护分支定位不符。此外 `JdbcLockFactory` 依赖 `JdbcClientPool`，需确认 1.4.x 已有相应能力。
