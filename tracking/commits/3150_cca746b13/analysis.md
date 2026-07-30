# 提交 3150：Build: Bump pymarkdownlnt from 0.9.34 to 0.9.35 (#15130)

## 提交信息

- **序号**：3150 / 4088
- **哈希**：cca746b13250bc4ecde53cf0df7536877070d51a
- **短哈希**：cca746b13
- **日期**：2026-01-24 20:51:38 -0800
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump pymarkdownlnt from 0.9.34 to 0.9.35 (#15130)
- **PR/Issue**：#15130

## 总体目的

本提交是 Dependabot 自动发起的依赖升级，将文档站点的 Python 依赖 `pymarkdownlnt` 从 `0.9.34` 升级到 `0.9.35`。`pymarkdownlnt`（PyMarkdownLint）是一个 Markdown 文档 linter，Iceberg 在文档站点构建与 CI 流程中用它对仓库内的 Markdown 文档进行风格与规范检查，保证文档质量一致性。该依赖声明在 `site/requirements.txt` 中，与 MkDocs 文档站点所需的其他 Python 包一同管理。

本次升级为补丁版本（patch）升级（`version-update:semver-patch`，0.9.34 → 0.9.35），按语义化版本约定仅包含向后兼容的缺陷修复与小幅改进，预期不会引入破坏性 lint 规则变更，对现有文档的 lint 结果影响极小。Dependabot 在提交信息中附带了上游 release notes、changelog 与 commits 对比链接，便于审查者评估升级风险。

## 如何达成设计目的

直接在 `site/requirements.txt` 中将 `pymarkdownlnt` 的版本固定从 `0.9.34` 改为 `0.9.35`，无需其他改动。该文件以 `==` 精确固定版本，CI 在构建文档与运行 lint 时会拉取新版本。

## 修改详情

### `site/requirements.txt` (+1/-1 lines)

**修改目的**：将 pymarkdownlnt 版本从 0.9.34 提升到 0.9.35。

**工作逻辑**：将文件末尾的 `pymarkdownlnt==0.9.34` 修改为 `pymarkdownlnt==0.9.35`。该文件集中声明文档站点构建与文档 lint 所需的 Python 依赖（如 mkdocs-material、mkdocs-rss-plugin 等），`pymarkdownlnt` 作为 Markdown linter 在 CI 中对文档进行校验。升级到 0.9.35 获取上游补丁修复，属 semver-patch 级别，预期向后兼容。

## 总结

本提交通过将文档 lint 工具 pymarkdownlnt 从 0.9.34 升级到 0.9.35，获取上游补丁修复，保持文档质量检查工具的及时更新；作为 semver-patch 升级，对现有 Markdown 文档的 lint 行为预期无破坏性影响。
