# 提交 2555：Build: Bump mkdocs-material from 9.6.17 to 9.6.18 (#13912)

## 提交信息

- **序号**：2555 / 4088
- **哈希**：4e71502ef1922e053aa324d73fd2de9f3481f9b7
- **短哈希**：4e71502ef
- **日期**：2025-08-24 09:20:24 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump mkdocs-material from 9.6.17 to 9.6.18 (#13912)
- **PR/Issue**：#13912

## 总体目的

该提交由 Dependabot 自动生成，将文档网站构建工具 mkdocs-material 从 9.6.17 升级到 9.6.18。mkdocs-material 是 Iceberg 项目文档网站（site/）使用的 Material 主题，用于生成项目官方文档站点。

此次升级为补丁版本升级（9.6.17 -> 9.6.18），属于向后兼容的维护性更新，通常包含 bug 修复和小的 UI 改进。保持文档构建工具的最新版本有助于确保文档网站正常构建和展示。

## 如何达成设计目的

- 在文档站点的 Python 依赖文件 `site/requirements.txt` 中将 `mkdocs-material` 版本从 `9.6.17` 修改为 `9.6.18`。

## 修改详情

### `site/requirements.txt` (+1/-1)

**修改目的**：升级 mkdocs-material 版本号。

**工作逻辑**：将 `mkdocs-material==9.6.17` 修改为 `mkdocs-material==9.6.18`，更新文档构建依赖。

## 总结

该提交是 Dependabot 自动生成的依赖升级，将 mkdocs-material 文档主题从 9.6.17 升级到 9.6.18（补丁版本升级），修改仅一行版本号配置。
