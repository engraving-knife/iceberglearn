# 提交 1611 445687d96 分析

## 提交信息
- 哈希：445687d96dc3988f4ee87b3e5c9765317c44aa44
- 日期：2025-01-21 09:04:03 -0600
- 作者：Fokko Driesprong
- 消息：Spec: Clarify `next-row-id` (#12018)

## 总体目的

本次提交对 Iceberg 表格式规范文档 `format/spec.md` 中表元数据字段 `next-row-id` 的描述进行措辞澄清，将其类型从模糊的“A value”明确为“A `long`”，使规范显式声明 `next-row-id` 是一个 64 位长整型。

`next-row-id` 是 Iceberg v3 规范引入的 Row Lineage（行血缘）机制中的表级元数据字段。当表的 `row-lineage` 设置为 true 时，引擎需要为每行数据分配唯一的 `_row_id`（同为 `long` 类型），而 `next-row-id` 记录的是“高于所有已分配 row ID 的下一个可用值”，下一个快照的 `first-row-id` 即取自该值。因此 `next-row-id` 必须能容纳与 `_row_id` 同等范围的数值（64 位）。

原描述“A value higher than all assigned row IDs”未说明类型，可能让实现者误以为可以是任意数值类型（如 32 位 int）。在行数可能超过 2^31 的大表场景下，若实现者按 int 处理会导致溢出。本次澄清将类型显式化为 `long`，与规范中其它相关字段（数据列 `_row_id` 为 `long`、manifest 的 `first_row_id` 为 `long`、snapshot 的 `first-row-id`）保持一致，消除实现歧义。

这是一处纯规范文档的措辞精确化，不改变任何字段的语义或格式版本，只是把原本隐含的类型约束显式化。

## 如何达成设计目的

设计思路是在表元数据字段表格中，给 `next-row-id` 的描述加上明确的类型标注 `long`，与规范中其它带类型的字段描述风格对齐（例如数据列定义中 `_row_id` 标注为 `long`、manifest 字段 `520 first_row_id` 标注为 `long`）。这种“在描述中以反引号标注类型名”是 Iceberg spec 文档的常见写法。

### 修改详情

#### format/spec.md

修改了表元数据字段表格（Table metadata consists of the following fields）中 `next-row-id` 一行的描述：

```diff
- |            |            | _optional_ | **`next-row-id`**           | A value higher than all assigned row IDs; the next snapshot's `first-row-id`. See [Row Lineage](#row-lineage). |
+ |            |            | _optional_ | **`next-row-id`**           | A `long` higher than all assigned row IDs; the next snapshot's `first-row-id`. See [Row Lineage](#row-lineage). |
```

即把开头的 `A value` 改为 `A \`long\``。其余描述（“高于所有已分配 row IDs；下一个快照的 `first-row-id`；参见 Row Lineage”）保持不变。

该字段在规范中的工作逻辑（未由本提交改动，仅作背景说明）：当启用 row-lineage 时，每个新快照的 `first-row-id` 取自表当前 `next-row-id`；快照提交后，表的 `next-row-id` 更新为“原 `next-row-id` + 本快照所有新增数据文件的 `record_count` 之和”，从而保证 `next-row-id` 始终高于表中任何已分配的 row ID。本提交只是把承载这一机制的数值类型在文档中显式标注为 `long`，与 `_row_id`（long）的取值范围对齐。

## 小结

本次提交成效在于提升规范文档的精确性：显式声明 `next-row-id` 为 `long` 类型，与 Row Lineage 体系中其它相关字段（`_row_id`、`first_row_id`、`first-row-id`）的类型保持一致，避免实现者因类型不明确而误用 32 位整数导致大表溢出。影响范围仅限规范文档一行措辞，不改变格式语义、不引入新版本、不涉及任何代码。

回迁到 1.4.x 分支的注意事项：
- 该改动为纯规范文档措辞澄清，回迁无风险，可直接应用。
- 前提：1.4.x 分支的 `format/spec.md` 需已包含 `next-row-id` 字段定义（即 Row Lineage / v3 规范相关内容）。若 1.4.x 的规范尚未引入 Row Lineage 章节，则该字段不存在，本提交不适用、无需回迁。
- 回迁时确认 1.4.x 中 `next-row-id` 描述的当前措辞；若已为 `A \`long\`` 或更精确表述则跳过，否则应用本提交的澄清。
- 由于是规范文档的精确化，回迁不会影响 1.4.x 的运行时行为，仅提升文档质量。
