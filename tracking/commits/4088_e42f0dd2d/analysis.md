# 提交 4088：Flink: Avoid unnecessary TableLoader.loadTable() in IcebergCommitter subtasks > 0

## 提交信息

- **序号**：4088 / 4088
- **哈希**：e42f0dd2de4f4051252bae13d7a5acd0f18336e9
- **短哈希**：e42f0dd2d
- **日期**：2026-07-24 11:51:34 +0200
- **作者**：GuoYu
- **提交说明**：Flink: Avoid unnecessary TableLoader.loadTable() in IcebergCommitter subtasks > 0 (#17251)
- **PR/Issue**：#17251

## 总体目的

Flink 的 Iceberg sink 在 `IcebergSink#addPreCommitTopology` 中通过 `.global()` shuffle 把所有 committable 路由到 committer 算子的 **subtask 0**。也就是说，无论 committer 算子的并行度是多少，真正执行 commit 的只有 subtask 0，其他 subtask（subtaskId > 0）实际上不会收到任何 commit 请求。

但在原实现中，`IcebergCommitter` 的构造函数对**所有** subtask 都无条件执行了：
- `tableLoader.open()`；
- `tableLoader.loadTable()`（加载表元数据，可能涉及 catalog RPC）；
- 从 table properties 读取 `max-continuous-empty-commits`；
- 创建固定大小的 `workerPool` 线程池。

这意味着在 committer 并行度 > 1 时，subtask 1..N-1 也会各自加载一次表、各创建一个线程池，但这些资源永远不会被使用。这造成：
1. **不必要的 catalog RPC**：每个 subtask 都触发一次 `loadTable`，给 catalog（如 Hive Metastore、REST catalog）增加无谓压力；
2. **资源浪费**：每个 subtask 都创建一个 workerPool 线程池却闲置，占用线程和内存；
3. **潜在的副作用**：`tableLoader.open()` 在非 0 subtask 上重复执行可能引发不必要的连接初始化。

本提交让 `IcebergCommitter` 在构造时根据 `subtaskId` 判断：只有 subtask 0 才执行 `tableLoader.open()`、`loadTable()`、读配置、创建 workerPool；其他 subtask 跳过这些初始化。同时在 `commit` 方法加入状态校验，确保非 0 subtask 永远不会收到 commit 请求（若收到则快速失败），以及在 `close` 方法中防御性地处理未初始化的 workerPool。

## 如何达成设计目的

1. **传入 subtaskId**：`IcebergSink` 在创建 `IcebergCommitter` 时通过 `context.getTaskInfo().getIndexOfThisSubtask()` 获取当前 subtask 编号并传入构造函数。
2. **条件化初始化**：构造函数中把 `tableLoader.open()`、`loadTable()`、读 `max-continuous-empty-commits`、创建 `workerPool` 的代码块用 `if (subtaskId == 0)` 包裹。注释说明这是基于 `.global()` shuffle 路由的假设。
3. **commit 防御**：`commit` 方法开头加 `Preconditions.checkState(subtaskId == 0, ...)`，若非 0 subtask 收到 commit 请求则抛 `IllegalStateException`，快速暴露路由异常。
4. **close 防御**：`close` 方法先检查 `tableLoader.isOpen()` 再关闭（非 0 subtask 未 open），并检查 `workerPool != null` 再 shutdown（非 0 subtask 未创建）。

## 修改详情

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/IcebergCommitter.java` (+34/-14 lines)

**修改目的**：让非 0 subtask 跳过表加载和线程池创建，并加入防御性校验。

**工作逻辑**：

1. **新增字段** `private final int subtaskId;`。

2. **构造函数新增参数** `int subtaskId`，并在构造体中：
```java
this.tableMaintenanceEnabled = tableMaintenanceEnabled;
this.subtaskId = subtaskId;

// IcebergSink#addPreCommitTopology routes all committables to subtask 0 via a .global()
// shuffle, so only subtask 0 needs to load the table and create the worker pool.
if (subtaskId == 0) {
  if (!tableLoader.isOpen()) {
    tableLoader.open();
  }

  this.table = tableLoader.loadTable();
  this.maxContinuousEmptyCommits =
      PropertyUtil.propertyAsInt(table.properties(), MAX_CONTINUOUS_EMPTY_COMMITS, 10);
  Preconditions.checkArgument(
      maxContinuousEmptyCommits > 0, MAX_CONTINUOUS_EMPTY_COMMITS + " must be positive");
  this.workerPool =
      ThreadPools.newFixedThreadPool(
          "iceberg-committer-pool-" + table.name() + "-" + sinkId, workerPoolSize);
}
this.continuousEmptyCheckpoints = 0;
```
   注意 `table`、`maxContinuousEmptyCommits`、`workerPool` 在非 0 subtask 上保持默认值（null/0/null）。

3. **`commit` 方法新增校验**：
```java
@Override
public void commit(Collection<CommitRequest<IcebergCommittable>> commitRequests)
    throws IOException, InterruptedException {
  Preconditions.checkState(
      subtaskId == 0,
      "IcebergCommitter received %s commit request(s) on subtask %s, but only subtask 0 is expected to commit.",
      commitRequests.size(),
      subtaskId);
  ...
}
```
   若非 0 subtask 收到请求，立即抛异常，避免在未初始化的 table/workerPool 上 NPE 或误操作。

4. **`close` 方法防御性改造**：
```java
@Override
public void close() throws IOException {
  if (tableLoader.isOpen()) {
    tableLoader.close();
  }

  if (workerPool != null) {
    workerPool.shutdown();
  }
}
```
   - 非 0 subtask 未调用 `tableLoader.open()`，`isOpen()` 返回 false，跳过 close；
   - 非 0 subtask 的 `workerPool` 为 null，跳过 shutdown。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/IcebergSink.java` (+2/-1 lines)

**修改目的**：把当前 subtask 编号传入 `IcebergCommitter`。

**工作逻辑**：在 `createCommitter` 中调用 `IcebergCommitter` 构造函数时新增参数 `context.getTaskInfo().getIndexOfThisSubtask()`：
```java
return new IcebergCommitter(
    ...
    maintenanceEnabled,
    context.getTaskInfo().getIndexOfThisSubtask());
```

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/sink/TestIcebergCommitter.java` (+50/-7 lines)

**修改目的**：验证 subtask 0 正常 commit、非 0 subtask 收到请求时快速失败。

**工作逻辑**：

1. **新增 `testCommitterSubtaskZeroCommits`**：
   - 用 `getTestHarness(2, 0)` 构造并行度为 2、subtaskIndex 为 0 的测试 harness；
   - open 后写入一条数据，notify checkpoint 完成；
   - 断言表中有该行、snapshot 数为 1、max committed checkpoint id 为 1，验证 subtask 0 端到端 commit 正常。

2. **新增 `testCommitOnNonZeroSubtaskFailsFast`**：
   - 用 `getTestHarness(2, 1)` 构造 subtaskIndex 为 1 的 harness；
   - 写入一条数据，调用 `notifyOfCompletedCheckpoint`；
   - 断言抛出 `IllegalStateException`，消息含 "only subtask 0 is expected to commit"；
   - 断言 snapshot 数为 0，验证快速失败未产生任何提交。

3. **重构 `getTestHarness()`**：拆分为 `getTestHarness()`（默认调用 `getTestHarness(1, 0)`）和带参 `getTestHarness(int parallelism, int subtaskIndex)`，后者用 `new OneInputStreamOperatorTestHarness<>(factory, parallelism, parallelism, subtaskIndex)` 指定并行度和 subtask 索引。

4. **更新既有 `createCommitter` 测试 helper**：调用 `IcebergCommitter` 构造函数时新增 `0` 作为 subtaskId 参数，保持既有测试用 subtask 0 语义。

5. **新增静态导入** `assertThatThrownBy`。

## 总结

一个针对 Flink Iceberg sink committer 的资源优化提交。利用 `.global()` shuffle 把 commit 路由到 subtask 0 的事实，让非 0 subtask 跳过 `tableLoader.loadTable()` 和 workerPool 创建，避免了在 committer 并行度 > 1 时无谓的 catalog RPC 和线程池资源浪费。同时通过 `commit` 方法的 `checkState` 和 `close` 方法的 null/状态检查，保证万一非 0 subtask 误收到请求能快速失败而非产生隐蔽的 NPE 或错误提交。测试覆盖了 subtask 0 正常提交和非 0 subtask 快速失败两种场景。这是本批次（4076-4088）的最后一个提交。
