# 提交 1657 53d2aca91 分析

## 提交信息
- 哈希：53d2aca916b8129baeeb7888369af1cea32acc9c
- 日期：2025-01-29 12:49:03 -0600
- 作者：Kevin Liu
- 消息：Docs: Fix spacing in How To Release Vote Section (#12123)

## 总体目的

本提交修复 Iceberg 网站"How To Release"文档中投票（Vote）部分的 Markdown 排版问题。该文档描述了 Apache Iceberg 发布流程中投票环节的格式和规范，包含投票选项的模板（+1 / +0 / -1）。

在 Markdown 渲染中，连续的引用行（`>` 开头）会被合并为同一段落，而空行则需要用 `>`（空引用行）来分隔段落。原文档中三个投票选项的复选框列表项之间缺少空引用行分隔，导致渲染时它们被合并为一段，显示为拥挤的连续文本，影响可读性。

本次修复在每个列表项之间插入空引用行（`>`），使每个投票选项渲染为独立的段落，呈现更清晰的视觉间距。

## 如何达成设计目的

通过在 Markdown 引用块（blockquote）的列表项之间插入空的引用行 `>`，强制 Markdown 渲染器将每个列表项作为独立段落处理。这是 GitHub Flavored Markdown 中分隔引用块内段落的标准做法。

### 修改详情

#### site/docs/how-to-release.md
在投票模板部分，将原本紧邻的三行：
```
> [ ] +1 Release this as Apache Iceberg {{ icebergVersion }}
[ ] +0
[ ] -1 Do not release this because...
```
修改为：
```
> [ ] +1 Release this as Apache Iceberg {{ icebergVersion }}
>
> [ ] +0
>
> [ ] -1 Do not release this because...
```
具体改动：
- 在 `+1` 选项和 `+0` 选项之间插入空引用行 `>`。
- 在 `+0` 选项和 `-1` 选项之间插入空引用行 `>`。
- 同时为 `+0` 和 `-1` 两行补上 `>` 前缀（原文本是 `[ ] +0` 和 `[ ] -1 Do not release this because...`，缺少 `>` 前缀导致它们不在引用块内）。

## 小结

这是一个纯文档排版修复提交，影响范围仅限网站文档的渲染效果，不影响任何代码或功能。

回迁到 1.4.x 注意事项：
- 文档修复对任何分支都是安全的，可随时回迁。
- 若 1.4.x 的发布文档存在同样排版问题，建议回迁以保持文档一致性。
- 回迁仅需修改一处 Markdown 文件，无冲突风险。
