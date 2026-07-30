# 提交 2795：Build: Bump mkdocs-macros-plugin from 1.4.0 to 1.4.1 (#14421)

## 提交信息

- **序号**：2795 / 4088
- **哈希**：68e555b94f4706a2af41dcb561c84007230c0bc1
- **短哈希**：68e555b94
- **日期**：2025-10-26 00:05:31 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump mkdocs-macros-plugin from 1.4.0 to 1.4.1 (#14421)
- **PR/Issue**：#14421

## 总体目的

本提交由 dependabot 自动生成，将 `mkdocs-macros-plugin` 从 1.4.0 升级到 1.4.1。

`mkdocs-macros-plugin` 是 MkDocs 的一个插件，允许在文档中使用 Jinja2 宏和变量，增强文档的可编程性和可重用性。Iceberg 项目使用 MkDocs 构建文档站点，该插件是文档构建工具链的一部分。

这是一个 patch 级别升级（1.4.0 → 1.4.1），通常包含 bug 修复和小改进，风险很低。

## 如何达成设计目的

通过修改文档站点的 Python 依赖文件 `site/requirements.txt`，将 `mkdocs-macros-plugin` 的版本从 `1.4.0` 更新为 `1.4.1`。

## 修改详情

### `site/requirements.txt` (+1/-1 lines)

**修改目的**：升级 mkdocs-macros-plugin 版本。

**工作逻辑**：将 `mkdocs-macros-plugin==1.4.0` 修改为 `mkdocs-macros-plugin==1.4.1`。文档站点构建时将使用新版本的宏插件。

## 总结

本提交是文档构建工具链的依赖升级，将 mkdocs-macros-plugin 从 1.4.0 升级到 1.4.1。作为 patch 级别升级，预期包含 bug 修复，对文档构建的稳定性有积极影响。
