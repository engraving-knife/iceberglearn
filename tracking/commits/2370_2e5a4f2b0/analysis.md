# 提交 2370：Docs: Add creating release from passed RC tag (#13595)

## 提交信息

- **序号**：2370 / 4088
- **哈希**：2e5a4f2b0bb4131c5a455288cabba797005b6e53
- **短哈希**：2e5a4f2b0
- **日期**：2025-07-18 09:28:54 -0700
- **作者**：Prashant Singh
- **提交说明**：Docs: Add creating release from passed RC tag (#13595)
- **PR/Issue**：#13595

## 总体目的

本提交是对 Iceberg 项目发布流程文档的补充。在 Iceberg 的发布流程中，当一个发布候选（Release Candidate, RC）通过投票后，需要基于该 RC 标签创建最终的发布标签。原有文档描述了如何通过 git 命令创建发布标签，但遗漏了在 GitHub 仓库中创建 Release 的步骤。

本次修改在发布文档中添加了一行说明，提示发布管理者在创建 git 标签后，还需要在 GitHub 仓库中基于该标签创建一个新的 Release，并附上了 GitHub 官方文档的链接作为操作指南。这确保了发布流程的完整性，使新版本的发布产物能够在 GitHub Releases 页面上正确展示。

## 如何达成设计目的

设计思路非常简单直接：在 `site/docs/how-to-release.md` 文件中，在"创建 git 发布标签"步骤和"在 Nexus 发布候选仓库"步骤之间，插入一行说明文字和对应的 GitHub 官方文档链接。这样发布管理者在按照文档执行发布流程时，不会遗漏在 GitHub 上创建 Release 这一环节。

## 修改详情

### `site/docs/how-to-release.md` (+2/-0 lines)

**修改目的**：补充发布流程中在 GitHub 创建 Release 的步骤说明。

**工作逻辑**：在文档中"使用 git tag 命令创建发布标签"的代码块之后、"在 Nexus 发布候选仓库"的说明之前，新增一行文字说明，提示用户使用之前创建的标签在 GitHub 仓库中创建新的 Release，并提供了 GitHub 官方操作指南的链接（https://docs.github.com/en/repositories/releasing-projects-on-github/managing-releases-in-a-repository）。

## 总结

本提交是一个纯文档修改，仅新增 2 行内容，补充了 Iceberg 发布流程文档中缺失的 GitHub Release 创建步骤。虽然改动量极小，但对于确保发布流程的完整性和可操作性具有重要意义，避免了发布管理者在执行发布时遗漏这一环节。
