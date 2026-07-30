# 提交 0420：Move nightly versioned docs to top-level docs directory (#9578)

## 提交信息

- **序号**：0420
- **哈希**：adec50c01ac7d8dfc2c72003305425c4a68f300e
- **短哈希**：adec50c01
- **日期**：Tue Jan 30 08:57:45 2024 -0800
- **作者**：Brian "bits" Olsen <brianolsen87@gmail.com>
- **提交说明**：Move nightly versioned docs to top-level docs directory (#9578)
- **PR/Issue**：#9578

## 总体目的

Iceberg 的文档系统在本次改动前存在一个尴尬的双轨结构：仓库里同时维护着两套内容高度重叠的 Markdown 文档。一套是位于顶层 `docs/` 目录下的"老文档"（如 `docs/aws.md`、`docs/spark-configuration.md` 等），它们的 frontmatter 带有 `url:` 和 `menu:` 字段，是历史遗留下来的站点构建入口；另一套是位于 `site/docs/docs/nightly/docs/` 下的"nightly 版本文档"（如 `site/docs/docs/nightly/docs/aws.md`），它们是版本化文档系统的源头，frontmatter 更简洁（只有 `title:`），文件命名也做过整理（例如 `branching.md` 取代了老的 `branching-and-tagging.md`，`flink.md` 取代了 `flink-getting-started.md`，`java-api-quickstart.md` 取代了 `java-api.md`/`java-custom-catalog.md` 等）。两套文档并存带来三个问题：内容维护时需要同步两份、贡献者不清楚该改哪一份、站点构建流程要在两者间做切换。

本提交的目的就是消除这套冗余，**把 nightly 版本文档提升为唯一的文档源头**：把 `site/docs/docs/nightly/docs/` 整体迁移到顶层 `docs/docs/`，把 `site/docs/docs/nightly/mkdocs.yml` 迁移到 `docs/mkdocs.yml`，同时删除老 `docs/` 下所有被取代的 Markdown 文件（共 35 个文件、8655 行删除）。这样仓库里只保留一套与版本化文档系统一致的源头文档，`docs/docs/` 既作为 nightly（未发布版本）的文档源，也作为站点构建的统一入口，消除了"两份文档"的认知负担和同步成本。

此外，提交同步调整了 `.gitignore` 和站点构建脚本 `site/dev/common.sh`：把 `site/docs/docs/` 从"注释掉的不忽略"改为"明确忽略"（因为 nightly 源已迁走，`site/docs/docs/` 现在只作为构建产物/工作区目录），并移除了构建脚本里针对 `docs/docs` 的 `rm -r` 和 `git restore` 操作（因为 `docs/docs` 现在是受版本控制的源目录，不能再被脚本删除再恢复）。这是一次文档治理层面的"清场"动作。

## 如何达成设计目的

实现路径是纯文件移动 + 清理，没有任何内容修改（所有迁移文件的 diff 都是 0 行变化，纯 rename）。具体地：(1) 用 `git mv` 把 `site/docs/docs/nightly/docs/` 下的全部 Markdown 和 `assets/` 资源迁移到 `docs/docs/`；(2) 把 `site/docs/docs/nightly/mkdocs.yml` 迁移到 `docs/mkdocs.yml`；(3) `git rm` 删除老 `docs/` 下被取代的 35 个 Markdown 文件；(4) 编辑 `.gitignore`，把 `#site/docs/docs/` 改为 `site/docs/docs/`；(5) 编辑 `site/dev/common.sh`，从 `pull_versioned_docs` 中移除 `rm -r docs/docs`，从 `clean` 中移除 `git restore docs/docs`。整次改动 82 个文件、1 行新增、8655 行删除——新增的 1 行是 `.gitignore` 取消注释带来的净变化。

## 修改详情

### .gitignore

**修改目的**：调整站点构建产物的忽略规则，反映 nightly 文档源已从 `site/docs/docs/` 迁出。

**工作逻辑**：把 `#site/docs/docs/` 这行（原本被注释掉、即 `site/docs/docs/` 不被忽略，因为里面存放着受版本控制的 nightly 文档源）改为 `site/docs/docs/`（取消注释、即现在明确忽略该目录）。这是因为 nightly 文档源已经迁到顶层 `docs/docs/`，`site/docs/docs/` 不再存放受控文件，转而作为站点构建时的工作区/产物目录，理应被忽略。

### docs/docs/* （由 site/docs/docs/nightly/docs/ 迁入）

**修改目的**：把 nightly 版本文档源提升到顶层 `docs/docs/`，作为唯一的文档源头。

**工作逻辑**：这是一组纯 rename 操作（0 行内容变化），涉及约 35 个 Markdown 文件和 `assets/images/` 下的 10 张图片资源。文件从 `site/docs/docs/nightly/docs/<name>.md` 迁移到 `docs/docs/<name>.md`，内容包括 `api.md`、`aws.md`、`branching.md`、`configuration.md`、`custom-catalog.md`、`dell.md`、`delta-lake-migration.md`、`evolution.md`、`flink-actions.md`、`flink-configuration.md`、`flink-connector.md`、`flink-ddl.md`、`flink-queries.md`、`flink-writes.md`、`flink.md`、`hive-migration.md`、`hive.md`、`index.md`、`java-api-quickstart.md`、`jdbc.md`、`maintenance.md`、`metrics-reporting.md`、`nessie.md`、`partitioning.md`、`performance.md`、`reliability.md`、`schemas.md`、`spark-configuration.md`、`spark-ddl.md`、`spark-getting-started.md`、`spark-procedures.md`、`spark-queries.md`、`spark-structured-streaming.md`、`spark-writes.md`、`table-migration.md`，以及 `assets/images/` 下的截图资源（audit-branch、historical-snapshot-tag、iceberg 各类迁移/快照步骤图、partition-spec-evolution 等）。迁移后这些文件成为仓库顶层 `docs/docs/` 下的正式文档源，目录层级比原先的 `site/docs/docs/nightly/docs/` 浅了三层，更直观。

### docs/mkdocs.yml （由 site/docs/docs/nightly/mkdocs.yml 迁入）

**修改目的**：把 nightly 文档对应的 mkdocs 配置文件随同文档源一起迁到顶层 `docs/`。

**工作逻辑**：纯 rename，0 行变化。`mkdocs.yml` 是 mkdocs 站点构建的配置入口，定义了文档导航结构、主题、插件等。把它从 `site/docs/docs/nightly/mkdocs.yml` 迁到 `docs/mkdocs.yml`，与 `docs/docs/` 下的文档内容同级，使 `docs/` 目录成为自包含的文档构建根目录。

### docs/*.md （老文档，被删除）

**修改目的**：删除被 nightly 文档取代的老版本顶层 Markdown 文件，消除冗余。

**工作逻辑**：共删除 35 个文件、8655 行。这些是老的文档源（如 `docs/aws.md`、`docs/spark-configuration.md`、`docs/spark-procedures.md`、`docs/hive.md`、`docs/flink-queries.md` 等），它们的 frontmatter 带有 `url:` 和 `menu:` 字段（用于老站点导航），文件命名也与 nightly 版本不同（如 `docs/branching-and-tagging.md` vs nightly 的 `branching.md`，`docs/java-api.md`+`docs/java-custom-catalog.md` vs nightly 的 `java-api-quickstart.md`，`docs/flink-getting-started.md` vs nightly 的 `flink.md`）。删除后，仓库里不再有"两份文档"的歧义，所有文档维护都指向 `docs/docs/` 这一套。另外还删除了 `docs/_index.md`（老的首页，54 行），由迁入的 `docs/docs/index.md` 接替。

### site/dev/common.sh

**修改目的**：同步调整站点构建脚本，移除针对 `docs/docs` 的删除/恢复操作，因为 `docs/docs` 现在是受版本控制的源目录。

**工作逻辑**：
- 在 `pull_versioned_docs()` 函数中，原本在 `git worktree add -f docs/docs "${REMOTE}/docs"` 之前有一行 `rm -r docs/docs`，用于清空 `docs/docs` 路径以便添加工作区。由于 `docs/docs` 现在是受控的文档源目录，`rm -r` 会误删源文件，因此整段（含空行）删除。后续的 `git worktree add -f docs/docs` 保留不变（`-f` 强制标志可处理路径占用情况，且此处工作区机制在后续构建流程中处理版本化文档）。
- 在 `clean()` 函数中，原本在 `git worktree remove docs/docs` 之后有一行 `git restore docs/docs`，用于把 `docs/docs` 恢复到受控状态。由于 `docs/docs` 现在是受控源、且 `rm -r docs/docs` 已被移除，`git restore` 不再有对应的需要恢复的删除动作，因此删除该行。`clean` 函数其余部分（`rm -rf docs/docs/latest`、`git worktree remove docs/docs`、`git worktree remove docs/javadoc`、`rm -rf site/`）保持不变。

## 小结

这是一次文档治理层面的"去冗余、定源头"重构。通过把 nightly 版本文档从深层路径 `site/docs/docs/nightly/` 提升到顶层 `docs/`，并删除老的双轨文档，确立了 `docs/docs/` 作为唯一文档源头的地位，降低了维护成本和贡献者认知负担。改动本身是纯文件移动 + 删除，没有内容变更（8655 行删除全是被取代的老文件），风险极低；配套的 `.gitignore` 和构建脚本调整确保了站点构建流程与新结构一致。这种"先统一源头、再清理冗余"的模式是大型项目文档演进的典型操作，为后续文档版本化发布流程的简化打下了基础。
