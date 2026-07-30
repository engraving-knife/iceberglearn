# 提交 1168：Docs: `field_id` in name serialisation spec should read `field-id` (#11135)

## 提交信息

- **序号**：1168 / 4088
- **哈希**：79fd977f67592a16579cff31478e7ea98ef126e4
- **短哈希**：79fd977f6
- **日期**：2024-09-19（Thu Sep 19 22:40:12 2024 +0200）
- **作者**：jonaswk <jonaswk@users.noreply.github.com>
- **提交说明**：Docs: `field_id` in name serialisation spec should read `field-id` (#11135)
- **PR/Issue**：#11135

## 总体目的

Iceberg 格式规范 `format/spec.md` 在「Name Mapping 序列化」一节用一张表格描述字段映射对象（field mapping object）的 JSON 表示。表格第二行列出了 `field-id` 这一 JSON 字段的含义与示例。但是该行左侧的「Field mapping field」列名错写成 `field_id`（下划线），与实际 JSON 字段名 `field-id`（连字符）不一致，导致规范自相矛盾：同一行左列写 `field_id`，右列示例又写 `"field-id": 4`。

本提交把表格左列的 `field_id` 修正为 `field-id`，使规范内部一致，并与 Iceberg REST/JSON 序列化惯例（连字符命名）保持统一。

## 如何达成设计目的

直接修改 `format/spec.md` 第 1249 行：把表格左列 `**`field_id`**` 改为 `**`field-id`**`，其他文字与示例不变。无代码改动。

## 修改详情

### `format/spec.md`

**修改目的**：修正 Name Mapping 序列化表格中 `field_id` 与示例 JSON `field-id` 的命名不一致。

**工作逻辑**：

修改前：

| Field mapping field | JSON representation | Example |
| --- | --- | --- |
| **`names`** | `JSON list of strings` | `["latitude", "lat"]` |
| **`field_id`** | `JSON int` | `1` |
| **`fields`** | `JSON field mappings (list of objects)` | `[{ ... "field-id": 4, ... }]` |

修改后：第二行左列改为 `**`field-id`**`，与示例 JSON 中的 `"field-id":` 字段名一致。

- Iceberg JSON 序列化一贯采用连字符命名（如 `metadata-location`、`table-uuid`、`field-id`），下划线命名是文档笔误。
- `field-id` 是 Name Mapping 中用于标识字段的全局唯一 ID，与 `names`（多个候选名）配对，用于在 schema 演化或字段重命名时把历史数据映射到当前字段。

## 小结

- **成效**：格式规范文档内部一致，避免规范读者误解 JSON 字段名。
- **影响范围**：仅 `format/spec.md` 一个文件，1 行 1 词级别的修改，无任何代码或运行时逻辑变更。
- **回迁到 1.4.x 的注意事项**：
  - 这是纯文档错误修正，对 1.4.x 运行时无任何影响。
  - 1.4.x 作为维护分支通常不会单独追平规范文档，**默认不需要回迁**。若 1.4.x 分支的 `spec.md` 同处也存在该笔误，可顺手回迁；回迁无任何风险。
  - 注意：规范文档（`format/spec.md`）是 Iceberg 跨版本契约的来源，1.4.x 的规范应反映 1.4.x 支持的格式版本；如果 1.4.x 分支的 spec 已经包含 Name Mapping 章节，则建议回迁此修正以保持规范准确性。
