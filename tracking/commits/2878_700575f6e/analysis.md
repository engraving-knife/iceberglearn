# 提交 2878：Build: Bump mkdocs-macros-plugin from 1.4.1 to 1.5.0 (#14598)

## 提交信息

- **序号**：2878 / 4088
- **哈希**：700575f6e58c655ba680a0e5cc25d5a88c225d3c
- **短哈希**：700575f6e
- **日期**：2025-11-16 00:01:15 -0800
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump mkdocs-macros-plugin from 1.4.1 to 1.5.0 (#14598)
- **PR/Issue**：#14598

## 总体目的

MkDocs Macros Plugin 是 MkDocs 的一个插件，允许在文档中使用 Jinja2 宏和变量，实现文档内容的动态生成和复用。Iceberg 项目使用 MkDocs（配合 Material 主题）构建项目文档网站，该插件用于增强文档的动态内容能力。

此提交由 Dependabot 自动生成，将 `mkdocs-macros-plugin` 从 1.4.1 升级到 1.5.0。这是一个次版本（minor）升级（1.4.x → 1.5.x），可能包含新功能和小的 API 变更，但通常保持向后兼容。

## 如何达成设计目的

通过修改 Python 依赖文件 `site/requirements.txt` 中的 `mkdocs-macros-plugin` 版本号，从 `1.4.1` 改为 `1.5.0`。

## 修改详情

### `site/requirements.txt` (+1/-1 lines)

**修改目的**：升级 mkdocs-macros-plugin 版本号。

**工作逻辑**：将 `mkdocs-macros-plugin==1.4.1` 改为 `mkdocs-macros-plugin==1.5.0`。该文件列出了文档网站构建所需的所有 Python 依赖，使用精确版本号（`==`）确保可重现构建。同一文件中还包含 `mkdocs-material==9.6.23`、`mkdocs-awesome-pages-plugin==2.10.1` 等其他 MkDocs 插件。

## 总结

该提交是 Dependabot 自动生成的依赖升级，将 mkdocs-macros-plugin 从 1.4.1 升级到 1.5.0（次版本升级）。这是一次文档构建工具的维护性升级，获取新功能和改进。由于是次版本升级，需要注意可能的 API 变更，但 MkDocs Macros Plugin 通常保持良好的向后兼容性。仅影响文档网站构建，不影响 Iceberg 核心功能。
