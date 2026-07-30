# 提交 2048：Infra: Add 1.9.0 to issue template

## 提交信息

- **序号**：2048 / 4088
- **哈希**：b0596267abd0d474b00ff41d963244fdaffc08dc
- **短哈希**：b0596267a
- **日期**：2025-04-28 09:55:45 +0200
- **作者**：Ajantha Bhat
- **提交说明**：Infra: Add 1.9.0 to issue template (#12913)
- **PR/Issue**：#12913

## 总体目的

Apache Iceberg 1.9.0 已于 2025 年 4 月 28 日正式发布（见提交 2047 的发布说明）。GitHub Issue 模板中有一个版本选择下拉框，用于让报告 Bug 的用户选择他们使用的 Iceberg 版本。本提交将该下拉框中的版本列表更新为包含 1.9.0 作为最新版本，并将此前标记为"latest release"的 1.8.1 移除该标记，确保用户在报告问题时能选择正确的版本。

## 如何达成设计目的

修改 GitHub Issue Bug 报告模板的 YAML 配置文件，在版本选项列表中添加 1.9.0 并调整标记。

## 修改详情

### `.github/ISSUE_TEMPLATE/iceberg_bug_report.yml` (修改, +2/-1 lines)

**修改目的**：在 Bug 报告模板的版本选择下拉框中添加 1.9.0。

**工作逻辑**：
在版本选项列表中：
- 新增 `"1.9.0 (latest release)"` 作为第一个选项（最新版本）
- 将原来的 `"1.8.1 (latest release)"` 改为 `"1.8.1"`（移除 latest release 标记）

这样用户在提交 Bug 报告时，版本下拉框的第一项就是 1.9.0（标记为最新发布），1.8.1 仍然保留在列表中但不再标记为最新。

## 总结

本提交更新 GitHub Bug 报告 Issue 模板，将 1.9.0 添加为最新版本选项并将 1.8.1 的"latest release"标记移除。属于项目基础设施维护，配合 1.9.0 版本发布。
