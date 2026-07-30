# 提交 3765：Docs: Drop manual deploy step from release instructions (#16495)

## 提交信息

- **序号**：3765 / 4088
- **哈希**：7bb0fa243baa0834fcc3fb6ec71524864a7a501f
- **短哈希**：7bb0fa243
- **日期**：2026-05-21 15:10:39 -0700
- **作者**：Steven Zhen Wu
- **提交说明**：Docs: Drop manual deploy step from release instructions (#16495)
- **PR/Issue**：#16495

## 总体目的

这个提交更新了文档发布流程的说明，移除了手动部署步骤。背景是 `site-ci` GitHub Actions 工作流已经实现自动化：任何推送到 `main` 分支且涉及 `docs/`、`site/` 或 `format/` 目录的变更，都会自动触发 `make deploy` 来构建并推送文档站点到 `asf-site` 分支。

之前发布指南中要求发布管理员手动执行 `make release` 和 `make deploy` 命令来发布版本化文档和 javadoc，这在自动化工作流上线后变得多余且可能导致混淆。需要更新文档以反映当前的自动化流程。

## 如何达成设计目的

1. 在 `site/README.md` 中，将详细的手动发布步骤替换为指向发布指南中"Documentation Release"章节的链接。
2. 在 `site/docs/how-to-release.md` 中，移除"Release versioned docs and javadoc"步骤（指向 README 的说明），在"Site update"步骤中添加关于 `site-ci` 工作流自动触发的说明。

## 修改详情

### `site/README.md` (+2/-16 lines)

**修改目的**：简化发布流程说明，移除手动部署步骤。

**工作逻辑**：
原来"Release process"章节包含详细的警告说明和两步手动流程（`make release` 和 `make deploy`）。修改后替换为简短说明，指向 `docs/how-to-release.md#documentation-release` 章节。移除了关于 `make release` 不可用的警告和两步手动部署命令。

### `site/docs/how-to-release.md` (+7/-4 lines)

**修改目的**：更新发布指南，描述自动化部署流程。

**工作逻辑**：
1. 移除"Release versioned docs and javadoc"子章节（原来只是指向 `site/README.md` 的说明）。
2. 在"Site update"步骤中，保留提交 PR 更新 Iceberg 版本和文档链接的说明，新增自动化部署的描述：

> Once this PR is merged, the `site-ci` GitHub Actions workflow is automatically triggered for any push to `main` that touches `docs/`, `site/`, or `format/`. The workflow runs `make deploy` to build and push the documentation site to the `asf-site` branch, which publishes the new version's docs and javadoc.
>
> The site will be updated automatically. Manually running `make deploy` is no longer required as part of the release.

明确告知发布管理员站点更新是自动的，不再需要手动运行 `make deploy`。

## 总结

这个提交更新了文档发布流程说明，移除了过时的手动部署步骤，反映了 `site-ci` GitHub Actions 工作流已经实现自动化部署的事实。这简化了发布管理员的操作流程，避免混淆，同时保持文档与实际自动化流程的一致性。
