# 提交 3811：Flink: Fix duplicate commits in DynamicCommitter when Flink jobId changes on restart (#16011)

## 提交信息

- **序号**：3811 / 4088
- **哈希**：c11404cb49985a39a7caff0328c5f927a795154f
- **短哈希**：c11404cb4
- **日期**：2026-06-01 14:03:02 +0200
- **作者**：lrsb <github@lrsb.xyz>
- **提交说明**：Flink: Fix duplicate commits in DynamicCommitter when Flink jobId changes on restart (#16011)
- **PR/Issue**：#16011

## 总体目的

本提交修复 Flink Dynamic Iceberg Sink 中 `DynamicCommitter` 在 Flink 作业重启（jobId 变化）后产生重复提交（duplicate commits）的缺陷。Iceberg 的 Flink sink 通过将已提交的 checkpoint ID 写入 Iceberg 快照摘要（snapshot summary，key 为 `flink.job-id` 和 `flink.operator-id`）来实现"精确一次"语义下的去重。当 sink 提交时，会读取表祖先链上的快照摘要，找出对应 (jobId, operatorId) 已提交的最大 checkpoint ID，跳过该 ID 及之前的请求，只提交之后的请求。

问题在于：Flink 作业重启后 jobId 会变化（operatorId 保持稳定）。如果一次提交批次中同时混合了重启前（旧 jobId）和重启后（新 jobId）的 committable，原实现只用批次中最后一个 committable 的 jobId/operatorId 去查询 `maxCommittedCheckpointId`，导致属于另一个 jobId 的已提交 checkpoint 无法被识别，从而被重复提交到 Iceberg 表，造成数据重复。

本提交通过将提交请求按 (table, branch) → (jobId, operatorId) → checkpointId 三层分组，独立地对每个 (jobId, operatorId) 组查询其已提交的最大 checkpoint ID 并去重，解决了混合 jobId 批次下的重复提交问题。

## 如何达成设计目的

核心设计是把原本"每个表一个分组、用最后一个 committable 的 jobId 查询"改为"每个表下再按 (jobId, operatorId) 分组，每组独立查询去重"。新增 `JobOperatorKey` 类作为 (jobId, operatorId) 的值对象，用于 Map 的中间层 key。提交时构建三层结构 `Map<TableKey, Map<JobOperatorKey, NavigableMap<Long, List<CommitRequest>>>>`，外层按表加载表与祖先链一次，内层对每个 (jobId, operatorId) 组分别调用 `getMaxCommittedCheckpointId(ancestors, jobId, operatorId)` 获取该组已提交的最大 checkpoint ID，再按 headMap/tailMap 切分跳过与待提交部分。同时在对多个 (jobId, operatorId) 组排序时按各组首个 checkpoint ID 升序，保证旧 jobId 的提交先于新 jobId 落地，避免乱序。

## 修改详情

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicCommitter.java` (+58/-27 lines)

**修改目的**：将单层按表分组改为三层按表→(jobId,operatorId)→checkpoint 分组，独立去重。

**工作逻辑**：
- 新增 `java.util.Comparator` import。
- 将 `commitRequestMap` 类型从 `Map<TableKey, NavigableMap<Long, List<CommitRequest>>>` 改为三层结构：
```java
Map<TableKey, Map<JobOperatorKey, NavigableMap<Long, List<CommitRequest<DynamicCommittable>>>>>
    commitRequestMap = Maps.newHashMap();
```
- 构建 map 时三层 `computeIfAbsent` 嵌套，使用 `committable.key()` 作为 TableKey，`new JobOperatorKey(committable)` 作为中间层 key：
```java
commitRequestMap
    .computeIfAbsent(committable.key(), unused -> Maps.newHashMap())
    .computeIfAbsent(new JobOperatorKey(committable), unused -> Maps.newTreeMap())
    .computeIfAbsent(committable.checkpointId(), unused -> Lists.newArrayList())
    .add(request);
```
- 提交循环改为外层遍历表（加载表与祖先链一次），内层遍历该表下的各 (jobId, operatorId) 组。先用 `Comparator.comparingLong(entry -> entry.getValue().firstKey())` 对各组按最小 checkpoint ID 升序排序，保证旧 jobId 提交先落地：
```java
jobEntries.sort(Comparator.comparingLong(entry -> entry.getValue().firstKey()));
```
- 对每个组独立调用 `getMaxCommittedCheckpointId(ancestors, jobKey.jobId(), jobKey.operatorId())`，再 headMap 标记已提交、tailMap 提交未完成部分：
```java
long maxCommittedCheckpointId =
    getMaxCommittedCheckpointId(ancestors, jobKey.jobId(), jobKey.operatorId());
```
- 新增详细注释说明三层结构与每层语义。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/JobOperatorKey.java` (+57/-0 lines, new file)

**修改目的**：提供 (jobId, operatorId) 的值对象，作为三层 map 的中间层 key。

**工作逻辑**：
新增 `JobOperatorKey` 类，从 `DynamicCommittable` 构造，持有 `jobId` 与 `operatorId`，提供访问器与 `equals`/`hashCode`（基于两个字段）。这样同一 (jobId, operatorId) 的 committable 会被分到同一组，独立去重。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/TableKey.java` (+0/-5 lines)

**修改目的**：移除不再使用的 `TableKey(DynamicCommittable)` 构造函数。

**工作逻辑**：
删除从 `DynamicCommittable` 构造 `TableKey` 的便捷构造函数，因为新代码直接使用 `committable.key()` 获取已存在的 `TableKey`，无需重复构造。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestDynamicCommitter.java` (+166/-2 lines)

**修改目的**：新增测试覆盖 jobId 变化场景下的去重行为。

**工作逻辑**：
新增 `testSkipsAlreadyCommittedDataAfterJobIdChanges` 测试：构造一个旧 jobId 的 committable 并模拟其已提交（写入快照摘要），然后用新 jobId 的 committable 提交，验证旧 jobId 对应的 checkpoint 被正确跳过而不会重复提交。测试使用 `OneInputStreamOperatorTestHarness` 驱动 aggregator 与 committer，模拟重启后混合批次的场景。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestDynamicIcebergSink.java` (+103/-2 lines)

**修改目的**：在端到端 sink 测试中覆盖作业重启后 jobId 变化的场景，验证不产生重复数据。

**工作逻辑**：
新增端到端测试，模拟 Flink 作业重启（jobId 变化）后继续写入，验证最终表中的数据没有重复，且 checkpoint 提交顺序正确。

## 总结

本提交修复了一个影响 Flink Dynamic Iceberg Sink 精确一次语义的实际缺陷：作业重启导致 jobId 变化时，混合批次的提交会产生重复数据。修复通过引入 (jobId, operatorId) 维度的独立去重，正确处理了多 jobId 共存的场景，并保证了提交顺序。测试覆盖单元与端到端两个层面，体现了对正确性的严格要求。这是 Flink 集成稳定性的重要改进。
