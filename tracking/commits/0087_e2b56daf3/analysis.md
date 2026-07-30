# 提交 0087：Docs: Add new site deployment (#8659)

## 提交信息

- **序号**：0087 / 4088
- **哈希**：e2b56daf35724700a9b57dbeee5fe23f99c592c4
- **短哈希**：e2b56daf3
- **日期**：2023-10-22 13:34:00 -0700
- **作者**：Brian "bits" Olsen
- **提交说明**：Docs: Add new site deployment (#8659)
- **PR/Issue**：#8659

## 总体目的

这个提交要解决的是 Iceberg 官网与文档站点的重建与部署问题。此前 Iceberg 的官方站点（iceberg.apache.org）由独立的 `apache/iceberg-docs` 仓库维护，主仓库内并不包含站点源码。本提交把一个全新的、基于 MkDocs Material 的站点与文档构建体系直接引入到主仓库 `site/` 目录下，使站点源码与代码、规范文档同仓维护，从而让文档变更可以随代码 PR 一起评审、随发版一起发布版本化文档。

这是一次体量很大的文档基础设施迁移（83 个文件、14006 行新增），其核心动机包括：

1. **统一仓库治理**：把非版本化的站点页面（about、community、blogs、talks、roadmap、spec、view-spec、puffin-spec 等）和版本化的引擎文档（Spark/Flink/Hive/Java API 等 nightly 文档）都纳入主仓库，目录结构镜像站点 sitemap，方便贡献者快速定位源文件。
2. **支持多版本文档编织**：通过 `mkdocs-monorepo-plugin` 把 nightly、latest、历史版本（1.4.0、1.3.1 等）以及 javadoc 多个子站点编译后聚合为单一静态站点，旧版本以 `docs-<version>` 分支形式在构建时通过 `git worktree` 挂载到 `site/docs/docs/<version>`。
3. **统一站点外观与导航**：采用 mkdocs-material 主题，启用 tabs 导航、搜索高亮、代码复制、宏变量（`variables.yml` 中 `icebergVersion` 等可复用变量）等能力。

对 Iceberg 演进的意义在于：把文档工程正式纳入主仓库生命周期，降低文档与代码脱节的风险，并为后续每个版本自动产出对应文档子站奠定基础。

## 如何达成设计目的

整体设计分三层。第一层是顶层 `site/mkdocs.yml`，定义非版本化站点（首页、Quickstart、Releases、Roadmap、Blogs、Talks、Vendors、Project、ASF 等导航），并通过 `monorepo` 插件以 `!include` 的方式把 nightly（以及将来 latest、1.4.0 等）子项目的 mkdocs.yml 聚合进来。第二层是 `site/docs/docs/nightly/mkdocs.yml`，定义版本化文档的导航（Tables、Spark、Flink、Hive、各引擎集成链接、API、Javadoc、PyIceberg）。第三层是大量 markdown 内容文件与图片资源，分别落在 `site/docs/`（非版本化页面）和 `site/docs/docs/nightly/docs/`（版本化页面）下。

此外通过 `site/variables.yml` 暴露 `icebergVersion` 等宏变量供文档引用，通过 `site/requirements.txt` 固定 mkdocs 及插件版本，通过 `site/.gitignore`（119 行）忽略本地构建产物与各类 IDE/OS 临时文件，并通过修改根 `.gitignore` 移除对 `site` 的忽略以使整个 `site/` 目录可被纳入版本控制。`site/README.md` 详细说明了目录布局、构建、运行、发版流程与链接规范。

## 修改详情

### `.gitignore`

**修改目的**：让新的 `site/` 子目录可以被纳入主仓库版本控制。

**工作逻辑**：删除根 `.gitignore` 中单独的 `site` 一行。此前 `site` 被忽略（可能因为旧的本地 mkdocs 构建产物会落在该目录），现在新站点源码要永久驻留于此，故必须解除忽略。

### `site/README.md`（新增）

**修改目的**：为新的站点构建体系提供完整的使用说明。

**工作逻辑**：详述目录结构（`/site/docs` 为非版本化页面根、`/site/docs/docs/nightly` 为本地版本化文档、`/site/docs/javadoc` 为 javadoc）、MkDocs 背景知识、用 `git worktree` 挂载历史版本与 javadoc 分支的步骤、`mkdocs build`/`mkdocs serve` 用法、发版流程（`deploy_docs.sh -v <version>` 切 `docs-<version>` 分支、把新版本加入 worktree 列表、构建后推送到 `gh-pages`），以及链接规范（只用相对链接、内部链接保留 `.md` 后缀、不要硬编码到特定版本）。文档中明确标注此阶段尚缺旧版本与 javadoc 分支，需等分支合并后流程才完整可用。

### `site/mkdocs.yml`（新增）

**修改目的**：定义非版本化站点的顶层 MkDocs 配置与导航。

**工作逻辑**：使用 `material` 主题，logo 与 favicon 指向 `assets/images/`；启用 `search`、`macros`（注入 `variables.yml`）、`monorepo` 三个插件。`nav` 中 Quickstart 下挂 Spark/Hive quickstart，Docs 下通过 `!include docs/docs/nightly/mkdocs.yml` 聚合 nightly 子站（latest、1.3.1、1.3.0 等暂注释，待分支就绪后启用），并定义 Releases、Roadmap、Blogs、Talks、Vendors、Project（community/spec/view-spec/puffin-spec/multi-engine-support/how-to-release/terms）、ASF 等导航。`markdown_extensions` 配置了代码高亮、tabbed、admonition、emoji、表格等扩展。copyright 块声明 Apache 商标与许可证。

### `site/variables.yml`（新增）

**修改目的**：提供可被 `macros` 插件注入到所有页面的全局变量。

**工作逻辑**：在 `extra` 下定义 `icebergVersion: 1.4.0`（文档中可通过宏引用当前版本号），以及 GitHub、YouTube、Slack 三个社交链接。

### `site/requirements.txt`（新增）

**修改目的**：固定站点构建所需的 Python 依赖版本，保证构建可复现。

**工作逻辑**：列出 `mkdocs-awesome-pages-plugin==2.9.2`、`mkdocs-macros-plugin==1.0.4`、`mkdocs-material==9.1.21`、`mkdocs-material-extensions==1.1.1`、`mkdocs-monorepo-plugin==1.0.5`、`mkdocs-redirects==1.2.1` 共六个包。

### `site/.gitignore`（新增）

**修改目的**：忽略 `site/` 子项目下的本地构建产物与编辑器/操作系统临时文件。

**工作逻辑**：119 行规则，重点包括临时忽略 `.github/`、忽略 mkdocs 构建输出 `/site/`、忽略 Vale 相关 `.github/vale/` 与 `.vale.ini`，以及 macOS（`.DS_Store` 等）、Linux（`*~`、`.Trash-*` 等）、Eclipse（`.metadata`、`.settings/` 等）一系列常见临时文件。这样新的站点源码本身被纳入版本控制，而构建副产物不会污染提交。

### `site/docs/docs/nightly/mkdocs.yml`（新增）

**修改目的**：定义 nightly 版本化文档子站的导航结构，供顶层 mkdocs 通过 monorepo 插件聚合。

**工作逻辑**：`site_name: docs/nightly`，仅启用 `search` 插件。`nav` 组织为：首页 `index.md`；Tables 分组（branching、configuration、evolution、maintenance、partitioning、performance、reliability、schemas）；Spark 分组（getting-started、configuration、ddl、procedures、queries、structured-streaming、writes）；Flink 分组（flink、flink-connector、flink-ddl、flink-queries、flink-writes、flink-actions、flink-configuration）；hive.md；以及指向 Trino/Clickhouse/Presto/Dremio/Starrocks/Athena/EMR/Impala/Doris 等外部引擎文档的链接；Integrations 分组（aws、dell、jdbc、nessie）；API 分组（java-api-quickstart、api、custom-catalog）；Javadoc 指向 `../../javadoc/nightly/`；PyIceberg 指向外部站点。

### `site/docs/index.md`（新增，非版本化首页）

**修改目的**：提供 Iceberg 官网首页内容。

**工作逻辑**：包含带 `intro-bg.webp` 背景的 intro 区块，介绍 Iceberg 是面向 analytic datasets 的开放表格式，并用 termynal 动画展示 Expressive SQL（MERGE INTO）、Schema Evolution（ALTER COLUMN）、Hidden Partitioning、Time Travel and Rollback（FOR VERSION AS OF）、Data Compaction（CALL system.rewrite_data_files）等核心能力，每段都附带"Learn More"链接到对应版本化文档。

### `site/docs/` 下其它非版本化页面（新增，分组说明）

**修改目的**：补齐站点导航所需的全部非版本化页面源文件。

**工作逻辑**：包括 `about.md`、`benchmarks.md`、`blogs.md`（389 行，按时间列出博客摘要与链接）、`catalog.md`、`community.md`、`contribute.md`（397 行，贡献指南）、`fileio.md`、`gcm-stream-spec.md`、`hive-quickstart.md`、`how-to-release.md`（516 行，发版流程）、`multi-engine-support.md`、`puffin-spec.md`（143 行，Puffin 规范）、`releases.md`（777 行，各版本发布说明）、`roadmap.md`、`security.md`、`spark-quickstart.md`（342 行）、`spec.md`（1259 行，Iceberg 表格式规范主体）、`talks.md`、`terms.md`、`vendors.md`、`view-spec.md`（329 行，View 规范）。这些文件此前在独立 docs 仓库中，现迁入主仓随代码同维护。同时新增 `site/docs/assets/images/` 下的 logo、favicon、iceberg-metadata、intro-bg.webp 等图片资源。

### `site/docs/docs/nightly/docs/` 下版本化文档（新增，分组说明）

**修改目的**：提供 nightly 版本下各引擎/集成的完整文档源文件，对应 nightly mkdocs.yml 中 nav 列出的每个页面。

**工作逻辑**：覆盖 Spark 系列（spark-getting-started、spark-configuration、spark-ddl 557 行、spark-procedures 875 行、spark-queries、spark-structured-streaming、spark-writes 466 行）、Flink 系列（flink 398 行、flink-connector、flink-ddl、flink-queries 489 行、flink-writes、flink-actions、flink-configuration）、hive.md（596 行）、Tables 系列（branching、configuration、evolution、maintenance、partitioning、performance、reliability、schemas）、Integrations（aws 657 行、custom-catalog、dell、delta-lake-migration、hive-migration、jdbc、nessie、table-migration）、API（api 256 行、java-api-quickstart 317 行）、index.md，以及对应的 `assets/images/`（audit-branch、historical-snapshot-tag、iceberg-migrateaction/snapshotaction 步骤图、partition-spec-evolution 等示意图）。这些内容与旧 docs 仓库对齐，确保迁移后 nightly 文档完整可用。

## 小结

本提交把基于 MkDocs Material 的完整站点与多版本文档构建体系一次性迁入主仓库 `site/` 目录，使 Iceberg 文档与代码同仓治理、随版本发布版本化文档，是 Iceberg 文档基础设施的一次重大升级。
