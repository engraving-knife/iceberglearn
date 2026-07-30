# 提交 0207：Build: Bump mkdocs-material from 9.4.10 to 9.4.12 (#9159)

## 提交信息

- **序号**：0207 / 4088
- **哈希**：ac71ceaa8d19405da4a4018a90ade8717c2a63c8
- **短哈希**：ac71ceaa8
- **日期**：2023-11-30
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump mkdocs-material from 9.4.10 to 9.4.12 (#9159)
- **PR/Issue**：#9159

## 总体目的

Apache Iceberg 项目使用 MkDocs（搭配 `mkdocs-material` 主题）来构建官方文档站点 <https://iceberg.apache.org/>。所有文档站构建依赖被固定在 `site/requirements.txt` 中，CI 通过 `pip install -r site/requirements.txt` 安装后渲染文档。`mkdocs-material` 是该文档站点使用的主题包，本提交由 GitHub Dependabot 自动生成，把 `mkdocs-material` 从 9.4.10 升级到 9.4.12，跨越两个 patch 版本（9.4.11、9.4.12）。

这是一次纯依赖维护性质的升级，目的是跟随上游主题的 bug 修复与小特性更新，避免文档构建链长期停留在某个旧版本上、积累与上游的偏差。Dependabot 把依赖类型标记为 `direct:production`、更新类型为 `version-update:semver-patch`，意味着按 SemVer 规则这是向后兼容的小补丁升级，不需要主仓改动任何调用代码。对 Iceberg 演进的意义在于把文档构建工具链持续保持在维护窗口内，确保文档站构建的稳定性与上游修复的及时接入。

## 如何达成设计目的

整体设计就是一次单行版本号替换：把 `site/requirements.txt` 中 `mkdocs-material==9.4.10` 改为 `mkdocs-material==9.4.12`，不动其它任何文件、不改配置或调用方式。由于 `mkdocs-material` 的 9.4.x 系列对外 API 稳定，文档站的 `mkdocs.yml` 配置与主题扩展使用方式都不需要任何调整，升级即可直接生效。

## 修改详情

### `site/requirements.txt`

**修改目的**：把文档站构建依赖 `mkdocs-material` 的固定版本从 9.4.10 提升到 9.4.12。

**工作逻辑**：

`site/requirements.txt` 是文档站构建时 `pip` 安装的 Python 依赖清单，包含 `mkdocs-awesome-pages-plugin`、`mkdocs-macros-plugin`、`mkdocs-material`、`mkdocs-material-extensions`、`mkdocs-monorepo-plugin`、`mkdocs-redirects` 等多个 MkDocs 插件与主题。本次改动只动了 `mkdocs-material` 一行：

```diff
-mkdocs-material==9.4.10
+mkdocs-material==9.4.12
```

`mkdocs-material` 是 squidfunk 维护的 Material for MkDocs 主题，是 Iceberg 文档站默认主题。从 9.4.10 → 9.4.12 跨两个 patch 版本，属于上游的 bug 修复与小改进（如修复某些配置下的渲染问题、改进搜索索引等），按项目惯例通过 Dependabot 自动升级、人工 review 后合入，不需要额外适配。

## 小结

本提交是文档构建链的例行依赖维护：把 `mkdocs-material` 从 9.4.10 升到 9.4.12，仅改一行 `requirements.txt`，确保 Iceberg 文档站主题跟上上游 patch 修复。
