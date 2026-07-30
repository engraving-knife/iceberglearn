# 提交 2208：Flink: Dynamic Iceberg Sink: Add dynamic writer and committer (#13080)

## 提交信息

- **序号**：2208 / 4088
- **哈希**：b8cc8eb846e70e49ca596864a5975641a04b65c4
- **短哈希**：b8cc8eb84
- **日期**：2025-06-04 14:52:29 +0200
- **作者**：Maximilian Michels
- **提交说明**：Flink: Dynamic Iceberg Sink: Add dynamic writer and committer (#13080)
- **PR/Issue**：#13080

## 总体目的

这个提交是 Flink Dynamic Iceberg Sink 功能的第二部分，添加动态 writer 和 committer 实现。结合提交 2204（#13032，schema 比较与表更新），共同构成动态 sink 的完整能力。Dynamic Iceberg Sink 的目标是让一个 Flink sink 能够在运行时动态地写入多个不同的 Iceberg 表和分支，并根据输入数据的 schema 自动演进目标表。本提交实现了核心的写入与提交机制：`DynamicWriter` 实现了 Flink SinkV2 的 `CommittingSinkWriter` 接口，根据每条记录的目标表/分支/schema 维护多个底层 `TaskWriter`，在 checkpoint 时输出 `DynamicWriteResult`；`DynamicCommitter` 实现了 `Committer` 接口，负责将多个表的写入结果分别提交到各自的 Iceberg 表，处理 checkpoint 幂等性、空提交限制、快照属性等。同时新增了 `DynamicCommittable`、`DynamicWriteResult`、`WriteTarget` 等数据结构及对应的序列化器、聚合器和指标类，并修改了若干现有 sink 类（如 `CommitSummary`、`DeltaManifests` 等）以提升可见性或适配。

## 如何达成设计目的

- 新增 `DynamicWriter`：实现 `CommittingSinkWriter<DynamicRecordInternal, DynamicWriteResult>`，内部维护 `WriteTarget` → `TaskWriter` 的映射，每条记录按其目标表/分支/schema 路由到对应 writer；使用 Caffeine 缓存管理 `RowDataTaskWriterFactory`；checkpoint 时收集所有 writer 的 `WriteResult` 输出为 `DynamicWriteResult`。
- 新增 `DynamicCommitter`：实现 `Committer<DynamicCommittable>`，按表/分支/checkpoint 维度提交，基于 `RowDelta`/`ReplacePartitions` 提交 manifest，维护 max-committed-checkpoint-id 以实现幂等性，限制连续空提交次数。
- 新增 `WriteTarget`：标识一个写入目标（表名+分支+schemaId+specId+upsertMode+equalityFields），作为 writer 路由和缓存的 key。
- 新增 `DynamicCommittable`/`DynamicWriteResult`：可提交对象和写入结果的数据结构，携带 checkpointId、jobId、operatorId、DeltaManifests 等信息。
- 新增序列化器（`DynamicCommittableSerializer`、`DynamicWriteResultSerializer`）、聚合器（`DynamicWriteResultAggregator`）、指标类（`DynamicWriterMetrics`、`DynamicCommitterMetrics`）。
- 修改现有 sink 类（如将部分类可见性从 package 改为 public，调整 `RowDataTaskWriterFactory` 构造以支持动态场景）。
- 为每个新类配备测试。

## 修改详情

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicWriter.java` (新增, +217/-0 lines)

**修改目的**：实现动态写入器，支持同时写入多个 Iceberg 表。

**工作逻辑**：实现 `CommittingSinkWriter` 接口。`write(DynamicRecordInternal, Context)`：根据记录的 tableName/branch/schemaId/specId/upsertMode/equalityFields 构造 `WriteTarget`，`computeIfAbsent` 获取或创建对应 `TaskWriter`（通过 Caffeine 缓存 `RowDataTaskWriterFactory`，加载时从 catalog 加载表并创建 factory），将 RowData 写入。`prepareCommit`：收集所有 writer 的 `WriteResult`，封装为 `DynamicWriteResult` 输出。`flush`/`close`：刷新或关闭所有 writer。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicCommitter.java` (新增, +422/-0 lines)

**修改目的**：实现动态提交器，将多表写入结果提交到各自 Iceberg 表。

**工作逻辑**：实现 `Committer<DynamicCommittable>` 接口。`commit(Collection<DynamicCommittable>)`：按表/分支分组，对每个 committable 解析 `DeltaManifests`，基于 `RowDelta`（或 `ReplacePartitions`）提交 manifest 文件，设置 snapshot 属性（flink.job-id、operator-id、max-committed-checkpoint-id），处理 worker pool 并行提交。维护 `maxContinuousEmptyCommitsMap` 限制连续空提交。`max-committed-checkpoint-id` 机制保证幂等性。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/WriteTarget.java` (新增, +144/-0 lines)

**修改目的**：标识写入目标，作为 writer 路由和缓存 key。

**工作逻辑**：不可变值对象，包含 tableName、branch、schemaId、specId、upsertMode、equalityFields，实现 equals/hashCode。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicCommittable.java` (新增, +104/-0 lines)

**修改目的**：可提交对象，携带 checkpoint/job/operator 信息和 DeltaManifests。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicWriteResult.java` (新增, +40/-0 lines)

**修改目的**：写入结果，携带 WriteTarget 和 WriteResult。

### 其他新增文件

- `DynamicCommittableSerializer`（+71）、`DynamicWriteResultSerializer`（+62）、`DynamicWriteResultAggregator`（+188）、`DynamicWriterMetrics`（+49）、`DynamicCommitterMetrics`（+50）：分别实现序列化、结果聚合、指标收集。
- 5 个测试类（`TestDynamicCommitter`、`TestDynamicWriter`、`TestDynamicCommittableSerializer`、`TestDynamicWriteResultSerializer`、`TestDynamicWriteResultAggregator`）：提供测试覆盖。

### 修改的现有文件（约 10 个, +调整）

- `CommitSummary`、`DeltaManifests`、`DeltaManifestsSerializer`、`FlinkManifestUtil`、`IcebergCommittableSerializer`、`IcebergFilesCommitterMetrics`、`IcebergStreamWriterMetrics`、`ManifestOutputFileFactory`、`RowDataTaskWriterFactory`、`WriteResultSerializer`：多为可见性调整（如将构造器或方法改为 package/public 可访问），以支持 dynamic sink 子包的调用。`RowDataTaskWriterFactory` 调整较大（+19/-8），支持动态场景的 writer factory 创建。

## 总结

该提交为 Flink Dynamic Iceberg Sink 添加了核心的动态 writer 和 committer 实现，使一个 sink 能够在运行时动态写入多个 Iceberg 表并自动提交。结合提交 2204 的 schema 比较与表更新能力，构成了完整的动态 sink 功能。这是 Flink 集成的重要功能增强，为多表动态写入场景提供了基础。
