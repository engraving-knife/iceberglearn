# 提交 2364：Docs: Fix indentation of 1.9.2 release note (#13583)

## 提交信息

- **序号**：2364 / 4088
- **哈希**：cea1093592e77ba4f9027b13a8dff5f7e1cfc6b5
- **短哈希**：cea109359
- **日期**：2025-07-17 09:58:30 +0200
- **作者**：Yuya Ebihara
- **提交说明**：Docs: Fix indentation of 1.9.2 release note (#13583)
- **PR/Issue**：#13583

## 总体目的

这个提交修复了提交 2360 引入的 1.9.2 发布说明中的缩进问题。在 Markdown 列表中，嵌套列表项的缩进需要与父列表层级正确对齐，否则会导致渲染异常。

提交 2360 在 `releases.md` 中添加 1.9.2 发布说明时，Core 修复条目的缩进为 2 个空格（`  - Core: ...`），但根据上下文应为 4 个空格（`    - Stop retrying...`），以正确嵌套在 `* Core` 下作为二级列表项。2 空格缩进在某些 Markdown 渲染器中可能无法正确识别为 `* Core` 的子项。

## 如何达成设计目的

将 1.9.2 发布说明中 Core 条目的缩进从 2 空格修正为 4 空格，并简化条目文本。

## 修改详情

### `site/docs/releases.md` (+1/-1 lines)

**修改目的**：修正 1.9.2 发布说明的列表缩进。

**工作逻辑**：将 `- Core: Stop retrying on 502 / 504 to avoid table corruption due to self conflicts [...]`（2 空格缩进）改为 `    - Stop retrying on 502 / 504 to avoid table corruption due to self conflicts [...]`（4 空格缩进），使其正确嵌套在 `* Core` 下列为子项。同时移除了条目前缀中的 "Core:" 重复文字（因父级已有 `* Core` 标题）。

## 总结

该提交修复了 1.9.2 发布说明中的 Markdown 列表缩进问题，确保条目正确嵌套在 Core 分类下。纯文档格式修正，无功能影响。
