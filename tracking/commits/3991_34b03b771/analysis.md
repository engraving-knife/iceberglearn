# 提交 3991：Flink: Resolve unpartitioned equality deletes across all partitions (#17018)

## 提交信息

- **序号**：3991 / 4088
- **哈希**：34b03b7710a116a7ffc14b2e68aad1b8ee484d1f
- **短哈希**：34b03b771
- **日期**：2026-07-07 11:08:49 +0200
- **作者**：Maximilian Michels
- **提交说明**：Flink: Resolve unpartitioned equality deletes across all partitions (#17018)
- **PR/Issue**：#17018

## 总体目的

本提交修复了 Flink 维护框架中 equality delete 转换在分区演化（partition evolution）场景下的正确性问题。此前，equality delete 的索引键（key）包含了 partition spec ID 作为前缀，导致不同 spec 下的同一主键行被分到不同的 keyed-state shard。这意味着一个 unpartitioned（全局）equality delete 只能删除与其相同 spec ID 的数据行，无法跨分区 spec 删除数据。

这在分区演化场景下是错误的：如果数据在旧 spec（分区表）下写入，然后表演化为 unpartitioned，此时写入的 equality delete 是全局的，应该删除所有分区中的匹配行，但实际上无法删除旧 spec 中的行。

修复方案是：将索引键从 spec ID + equality values 改为仅 equality values，使所有 spec 下的同一主键行 co-locate 到同一 shard。然后在 resolve 时通过 spec ID 范围（scope）来决定一个 delete 应用到哪些 spec 的行——unpartitioned delete（GLOBAL_DELETE_SPEC_ID）应用到所有 spec，partitioned delete 只应用到相同 spec 的行。

## 如何达成设计目的

1. **StructLikeSerializer**：`serializeKey` 方法移除 spec ID 前缀，仅从 equality values 序列化键。
2. **IndexCommand**：新增 `deleteSpecId` 字段和 `GLOBAL_DELETE_SPEC_ID = -1` 哨兵值。`resolveDelete` 工厂方法新增 deleteSpecId 参数。
3. **EqualityConvertReader**：对于 unpartitioned delete 文件，使用 `GLOBAL_DELETE_SPEC_ID`；对于 partitioned delete，使用实际 spec ID。同时修复 partition type 获取，使用 planner 序列化的 task spec 而非 reader 加载的 table（后者可能缺少新增的 spec）。
4. **EqualityConvertPKIndex**：新增 `resolveSpecIds` ListState 累积一个 cycle 中所有 delete 的 spec ID。在 resolve 时，如果 deleteSpecIds 包含 GLOBAL_DELETE_SPEC_ID，则应用到所有 spec 的行；否则只应用到匹配 spec ID 的行。

## 修改详情

### `flink/v2.1/flink/src/main/java/.../EqualityConvertPKIndex.java` (+24/-4 lines)

**修改目的**：按 spec 范围应用 delete。

**工作逻辑**：
- 新增 `RESOLVE_SPEC_IDS_DESCRIPTOR` ListState 和 `resolveSpecIds` 状态。
- 处理 RESOLVE_DELETE 时：`resolveSpecIds.add(cmd.deleteSpecId())` 累积每个 delete 的 spec。
- `resolveDeletes` 中：
```java
Set<Integer> deleteSpecIds = Sets.newHashSet(resolveSpecIds.get());
boolean globalDelete = deleteSpecIds.contains(IndexCommand.GLOBAL_DELETE_SPEC_ID);
for (DVPosition pos : dataRowPositions.get()) {
  boolean specMatch = globalDelete || deleteSpecIds.contains(pos.specId());
  boolean sequenceMatch = !stagingOnTargetBranch || deleteSeq == null || pos.dataSequenceNumber() < deleteSeq;
  if (specMatch && sequenceMatch) {
    out.collect(pos);
  }
}
```

### `flink/v2.1/flink/src/main/java/.../EqualityConvertReader.java` (+8/-8 lines)

**修改目的**：为 delete 设置正确的 spec ID，移除键序列化中的 spec ID。

**工作逻辑**：
```java
int deleteSpecId = task.spec().isUnpartitioned() ? IndexCommand.GLOBAL_DELETE_SPEC_ID : specId;
```
- partition type 从 `task.spec().partitionType()` 获取（而非 table.specs()）。
- `serializeKey` 调用移除 specId 参数。

### `flink/v2.1/flink/src/main/java/.../IndexCommand.java` (+18/-6 lines)

**修改目的**：新增 deleteSpecId 字段和 GLOBAL_DELETE_SPEC_ID 常量。

**工作逻辑**：record 新增 `int deleteSpecId` 字段，`resolveDelete` 工厂方法新增参数，`addDataRow` 和 `clearBeforeReindex` 传入 -1。

### `flink/v2.1/flink/src/main/java/.../StructLikeSerializer.java` (+5/-6 lines)

**修改目的**：移除键中的 spec ID 前缀。

**工作逻辑**：`serializeKey` 方法移除 `dos.writeInt(specId)`，仅序列化 equality values。

### 测试文件

**修改目的**：验证跨分区演化的 delete 行为。

**工作逻辑**：
- `TestConvertEqualityDeletes`：新增 `testUnpartitionedDeleteAppliesAcrossPartitionEvolution`（全局 delete 删除旧 spec 行）和 `testPartitionedDeleteDoesNotApplyAcrossPartitionEvolution`（分区 delete 不删除不同 spec 的行）。
- `TestEqualityConvertPKIndex`：新增参数化测试 `resolvesDeletesScopedBySpec`，覆盖 4 种 spec 组合场景。
- `TestStructLikeSerializer`：更新键序列化测试（移除 specId 参数）。

## 总结

本提交修复了 equality delete 转换在分区演化场景下的正确性问题。通过将索引键与 spec ID 解耦，并在 resolve 时按 spec 范围应用 delete，确保 unpartitioned（全局）delete 能跨分区 spec 删除数据，而 partitioned delete 只作用于相同 spec 的行。这是一个重要的正确性修复，解决了分区演化下数据不一致的风险。
