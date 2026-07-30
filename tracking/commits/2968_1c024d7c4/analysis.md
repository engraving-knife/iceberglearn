# 提交 2968：Update configuration.md (#14771)

## 提交信息

- **序号**：2968 / 4088
- **哈希**：1c024d7c4e66a0654118d30d04a0d9c0c5ab4de3
- **短哈希**：1c024d7c4
- **日期**：2025-12-05
- **作者**：Kurtis Wright
- **提交说明**：Update configuration.md (#14771)
- **PR/Issue**：#14771

## 总体目的

`docs/docs/configuration.md` 中 `format-version` 表项描述了"表格式版本由 Spec 定义"，并带有一个指向规范文档 `#format-versioning` 锚点的相对链接。该链接原本写作 `../../spec.md#format-versioning`，从 `docs/docs/configuration.md` 出发解析到仓库根目录的 `spec.md`，但仓库根目录并不存在 `spec.md`（规范文档实际位于 `format/spec.md`），因此该链接在 GitHub 浏览或基于仓库相对路径渲染时是一个失效链接。本提交的目的是修复这个失效链接，将其指向实际存在的规范文档路径 `../../format/spec.md#format-versioning`，使读者能正确跳转到格式版本说明。提交说明明确写道 "Fixes spec.md link in format-version table."。

## 如何达成设计目的

改动只涉及一行 Markdown 链接的路径部分：将 `../../spec.md` 改为 `../../format/spec.md`，锚点 `#format-versioning` 保持不变。这与仓库中实际的 `format/spec.md` 文件位置一致。

## 修改详情

### `docs/docs/configuration.md` (+1/-1 lines)

**修改目的**：修复 `format-version` 表项中指向规范文档的失效相对链接。

**工作逻辑**：
在 `format-version` 行的描述列中，链接由 `[Spec](../../spec.md#format-versioning)` 改为 `[Spec](../../format/spec.md#format-versioning)`。从该文件所在目录 `docs/docs/` 出发，`../../format/spec.md` 解析到仓库根的 `format/spec.md`，正是 Iceberg 规范文档的真实位置，从而修正了原先指向不存在文件的问题。其余文本（"Defaults to 2 since version 1.4.0." 等）不变。

## 总结

本提交是一个一行级别的文档修复，把 `configuration.md` 中 `format-version` 表项指向规范文档的相对链接从失效的 `../../spec.md` 修正为实际存在的 `../../format/spec.md`，提升了文档链接的可达性。需注意该改动在随后一个提交（2969）中被回退，因为该路径在站点渲染上下文下并非正确解。
