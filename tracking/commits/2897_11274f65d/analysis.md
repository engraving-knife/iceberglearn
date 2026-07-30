# 提交 2897：Flink: Backport fix commit duplication in DynamicIcebergSink (#14637)

## 提交信息

- **序号**：2897 / 4088
- **哈希**：11274f65dc5972a9d260b3c64e7026ab97fd08f0
- **短哈希**：11274f65d
- **日期**：2025-11-20 20:00:39 +1100
- **作者**：aiborodin
- **提交说明**：Flink: Backport fix commit duplication in DynamicIcebergSink (#14637)
- **PR/Issue**：#14637

## 总体目的

本提交是一个回移（backport）操作，将 main 分支上修复 DynamicIcebergSink 重复提交的改动（原始提交 `2fe4495e9`，PR #14517，2025-11-19 合入）回移到 Flink v2.0 与 v2.1 两个版本目录。原始提交只改动了 main 分支维护的 Flink 版本目录（3 个文件），本 backport 将同一修复应用到 `flink/v2.0` 与 `flink/v2.1` 两套目录共 6 个文件。

修复要解决的问题是：DynamicIcebergSink 的 `DynamicCommitter` 在提交变更到 Iceberg 表时，可能因为失败后的重试导致同一批变更被重复提交。具体场景是——当一次 commit 操作在服务端已实际成功（或已 stage），但由于网络故障、JVM 异常等原因 committer 未能收到成功确认，Flink 会触发作业重启并重试同一 checkpoint 的提交。现有逻辑虽然在重试前会通过遍历快照祖先链查找已提交的最大 checkpoint id 来跳过已完成的 `FilesCommittable`，但这一预检查与真正执行 commit 之间存在时间窗口：若在 commit 操作执行期间表状态已发生变化（例如并发的重复提交、或上一轮已成功提交），仅靠预检查无法阻止重复提交，从而导致同一批数据文件被提交两次，造成数据重复。

回移的原因是该缺陷同样影响已发布/维护中的 Flink 2.0 与 2.1 集成版本，需要把修复同步到这两个版本目录以避免用户在生产中遭遇数据重复。

## 如何达成设计目的

核心思路是利用 Iceberg 提交操作的 `validateWith` 乐观并发校验机制，在真正执行 commit 时基于当前表的最新快照祖先重新检查"是否已存在同一 checkpoint 的已提交记录"，若存在则判定为重复提交并安全跳过，而非再次提交。为此：重构 `getMaxCommittedCheckpointId` 使其接收预计算的祖先快照迭代器（便于预检查与校验器复用同一计算逻辑）；新增 `MaxCommittedCheckpointIdValidator`（实现 `SnapshotAncestryValidator`）在 commit 前校验；新增 `MaxCommittedCheckpointMismatchException`（继承 `ValidationException`）作为重复提交的信号；在 `commitOperation` 中用 `validateWith` 注册校验器并捕获该异常，捕获后记录日志并跳过提交。同时重构测试的 `CommitHook` 钩子以更精细地模拟提交各阶段失败，并新增并发重复提交的测试用例。

## 修改详情

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicCommitter.java` (+69/-6 lines)

**修改目的**：在 `DynamicCommitter` 的提交流程中加入重复提交检测与跳过逻辑。

**工作逻辑**：
- **重构 `getMaxCommittedCheckpointId` 的签名与祖先遍历**：原方法签名为 `getMaxCommittedCheckpointId(Table table, String flinkJobId, String operatorId, String branch)`，内部通过 `table.snapshot(branch)` 取起始快照，再用 `snapshot.parentId()` 沿父链向上遍历。改为 `getMaxCommittedCheckpointId(Iterable<Snapshot> ancestors, String flinkJobId, String operatorId)`，直接对传入的祖先快照迭代遍历，把祖先计算的职责上移到调用方。相应地，在调用处（`commit` 方法）预先用 `table.snapshot(entry.getKey().branch())` 取最新快照，再通过 `SnapshotUtil.ancestorsOf(latestSnapshot.snapshotId(), table::snapshot)` 构造祖先迭代器（若分支无快照则用 `List.of()`）。这样同一份祖先既用于预检查标记已完成 committable，也可传给校验器复用。

- **新增 `MaxCommittedCheckpointIdValidator` 与 `MaxCommittedCheckpointMismatchException`**：`MaxCommittedCheckpointMismatchException` 继承 `ValidationException`，固定消息 `"Table already contains staged changes."`。`MaxCommittedCheckpointIdValidator` 实现 `SnapshotAncestryValidator`，持有 `stagedCheckpointId`、`flinkJobId`、`flinkOperatorId`；其 `apply(Iterable<Snapshot> baseSnapshots)` 调用 `getMaxCommittedCheckpointId` 重新计算已提交的最大 checkpoint id，若 `maxCommittedCheckpointId >= stagedCheckpointId` 则抛出 `MaxCommittedCheckpointMismatchException`，否则返回 `true`。

- **在 `commitOperation` 中注册校验并捕获异常**：在 `operation.toBranch(branch)` 之后、`operation.commit()` 之前，调用 `operation.validateWith(new MaxCommittedCheckpointIdValidator(checkpointId, newFlinkJobId, operatorId))`。这样 Iceberg 在真正提交时会基于提交时刻的最新 base 快照祖先执行该校验。随后将原来的 `operation.commit()` 包入 try-catch：捕获 `MaxCommittedCheckpointMismatchException` 时，记录一条 INFO 日志（说明分支已包含该 checkpoint 的变更、可能因失败未收到成功确认导致 Flink 重试同一批变更）并直接 `return` 跳过本次提交；正常情况继续记录提交耗时日志。由于 `validateWith` 的校验与 commit 在同一乐观并发事务中，校验失败时 Iceberg 会自动 abort，避免实际写入。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestDynamicCommitter.java` (+110/-15 lines)

**修改目的**：为重复提交修复新增测试，并重构提交阶段失败模拟钩子以匹配新的提交流程。

**工作逻辑**：
- **重构 `CommitHook` 接口**：原接口有三个方法 `beforeCommit()`、`duringCommit()`、`afterCommit()`。改为 `beforeCommit(Collection<CommitRequest<DynamicCommittable>>)`、`beforeCommitOperation()`、`afterCommitOperation()`、`afterCommit()`，且除 `beforeCommit` 外均提供默认空实现。这把原先语义模糊的"提交中"失败细分为 `commitOperation` 调用前与调用后两个时机，更精确对应新流程（校验/提交在 `commitOperation` 内）。

- **更新 `FailBeforeAndAfterCommit`**：静态字段由 `failedDuringCommit` 拆分为 `failedBeforeCommitOperation` 与 `failedAfterCommitOperation`，相应的失败模拟阶段从 3 次增加到 4 次（beforeCommit → beforeCommitOperation → afterCommitOperation → afterCommit）。`CommitHookEnabledDynamicCommitter.commitOperation` 在调用 `super.commitOperation(...)` 前后分别触发 `beforeCommitOperation` 与 `afterCommitOperation`。

- **新增 `testThrowsValidationExceptionOnDuplicateCommit`**：参数化测试（`@ValueSource(booleans = {false, true})` 覆盖 overwrite 模式开关），构造一个 `DynamicCommittable`（checkpoint id=1），用 `DuplicateCommitHook` 先让一个独立的 `DynamicCommitter` 并发提交同一请求，再用主 committer 提交同一请求，断言最终表只有一个快照，且快照 summary 包含正确的 `flink.job-id`、`flink.max-committed-checkpoint-id`、`flink.operator-id` 等属性，验证重复提交被跳过。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestDynamicIcebergSink.java` (+114/-10 lines)

**修改目的**：在端到端 Flink sink 测试中新增并发重复提交场景，并适配钩子重构与 overwrite 参数。

**工作逻辑**：
- **新增 `testCommitsOnceWhenConcurrentDuplicateCommit`**：参数化测试（覆盖 overwrite 开关），通过 `DuplicateCommitHook` 模拟生产中可能出现的并发重复提交（如使用 REST catalog 时）。`DuplicateCommitHook` 在 `beforeCommit` 中缓存请求列表，在 `beforeCommitOperation` 中用另一个 `DynamicCommitter` 先提交这批请求（模拟并发提交），随后主 committer 再提交时触发重复检测。测试断言非 overwrite 模式下结果正确，且所有快照的 `added-records` 之和等于记录数（证明没有重复写入）。

- **新增 `DuplicateCommitHook` 内部类**：用静态 `hasTriggered` 标志在 Flink 重启后保持状态，持有 `SerializableSupplier<DynamicCommitter>` 用于构造并发提交的 committer；`beforeCommit` 缓存请求，`beforeCommitOperation` 执行一次并发提交后清空缓存并置位。

- **适配钩子重构**：`AppendRightBeforeCommit` 原先在 `duringCommit` 中制造冲突（`table.newAppend()`），现改在 `beforeCommitOperation` 中执行；删除多余的空方法。`executeDynamicSink` 新增带 `boolean overwrite` 参数的重载，原方法委托给新重载（`overwrite=false`）；两处 `DynamicIcebergSink` 构建链路都新增 `.overwrite(overwrite)` 调用。`CommitHookDynamicIcebergSink` 新增 `overwriteMode` 字段，从 `flinkWriteConf.overwriteMode()` 取值并传给 committer。失败重试次数从 3 调整为 4（`RESTART_STRATEGY_FIXED_DELAY_ATTEMPTS`），以匹配现在模拟的 4 个失败阶段。

### `flink/v2.0/...` 下三个文件（与 v2.1 同名，+324/-38 lines 合计）

**修改目的**：将上述相同的修复与测试改动同步到 Flink v2.0 版本目录。

**工作逻辑**：`flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicCommitter.java`、`TestDynamicCommitter.java`、`TestDynamicIcebergSink.java` 三个文件的改动与 v2.1 目录完全一致（行数相同：DynamicCommitter +69/-6、TestDynamicCommitter +110/-15、TestDynamicIcebergSink +114/-10），是同一 backport 在另一维护版本上的复制应用，工作逻辑同上三节所述。

## 总结

本提交将 main 分支修复 DynamicIcebergSink 重复提交的改动（PR #14517）回移到 Flink v2.0 与 v2.1 两个版本目录。修复通过 Iceberg 的 `validateWith` 乐观并发校验机制，在 commit 时基于最新表状态重新检查是否已存在同一 checkpoint 的提交记录，若检测到重复则安全跳过，从根本上消除了失败重试导致的重复数据提交问题。同时重构了测试钩子以精细模拟提交各阶段失败，并新增并发重复提交的端到端测试。该修复对保障 Flink sink 在生产中 Exactly-once 语义的正确性具有重要意义。
