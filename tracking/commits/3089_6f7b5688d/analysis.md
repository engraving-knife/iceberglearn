# 提交 3089：Flink: Dynamic Sink: Refactor write result aggregation (#14810)

## 提交信息

- **序号**：3089 / 4088
- **哈希**：6f7b5688dada3fa92a2b510b3ea26400b332a25b
- **短哈希**：6f7b5688d
- **日期**：2026-01-09
- **作者**：aiborodin
- **提交说明**：Flink: Dynamic Sink: Refactor write result aggregation (#14810)
- **PR/Issue**：#14810

## 总体目的

该提交对 Flink Dynamic Sink（动态多表 Sink）的写入结果聚合逻辑进行了深度重构。核心问题是：此前 `DynamicWriteResultAggregator` 会将同一张表（相同 `WriteTarget`）的所有写入结果聚合到一个单一的 manifest 中，而不区分分区规范（partition spec）。然而，Iceberg 表可以拥有多个分区规范（例如经过分区演进后，不同时间段的数据使用不同的分区策略），将不同 spec 的数据文件混合写入同一个 `DeltaManifests` 会导致提交错误或数据不一致。

具体来说，原来的 `WriteTarget` 包含 `tableName`、`branch`、`schemaId`、`specId`、`upsertMode`、`equalityFields` 等字段，被用作聚合结果的键。这意味着同一个 `WriteTarget`（含特定 specId）的写入结果才会被聚合在一起，但 `DynamicCommittable` 只携带单个 manifest（`byte[] manifest`），在 committer 端反序列化时只能恢复出一个 spec 的文件。这种设计在多 spec 场景下存在根本性缺陷。

此外，原 `DynamicCommitter` 中包含一套"连续空提交跳过"机制（`maxContinuousEmptyCommitsMap` / `continuousEmptyCheckpointsMap`），用于在连续多次空提交后跳过实际的 commit 操作以减少开销。这套逻辑增加了 committer 的状态管理复杂度，且在多表动态 sink 场景下维护困难。本提交移除了该机制，简化了提交逻辑。

同时，原 `DynamicCommitter` 内部有一个私有的 `TableKey` 内部类（只含 `tableName` 和 `branch`），本提交将其提取为独立的顶层类 `TableKey`，因为新的聚合逻辑中 committable 的键只需要表名和分支，specId 信息被下沉到每个 manifest 中。

## 如何达成设计目的

整体思路是将聚合粒度从"按 WriteTarget（含 specId）聚合为单个 manifest"改为"按 TableKey（仅表名+分支）聚合，但内部按 specId 分组生成多个 manifest"。具体涉及以下改动方向：

1. 新建 `TableKey` 类（仅 tableName + branch），替代 `DynamicCommittable` 和 `DynamicWriteResult` 中作为键的 `WriteTarget`。
2. `DynamicCommittable` 的 manifest 字段从 `byte[]` 改为 `byte[][]`，一个 committable 可携带多个 manifest（每个对应一个 spec）。
3. `DynamicWriteResultAggregator` 的结果映射从 `Map<WriteTarget, Collection<DynamicWriteResult>>` 改为 `Map<TableKey, Map<Integer, Collection<WriteResult>>>`，按表和 specId 二级分组。
4. `DynamicCommitter` 移除空提交跳过逻辑，简化提交流程；DV 校验改为跨所有结果统一检查；CommitSummary 改为按 checkpoint 粒度创建。
5. 序列化器版本升级到 V2，保留 V1 反序列化以兼容旧状态。

## 修改详情

### `core/src/main/java/org/apache/iceberg/io/WriteResult.java` (+11/-0 lines)

**修改目的**：为 `WriteResult` 添加 `toString()` 方法。

**工作逻辑**：
使用 `MoreObjects.toStringHelper(this)` 添加 `dataFiles`、`deleteFiles`、`referencedDataFiles`、`rewrittenDeleteFiles` 四个字段。这是辅助性改动，主要为了在新的聚合逻辑调试日志中能更好地输出 WriteResult 内容。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/sink/CommitSummary.java` (+6/-1 lines)

**修改目的**：新增按 checkpoint 粒度添加写入结果的能力。

**工作逻辑**：
原 `addAll(NavigableMap<Long, List<WriteResult>>)` 方法直接遍历每个 checkpoint 的结果列表并逐个调用 `addWriteResult`。重构后，该方法改为委托调用新的 `addAll(Collection<WriteResult>)` 重载方法：`pendingResults.values().forEach(this::addAll)`。新增的 `addAll(Collection<WriteResult>)` 方法遍历集合并对每个结果调用 `addWriteResult`。这使得 `DynamicCommitter` 可以按单个 checkpoint 的结果列表来更新摘要，而非一次性传入所有 checkpoint 的映射。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicCommittable.java` (+14/-10 lines)

**修改目的**：支持单个 committable 携带多个 manifest。

**工作逻辑**：
键类型从 `WriteTarget` 改为 `TableKey`；manifest 字段从 `byte[] manifest` 改为 `byte[][] manifests`（二维数组，每个元素是一个 spec 的 manifest 序列化字节）。构造函数、`key()` 和新增的 `manifests()` 方法相应调整。`equals` 使用 `Arrays.deepEquals(manifests, that.manifests)` 比较二维数组；`hashCode` 使用 `Arrays.deepHashCode(manifests)`。移除了 `writeTarget()` 方法（与 `key()` 功能重复）。类注释中 "file" 改为 "files"。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicCommittableSerializer.java` (+48/-17 lines)

**修改目的**：实现多 manifest 的序列化/反序列化，并保持向后兼容。

**工作逻辑**：
版本号从 `VERSION = 1` 升级到 `VERSION_2 = 2`（保留 `VERSION_1 = 1` 常量用于兼容判断）。序列化时，先写入 manifest 数量 `numManifests`，再循环写入每个 manifest 的长度和字节。反序列化时根据版本号分支：`deserializeV1` 保留旧逻辑（反序列化 `WriteTarget` 和单个 manifest），但将 `WriteTarget` 转换为 `TableKey`（取 `tableName` 和 `branch`），将单个 manifest 包装为 `new byte[][] {manifestBuf}`，实现旧状态到新格式的迁移；`deserializeV2` 反序列化 `TableKey`，然后读取 manifest 数量并循环读取每个 manifest 字节。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/TableKey.java` (+84/-0 lines)

**修改目的**：新建独立的 `TableKey` 类，作为 committable 的键。

**工作逻辑**：
`TableKey` 包含 `tableName` 和 `branch` 两个字段，是 `WriteTarget` 的子集（去除了 schemaId、specId、upsertMode、equalityFields）。提供从 `DynamicCommittable` 构造的构造函数 `TableKey(DynamicCommittable committable)`。实现 `serializeTo` / `deserializeFrom` 用于 Flink 状态序列化（写入/读取两个 UTF 字符串）。实现了标准的 `equals`、`hashCode`（基于 `Objects.hash(tableName, branch)`）和 `toString`。该类从 `DynamicCommitter` 的内部静态类提取为顶层类，因为多个类需要引用它。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicCommitter.java` (+44/-138 lines)

**修改目的**：简化提交逻辑，适配多 manifest 结构，移除空提交跳过机制。

**工作逻辑**：
移除了 `EMPTY_MANIFEST_DATA`、`EMPTY_WRITE_RESULT` 常量和 `MAX_CONTINUOUS_EMPTY_COMMITS` 配置项。移除了 `maxContinuousEmptyCommitsMap` 和 `continuousEmptyCheckpointsMap` 两个状态映射及整个 `commitPendingResult` 方法（该方法包含空提交跳过逻辑：当连续空提交数未达到阈值时跳过实际 commit）。

在 `commit` 方法中，遍历 committable 的 `manifests()` 数组（而非单个 manifest），对每个 manifest 反序列化 `DeltaManifests` 并读取 `WriteResult`，按 checkpoint 归入 `pendingResults`。DV（deletion vector）位置删除文件的校验从原来在循环内逐 manifest 检查，改为在所有结果聚合后统一用 stream 查找任意非 DV 的位置删除文件，若存在则抛出异常，避免并发 V3 升级冲突。

`CommitSummary` 的创建从原来一次性对所有 pendingResults 创建，改为在每个 checkpoint 的 `replacePartitions` 和 `commitDeltaTxn` 方法中分别创建（调用 `summary.addAll(writeResults)` 传入单个 checkpoint 的结果列表）。`commitOperation` 方法中 `committerMetrics.updateCommitSummary` 的调用移到了方法内部（与 `commitDuration` 一起），确保摘要和耗时同步上报。新增了 `skippedCommitRequests` 的 debug 日志。注释说明了当前保留 List 形式的 commit requests 是为了兼容旧版本状态，1.12 将移除。移除了文件末尾的内部 `TableKey` 类（已提取为独立文件）。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicWriteResult.java` (+18/-5 lines)

**修改目的**：将 specId 从键中分离为独立字段，键类型改为 TableKey。

**工作逻辑**：
键类型从 `WriteTarget` 改为 `TableKey`，新增 `private final int specId` 字段（原先 specId 包含在 WriteTarget 中）。构造函数增加 `specId` 参数。新增 `specId()` 访问方法和 `toString()`。这样同一个表的不同 spec 的写入结果共享相同的 `TableKey`，但通过 `specId` 区分，为聚合器按 spec 分组提供基础。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicWriteResultSerializer.java` (+4/-2 lines)

**修改目的**：序列化 specId 字段，使用 TableKey。

**工作逻辑**：
序列化时在写入 key 后额外写入 `writeResult.specId()`（`view.writeInt`）。反序列化时使用 `TableKey.deserializeFrom(view)` 替代 `WriteTarget.deserializeFrom(view)`，并读取 `specId`。注意反序列化仍只处理 version 1，但内部格式已变化（增加了 specId），这是因为 DynamicWriteResultSerializer 的版本未升级，实际的格式变化通过 committable 序列化器版本管理。实际上 version 1 的反序列化逻辑在此处被修改以适配新的字段结构。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicWriter.java` (+4/-2 lines)

**修改目的**：适配 DynamicWriteResult 新构造函数。

**工作逻辑**：
在创建 `DynamicWriteResult` 时，从原 `writeTarget` 中提取 `tableName` 和 `branch` 构造新的 `TableKey`，并单独传入 `writeTarget.specId()`，替代原来直接传入整个 `writeTarget`。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicWriteResultAggregator.java` (+42/-11 lines)

**修改目的**：按表和 specId 二级分组聚合写入结果，为每个 spec 生成独立 manifest。

**工作逻辑**：
结果存储从 `Map<WriteTarget, Collection<DynamicWriteResult>> results` 改为 `Map<TableKey, Map<Integer, Collection<WriteResult>>> resultsByTableKeyAndSpec`。在 `processElement` 中，收到 `DynamicWriteResult` 后，按 `result.key()`（TableKey）和 `result.specId()` 二级查找，将 `WriteResult` 添加到对应的列表中，并输出 debug 日志记录添加的结果和累计数量。

`prepareSnapshotPreBarrier` 中遍历新的结果映射，对每个 TableKey 调用 `writeToManifests(entries.getKey().tableName(), entries.getValue(), checkpointId)` 生成多 manifest 数组，构造 `DynamicCommittable`。`writeToManifests`（原 `writeToManifest` 重命名）遍历每个 specId 对应的 WriteResult 集合，调用私有方法 `writeToManifest(tableName, specId, writeResults, checkpointId)` 生成单个 manifest 字节，返回 `byte[][]` 数组。移除了 `EMPTY_MANIFEST_DATA` 常量和空结果提前返回逻辑（空集合由调用方处理）。私有 `writeToManifest` 方法中 `spec(key.tableName(), key.specId())` 改为 `spec(tableName, specId)`，参数直接传入而非从 key 获取。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/WriteTarget.java` (+0/-13 lines)

**修改目的**：移除不再需要的序列化方法。

**工作逻辑**：
移除了 `serializeTo(DataOutputView)` 方法（新代码使用 `TableKey.serializeTo` 替代）。保留 `deserializeFrom` 方法用于向后兼容反序列化旧状态中的 `WriteTarget`（在 `DynamicCommittableSerializer.deserializeV1` 中使用）。移除了 `DataOutputView` 的 import。

### 测试文件（6 个文件，+293/-113 lines）

**修改目的**：适配新的数据结构并新增覆盖测试。

**工作逻辑**：
`TestDynamicCommittableSerializer` 适配多 manifest 结构和 V1/V2 版本兼容测试。`TestDynamicCommitter` 大幅简化（移除空提交跳过相关测试和 mock，适配新的提交逻辑），从 249 行变更中可见大量删除。`TestDynamicIcebergSink` 新增 93 行多表写入集成测试。`TestDynamicWriteResultAggregator` 适配新的二级分组结构和 `writeToManifests` 方法。`TestDynamicWriteResultSerializer` 适配 specId 字段。`TestDynamicIcebergSinkPerf` 小幅适配。

## 总结

该提交对 Flink Dynamic Sink 的写入结果聚合机制进行了根本性重构，核心变化是将单一 manifest 模式改为按分区规范（specId）分组的多 manifest 模式，解决了多 spec 表的提交正确性问题。同时移除了复杂的连续空提交跳过逻辑，简化了 committer 实现，并将 `TableKey` 提取为独立类。序列化器版本升级到 V2 并保留 V1 兼容反序列化，确保已有 Flink 状态可平滑迁移。这是 Dynamic Sink 走向稳定的重要一步，为后续 1.12 版本进一步简化（移除 List 兼容层）铺平了道路。
