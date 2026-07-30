# 提交 0477：Build: Bump mkdocs-material-extensions from 1.3 to 1.3.1 (#9160)

## 提交信息

- **序号**：0477
- **哈希**：a130f8f1c86fa9cf37047bef505f8a8db2e9bbc1
- **短哈希**：a130f8f1c
- **日期**：2024-02-06 20:11:11 +0100
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump mkdocs-material-extensions from 1.3 to 1.3.1
- **PR/Issue**：#9160

## 总体目的

本提交由 Dependabot 自动生成，将 `mkdocs-material-extensions` 从 `1.3` 升级到 `1.3.1`，属于一次语义化版本中的 patch 版本更新（`version-update:semver-patch`）。`mkdocs-material-extensions` 是 MkDocs Material 主题的一个伴随扩展库，它为 Material 主题提供额外的功能支持（如社交卡片、代码注解等高级渲染能力），是 Iceberg 文档站点构建链的组成部分之一。

Iceberg 项目使用 MkDocs 配合 Material 主题来构建其官方文档站点（位于仓库的 `site/` 目录）。文档站点的 Python 依赖通过 `site/requirements.txt` 进行精确的版本锁定管理，其中包含 mkdocs 主体、mkdocs-material 主题及其扩展、各类页面插件（awesome-pages、macros、monorepo、redirects）等。`mkdocs-material-extensions` 与 `mkdocs-material` 主题版本需要保持兼容，因此当扩展库发布 patch 修复时，及时升级能避免文档构建中可能出现的渲染异常或兼容性问题。

由于这是 patch 级别更新（1.3 → 1.3.1），按照语义化版本约定，它仅包含向后兼容的缺陷修复，不引入破坏性变更，因此升级风险极低。Dependabot 将其标记为 `direct:production`，意味着该依赖直接影响文档站点的产出质量。

## 如何达成设计目的

Dependabot 扫描 `site/requirements.txt` 中的 pip 依赖锁定，识别出 `mkdocs-material-extensions==1.3` 这一可升级项，将其精确版本号替换为 `1.3.1`。由于该文件使用 `==` 进行严格版本锁定，单点修改即可确保文档构建环境在重新安装依赖时拉取到新版本。

## 修改详情

### `site/requirements.txt`

**修改目的**：将文档站点构建依赖 `mkdocs-material-extensions` 的锁定版本从 `1.3` 提升到 `1.3.1`。

**工作逻辑**：

文件在 mkdocs 相关依赖列表区域（第 18-23 行附近）将：

```
mkdocs-material-extensions==1.3
```

改为：

```
mkdocs-material-extensions==1.3.1
```

该文件同时锁定了 `mkdocs-material==9.5.7`（主题本体）、`mkdocs-awesome-pages-plugin==2.9.2`、`mkdocs-macros-plugin==1.0.5`、`mkdocs-redirects==1.2.1` 以及通过 git 引用的 `mkdocs-monorepo-plugin`。`mkdocs-material-extensions` 作为 Material 主题的功能扩展，其版本需与主题保持兼容；本次 patch 升级仅修复缺陷，不改变扩展 API，因此与已锁定的 `mkdocs-material==9.5.7` 保持兼容。在文档构建（如 CI 中的 `mkdocs build`）时，pip 会依据更新后的 requirements 安装 `1.3.1` 版本，从而应用上游修复。

## 小结

本提交是一次由 Dependabot 驱动的文档构建依赖 patch 升级，仅修改 `site/requirements.txt` 中 `mkdocs-material-extensions` 的锁定版本（1.3 → 1.3.1），文件改动量为 1 行增、1 行删。该扩展库服务于 Iceberg 文档站点的 Material 主题渲染，本次 patch 升级旨在应用上游缺陷修复，保持文档构建链的稳定性与兼容性。
