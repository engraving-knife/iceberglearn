# 提交 0149：Spark 3.5: Set useCommitCoordinator to false in streaming writes (#9027)

## 提交信息

- **序号**：0149 / 4088
- **哈希**：13fd06d90ddc6f5c6bacf79b2c0dbe64838e7b65
- **短哈希**：13fd06d90
- **日期**：2023-11-11 10:15:38 -0800
- **作者**：Huaxin Gao
- **提交说明**：Spark 3.5: Set useCommitCoordinator to false in streaming writes (#9027)
- **PR/Issue**：#9027

## 总体目的

本提交是提交 0148（PR #9017，关闭批写入的提交协调器）的流写入对应版本：为 Spark 3.5 模块下的流写入（streaming writes）显式覆写 `useCommitCoordinator()` 返回 `false`，把 Spark DataSource V2 默认开启的输出提交协调器（OutputCommitCoordinator）在流式写入路径上也关掉。

动机与 0148 完全一致，只是作用域从批写入换成了流写入。Spark 的 `StreamingWrite` 接口在 Spark 3.5 才新增了 `useCommitCoordinator()` 方法（默认返回 `true`），这也是本系列只对 Spark 3.5 做流写入覆写、而不涉及 Spark 3.4/3.3 流写入的原因——老版本 Spark 的 `StreamingWrite` 接口根本不存在该方法。

回顾 Iceberg 的提交模型与协调器的冲突（详见 0148 的分析）：Iceberg 的任务级 `DataWriter.commit()` 是无副作用的，只产出唯一命名的数据文件列表（`TaskCommit`），真正的表级原子提交发生在驱动端的 `StreamingWrite.commit(epochId, messages)` 里。若沿用 `useCommitCoordinator=true`，被协调器拒绝的任务会跳过 `dataWriter.commit()`，对应分区在该 epoch 的 `WriterCommitMessage` 为 `null`，而 Iceberg 在收集文件时对 `null` 直接跳过——这会导致该 epoch 内部分分区的微批数据被静默丢失。流式场景下这种丢数据尤其危险：微批按 epoch 提交，丢失的分区数据不会在后续 epoch 重放（epoch 已推进），造成静默的 exactly-once 破坏。

把 `useCommitCoordinator()` 设为 `false` 后，所有成功完成的任务都能调用 `dataWriter.commit()` 产出数据文件，Spark 在驱动端为每个分区挑选一个成功消息传给 `StreamingWrite.commit(epochId, messages)`，由 Iceberg 做一次原子表提交。Iceberg 的流式提交本身还通过 `queryId` + `epochId` 写入快照摘要、并在 `commit(epochId, ...)` 里检查 `lastCommittedEpochId` 来实现 epoch 级幂等（重提交时跳过已提交的 epoch），因此关掉协调器不会破坏 exactly-once 语义。本提交与 0148（批）、0150（Spark 3.4 批 cherry-pick）同属一个系列。

## 如何达成设计目的

在 Spark 3.5 的 `SparkWrite` 中，所有流写入子类（`StreamingAppend`、`StreamingOverwrite` 等）都继承自抽象基类 `BaseStreamingWrite`。只需在 `BaseStreamingWrite` 这一处覆写 `useCommitCoordinator()` 返回 `false`，即可对全部流写入子类生效。改动只有一处 5 行方法插入。

## 修改详情

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SparkWrite.java`

**修改目的**：为流写入基类 `BaseStreamingWrite` 关闭 Spark 提交协调器。

**工作逻辑**：`BaseStreamingWrite` 实现 `StreamingWrite`，是 `StreamingAppend`、`StreamingOverwrite` 等流写入子类的公共基类。其 `commit(epochId, messages)` 是 `final` 方法，会先 `table.refresh()`，再查找该 `queryId` 下已提交的最大 `epochId`（`findLastCommittedEpochId()`），若当前 `epochId` 已提交则跳过，否则调用 `doCommit(epochId, messages)` 由子类执行真正的 `SnapshotUpdate` 提交并把 `queryId`/`epochId` 写入快照摘要——这套机制保证了 epoch 级幂等。

在此之前，`BaseStreamingWrite` 没有覆写 `useCommitCoordinator()`，继承 Spark 3.5 `StreamingWrite` 接口的默认值 `true`。本次在 `createStreamingWriterFactory(...)` 与 `commit(long epochId, ...)` 之间插入：

```java
@Override
public boolean useCommitCoordinator() {
  return false;
}
```

这样每个流式任务成功完成后都会调用 `dataWriter.commit()` 产出该 epoch 该分区的数据文件，Spark 在驱动端为每个分区取一个成功消息交给 `commit(epochId, messages)`，再由 Iceberg 依据 `epochId` 做幂等原子提交，避免了协调器拒绝 commit 导致的微批分区数据静默丢失。由于覆写在基类，`StreamingAppend`/`StreamingOverwrite` 等所有流写入模式同时受益。

## 小结

本提交为 Spark 3.5 流写入基类 `BaseStreamingWrite` 显式将 `useCommitCoordinator()` 置为 `false`，把输出提交协调器在流式路径上关闭，使 Iceberg 的 epoch 级幂等原子提交模型不再受协调器拒绝 commit 的干扰，避免微批分区数据被静默丢失，与 0148 批写入侧的修复共同覆盖 Spark 3.5 的全部写入路径。
