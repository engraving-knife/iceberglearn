# 提交 2922：Build: Bump actions/labeler from 5 to 6 (#14689)

## 提交信息

- **序号**：2922 / 4088
- **哈希**：790a82065ca30ccf23a97b5a22497a42ed19c7df
- **短哈希**：790a82065
- **日期**：2025-11-25 23:33:57 -0800
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump actions/labeler from 5 to 6
- **PR/Issue**：#14689

## 总体目的

这是 dependabot 自动生成的 GitHub Actions 版本升级提交（在 2921 移除主版本忽略规则后生效）。`actions/labeler` 是 GitHub 官方的 PR 自动标签 Action，用于根据修改的文件路径自动为 PR 添加标签。此次从 v5 升级到 v6，属于主版本升级，可能包含行为变更和新功能。

## 如何达成设计目的

通过修改使用 labeler Action 的 workflow 文件，将引用版本从 v5 更新为 v6。

## 修改详情

### `.github/workflows/labeler.yml` (+1/-1 lines)

**修改目的**：将 actions/labeler 从 v5 升级到 v6。

**工作逻辑**：将 `- uses: actions/labeler@v5` 修改为 `- uses: actions/labeler@v6`，其余配置（repo-token、sync-labels）不变。

## 总结

本提交是 GitHub Actions labeler 的主版本升级（v5 到 v6），在 2921 允许主版本升级后由 dependabot 自动生成。主版本升级可能包含行为变更，需要关注 labeler v6 的迁移指南。
