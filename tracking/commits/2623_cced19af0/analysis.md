# 提交 2623：Compulsorily close coordinator if task is stopped by the connect framework. (#13756)

## 提交信息

- **序号**：2623 / 4088
- **哈希**：cced19af0fbdcb6719db2e09be3025d7e6e3d591
- **短哈希**：cced19af0
- **日期**：2025-09-11 11:35:02 -0700
- **作者**：kumarpritam863
- **提交说明**：Compulsorily close coordinator if task is stopped by the connect framework.
- **PR/Issue**：#13756

## 总体目的

Iceberg 的 Kafka Connect sink 中，当 Connect 框架停止一个 task（如 connector 被停止、rebalance 导致 task 关闭）时，会调用 `IcebergSinkTask.close()`。此前该方法将 `context.assignment()`（当前分配的分区）作为 `closedPartitions` 传给 `committer.close()`。而 `CommitterImpl.close()` 的逻辑是：仅当 `closedPartitions` 包含 leader 分区时才停止 coordinator。

这意味着：如果被停止的 task 不是 leader，或者其分配的分区不含 leader 分区，coordinator 不会被停止。这会导致 coordinator 线程在 task 已停止后仍在运行，可能造成重复提交、资源泄漏或在下次启动时出现异常行为。

此外，`CoordinatorThread.terminate()` 此前只设置了线程的 `terminated` 标志，并未实际调用 `coordinator.terminate()`，导致 coordinator 的 executor 未被关闭。

本提交修复这些问题：当 task 被 Connect 框架停止时，强制关闭 coordinator；并确保 `CoordinatorThread.terminate()` 真正调用 `coordinator.terminate()`；同时在 coordinator 已终止时中止提交以避免不一致。

## 如何达成设计目的

1. **区分"task 停止"与"分区 rebalance"**：`IcebergSinkTask.close()` 改为传入空列表 `List.of()` 而非 `context.assignment()`，作为"task 被显式停止"的信号。`CommitterImpl.close()` 检测到空列表时，无条件停止 coordinator。
2. **始终先停止 worker**：将 `stopWorker()` 提到 `close()` 开头，避免重复提交（无论何种关闭场景）。
3. **补全 coordinator 终止链路**：`CoordinatorThread.terminate()` 增加 `coordinator.terminate()` 调用；`Coordinator` 新增 `volatile boolean terminated` 标志，在 `terminate()` 中先置位再关闭 executor，并在 commit 前检查该标志以中止已终止后的提交。
4. **移除冗余的 `Coordinator.stop()`**：原先 `stop()` 调用 `terminate()` 再 `super.stop()`，但 `terminate()` 已是正确的终止路径，`stop()` 覆盖反而可能绕过新逻辑，故移除。
5. **改进日志与异常信息**：在多处日志和异常消息中加入 `connectorName-taskId` 标识，便于定位问题。

## 修改详情

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/IcebergSinkTask.java` (+2/-1 lines)

**修改目的**：用空列表信号通知 committer "task 被停止"。

**工作逻辑**：导入 `java.util.List`；`close()` 中将 `committer.close(context.assignment())` 改为 `committer.close(List.of())`。空列表表示这不是分区 rebalance，而是 task 整体停止，触发 committer 无条件关闭 coordinator。

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/channel/CommitterImpl.java` (+26/-11 lines)

**修改目的**：重构 `close()` 逻辑，区分 task 停止与分区 rebalance，确保 coordinator 被关闭。

**工作逻辑**：
- `close()` 开头先调用 `stopWorker()`（无论何种场景都停止 worker，避免重复提交）。
- 若未初始化（`isInitialized` 为 false），记录 warn 日志并返回（防御性处理）。
- 若 `closedPartitions` 为空（task 被停止）：记录 info 日志 "Task stopped. Closing coordinator."，调用 `stopCoordinator()` 并返回。
- 若含 leader 分区（rebalance 丢失 leader）：记录日志并 `stopCoordinator()`。
- 最后重置 offset 到最后提交位置（避免数据丢失），原有逻辑保留。
- `processControlEvents()` 中 `NotRunningException` 消息加入 connectorName-taskId。
- `startWorker()` 与 `startCoordinator()` 的日志加入 connectorName-taskId 标识。

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/channel/Coordinator.java` (+10/-6 lines)

**修改目的**：增加终止标志，确保终止后不再提交，并修正终止流程。

**工作逻辑**：
- 新增 `private volatile boolean terminated;` 字段。
- 在 commit 方法中，计算 dataFiles/deleteFiles 后，检查 `if (terminated) { throw new ConnectException("Coordinator is terminated, commit aborted"); }`，防止终止后仍执行提交造成不一致。
- `terminate()` 方法：先 `this.terminated = true;`（置位标志），再 `exec.shutdownNow()`，然后 `awaitTermination`。这样若 commit 正在进行，会因标志位中止。
- 移除了 `@Override public void stop()` 方法（原先调用 `terminate()` + `super.stop()`）。终止统一走 `terminate()` 路径，避免 `stop()` 绕过新逻辑。

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/channel/CoordinatorThread.java` (+1 line)

**修改目的**：补全 coordinator 终止调用。

**工作逻辑**：在 `terminate()` 中，除了设置线程 `this.terminated = true`，新增 `coordinator.terminate();` 调用。此前缺少此调用，导致线程标志虽置位但 coordinator 的 executor 未被实际关闭。

## 总结

本提交修复了 Kafka Connect Iceberg sink 在 task 被 Connect 框架停止时 coordinator 未被正确关闭的问题。通过用空列表信号区分"task 停止"与"分区 rebalance"、在 task 停止时无条件关闭 coordinator、补全 `CoordinatorThread.terminate()` 对 `coordinator.terminate()` 的调用、以及增加终止标志防止终止后提交，避免了 coordinator 残留导致的重复提交与资源泄漏。同时改进了日志可观测性。这是一个重要的健壮性修复。
