# 提交 1718：spec: Remove `source-ids` for `V{1,2}` tables (#12161)

## 提交信息

- **序号**：1718 / 4088
- **哈希**：1153c303b1bb6c4843df4f8f713f45ab4812074e
- **短哈希**：1153c303b
- **日期**：2025-02-13 09:28:17 +0100
- **作者**：Fokko Driesprong
- **提交说明**：spec: Remove `source-ids` for `V{1,2}` tables (#12161)
- **PR/Issue**：#12161

## 总体目的

修正 Iceberg 规范中 `source-ids` 字段在 v1/v2 表中的使用规则。`source-ids` 是多参数变换（multi-arg transform）使用的字段，用于引用多个源列。此前规范允许在 v1 和 v2 表中写入 `source-ids` 字段，这可能导致不兼容问题。

作者在为 PyIceberg 添加 `source-ids` 支持时发现，Java 实现也尚未完全支持此功能。更关键的是，`source-ids` 被回溯到了 v1 和 v2 表中，这会让不识别 `source-ids` 的现有 v2 实现产生兼容性问题。由于多参数变换是 v3 才引入的特性，规范应明确限制 `source-ids` 仅在 v3 表中使用，v1/v2 表不应写入此字段。

参见相关讨论：PR #9661 和 Apache 邮件列表讨论。

## 如何达成设计目的

通过修改 `format/spec.md` 中分区字段和排序字段的 JSON 表示规范，将 `source-ids` 在 v1/v2 中从 "optional" 改为不适用（留空），并删除关于在 v1/v2 元数据中写入 `source-ids` 的指导说明。

## 修改详情

### `format/spec.md`（修改, +3/-9 lines）

**修改目的**：限制 `source-ids` 字段仅在 v3 表中使用。

**工作逻辑**：

1. **分区字段 JSON 表示表（第 1416 行附近）**：将 `source-ids` 行的 V1 和 V2 列从 "optional" 改为留空，表示 v1/v2 表不应使用此字段。V3 列保持 "required"。

2. **分区字段说明（第 1437 行附近）**：删除了 "In v1 and v2 metadata, writers must always write `source-id`; for multi-arg transforms, writers must produce `source-ids` and set `source-id` to the first ID from the field ID list." 这段说明，仅保留 v3 的规则。即 v1/v2 表不再需要写入 `source-ids`。

3. **排序字段说明（第 1460 行附近）**：同样删除了关于 v1/v2 元数据中写入 `source-ids` 的说明。

4. **v1/v2 元数据读取/写入规则（第 1614 行附近）**：删除了 "Writing v1 or v2 metadata" 整个小节，该小节说明了在 v1/v2 元数据中如何写入 `source-id` 和 `source-ids`。保留读取规则（读取 v1/v2 时 `source-ids` 默认为 `source-id` 的单元素列表）。

## 小结

- **成效**：明确了 `source-ids` 仅在 v3 表中使用，避免了在 v1/v2 表中引入 `source-ids` 可能导致的兼容性问题。现有 v2 实现无需处理 `source-ids` 字段。
- **影响范围**：规范层面的变更，影响多参数变换在 v1/v2 表中的使用规则。对已实现的 v3 多参数变换无影响，对 v1/v2 表的实现是保护性的（减少了需要处理的字段）。
- **回迁到 1.4.x 的注意事项**：取决于 1.4.x 分支的规范版本是否已包含 `source-ids` 的定义。如果 1.4.x 的规范已允许在 v1/v2 中使用 `source-ids`，则建议回迁此修正以避免兼容性问题。如果 1.4.x 尚未引入 `source-ids` 概念，则无需回迁。需检查 1.4.x 分支的 spec.md 中相关内容。
