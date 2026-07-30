# 提交 2234：Spark: RewriteTablePath should filter returned content files by snapshotId

## 提交信息

- **序号**：2234 / 4088
- **哈希**：62d9ff5d043a5571efe020b9177998ae763a41a0
- **短哈希**：62d9ff5d0
- **日期**：2025-06-12 20:50:07 -0700
- **作者**：Hongyue/Steve Zhang
- **提交说明**：Spark: RewriteTablePath should filter returned content files by snapshotId
- **PR/Issue**：#12885

## 总体目的

本提交修复了 Spark `RewriteTablePath` 动作中的一个重要问题。`RewriteTablePath` 用于重写表路径（将数据文件的路径前缀从一个位置替换到另一个位置），在增量拷贝场景下（指定 startVersion），它应只返回在指定版本之间变更的内容文件。但原来的实现没有按 snapshotId 过滤返回的内容文件，导致即使某些文件在增量快照范围之外（例如通过 manifest rewrite 操作产生的未变更数据文件），也被错误地包含在 copyPlan 中。这会导致不必要的数据文件拷贝，浪费存储和计算资源。修复后，只有属于增量快照范围内的文件才会被包含在 copyPlan 中。

## 如何达成设计目的

- 在 `RewriteTablePathUtil` 中新增带 `snapshotIds` 参数的 `rewriteDataManifest` 和 `rewriteDeleteManifest` 方法重载，将旧的无 snapshotIds 参数的方法标记为 `@Deprecated`。
- 在 `writeDataFileEntry` 和 `writeDeleteFileEntry` 方法中，将 copyPlan 的构建条件从仅检查 `entry.isLive()` 改为同时检查 `snapshotIds.contains(entry.snapshotId())`，即只有属于增量快照范围的存活文件才会被加入 copyPlan。
- 在 `RewriteTablePathSparkAction` 中，将增量快照的 snapshotId 集合通过 Broadcast 传递到 Spark executor，供 manifest 重写时过滤使用。
- 新增测试 `testManifestRewriteAndIncrementalCopy` 验证 manifest rewrite 后增量拷贝不会包含未变更的数据文件。
- 修改在 v3.4、v3.5、v3.5 三个 Spark 版本下同步进行。

## 修改详情

### `core/src/main/java/org/apache/iceberg/RewriteTablePathUtil.java` (修改, +88/-6 lines)

**修改目的**：新增按 snapshotId 过滤内容文件的能力。

**工作逻辑**：
- 新增带 `Set<Long> snapshotIds` 参数的 `rewriteDataManifest` 和 `rewriteDeleteManifest` 方法，将旧方法标记 `@Deprecated`（旧方法内部调用时传入空集合 `Set.of()` 保持兼容）。
- 在 `writeDataFileEntry` 和 `writeDeleteFileEntry` 中，copyPlan 的添加条件从 `entry.isLive()` 改为 `entry.isLive() && snapshotIds.contains(entry.snapshotId())`。所有 manifest entry 仍会写入新 manifest（保留完整元数据），但只有属于增量快照范围的文件才会被加入 copyPlan（即实际需要拷贝的文件列表）。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteTablePathSparkAction.java` (修改, +30/-8 lines)

**修改目的**：将增量快照 ID 集合传递到 Spark executor 用于过滤。

**工作逻辑**：
- `rewriteManifests` 方法新增 `Set<Snapshot> deltaSnapshots` 参数，将其转为 snapshotId 集合并通过 `sparkContext().broadcast()` 广播到 executor。
- `toManifests`、`writeDataManifest`、`writeDeleteManifest` 方法链路中新增 `Broadcast<Set<Long>> deltaSnapshotIds` 参数，最终传递给 `RewriteTablePathUtil` 的对应方法。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteTablePathsAction.java` (修改, +30/-8 lines)

**修改目的**：验证增量拷贝场景下按 snapshotId 过滤的正确性。

**工作逻辑**：新增 `testManifestRewriteAndIncrementalCopy` 测试，先执行一次完整的 RewriteTablePath，然后执行 manifest rewrite（不改变数据文件），再以 v3 为起始版本执行增量 RewriteTablePath，验证结果中没有数据文件需要移动（只有 manifest 文件）。同时修复了已有测试中因过滤逻辑变化导致的预期文件数调整。

### Spark v3.5 和 v3.5 版本同步修改

`RewriteTablePathSparkAction.java` 和 `TestRewriteTablePathsAction.java` 在 Spark 3.5 和 3.5 版本目录下做了相同的修改。

## 总结

本提交修复了 `RewriteTablePath` 在增量拷贝场景下未按 snapshotId 过滤内容文件的 bug。修复后，只有属于增量快照范围的数据文件和删除文件才会被包含在 copyPlan 中，避免了不必要的数据拷贝操作。这对于 manifest rewrite 等不改变数据文件的操作场景尤为重要，显著提升了增量路径重写的效率。
