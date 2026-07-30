# 提交 2215：Flink: Backport Dynamic Iceberg Sink: Add dynamic writer and committer to Flink 1.19 / 1.20 (#13248)

## 提交信息

- **序号**：2215 / 4088
- **哈希**：e631db69abbd43af687715e8634a46454742cdd1
- **短哈希**：e631db69a
- **日期**：2025-06-05 16:47:18 +0200
- **作者**：Maximilian Michels
- **提交说明**：Flink: Backport Dynamic Iceberg Sink: Add dynamic writer and committer to Flink 1.19 / 1.20 (#13248) Backports #13080
- **PR/Issue**：#13248（backport #13080）

## 总体目的

这个提交是 PR #13080 的反向移植，为 Iceberg Flink 动态 Sink 功能添加核心的 writer 和 committer 实现。动态 sink 允许一条 Flink 数据流中的记录路由到不同的 Iceberg 表（DynamicRecord 携带目标表信息），这需要 writer 能够同时为多个表写入数据，committer 能够将多个表的写入结果分别提交。本提交引入了 `DynamicWriter`（多表并行写入器）、`DynamicCommitter`（多表分别提交器）、`WriteTarget`（写入目标标识）、`DynamicWriteResult`/`DynamicCommittable`（写入/提交结果）以及相关的序列化器和聚合器。同时对现有 sink 类（如 CommitSummary、DeltaManifests、RowDataTaskWriterFactory 等）做了可见性调整，使 dynamic sink 能复用这些基础设施。该提交是动态 sink 功能的核心写入和提交实现，与前一提交（schema 演化）共同构成完整的动态 sink 基础。

## 如何达成设计目的

- 引入 `WriteTarget`：作为写入目标（tableName+branch+schemaId+specId+upsertMode+equalityFields）的标识，用于区分和管理多个表的 writer。
- 引入 `DynamicWriter`：实现 `CommittingSinkWriter`，使用 Caffeine 缓存按 WriteTarget 管理 `RowDataTaskWriterFactory` 和 `TaskWriter`，支持同一算子并行写入多个表。checkpoint 时 flush 所有 writer 的 WriteResult，封装为 DynamicWriteResult 返回。
- 引入 `DynamicCommitter`：实现 Flink SinkV2 `Committer`，按 TableKey（tableName+branch）分组 DynamicCommittable，按 checkpoint 顺序提交。从快照历史中恢复 maxCommittedCheckpointId 实现精确一次语义。支持 RowDelta 和 ReplacePartitions 两种提交模式。
- 引入 `DynamicWriteResultAggregator`：在 writer 和 committer 之间聚合 DynamicWriteResult，将同一表的多个 WriteResult 合并为 DeltaManifests。
- 引入 `DynamicCommittable`/`DynamicWriteResult` 及其序列化器：定义 writer→committer 之间传递的数据模型和序列化。
- 修改现有 sink 类的可见性（package-private → public 或增加构造函数重载），使 dynamic sink 包能复用。

## 修改详情

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicWriter.java` (新增, +217 lines)

**修改目的**：实现多表并行写入的 sink writer。

**工作逻辑**：
- 使用 `Cache<WriteTarget, RowDataTaskWriterFactory>` 缓存 writer 工厂，`Map<WriteTarget, TaskWriter<RowData>>` 管理活跃 writer。
- `write(element, context)`：根据 DynamicRecordInternal 构造 WriteTarget，若不存在对应 writer 则通过 Caffeine 创建 RowDataTaskWriterFactory（从 catalog 加载表、配置写入属性和 equality fields、处理 upsert 校验），然后 initialize 并 create writer，最后调用 writer.write(rowData)。
- `prepareCommit()`：遍历所有 writer，调用 complete() 获取 WriteResult，封装为 DynamicWriteResult 返回，然后清空 writers map。
- `getEqualityFields`：优先使用传入的 equalityFieldIds，否则使用表的 identifierFieldIds。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicCommitter.java` (新增, +422 lines)

**修改目的**：实现多表分别提交的 sink committer，支持精确一次语义。

**工作逻辑**：
- `commit(commitRequests)`：按 TableKey（tableName+branch）分组，按 checkpointId 排序。对每个表：
  - 通过 `getMaxCommittedCheckpointId` 遍历快照历史，根据 FLINK_JOB_ID、OPERATOR_ID、MAX_COMMITTED_CHECKPOINT_ID 找到已提交的最大 checkpointId。
  - 将已提交的请求标记为 `signalAlreadyCommitted`。
  - 对未提交的请求调用 `commitPendingRequests`。
- `commitPendingRequests`：反序列化 DeltaManifests，读取 WriteResult，按 checkpoint 顺序提交。通过 `commitPendingResult` 判断是否需要提交（空提交跳过逻辑），然后调用 `commitDeltaTxn`（RowDelta 模式）或 `replacePartitions`（覆盖模式）。
- `commitOperation`：设置快照属性（flink.job-id、flink.operator-id、flink.max-committed-checkpoint-id、分支），配置 worker pool 扫描 manifest，提交到指定分支。
- 空提交控制：每个表独立跟踪连续空 checkpoint 数，超过 `MAX_CONTINUOUS_EMPTY_COMMITS`（默认10）时强制提交一次空快照。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/WriteTarget.java` (新增, +144 lines)

**修改目的**：标识一个写入目标，用于区分不同表/分支/schema/spec 的 writer。

**工作逻辑**：包含 tableName、branch、schemaId、specId、upsertMode、equalityFields 字段，实现 equals/hashCode（基于这些字段），用于作为 writer 管理的 key。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicWriteResultAggregator.java` (新增, +188 lines)

**修改目的**：聚合同一表的多个 WriteResult 为 DynamicCommittable。

**工作逻辑**：在 checkpoint 时将 writer 产生的多个 DynamicWriteResult 按 WriteTarget 分组，将 WriteResult 写入 DeltaManifests（manifest 文件），序列化为 DynamicCommittable 传递给 committer。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicCommittable.java` (新增, +104 lines)

**修改目的**：定义 writer→committer 传递的可提交数据。

**工作逻辑**：包含 tableName、branch、jobId、operatorId、checkpointId、manifest（序列化的 DeltaManifests 字节数组），提供序列化支持。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicWriteResult.java` (新增, +40 lines)

**修改目的**：封装 writer flush 的结果。

**工作逻辑**：包含 WriteTarget 和 WriteResult，是 prepareCommit 的返回元素。

### 序列化器和指标类（新增, 共约 +241 lines）

- `DynamicCommittableSerializer.java`（+71）：序列化 DynamicCommittable。
- `DynamicWriteResultSerializer.java`（+62）：序列化 DynamicWriteResult。
- `DynamicCommitterMetrics.java`（+50）：committer 指标（按表名分组的提交摘要）。
- `DynamicWriterMetrics.java`（+49）：writer 指标（按表名分组的 flush 结果和耗时）。

### 现有 sink 类的可见性修改（每个版本约 -46 lines 修改）

**修改目的**：使 dynamic sink 包能复用现有 sink 基础设施。

**工作逻辑**：
- `CommitSummary.java`：从 package-private 改为 public，新增 `addAll` 方法供 DynamicCommitter 聚合多个 checkpoint 的结果。
- `DeltaManifests.java`：可见性调整。
- `DeltaManifestsSerializer.java`：可见性调整。
- `FlinkManifestUtil.java`：新增方法重载，支持从表的 specs 而非单一 spec 读取 WriteResult。
- `IcebergCommittableSerializer.java`、`IcebergFilesCommitterMetrics.java`、`IcebergStreamWriterMetrics.java`、`ManifestOutputFileFactory.java`、`WriteResultSerializer.java`：可见性调整。
- `RowDataTaskWriterFactory.java`：新增构造函数重载，接收 Schema 和 PartitionSpec 参数，供 DynamicWriter 使用。

### 测试文件（新增, 共约 +791 lines per version）

- `TestDynamicWriter.java`（+183）：测试多表写入、prepareCommit、close 等。
- `TestDynamicCommitter.java`（+381）：最全面的测试，覆盖提交、恢复、空提交、分支提交、并发等场景。
- `TestDynamicWriteResultAggregator.java`（+83）：测试 WriteResult 聚合。
- `TestDynamicWriteResultSerializer.java`（+82）：测试序列化往返。
- `TestDynamicCommittableSerializer.java`（+62）：测试序列化往返。

### Flink 1.19 模块的相同文件

与 1.20 模块完全相同的文件集合。

## 总结

该提交为 Iceberg Flink 动态 Sink 功能实现了核心的写入和提交逻辑。DynamicWriter 通过 Caffeine 缓存管理多表 writer 实现并行写入，DynamicCommitter 按表分组提交并支持精确一次语义，DynamicWriteResultAggregator 在两者间聚合结果。同时对现有 sink 类做了可见性调整以支持复用。该提交与 schema 演化提交（2214）共同构成了动态 sink 的完整基础设施，测试覆盖全面。
