# 提交 2624：Docs: Fix style of row-level deletes in spec

## 提交信息

- **序号**：2624 / 4088
- **哈希**：4e154bd4efc2bb4ef0a8a8d14e436e7737ed582a
- **短哈希**：4e154bd4e
- **日期**：2025-09-11 14:46:09 -0500
- **作者**：JeonDaehong
- **提交说明**：Docs: Fix style of row-level deletes in spec
- **PR/Issue**：无（直接提交到 main）

## 总体目的

Iceberg 规范文档 `format/spec.md` 中描述"行级删除（row-level deletes）"的两种类型时，markdown 列表项的格式与 spec 文档其他部分的风格不一致。原先使用斜体 `_Position deletes_` 作为列表项标题，且列表项之间无空行，渲染效果不够清晰，与 spec 中其他列表的样式（加粗标题 + 空行分隔）不统一。

本提交修正该段落的样式：将斜体改为加粗、使用 `--` 分隔标题与说明、列表项之间增加空行，使行级删除的描述与规范文档整体风格保持一致，提升可读性。这是纯文档样式修复，不改变规范语义。

## 如何达成设计目的

修改 `format/spec.md` 中"two types of row-level deletes"段落的 markdown 格式：将 `* _Position deletes_ mark...` 改为 `* **Position deletes** -- Mark...`（斜体改加粗、首字母大写动词、用 `--` 分隔），并在两个列表项之间增加空行。Equality deletes 同理处理。同时将内联代码 `id = 5` 改为普通文本（去掉反引号）。

## 修改详情

### `format/spec.md` (+5/-3 lines)

**修改目的**：统一行级删除描述的 markdown 样式。

**工作逻辑**：
- 引导句末冒号后不变。
- 第一项：`* _Position deletes_ mark a row deleted...` 改为 `* **Position deletes** -- Mark a row deleted...`（`_..._` 改 `**...**`，`mark` 改 `Mark`，加 `--` 分隔符）。
- 两项之间增加空行。
- 第二项：`* _Equality deletes_ mark a row deleted by one or more column values, like \`id = 5\`.` 改为 `* **Equality deletes** -- Mark a row deleted by one or more column values, like id = 5.`（同样样式调整，并去掉 `id = 5` 的反引号）。

这些改动使该段落与 spec 文档中其他列表项（加粗标题 + `--` 分隔 + 空行）的样式一致。

## 总结

这是一次 Iceberg 规范文档的样式修复，将行级删除（position deletes / equality deletes）描述的 markdown 格式统一为加粗标题 + `--` 分隔 + 空行的风格，与 spec 其他部分一致。纯文档样式改动，不影响规范语义。
