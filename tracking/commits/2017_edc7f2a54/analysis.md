# 提交 2017：Spec: Update row lineage requirements for upgrading tables (#12781)

## 提交信息

- **序号**：2017 / 4088
- **哈希**：edc7f2a5400897499b8e9b63a2e75bafe6be5808
- **短哈希**：edc7f2a54
- **日期**：2025-04-19 17:13:46 -0700
- **作者**：Ryan Blue
- **提交说明**：Spec: Update row lineage requirements for upgrading tables (#12781)
- **PR/Issue**：#12781

## 总体目的

这个提交更新了 Iceberg 规范中关于行血统（row lineage）的文档，主要修正了 first-row-id 分配的描述，特别是针对表升级（upgrading tables）到 V3 格式时的行 ID 分配要求。这是对提交 2014 实现的 first-row-id 机制的规范文档同步和修正。

此前规范中存在几个不准确之处：
1. first-row-id 的分配仅考虑了 `added_rows_count`，未考虑 `existing_rows_count`（升级表中的现有数据文件也需要分配行 ID）。
2. 移除了 `added-rows` 快照字段（改为在代码中计算 assignedRows）。
3. 升级表的行血统要求描述不够清晰，需要明确升级前后的行为差异。
4. `next-row-id` 从 optional 改为 required。
5. 行 ID 继承规则需要扩展为对所有 null first_row_id 的数据文件生效（不仅仅是 ADDED 文件）。

## 如何达成设计目的

通过修改 `format/spec.md` 中的多个章节来修正规范描述，包括：行血统概述、First Row ID Inheritance、First Row ID Assignment、Snapshot Row IDs、表元数据中的 next-row-id 等。

## 修改详情

### `format/spec.md` (修改, +32/-24 lines)

**修改目的**：修正行血统规范中关于 first-row-id 分配的多个描述。

**工作逻辑**：

1. **first-row-id 分配规则修正**：清单列表中 manifest 的 first_row_id 分配从仅基于 `added_rows_count` 改为基于 `added_rows_count` 和 `existing_rows_count` 之和。示例表格中 added2 的 first_row_id 从 1100 改为 1125，added3 从 1100 改为 1225，因为 existing rows 也需要分配行 ID 空间。

2. **移除 added-rows 字段**：从快照字段表中移除了 `added-rows` 字段，因为该值现在通过 ManifestListWriter 的 nextRowId 计算（assignedRows），不再作为独立字段存储。同时移除了相关描述。

3. **升级表行血统要求更新**：重写了"Row Lineage for Upgraded Tables"章节。明确了升级后 next-row-id 初始化为 0，旧快照不修改，新快照必须设置 first-row-id 并为所有数据 manifest 分配 first_row_id。新增了关于不同分支中同一数据文件可能被分配不同 first_row_id 的说明。

4. **First Row ID Inheritance 更新**：继承规则从"仅 ADDED 数据文件"扩展为"所有 null first_row_id 的数据文件"（包括 EXISTING），确保升级表中现有数据文件也能获得行 ID。

5. **next-row-id 改为 required**：表元数据中 `next-row-id` 从 optional 改为 required。

6. **First Row ID Assignment 更新**：清单列表中 first_row_id 的分配描述更新为考虑所有需要通过继承分配 first_row_id 的数据文件，提供了简单的有效估算方法：`first_row_id = last_assigned.first_row_id + last_assigned.added_rows_count + last_assigned.existing_rows_count`。

7. **first-row-id 始终必需**：`first-row-id` 字段即使在提交不分配任何 ID 空间时也是必需的。

## 总结

本提交是行血统规范的文档修正，使规范描述与提交 2014 的实现保持一致。关键变更包括：first-row-id 分配考虑 existing_rows_count、移除 added-rows 快照字段、扩展行 ID 继承规则至所有 null first_row_id 的文件、next-row-id 改为 required，以及明确升级表的行血统行为。
