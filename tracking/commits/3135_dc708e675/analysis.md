# 提交 3135：Flink: Backport: Dynamic Sink: Refactor write result aggregation (#15054)

## 提交信息

- **序号**：3135 / 4088
- **哈希**：dc708e675d263bd09f95f11421a3fd77e430aca6
- **短哈希**：dc708e675
- **日期**：2026-01-20
- **作者**：aiborodin
- **提交说明**：Flink: Backport: Dynamic Sink: Refactor write result aggregation (#15054)
- **PR/Issue**：#15054（回移自 #14810）

## 总体目的

本提交将 PR #14810（动态 Sink 写入结果聚合重构）回移到 Flink v1.20 与 v2.1 两个版本模块。其核心目的是重构 Flink 动态 Sink（Dynamic Sink）中写入结果的聚合方式，使同一检查点内同一（表、分支）下的多个分区规格（spec）的写入结果被聚合为单个提交请求，而非每个 spec 各产生一个提交，从而减少提交请求数量、简化提交器逻辑，并移除复杂且有状态的空提交抑制机制。

背景与问题：动态 Sink 在每个 checkpoint 把上游 `DynamicWriter` 产出的 `DynamicWriteResult` 聚合成 `DynamicCommittable` 交给下游 `DynamicCommitter` 提交。重构前，聚合的键是 `WriteTarget`（含 `tableName`、`branch`、`schemaId`、`specId`、`upsertMode`、`equalityFields`），每个 `WriteTarget` 产生一个 `DynamicCommittable`，其中只含单个 manifest（`byte[]`）。这意味着同一张表在同一 checkpoint 若涉及多个分区 spec，会产生多个独立的 `DynamicCommittable`，导致提交请求分散、提交器需维护每表的空提交计数器来抑制连续空提交（`MAX_CONTINUOUS_EMPTY_COMMITS` 机制），逻辑复杂且引入了跨 checkpoint 的可变状态。

重构后：聚合键简化为 `TableKey`（仅 `tableName` + `branch`），同一 checkpoint 内同一 (表, 分支) 的所有 spec 的写入结果被归并到一个 `DynamicCommittable`，其中包含多个 manifest（`byte[][]`，每个 spec 一个）。提交器侧相应地遍历多个 manifest 统一组装 `WriteResult`，一次提交涵盖所有 spec。同时移除了空提交抑制机制——不再产生空的 manifest，空提交由 Flink 框架自身的 `signalAlreadyCommitted` 与已提交 checkpoint 跳过逻辑处理。状态序列化升级到 V2 并保留 V1 反序列化以兼容旧版本 Flink 状态，注释说明 Iceberg 1.12 将移除兼容层，用户应先升级到 1.11 迁移状态。

## 如何达成设计目的

整体设计分三步：一是抽出顶层 `TableKey` 类（从 `DynamicCommitter` 内部类提升）作为新的轻量聚合键，仅含表名与分支；二是改造聚合管线 `DynamicWriteResultAggregator` 把结果按 `(TableKey, specId)` 二级分组，对每个 spec 各写一个 `DeltaManifests`，最终把同一 `TableKey` 的多个 manifest 打包成 `byte[][]` 存入单个 `DynamicCommittable`；三是简化 `DynamicCommitter`，遍历 `manifests()` 数组统一组装结果、移除空提交计数器与 `EMPTY_MANIFEST_DATA` 特殊分支，并把 `CommitSummary` 构造下放到实际提交方法中。序列化层升级版本号并保留旧版反序列化以兼容存量 Flink 状态。改动同时应用于 flink/v1.20 与 flink/v2.1 两个模块（内容一致），共 32 文件、+1263/-831 行。

## 修改详情

以下以 flink/v2.1 模块为例描述（v1.20 改动完全一致）。

### `TableKey.java` (+84 lines，新增)

**修改目的**：新增顶层 `TableKey` 作为聚合键，仅含表名与分支。

**工作逻辑**：
从原先 `DynamicCommitter` 的内部类 `TableKey` 提升为独立顶层类。包含 `tableName` 与 `branch` 两个字段（相比 `WriteTarget` 去掉了 schemaId/specId/upsertMode/equalityFields）。提供从 `DynamicCommittable` 构造的便捷构造函数、`serializeTo`/`deserializeFrom` 用于 Flink 状态序列化，以及标准 `equals`/`hashCode`/`toString`。这样聚合与序列化只关心表与分支，spec 维度下沉到 manifest 数组与 `DynamicWriteResult.specId` 中。

### `DynamicCommittable.java` (+约12/-10 lines)

**修改目的**：将单 manifest 改为多 manifest 数组，键类型改为 `TableKey`。

**工作逻辑**：
键由 `WriteTarget key` 改为 `TableKey key`；`byte[] manifest` 改为 `byte[][] manifests`。`manifest()` 方法改为 `manifests()` 返回数组。`equals` 用 `Arrays.deepEquals(manifests, ...)`，`hashCode` 用 `Arrays.deepHashCode`。移除了冗余的 `writeTarget()` 方法（与 `key()` 重复）。类注释同步更新为"files"（复数）。这使一个 committable 能承载同一表的多个 spec 的 manifest。

### `DynamicWriteResult.java` (+约18/-3 lines)

**修改目的**：携带 specId 并改用 TableKey，使聚合器能按 spec 分组。

**工作逻辑**：
键由 `WriteTarget` 改为 `TableKey`，新增 `int specId` 字段及访问器。构造函数变为 `DynamicWriteResult(TableKey key, int specId, WriteResult writeResult)`。新增 `toString`（用 `MoreObjects.toStringHelper`）便于调试。specId 让聚合器在收到结果时知道该写入属于哪个分区规格。

### `DynamicWriteResultAggregator.java` (+约30/-15 lines)

**修改目的**：按 (TableKey, specId) 二级聚合，每表每 checkpoint 产出含多 manifest 的单个 committable。

**工作逻辑**：
聚合状态由 `Map<WriteTarget, Collection<DynamicWriteResult>> results` 改为 `Map<TableKey, Map<Integer, Collection<WriteResult>>> resultsByTableKeyAndSpec`（外层按表/分支，内层按 specId）。`addRecord` 中按 `result.key()` 与 `result.specId()` 二级 `computeIfAbsent` 归并，并记录调试日志。checkpoint 提交时对每个 `TableKey` 调用新方法 `writeToManifests(tableName, writeResultsBySpec, checkpointId)`：遍历每个 specId 的结果集合，分别调用私有 `writeToManifest(tableName, specId, writeResults, checkpointId)` 写一个 `DeltaManifests` 并序列化为 `byte[]`，最终返回 `byte[][]`。移除了 `EMPTY_MANIFEST_DATA` 常量与空结果短路返回逻辑——不再产生空 manifest。

### `DynamicCommitter.java` (+约40/-145 lines)

**修改目的**：适配多 manifest 结构，移除空提交抑制与内部 TableKey，下放 CommitSummary 构造。

**工作逻辑**：
- 移除 `EMPTY_MANIFEST_DATA`/`EMPTY_WRITE_RESULT` 常量、`MAX_CONTINUOUS_EMPTY_COMMITS` 配置键，以及 `maxContinuousEmptyCommitsMap`/`continuousEmptyCheckpointsMap` 两个可变状态映射和 `commitPendingResult` 方法中整套连续空提交跳过逻辑。提交路径简化为：组装 pendingResults 后直接 `replacePartitions` 或 `commitDeltaTxn`。
- `commitPending` 中遍历 `committable.getCommittable().manifests()` 数组，对每个 manifest 反序列化 `DeltaManifests` 并 `readCompletedFiles` 组装 `WriteResult`，收集到 `pendingResults`。
- 位置删除 DV 校验从原来"每个 manifest 内逐文件检查"改为"跨所有 pendingResults 一次性流式检查"：用 `findAny()` 找到非 DV 的位置删除文件即抛异常，逻辑更集中。
- `CommitSummary` 不再在 `commitPending` 顶层构造并传参，而是下沉到 `replacePartitions`/`commitDeltaTxn` 内部各自局部构造并 `addAll`，再在 `commitOperation` 中更新 metrics。
- 删除内部 `TableKey` 类（已提升为顶层）。
- 新增注释说明当前保留每 checkpoint 的 List 形式 commit 请求以兼容旧版本 Flink 状态，Iceberg 1.12 将移除，用户需先经 1.11 迁移。
- 跳过已提交 checkpoint 时新增 `skippedCommitRequests` 变量与 DEBUG 日志，便于排查。

### `DynamicCommittableSerializer.java` (+约45/-20 lines)

**修改目的**：升级序列化到 V2 支持多 manifest，保留 V1 反序列化兼容旧状态。

**工作逻辑**：
版本常量拆为 `VERSION_1=1`、`VERSION_2=2`，`getVersion` 返回 `VERSION_2`。V2 序列化先写 `TableKey`（经其 `serializeTo`），再写 manifest 数量与逐个 manifest 长度+内容。反序列化按版本分支：`deserializeV1` 仍按旧格式读 `WriteTarget` 与单个 manifest，但把 `WriteTarget` 转换为 `TableKey`（取 tableName/branch）并把单 manifest 包装为 `new byte[][]{manifestBuf}`，实现旧状态向新结构的迁移；`deserializeV2` 读 `TableKey` 与 manifest 数组。

### `DynamicWriteResultSerializer.java` (+4/-2 lines)

**修改目的**：序列化 specId 并改用 TableKey 反序列化。

**工作逻辑**：
序列化时在 key 之后增加 `view.writeInt(writeResult.specId())`。反序列化时由 `WriteTarget.deserializeFrom` 改为 `TableKey.deserializeFrom`，并读取 `specId`，构造 `new DynamicWriteResult(key, specId, writeResult)`。

### `DynamicWriter.java` (+5/-1 lines)

**修改目的**：产出 DynamicWriteResult 时携带 TableKey 与 specId。

**工作逻辑**：
完成写入后构造结果时，由 `new DynamicWriteResult(writeTarget, writeResult)` 改为 `new DynamicWriteResult(new TableKey(writeTarget.tableName(), writeTarget.branch()), writeTarget.specId(), writeResult)`，从 `WriteTarget` 提取表名/分支构造 `TableKey` 并传入 specId。

### `WriteTarget.java` (+0/-13 lines)

**修改目的**：移除不再使用的序列化方法。

**工作逻辑**：
删除 `serializeTo(DataOutputView)` 方法（新路径不再序列化 `WriteTarget`，改由 `TableKey.serializeTo` 承担）。保留 `deserializeFrom` 用于 V1 旧状态反序列化兼容。`DataOutputView` import 一并移除。

### `CommitSummary.java` (+4/-1 lines)

**修改目的**：新增按集合批量添加 WriteResult 的重载。

**工作逻辑**：
原 `addAll(NavigableMap<Long, List<WriteResult>>)` 改为委托新重载 `addAll(Collection<WriteResult>)`，后者对集合内每个 `WriteResult` 调用 `addWriteResult`。这使提交器在 `replacePartitions`/`commitDeltaTxn` 中可对单个 checkpoint 的结果列表直接 `summary.addAll(writeResults)`。

### 测试文件（6 个，每个 Flink 版本）

**修改目的**：验证按表/分支/检查点单次提交、多 spec 聚合与序列化兼容性。

**工作逻辑**：
`TestDynamicWriteResultAggregator` 新增 `testCommitsOncePerTableBranchAndCheckpoint`（验证同一表同一 checkpoint 只产出一个 committable）、`testAggregatesWriteResultsForTwoTables`、`testAggregatesWriteResultsForOneTable`（验证同一表多 spec 结果被聚合到多 manifest 的单个 committable），测试辅助方法增加 specId 参数。`TestDynamicCommitter` 大幅重写以适配移除空提交逻辑后的简化提交路径。`TestDynamicCommittableSerializer` 增加 V2 多 manifest 序列化与 V1 旧状态反序列化迁移测试。`TestDynamicIcebergSink` 新增端到端多 spec 提交测试。`TestDynamicWriteResultSerializer` 适配 specId 字段。`TestDynamicIcebergSinkPerf` 小幅适配。

## 总结

本提交将动态 Sink 写入结果聚合重构（#14810）回移到 Flink v1.20 与 v2.1，通过引入轻量 `TableKey`、按 (表, 分支, spec) 二级聚合、把单 manifest 升级为多 manifest 数组，使同一表同一 checkpoint 的多个 spec 写入合并为单次提交，简化了 `DynamicCommitter`（移除空提交抑制机制与内部 TableKey、下放 CommitSummary），并通过序列化版本升级与 V1 兼容反序列化保障存量 Flink 状态平滑迁移，降低了提交开销与代码复杂度。
