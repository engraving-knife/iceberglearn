# 提交 3175：Docs: Fix bullet list formatting in fileio usage section (#15106)

## 提交信息

- **序号**：3175 / 4088
- **哈希**：83653ba9ad1de44fd389ea6468f2886d18f475e8
- **短哈希**：83653ba9a
- **日期**：2026-01-29
- **作者**：Debjani Banerjee
- **提交说明**：Docs: Fix bullet list formatting in fileio usage section (#15106)
- **PR/Issue**：#15106

## 总体目的

这是文档格式修复提交。Iceberg 的 `docs/docs/fileio.md` 文档在"FileIO 使用"章节中，先有一段说明文字介绍 Iceberg 支持多种 FileIO 实现，随后紧跟一个无序列表列举三种存储实现（Amazon S3、Google Cloud Storage、Object Service Storage）。但在 Markdown 渲染时，由于说明段落与列表之间缺少空行，许多 Markdown 解析器（包括 Hugo/Docusaurus 等静态站点生成器）不会将该列表识别为独立的 `<ul>` 列表元素，而是将其渲染为段落内的普通文本行，导致项目符号丢失、排版错乱，影响文档可读性。

本次提交由 Debjani Banerjee 通过 PR #15106 修复此问题，在说明段落与列表之间插入一个空行，使列表被正确解析为 Markdown 无序列表，恢复应有的项目符号渲染。这是一个典型的 Markdown 格式细节修复，背景是 Markdown 规范要求列表前必须有空行与前文分隔（CommonMark 规范），缺少空行时行为因解析器而异。

该修复的实际指导价值在于：使 FileIO 文档章节在 Iceberg 官网上正确显示存储实现列表，让用户能清晰识别 Iceberg 开箱支持的三种 FileIO 实现。

## 如何达成设计目的

在 `fileio.md` 的说明段落（"Iceberg comes with a set of FileIO implementations for popular storage providers."）与无序列表（"- Amazon S3" 等）之间插入一个空行，满足 Markdown 列表解析的前置分隔要求。

## 修改详情

### `docs/docs/fileio.md` (+1/-0 lines)

**修改目的**：修复说明段落与 FileIO 实现列表之间的 Markdown 格式问题。

**工作逻辑**：
在 `...Iceberg comes with a set of FileIO implementations for popular storage providers.` 行之后、`- Amazon S3` 行之前，新增一个空行。改动前后对比：
- 改动前：段落紧接列表无空行，列表可能被渲染为段落文本。
- 改动后：段落与列表间有空行，列表被正确解析为 `<ul>`，显示项目符号。

仅此一行空行的新增，无内容变更。

## 总结

本次提交修复了 `fileio.md` 文档中 FileIO 实现列表因缺少前置空行而无法正确渲染为无序列表的格式问题。通过插入一个空行，使列表在 Markdown 解析器中被正确识别，恢复了项目符号显示，提升了文档的可读性与专业度。属于低风险、高收益的文档质量改进。
