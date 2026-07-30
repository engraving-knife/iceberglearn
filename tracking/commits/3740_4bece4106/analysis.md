# 提交 3740：Docs: Fix duplicate search results by moving versioned docs out of docs_dir (#16371)

## 提交信息

- **序号**：3740 / 4088
- **哈希**：4bece4106b75861a1e5a501ff71e4a6604268d1f
- **短哈希**：4bece4106
- **日期**：2026-05-18 19:27:31 -0700
- **作者**：Kevin Liu
- **提交说明**：Docs: Fix duplicate search results by moving versioned docs out of docs_dir (#16371)
- **PR/Issue**：#16371

## 总体目的

本提交通过将版本化文档从 `docs_dir`（`site/docs/docs/`）迁移到独立的 `site/versioned-docs/` 目录，从根本上修复文档站点搜索结果重复的问题。

此前版本化文档（nightly、latest、各历史版本）通过 git worktree 挂载在 `site/docs/docs/` 目录下，而 `site/docs/` 正是 MkDocs 的 `docs_dir`。这意味着所有版本化文档都被 MkDocs 视为站点源文档的一部分，即使使用 `exclude_docs` 排除，搜索索引插件仍可能索引到这些文档，导致同一篇文档的不同版本副本都出现在搜索结果中。

之前 #16368 和 #16398 尝试用 `mkdocs-exclude-search` 插件排除特定版本来缓解该问题，但这是一种治标不治本的方案。本提交采取根治措施：将版本化文档移出 `docs_dir`，放到 `site/versioned-docs/` 目录，通过 `!include` 在导航中引用，使其不进入 MkDocs 的默认文档收集范围，从而彻底消除搜索索引重复的根源。

## 如何达成设计目的

1. **目录迁移**：将版本化文档的挂载点从 `site/docs/docs/` 改为 `site/versioned-docs/`，使版本化文档物理上脱离 `docs_dir`。
2. **配置同步**：更新 `nav.yml`、`mkdocs-dev.yml` 中所有 `!include docs/docs/<version>/mkdocs.yml` 路径为 `!include versioned-docs/<version>/mkdocs.yml`。
3. **构建脚本更新**：更新 `site/dev/common.sh` 中 `create_nightly`、`create_latest`、`pull_versioned_docs`、`clean` 函数，将所有 `docs/docs/` 路径改为 `versioned-docs/`，并调整符号链接的相对路径深度（少一层）。
4. **exclude_docs 简化**：移除 `mkdocs-dev.yml` 中针对 `docs/` 的排除规则（不再需要，因为版本化文档已不在 docs_dir 下），只保留 javadoc 相关的排除。
5. **gitignore 更新**：将 `.gitignore` 中的 `site/docs/docs/` 改为 `site/versioned-docs/`。
6. **README 更新**：更新 `site/README.md` 中的目录结构图和说明，反映新的目录布局。
7. **clean 函数兼容**：在 `clean` 函数中保留对旧布局 `docs/docs/` 的清理，避免遗留。

## 修改详情

### `.gitignore` (+1/-1 lines)

**修改目的**：更新忽略的构建产物路径。

**工作逻辑**：
将 `site/docs/docs/` 改为 `site/versioned-docs/`，因为版本化文档的构建产物现在生成在新目录下。

### `site/README.md` (+42/-41 lines)

**修改目的**：更新文档构建说明，反映新的目录结构。

**工作逻辑**：
- 将版本化文档路径说明从 `/docs/docs/*.md` 改为 `/site/versioned-docs/<version>/docs/*.md`。
- 更新目录结构图，将 `docs (versioned)` 改为 `versioned-docs (versioned, per version)`，并调整层级。
- 更新 git worktree 挂载路径说明，从 `/site/docs/docs/<version>` 改为 `/site/versioned-docs/<version>`。
- 更新构建后的目录结构图，将 versioned-docs 提到与 docs 同级。

### `site/dev/common.sh` (+18/-14 lines)

**修改目的**：更新构建脚本，将版本化文档操作从 `docs/docs/` 改为 `versioned-docs/`。

**工作逻辑**：
- `create_nightly`：将 `rm -rf docs/docs/nightly/` 改为 `rm -rf versioned-docs/nightly/`，符号链接从 `ln -s "../../../../docs/docs/" docs/docs/nightly/docs` 改为 `ln -s "../../../docs/docs/" versioned-docs/nightly/docs`（少一层，因为 versioned-docs 比 docs/docs 少一层目录深度），`cd docs/docs/` 改为 `cd versioned-docs/`。
- `create_latest`：类似改动，将 `docs/docs/latest/` 改为 `versioned-docs/latest/`，符号链接源从 `docs/docs/${ICEBERG_VERSION}/mkdocs.yml` 改为 `versioned-docs/${ICEBERG_VERSION}/mkdocs.yml`。
- `pull_versioned_docs`：将 `git worktree add ... docs/docs "${docs_branch}"` 改为 `git worktree add ... versioned-docs "${docs_branch}"`，sparse-checkout 操作目录同步更新。
- `clean`：将清理 `docs/docs/latest`、`docs/docs/nightly` 改为 `versioned-docs/latest`、`versioned-docs/nightly`，`git worktree remove docs/docs` 改为 `git worktree remove versioned-docs`。同时保留对旧布局 `docs/docs` 的清理逻辑以兼容历史遗留：
```bash
# Clean up legacy layout (versioned docs used to live under docs/docs/)
rm -rf docs/docs/latest docs/docs/nightly &> /dev/null
git worktree remove docs/docs &> /dev/null
```

### `site/mkdocs-dev.yml` (+3/-6 lines)

**修改目的**：更新开发环境导航路径和简化 exclude_docs。

**工作逻辑**：
- 导航路径从 `docs/docs/latest/mkdocs.yml`、`docs/docs/nightly/mkdocs.yml` 改为 `versioned-docs/latest/mkdocs.yml`、`versioned-docs/nightly/mkdocs.yml`。
- `exclude_docs` 移除对 `docs/`、`!docs/nightly/`、`!docs/latest/` 的排除/包含规则，只保留 `javadoc/`、`!javadoc/nightly/`、`!javadoc/latest/`。因为版本化文档已不在 docs_dir 下，无需再排除。

### `site/nav.yml` (+21/-21 lines)

**修改目的**：更新生产环境导航中所有版本化文档的 include 路径。

**工作逻辑**：
将所有 21 个版本的 `!include docs/docs/<version>/mkdocs.yml` 路径统一改为 `!include versioned-docs/<version>/mkdocs.yml`，覆盖 latest、nightly 以及 1.4.0 到 1.10.2 的所有历史版本。

## 总结

本提交通过将版本化文档从 `site/docs/docs/`（MkDocs 的 docs_dir 内）迁移到独立的 `site/versioned-docs/` 目录，从根本上解决了文档站点搜索结果重复的问题。此前 #16368 和 #16398 用 exclude-search 插件排除特定版本只是治标，本次迁移使版本化文档物理上脱离 docs_dir，不再被 MkDocs 默认收集和索引，彻底消除重复根源。改动涉及构建脚本、导航配置、gitignore 和文档说明的全面同步更新，并在 clean 函数中保留对旧布局的清理以兼容过渡。这是文档基础设施的结构性优化。
