# 提交 3343：Docs: Improve json readability in view spec (#15505)

## 提交信息

- **序号**：3343 / 4088
- **哈希**：7064b09797cc7448c7b8ab1cb8430608ddf8bfb2
- **短哈希**：7064b0979
- **日期**：2026-03-04 11:11:12 +0100
- **作者**：Yuya Ebihara
- **提交说明**：Docs: Improve json readability in view spec (#15505)
- **PR/Issue**：#15505

## 总体目的

本提交通过为 view 规范文档中的 JSON 示例代码块添加语言标识，提升其在 GitHub 与文档网站上的语法高亮与可读性。

背景是：`format/view-spec.md` 中包含两段 view 元数据文件的 JSON 示例（分别对应版本 00001 与 00002 的 metadata.json），它们展示了 view 的完整元数据结构（`view-uuid`、`format-version`、`location`、`current-version-id`、`versions`、`version-log`、`properties`、`schemas` 等字段）。这两个代码块此前使用的是无语言标识的普通围栏（` ``` `），在 GitHub 和 MkDocs 渲染时只会以等宽纯文本显示，没有 JSON 语法高亮——键名、字符串、数字、嵌套结构无法通过颜色区分，对于这种层级较深、字段较多的 JSON 示例，阅读体验不佳。将围栏改为 ` ```json ` 后，渲染器会启用 JSON 语法高亮，键名、字符串值、数字等以不同颜色区分，括号匹配与缩进层级也更清晰，便于读者理解 view 元数据结构。

## 如何达成设计目的

直接将两处 JSON 代码块的起始围栏从 ` ``` ` 改为 ` ```json `，告知 Markdown 渲染器按 JSON 语法进行高亮。改动不涉及任何 JSON 内容本身，仅是代码块语言标注。

## 修改详情

### `format/view-spec.md` (+2/-2 lines)

**修改目的**：为 view 元数据 JSON 示例添加 JSON 语法高亮。

**工作逻辑**：
两处改动完全相同，分别位于文档中展示版本 00001 与版本 00002 的 view metadata.json 示例处。每处将代码块起始围栏 ` ``` ` 改为 ` ```json `。第一处紧跟在路径 `s3://bucket/warehouse/default.db/event_agg/metadata/00001-(uuid).metadata.json` 之后，第二处紧跟在 `00002-(uuid).metadata.json` 路径之后。结尾围栏保持 ` ``` ` 不变。这样 GitHub 与支持 Pygments/Prism 的 MkDocs 站点在渲染时会按 JSON 规则对键名（带引号）、字符串值、数字、布尔值等着色，显著提升两段较长 JSON 示例的可读性。

## 总结

本提交将 view 规范文档中两段 view 元数据 JSON 示例的代码块围栏从无语言标识改为 ` ```json `，启用 JSON 语法高亮，在不改变任何内容的前提下提升了文档在 GitHub 与网站上的可读性，方便读者理解 view 元数据的字段结构与层级关系。
