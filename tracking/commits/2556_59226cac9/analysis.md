# 提交 2556：Docs: Update API docs and remove iceberg-hive3 reference (#13898)

## 提交信息

- **序号**：2556 / 4088
- **哈希**：59226cac9ef3fc301f4644ce30acbbdcbdad99d5
- **短哈希**：59226cac9
- **日期**：2025-08-25 09:23:31 +0200
- **作者**：ayushjariyal
- **提交说明**：Docs: Update API docs and remove iceberg-hive3 reference (#13898)
- **PR/Issue**：#13898

## 总体目的

该提交更新了 Iceberg 项目的 API 文档，主要做了两件事：

1. **补充 API 操作列表**：在 `docs/docs/api.md` 的表更新操作列表中，补充了多个之前未列出的操作方法，使文档更完整地反映 Iceberg Table API 实际支持的操作。之前文档只列出了部分操作（如 updateSchema、updateProperties、newAppend 等），遗漏了 updateSpec、updateStatistics、expireSnapshots、manageSnapshots、newRowDelta、replaceSortOrder、newReplacePartitions 等重要操作，用户无法从文档中全面了解可用功能。

2. **移除 iceberg-hive3 引用**：Iceberg 项目已不再维护 `iceberg-hive3` 模块（Hive 3 专用的 SerDe 实现），但文档中仍残留对该模块的引用。移除过时的模块引用可以避免用户误以为该模块仍在维护，减少混淆。

## 如何达成设计目的

- 在 API 文档的操作列表中补充 6 个遗漏的操作方法及其简要说明。
- 删除文档中对 `iceberg-hive3` 模块的整行描述。

## 修改详情

### `docs/docs/api.md` (+8/-2)

**修改目的**：完善 API 文档操作列表并移除过时的模块引用。

**工作逻辑**：
- 新增以下操作说明：`updateSpec`（修改分区规范）、`updateStatistics`（更新统计文件）、`updatePartitionStatistics`（更新分区统计）、`expireSnapshots`（移除旧快照）、`manageSnapshots`（管理快照）、`newRowDelta`（删除或替换行）、`replaceSortOrder`（替换排序顺序）、`newReplacePartitions`（动态覆盖分区）。
- 将 `rollback` 替换为 `replaceSortOrder` 和 `newReplacePartitions` 的说明（rollback 被移除并替换为新内容，可能是调整了文档的组织方式）。
- 删除 `iceberg-hive3` 模块的描述行。

## 总结

该提交完善了 API 文档中的表操作列表，补充了 6+ 个遗漏的操作说明，并移除了已废弃的 iceberg-hive3 模块引用，使文档更准确完整。
