# 提交 2362：INFRA: Add 1.9.2 to latest (#13577)

## 提交信息

- **序号**：2362 / 4088
- **哈希**：026374b530eeea46429cef61a99ba9321e0587b6
- **短哈希**：026374b53
- **日期**：2025-07-16 16:54:21 -0700
- **作者**：Prashant Singh
- **提交说明**：INFRA: Add 1.9.2 to latest (#13577)
- **PR/Issue**：#13577

## 总体目的

这个提交更新仓库基础设施配置，将 1.9.2 标记为最新发布版本。涉及 GitHub Issue 模板和 DOAP（Description of a Project）RDF 文件的更新，与提交 2360（站点文档更新）配合完成 1.9.2 发布的配套工作。

背景：Apache 项目的发布流程涉及多个层面的版本信息维护。GitHub 的 bug 报告模板需要列出可选的版本号供用户选择当前使用的版本；DOAP RDF 文件是 Apache 项目用于向 projects.apache.org 报告项目发布信息的标准格式文件。每次发布新版本时需要更新这两处以反映最新版本。

## 如何达成设计目的

1. 在 GitHub bug 报告模板中将 1.9.2 添加为最新版本选项，将 1.9.1 降级为普通选项。
2. 在 DOAP RDF 文件中将最新发布版本从 1.9.1 更新为 1.9.2。

## 修改详情

### `.github/ISSUE_TEMPLATE/iceberg_bug_report.yml` (+3/-1 lines)

**修改目的**：在 bug 报告模板中添加 1.9.2 版本选项。

**工作逻辑**：在版本选择下拉列表中将 `"1.9.1 (latest release)"` 改为 `"1.9.2 (latest release)"`，并在其后新增 `"1.9.1"` 选项（去掉 latest 标记）。这样用户报告 bug 时可以选择 1.9.2（最新）或 1.9.1 作为使用的版本。

### `doap.rdf` (+3/-3 lines)

**修改目的**：更新 DOAP 文件中的最新发布版本信息。

**工作逻辑**：将 `<release>` 块中的 `<name>1.9.1</name>` 改为 `<name>1.9.2</name>`，`<created>2025-05-27</created>` 改为 `<created>2025-07-16</created>`，`<revision>1.9.1</revision>` 改为 `<revision>1.9.2</revision>`。使 Apache 项目目录中显示的最新发布版本为 1.9.2。

## 总结

该提交更新了仓库基础设施配置以反映 1.9.2 发布，包括 GitHub bug 报告模板的版本选项和 DOAP RDF 文件的发布记录。这是发布流程的标准基础设施更新，确保用户在报告 bug 时能选择正确版本，Apache 项目目录显示最新发布信息。
