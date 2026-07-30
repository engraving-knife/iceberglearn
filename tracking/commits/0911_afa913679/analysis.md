# 提交 0911：Build: Bump mkdocs-material from 9.5.27 to 9.5.28 (#10648)

## 提交信息

- **序号**：0911 / 4088
- **哈希**：afa913679b8b5db2a120ad6eae7244c7a8451c59
- **短哈希**：afa913679
- **日期**：2024-07-08
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump mkdocs-material from 9.5.27 to 9.5.28 (#10648)
- **PR/Issue**：#10648

## 总体目的

Iceberg 项目文档网站使用 MkDocs Material 主题构建（位于 `site/` 目录）。dependabot 监控文档构建工具链的 Python 依赖并定期提交升级 PR。本次将 `mkdocs-material` 从 `9.5.27` 升级到 `9.5.28`，引入主题的 patch 版本修复。这是文档构建工具链维护，不影响 Iceberg 任何运行时代码逻辑。

## 如何达成设计目的

Python 依赖通过 `site/requirements.txt` 精确版本锁定（`==`）。升级时只需修改 `requirements.txt` 中 `mkdocs-material` 的版本号，文档构建时 pip 安装新版本主题，重新生成的文档站点外观和行为不变（patch 版本通常只修复 bug）。

## 修改详情

### `site/requirements.txt`

**修改目的**：将 mkdocs-material 从 9.5.27 升级到 9.5.28。

**工作逻辑**：

```diff
-mkdocs-material==9.5.27
+mkdocs-material==9.5.28
```

该文件锁定文档站点构建的 Python 依赖版本。其中包含多个 mkdocs 插件：`mkdocs-awesome-pages-plugin`、`mkdocs-macros-plugin`、`mkdocs-material`（主题）、`mkdocs-material-extensions`、`mkdocs-monorepo-plugin`（git 依赖）、`mkdocs-redirects`。本次仅升级 `mkdocs-material` 主题本身，其他插件版本不变。MkDocs Material 9.5.x 系列 patch 升级通常修复主题渲染 bug 或小幅改善，不影响文档内容。

## 小结

- **成效**：将 mkdocs-material 从 9.5.27 升级到 9.5.28，引入文档主题的 patch 版本修复。
- **影响范围**：1 个文件 `site/requirements.txt`，1 行改动，仅影响文档网站构建工具链，不影响 Iceberg 运行时代码。
- **回迁到 1.4.x 的注意事项**：可以回迁但优先级最低。这是文档构建工具的 patch 版本升级，对产品功能无任何影响。1.4.x 分支若维护独立的文档站点，可按需升级。此提交不涉及任何 Java/Python 运行时代码，回迁与否不影响 Iceberg 功能。建议作为低优先级的文档工具链维护项处理。
