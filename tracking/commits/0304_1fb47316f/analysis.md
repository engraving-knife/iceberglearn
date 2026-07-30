# 提交 0304：Build: Bump mkdocs-material from 9.5.1 to 9.5.3 (#9376)

## 提交信息

- **序号**：0304 / 4088
- **哈希**：1fb47316fedd81449c5df69a5be7cbf8075877ac
- **短哈希**：1fb47316f
- **日期**：2023-12-24 09:17:26 +0100
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump mkdocs-material from 9.5.1 to 9.5.3 (#9376)
- **PR/Issue**：#9376

## 总体目的

本提交是 Dependabot 自动生成的文档站点构建依赖升级，把 `mkdocs-material` 从 9.5.1 升到 9.5.3。`mkdocs-material` 是基于 MkDocs 的流行文档主题，Iceberg 项目用其在 `site/` 目录下构建官方文档站点（<https://iceberg.apache.org>）。9.5.1 → 9.5.3 是 patch 版本升级，按语义化版本约定仅含 bug 修复与小改进，不引入破坏性变更。

Dependabot 提交信息中给出了升级类型 `update-type: version-update:semver-patch` 和依赖类型 `direct:production`，并附上了上游 release notes、changelog、commits 对比链接。升级动机是获取上游修复（通常涉及主题渲染、搜索、国际化、可访问性等 bug 修复）并保持文档构建工具链新鲜。由于该依赖只作用于文档站点构建，不进入 Iceberg 运行时或编译产物，风险面很小，最坏情况也只是文档渲染异常，可快速回退。

## 如何达成设计目的

设计上极简：直接修改 `site/requirements.txt` 中 `mkdocs-material` 的版本钉，从 `==9.5.1` 改为 `==9.5.3`，其它依赖不动。Dependabot 自动比对 PyPI 版本后生成此 PR，无人工代码改动。

## 修改详情

### `site/requirements.txt`

**修改目的**：把文档站点构建依赖 `mkdocs-material` 的版本钉从 9.5.1 提升到 9.5.3。

**工作逻辑**：该文件固定了 `site/` 模块构建文档所用的 Python 依赖版本，包含 `mkdocs-awesome-pages-plugin`、`mkdocs-macros-plugin`、`mkdocs-material`、`mkdocs-material-extensions`、`mkdocs-monorepo-plugin`、`mkdocs-redirects` 等多个插件。本次仅把 `mkdocs-material==9.5.1` 一行改为 `mkdocs-material==9.5.3`，其余行不变。升级后下次构建文档站点时会使用 9.5.3 版本的主题与渲染逻辑。

## 小结

本提交是一次低风险的文档构建依赖 patch 版本升级，把 `mkdocs-material` 从 9.5.1 升到 9.5.3，仅影响 `site/` 模块的文档站点构建，不触及 Iceberg 运行时或编译产物。属于 Dependabot 例行维护，保持文档工具链新鲜、获取上游 bug 修复。
