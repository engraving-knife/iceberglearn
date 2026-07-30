# 提交 0148：Spark 3.5: Set useCommitCoordinator to false in batch writes (#9017)

## 提交信息

- **序号**：0148 / 4088
- **哈希**：6a9c182b47476025da1cfb741cc2f581e955330b
- **短哈希**：6a9c182b4
- **日期**：2023-11-10 11:36:01 -0800
- **作者**：Huaxin Gao
- **提交说明**：Spark 3.5: Set useCommitCoordinator to false in batch writes (#9017)
- **PR/Issue**：#9017

## 总体目的

本提交为 Spark 3.5 模块下所有批写入（batch writes）路径显式覆写 `useCommitCoordinator()` 使其返回 `false`，关掉 Spark DataSource V2 默认开启的“输出提交协调器（OutputCommitCoordinator）”机制。这是 Iceberg 向 Spark 3.5 适配时一个容易被忽视但影响写入正确性的修复。

要理解这个修复，需要先理清 Spark DataSource V2 的提交模型与 `useCommitCoordinator` 的作用。Spark 的 `BatchWrite` 接口定义了 `default boolean useCommitCoordinator() { return true; }`（默认为 `true`）。当返回 `true` 时，Spark 在每个任务（task attempt）调用 `DataWriter.commit()` 之前，会先询问 `OutputCommitCoordinator.canCommit(stageId, stageAttempt, partId, attemptId)`，只有被授权的那一个任务尝试才能调用 `dataWriter.commit()`；未被授权的任务（例如推测执行产生的重复任务，或协调器只放行 attempt 0 时的后续尝试）会抛出 `CommitDeniedException`，从而**跳过** `dataWriter.commit()`，并最终在驱动端的 `WriterCommitMessage[]` 数组中对应分区的槽位留下 `null`。这一机制是为传统文件格式（如 Hadoop `FileOutputCommitter`）设计的——那种格式里每个任务的 commit 会移动/重命名文件到最终位置，必须保证每个分区只有一个任务真正提交，否则会出现重复或冲突。

但 Iceberg 的提交模型与此不同，沿用默认 `true` 反而会引入正确性风险：

1. **Iceberg 的任务级 commit 是无副作用的**。每个任务写出的数据文件路径都包含唯一的 task/attempt ID，互不冲突；`DataWriter.commit()` 只是关闭文件写入器、把写出的 `DataFile` 列表封装成 `TaskCommit` 返回，并不移动文件，也不触碰任何共享/表级状态。真正对 Iceberg 表的原子提交发生在驱动端的 `BatchWrite.commit(messages)` 里（如 `table.newAppend()`、`table.newOverwrite()`、`table.newRowDelta()` 一次性把所有文件提交为一个新的快照）。
2. **被拒绝的任务会导致静默数据丢失或不完整重写**。当协调器拒绝某个任务的 commit 时，该任务不调用 `dataWriter.commit()`，对应分区的 `WriterCommitMessage` 为 `null`。而 Iceberg 在 [`SparkWrite.files(messages)`](spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SparkWrite.java) 里收集数据文件时对 `null` 是直接跳过的——这意味着该分区写出的文件会被静默丢弃。对于普通追加/覆盖写入，这表现为部分分区的数据丢失；对于 `RewriteFiles`/`PositionDeletesRewrite` 这类只“暂存（stage）”文件、由后续独立作业真正提交的重写路径，则会导致重写结果不完整。
3. **Iceberg 不需要协调器来防止重复提交**。因为根本不存在“任务级共享存储提交”这一步，唯一的表级提交由驱动端单点完成、原子生效。推测执行产生的重复任务各自写出唯一命名的文件，Spark 在 `useCommitCoordinator=false` 时会保证“每个分区只把一个成功任务的 commit 消息传给驱动，其余被忽略”，重复文件则作为孤儿文件由 `abort()`/孤儿文件清理善后。

因此把 `useCommitCoordinator()` 设为 `false` 才与 Iceberg 的提交模型相匹配：让所有成功完成的任务尝试都能调用 `dataWriter.commit()` 产出各自的 `DataFile` 列表，由 Spark 在驱动端为每个分区挑选一个成功消息，再由 Iceberg 驱动端做一次原子表提交。这避免了协调器拒绝 commit 带来的静默丢数据/不完整重写风险。

本提交是 Iceberg 在 Spark 3.5 批写入侧补齐这一覆写；后续提交 0149（PR #9027）补齐 Spark 3.5 流写入侧，0150（PR #9028）把本提交 cherry-pick 到 Spark 3.4 批写入侧，三者构成同一主题的系列改动。

## 如何达成设计目的

设计思路很直接：在 Spark 3.5 模块下所有实现 `BatchWrite`（或 Iceberg 自定义 `DeltaWrite`/`DeltaBatchWrite`）的写入类中，覆写 `useCommitCoordinator()` 返回 `false`。改动覆盖三条批写入路径：通用的 `SparkWrite.BaseBatchWrite`（所有普通批写入的基类）、`SparkPositionDeltaWrite` 内的 `PositionDeltaBatchWrite`（行级更新/删除的 position delta 批写入）、以及 `SparkPositionDeletesRewrite` 内的写入类（位置删除文件的重写）。三处均在各自的 `commit(...)` 方法前插入同一个 5 行覆写方法，统一把协调器关掉。

## 修改详情

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SparkWrite.java`

**修改目的**：为通用批写入基类 `BaseBatchWrite` 关闭 Spark 提交协调器。

**工作逻辑**：`BaseBatchWrite` 是 `SparkWrite` 内所有批写入子类（`BatchAppend`、`DynamicOverwrite`、`OverwriteByFilter`、`CopyOnWriteOperation`、`RewriteFiles` 等）的抽象基类，实现 `BatchWrite`。在此之前它没有覆写 `useCommitCoordinator()`，因此继承接口默认值 `true`。本次在 `createBatchWriterFactory(...)` 与 `abort(...)` 之间插入：

```java
@Override
public boolean useCommitCoordinator() {
  return false;
}
```

由于它是基类，这一处覆写对全部批写入子类生效。其中 `RewriteFiles`/`CopyOnWriteOperation` 的 `commit()` 只是暂存或提交文件到 Iceberg 表，任务级 `DataWriter.commit()` 仅产出 `DataFile`，关闭协调器后可避免被拒绝任务的文件被静默丢弃。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SparkPositionDeltaWrite.java`

**修改目的**：为行级 position delta 批写入 `PositionDeltaBatchWrite` 关闭 Spark 提交协调器。

**工作逻辑**：`PositionDeltaBatchWrite` 实现 Iceberg 自定义的 `DeltaBatchWrite`（用于 `MERGE INTO`/`UPDATE`/`DELETE` 走 position delta 的路径），其 `commit(messages)` 会构造 `table.newRowDelta()` 把数据文件与删除文件一起提交。它在 `createBatchWriterFactory(...)` 与 `commit(...)` 之间同样插入 `useCommitCoordinator()` 返回 `false` 的覆写。值得注意的是 `DeltaBatchWrite` 继承自 Spark 的 `BatchWrite`，因而同样受 `useCommitCoordinator` 默认 `true` 的影响，必须显式覆写。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SparkPositionDeletesRewrite.java`

**修改目的**：为位置删除文件重写路径的写入类关闭 Spark 提交协调器。

**工作逻辑**：`SparkPositionDeletesRewrite` 用于重写位置删除文件（position deletes），其 `commit(messages)` 调用 `PositionDeletesRewriteCoordinator.get().stageRewrite(...)` 把任务写出的删除文件**暂存**起来，真正的 Iceberg 表提交由后续独立的 Spark 作业完成。这条路径对“任务被协调器拒绝”尤为敏感：一旦某任务的 `dataWriter.commit()` 被跳过，其删除文件就不会进入暂存集合，重写结果即不完整。在 `createBatchWriterFactory(...)` 与 `commit(...)` 之间插入 `useCommitCoordinator()` 返回 `false` 的覆写，确保每个成功任务都能产出删除文件并参与暂存。

## 小结

本提交通过在 Spark 3.5 三条批写入路径上显式将 `useCommitCoordinator()` 置为 `false`，使 Spark 的输出提交协调器不再干预 Iceberg 的任务级提交，从而与 Iceberg“任务级无副作用、驱动端原子提交”的写入模型对齐，消除了协调器拒绝 commit 带来的静默丢数据与不完整重写风险。
