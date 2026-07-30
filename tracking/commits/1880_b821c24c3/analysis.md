# 提交 1880：Infra: Update Bug report template for 1.7.2 (#12574)

## 提交信息

- **序号**：1880 / 4088
- **哈希**：b821c24c3bd04284c7c138e064d7f50dc1d2b9b6
- **短哈希**：b821c24c3
- **日期**：2025-03-19 16:40:10 +0100
- **作者**：Eduard Tudenhoefner
- **提交说明**：Infra: Update Bug report template for 1.7.2 (#12574)
- **PR/Issue**：#12574

## 总体目的

本提交在 GitHub Issue 的 Bug 报告模板中新增 1.7.2 版本选项，使用户在提交 bug 时能选择该版本。

背景：Iceberg 1.7.2 即将发布，bug 报告模板的版本下拉列表需要包含该版本，以便准确分类和复现问题。

## 如何达成设计目的

在 `.github/ISSUE_TEMPLATE/iceberg_bug_report.yml` 的版本选项列表中，在 "1.8.0" 之后、"1.7.1" 之前插入 "1.7.2"。

## 修改详情

### `.github/ISSUE_TEMPLATE/iceberg_bug_report.yml` (修改, +1/-0 lines)

**修改目的**：新增 1.7.2 版本选项。

**工作逻辑**：在 `options` 列表中插入 `- "1.7.2"`，位于 `1.8.0` 和 `1.7.1` 之间，与版本发布顺序一致。

## 总结

本提交是单行基础设施更新，在 bug 报告模板的版本下拉列表中新增 1.7.2 选项，配合 1.7.2 版本发布。
