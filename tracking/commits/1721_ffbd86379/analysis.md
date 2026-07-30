# 提交 1721：Infra: Update Iceberg bug report template for 1.8.0 (#12248)

## 提交信息

- **序号**：1721 / 4088
- **哈希**：ffbd8637915491f241d7c54f8f31b128939ba1c2
- **短哈希**：ffbd86379
- **日期**：2025-02-13 14:34:54 +0530
- **作者**：Amogh Jahagirdar
- **提交说明**：Infra: Update Iceberg bug report template for 1.8.0 (#12248)
- **PR/Issue**：#12248

## 总体目的

更新 GitHub Issue 模板中的 Iceberg 版本下拉选项，将 1.8.0 标记为最新发布版本。当用户提交 bug 报告时，需要选择使用的 Iceberg 版本，模板中的版本列表应与实际发布版本保持同步。

在 1.8.0 发布后，需要将版本下拉选项中的 "1.7.1 (latest release)" 改为 "1.8.0 (latest release)"，并将 1.7.1 保留为普通选项（去掉 "latest release" 标记）。

## 如何达成设计目的

修改 `.github/ISSUE_TEMPLATE/iceberg_bug_report.yml` 中版本选择下拉框的选项列表。

## 修改详情

### `.github/ISSUE_TEMPLATE/iceberg_bug_report.yml`（修改, +2/-1 lines）

**修改目的**：更新 bug 报告模板中的版本选项。

**工作逻辑**：在版本选择的 `options` 列表中，将原来的 `- "1.7.1 (latest release)"` 替换为两行：`- "1.8.0 (latest release)"` 和 `- "1.7.1"`。即 1.8.0 成为最新的发布版本标记，1.7.1 保留在列表中但不再标记为最新。

## 小结

- **成效**：Bug 报告模板的版本选项更新为 1.8.0 作为最新版本，用户提交 issue 时可以选择正确的版本。
- **影响范围**：仅影响 GitHub Issue 模板，不影响项目代码。
- **回迁到 1.4.x 的注意事项**：不建议回迁。1.4.x 分支有自己的版本发布计划，Issue 模板中的版本选项应根据该分支的实际发布版本设置。此提交属于 1.8.0 发布流程的一部分。
