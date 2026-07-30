# 提交 1295：Build: Bump mkdocs-macros-plugin from 1.2.0 to 1.3.7 (#11399)

## 提交信息

- **序号**：1295 / 4088
- **哈希**：b4b0bdc543078c55301da6e4b29b81f6e1329a49
- **短哈希**：b4b0bdc54
- **日期**：2024-10-28（Mon Oct 28 14:18:50 2024 +0100）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump mkdocs-macros-plugin from 1.2.0 to 1.3.7 (#11399)
- **PR/Issue**：#11399

## 总体目的

Iceberg 仓库使用 MkDocs Material 构建文档站点（位于 `site/` 目录），其中 `mkdocs-macros-plugin` 是一个 MkDocs 插件，允许在文档中使用 Jinja2 宏模板，实现动态内容渲染（如版本号注入、变量替换等）。本次提交将该插件从 1.2.0 升级到 1.3.7，属于 semver-minor 级别的次版本更新，跨度较大（1.2.x → 1.3.x），可获取新功能和 bug 修复。

## 如何达成设计目的

Dependabot 检测到 `site/requirements.txt` 中 `mkdocs-macros-plugin` 的版本固定为 1.2.0，自动将其更新为 1.3.7。该文件是 Python 依赖清单，用于 `pip install` 安装文档构建所需的 Python 包。

## 修改详情

### `site/requirements.txt`

**修改目的**：将 mkdocs-macros-plugin 从 1.2.0 升级到 1.3.7。

**工作逻辑**：在 Python 依赖清单中，将：

```
mkdocs-macros-plugin==1.2.0
```

修改为：

```
mkdocs-macros-plugin==1.3.7
```

该行位于 `mkdocs-awesome-pages-plugin==2.9.3` 和 `mkdocs-material==9.5.39` 之间。`==` 操作符表示精确版本锁定，确保文档构建环境的一致性。升级后，文档站点的宏模板渲染将使用新版本的插件逻辑。

## 小结

- **成效**：mkdocs-macros-plugin 升级到 1.3.7，获取 1.3.x 系列的新功能和 bug 修复。
- **影响范围**：仅 `site/requirements.txt` 一个文件，1 行改动，仅影响文档构建，不影响代码运行时。
- **回迁到 1.4.x 的注意事项**：这是文档构建工具链升级，与产品代码无关。1.4.x 分支的文档站点如果由 main 统一构建，则无需在 1.4.x 分支单独回迁。如果 1.4.x 需要独立构建文档，可以考虑回迁以保持工具链一致，但需验证 1.3.7 与现有 MkDocs 配置和宏用法的兼容性（跨次版本可能有 API 变化）。一般情况下**无需回迁**。
