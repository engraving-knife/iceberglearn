# 提交 1817：Docs: Deprecate `data_file.distinct_counts` (#12182)

## 提交信息

- **序号**：1817 / 4088
- **哈希**：be9808fb3d75b1697fc4ed45c02602de9cfb6371
- **短哈希**：be9808fb3
- **日期**：2025-03-03 22:28:27 -0800
- **作者**：Jacob Marble
- **提交说明**：Docs: Deprecate `data_file.distinct_counts` (#12182)
- **PR/Issue**：#12182

## 总体目的

这是一个规范文档变更提交，将 Iceberg 表格式规范（spec）中 manifest 文件的 `data_file.distinct_counts` 字段标记为已废弃（Deprecated）。

`distinct_counts` 字段（字段 ID 111）原本用于记录每个列的不同值数量，作为统计信息辅助查询优化器估算基数。然而实践中，该字段存在以下问题：distinct counts 必须通过对文件中的值进行计数或使用 sketch 派生，而不能通过合并已有 distinct counts 得到，这导致该字段的维护成本高且跨文件聚合困难。社区决定废弃该字段，不再鼓励写入器写入该统计信息。

本次提交将规范中该字段的描述从详细的功能说明改为明确的废弃声明，采用删除线标记字段名并注明"Do not write"。

## 如何达成设计目的

作者通过修改 `format/spec.md` 规范文档中 manifest_entry schema 表格的对应行，将 `distinct_counts` 字段的 V2/V3/V4 列勾选从 `_optional_` 改为空白（表示不再写入），字段名添加删除线标记 `~~**111 distinct_counts**~~`，并将描述文本从详细的功能说明替换为简洁的废弃声明 `**Deprecated. Do not write.**`。这一变更遵循了规范文档中标记废弃字段的惯例。

## 修改详情

### format/spec.md (修改, 1 line)

修改了 manifest_entry schema 表格中 `distinct_counts` 字段的行。变更内容：
- V2 列：保留 `_optional_`（旧实现仍可能读取）
- V3 列：保留 `_optional_`
- V4 列：从 `_optional_` 改为空白（表示 V4 起不再写入）
- 字段名：添加删除线标记 `~~**111 distinct_counts**~~`
- 类型：保持 `map<123: int, 124: long>` 不变
- 描述：从原来的"Map from column id to number of distinct values in the column; distinct counts must be derived using values in the file by counting or using sketches, but not using methods like merging existing distinct counts"替换为"`**Deprecated. Do not write.**`"

## 小结

这是一个纯规范文档变更，将 `distinct_counts` 字段标记为废弃，指导写入器不再写入该统计信息。影响范围为规范文档层面，不直接修改代码逻辑。回迁到 1.4.x 分支时，需确认 1.4.x 分支的 spec.md 中该字段的当前状态；若 1.4.x 分支仍保留旧描述，可直接 cherry-pick 此文档变更。该变更与后续废弃字段清理的代码变更相配合。注意完整哈希对应的短哈希为 `be9808fb3`（取前9位）。
