# 提交 0150：Spark 3.4: Set useCommitCoordinator to false in batch writes (#9028)

## 提交信息

- **序号**：0150 / 4088
- **哈希**：d7f8e91c0983048768be1cb17893a485088da937
- **短哈希**：d7f8e91c0
- **日期**：2023-11-11 10:17:24 -0800
- **作者**：Huaxin Gao
- **提交说明**：Spark 3.4: Set useCommitCoordinator to false in batch writes (#9028)
- **PR/Issue**：#9028

## 总体目的

本提交把提交 0148（PR #9017，Spark 3.5 批写入关闭提交协调器）cherry-pick 到 Spark 3.4 模块，为 Spark 3.4 下所有批写入（batch writes）路径显式覆写 `useCommitCoordinator()` 返回 `false`。提交说明明确写道：“This change cherrypicks PR #9017 to Spark 3.4.”

动机与 0148 完全相同：Spark DataSource V2 的 `BatchWrite.useCommitCoordinator()` 默认返回 `true`，会启用 Spark 的输出提交协调器（OutputCommitCoordinator）。被协调器拒绝的任务会跳过 `dataWriter.commit()`，对应分区的 `WriterCommitMessage` 为 `null`，而 Iceberg 在 [`SparkWrite.files(messages)`](spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/SparkWrite.java) 里收集数据文件时直接跳过 `null`——这会导致被拒分区写出的文件被静默丢弃，对普通追加/覆盖写入表现为部分分区数据丢失，对 `RewriteFiles`/`PositionDeletesRewrite` 这类只暂存文件、由后续独立作业提交的重写路径则导致重写结果不完整。

Iceberg 的任务级 `DataWriter.commit()` 本身无副作用（只产出唯一命名的 `DataFile` 列表，不移动文件、不触碰表级状态），真正的原子提交由驱动端 `BatchWrite.commit(messages)` 单点完成，因此不需要协调器来防止重复提交。把 `useCommitCoordinator()` 设为 `false`，让所有成功任务都产出数据文件，由 Spark 在驱动端为每个分区挑选一个成功消息，再由 Iceberg 做一次原子表提交，重复任务写出的文件作为孤儿文件由 `abort()`/孤儿文件清理善后。

由于 Iceberg 需要同时维护 Spark 3.2/3.3/3.4/3.5 多个分支模块，同一修复需分别落地到各模块。本提交即该修复在 Spark 3.4 批写入侧的落地，与 0148（Spark 3.5 批）、0149（Spark 3.5 流）同属一个系列。需要说明的是，Spark 3.5 的 `StreamingWrite` 接口在 3.5 才新增 `useCommitCoordinator()` 方法，而 Spark 3.4 的 `StreamingWrite` 没有该方法，故本系列在 3.4 侧只需处理批写入、无需处理流写入。

## 如何达成设计目的

与 0148 的改动结构完全一致，只是把同样三处覆写应用到 `spark/v3.4/` 对应的三个文件：通用批写入基类 `SparkWrite.BaseBatchWrite`、`SparkPositionDeltaWrite.PositionDeltaBatchWrite`、`SparkPositionDeletesRewrite` 的写入类。三处均在各自 `commit(...)` 方法前插入同一个 5 行覆写方法 `useCommitCoordinator()` 返回 `false`。

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/SparkWrite.java`

**修改目的**：为 Spark 3.4 通用批写入基类 `BaseBatchWrite` 关闭 Spark 提交协调器。

**工作逻辑**：`BaseBatchWrite` 是 `SparkWrite` 内所有批写入子类（`BatchAppend`、`DynamicOverwrite`、`OverwriteByFilter`、`CopyOnWriteOperation`、`RewriteFiles` 等）的抽象基类，实现 `BatchWrite`。此前未覆写 `useCommitCoordinator()`，继承接口默认值 `true`。本次在 `createBatchWriterFactory(...)` 与 `abort(...)` 之间插入：

```java
@Override
public boolean useCommitCoordinator() {
  return false;
}
```

因覆写在基类，全部批写入子类同时生效。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/SparkPositionDeltaWrite.java`

**修改目的**：为 Spark 3.4 行级 position delta 批写入 `PositionDeltaBatchWrite` 关闭 Spark 提交协调器。

**工作逻辑**：`PositionDeltaBatchWrite` 实现 `DeltaBatchWrite`（用于 `MERGE INTO`/`UPDATE`/`DELETE` 走 position delta 的路径），其 `commit(messages)` 构造 `table.newRowDelta()` 把数据文件与删除文件一起提交。在 `createBatchWriterFactory(...)` 与 `commit(...)` 之间插入 `useCommitCoordinator()` 返回 `false` 的覆写。`DeltaBatchWrite` 继承自 Spark `BatchWrite`，同样受默认 `true` 影响，需显式覆写。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/SparkPositionDeletesRewrite.java`

**修改目的**：为 Spark 3.4 位置删除文件重写路径的写入类关闭 Spark 提交协调器。

**工作逻辑**：`SparkPositionDeletesRewrite` 用于重写位置删除文件，其 `commit(messages)` 调用 `PositionDeletesRewriteCoordinator.get().stageRewrite(...)` 把任务写出的删除文件**暂存**，真正提交由后续独立 Spark 作业完成。这条路径对“任务被协调器拒绝”尤为敏感——一旦某任务的 `dataWriter.commit()` 被跳过，其删除文件就不会进入暂存集合，重写结果即不完整。在 `createBatchWriterFactory(...)` 与 `commit(...)` 之间插入 `useCommitCoordinator()` 返回 `false` 的覆写，确保每个成功任务都能产出删除文件并参与暂存。

## 小结

本提交将 0148（Spark 3.5 批写入关闭提交协调器）的修复 cherry-pick 到 Spark 3.4 模块，在三条批写入路径上显式将 `useCommitCoordinator()` 置为 `false`，使 Spark 3.4 下的 Iceberg 批写入不再受输出提交协调器拒绝 commit 的影响，避免静默丢数据与不完整重写，与 0148/0149 共同把该修复覆盖到 Spark 3.4/3.5 的全部适用写入路径。
