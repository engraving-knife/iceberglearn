# 提交 2886：Flink: Add test to ensure that append commits are created in dynamic iceberg sink when possible (#14559)

## 提交信息

- **序号**：2886 / 4088
- **哈希**：cdf4723e49a54fe725b1e92f4e95e127ae7b2957
- **短哈希**：cdf4723e4
- **日期**：2025-11-18 17:45:26 +0100
- **作者**：bezdomniy
- **提交说明**：Flink: Add test to ensure that append commits are created in dynamic iceberg sink when possible (#14559)
- **PR/Issue**：#14559

## 总体目的

此提交与 2884（Core: Classify RowDelta with data files only as APPEND）密切相关。2884 修复了 `BaseRowDelta` 的操作分类逻辑，使仅添加数据文件的 RowDelta 被标记为 `APPEND`。此提交在 Flink DynamicIcebergSink 的 `TestDynamicCommitter` 中新增测试，验证该修复在 Flink 动态 sink 的实际提交场景中生效：当所有 WriteResult 只包含数据文件（没有删除文件）时，提交产生的快照操作类型应为 `append`。

DynamicIcebergSink 使用 `RowDelta` 进行提交，在 upsert 模式下可能同时包含数据文件和删除文件。此测试确保在纯追加场景下（无删除文件），提交被正确分类为 append 操作，而非 overwrite。这对于下游消费者正确识别增量数据很重要。

## 如何达成设计目的

在 `TestDynamicCommitter` 中新增 `testCommitDeltaTxnWithAppendFiles` 测试：
1. 创建两个 `WriteTarget`，各自产生只包含数据文件（无删除文件）的 `WriteResult`。
2. 通过 `DynamicWriteResultAggregator` 将 WriteResult 写入 manifest。
3. 使用 `DynamicCommitter` 提交两个 commit request。
4. 验证最终只产生一个快照，且该快照的操作类型为 `"append"`。

## 修改详情

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestDynamicCommitter.java` (+65/-0 lines)

**修改目的**：验证纯数据文件提交产生 append 操作类型。

**工作逻辑**：
- 创建两个 `WriteTarget`（writeTarget1 和 writeTarget2），都指定分支 "branch1"。
- 每个 WriteTarget 对应一个 `WriteResult`，只包含数据文件（`DATA_FILE` 和 `DATA_FILE_2`），不包含删除文件。
- 使用 `DynamicWriteResultAggregator.writeToManifest()` 将每个 WriteResult 写入 delta manifest。
- 构建两个 `CommitRequest<DynamicCommittable>`。
- 创建 `DynamicCommitter`（`overwriteMode = false`），调用 `commit()` 同时提交两个 commit request。
- 验证：`table.snapshots()` 大小为 1（两个提交合并为一个快照），且快照操作为 `"append"`。

关键断言：
```java
assertThat(table.snapshots()).hasSize(1);
Snapshot snapshot = Iterables.getFirst(table.snapshots(), null);
assertThat(snapshot.operation()).isEqualTo("append");
```

## 总结

该提交是 2884（RowDelta APPEND 分类修复）的配套测试，在 Flink DynamicIcebergSink 场景下验证：当所有 WriteResult 只包含数据文件时，提交产生的快照操作类型为 `append`。这确保了 Flink 动态 sink 在纯追加场景下的操作分类正确性，下游消费者可以正确识别增量追加数据。注意此修改仅应用于 Flink v2.1 分支。
