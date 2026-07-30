# 提交 1960：Core: Enhance remove snapshots efficiency by executing them in bulk (#12670)

## 提交信息

- **序号**：1960 / 4088
- **哈希**：06f667ada5a5b9edeaa20ae9269ff5de1721b91d
- **短哈希**：06f667ada
- **日期**：2025-04-03 16:05:54 -0600
- **作者**：Ricardo Pereira
- **提交说明**：Core: Enhance remove snapshots efficiency by executing them in bulk (#12670)
- **PR/Issue**：#12670

## 总体目的

此前 Iceberg 在删除多个快照（snapshot）时，`TableMetadata.Builder` 的 `rewriteSnapshotsInternal` 会对每一个要删除的 snapshot 单独产生一个 `MetadataUpdate.RemoveSnapshot(snapshotId)` 更新。这意味着删除 N 个快照会产生 N 个独立的 `RemoveSnapshot` 更新，在提交（commit）时这些更新会被逐个序列化与处理，效率较低，且与规范中"基于集合的删除"（set-based removal，JSON 字段 `snapshot-ids` 是数组）的语义不完全契合（此前 parser 虽然读数组但强制只允许一个元素）。

本提交引入新的 `MetadataUpdate.RemoveSnapshots`（复数）更新类型，一次性携带一组要删除的 snapshot id，使 `TableMetadata.Builder` 在删除多个快照时只产生一个批量更新，从而提升提交效率并更好地对齐规范的 set-based 语义。旧的 `RemoveSnapshot`（单数）保留并标记为 `@Deprecated`（自 1.9.0 起），向后兼容。

## 如何达成设计目的

整体设计是新增一个批量版本的更新类型，并让 builder 优先使用它，同时保持序列化/反序列化的向后兼容：

1. **`MetadataUpdate.RemoveSnapshots`**（新增）：持有 `Set<Long> snapshotIds`，`applyTo` 调用 `metadataBuilder.removeSnapshots(snapshotIds)`。
2. **`MetadataUpdate.RemoveSnapshot`**（旧，标记 `@Deprecated`）：保留单个 snapshot id 的语义，向后兼容。
3. **`TableMetadata.Builder.rewriteSnapshotsInternal`**：改为在循环中收集要删除的 snapshot id 到一个 `Set`，循环结束后若集合非空则只添加一个 `RemoveSnapshots` 更新（而非每条一个 `RemoveSnapshot`）。
4. **`TableMetadata.Builder` 的快照日志处理**：判断"是否有删除快照的更新"时，同时识别 `RemoveSnapshots` 与 `RemoveSnapshot` 两种类型。
5. **`MetadataUpdateParser`**：
   - 序列化（`writeRemoveSnapshots`）：参数类型改为 `RemoveSnapshots`；当遇到旧的 `RemoveSnapshot` 时，先转换为 `RemoveSnapshots(ImmutableSet.of(snapshotId))` 再序列化。两个类映射到同一个 JSON action `remove-snapshots`，写出 `snapshot-ids` 数组。
   - 反序列化（`readRemoveSnapshots`）：放宽校验，允许数组含任意非空数量的 id；若只有一个则返回旧版 `RemoveSnapshot`（兼容），否则返回新版 `RemoveSnapshots`。
6. **测试**：扩展 `TestMetadataUpdateParser`、`TestUpdateRequirements`，新增 `TestCommitTransactionRequestParser` 测试覆盖批量场景。

## 修改详情

### `core/src/main/java/org/apache/iceberg/MetadataUpdate.java` (修改, +22/-0 lines)

**修改目的**：新增批量删除快照更新类型，并弃用旧的单一删除类型。

**工作逻辑**：
- 给 `RemoveSnapshot` 类添加 `@Deprecated` 注解与 Javadoc（since 1.9.0，will be removed in 2.0.0，建议用 `RemoveSnapshots`）。
- 新增 `RemoveSnapshots` 类：持有 `Set<Long> snapshotIds`，提供构造器、`snapshotIds()` 访问器，`applyTo` 调用 `metadataBuilder.removeSnapshots(snapshotIds)`。

### `core/src/main/java/org/apache/iceberg/TableMetadata.java` (修改, +14/-1 lines)

**修改目的**：让 builder 在删除多个快照时产生单个批量更新。

**工作逻辑**：
- `rewriteSnapshotsInternal`：新增 `Set<Long> snapshotIdsToRemove`，循环中对每个被删除的 snapshot 不再立即 `changes.add(new RemoveSnapshot(snapshotId))`，而是把 id 加入集合；循环结束后若集合非空，一次性 `changes.add(new RemoveSnapshots(snapshotIdsToRemove))`。
- 快照日志处理中判断 `hasRemovedSnapshots` 时，改为 `change instanceof RemoveSnapshots || change instanceof RemoveSnapshot`，同时识别新旧两种类型。

### `core/src/main/java/org/apache/iceberg/MetadataUpdateParser.java` (修改, +32/-7 lines)

**修改目的**：支持批量类型的序列化/反序列化，并保持向后兼容。

**工作逻辑**：
- 类映射表 `METADATA_UPDATE_ACTION` 中新增 `RemoveSnapshots.class -> REMOVE_SNAPSHOTS`（与 `RemoveSnapshot` 共用同一 action 名）。
- 序列化分支 `REMOVE_SNAPSHOTS`：若 update 是旧版 `RemoveSnapshot`，先转为 `RemoveSnapshots(ImmutableSet.of(snapshotId))`，再调用 `writeRemoveSnapshots`；否则直接强转。`writeRemoveSnapshots` 签名改为接收 `RemoveSnapshots`，写出 `update.snapshotIds()` 数组（移除原来"TODO reconcile set-based removal"注释）。
- 反序列化 `readRemoveSnapshots`：放宽校验为"snapshotIds 非空"（不再要求 size==1）；若 size==1 返回旧版 `RemoveSnapshot`（兼容），否则返回新版 `RemoveSnapshots`。

### `core/src/test/java/org/apache/iceberg/TestMetadataUpdateParser.java` (修改, +52/-3 lines)

**修改目的**：覆盖批量删除快照的序列化/反序列化。

**工作逻辑**：扩展测试，验证多个 snapshot id 的 `RemoveSnapshots` 能正确往返序列化，以及单个 id 仍使用旧版 `RemoveSnapshot` 的兼容行为。

### `core/src/test/java/org/apache/iceberg/TestUpdateRequirements.java` (修改, +27/-1 lines)

**修改目的**：覆盖 builder 产生批量更新的需求校验。

**工作逻辑**：扩展测试验证删除多个快照时产生单个 `RemoveSnapshots` 更新而非多个 `RemoveSnapshot`。

### `core/src/test/java/org/apache/iceberg/rest/.../TestCommitTransactionRequestParser.java` (修改, +80/-0 lines)

**修改目的**：覆盖提交事务请求中批量删除快照的解析。

**工作逻辑**：新增测试验证 `CommitTransactionRequest` 中包含 `RemoveSnapshots` 更新的序列化与反序列化。

## 总结

本提交通过新增 `MetadataUpdate.RemoveSnapshots`（批量）类型，让 `TableMetadata.Builder` 在删除多个快照时只产生一个批量更新而非每快照一个更新，提升提交效率并更好地对齐规范的 set-based 语义。`MetadataUpdateParser` 支持新旧两种类型与同一 JSON action 的兼容序列化，旧版 `RemoveSnapshot` 标记为弃用（1.9.0 起，2.0.0 移除），并补充了相应测试。
