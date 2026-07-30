# 提交 3936：Flink: Add equality delete conversion planner (#16889)

## 提交信息

- **序号**：3936 / 4088
- **哈希**：6bef83a6bc24a2ba4af12329395d5162162c1df5
- **短哈希**：6bef83a6b
- **日期**：2026-06-23 21:49:30 -0700
- **作者**：Maximilian Michels
- **提交说明**：Flink: Add equality delete conversion planner (#16889)
- **PR/Issue**：#16889

## 总体目的

这次提交为 Flink 的 Iceberg 维护任务引入了 `EqualityConvertPlanner` 算子，它是 equality delete 转换管道的驱动组件。Equality deletes 是 Iceberg 中一种删除文件类型，记录被删除行的等值条件。随着 equality deletes 积累，读取性能会下降，因此需要定期将其转换为 position deletes 或合并到数据文件中。

`EqualityConvertPlanner` 是该管道的第一个算子，负责：每次触发时选择下一个未转换的 staging 快照，并发出读取命令（ReadCommand）和计划元数据（EqualityConvertPlan），驱动下游的 reader 和 worker 算子完成实际的删除转换工作。

该算子需要解决几个关键问题：
1. **索引一致性**：equality delete 的解析必须基于反映当前目标分支状态的索引，因此每次触发时需要先协调 worker 索引（首次构建、外部提交后重建）。
2. **并行读取顺序**：reader 并行运行，需要通过 watermark 分隔阶段来保证 worker 不会基于陈旧或半构建的索引解析删除。
3. **幂等性**：planner 通过 committer 在目标分支上的标记识别已转换的快照，重启后能重新推导位置且不重复处理。
4. **正确性优先**：对无法正确转换的输入（数据文件重写、V2 positional deletes、字段 ID 不匹配的 equality deletes）直接失败而非静默丢弃。

## 如何达成设计目的

`EqualityConvertPlanner` 继承自 `AbstractStreamOperator<ReadCommand>` 并实现 `OneInputStreamOperator<Trigger, ReadCommand>`。它以 `Trigger` 作为输入，输出 `ReadCommand`。设计上每个 trigger 执行两个有序步骤：

1. `ensureIndexCurrent`：从 main 分支历史更新 `lastStagingSnapshotId`，首次运行时从 main 引导 worker 索引，外部提交（如 compaction）推进 main 后重建索引（通过 `CLEAR_BROADCAST_STREAM` 驱逐过时条目）。
2. `processStagingSnapshot`：将选定 staging 快照的 equality deletes 解析到（现已最新的）索引上，透传 DV 文件，并为下一轮循环索引新数据文件。

Watermark 用于分隔阶段以控制 worker 的 keyed state 门控。元数据通过 `METADATA_STREAM` 侧输出在 read commands 之后发出。

算子状态包括 `indexSnapshotId`（索引反映的 main 快照 ID）、`indexedSequenceNumber`（索引的 main 序列号）、`eqFieldIds`（equality 字段 ID 集合，用于检测重配置）。

## 修改详情

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/EqualityConvertPlanner.java` (+762 lines, 新文件)

**修改目的**：新增 EqualityConvertPlanner 算子。

**工作逻辑**：
- 静态常量：定义了 `METADATA_STREAM`（元数据侧输出）、`CLEAR_BROADCAST_STREAM`（清理广播流）和多个 metric 名称。
- 构造函数：接收 tableName、taskName、tableLoader、stagingBranch、targetBranch、eqFieldIds，校验 eqFieldIds 非空。
- `open()`：加载表，注册 metrics（processedEqDeleteFileNum、processedStagingSnapshotNum、skippedNoOpCycles、reindexCount、errorCounter）。
- `initializeState()`：从 operator state 恢复 indexSnapshotId、indexedSequenceNumber、eqFieldIds，并校验 eqFieldIds 未被重配置。
- `snapshotState()`：持久化上述状态。
- `processElement()`：处理 Trigger，刷新表，调用 `ensureIndexCurrent`，查找下一个未处理 staging 快照，无则发 no-op 结果，有则调用 `processStagingSnapshot`。异常时记录错误并发送 drain 结果。
- `ensureIndexCurrent()`：比较当前 main 快照与上次，发现变化时通过 `discoverLastCommittedWork` 查找 committer 标记，决定 bootstrap 或 reindex，发出清理命令和数据读取命令。
- `discoverLastCommittedWork()`：从 main 头部回溯查找带有 `COMMITTED_STAGING_SNAPSHOT_PROPERTY` 标记的快照，返回最后提交的 staging ID 和外部提交计数。
- `processStagingSnapshot()`：解析 staging 快照的 equality delete 文件，与索引对照生成 ReadCommand，透传 DV 文件，索引新数据文件，发出元数据和阶段推进 watermark。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/TestEqualityConvertPlanner.java` (+1115 lines, 新文件)

**修改目的**：为 EqualityConvertPlanner 提供全面测试。

**工作逻辑**：覆盖各种场景包括首次转换、增量转换、外部提交后 reindex、幂等性（重启不重复处理）、no-op 计划、错误处理（字段 ID 不匹配等）、指标验证、时间戳和 watermark 断言等。

## 总结

这次提交引入了 Flink equality delete 转换管道的核心驱动算子 `EqualityConvertPlanner`，它负责选择待转换快照、维护 worker 索引一致性、发出读取命令和阶段分隔 watermark，并保证幂等性和正确性优先。这是 Iceberg Flink 集成中自动化删除文件维护的重要基础设施，配有超过 1100 行的全面测试。该提交随后在 #16944 中被 backport 到 Flink 1.20 和 2.0 分支。
