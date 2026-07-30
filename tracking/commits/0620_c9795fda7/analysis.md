# 提交 0620：文档站点新增 nightly 版本以预览本地 docs 改动

## 提交信息

- **序号**：0620 / 4088
- **哈希**：c9795fda7105789edc0d1f8a624ceb251dfd4a69
- **短哈希**：c9795fda7
- **日期**：2024-03-22 02:23:36 -0500
- **作者**：Brian "bits" Olsen <bits@bitsondata.dev>
- **提交说明**：Docs: Add local nightly build to test current docs changes (#9943)
- **PR/Issue**：#9943

## 总体目的

本提交为 Iceberg 文档站点的本地构建流程新增一个 `nightly` 版本，让贡献者在本地编辑 `/docs` 目录下的 markdown 文件后，能立即在已构建的文档站点中预览这些未提交的改动，而无需先把改动推送到 orphan `docs` 分支再重新拉取。

背景动机：

- Iceberg 文档站点（位于 `site/`）通过 MkDocs 构建，它把"非版本化站点"（`site/docs/*.md`）与"版本化文档"（来自 orphan `docs` 分支，通过 `git worktree` 挂载到 `site/docs/docs/<version>/`）编织在一起。`latest` 是指向最新发布版本的软链接。
- 仓库根目录下的 `/docs` 目录是"当前版本化文档的源"，它在发布时会作为新版本提交到 orphan `docs` 分支。但本地对 `/docs` 的修改，并不会被现有的 `latest` 软链接感知到——`latest` 指向的是 `docs` 分支里已发布版本的内容。
- 因此贡献者在修改 `/docs` 时，无法在完整站点上下文（含版本选择器、javadoc 链接、MkDocs 主题渲染）下预览自己的改动，只能直接看原始 markdown 或推到分支再看 CI 结果。这降低了文档迭代效率，也容易让排版/链接错误漏到合入后。
- 本提交通过新增一个名为 `nightly` 的"本地预览版本"解决该问题：它通过软链接直接指向仓库根目录下的 `/docs` 源文件，从而让本地构建自动包含当前未提交的 docs 改动。

## 如何达成设计目的

设计思路是在已有的版本化文档构建流程中，仿照 `create_latest` 的模式新增一个 `create_nightly` 函数，把"nightly"作为一个新的伪版本挂载到 `docs/docs/nightly/` 下，但与 `latest` 不同的是，nightly 的 `docs` 软链接不指向已发布版本，而是指向本地 `/docs` 源目录。具体设计要点：

1. **复用既有 worktree 挂载机制**：`pull_versioned_docs()` 已经通过 `git worktree add -f docs/docs "${REMOTE}/docs"` 把 orphan `docs` 分支挂到 `site/docs/docs/`，并在其中枚举各发布版本目录（`1.5.0/`、`1.4.3/` 等）。`create_nightly` 在该挂载点下额外创建 `nightly/` 目录，使其与发布版本目录并列，自然融入版本选择器。
2. **软链接穿透到仓库根 `/docs`**：通过 4 层 `..` 的相对路径 `../../../../docs/docs/`，从 `site/docs/docs/nightly/` 回到仓库根，再进入 `/docs/docs/`（即仓库根下的版本化文档源目录）。用相对路径而非绝对路径，保证脚本在不同开发者机器上可移植。
3. **复用 mkdocs 配置**：从 `../docs/mkdocs.yml`（即仓库根 `/docs/mkdocs.yml`）拷贝配置到 `nightly/`，让 nightly 版本使用与正式发布相同的 MkDocs 配置。随后调用既有的 `update_version "nightly"` 用 sed 改写 `mkdocs.yml` 中的 `site_name` 与 javadoc 链接，把版本号文本替换为 `nightly`。
4. **导航中显式列出 nightly**：在 `site/nav.yml` 的 Docs 节最前面（位于 `latest` 之前）插入 `nightly: '!include docs/docs/nightly/mkdocs.yml'`，让站点顶部的版本选择器把 `nightly` 作为第一项展示，方便本地预览时快速切换。
5. **清理同步**：`clean()` 函数增加 `rm -rf docs/docs/nightly`，与 `latest` 同等处理，避免遗留垃圾目录。
6. **文档同步更新**：`site/README.md` 中把原"docs 与 javadoc 在 orphan 分支"的描述重写为编号列表，明确区分两个 orphan 分支；新增"nightly 是指向当前本地 `/docs` 的软链接"的说明；并更新两处目录结构示意图（构建后产物结构、MkDocs 多版本结构），分别加上 `nightly (symlink to /docs/)` 与 `nightly (currently points to latest)`。

## 修改详情

### `site/dev/common.sh`

**修改目的**：新增 `create_nightly` 函数，并在版本化文档拉取流程中调用，在清理流程中同步清理。

**工作逻辑**：

- 新增 `create_nightly ()` 函数：
  - `rm -rf docs/docs/nightly/` 与 `mkdir docs/docs/nightly/`：先清后建，保证幂等。
  - `ln -s "../../../../docs/docs/" docs/docs/nightly/docs`：从 `site/`（脚本 cwd）视角创建一个名为 `docs` 的符号链接放在 `docs/docs/nightly/` 下，链接目标 `../../../../docs/docs/` 相对链接所在目录解析：从 `site/docs/docs/nightly/` 向上 4 级到仓库根，再下到 `docs/docs/`，即仓库根的版本化文档源目录。这样 nightly 的 docs 内容直接复用本地 `/docs/docs/`，任何对源文件的修改即时生效。
  - `cp "../docs/mkdocs.yml" docs/docs/nightly/`：从 `site/` 视角 `../docs/mkdocs.yml` 就是仓库根 `/docs/mkdocs.yml`，复制为 nightly 版本独立的 mkdocs 配置。
  - `cd docs/docs/` 后调用 `update_version "nightly"`：复用既有函数，用 sed 把 `nightly/mkdocs.yml` 中的 `site_name: docs/...` 改为 `docs/nightly`，把 Javadoc 导航项的版本路径改为 `javadoc/nightly`。`cd -` 返回原目录。
- `pull_versioned_docs ()` 在 `create_latest "${latest_version}"` 之后追加调用 `create_nightly`，使本地构建在拉取版本化文档时自动构建 nightly 版本。
- `clean ()` 在 `rm -rf docs/docs/latest &> /dev/null` 之后追加 `rm -rf docs/docs/nightly &> /dev/null`，并把原注释 `# Remove 'latest' directories and related Git worktrees` 改为更通用的 `# Remove temp directories and related Git worktrees`。

### `site/nav.yml`

**修改目的**：在 MkDocs 导航的 Docs 节最前面（`latest` 之前）新增 nightly 项，让版本选择器显示 nightly。

**工作逻辑**：在 `Docs:` 节列表顶部插入 `- nightly: '!include docs/docs/nightly/mkdocs.yml'`，使用 MkDocs 的 `!include` 语法引入 nightly 版本独立的 mkdocs 配置文件。由于置于列表首位，版本选择器会把 nightly 排在 latest 之前，便于本地预览时一眼看到。

### `site/README.md`

**修改目的**：同步文档说明，让贡献者了解 nightly 版本的存在与用途。

**工作逻辑**：

- 把原本一段长描述拆分为两个编号列表项，分别说明 `docs` 与 `javadoc` 两个 orphan 分支的用途与挂载位置，提升可读性。
- 新增一句："The `latest` version, is a soft link to the most recent semver version in the `docs` branch. The `nightly` version, is a soft link to the current local state of the `/docs` markdown files."——明确 latest 与 nightly 的语义差异。
- 更新"构建后产物结构"示意图：在 `docs/docs/` 下加 `nightly (symlink to /docs/)`，在 `javadoc/` 下加 `nightly (currently points to latest)`。
- 更新"MkDocs 多版本目录结构"示意图：在 `docs/docs/` 下加 `nightly/docs/` 与 `nightly/mkdocs.yml`，在 `javadoc/` 下加 `nightly`。
- 关于 `javadoc/nightly` 的注释说明："currently points to latest"——表示脚本并未真正从当前源码构建 javadoc，而是把 nightly javadoc 链接到 latest javadoc。这是务实选择：构建 javadoc 需要完整 Java 构建，重量级，不适合放在文档站点构建脚本里。

## 小结

本提交是文档构建工具链的体验改进，纯 shell + 配置 + markdown 改动，无 Java/Python 代码变更。它让贡献者在本地 `make build` 或 `make serve` 后，能在文档站点的版本选择器中切换到 `nightly` 版本，立即看到自己对 `/docs` 的修改效果，而不必推送到 orphan `docs` 分支。

**影响范围**：

- 仅触及 `site/` 目录下 3 个文件（`dev/common.sh`、`nav.yml`、`README.md`），不影响任何发布产物或运行时行为。
- 改动仅作用于本地文档构建流程，对生产站点（apache iceberg 官网）的部署流程无影响——除非部署脚本也调用 `pull_versioned_docs`，那时 nightly 也会被构建出来并出现在版本选择器中（这点需要部署侧注意，但通常部署用的是 orphan 分支已发布内容，nightly 也只是软链接到 `/docs` 源，发布时 `/docs` 与最新发布版本几乎一致，副作用有限）。
- 对开发者本地预览体验提升显著，对 CI 也有帮助——可以在 PR 检查中渲染 nightly 版本以验证 docs 改动。

**回迁到 1.4.x 的注意事项**：

1. **零风险回迁**：本提交是工具链改动，无依赖、无副作用，1.4.x 上若希望支持本地 docs 预览可直接回迁。
2. **路径假设**：脚本依赖 `site/docs/docs/` 是 worktree 挂载点、`/docs` 在仓库根的目录布局。1.4.x 上若目录结构未变，回迁即可用。
3. **`update_version` 兼容性**：`create_nightly` 复用 `update_version` 函数，需确保 1.4.x 上该函数已存在且行为一致（在 macOS 与 Linux 上分别用 BSD 与 GNU sed），否则需一并回迁。
4. **价值中等**：1.4.x 作为维护分支，文档改动较少，nightly 预览的收益不如 main 分支大；但若 1.4.x 上仍会做文档勘误与回迁，回迁本提交能改善体验且零成本。
5. **注意 javadoc/nightly 是占位**：回迁后若用户期待 nightly javadoc 也反映当前源码，需另行实现从源码构建 javadoc 的流程，本提交并未提供。
