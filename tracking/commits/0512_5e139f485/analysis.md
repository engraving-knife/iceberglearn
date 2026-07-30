# 提交 0512：Core: Don't reset snapshotLog when replacing Table

## 提交信息

- **序号**：0512 / 4088
- **哈希**：5e139f48505b9b622e2cf389eae2bb74d6420510
- **短哈希**：5e139f485
- **日期**：2024-02-17（Sat Feb 17 17:16:57 2024 +0100）
- **作者**：Eduard Tudenhoefner（etudenhoefner）
- **提交说明**：Core: Don't reset snapshotLog when replacing Table (#9732)
- **PR/Issue**：#9732

## 总体目的

`TableMetadata.buildReplacement(...)` 用于“替换表”语义（例如 Spark 的 `CREATE OR REPLACE TABLE`）：在保留旧表快照与历史元数据文件的前提下，重置 schema/partition spec/sort order/location/properties，并把当前分支（main branch）指向“无当前快照”状态——也就是说替换后表的 `currentSnapshot()` 为 null，但旧的 `snapshots()` 列表与 `previousFiles()`（metadata log）保留。

原始实现里，buildReplacement 通过 `Builder.removeRef(SnapshotRef.MAIN_BRANCH)` 来移除 main 分支并重置 `currentSnapshotId = -1`。但 `removeRef` 在处理 main 分支时还有一项副作用：`snapshotLog.clear()`——即把整个快照日志清空。这导致替换表后 `snapshotLog` 丢失，进而破坏以下能力：

1. `MetadataLogEntriesTable`（`metadata_log_entries` 元数据表）的 `latest_snapshot_id` / `latest_schema_id` / `latest_sequence_number` 列依赖 `SnapshotUtil.snapshotIdAsOfTime(table, timestamp)` 反查，而后者正是通过遍历 `table.history()`（即 snapshotLog）实现的。snapshotLog 被清空后，所有历史 metadata log 条目的 `latest_snapshot_id` 都会变成 null（即使对应的 snapshot 仍然存在于 `snapshots()` 列表中），用户查询 `metadata_log_entries` 看不到准确的最新快照信息。
2. 任何依赖 `table.history()` 做“按时间点回溯”的逻辑都会在替换表之后失去历史。

本提交的目的就是：在替换表时只重置 main 分支与 currentSnapshotId，**不要清空 snapshotLog**，从而保留快照历史日志，使 `metadata_log_entries` 等下游查询在 replace 后仍能给出正确结果。

## 如何达成设计目的

设计思路是**分离关注点**：把“重置 main 分支”从通用的 `removeRef(name)` 中拆出来，新增一个专门用于 buildReplacement 的私有方法 `resetMainBranch()`，只做必要的三件事——把 `currentSnapshotId` 置为 -1、从 refs 中移除 main 分支、把对应的 `RemoveSnapshotRef` 变更记入 changes 列表（保证提交变更事件仍然被正确记录）。它**不再调用 `snapshotLog.clear()`**，从而保留历史日志。

然后 `buildReplacement` 中把原来的 `.removeRef(SnapshotRef.MAIN_BRANCH)` 改为调用新的 `.resetMainBranch()`。这样：

- 行为上仍然满足“替换后无当前快照、main 分支被移除”（后续 `build()` 末尾的 `updateSnapshotLog` 在 `currentSnapshotId == -1` 时不会再追加新条目，但也不会清空已有条目，且 `changes` 里会有一条 `RemoveSnapshotRef("main")` 通知下游）。
- 但 snapshotLog 中的历史条目被完整保留，下游 `SnapshotUtil.snapshotIdAsOfTime` 仍能基于这些条目反查到正确的 snapshot id。

注意：`removeRef(String name)` 公共方法本身保持不变，仍可用于其他需要清空历史的场景（如 `removeSnapshots` 流程中 `danglingRefs.forEach(this::removeRef)`），即只针对 buildReplacement 这个特定路径做最小修复。

## 修改详情

### `core/src/main/java/org/apache/iceberg/TableMetadata.java`

**修改目的**：把 buildReplacement 中“重置 main 分支”的行为与 `removeRef` 解耦，避免清空 snapshotLog。

**工作逻辑**：
- `buildReplacement(...)` 末尾的链式调用从 `.removeRef(SnapshotRef.MAIN_BRANCH)` 改为 `.resetMainBranch()`。
- 新增私有方法 `resetMainBranch()`：
  ```java
  private Builder resetMainBranch() {
    this.currentSnapshotId = -1;
    SnapshotRef ref = refs.remove(SnapshotRef.MAIN_BRANCH);
    if (ref != null) {
      changes.add(new MetadataUpdate.RemoveSnapshotRef(SnapshotRef.MAIN_BRANCH));
    }
    return this;
  }
  ```
  - `currentSnapshotId = -1`：把当前快照置为“无”，使 `currentSnapshot()` 返回 null。
  - `refs.remove(SnapshotRef.MAIN_BRANCH)`：从 refs map 中移除 main 分支引用（如果有）。
  - 仅当确实移除了一个 ref 时，才向 `changes` 列表追加 `MetadataUpdate.RemoveSnapshotRef("main")`，与 `removeRef` 中保持一致（确保变更事件能被下游消费，例如 REST Catalog 的 metadata 更新通知）。
  - **关键**：不再调用 `snapshotLog.clear()`，历史快照日志被保留。

对照原 `removeRef` 实现：
```java
public Builder removeRef(String name) {
  if (SnapshotRef.MAIN_BRANCH.equals(name)) {
    this.currentSnapshotId = -1;
    snapshotLog.clear();   // ← 这行就是 bug 源头
  }
  SnapshotRef ref = refs.remove(name);
  if (ref != null) {
    changes.add(new MetadataUpdate.RemoveSnapshotRef(name));
  }
  return this;
}
```
新方法把“main 分支特例”那一段 `snapshotLog.clear()` 显式跳过。

### `core/src/test/java/org/apache/iceberg/TestTableMetadata.java`

**修改目的**：单元测试验证替换表后 snapshotLog 与 snapshots 被保留、currentSnapshot 为 null。

**工作逻辑**：新增 `buildReplacementKeepsSnapshotLog()`：
- 从 `TableMetadataV2Valid.json` 读取一个有 2 个快照、snapshotLog 长度为 2 的 TableMetadata。
- 调用 `metadata.buildReplacement(schema, spec, sortOrder, location, properties)`（用原 metadata 的属性做参数，相当于无变更的替换）。
- 断言 `replacement.currentSnapshot()` 为 null（替换后无当前快照）。
- 断言 `replacement.snapshots()` 长度为 2 且与原列表元素完全一致（快照保留）。
- 断言 `replacement.snapshotLog()` 长度为 2 且与原列表元素完全一致（snapshotLog 保留）——这是修复的核心断言，修复前此断言会失败。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestMetadataTables.java`

**修改目的**：在 Spark 端到端层面验证 `CREATE OR REPLACE TABLE` 之后 `metadata_log_entries` 元数据表的输出仍然正确。

**工作逻辑**：新增 `metadataLogEntriesAfterReplacingTable()` 端到端测试，关键步骤如下：

1. 建表，断言 `snapshots()` / `snapshotLog()` 为空、`currentSnapshot()` 为 null；查 `metadata_log_entries` 仅有一条建表条目（firstEntry，对应表创建，latest_snapshot_id 为 null）。
2. `INSERT INTO` 一次 → snapshots=1, snapshotLog=1，`metadata_log_entries` 应包含 firstEntry + secondEntry（secondEntry 的 latest_snapshot_id 是这次 insert 的 snapshot id）。
3. 再 `INSERT INTO` 一次 → snapshots=2, snapshotLog=2，metadata_log_entries 应包含三条（firstEntry, secondEntry, thirdEntry），thirdEntry 对应第二次 insert 的快照。
4. 执行 `CREATE OR REPLACE TABLE`（同样的 schema/分区）→ 这是关键验证点：
   - 断言 `snapshots()` 仍为 2，`snapshotLog()` 仍为 2（替换未清空历史）。
   - 断言 `currentSnapshot()` 为 null。
   - 但是 `metadata_log_entries` 应该有第四条 fourthEntry，对应这次 replace 操作生成的 metadata 文件；它的 `latest_snapshot_id` 应当是 snapshotLog 最后一条所指向的 snapshot id（即最后一次 insert 的 snapshot id），而不是 null。这正是因为 snapshotLog 没被清空，`SnapshotUtil.snapshotIdAsOfTime` 才能反查到。
5. 再 `INSERT INTO` 一次 → snapshots=3, snapshotLog=3，metadata_log_entries 应有第五条 fifthEntry，对应这次新 insert 的快照。

测试还引入了 `org.apache.iceberg.HistoryEntry` 与 `assertj` 的 `assertThat`，体现了对 snapshotLog 历史条目的直接断言能力。

注意：此测试只覆盖 spark v3.5 模块（实际改动路径是 `spark/v3.5/spark-extensions/...`），因为该修复属于 core 行为修复，v3.5 测试足以代表行为，其他 Spark 版本未同步加入测试。

## 小结

本提交修复了一个比较隐蔽但影响显著的回归：替换表（`CREATE OR REPLACE TABLE` 或 `buildReplacement`）后，`snapshotLog` 被错误清空，导致下游 `metadata_log_entries` 元数据表对历史 metadata 文件的 `latest_snapshot_id` 列查询失效（变成 null），同时任何依赖 `table.history()` 的时间点回溯能力在 replace 后失效。

修复方式极简：新增 `Builder.resetMainBranch()` 私有方法替代 buildReplacement 中调用的 `removeRef(MAIN_BRANCH)`，把“清空 snapshotLog”这一副作用剥离出去。公共 `removeRef` 方法保持不变，避免影响 expire snapshots 等其他需要清空历史的合法场景。

**影响范围**：
- 仅影响 buildReplacement 这条路径的 snapshotLog 行为。
- 行为变化：replace 表后 snapshotLog 保留（之前被清空）；snapshots、previousFiles 等本就保留，不变。
- `changes` 中仍会发出 `RemoveSnapshotRef("main")` 更新事件，下游消费者感知一致。

**回迁到 1.4.x 注意事项**：
1. 这是行为修复，回迁后 1.4.x 中 `CREATE OR REPLACE TABLE` 之后的 `metadata_log_entries` 查询结果会发生变化（latest_snapshot_id 不再为 null），属于向前修正，但需注意是否有下游用户依赖了旧的（错误）行为——一般不应有。
2. 修改极小，只需把 `buildReplacement` 中 `.removeRef(SnapshotRef.MAIN_BRANCH)` 改为 `.resetMainBranch()` 并新增 `resetMainBranch()` 私有方法即可。
3. 测试需要同步回迁 `TestTableMetadata#buildReplacementKeepsSnapshotLog` 与 `TestMetadataTables#metadataLogEntriesAfterReplacingTable`（后者位于 `spark/v3.5/spark-extensions`），若 1.4.x 还维护 spark v3.4/v3.3 模块，可考虑同步加入等价测试。
4. 回迁前确认 1.4.x 的 `TableMetadata.Builder` 结构与 main 一致；`refs`、`currentSnapshotId`、`changes` 字段在 1.4.x 中本就存在，无新依赖。
