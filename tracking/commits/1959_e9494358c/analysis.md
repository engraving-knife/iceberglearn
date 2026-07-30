# 提交 1959：Spec: update to reflect lineage is required (#12580)

## 提交信息

- **序号**：1959 / 4088
- **哈希**：e9494358cdfbf0f5ca135be19f5e53173b75f292
- **短哈希**：e9494358c
- **日期**：2025-04-03 15:55:50 -0600
- **作者**：Daniel Weeks
- **提交说明**：Spec: update to reflect lineage is required (#12580)
- **PR/Issue**：#12580

## 总体目的

本提交更新 Iceberg 规范文档 `format/spec.md`，以反映 row lineage（行血缘）在 v3 表中是"必需的"（required）而非"可选启用"的设计决策。

此前规范把行血缘描述为可通过在表 metadata 中设置 `row-lineage: true` 来启用的可选特性，并在多处用"when row lineage is enabled / when not enabled"的条件式表述。随着规范演进，行血缘在 v3 表中被定义为必需行为，因此需要：

1. 删除 `row-lineage` 这个表 metadata 布尔字段（不再需要开关）。
2. 把所有"可选启用"的表述改为"必需"的表述，删除"未启用时"的分支说明。
3. 把 snapshot 的 `first-row-id` 与 `added-rows` 字段从 optional 改为 required（v3）。
4. 调整措辞：writers 从"required to write"改为"should write"，并新增"engines may model operations as deleting/inserting rows or as modifications"的说明。
5. 把"Enabling Row Lineage for Non-empty Tables"重命名为"Row Lineage for Upgraded Tables"，描述升级到 v3 的行为。

## 如何达成设计目的

通过直接编辑 `format/spec.md` 的多个章节完成规范文本更新：metadata columns 表的描述、Row Lineage 章节、Row lineage assignment、Row Lineage example、Snapshot 字段表、Table metadata 字段表（移除 `row-lineage` 字段），以及 First Row ID Inheritance / Snapshot Row IDs / First Row ID Assignment 等章节中删除"未启用时"的条件分支。

## 修改详情

### `format/spec.md` (修改, +14/-22 lines)

**修改目的**：将行血缘从可选启用改为 v3 必需，并移除 `row-lineage` 开关字段。

**工作逻辑**：
- **metadata columns 表**：`_row_id` 与 `_last_updated_sequence_number` 的描述从 "assigned when row-lineage is enabled" 改为 "assigned for row lineage"。
- **Row Lineage 章节**：把 "an Iceberg table can track ... Row lineage is enabled by setting the field `row-lineage` to true" 改为 "an Iceberg table must track row lineage fields for all newly created rows. Engines must maintain the `next-row-id` table field..."。简化 `_row_id`/`_last_updated_sequence_number` 的赋值说明（移除"explicitly written when copied"措辞）。
- **Row lineage assignment**：删除"Row lineage fields are written when row lineage is enabled. When not enabled ... must not be written"这一段。把 "writers are required to write" 改为 "writers should write"。新增 "Engines may model operations as deleting/inserting rows or as modifications to rows that preserve row ids."
- **Row Lineage example**：移除 "when row lineage is enabled" 措辞。
- **Enabling → Upgraded**：章节标题改为 "Row Lineage for Upgraded Tables"，描述改为升级到 v3 的行为，"should propagate null" 改为 "must propagate null"。
- **First Row ID Inheritance / Snapshot Row IDs / First Row ID Assignment**：删除各处 "When row lineage is not enabled / is used when row lineage is enabled. When not enabled ..." 的条件分支段落。
- **Snapshot 字段表**：`first-row-id` 与 `added-rows` 在 v3 列由 _optional_ 改为 _required_；移除 `added-rows` 描述中 "Required if Row Lineage is enabled" 的措辞。
- **Table metadata 字段表**：移除 `row-lineage` 布尔字段行。

## 总结

本提交更新 Iceberg 规范，将行血缘（row lineage）从可通过 `row-lineage` 字段可选启用的特性，改为 v3 表的必需行为。具体移除了 `row-lineage` 表 metadata 字段，把 `first-row-id`/`added-rows` 改为 v3 必需，并清理了所有"启用/未启用"的条件式表述，统一为"必需"语义。
