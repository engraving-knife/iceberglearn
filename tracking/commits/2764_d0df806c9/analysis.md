# 提交 2764：Build: Bump mkdocs-material from 9.6.21 to 9.6.22 (#14371)

## 提交信息

- **序号**：2764 / 4088
- **哈希**：d0df806c92444077e49545b039fd4910d9e3f02b
- **短哈希**：d0df806c9
- **日期**：2025-10-18 21:13:30 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump mkdocs-material from 9.6.21 to 9.6.22 (#14371)
- **PR/Issue**：#14371

## 总体目的

本提交由 dependabot 自动生成，将 Iceberg 文档站点使用的 mkdocs-material 主题从 9.6.21 升级到 9.6.22（补丁版本升级）。

背景在于：Iceberg 使用 MkDocs 配合 mkdocs-material 主题来构建官方文档站点（site/ 目录）。mkdocs-material 是一个流行的 MkDocs 主题，提供现代化的文档外观和搜索等功能。dependabot 会定期检查依赖更新并提交 PR。本次是 9.6.x 系列内的补丁升级（9.6.21 → 9.6.22），属于 `version-update:semver-patch` 类型，通常包含 bug 修复和小幅改进，不引入破坏性变化。

## 如何达成设计目的

在 `site/requirements.txt` 中将 mkdocs-material 的版本从 9.6.21 改为 9.6.22。

## 修改详情

### `site/requirements.txt` (+1/-1 lines)

**修改目的**：升级 mkdocs-material 到最新补丁版本。

**工作逻辑**：将 `mkdocs-material==9.6.21` 改为 `mkdocs-material==9.6.22`（或对应的需求文件语法）。这是文档构建依赖，不影响 Iceberg 的 Java 代码或运行时行为。

## 总结

本提交是 dependabot 自动发起的文档构建依赖补丁升级，将 mkdocs-material 从 9.6.21 升级到 9.6.22。作为 semver-patch 级别升级，预期仅包含 bug 修复和小幅改进，对文档站点功能无影响，风险极低。
