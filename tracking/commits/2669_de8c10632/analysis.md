# 提交 2669：Build: Bump mkdocs-material from 9.6.19 to 9.6.20 (#14131)

## 提交信息

- **序号**：2669 / 4088
- **哈希**：de8c10632d1d02368197c218dde9bff5b9f153a2
- **短哈希**：de8c10632
- **日期**：2025-09-20 22:32:19 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump mkdocs-material from 9.6.19 to 9.6.20 (#14131)
- **PR/Issue**：#14131

## 总体目的

本提交由 Dependabot 自动生成，将 mkdocs-material 从 9.6.19 升级到 9.6.20。这是一个补丁版本（patch version）升级，属于 `version-update:semver-patch` 类型。

mkdocs-material 是 Apache Iceberg 文档网站使用的主题框架。Iceberg 使用 MkDocs 配合 Material 主题来构建和部署其官方文档站点（iceberg.apache.org）。保持文档依赖的最新状态有助于获取 Bug 修复、安全补丁和小的 UI 改进，确保文档站点的稳定性和用户体验。

9.6.20 作为补丁版本升级，主要包含 Bug 修复，不涉及功能变更或破坏性改动。

## 如何达成设计目的

通过修改文档站点的 Python 依赖 requirements 文件中 mkdocs-material 的版本引用，从 9.6.19 更新为 9.6.20。

## 修改详情

### `site/requirements.txt` (+1/-1 lines)

**修改目的**：升级 mkdocs-material 版本。

**工作逻辑**：将 `mkdocs-material` 的版本从 `9.6.19` 改为 `9.6.20`。该文件列出了文档站点构建所需的所有 Python 依赖，mkdocs-material 是其中的核心主题依赖。

## 总结

本提交是 Dependabot 自动生成的依赖升级，将文档主题框架 mkdocs-material 从 9.6.19 升级到 9.6.20（补丁版本升级）。修改仅涉及 requirements.txt 中一行版本号变更。这次升级为 Iceberg 文档站点带来了 mkdocs-material 9.6.20 版本的 Bug 修复，有助于保持文档构建的稳定性。
