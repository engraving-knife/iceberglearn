# 提交 2703：Build: Bump mkdocs-macros-plugin from 1.3.9 to 1.4.0 (#14203)

## 提交信息

- **序号**：2703 / 4088
- **哈希**：0442339856c5cb4990854b6376fe568b23df24d9
- **短哈希**：044233985
- **日期**：2025-09-29 13:19:48 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump mkdocs-macros-plugin from 1.3.9 to 1.4.0 (#14203)
- **PR/Issue**：#14203

## 总体目的

Dependabot 自动将站点构建依赖 `mkdocs-macros-plugin` 从 `1.3.9` 升级到 `1.4.0`。该插件是 MkDocs 的宏插件，允许在 Markdown 文档中使用 Jinja2 宏，Iceberg 站点（`site/`）用它来构建文档站点。

本次为 semver-minor 升级（`version-update:semver-minor`，`direct:production`），从 1.3.x 跳到 1.4.0，可能引入新功能与潜在的 API 调整。由于是站点构建工具链依赖（不进入运行时产物），升级风险主要影响文档站点构建，需验证站点仍能正常生成。Dependabot 提交说明附带了上游 release notes、changelog 与 commits 对比链接。

## 如何达成设计目的

修改站点 Python 依赖锁定文件 `site/requirements.txt`，将 `mkdocs-macros-plugin` 的版本从 `1.3.9` 改为 `1.4.0`。该文件锁定站点构建所需的全部 Python 包版本，改动一处即可在下次站点构建时生效。

## 修改详情

### `site/requirements.txt` (+1/-1 lines)

**修改目的**：升级 mkdocs-macros-plugin 版本。

**工作逻辑**：将 `mkdocs-macros-plugin==1.3.9` 改为 `mkdocs-macros-plugin==1.4.0`。使用 `==` 精确锁定版本，构建时 pip 会安装 1.4.0。

## 总结

Dependabot 自动将站点文档构建插件 mkdocs-macros-plugin 从 1.3.9 升级到 1.4.0 的次版本更新，属站点工具链常规维护。改动仅一行版本号，风险主要在于站点构建兼容性，需验证宏用法在新版本下仍正常。不涉及运行时代码。
