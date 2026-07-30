# 提交 3136：Flink: Backport: Add test to ensure that append commits are created in dynamic iceberg sink when possible (#15088)

## 提交信息

- **序号**：3136 / 4088
- **哈希**：84be38acc59bb94cf441180ab7496bd828167093
- **短哈希**：84be38acc
- **日期**：2026-01-20
- **作者**：pvary
- **提交说明**：Flink: Backport: Add test to ensure that append commits are created in dynamic iceberg sink when possible (#15088)
- **PR/Issue**：#15088

## 总体目的

本提交是针对 main 分支上 PR #14559（提交 `cdf4723e4`）的回移（backport），目标分支为 Flink 1.4.x 维护分支。其要解决的核心问题是为 Flink Dynamic Iceberg Sink 的提交行为提供一个回归测试，确保在非覆盖写入模式下、当只追加数据文件（无删除文件）时，`DynamicCommitter` 真正生成 `append` 类型的快照，而不是退化为 `overwrite` 或 `replace` 操作。

这一行为至关重要：Iceberg 的快照操作类型直接决定了下游读取者能否把这次提交当作纯增量追加来处理。若动态 sink 在本应只追加数据时却触发了分区替换（`ReplacePartitions`）或带删除的 `RowDelta`，会带来不必要的元数据复杂度、影响并发隔离语义，并可能让依赖 `append` 操作做增量消费的下游产生误判。该测试用最小化的双提交场景把这一不变量固化下来，防止后续重构（例如 `commitDeltaTxn` 与 `replacePartitions` 分支选择逻辑的调整）无意中破坏“能 append 就 append”的承诺。

由于这是回移提交，改动只涉及测试代码，且同时覆盖 `flink/v1.20` 与 `flink/v2.0` 两个 Flink 版本分支目录，确保两个维护版本都具备该回归保护。

## 如何达成设计目的

思路是新增一个端到端风格的单元测试 `testCommitDeltaTxnWithAppendFiles`：构造一个空表，用 `DynamicWriteResultAggregator` 把两份只含数据文件的写结果（`WRITE_RESULT_BY_SPEC` 和 `WRITE_RESULT_BY_SPEC_2`）写入 manifest，包装成两个 `DynamicCommittable`/`CommitRequest`，再以 `overwriteMode = false` 构造 `DynamicCommitter` 并一次性提交两个请求；最后断言表只产生一个快照、且该快照的 `operation()` 等于 `"append"`。两个 Flink 版本目录的测试文件做了完全一致的修改。

## 修改详情

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestDynamicCommitter.java` (+53/-0 lines)

**修改目的**：为 Flink 1.20 动态 sink committer 新增“追加提交”回归测试。

**工作逻辑**：
测试复用了既有的测试基础设施（`OneInputStreamOperatorTestHarness`、`MockCommitRequest`、`DynamicWriteResultAggregator`、`DynamicCommitterMetrics` 等）。关键步骤：
- 以 `TableKey(TABLE1, "branch1")` 标识目标表与分支，`JobID.generate()` 与 `new OperatorID()` 生成 jobId/operatorId，`checkpointId = 1`。
- 调用 `aggregator.writeToManifests(...)` 两次，分别传入 `WRITE_RESULT_BY_SPEC` 与 `WRITE_RESULT_BY_SPEC_2`，得到两套 delta manifest（`deltaManifest1`、`deltaManifest2`），各自包装成 `DynamicCommittable` 再装入 `MockCommitRequest`。
- 以 `overwriteMode = false`、`workerPoolSize = 1` 构造真实的 `DynamicCommitter`（非 `CommitHookEnabledDynamicCommitter`，因此走正常提交路径），调用 `dynamicCommitter.commit(Sets.newHashSet(commitRequest1, commitRequest2))`。
- `table.refresh()` 后断言 `table.snapshots()` 大小为 1，且 `Iterables.getFirst(..., null).operation()` 等于 `"append"`。

由于提交只含数据文件（无 delete files），`DynamicCommitter.commitDeltaTxn` 通过 `table.newRowDelta()` 仅调用 `addRows`，最终产生 `append` 操作；测试通过断言 `operation()` 直接锁定了这一行为。

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestDynamicCommitter.java` (+53/-0 lines)

**修改目的**：为 Flink 2.0 动态 sink committer 同步新增相同的回归测试。

**工作逻辑**：
与 v1.20 目录的改动逐字相同，确保两个 Flink 版本分支目录共享同一份回归保护，避免任一分支在后续维护中偏离“能 append 就 append”的语义。

## 总结

该回移提交以最小、零生产代码改动的代价，为 Flink 动态 Iceberg Sink 的核心不变量——非覆盖模式下纯追加数据应生成 `append` 快照——补齐了回归测试，并同步覆盖 `flink/v1.20` 与 `flink/v2.0` 两个版本目录，有效防止后续重构破坏提交操作类型的正确性。
