# 提交 1148：Flink: Maintenance - Lock remover (#11010)

## 提交信息

- **序号**：1148
- **哈希**：6ff7a6ec2e2790e5d2d2b35a2091e68101ab3aa3
- **短哈希**：6ff7a6ec2
- **日期**：2024-09-12（Thu Sep 12 08:17:58 2024 +0300）
- **作者**：pvary <peter.vary.apache@gmail.com>
- **提交说明**：Flink: Maintenance - Lock remover (#11010)
- **PR/Issue**：#11010

## 总体目的

Iceberg 的 Flink Table Maintenance 模块（`flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/`）提供了一组算子，用于在 Flink 流作业中持续触发并执行 Iceberg 表的维护任务（如 manifest 合并、过期快照删除等）。该模块的设计是：

- `TriggerManager` 监听表变更事件，按触发评估器（`TriggerEvaluator`）的策略决定何时触发一次维护任务；触发前需要先通过 `TriggerLockFactory.createLock()` 获取"任务锁"，避免并发触发；
- 任务执行结果原本没有专门的算子来"善后"——既需要在任务完成后释放任务锁，也需要在故障恢复场景下释放"恢复锁"（`createRecoveryLock()`），还需要记录任务的成功/失败次数与运行时长等度量指标。

本提交新增 `LockRemover` 算子与 `TaskResult` 数据对象，专门负责：
1. 接收上游维护任务执行结果（`TaskResult`），释放对应的任务锁；
2. 在 watermark 推进到任务开始时间之后，释放恢复锁（处理 recovery 场景）；
3. 收集并暴露每个维护任务的成功/失败计数与最近一次运行时长指标。

同时把 `TriggerManager` 中的字段 `taskNames` 重命名为 `maintenanceTaskNames` 以提高语义清晰度，并修复测试辅助类 `ManualSource` 中 watermark 发送逻辑的 bug。这是 Flink Maintenance 模块从"骨架"走向"可用"的关键一环。

## 如何达成设计目的

1. **新增 `TaskResult` 数据类**：作为维护任务执行结果的载体，包含 `taskIndex`（任务索引）、`startEpoch`（任务开始的 epoch 毫秒）、`success`（是否成功）、`exceptions`（异常列表）。下游 `LockRemover` 据此判断锁的释放与度量更新。
2. **新增 `LockRemover` 算子**：继承 `AbstractStreamOperator<Void>` 实现 `OneInputStreamOperator<TaskResult, Void>`，输入是 `TaskResult` 流，输出无（`Void`）。它利用 Flink 的 watermark 机制区分"正常任务完成"与"recovery 完成"两种场景，分别在不同回调中释放任务锁与恢复锁。
3. **新增度量指标常量**：在 `TableMaintenanceMetrics` 中新增 `SUCCEEDED_TASK_COUNTER`、`FAILED_TASK_COUNTER`、`LAST_RUN_DURATION_MS` 三个指标名常量。
4. **为 `JdbcLockFactory` 补充 Javadoc**：原本无类注释，新增对其用途的说明。
5. **重命名 `taskNames` → `maintenanceTaskNames`**：在 `TriggerManager` 中将字段、参数、本地变量统一改名，并相应更新错误消息，让"task"语义更明确为"维护任务"。
6. **修复 `ManualSource` 测试辅助类**：原本 `pollNext` 在收到 `next.f0 == null`（哨兵值）时直接返回 `END_OF_INPUT`，未处理 watermark；本次重构后支持通过 `(null, timestamp)` 发送 watermark、通过 `(null, null)` 表示输入结束，让测试能模拟 watermark 推进场景。
7. **新增 `TestLockRemover` 测试类**：覆盖正常流程、作为 sink 后置拓扑、度量指标统计、recovery 多源 watermark 等待四类场景。

## 修改详情

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/LockRemover.java`（新增 144 行）

**修改目的**：新增 LockRemover 算子，负责释放锁与收集度量。

**工作逻辑**：

类签名为 `public class LockRemover extends AbstractStreamOperator<Void> implements OneInputStreamOperator<TaskResult, Void>`，标注 `@Internal`。

Javadoc 详细说明了三类输入场景的假设：
- 正常执行：先收到 `TaskResult`，后收到对应 `Watermark`，由 `processElement` 解锁；
- 无进行中任务的 recovery：只收到 `Trigger.recovery(long)` 的 watermark（无 TaskResult），由 `processWatermark` 同时解锁 lock 与 recoveryLock（此时 `lastProcessedTaskStartEpoch` 为 0）；
- 有进行中任务的 recovery：先收到 `TaskResult`（解锁 lock），后收到 watermark（解锁 recoveryLock）。

字段：
- `lockFactory`：锁工厂；
- `maintenanceTaskNames`：任务名列表（用于按索引生成度量）；
- `succeededTaskResultCounters` / `failedTaskResultCounters` / `taskLastRunDurationMs`：每个任务对应的成功/失败计数器与最近一次运行时长（AtomicLong）；
- `lock` / `recoveryLock`：从工厂获取的锁实例；
- `lastProcessedTaskStartEpoch`：最近处理的任务开始时间，用于 watermark 判断"是否所有进行中任务已完成"。

`open()` 方法：
- 为每个任务名创建一个 metric group（`addGroup(TableMaintenanceMetrics.GROUP_KEY, name)`），注册成功/失败 Counter 与运行时长 Gauge（`duration::get`）；
- 通过 `lockFactory.createLock()` 与 `createRecoveryLock()` 获取两个锁实例。

`processElement(StreamRecord<TaskResult>)` 方法：
- 取出 `TaskResult`，记录日志；
- 计算运行时长 `duration = System.currentTimeMillis() - taskResult.startEpoch()`；
- **立即调用 `lock.unlock()` 释放任务锁**——这是核心动作，让 `TriggerManager` 能在下一次评估时重新获取锁触发新任务；
- 把 `lastProcessedTaskStartEpoch` 更新为本任务的 startEpoch（供后续 watermark 判断）；
- 更新对应任务的运行时长 Gauge，并根据 `taskResult.success()` 自增成功或失败计数器。

`processWatermark(Watermark)` 方法：
- 若 `mark.getTimestamp() > lastProcessedTaskStartEpoch`，说明所有已收到的任务结果对应的 watermark 都已推进到位，**同时释放 `lock` 与 `recoveryLock`**；
- 这处理了 recovery 场景：recovery watermark 到达后，确保 recoveryLock 被释放，让 `TriggerManager` 知道恢复完成可以继续正常触发。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/TaskResult.java`（新增 65 行）

**修改目的**：定义维护任务执行结果数据类。

**工作逻辑**：不可变 POJO，标注 `@Internal`。四个 `final` 字段：`taskIndex`（int）、`startEpoch`（long）、`success`（boolean）、`exceptions`（`List<Exception>`）。提供全参构造与四个 getter。`toString()` 用 Guava `MoreObjects.toStringHelper` 输出可读形式。该类会被下游维护任务的执行算子产生，并被 `LockRemover` 消费。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/TableMaintenanceMetrics.java`

**修改目的**：新增 LockRemover 使用的度量指标名常量。

**工作逻辑**：在原有 `TRIGGERED`、`NOTHING_TO_TRIGGER` 之后追加：
```java
// LockRemover metrics
public static final String SUCCEEDED_TASK_COUNTER = "succeededTasks";
public static final String FAILED_TASK_COUNTER = "failedTasks";
public static final String LAST_RUN_DURATION_MS = "lastRunDurationMs";
```
分别对应成功任务计数、失败任务计数、最近一次运行时长（毫秒）。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/TriggerManager.java`

**修改目的**：将 `taskNames` 字段统一重命名为 `maintenanceTaskNames`，提高语义清晰度。

**工作逻辑**：
- 字段声明 `private final List<String> taskNames;` → `private final List<String> maintenanceTaskNames;`
- 构造方法参数 `List<String> taskNames` → `List<String> maintenanceTaskNames`
- `Preconditions.checkArgument` 中的错误消息由 "Invalid task names: null or empty" 改为 "Invalid maintenance task names: null or empty"，"Provide a name and evaluator for all of the tasks" 改为 "Provide a name and evaluator for all of the maintenance tasks"；
- 字段赋值 `this.taskNames = taskNames;` → `this.maintenanceTaskNames = maintenanceTaskNames;`
- `open()` 方法中创建 triggerCounters 的 stream 来源由 `taskNames.stream()` 改为 `maintenanceTaskNames.stream()`。

无行为变更，纯命名重构。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/JdbcLockFactory.java`

**修改目的**：为类补充 Javadoc 说明。

**工作逻辑**：在类声明前新增：
```java
/**
 * JDBC table backed implementation of the {@link
 * org.apache.iceberg.flink.maintenance.operator.TriggerLockFactory}.
 */
```
让阅读者一眼明白这是基于 JDBC 表的锁工厂实现。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/ManualSource.java`

**修改目的**：修复并扩展测试用 Source 的 watermark 发送逻辑。

**工作逻辑**：

`ManualSource` 是测试辅助类，通过静态队列 `QUEUES` 向 Flink 流注入 `(record, timestamp)` 元组。原 `pollNext` 逻辑：
- 取出 `next = (T, Long)`；
- 若 `next.f0 == null`，直接返回 `END_OF_INPUT`（视为输入结束）；
- 若 `next.f1 == null`，`output.collect(next.f0)`（无事件时间）；
- 否则 `output.collect(next.f0, next.f1)`（带事件时间）。

问题：原逻辑无法发送纯 watermark（即没有数据记录、只有 watermark 推进的场景），而 `LockRemover` 的 recovery 测试需要模拟 watermark 推进。修改后：
- 新增 import `org.apache.flink.api.common.eventtime.Watermark`；
- `new SourceReader<T, DummySplit>()` 改为 `new SourceReader<>()`（Java 菱形语法，无功能影响）；
- `pollNext` 重构为三分支：
  - `next.f0 == null`（哨兵）：若 `next.f1 == null` → `END_OF_INPUT`；否则 `output.emitWatermark(new Watermark(next.f1))` 发送 watermark；
  - `next.f0 != null && next.f1 == null`：`output.collect(next.f0)`（无事件时间）；
  - `next.f0 != null && next.f1 != null`：`output.collect(next.f0, next.f1)`（带事件时间）；
- 加 `@SuppressWarnings("unchecked")` 抑制 `QUEUES` 取值的强转告警。

这让测试可以通过 `sendRecord(taskResult)` + `sendWatermark(ts)` 的组合精确控制 watermark 推进节奏，验证 `LockRemover.processWatermark` 的行为。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/TestLockRemover.java`（新增 379 行）

**修改目的**：为 `LockRemover` 提供端到端测试覆盖。

**工作逻辑**：测试类继承 `OperatorTestBase`，使用 `@Timeout(10)` 防止死锁。包含四个测试用例：

1. **`testProcess`**：基本流程。用 `ManualSource<TaskResult>` 注入一个成功任务结果，验证 `LOCK` 被释放（`isHeld() == false`）。
2. **`testInSink`**：将 `LockRemover` 作为 sink 的 post-commit topology 接入（通过自定义 `SinkTest` 实现 `SupportsPostCommitTopology`），验证在真实 sink 提交后 `LockRemover` 仍能正确释放锁。这模拟了生产环境中的接入方式。
3. **`testMetrics`**：注入 6 个 `TaskResult`（task0：2 成功 1 失败；task1：3 成功 0 失败），通过 `MetricsReporterFactoryForTests` 验证 `SUCCEEDED_TASK_COUNTER` 与 `FAILED_TASK_COUNTER` 的值符合预期。
4. **`testRecovery`**：用两个 `ManualSource` union 后接入 `LockRemover`，模拟多上游场景。先发送一个任务结果（解锁 lock 但不解锁 recoveryLock），再分别从两个 source 发送 recovery watermark（ts=10），验证只有当两个 source 的 watermark 都到达后 `RECOVERY_LOCK` 才被释放。这验证了 watermark 在 union 后取最小值的特性——只有所有上游都推进过 recovery 时间点，recoveryLock 才应被释放。

辅助类：
- `TestingLockFactory` / `TestingLock`：内存锁实现，用于测试；
- `SinkTest`：自定义 Sink，实现 `SupportsCommitter` 与 `SupportsPostCommitTopology`，在 `addPostCommitTopology` 中接入 `LockRemover`，用于 `testInSink`。

## 小结

- **成效**：Flink Table Maintenance 模块新增了 `LockRemover` 算子与 `TaskResult` 数据类，补齐了"任务执行后释放锁 + 收集度量"这一关键环节；同时通过 watermark 机制优雅地处理了正常执行与 recovery 两种场景下的锁释放；附带修复了测试辅助类 `ManualSource` 不支持发送纯 watermark 的限制，并重命名 `TriggerManager.taskNames` 提升语义清晰度。
- **影响范围**：仅 `flink/v1.20` 模块，7 个文件、+615/-12 行。其中 4 个新增文件（`LockRemover.java`、`TaskResult.java`、`TestLockRemover.java` + 部分内容）、3 个修改文件（`TableMaintenanceMetrics.java`、`TriggerManager.java`、`ManualSource.java`、`JdbcLockFactory.java`）。所有改动集中在 maintenance operator 子包，不影响其它模块。
- **回迁到 1.4.x 的注意事项**：
  - 这是 Flink Maintenance 模块的功能性新增，**仅作用于 `flink/v1.20`**。1.4.x 若不维护 v1.20 版本（早期 1.4.x 可能只到 v1.18/v1.19），则**无需也无法回迁**——文件路径不存在。
  - 后续提交 #11117（见提交 1150）会将本提交的改动 port 到 `flink/v1.19`。若 1.4.x 维护 v1.19，应优先考虑回迁 1150 而非本提交。
  - 若 1.4.x 维护 v1.20 且需要 Flink Maintenance 功能（该模块标注 `@Experimental`，仍处于实验阶段），则可回迁。需注意：
    - `LockRemover` 依赖 `TriggerLockFactory` 接口，回迁前需确认 1.4.x 的 v1.20 中该接口已存在且签名一致；
    - `ManualSource` 的 watermark 修复是测试基础设施改进，回迁后可让 1.4.x 的相关测试也能模拟 watermark；
    - `TriggerManager` 的重命名是纯重构，可与本提交一起回迁以保持代码一致性，也可单独回迁。
  - 该模块仍处于 `@Experimental` 阶段，API 不稳定，回迁前应评估 1.4.x 用户是否真的在使用该模块——若无人使用，回迁价值有限。
