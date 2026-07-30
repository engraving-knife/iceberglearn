# 提交 3813：Flink: Backport DynamicCommitter jobId fix to v1.20 and v2.0 (#16648)

## 提交信息

- **序号**：3813 / 4088
- **哈希**：785723853323db8395ffdc5a4c913857b2007c68
- **短哈希**：785723853
- **日期**：2026-06-01 09:26:46 -0700
- **作者**：lrsb <github@lrsb.xyz>
- **提交说明**：Flink: Backport DynamicCommitter jobId fix to v1.20 and v2.0 (#16648)
- **PR/Issue**：#16648（回PORT PR），原修复 #16011

## 总体目的

本提交将上一个提交（#16011，提交 3811）中针对 Flink v2.1 的 `DynamicCommitter` jobId 变化重复提交修复，回移植（backport）到 Iceberg 同时维护的另外两个 Flink 版本分支：`flink/v1.20` 与 `flink/v2.0`。Iceberg 项目为多个 Flink 大版本（1.20、2.0、2.1）分别维护独立的源代码目录，每个目录下都有完整的 sink 实现。原修复只落在 v2.1 目录，若不回移植，使用 v1.20 或 v2.0 的用户在作业重启后仍会遇到重复提交问题。

回移植的内容与原修复完全一致：将单层按表分组改为三层按表→(jobId, operatorId)→checkpoint 分组，独立对每个 (jobId, operatorId) 组查询已提交的最大 checkpoint ID 进行去重，并新增 `JobOperatorKey` 值对象，移除 `TableKey` 的冗余构造函数，同时移植单元测试与端到端测试。

## 如何达成设计目的

由于 Iceberg 的 Flink 多版本目录结构平行，回移植只需把 v2.1 下的改动原样应用到 v1.20 与 v2.0 对应文件。两个版本的改动内容、行数完全一致，体现了多版本并行维护的对称性。

## 修改详情

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicCommitter.java` (+58/-27 lines)

**修改目的**：将 v1.20 版本的 `DynamicCommitter` 改为三层分组独立去重，与 v2.1 修复一致。

**工作逻辑**：
与提交 3811 中 `DynamicCommitter.java` 的改动完全相同：构建 `Map<TableKey, Map<JobOperatorKey, NavigableMap<Long, List<CommitRequest>>>>` 三层结构，外层按表加载祖先链一次，内层对每个 (jobId, operatorId) 组独立调用 `getMaxCommittedCheckpointId` 去重，并按各组最小 checkpoint ID 升序排序保证旧 jobId 先提交。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/JobOperatorKey.java` (+57/-0 lines, new file)

**修改目的**：在 v1.20 目录新增 (jobId, operatorId) 值对象。

**工作逻辑**：与提交 3811 中新增的 `JobOperatorKey` 完全一致。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/TableKey.java` (+0/-5 lines)

**修改目的**：移除 v1.20 中不再使用的 `TableKey(DynamicCommittable)` 构造函数。

**工作逻辑**：与提交 3811 一致。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestDynamicCommitter.java` (+167/-2 lines)

**修改目的**：在 v1.20 测试中新增 jobId 变化场景的单元测试。

**工作逻辑**：与提交 3811 一致。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestDynamicIcebergSink.java` (+103/-2 lines)

**修改目的**：在 v1.20 端到端测试中新增重启场景测试。

**工作逻辑**：与提交 3811 一致。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicCommitter.java` (+58/-27 lines)

**修改目的**：将 v2.0 版本的 `DynamicCommitter` 改为三层分组独立去重。

**工作逻辑**：与 v1.20、v2.1 改动完全一致。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/JobOperatorKey.java` (+57/-0 lines, new file)

**修改目的**：在 v2.0 目录新增 `JobOperatorKey` 值对象。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/TableKey.java` (+0/-5 lines)

**修改目的**：移除 v2.0 中不再使用的 `TableKey(DynamicCommittable)` 构造函数。

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestDynamicCommitter.java` (+167/-2 lines)

**修改目的**：在 v2.0 测试中新增 jobId 变化场景的单元测试。

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestDynamicIcebergSink.java` (+103/-2 lines)

**修改目的**：在 v2.0 端到端测试中新增重启场景测试。

## 总结

本提交是提交 3811 的回移植，将 `DynamicCommitter` jobId 变化重复提交修复同步到 Flink v1.20 与 v2.0 两个版本分支，确保所有受支持的 Flink 版本都能受益于该修复。这体现了 Iceberg 项目对多版本并行维护纪律的严格遵守——重要的 bug 修复必须同步到所有受影响版本分支，避免版本间的行为差异。
