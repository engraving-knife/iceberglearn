# 提交 2873：Spec: minor clarification, Parquet int type is int32, long is int64 (#14546)

## 提交信息

- **序号**：2873 / 4088
- **哈希**：a6c4e6aef06103db38be064534b7b0be09b3ebca
- **短哈希**：a6c4e6aef
- **日期**：2025-11-12 13:51:40 -0800
- **作者**：Nándor Kollár
- **提交说明**：Spec: minor clarification, Parquet int type is int32, long is int64 (#14546)
- **PR/Issue**：#14546

## 总体目的

Iceberg 规范文档 `format/spec.md` 中定义了 Iceberg 类型到 Parquet 物理类型的映射表。在此次修改之前，`int` 类型的 Parquet 物理类型被标记为 `int`，`long` 类型被标记为 `long`。然而，Parquet 格式规范中实际的物理类型名称是 `int32` 和 `int64`，而非 `int` 和 `long`。使用 `int`/`long` 这一命名容易造成歧义，因为它们更像 Java 类型名而非 Parquet 物理类型名。

此修改将规范文档中的 `int` → `int32`、`long` → `int64`，使映射表与 Parquet 格式规范中的标准物理类型名称完全一致，消除歧义。这是纯粹的文档澄清修改，不涉及任何代码变更。

## 如何达成设计目的

修改仅涉及 `format/spec.md` 文件中的类型映射表。将 `int` 类型的 Parquet physical type 从 `int` 改为 `int32`，`long` 类型的从 `long` 改为 `int64`。同时由于表格列宽变化，对整个表格的对齐格式做了相应调整。

## 修改详情

### `format/spec.md` (+25/-25 lines)

**修改目的**：澄清 Iceberg `int` 和 `long` 类型对应的 Parquet 物理类型名称。

**工作逻辑**：在类型映射表中：
- `**int**` 行的 Parquet physical type 从 `int` 改为 `int32`
- `**long**` 行的 Parquet physical type 从 `long` 改为 `int64`

其余行的内容不变，但由于表格列宽因这两行内容变长而需要重新对齐，整个表格的格式都做了调整（25 行删除、25 行新增，实际内容变化仅 2 处）。

## 总结

该提交是规范文档的小型澄清，将 Iceberg `int`/`long` 类型对应的 Parquet 物理类型名称从容易混淆的 `int`/`long` 更正为 Parquet 规范中标准的 `int32`/`int64`，与表中其他使用 `int32`/`int64` 的行（如 `date`、`time`、`timestamp` 等）保持一致，消除了命名歧义。
