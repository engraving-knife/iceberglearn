# 提交 2093：Spec: Update v3 summary, add row lineage (#12982)

## 提交信息

- **序号**：2093 / 4088
- **哈希**：3bb6a25ccd34c4bd3e31abc7ad99d26b9cf3557e
- **短哈希**：3bb6a25c
- **日期**：2025-05-07 09:16:55 -0500
- **作者**：Ryan Blue <blue@apache.org>
- **提交说明**：Spec: Update v3 summary, add row lineage (#12982)
- **PR/Issue**：#12982

## 总体目的

Iceberg 表格式 v3 引入了行级血缘（row lineage）能力，使每一行数据都能获得一个稳定、唯一的行标识 `_row_id`，并配合 `_last_updated_sequence_number` 反映该行最后被更新的序列号，从而支持跨快照的行级追踪与增量更新语义。本提交在 `format/spec.md` 的 v3 变更摘要（与"Row-level delete changes"并列）中新增"Row lineage changes"小节，正式把 row lineage 的写入端与读取端规则写入格式规范。

这一改动紧接 #12986（提交 2092，移除了 `EnableRowLineageUpdate` 这一 REST 更新动作），表明 row lineage 不再通过单独的 REST update 启用，而是作为 v3 表的内在能力，由 metadata 上的 `next_row_id` 与 manifest/data file 上的 `first_row_id` 共同驱动。规范化的目的是让所有 Iceberg 实现方（Java、PyIceberg、其它引擎集成）在读写 v3 表时对 row lineage 行为有一致理解。

## 如何达成设计目的

通过在 spec.md 的 v3 摘要中新增 20 行 markdown 列表条目，分四个维度描述规则：
1. **表级 `next-row-id` 维护**：写入者在创建新快照时必须读取并更新表 metadata 的 `next_row_id`，将其作为新快照的 `first-row-id`；升级到 v3 时初始化为 0；提交时至少按本快照新增行 id 数量自增，推荐按所有 manifest 的 `added_rows_count + existing_rows_count` 之和自增。
2. **manifest 级 `first_row_id` 分配**：写入 manifest list 时为每个新 manifest 分配 `first_row_id`，至少按 manifest 内新增行 id 数自增，推荐按 manifest 的 `added_rows_count + existing_rows_count` 自增。
3. **data file 写入/读取规则**：写入新 data file 时其 `first_row_id` 写空，留待读取时按所属 manifest 的 `first_row_id` 推算；写入已存在的 data file 到新 manifest 时，必须把已有的 `first_row_id` 写入 manifest。读取时：若 manifest 的 `first_row_id` 非空，则为缺失/null 的 data file 分配 `first_row_id`（按 `record_count` 递增）；若 data file 的 `first_row_id` 非空，则用 `first_row_id + _pos` 填充 null/缺失的 `_row_id`，并把缺失的 `_last_updated_sequence_number` 填为 data file 的 `data_sequence_number`，非 null 值原样读取；若 data file 的 `first_row_id` 为空，则 `_row_id` 与 `_last_updated_sequence_number` 输出 null。
4. **行级写入规则**：把已有行写入新 data file 时，若其 `_row_id`、`_last_updated_sequence_number` 非 null，必须一并写入。

## 修改详情

### `format/spec.md` (修改, +20/-0 lines)

**修改目的**：在 v3 规范摘要中正式记录 row lineage 的写入与读取规则。

**工作逻辑**：
在"Row-level delete changes"小节之后、"Encryption changes"小节之前，新增"Row lineage changes"小节，包含上述四类规则要点。所有条目均为规范说明文本，无代码逻辑，但明确了写入者与读取者对 `next-row-id`、`first_row_id`、`_row_id`、`_last_updated_sequence_number` 的契约，是后续各语言实现 row lineage 的权威依据。

## 总结

本次提交是 Iceberg v3 格式规范的重要补充，正式把 row lineage 的表级、manifest 级、data file 级、行级规则写入 `format/spec.md`。它定义了 `next_row_id` / `first_row_id` 的分配与递增规则，以及读取端如何据此填充 `_row_id` 与 `_last_updated_sequence_number`，使 v3 表具备稳定的行级标识能力。与紧接其前的 #12986（移除 `EnableRowLineageUpdate` REST 更新动作）配套，确立了 row lineage 作为 v3 内在能力而非独立开关的设计方向。
