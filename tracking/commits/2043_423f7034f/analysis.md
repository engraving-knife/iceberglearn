# 提交 2043：Build: Bump mkdocs-material from 9.6.11 to 9.6.12

## 提交信息

- **序号**：2043 / 4088
- **哈希**：423f7034f51d48eb6312533313069bd495218523
- **短哈希**：423f7034f
- **日期**：2025-04-28 08:14:41 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump mkdocs-material from 9.6.11 to 9.6.12 (#12848)
- **PR/Issue**：#12848

## 总体目的

本提交由 Dependabot 自动生成，将 mkdocs-material 从 9.6.11 升级到 9.6.12。mkdocs-material 是 Iceberg 文档站点（`site/`）使用的前端主题框架，基于 MkDocs 构建静态文档网站。此次升级为补丁版本升级（semver-patch），包含 bug 修复和小改进。

## 如何达成设计目的

通过修改文档站点的 Python 依赖文件中的 mkdocs-material 版本号来完成升级。

## 修改详情

### `site/requirements.txt` (修改, +1/-1 lines)

**修改目的**：升级 mkdocs-material 版本号。

**工作逻辑**：
将 `mkdocs-material==9.6.11` 改为 `mkdocs-material==9.6.12`。该文件定义了 Iceberg 文档站点的 Python 依赖。

## 总结

Dependabot 自动依赖升级提交，将文档站点主题框架 mkdocs-material 从 9.6.11 升级到 9.6.12（补丁版本）。改动仅 1 行版本号变更。
