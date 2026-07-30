# 提交 1914：Spark 3.4: Propagate snapshot properties / Add max allowed failed commits

## 提交信息

- **序号**：1914 / 4088
- **哈希**：50c8697f937e0fea3c87fe8422bed881792a8129
- **短哈希**：50c8697f9
- **日期**：2025-03-24 09:14:30 -0600
- **作者**：Eduard Tudenhoefner
- **提交说明**：Spark 3.4: Propagate snapshot properties / Add max allowed failed commits
- **PR/Issue**：backports #9449 和 #9611 到 Spark 3.4

## 总体目的

本提交把两个 PR（#9449、#9611）回移到 Spark 3.4 模块，修复 `RewriteDataFilesSparkAction` 与 `RewritePositionDeleteFilesSparkAction` 两个 action 的两类问题：

1. **快照属性丢失（#9449 Propagate snapshot properties）**：用户可以通过 `snapshotProperty(key, value)` 给 action 产生的快照附加自定义属性，这些属性存放在 `BaseSnapshotUpdateSparkAction.summary` 里。但 action 内部创建的 commit manager（`RewriteDataFilesCommitManager` / `RewritePositionDeletesCommitManager`）此前并没有接收这个 summary，导致在 partial progress 等场景下由 commit manager 自行提交时，用户设置的快照属性没有被写入快照 summary，内部产生的指标属性也可能丢失。本提交让 commit manager 接收并使用 `commitSummary()`。

2. **部分进度模式缺少失败提交上限（#9611 Add max allowed failed commits）**：在 `PARTIAL_PROGRESS_ENABLED=true` 时，原 `doExecuteWithPartialProgress` 仅当"没有任何提交成功"时记一条 error 日志（不抛异常），其余情况静默。这使运维无法控制"允许多少个提交失败"。本提交新增 `PARTIAL_PROGRESS_MAX_FAILED_COMMITS` 选项：失败提交数在阈值内时仅 warn，超过阈值则抛 `RuntimeException`，从而让重写操作在失败过多时显式失败而非静默继续。

## 如何达成设计目的

1. 在 `BaseSnapshotUpdateSparkAction` 新增 `commitSummary()` 返回 `summary` 的不可变副本，供子类把快照属性传给 commit manager。
2. `RewriteDataFilesSparkAction.commitManager(...)` 与 `RewritePositionDeleteFilesSparkAction.commitManager()` 把 `commitSummary()` 传给各自 commit manager 构造器（commit manager 内部会用它设置每次 commit 的 snapshot-property）。
3. `RewriteDataFilesSparkAction` 新增 `maxFailedCommits` 字段与 `PARTIAL_PROGRESS_MAX_FAILED_COMMITS` 选项（默认值 `maxCommits`，即默认容忍全部失败）；`doExecuteWithPartialProgress` 改为：`failedCommits = maxCommits - commitService.succeededCommits()`，若 `0 < failedCommits <= maxFailedCommits` 则 warn，若 `failedCommits > maxFailedCommits` 则抛 `RuntimeException`。
4. 顺带把"成功结果"转换抽成 `toRewriteResults(...)` 私有方法，便于读性；并把 `VALID_OPTIONS` 集合加入 `PARTIAL_PROGRESS_MAX_FAILED_COMMITS`。
5. 测试：新增 `testParallelPartialProgressWithMaxFailedCommits`（mock 失败若干组、设 maxFailedCommits=0 验证抛异常且数据未变、快照数正确）、`testSnapshotProperty`（验证自定义 snapshot-property 与内部指标属性都被写入快照）。

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/actions/BaseSnapshotUpdateSparkAction.java` (修改, +5 lines)

**修改目的**：暴露快照属性摘要给子类。

**工作逻辑**：

```java
protected Map<String, String> commitSummary() {
  return ImmutableMap.copyOf(summary);
}
```

返回 `summary`（`Map<String, String>`）的不可变副本，子类 commit manager 构造时可拿到用户设置的 snapshot-property。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteDataFilesSparkAction.java` (修改, +33/-7 lines)

**修改目的**：传播快照属性并加入失败提交上限。

**工作逻辑**：

- `VALID_OPTIONS` 加入 `PARTIAL_PROGRESS_MAX_FAILED_COMMITS`；新增字段 `private int maxFailedCommits;`。
- `commitManager(startingSnapshotId)` 改为 `new RewriteDataFilesCommitManager(table, startingSnapshotId, useStartingSequenceNumber, commitSummary())`，把快照属性传进去。
- `doExecuteWithPartialProgress` 末尾逻辑改写：

```java
int failedCommits = maxCommits - commitService.succeededCommits();
if (failedCommits > 0 && failedCommits <= maxFailedCommits) {
  LOG.warn("... {} rewrite commits failed. ...", PARTIAL_PROGRESS_ENABLED, failedCommits, PARTIAL_PROGRESS_MAX_COMMITS);
} else if (failedCommits > maxFailedCommits) {
  throw new RuntimeException(String.format(
      "%s is true but %d rewrite commits failed. This is more than the maximum allowed failures of %d. ...",
      PARTIAL_PROGRESS_ENABLED, failedCommits, maxFailedCommits, PARTIAL_PROGRESS_MAX_COMMITS));
}
```

- 成功结果转换抽成 `toRewriteResults(commitService.results())`。
- `validateAndInitOptions`（选项初始化）中：

```java
maxFailedCommits = PropertyUtil.propertyAsInt(
    options(), PARTIAL_PROGRESS_MAX_FAILED_COMMITS, maxCommits);
```

默认为 `maxCommits`，即默认容忍全部失败（warn）以保持向后兼容。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/actions/RewritePositionDeleteFilesSparkAction.java` (修改, +1/-1 lines)

**修改目的**：把快照属性传给 position-delete commit manager。

**工作逻辑**：`commitManager()` 改为 `new RewritePositionDeletesCommitManager(table, commitSummary())`。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteDataFilesAction.java` (修改, +60 lines)

**修改目的**：验证失败提交上限与快照属性。

**工作逻辑**：

`testParallelPartialProgressWithMaxFailedCommits`：用 Mockito spy 让组 1/3/7 的 `rewriteFiles` 抛异常，设 `PARTIAL_PROGRESS_MAX_COMMITS=3`、`PARTIAL_PROGRESS_MAX_FAILED_COMMITS=0`，断言 `execute()` 抛 `RuntimeException` 且消息含 "1 rewrite commits failed. This is more than the maximum allowed failures of 0"；并验证数据未变、3 个快照、无孤儿文件、缓存干净。

`testSnapshotProperty`：执行 `basicRewrite(table).snapshotProperty("key", "value")`，断言快照 summary 含 `key=value`，并断言内部指标键（`ADDED_FILES_PROP`、`DELETED_FILES_PROP`、`TOTAL_DATA_FILES_PROP`、`CHANGED_PARTITION_COUNT_PROP`）仍存在，确保自定义属性不会覆盖内部属性。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewritePositionDeleteFilesAction.java` (修改, +36/-1 lines)

**修改目的**：验证 position-delete 重写的快照属性。

**工作逻辑**：新增 `testSnapshotProperty`：写数据文件与 position deletes，执行 `SparkActions.get(spark).rewritePositionDeletes(table).snapshotProperty("key", "value")`，断言快照 summary 含 `key=value` 且内部 delete 相关指标键（`ADDED_DELETE_FILES_PROP`、`ADDED_POS_DELETES_PROP`、`REMOVED_DELETE_FILES_PROP` 等）仍存在。同时把一处 `assertEquals("Rows", ...)` 改为 `assertEquals("Rows must match", ...)`。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewritePositionDeleteFilesAction.java` (修改, +20/-20 lines)

**修改目的**：统一断言消息文案。

**工作逻辑**：把多处 `assertEquals("Rows", ...)` / `assertEquals("Position deletes", ...)` 的消息字符串改为 `"Rows must match"` / `"Position deletes must match"`，仅文案调整无逻辑变化（与 3.4 保持一致）。

## 总结

本提交把 #9449（快照属性传播）与 #9611（部分进度失败提交上限）两个改进回移到 Spark 3.4：让 `RewriteDataFilesSparkAction` / `RewritePositionDeleteFilesSparkAction` 的 commit manager 接收并使用用户设置的 `snapshotProperty`，避免自定义与内部快照属性丢失；并新增 `PARTIAL_PROGRESS_MAX_FAILED_COMMITS` 选项，在部分进度模式下按失败提交数阈值 warn 或抛异常，使重写在失败过多时显式失败。同时补充对应测试，并统一 3.5 测试断言消息文案。
