# 提交 1678：Core: Remove `TableMetadata::Builder::resetMainBranch` (#12149)

## 提交信息

- **序号**：1678 / 4088
- **哈希**：1afa0e739b62355518942629b30c845e51e724a6
- **短哈希**：1afa0e739
- **日期**：2025-02-04（Tue Feb 4 00:27:50 2025 +0000，原始 -0700 时区为 17:27:50 2/3）
- **作者**：smaheshwar-pltr <maheshwarsreesh@gmail.com>
- **提交说明**：Core: Remove `TableMetadata::Builder::resetMainBranch` (#12149)
- **PR/Issue**：#12149

## 总体目的

`TableMetadata.Builder` 中有一个私有方法 `resetMainBranch()`，其功能是"重置 main 分支"——把 `currentSnapshotId` 设为 -1、从 `refs` 中移除 `main` 引用、并记录一条 `RemoveSnapshotRef` 变更。这个方法只在 `updateSchema` 重建表元数据的链路中被调用一次。

同时，Builder 上已存在一个**公共**方法 `removeRef(String name)`，当 `name` 是 `main` 分支时，它不仅做与 `resetMainBranch` 相同的事（设 `currentSnapshotId = -1`、移除 ref、记录变更），还额外**清空 `snapshotLog`**（快照日志）。也就是说 `removeRef` 是 `resetMainBranch` 的超集，功能更完整。

本提交把 `resetMainBranch()` 这个冗余的私有方法删除，将其唯一调用点改为调用已有的公共方法 `removeRef(SnapshotRef.MAIN_BRANCH)`，消除重复代码并使 main 分支重置逻辑统一走同一条路径（同时获得了清空 snapshotLog 的更完整语义）。

## 如何达成设计目的

1. 把 `updateSchema` 链路中的 `.resetMainBranch()` 调用改为 `.removeRef(SnapshotRef.MAIN_BRANCH)`；
2. 删除 `resetMainBranch()` 私有方法定义。

## 修改详情

### `core/src/main/java/org/apache/iceberg/TableMetadata.java`（修改，+1/-11 行）

**修改目的**：消除冗余的私有方法，统一使用公共 `removeRef` 方法。

**工作逻辑**：

- 调用点修改：在重建表元数据的 Builder 链中，把 `.resetMainBranch()` 改为 `.removeRef(SnapshotRef.MAIN_BRANCH)`；
- 删除 `resetMainBranch()` 方法：原方法体为 `this.currentSnapshotId = -1; refs.remove(SnapshotRef.MAIN_BRANCH); if (ref != null) changes.add(new MetadataUpdate.RemoveSnapshotRef(SnapshotRef.MAIN_BRANCH));`；
- 行为差异：`removeRef("main")` 除了做上述操作外，还会 `snapshotLog.clear()`——即清空快照日志。这在重建场景下是更正确的行为，因为重建后旧的快照日志条目已无意义。

## 小结

- **成效**：消除 `TableMetadata.Builder` 中的重复代码，把 main 分支重置逻辑统一到公共 `removeRef` 方法上。同时修正了潜在的不完整行为——`resetMainBranch` 未清空 `snapshotLog`，而 `removeRef` 会清空，使重建后的元数据状态更一致。
- **影响范围**：仅 `core` 模块的 `TableMetadata` 一个文件，涉及 `updateSchema` 重建链路。对正常 schema 更新无功能影响（重建场景下 snapshotLog 本就应清空）。
- **回迁到 1.4.x 的注意事项**：回迁安全，纯重构。需确认 1.4.x 的 `TableMetadata.Builder` 上已有公共 `removeRef(String)` 方法且其语义与 main 分支（含清空 snapshotLog）一致；若 1.4.x 的 `removeRef` 不清空 snapshotLog，则回迁后行为与 main 不完全一致（但与原 `resetMainBranch` 一致，无回退风险）。
