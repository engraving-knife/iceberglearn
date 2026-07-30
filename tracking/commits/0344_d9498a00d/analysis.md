# 提交 0344：Shift site build to use monorepo and gh-pages

## 提交信息

- **序号**：0344
- **哈希**：d9498a00dd1d3f0a5468b4b7c7da84750a7636f1
- **短哈希**：d9498a00d
- **日期**：2024-01-09 07:04:59 -0800
- **作者**：Brian Olsen
- **提交说明**：Shift site build to use monorepo and gh-pages

## 总体目的

本提交是 Iceberg 文档站点构建基础设施的全面重构。重构的核心是把站点的版本化文档（versioned docs）从原本"分散在多个孤儿分支（orphan branch）`docs-<version>` 中、构建时通过 git worktree 拉取"的方案，改为"集中到一个 `docs` 孤儿分支 + 一个 `javadoc` 孤儿分支，构建时用 git worktree 一次性挂载全部版本"的方案，并新增 GitHub Actions 工作流 `site-ci.yml` 实现 push 到 `main` 时自动部署到 `gh-pages` 分支。这同时是一次目录结构重组：原本静态站点页面（about、community、spec 等）和版本化文档（nightly、latest、1.4.0 等版本目录）都位于 `site/docs/` 下，新方案把版本化文档的源文件从 `site/docs/docs/<version>/` 移到主仓库根目录的 `docs/`，让版本化文档成为"主仓库的一部分"——后续每个 Iceberg release 提交时，新的 `docs/` 内容会自动出现在 `main` 分支上，不需要在孤儿分支里额外维护。

这一改动有两层动机。第一是降低维护成本：原方案下每个 Iceberg 版本都需要维护一个 `docs-<version>` 孤儿分支，分支数量随版本累积不断膨胀，更新某个旧版本文档（如修正 typo）需要切到对应分支单独修改、push，运维负担重；新方案下所有版本的文档统一放在 `docs` 孤儿分支的不同子目录下（`docs/<version>/`），更新任意版本只需在该分支上一次性修改即可。第二是构建可重现性：原方案下 `mkdocs.yml` 通过 `mkdocs-monorepo-plugin` 的 `!include` 语法分别引用每个 nightly/latest/具体版本 子项目，依赖孤儿分支的可用性；新方案下构建脚本 `dev/common.sh` 中的 `pull_versioned_docs` 函数会自动 `git worktree add -f docs/docs "${REMOTE}/docs"`、`git worktree add -f docs/javadoc "${REMOTE}/javadoc"`，把两个孤儿分支的内容挂载到本地工作树，再调用 `create_latest` 自动创建 `latest` 软链接（指向 `get_latest_version` 返回的最新 semver 目录），整个流程对开发者完全自动化。

附带地，本提交还完成了一系列文档内容更新：补齐 1.4.0/1.4.1/1.4.2/1.4.3 的 release notes（之前 release notes 只到 1.3.1），新增 blogs.md 中缺失的几条博客条目（Hive 4.x 与 Iceberg 系列、From Hive Tables to Iceberg Tables 等），更新 roadmap.md 把项目按 General/Clients/Spec V2/Spec V3 重新组织，更新 view-spec.md 把视图版本元数据中的 `default-catalog` 与 `default-namespace` 字段从 representation 层移到 version 层（与代码实现保持一致），新增 spec.md 中 Content File 与 File Scan Task 的 JSON 序列化说明，更新 multi-engine-support.md 把 Spark 3.1 标记为 End of Life、Spark 3.2 标记为 Deprecated，更新 hive-quickstart.md 把 Hive 版本从 `4.0.0-alpha-2` 升到 `4.0.0-beta-1`，更新 community.md 中的 Slack 邀请链接（旧链接已失效），新增 vendors.md 中 ClickHouse 厂商条目，更新 releases.md 把 Spark 3.5 runtime jar、aws-bundle/gcp-bundle/azure-bundle jar 加入下载清单。这些内容更新与基础设施重构合并在一个 PR 中，是因为它们都是为即将发布的 Iceberg 1.4.x 系列文档做准备。

## 如何达成设计目的

实现路径分四层：(1) **目录结构重组**——把版本化文档源文件从 `site/docs/docs/<version>/` 移到主仓库根目录 `docs/`（commit 中体现为：删掉 `site/.gitignore` 中关于 `.github/`、`/site/`、`.github/vale/`、`.vale.ini`、macOS/Linux/Eclipse 等的旧忽略规则——因为 `site/` 目录现在的产物结构变了；修改主 `.gitignore` 增加 `site/site/`、`site/docs/javadoc/`、注释掉的 `site/docs/docs/`）。(2) **构建脚本**——新增 `site/Makefile` 提供 `serve`/`build`/`deploy`/`clean`/`help` 五个 target，分别委托给 `site/dev/{serve,build,deploy,clean}.sh`；新增 `site/dev/common.sh`（216 行的核心脚本，封装 git remote 管理、worktree 挂载、`create_latest`、`update_version`、`search_exclude_versioned_docs` 等函数）和 `site/dev/setup_env.sh`（按顺序调用 `clean` + `install_deps` + `pull_versioned_docs` 准备环境）。(3) **CI 自动化**——新增 `.github/workflows/site-ci.yml`，监听 `main` 分支上 `site/**` 路径的 push，运行 `make deploy` 自动部署到 `gh-pages` 分支；同时在 `flink-ci.yml`/`hive-ci.yml`/`java-ci.yml`/`spark-ci.yml` 的 `paths` 触发列表中加入 `site/**`，让文档变更也能触发各引擎 CI 跑测试（避免文档改动意外破坏引擎集成的 markdown 引用）。(4) **MkDocs 配置**——把 `nav` 配置从 `mkdocs.yml` 内联移到独立的 `site/nav.yml`，用 MkDocs 的 `INHERIT:` 机制继承；启用 `navigation.tabs.sticky`、`offline` 等新特性；把 `mkdocs-monorepo-plugin` 的版本从 PyPI 固定版 `1.1.0` 改为 GitHub fork 版 `git+https://github.com/bitsondatadev/mkdocs-monorepo-plugin@url-fix`，因为该 fork 包含了上游未合入的 URL 修复（这是 monorepo 方案的关键依赖）。

## 修改详情

### `.github/workflows/site-ci.yml`（新文件，37 行）

**修改目的**：新增 GitHub Actions 工作流，实现 push 到 `main` 的 `site/**` 路径时自动部署文档站点到 `gh-pages` 分支。

**工作逻辑**：工作流触发条件为 `on: push: branches: [main] paths: ['site/**']` 与 `workflow_dispatch`（手动触发）。`jobs.deploy` 在 `ubuntu-latest` 上运行，步骤：(1) `actions/checkout@v3` 检出代码；(2) `actions/setup-python@v4` 配置 Python 3.x；(3) 在 `./site` 工作目录下执行 `make deploy`，最终通过 `mkdocs gh-deploy --dirty` 把构建产物推到 `gh-pages` 分支。`--dirty` 标志允许在未提交的临时文件状态下部署（因为 `make deploy` 中间会通过 git worktree 挂载 docs/javadoc 分支产生未跟踪文件）。

### `.github/workflows/flink-ci.yml`、`hive-ci.yml`、`java-ci.yml`、`spark-ci.yml`

**修改目的**：在四个引擎 CI 工作流的 `paths` 触发列表中加入 `site/**`，让文档变更也能触发引擎 CI。

**工作逻辑**：每个工作流的 `on.push.paths` 数组中新增一行 `- 'site/**'`。这是为了应对文档变更可能影响引擎集成的场景——例如某次文档变更修改了 markdown 中的代码示例，CI 需要验证示例代码仍能编译。`spark-ci.yml` 还顺便修复了文件末尾缺少换行符的问题（`No newline at end of file` → 正常换行）。

### `.gitignore` 与 `site/.gitignore`

**修改目的**：调整文档构建产物的忽略规则，反映新的目录结构。

**工作逻辑**：
- 主 `.gitignore` 修改三处：把 `site/site` 改为 `site/site/`（明确是目录而非文件）；新增 `#site/docs/docs/`（注释掉，原本是要忽略 docs/docs 但现在不再需要）；新增 `site/docs/javadoc/`（忽略 javadoc worktree 挂载点）。同时删除了文件末尾的空行。
- 删除 `site/.gitignore`（119 行）。该文件原本包含了大量与站点构建无关的通用忽略规则（macOS、Linux、Eclipse 等），属于从上游模板复制过来的遗留物，新方案中这些规则已经合并到主 `.gitignore` 或不再相关，删除以减少噪音。

### `site/Makefile`（新文件，34 行）

**修改目的**：提供统一的 `make` 命令入口，封装构建、运行、部署、清理等操作。

**工作逻辑**：定义 5 个 `.PHONY` target：
- `help`：用 `grep -E '^[a-zA-Z0-9 -]+:.*#' Makefile` 解析自身并打印每个 target 的注释，让开发者快速了解可用命令。
- `serve` → `dev/serve.sh`：本地清理 + 构建 + 启动 mkdocs 开发服务器。
- `build` → `dev/build.sh`：清理 + 构建静态站点。
- `deploy` → `dev/deploy.sh`：清理 + 构建 + 部署到 `gh-pages`。
- `clean` → `dev/clean.sh`：清理本地构建产物。

每个 target 都委托给 `site/dev/` 下的对应 shell 脚本，遵循"Makefile 是入口、shell 是实现"的分层模式，便于在非 make 环境（如 CI）中直接调用 shell 脚本。

### `site/dev/common.sh`（新文件，216 行）

**修改目的**：封装文档构建流程的所有核心函数，是脚本基础设施的核心。

**工作逻辑**：定义全局变量 `REMOTE="iceberg_docs"`，提供以下函数：

- `create_or_update_docs_remote`：检查 `git config "remote.${REMOTE}.url"` 是否存在，若不存在则 `git remote add "${REMOTE}" https://github.com/apache/iceberg.git`，然后 `git fetch "${REMOTE}"`。这是与 Iceberg 主仓库通信的入口——`iceberg_docs` 这个 remote 名指向 Iceberg 主仓库本身（不是 fork），用于 fetch `docs` 与 `javadoc` 这两个孤儿分支。
- `pull_remote <BRANCH>` / `push_remote <BRANCH>`：从指定分支 pull/push 更新。
- `install_deps`：`pip -q install -r requirements.txt --upgrade` 安装 mkdocs 及插件。
- `assert_not_empty <ARG>`：参数非空校验，空则退出码 1。
- `get_latest_version`：`ls -d docs/docs/[0-9]* | sort -V | tail -1` 取最大 semver 目录名，作为"latest"版本。
- `create_latest <ICEBERG_VERSION>`：删除 `docs/docs/latest/` 重建，用 `ln -s "../${ICEBERG_VERSION}/docs" docs/docs/latest/docs` 创建软链接指向最新版本目录，并 `cp` 最新版本的 `mkdocs.yml` 到 latest 下，最后调用 `update_version "latest"`。
- `update_version <ICEBERG_VERSION>`：用 `sed -i` 在指定版本的 `mkdocs.yml` 中替换 `site_name: docs/<version>` 与 `Javadoc: .../javadoc/<version>` 路径，区分 Darwin 与 Linux 的 `sed` 语法差异（macOS 的 BSD sed 需要 `sed -i ''`，Linux 的 GNU sed 用 `sed -i''`）。
- `search_exclude_versioned_docs <ICEBERG_VERSION>`：在指定版本目录的 `.md` 文件头部插入 `search:\n  exclude: true\n`，让该版本不出现在站点搜索结果中（避免不同版本的同一文档污染搜索）。
- `pull_versioned_docs`：核心函数。调用 `create_or_update_docs_remote`，删除 `docs/docs`，用 `git worktree add -f docs/docs "${REMOTE}/docs"` 与 `git worktree add -f docs/javadoc "${REMOTE}/javadoc"` 把两个孤儿分支挂载到本地，再 `create_latest` 创建最新版本软链接。
- `clean`：`set +e` 临时禁用错误退出，删除 `docs/docs/latest`，移除两个 worktree，`git restore docs/docs`，删除 `site/`，最后 `set -e` 恢复。

### `site/dev/build.sh`、`clean.sh`、`deploy.sh`、`serve.sh`、`setup_env.sh`（新文件，每个约 22-26 行）

**修改目的**：拆分构建流程为可独立调用的子脚本，便于组合与调试。

**工作逻辑**：
- `setup_env.sh`：`source dev/common.sh` → `clean` → `install_deps` → `pull_versioned_docs`，是任何构建/部署/运行前的通用前置。
- `build.sh`：`./dev/setup_env.sh` → `mkdocs build`。
- `serve.sh`：`./dev/setup_env.sh` → `mkdocs serve --dirty --watch .`，`--watch .` 让 mkdocs 监听整个 site 目录变化实现热重载。
- `deploy.sh`：`./dev/setup_env.sh` → `mkdocs gh-deploy --dirty`，部署到 gh-pages 分支（注释说明 `--remote-branch asf-site` 是预留的备选 remote branch 名）。
- `clean.sh`：`source dev/common.sh` → `clean`，仅清理不重新构建。

### `site/mkdocs.yml`

**修改目的**：重构 MkDocs 配置以支持新的目录结构与功能。

**工作逻辑**：主要变化：
- 新增 `INHERIT: ./nav.yml`：把导航结构（nav）从 `mkdocs.yml` 内联移到独立的 `nav.yml`，便于维护与重用。
- 把 `copyright` 从文件末尾移到顶部。
- 新增 theme 配置 `navigation.tabs.sticky`（让导航 tabs 滚动时保持可见）、`offline`（启用 MkDocs Material 的离线模式特性）。
- plugins 简化：`- macros:` 移除 `include_yaml` 配置（不再需要 `variables.yml`）；新增 `- offline:` 插件，通过 `enabled: !ENV [OFFLINE, false]` 让 OFFLINE 环境变量控制开关。
- 删除原内联的 `nav:` 配置（已移到 `nav.yml`）。
- markdown_extensions 更新：`emoji_index`/`emoji_generator` 从 `materialx.emoji.*` 改为 `material.extensions.emoji.*`（MkDocs Material 9.5.x 的 API 路径变更）；新增 `md_in_html`（让 markdown 在 HTML 块内生效）、`toc.permalink`（每个标题后加 🔗 永久链接）。
- 新增 `extra:` 段：`icebergVersion: '1.4.2'`（模板变量，被 `releases.md` 等文档引用）、`social:` 数组（community、github、youtube、slack 等社交链接，被 footer 渲染）。
- 新增 `watch:` 段：`- nav.yml`，让 `mkdocs serve` 监听 `nav.yml` 变化触发重建。

### `site/nav.yml`（新文件，48 行）

**修改目的**：从 `mkdocs.yml` 中拆出导航结构，独立维护。

**工作逻辑**：定义 `nav:` 数组，结构为：
- `Home: index.md`
- `Quickstart`：Spark、Hive 快速开始。
- `Docs`：通过 `!include docs/docs/latest/mkdocs.yml`、`!include docs/docs/1.4.2/mkdocs.yml`、`!include docs/docs/1.4.1/mkdocs.yml`、`!include docs/docs/1.4.0/mkdocs.yml` 引入各版本的子文档项目（利用 `mkdocs-monorepo-plugin` 的 `!include` 语法）。注意 `nightly` 已被替换为 `latest`（因为最新版本已发布，不再需要 nightly 占位）。
- `Releases`、`Roadmap`、`Blogs`、`Talks`、`Vendors`：静态页面。
- `Project`：Join、Spec、View spec、Puffin spec、Multi-engine support、How to release、Terms。
- `Concepts`：Catalogs（新增条目，但目前指向 `catalog.md` 文件可能尚未存在，属于占位）。
- `ASF`：Apache Software Foundation 相关链接（Sponsorship、Events、License、Security、Sponsors）。

### `site/requirements.txt`

**修改目的**：把 `mkdocs-monorepo-plugin` 从 PyPI 固定版改为 GitHub fork 版。

**工作逻辑**：把 `mkdocs-monorepo-plugin==1.1.0` 改为 `mkdocs-monorepo-plugin @ git+https://github.com/bitsondatadev/mkdocs-monorepo-plugin@url-fix`。这是 monorepo 方案的关键依赖——该 fork 包含上游未合入的 URL 修复补丁，让 `!include` 在新目录结构下正确解析版本化子项目的 URL。`@ url+...@<ref>` 语法让 pip 直接从 Git 仓库安装指定 ref 的代码，绕过 PyPI 发布。

### `site/README.md`

**修改目的**：重写文档站点的开发指南，反映新的目录结构与构建流程。

**工作逻辑**：主要更新：
- 重新描述目录结构：版本化文档在主仓库根目录的 `docs/`，静态站点在 `site/docs/*.md`，版本化文档的旧版本与 javadoc 在孤儿分支中通过 git worktree 挂载。
- 提供新的目录树示意图，区分 `docs (versioned)` 与 `site (non-versioned)` 两部分。
- 新增"Building the versioned docs"段，详细说明 `docs` 与 `javadoc` 孤儿分支、`git worktree` 机制、`latest` 软链接的工作原理。
- 把构建命令从 `mkdocs build`/`mkdocs serve` 改为 `make build`/`make serve`/`make clean`/`make deploy`。
- 新增"Offline mode"段，说明 `make build OFFLINE=true` 启用离线构建（禁用 `use_directory_urls` 让用户能从本地文件系统直接打开文档，但警告"Do not enable this for releases or deployments"）。
- 重写"Release process"段：从原本"切 `docs-<version>` 分支 + push 到 `gh-pages`"改为"`make release ICEBERG_VERSION=<MAJOR.MINOR.PATCH>` 把 `/docs` 复制到 `docs` 分支的对应版本目录 + 在 `javadoc` 分支构建 javadoc + `make deploy`"。
- 更新"Validate Links"段的目录树示例。

### `site/docs/releases.md`

**修改目的**：补齐 1.4.0/1.4.1/1.4.2/1.4.3 的 release notes，并把 1.3.x 标记为"Past releases"。

**工作逻辑**：
- 在最新版本下载清单中新增 Spark 3.5 runtime jar（`_2.12` 与 `_2.13`）、aws-bundle jar、gcp-bundle jar、azure-bundle jar 的下载链接，删除已 End of Life 的 Spark 3.1 runtime jar 链接。
- 新增 1.4.3 release notes：发布于 2023-12-27，主要修复事务重试时的 missing files 问题。
- 新增 "## Past releases" 标题分隔当前与历史版本。
- 新增 1.4.2 release notes：发布于 2023-11-02，修复 split offsets 处理。
- 新增 1.4.1 release notes：发布于 2023-10-23，修复 1.4.0 的多个问题。
- 新增 1.4.0 release notes：发布于 2023-10-04，详细列出 API、Core、Spark、Flink、Parquet、ORC、Vendor Integrations、Dependencies 各部分的变更。

### `site/docs/blogs.md`

**修改目的**：补齐缺失的博客条目。

**工作逻辑**：新增三条博客链接（按时间倒序插入到列表顶部）：
- Apache Hive-4.x with Iceberg Branches & Tags（2023-10-12，Cloudera，Ayush Saxena）
- Apache Hive 4.x With Apache Iceberg（2023-10-12，Cloudera，Ayush Saxena）
- From Hive Tables to Iceberg Tables: Hassle-Free（2023-07-14，Cloudera，Srinivas Rishindra Pothireddi）

### `site/docs/roadmap.md`

**修改目的**：重构 roadmap 页面，按类别组织项目。

**工作逻辑**：从原本的 "Priority 1"/"Priority 2" 两级分类改为四个类别：
- `# General`：多表事务、视图支持、CDC、Snapshot tagging/branching、Inline file compaction、Delete File compaction、Z-ordering、UPSERT。
- `# Clients`：指向 Python/Rust/Go 三个独立仓库（说明这三个客户端实现还未 final，issue 在各自仓库中跟踪）。
- `# Spec V2`：Views Spec、DSv2 streaming improvements、Secondary indexes。
- `# Spec V3`：Encryption、Relative paths、Default field values。

### `site/docs/spec.md`

**修改目的**：新增 Content File 与 File Scan Task 的 JSON 序列化规范。

**工作逻辑**：
- 删除 front matter 中的 `url: spec`、`toc: true`、`disableSidebar: true` 三个过时字段（新的 MkDocs Material 不再需要这些）。
- 新增 "### Content File (Data and Delete) Serialization" 段：用表格列出 Content File 的所有 JSON 字段（`spec-id`、`content`、`file-path`、`file-format`、`partition`、`record-count`、`file-size-in-bytes`、`column-sizes`、`value-counts`、`null-value-counts`、`nan-value-counts`、`lower-bounds`、`upper-bounds`、`key-metadata`、`split-offsets`、`equality-ids`、`sort-order-id`）及其 JSON 表示与示例，与 Iceberg `FileMetadataParser`/`ContentFileParser` 的实现一致。
- 新增 "### File Scan Task Serialization" 段：列出 File Scan Task 的 JSON 字段（`schema`、`spec`、`data-file`、`delete-files`、`residual-filter`）。

### `site/docs/view-spec.md`

**修改目的**：把视图版本元数据中的 `default-catalog` 与 `default-namespace` 字段从 representation 层移到 version 层。

**工作逻辑**：
- 在 view version metadata 字段表中新增 `view-uuid`（必填，UUID，标识视图），删除 `current-schema-id`（移到 schema 层管理）。
- 在 view version 字段表中新增 `default-catalog`（optional）与 `default-namespace`（required）两个字段，并补充说明"When `default-catalog` is `null` or not set, the catalog in which the view is stored must be used as the default catalog."。
- 在 SQL representation 字段表中删除原本的 `default-catalog`、`default-namespace`、`field-aliases`、`field-comments` 四个 optional 字段（因为已移到 version 层，或不再属于 representation）。
- 在示例 metadata JSON 中加入 `"view-uuid": "fa6506c3-7681-40c8-86dc-e36561f83385"`，并删除示例 SQL representation 表中的 `default-catalog`/`default-namespace`/`field-aliases`/`field-comments` 行，新增说明"The `event_count` (with the `Count of events` comment) and `event_date` field aliases must be part of the view version's `schema`."（即字段别名通过 schema 表达，不再单独存在 representation 字段中）。

### `site/docs/community.md`、`hive-quickstart.md`、`how-to-release.md`、`multi-engine-support.md`、`vendors.md`

**修改目的**：内容维护更新。

**工作逻辑**：
- `community.md`：更新 Slack 邀请链接（旧链接 `zt-1znkcg5zm-...` 已失效，改为 `zt-287g3akar-...`）；修正拼写错误"contians" → "contains"。
- `hive-quickstart.md`：把 Hive 容器版本从 `4.0.0-alpha-2` 升到 `4.0.0-beta-1`。
- `how-to-release.md`：把"Update github issue template"段重写为"Update GitHub"段，新增"Draft a new release to update Github to show the latest release"步骤。
- `multi-engine-support.md`：把 Spark 3.1 的状态从 "Deprecated" 改为 "End of Life"（最后支持版本固定为 `1.3.1`，不再用 `{{ icebergVersion }}` 模板变量）；把 Spark 3.2 的状态从 "Maintained" 改为 "Deprecated"。
- `vendors.md`：新增 ClickHouse 厂商条目，描述其与 Iceberg 的集成方式。

## 小结

本提交是 Iceberg 文档站点基础设施的全面重构，从"多孤儿分支 + monorepo plugin"模式重构为"集中孤儿分支 + git worktree + Makefile + GitHub Actions 自动部署"模式。改动覆盖：CI 工作流（新增 `site-ci.yml`、其他四个 CI 加入 `site/**` 触发）、目录结构（删除 `site/.gitignore`、调整主 `.gitignore`）、构建脚本（新增 `Makefile` + `dev/{common,build,clean,deploy,serve,setup_env}.sh`）、MkDocs 配置（`mkdocs.yml` 重构 + `nav.yml` 拆分 + `requirements.txt` 改用 fork 版 monorepo plugin）、README 重写、以及大量文档内容更新（release notes 补齐、blogs/roadmap/spec/view-spec/multi-engine-support/vendors 等内容修订）。这是一次为即将到来的 1.4.x 系列发布做准备的"基础设施 + 内容"双重刷新。
