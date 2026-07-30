# 提交 3719：Website: remove duplicated entries from search (#16368)

## 提交信息

- **序号**：3719 / 4088
- **哈希**：8caf7c2b6c9c5b48e8b993936b87b0a033fca930
- **短哈希**：8caf7c2b6
- **日期**：2026-05-16 18:54:30 -0700
- **作者**：Maksim Konstantinov
- **提交说明**：Website: remove duplicated entries from search (#16368)
- **PR/Issue**：#16368

## 总体目的

本提交旨在修复 Iceberg 文档网站搜索功能中出现的重复结果问题。Iceberg 文档站点使用 MkDocs Material 构建，并使用 `mkdocs-monorepo-plugin` 将多版本文档（nightly、latest 以及具体的版本号如 1.10.2、1.11.0 等）都收录到同一个站点下。这种结构导致搜索索引中同一篇文档的不同版本副本都被索引，用户搜索某个关键词时会看到同一内容的多条结果（每个版本各一条），既冗余又影响搜索体验。

为解决该问题，需要让搜索索引只收录某一版本（nightly）的内容，过滤掉 latest 与具体版本号目录下的文档副本，从而消除重复命中。

## 如何达成设计目的

通过引入 `mkdocs-exclude-search` 插件（版本 0.6.6）来排除特定路径下的文档不进入搜索索引。在 `mkdocs.yml` 的 plugins 配置中新增 `exclude-search` 插件，配置排除规则：排除 `docs/latest*`（latest 版本及其子内容）以及 `docs/[0-9]*`（具体版本号目录及其子内容），只保留 `docs/nightly/*` 进入搜索索引。同时在 `requirements.txt` 中添加该插件的依赖声明。

## 修改详情

### `site/mkdocs.yml` (+5/-0 lines)

**修改目的**：配置 `exclude-search` 插件，从搜索索引中排除非 nightly 版本的文档。

**工作逻辑**：
在 `plugins` 列表中新增 `exclude-search` 插件配置：
```yaml
- exclude-search:
    # Index only docs/nightly/* to avoid duplicate hits across versions.
    exclude:
      - 'docs/latest*'  # excludes latest and its children
      - 'docs/[0-9]*'    # excludes docs/<x.y.z> and their children
```
两条排除规则：
- `docs/latest*`：排除 latest 版本目录及其所有子文档。
- `docs/[0-9]*`：通过正则匹配排除以数字开头的目录（即具体版本号如 `docs/1.10.2`、`docs/1.11.0` 等）。
最终只有 `docs/nightly/*` 进入搜索索引，避免不同版本相同文档导致的重复命中。

### `site/requirements.txt` (+1/-0 lines)

**修改目的**：声明 `mkdocs-exclude-search` 依赖。

**工作逻辑**：
新增依赖 `mkdocs-exclude-search==0.6.6`，使 CI/本地构建能正确安装该插件。

## 总结

本提交通过引入 `mkdocs-exclude-search` 插件并配置排除规则，使 Iceberg 文档站点的搜索索引只收录 nightly 版本，过滤掉 latest 与具体版本号目录下的文档副本，从而消除搜索结果中的重复条目，显著提升了文档搜索的用户体验。改动局限于站点构建配置，不影响产品代码。
