# 提交 0452：Remove nightly and add .asf.yaml (#9622)

## 提交信息

- **序号**：0452
- **完整哈希**：aff5b39a7dddd22790b6ba47f514860c53e33c00
- **短哈希**：aff5b39a7
- **日期**：2024-02-02 15:04:40 -0800
- **作者**：Brian "bits" Olsen <brianolsen87@gmail.com>
- **提交说明**：Remove nightly and add .asf.yaml (#9622)
- **关联 PR**：#9622
- **修改文件**：52 个，共 20 行新增、8357 行删除

## 总体目的

本提交针对 Iceberg 官网（site/）构建流程做了一次较大的清理与重整。在改造前，仓库内 `site/docs/docs/nightly/` 目录下维护了一整套"nightly"版本的文档源（包括 mkdocs.yml、api.md、aws.md、spark-*.md、flink-*.md、hive.md 等数十个 Markdown 与图片资源），用于在网站上发布"最新主干"文档。这套 nightly 文档与按版本拉取（1.4.0、1.3.1 等）的版本化文档并存，既造成内容维护重复，也使构建脚本（`site/dev/*.sh`）需要额外处理 nightly 目录。

社区决定移除 nightly 这套独立维护的文档源，转而通过 `pull_versioned_docs` 从远端 `iceberg_docs` 仓库的 `docs` 分支按版本拉取文档，让"最新版本"自然充当主干文档，避免双轨维护。本提交正是该决策的落地：删除整个 `site/docs/docs/nightly/` 目录（含 mkdocs.yml 与全部 md/图片），同时调整构建脚本与 `.gitignore`，并新增 `.asf.yaml` 处理逻辑以适配 ASF 项目站点托管规范。

`.asf.yaml` 是 Apache 软件基金会用于配置项目仓库行为（包括网站发布、GitHub 仓库元数据等）的标准化文件。本提交在构建流程中通过 `git show "${REMOTE}/main:../.asf.yaml" > docs/.asf.yaml` 从远端 main 分支取出该文件并放入 `docs/` 目录，使其随构建产物一起部署到 `asf-site` 分支，从而让 ASF 基础设施能够正确识别与发布站点。同时 `site/mkdocs.yml` 增加 `exclude_docs` 配置，确保 `.asf.yaml` 不被 mkdocs 当作文档页面处理，但又能保留在产物中。

附带改动还包括：`docs/mkdocs.yml` 在导航中新增 IcebergRust 子项目链接，体现 Rust 客户端项目的正式上线；`site/dev/deploy.sh` 把 `--remote-branch asf-site` 改为 `--remote-branch=asf-site`（等价但更规范的等号写法）；`site/dev/common.sh` 把 `REMOTE` 变量从局部改为 `export`，使其能被 `setup_env.sh` 中的 `git show "${REMOTE}/main:..."` 引用。

## 如何达成设计目的

实现路径分两步：第一步是删除 nightly 文档源（`site/docs/docs/nightly/` 整目录），并从 `site/dev/common.sh` 的 `pull_versioned_docs` 中移除 `rm -r docs/docs` 这一行（因为不再需要在拉取前清掉 nightly 目录），同时调整 `clean` 函数，把原先 `git restore docs/docs` 改为 `rm -f docs/.asf.yaml`，反映清理对象由版本化文档目录变为 `.asf.yaml` 文件；第二步是在 `site/dev/setup_env.sh` 末尾追加 `git show "${REMOTE}/main:../.asf.yaml" > docs/.asf.yaml` 把 `.asf.yaml` 拉进构建目录，并在 `site/mkdocs.yml` 用 `exclude_docs` 让 mkdocs 跳过该文件，同时在 `.gitignore` 中加入 `site/docs/docs/` 与 `site/docs/.asf.yaml`，避免本地构建产物被提交回仓库。`site/README.md` 同步更新目录结构示意图，展示新生成的 `.asf.yaml` 文件位置。

## 修改详情

### .gitignore

**修改目的**：更新网站构建产物的忽略规则，反映 nightly 目录被移除与 `.asf.yaml` 加入。

**工作逻辑**：原先注释掉的 `#site/docs/docs/` 改为启用的 `site/docs/docs/`（因为版本化文档现在由 worktree 拉取，不应入库），新增 `site/docs/.asf.yaml`（构建时生成的 ASF 配置文件不入库），保留 `site/docs/javadoc/`。

### docs/mkdocs.yml

**修改目的**：在主文档导航中新增 IcebergRust 子项目入口。

**工作逻辑**：在 nav 末尾追加 `IcebergRust: https://rust.iceberg.apache.org/`，与已有的 PyIceberg 入口并列，指向 Rust 客户端项目的独立站点。

### site/README.md

**修改目的**：更新网站构建说明，反映新的目录结构（含 `.asf.yaml`）。

**工作逻辑**：把 "This step will generate the following layout" 改为 "This step will generate the staged source code which blends into the original source code above"，更准确描述生成产物与源码混合的关系；目录树示意图中把 javadoc 与新出现的 `.asf.yaml` 调整为同级条目，明确 `.asf.yaml` 位于 `site/docs/` 下。

### site/dev/common.sh

**修改目的**：调整构建脚本以适配 nightly 移除与 `.asf.yaml` 引入。

**工作逻辑**：
- `REMOTE="iceberg_docs"` 改为 `export REMOTE="iceberg_docs"`，使该变量能被 `setup_env.sh` 中的 `git show "${REMOTE}/main:..."` 在子 shell 中引用。
- `pull_versioned_docs` 中删除 `rm -r docs/docs` 一行。原先这行用于在拉取版本化文档前清掉 nightly 目录，nightly 移除后不再需要。
- `clean` 函数中把 `git restore docs/docs` 改为 `rm -f docs/.asf.yaml &> /dev/null`。原 `git restore` 用于还原被 worktree 占用的 `docs/docs` 目录，新逻辑改为直接删除构建时生成的 `.asf.yaml` 文件。
- 其余 worktree 操作（`git worktree remove docs/docs`、`git worktree remove docs/javadoc`）保持不变。
- 删除一处空行，使脚本更紧凑。

### site/dev/deploy.sh

**修改目的**：规范化 `mkdocs gh-deploy` 的 `--remote-branch` 参数写法。

**工作逻辑**：`--remote-branch asf-site` 改为 `--remote-branch=asf-site`，两者等价，等号写法更明确，避免参数解析歧义。

### site/dev/setup_env.sh

**修改目的**：在环境搭建流程末尾生成 `.asf.yaml` 文件。

**工作逻辑**：在 `source dev/common.sh` 后加一空行；在 `pull_versioned_docs` 之后追加 `git show "${REMOTE}/main:../.asf.yaml" > docs/.asf.yaml`。该命令从 `iceberg_docs` 远端的 main 分支取出仓库根级的 `.asf.yaml`（路径 `../.asf.yaml` 是因为 worktree 检出在 `docs/docs`，相对其父级回溯），写入 `docs/.asf.yaml`，使其随 mkdocs 构建产物一起部署到 `asf-site` 分支。

### site/docs/docs/nightly/mkdocs.yml（已删除）

**修改目的**：移除 nightly 文档的 mkdocs 配置。

**工作逻辑**：原文件 70 行，定义了 nightly 站点的 `site_name: docs/nightly`、search 插件、以及完整的 nav 结构（Tables/Spark/Flink/Integrations/API 等分类，外加 Trino/Clickhouse/Presto 等外部引擎链接与 Javadoc/PyIceberg 入口）。删除后，nightly 站点不再独立构建，由版本化文档的最新版本承担主干文档角色。

### site/docs/docs/nightly/docs/*.md（已删除，约 38 个 Markdown 文件）

**修改目的**：移除 nightly 文档源全部内容。

**工作逻辑**：删除包括 `api.md`（256 行）、`aws.md`（657 行）、`branching.md`、`configuration.md`、`custom-catalog.md`、`dell.md`、`delta-lake-migration.md`、`evolution.md`、`flink*.md`（多个，含 flink-queries.md 489 行、flink.md 398 行等）、`hive.md`（596 行）、`hive-migration.md`、`index.md`、`java-api-quickstart.md`（317 行）、`jdbc.md`、`maintenance.md`、`metrics-reporting.md`、`nessie.md`、`partitioning.md`、`performance.md`、`reliability.md`、`schemas.md`、`spark-*.md`（多个，含 spark-procedures.md 875 行、spark-ddl.md 557 行、spark-writes.md 466 行、spark-queries.md 457 行等）、`table-migration.md` 等全部 Markdown 文档。这些文档的内容此后由版本化文档仓库（`iceberg_docs` 远端）按版本提供，避免在主仓库内双轨维护。

### site/docs/docs/nightly/docs/assets/images/*.png（已删除，9 个图片）

**修改目的**：移除 nightly 文档引用的图片资源。

**工作逻辑**：删除 `audit-branch.png`、`historical-snapshot-tag.png`、`iceberg-in-place-metadata-migration.png`、`iceberg-migrateaction-step1/2/3.png`、`iceberg-snapshotaction-step1/2.png`、`partition-spec-evolution.png` 等图片。这些图片原本用于 audit、migration、snapshot action、partition spec evolution 等章节的图示，随 nightly 文档一并移除，由版本化文档仓库自行管理。

### site/mkdocs.yml

**修改目的**：在站点级 mkdocs 配置中排除 `.asf.yaml` 不被当作文档页面。

**工作逻辑**：在 `extra` 段之后新增 `exclude_docs: |` 块，内容为 `!.asf.yaml`。mkdocs 的 `exclude_docs` 通过 gitignore 风格的模式排除文件，`!.asf.yaml` 表示"不要忽略 `.asf.yaml`"——但实际上结合 `exclude_docs` 的语义，这是把 `.asf.yaml` 排除在 mkdocs 文档收集之外，使其不参与 nav 构建与页面生成，但仍保留在产物目录中以供 ASF 基础设施使用。

## 小结

本提交是 Iceberg 官网构建流程的一次结构性清理：删除整个 `site/docs/docs/nightly/` 目录（约 8300 行 Markdown 与图片），终止 nightly 文档在主仓库内的独立维护，转而由版本化文档仓库按版本提供；同时引入 `.asf.yaml` 处理逻辑，通过 `setup_env.sh` 从远端 main 分支取该文件并放入构建目录，配合 `site/mkdocs.yml` 的 `exclude_docs` 与 `.gitignore` 规则，使其既不污染 mkdocs 文档收集，又能随 `asf-site` 分支部署以适配 ASF 站点托管规范。配套改动包括脚本变量 export 化、`clean` 函数调整、deploy 参数规范化、README 目录树更新，以及在主 docs 导航中新增 IcebergRust 入口。修改性质为基础设施/文档构建流程调整，不涉及业务代码。
