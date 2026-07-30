# 提交 2176：Infra: Set Latest Release to 1.9.1 (#13177)

## 提交信息

- **序号**：2176 / 4088
- **哈希**：8eb476f4b8cd918ec62ec3470b47d6da3204fd1c
- **短哈希**：8eb476f4b
- **日期**：2025-05-28 15:18:07 -0500
- **作者**：Russell Spitzer
- **提交说明**：Infra: Set Latest Release to 1.9.1 (#13177)
- **PR/Issue**：#13177

## 总体目的

此提交将项目的最新发布版本从 1.9.0 更新为 1.9.1。这是 Iceberg 1.9.1 版本发布后的基础设施更新，确保 GitHub Bug 报告模板中的版本选项和 Apache DOAP（Description of a Project）文件中的版本信息与最新发布保持同步。这是版本发布流程的标准步骤，确保用户在报告 Bug 时能选择正确的版本，同时让 Apache 项目页面显示最新的发布信息。

## 如何达成设计目的

- 更新 GitHub Bug 报告模板中的版本选项列表
- 更新 DOAP RDF 文件中的版本发布信息

## 修改详情

### `.github/ISSUE_TEMPLATE/iceberg_bug_report.yml` (修改, +2/-1 lines)

**修改目的**：更新 Bug 报告模板的版本选项。

**工作逻辑**：将 "1.9.0 (latest release)" 改为 "1.9.1 (latest release)"，同时保留 "1.9.0" 作为一个选项（去掉 latest release 标记），使用户仍能为 1.9.0 版本报告问题。

### `doap.rdf` (修改, +3/-3 lines)

**修改目的**：更新 Apache DOAP 文件中的发布版本信息。

**工作逻辑**：将 DOAP 文件中的版本信息从 1.9.0（发布日期 2025-04-28）更新为 1.9.1（发布日期 2025-05-27），包括 name、created 和 revision 三个字段。

## 总结

此提交是 Iceberg 1.9.1 版本发布后的标准基础设施更新，将 GitHub Bug 报告模板和 Apache DOAP 文件中的最新版本信息从 1.9.0 更新为 1.9.1。这是版本发布流程的必要步骤，确保项目元数据与实际发布版本一致。
