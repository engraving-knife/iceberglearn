# 提交 2890：Flink: Fix commit duplication in DynamicIcebergSink (#14517)

## 提交信息

- **序号**：2890 / 4088
- **哈希**：2fe4495e978f02513b1348652bf2e87aca317a4e
- **短哈希**：2fe4495e9
- **日期**：2025-11-19 09:41:40 +0100
- **作者**：aiborodin
- **提交说明**：Flink: Fix commit duplication in DynamicIcebergSink (#14517)
- **PR/Issue**：#14517

## 总体目的

Flink 的 `DynamicIcebergSink` 在提交（commit）阶段存在重复提交的问题。当 Flink 作业在 commit 成功之后、但在收到成功确认之前发生失败时，Flink 会根据其 checkpoint 机制重试这次提交。由于 Iceberg 的 commit 操作本身是幂等的（基于 snapshot 的乐观锁），但是 `DynamicCommitter` 在重试时无法识别出该 checkpoint 对应的提交已经完成，导致相同的变更被重复提交到表中，造成数据重复。

这个问题的典型场景是：commit 操作已经成功写入了一个新的 snapshot，但由于网络故障或进程崩溃，Flink 的 committer 没有收到成功响应。当 Flink 重新启动并从上一个 checkpoint 恢复时，它会再次尝试提交相同的 committable，从而产生重复数据。

本提交的目标是让 `DynamicCommitter` 具备识别重复提交的能力，在检测到当前要提交的 checkpoint 已经在表的 snapshot 历史中存在时，跳过本次提交，从而实现真正的幂等性。

## 如何达成设计目的

核心设计思路是在 commit 操作执行前进行校验：通过检查表的 snapshot 祖先链，确认是否已经存在相同 `flink.job-id`、`flink.operator-id` 和 `flink.max-committed-checkpoint-id` 的 snapshot。如果存在，说明本次提交是重复的，应该跳过。

主要修改点包括：

1. **重构 `getMaxCommittedCheckpointId` 方法**：将其参数从 `Table` 和 `branch` 改为 `Iterable<Snapshot>`，使其既能用于 commit 前的查询（用于标记已完成的 committable），也能用于 commit 时的校验。同时将遍历逻辑从手动遍历 parent 链改为使用 `SnapshotUtil.ancestorsOf`。

2. **新增 `MaxCommittedCheckpointIdValidator`**：实现 `SnapshotAncestryValidator` 接口，在 commit 操作执行时被调用，检查 snapshot 祖先链中是否已存在大于等于当前 checkpointId 的提交。如果存在，抛出 `MaxCommittedCheckpointMismatchException`（一种 `ValidationException`）。

3. **在 `commitOperation` 中捕获异常并跳过提交**：当 commit 抛出 `MaxCommittedCheckpointMismatchException` 时，记录日志并直接返回，不抛出异常，从而实现幂等的重试行为。

4. **测试增强**：新增 `DuplicateCommitHook` 模拟并发重复提交场景，新增参数化测试验证在 append 和 overwrite 两种模式下都能正确避免重复提交。

## 修改详情

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicCommitter.java` (+62/-7 lines)

**修改目的**：修复 DynamicCommitter 的重复提交问题，实现 commit 操作的幂等性。

**工作逻辑**：

1. 在 `commit` 方法中，先加载表的最新 snapshot，并使用 `SnapshotUtil.ancestorsOf` 获取祖先链，再传给 `getMaxCommittedCheckpointId` 来标记已完成的 committable。这样避免在 `getMaxCommittedCheckpointId` 内部重复加载表和遍历。

2. 新增内部类 `MaxCommittedCheckpointMismatchException`（继承 `ValidationException`），表示"表已包含暂存的变更"。

3. 新增内部类 `MaxCommittedCheckpointIdValidator` 实现 `SnapshotAncestryValidator`。其 `apply` 方法接收 commit 时 Iceberg 提供的 base snapshots，调用 `getMaxCommittedCheckpointId` 检查是否已存在 >= 当前 stagedCheckpointId 的提交。如果存在则抛出异常阻止本次 commit。

4. 在 `commitOperation` 方法中，通过 `operation.validateWith(new MaxCommittedCheckpointIdValidator(...))` 注册校验器。然后使用 try-catch 捕获 `MaxCommittedCheckpointMismatchException`，捕获后记录 INFO 日志并直接 return，使本次重试被安全跳过。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestDynamicCommitter.java` (+120/-10 lines)

**修改目的**：为重复提交修复添加单元测试，并调整 CommitHook 接口以支持更细粒度的失败注入。

**工作逻辑**：

1. 重构 `CommitHook` 接口：将原来的 `beforeCommit()`、`duringCommit()`、`afterCommit()` 三个方法调整为 `beforeCommit(Collection)`、`beforeCommitOperation()`、`afterCommitOperation()`、`afterCommit()` 四个方法，使注入点更精确（区分 committer 的 commit 方法和内部的 commitOperation 方法）。相应更新 `FailBeforeAndAfterCommit` 实现类，并增加 `failedBeforeCommitOperation`、`failedAfterCommitOperation` 两个标志位。

2. 新增参数化测试 `testThrowsValidationExceptionOnDuplicateCommit`：使用 `DuplicateCommitHook` 在 commitOperation 执行前先触发一次重复提交，验证表最终只有一个 snapshot，且 summary 中包含正确的 checkpointId 等信息。该测试覆盖 append 和 overwrite 两种模式。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestDynamicIcebergSink.java` (+116/-10 lines)

**修改目的**：在集成测试层面验证重复提交的幂等性，并支持 overwrite 模式的测试。

**工作逻辑**：

1. 新增 `DuplicateCommitHook` 静态内部类，模拟并发重复提交场景：在 `beforeCommit` 中保存 commitRequests，在 `beforeCommitOperation` 中使用一个独立的 `DynamicCommitter` 先执行一次提交，从而制造主 committer 重试时遇到重复的场景。

2. 新增参数化集成测试 `testCommitsOnceWhenConcurrentDuplicateCommit`，验证在 append 和 overwrite 模式下，即使发生并发重复提交，最终表中所有 snapshot 的 added-records 总和也等于实际记录数（不会翻倍）。

3. 调整 `executeDynamicSink` 方法签名，新增 `overwrite` 参数，并将数据源从 `createBoundedSource` 改为 `env.fromData`，同时在 sink 构建时调用 `.overwrite(overwrite)`。

4. 调整 `AppendRightBeforeCommit` 以适配新的 CommitHook 接口；将失败重试次数从 3 调整为 4 以适配新增的失败注入点。

## 总结

本提交通过在 commit 操作中引入基于 snapshot 祖先链的校验机制，使 `DynamicCommitter` 能够识别并跳过重复的 checkpoint 提交，从而修复了 Flink 作业恢复重试时可能导致数据重复的问题。这是一个重要的正确性修复，使得 `DynamicIcebergSink` 在故障恢复场景下具备真正的幂等性保证。需要注意的是，此修复针对 Flink 1.20 版本，后续提交 2896 将其 backport 到其他 Flink 版本。
