# 提交 1004：Core: Allow SnapshotProducer to skip uncommitted manifest cleanup after commit (#10523)

## 提交信息

- **序号**：1004 / 4088
- **哈希**：39373d09c276586ddcec971fe35951975bdac66f
- **短哈希**：39373d09c
- **日期**：2024-08-01 12:31:18 -0700
- **作者**：Grant Nicholas
- **提交说明**：Core: Allow SnapshotProducer to skip uncommitted manifest cleanup after commit (#10523)
- **PR/Issue**：#10523

## 总体目的

Iceberg 的 `SnapshotProducer` 是所有"产生新快照"操作（FastAppend、MergeAppend、Overwrite、RowDelta 等）的基类。在一次 commit 成功之后，原本的逻辑会通过 `ops.refresh()` 重新加载表元数据，按快照 ID 取回刚提交的 snapshot，再用它的 manifest 列表调用 `cleanUncommitted`，把本次 commit 之前各次失败尝试所产生的"未提交"manifest 文件从底层存储删除。如果 refresh 因最终一致性等原因拿不到 snapshot，就跳过清理并打一条 warn 日志。

这一套"提交后必清理"的逻辑在 `FastAppend` 场景下其实是多余甚至有害的：

1. FastAppend 不会重写已存在的 manifest，被 append 的外部 manifest 永远不会被改写，因此不存在"重写后的旧 manifest 需要清理"的情况。
2. FastAppend 通过 `appendFile` 写出的新 manifest，已经在每次 commit 重试之间由 `writeNewManifests` 方法清理掉了（重试时会先删上一次 staged 的 manifest 再写新的），所以 commit 成功后再去清理也基本没有东西可删。
3. 原本 `cleanUncommitted` 依赖 `ops.refresh()` 重新加载元数据来获取"已提交 manifest 列表"。在高度最终一致的存储（如 S3）上，refresh 可能拿不到刚提交的 snapshot，导致跳过清理；而即便能拿到，这次额外的 refresh 也是一次对元数据存储的额外 IO。

本提交的目的就是让 `SnapshotProducer` 支持跳过提交后的 manifest 清理，并由 `FastAppend` 在没有 rewrittenAppendManifests 的情况下选择跳过，从而避免不必要的 refresh 与清理调用，同时保留对其它仍需清理的提交类型（默认行为不变）的支持。

## 如何达成设计目的

设计思路分两步：

1. 把"提交后是否需要清理"做成一个可被子类覆盖的钩子 `cleanupAfterCommit()`，默认返回 `true`（保持旧行为）。`FastAppend` 覆盖该方法，仅在 `rewrittenAppendManifests` 非空时返回 `true`，否则返回 `false`。
2. 改造 `commit()` 中清理阶段的实现：
   - 不再使用 `AtomicLong newSnapshotId` 只记录快照 ID，而是改用 `AtomicReference<Snapshot> stagedSnapshot` 保存最后一次 `apply()` 产出的 staged 快照对象。这样 commit 成功后可以直接使用这个 staged snapshot 的 manifest 列表，无需再 `ops.refresh()`。
   - commit 成功后，如果 `cleanupAfterCommit()` 为 true，就用 `stagedSnapshot.get()` 的 `allManifests` 调用 `cleanUncommitted`；不论是否清理，都仍然会删除多次重试产生的多余 manifest list 文件（这是必要的，因为 manifest list 文件名是每次尝试新建的）。
   - 删除原来"refresh 拿不到 snapshot 就跳过清理并 warn"的分支，因为现在不再依赖 refresh。

## 修改详情

### `core/src/main/java/org/apache/iceberg/SnapshotProducer.java`

**修改目的**：引入 `cleanupAfterCommit()` 钩子，并将 commit 后清理从"依赖 refresh 重载"改为"使用 staged snapshot"，让子类可以跳过未提交 manifest 的清理。

**工作逻辑**：
- import 由 `AtomicLong` 改为 `AtomicReference`。
- `commit()` 中把 `AtomicLong newSnapshotId = new AtomicLong(-1L)` 替换为 `AtomicReference<Snapshot> stagedSnapshot = new AtomicReference<>()`；在 `Tasks.foreach(ops)...run` 的 lambda 中，把 `newSnapshotId.set(newSnapshot.snapshotId())` 改为 `stagedSnapshot.set(newSnapshot)`。
- commit 成功后取 `Snapshot committedSnapshot = stagedSnapshot.get()`，日志改为打印该 snapshot 的 id。
- 清理逻辑改为：仅当 `cleanupAfterCommit()` 返回 true 时才调用 `cleanUncommitted(Sets.newHashSet(committedSnapshot.allManifests(ops.io())))`；之后无论如何都遍历 `manifestLists`，删除与 `committedSnapshot.manifestListLocation()` 不同的 manifest list 文件（多次重试产生的孤儿 manifest list）。
- 删除原来基于 `ops.refresh().snapshot(newSnapshotId.get())` 的 `saved != null` 判断与"Failed to load committed snapshot, skipping manifest clean-up"的 warn 分支。
- 新增 `protected boolean cleanupAfterCommit() { return true; }` 方法作为默认实现，供子类覆盖。

### `core/src/main/java/org/apache/iceberg/FastAppend.java`

**修改目的**：让 FastAppend 在没有重写 append manifest 的情况下跳过提交后清理，避免不必要的 IO。

**工作逻辑**：新增覆盖方法：
```java
@Override
protected boolean cleanupAfterCommit() {
  return !rewrittenAppendManifests.isEmpty();
}
```
注释说明：FastAppend 关闭提交后清理的原因是 (1) 被 append 的 manifest 不会被重写；(2) `appendFile` 写出的 manifest 已经在 `writeNewManifests` 中于每次 commit 重试之间被清理。仅当存在 `rewrittenAppendManifests` 时才需要清理。

### `core/src/test/java/org/apache/iceberg/TestFastAppend.java`

**修改目的**：为新的清理行为与重试幂等性添加测试。

**工作逻辑**：新增两个测试：
- `testWriteNewManifestsIdempotency`：注入 3 次 commit 失败后第 4 次成功，验证重试过程中 manifest 不会被重复产生，最终只存在一个 manifest 文件，且 staged manifest 在 commit 后仍存在。
- `testWriteNewManifestsCleanup`：先 `appendFile(FILE_A).apply()` stage 一次（产生 oldManifest），再 `appendFile(FILE_B).apply()` 重新 stage（产生 newManifest），验证 `writeNewManifests` 在重新 stage 时删除了 oldManifest；commit 后验证 oldManifest 文件不存在、newManifest 存在，且表目录下只剩 newManifest。

## 小结

- **成效**：使 `SnapshotProducer` 的提交后清理可被关闭，`FastAppend` 在无重写 manifest 时跳过清理，减少了不必要的 `ops.refresh()` 调用与 `cleanUncommitted` 调用，降低了对最终一致性存储的依赖，并修复了重试场景下 manifest 清理的语义。新增的两个测试覆盖了幂等性与 staged manifest 清理行为。
- **影响范围**：核心模块的 `SnapshotProducer.java`、`FastAppend.java` 与测试 `TestFastAppend.java`，共 3 个文件、+82/-21 行。其它继承 `SnapshotProducer` 的提交类型（MergeAppend、Overwrite、RowDelta 等）因默认 `cleanupAfterCommit()` 为 true，行为保持不变。
- **回迁到 1.4.x 的注意事项**：可以考虑回迁，但需谨慎评估。该改动改变了 commit 后清理的执行路径（从 refresh-based 改为 staged-snapshot-based），属于行为上有一定风险的优化。如果 1.4.x 上游或下游有依赖"commit 后必然 refresh 一次"的隐式行为，可能受影响。建议在回迁前确认 1.4.x 的 `SnapshotProducer.commit()` 与本提交前版本结构一致，并跑全量 commit 相关测试（特别是涉及重试与最终一致性的测试）。整体属于中等风险回迁，主要收益是性能与 IO 减少。
