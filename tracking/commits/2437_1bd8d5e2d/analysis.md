# 提交 2437：Spec: Fix wrong type for snapshot-id in table statistics (#13513)

## 提交信息

- **序号**：2437 / 4088
- **哈希**：1bd8d5e2de56d05180030b856ce2c50c66ef1f13
- **短哈希**：1bd8d5e2d
- **日期**：2025-07-31 07:36:33 +0200
- **作者**：Smith Cruise
- **提交说明**：Spec: Fix wrong type for snapshot-id in table statistics (#13513)
- **PR/Issue**：#13513

## 总体目的

本提交修复了 Iceberg 规范文档（spec.md）中表统计（table statistics）元数据的一个类型错误：`snapshot-id` 字段的类型从 `string` 修正为 `long`。

在 Iceberg 规范中，`snapshot-id` 在其他所有出现的地方（如 snapshot 元数据、manifest list 等）都是 `long` 类型，因为 snapshot ID 本质上是一个 64 位整数。但在表统计文件元数据的字段定义中，`snapshot-id` 被错误地标注为 `string` 类型，这与规范的其余部分不一致，也可能误导实现者。

这个文档错误如果不修正，可能导致统计文件的实现者按 string 类型处理 snapshot-id，造成与表其他部分的不兼容。

## 如何达成设计目的

直接在 `format/spec.md` 的统计文件元数据字段表中将 `snapshot-id` 的类型从 `string` 改为 `long`。

## 修改详情

### `format/spec.md` (+1/-1 lines)

**修改目的**：修正 `snapshot-id` 字段类型。

**工作逻辑**：在 statistics 表元数据字段的定义表中，将 `snapshot-id` 的 Type 列从 `string` 修改为 `long`，与规范中其他位置（如 snapshot 的 `snapshot-id` 字段）保持一致。该字段表示统计文件关联的快照 ID，是 64 位整数。

## 总结

本提交是一个规范文档的 bug 修复，将表统计元数据中 `snapshot-id` 的类型从错误的 `string` 修正为 `long`，与 Iceberg 规范中其他位置的 `snapshot-id` 类型定义保持一致。改动仅一行，但避免了实现者因文档错误而产生类型处理不一致的问题。
