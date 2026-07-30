# 提交 2959：Docs: fix rendering issues in encryption doc (#14756)

## 提交信息

- **序号**：2959 / 4088
- **哈希**：ba983470e7caf03290933402cdc6f15d883e1073
- **短哈希**：ba983470e
- **日期**：2025-12-04
- **作者**：Huaxin Gao
- **提交说明**：Docs: fix rendering issues in encryption doc (#14756)
- **PR/Issue**：#14756

## 总体目的

Iceberg 的加密文档 `docs/docs/encryption.md` 存在多处 Markdown 渲染问题，导致文档在实际展示时格式不正确或链接失效。这些问题包括：列表前缺少空行导致有序列表项无法正确渲染、代码块中的占位符使用了 Hugo 模板语法 `{{ }}` 而非标准占位符 `< >`、嵌套列表缩进不规范导致子项未正确嵌套在父列表项下、以及多处文档内相对链接路径多了一层 `format/` 目录导致 404。本提交逐一修复这些渲染问题，使加密文档在各种 Markdown 渲染器（包括 Iceberg 官网使用的 Hugo/Docusaurus）下都能正确显示。

## 如何达成设计目的

改动全部集中在 `docs/docs/encryption.md` 一个文件中，分四类修复：列表前补空行与冒号以正确触发列表渲染；代码块占位符从 `{{ }}` 改为 `< >`；嵌套子列表统一使用 `-` 并增加 4 空格缩进以正确嵌套；修正 4 处相对链接路径，去掉多余的 `format/` 段。

## 修改详情

### `docs/docs/encryption.md` (+14/-14 lines)

**修改目的**：修复加密文档的 Markdown 渲染与链接问题。

**工作逻辑**：
改动分为以下几组：

1. **列表渲染修复**：在 "Two parameters are required to activate encryption of a table" 后添加冒号 `:` 和空行。Markdown 规范要求列表前必须有空行才能正确渲染为列表项，否则部分渲染器会将其视为普通段落文本。同理，在约束条件第 2 条的引导句后也添加了冒号，使其后的子列表能正确嵌套。

2. **占位符格式修复**：将 SQL 代码块中的 `'{{ master key id }}'` 改为 `'<master-key-id>'`，将 shell 代码块中的 `{{ /path/to/file }}` 改为 `<path/to/file>`。`{{ }}` 是 Hugo/Go 模板语法，在静态网站生成时会被当作模板变量解析而导致渲染错误或内容消失；改用尖括号占位符既符合通用约定又避免模板解析冲突。

3. **嵌套列表缩进修复**：将约束条件第 2 条下的三个子项从 `*` 无缩进改为 `-` 配合 4 空格缩进，使其正确呈现为第 2 条的子列表。同时将最后一个子项的换行续行（`(the checksums must be kept...`）对齐到子项内容位置，保证渲染时段落不断裂。

4. **相对链接路径修复**：将 4 处文档内链接路径中的 `../../format/spec.md` 改为 `../../spec.md`，`../../format/gcm-stream-spec.md` 改为 `../../gcm-stream-spec.md`。由于 `encryption.md` 位于 `docs/docs/` 目录下，规范文件实际在 `docs/format/` 下，相对路径应为 `../format/spec.md` 而非 `../../format/spec.md`——但此处统一去掉了 `format/` 段，说明文档站点结构下 spec.md 与 gcm-stream-spec.md 位于上级目录。修正后链接不再 404，读者可正常跳转到 Iceberg 规范中的 data-file-fields、manifest-lists、snapshots、table-metadata-fields 等锚点。

## 总结

本提交是对加密文档的纯格式修复，解决了列表不渲染、模板占位符被误解析、嵌套列表缩进错误和相对链接失效四类问题，提升了文档的可读性与可用性。虽然不涉及功能代码改动，但对用户正确理解和使用 Iceberg 加密功能具有实际指导价值。
