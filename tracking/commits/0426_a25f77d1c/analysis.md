# 提交 0426：Revert "Move nightly versioned docs to top-level docs directory (#9578)" (#9589)

## 提交信息

- **序号**：0426
- **哈希**：a25f77d1cd04204efd2bc3ccea5d62f0d34b0d90
- **短哈希**：a25f77d1c
- **日期**：2024 年 1 月 30 日（Tue Jan 30 12:30:27 2024 -0800）
- **作者**：Brian "bits" Olsen <brianolsen87@gmail.com>
- **提交说明**：Revert "Move nightly versioned docs to top-level docs directory (#9578)" (#9589)

  This reverts commit adec50c01ac7d8dfc2c72003305425c4a68f300e.

  Co-authored-by: Fokko Driesprong <fokko@apache.org>
- **PR/Issue**：#9589（撤销 PR #9578，对应原提交 adec50c01ac7d8dfc2c72003305425c4a68f300e）

## 总体目的

这个提交是对提交 `adec50c01`（PR #9578 "Move nightly versioned docs to top-level docs directory"）的完整回滚。原 PR #9578 的目的是把每晚构建的版本化文档从 `site/docs/docs/nightly/` 目录移动到仓库根目录下的 `docs/` 顶层目录，期望让文档源码与最终发布站点的目录结构更接近、便于在 GitHub 上直接浏览。

然而实际执行后发现这种目录布局调整带来了副作用：把 nightly 版本文档放到顶层 `docs/` 后，会与 Iceberg 文档站点的构建脚本（`site/dev/` 下的脚本）和 worktree 管理流程产生冲突。具体来说，`pull_versioned_docs` 脚本通过 `git worktree add docs/docs` 来管理版本化文档的检出，而把文档直接放在 `docs/` 顶层会与 worktree 的目录占位以及 `.gitignore` 中对 `site/docs/docs/` 的忽略规则相互干扰，导致文档构建流程不稳定。因此维护者选择在同一天内撤销这次重构，把文档结构恢复到 #9578 之前的状态（nightly 文档仍存放在 `site/docs/docs/nightly/` 下）。

这种"快速试错并回滚"是 Iceberg 在文档基础设施调整上的常见做法：文档构建链路涉及多个脚本和外部仓库（apache/iceberg 的 `docs` 和 `javadoc` 分支），任何目录变更都需要与 worktree 流程严格对齐，否则会破坏 nightly 发布。回滚本身由原 PR 作者 Brian Olsen 发起，并由 Fokko Driesprong 共同署名，体现了文档维护团队对稳定性的谨慎态度。

## 如何达成设计目的

通过 `git revert` 直接生成反向 diff，把 #9578 中所有文件的移动方向反转：原 PR 删除的 `docs/_index.md`、`docs/aws.md`、`docs/spark-ddl.md` 等顶层文档被重新创建，而 `site/docs/docs/nightly/` 目录下的文件被恢复。同时对 `site/dev/common.sh` 脚本做了针对性补丁（在 `pull_versioned_docs` 中重新加回 `rm -r docs/docs` 和 `clean` 中的 `git restore docs/docs`），以保证 worktree 流程在回滚后的目录结构下仍能正确工作；`.gitignore` 中对 `site/docs/docs/` 的忽略行也恢复为注释形式。

## 修改详情

### .gitignore

**修改目的**：恢复对 nightly 文档目录的忽略规则。

**工作逻辑**：把 `site/docs/docs/` 这一行从启用状态改回注释状态（`#site/docs/docs/`）。原 PR #9578 因为把文档挪到顶层 `docs/`，所以启用了对 `site/docs/docs/` 的忽略；回滚后 nightly 文档重新放回 `site/docs/docs/nightly/`，而这部分由 worktree 管理，不需要在 `.gitignore` 中忽略整个 `site/docs/docs/`，因此恢复为注释。

### docs/_index.md

**修改目的**：恢复被 #9578 删除的顶层文档索引页。

**工作逻辑**：重新创建 `docs/_index.md`（54 行），内容为 Iceberg 文档站点的介绍页，包含 title、menu、weight 等 front matter 以及 "Documentation"、"User experience" 等章节。这是 mkdocs 站点在顶层 `docs/` 目录下的入口文件。

### docs/aws.md, docs/branching-and-tagging.md, docs/configuration.md, docs/dell.md, docs/delta-lake-migration.md, docs/evolution.md, docs/flink-*.md, docs/hive*.md, docs/java-*.md, docs/jdbc.md, docs/maintenance.md, docs/metrics-reporting.md, docs/nessie.md, docs/partitioning.md, docs/performance.md, docs/reliability.md, docs/schemas.md, docs/spark-*.md, docs/table-migration.md

**修改目的**：恢复被 #9578 删除的顶层用户文档。

**工作逻辑**：这一大批 `.md` 文件是 Iceberg 的核心用户文档（Spark/Flink/Hive 使用指南、配置、DDL、查询、写入、分区、演化、维护等）。回滚后它们重新作为顶层 `docs/` 目录下的文档源码存在，与 nightly 版本化文档（`site/docs/docs/nightly/`）分离。每个文件内容都是完整的文档正文，规模从几十行到八九百行不等（如 `docs/spark-procedures.md` 889 行、`docs/aws.md` 663 行）。

### site/dev/common.sh

**修改目的**：修复 worktree 管理脚本以适配回滚后的目录结构。

**工作逻辑**：在 `pull_versioned_docs` 函数中，于 `create_or_update_docs_remote` 之后重新加入 `rm -r docs/docs`，确保在执行 `git worktree add -f docs/docs` 之前先清理掉顶层 `docs/docs` 目录（因为 worktree add 要求目标路径不存在或为空）。同时在 `clean` 函数中，于移除 worktree 之后重新加入 `git restore docs/docs`，用于恢复被 worktree 操作影响的顶层 `docs/docs` 内容。这两处是 #9578 删除掉的逻辑，回滚时一并恢复，确保 nightly 文档拉取与清理流程在旧的目录布局下正常工作。

### site/docs/docs/nightly/docs/api.md, site/docs/docs/nightly/docs/assets/images/*.png, site/docs/docs/nightly/docs/*.md, site/docs/docs/nightly/mkdocs.yml

**修改目的**：把 nightly 版本化文档移回 `site/docs/docs/nightly/` 目录。

**工作逻辑**：这些条目全部是文件移动（rename），从 `docs/docs/` 移回 `site/docs/docs/nightly/`。包括 `api.md`、`aws.md`、`branching.md`、`configuration.md`、`custom-catalog.md`、`dell.md`、`delta-lake-migration.md`、`evolution.md`、`flink-*.md`、`hive*.md`、`index.md`、`java-api-quickstart.md`、`jdbc.md`、`maintenance.md`、`metrics-reporting.md`、`nessie.md`、`partitioning.md`、`performance.md`、`reliability.md`、`schemas.md`、`spark-*.md`、`table-migration.md`、`mkdocs.yml` 以及 `assets/images/` 下的多张 PNG 图片。所有文件内容为 0 改动（纯路径迁移），git 以 rename 形式记录。

## 小结

这是一个纯粹的回滚提交，把 Iceberg 文档的 nightly 版本化目录结构从顶层 `docs/` 恢复到 `site/docs/docs/nightly/`。意义在于：维护文档基础设施的稳定性，避免与 worktree 管理脚本和 `.gitignore` 规则产生冲突。整体模式是"重构 → 发现问题 → 当日回滚"，体现了文档构建链路对目录布局的敏感性，以及维护者在基础设施变更上的快速纠错能力。对本仓库的代码逻辑无任何影响，仅涉及文档与构建脚本。
