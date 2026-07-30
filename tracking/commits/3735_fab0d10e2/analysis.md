# 提交 3735：Docs: switch default docs version from nightly to latest (#16398)

## 提交信息

- **序号**：3735 / 4088
- **哈希**：fab0d10e2bb0724c4c685e78bc9f4875a796aa3d
- **短哈希**：fab0d10e2
- **日期**：2026-05-18 12:44:17 -0700
- **作者**：Maksim Konstantinov
- **提交说明**：Docs: switch default docs version from nightly to latest (#16398)
- **PR/Issue**：#16398

## 总体目的

本提交将 Iceberg 文档网站的默认文档版本从 nightly（每日构建版本）切换为 latest（最新发布版本 1.10.1）。

原先文档站点的 Docs 标签页默认展示 nightly 版本（即开发分支的最新文档），这对大多数用户而言并不合适——普通用户通常需要的是已发布版本的文档，而非未稳定的开发版文档。将默认版本切换为 latest（1.10.1）能让用户在访问文档时首先看到与最新发布版本对应的内容，提升用户体验。

同时，由于上一个提交（#16368）将搜索索引设置为只索引 nightly 目录，本次切换默认版本后需要相应地将搜索索引目录也从 nightly 改为 latest，以保持默认展示版本与搜索索引版本的一致性。

## 如何达成设计目的

通过调整三个 MkDocs 配置文件实现：
1. 在 `nav.yml` 和 `mkdocs-dev.yml` 的导航配置中，将 `Latest (1.10.1)` 调整为 Java 文档的第一个条目（第一个条目决定默认落地页），并将 `Nightly` 移到其后。
2. 在 `mkdocs.yml` 的 `exclude-search` 插件配置中，将搜索索引的排除规则从"排除 latest、只索引 nightly"调整为"排除 nightly、只索引 latest"，使搜索索引与默认版本一致。

## 修改详情

### `site/mkdocs-dev.yml` (+2/-1 lines)

**修改目的**：调整开发环境导航，使 Latest 成为默认落地页。

**工作逻辑**：
将 Java 文档的导航顺序从：
```yaml
- Nightly: '!include docs/docs/nightly/mkdocs.yml'
- Latest (1.10.1): '!include docs/docs/latest/mkdocs.yml'
```
调整为：
```yaml
# First entry determines the default landing page for the Docs tab.
- Latest (1.10.1): '!include docs/docs/latest/mkdocs.yml'
- Nightly: '!include docs/docs/nightly/mkdocs.yml'
```
添加注释说明第一个条目决定 Docs 标签的默认落地页。

### `site/mkdocs.yml` (+2/-2 lines)

**修改目的**：将搜索索引从 nightly 切换到 latest。

**工作逻辑**：
将 `exclude-search` 插件的排除规则从：
```yaml
# Index only docs/nightly/* to avoid duplicate hits across versions.
exclude:
  - 'docs/latest*'  # excludes latest and its children
  - 'docs/[0-9]*'    # excludes docs/<x.y.z> and their children
```
调整为：
```yaml
# Index only docs/latest/* to avoid duplicate hits across versions.
exclude:
  - 'docs/nightly*'  # excludes nightly and its children
  - 'docs/[0-9]*'    # excludes docs/<x.y.z> and their children
```
现在搜索只索引 latest 版本，排除 nightly 和具体版本号目录，与默认展示版本保持一致。

### `site/nav.yml` (+2/-1 lines)

**修改目的**：调整生产环境导航，使 Latest 成为默认落地页。

**工作逻辑**：
与 `mkdocs-dev.yml` 相同的调整，将 Latest (1.10.1) 移到 Nightly 之前，并添加注释说明第一个条目决定默认落地页。Previous 版本（1.10.0、1.9.2 等）保持在 Nightly 之后不变。

## 总结

本提交将 Iceberg 文档网站的默认文档版本从 nightly 切换为 latest（1.10.1），使用户访问时首先看到已发布版本的文档而非开发版文档。同时在三个 MkDocs 配置文件中调整了导航顺序（Latest 作为第一个条目成为默认落地页）和搜索索引目录（从索引 nightly 改为索引 latest），保持默认展示版本与搜索索引的一致性。这是面向用户体验的文档配置调整，不影响产品代码。
