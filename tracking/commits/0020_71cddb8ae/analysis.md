# 提交 0020：Python: Remove python directory and references (#8695)

## 提交信息

- **序号**：0020 / 4088
- **哈希**：71cddb8aeea20d32e3e97bbecd93c52cb22ad959
- **短哈希**：71cddb8aa
- **日期**：2023-10-09 11:43:15 +0530
- **作者**：Ajantha Bhat
- **提交说明**：Python: Remove python directory and references (#8695)
- **PR/Issue**：#8695

## 总体目的

这个提交从 Apache Iceberg 主仓库中整体移除了 `python/` 目录及其所有相关引用，涉及 175 个文件、约 17.25 万行删除，是 Iceberg 项目在仓库治理上的一次重大结构调整。

背景：Iceberg 此前是一个"多语言单仓库"项目，Java 实现（含 Spark/Flink/Hive 等集成）与 Python 实现（`pyiceberg`）共存于同一个 git 仓库的 `python/` 目录下。随着 `pyiceberg` 的快速发展和社区规模扩大，Python 子项目在发布节奏、依赖管理、CI/CD 流程、贡献者流程上与 Java 主项目逐渐产生差异，单仓库模式带来了一些摩擦：Java 侧的 PR 会触发不必要的 Python CI 路径变更、版本发布需要协调两种语言、Python 文档站点与 Java 文档分开维护等。Iceberg 社区决定让 `pyiceberg` 迁出主仓库，作为独立项目 `apache/iceberg-python` 单独演进（这也是当时 ASF 多语言项目治理的常见做法，与 Maven/Java 主仓库解耦）。

本提交正是这一拆分的主仓库侧落地动作：把 `python/` 目录整体删除，并清理所有指向它的工程化配置（CI 工作流、dependabot、labeler、gitignore、gitattributes、README 徽章等），让主仓库回归为以 Java 为核心的单一语言仓库。Python 实现本身并未消失，而是迁移到了独立仓库继续维护。

## 如何达成设计目的

改动可分为两大类：一是直接删除整个 `python/` 目录（含 pyiceberg 源码、测试、vendored thrift stub、mkdocs 文档、构建/开发脚本、许可证文件等），二是清理仓库根目录及 `.github/` 下所有引用 `python/` 或 Python CI 的工程配置，使主仓库不再保留任何 Python 相关入口。所有改动都是删除（无新增代码），属于纯删除式重构。

## 修改详情

### `python/` 目录整体删除

**修改目的**：移除 Iceberg 主仓库中的 Python 实现源码与配套文件。

**工作逻辑**：删除范围覆盖以下子集（按功能分组）：

- **pyiceberg 核心源码**：`python/pyiceberg/` 下全部模块，包括 `avro/`（含 Cython 加速的 `decoder_fast.pyx`、codecs）、`catalog/`（rest/glue/hive/dynamodb/sql/noop）、`cli/`、`expressions/`、`io/`（fsspec、pyarrow）、`table/`、`utils/`，以及顶层 `schema.py`、`types.py`、`transforms.py`、`manifest.py`、`serializers.py`、`conversions.py`、`exceptions.py` 等核心模块。
- **测试套件**：`python/tests/` 下全部测试（avro/catalog/cli/expressions/io/table/utils 各子目录及顶层测试文件）和 `python/tests/conftest.py`。
- **vendored 依赖**：`python/vendor/` 下的 `fb303/` 与 `hive_metastore/`（Thrift 生成的 Python stub，体量巨大，`ThriftHiveMetastore.py` 单文件约 7.3 万行）。
- **文档站点**：`python/mkdocs/` 整套 mkdocs 配置与文档（api.md、cli.md、configuration.md、contributing.md、how-to-release.md、verify-release.md、feature-support.md、index.md、SUMMARY.md、gen_doc_stubs.py、mkdocs.yml、requirements.txt）。
- **构建与开发工具**：`python/pyproject.toml`、`python/poetry.lock`、`python/MANIFEST.in`、`python/Makefile`、`python/build-module.py`、`python/pylintrc`、`python/.pre-commit-config.yaml`。
- **开发环境脚本**：`python/dev/` 下的 Dockerfile、docker-compose 系列文件（azurite/gcs-server/integration/minio）、entrypoint.sh、provision.py、run-*.sh、spark-defaults.conf、check-license、.rat-excludes。
- **许可证与说明**：`python/LICENSE`、`python/NOTICE`、`python/README.md`。

### `.github/workflows/python-ci.yml`、`python-ci-docs.yml`、`python-integration.yml`、`python-release.yml`

**修改目的**：删除所有 Python 专属的 CI 工作流，主仓库不再运行 Python CI。

**工作逻辑**：这 4 个工作流文件被整体删除（合计约 260 行），分别对应 Python 的常规 CI、文档 CI、集成测试 CI、发布流程。删除后主仓库的 GitHub Actions 只保留 Java/Spark/Flink/Hive 等相关 CI。

### `.github/workflows/spark-ci.yml`、`flink-ci.yml`、`hive-ci.yml`、`java-ci.yml`、`delta-conversion-ci.yml`

**修改目的**：从其他 CI 工作流的 `paths-ignore` 列表中移除 `python/**` 和 `python-ci.yml` 引用。

**工作逻辑**：这些 Java 侧 CI 工作流原本在 `pull_request.paths-ignore` 中列出了 `python/**` 和 `.github/workflows/python-ci.yml`，目的是当仅 Python 改动时不触发 Java CI。本提交把这两类条目从各工作流的 `paths-ignore` 中删掉（每个文件删 2 行）。由于 `python/` 目录和 `python-ci.yml` 已不存在，保留这些忽略项已无意义，删除后配置更干净。

### `.github/dependabot.yml`

**修改目的**：移除针对 `python/` 的 pip 依赖 Dependabot 配置。

**工作逻辑**：删除原配置中 `package-ecosystem: "pip"`、`directory: "/python/"`、每周日检查、`open-pull-requests-limit: 5` 的整块配置（6 行），保留 github-actions 等其他生态的 Dependabot 配置。

### `.github/labeler.yml`

**修改目的**：移除 `PYTHON` 标签的自动归类规则。

**工作逻辑**：删除原配置中的 `PYTHON:` 块及其 `python/**/*` 规则（2 行）。该规则用于 PR 自动打标签机器人根据改动路径打 `PYTHON` 标签，目录删除后该规则失效。

### `.gitattributes`

**修改目的**：移除将 `python/` 目录从源码发布包中排除的 `export-ignore` 规则。

**工作逻辑**：删除 `/python export-ignore` 和 `/python/** export-ignore` 两行。这两条规则原用于在 `git archive` 生成源码包时排除 Python 目录，目录删除后无需保留。

### `.gitignore`

**修改目的**：移除 Python 相关的忽略规则。

**工作逻辑**：删除 `.gitignore` 末尾的 Python 专属忽略段（9 行），包括 `python/.mypy_cache/`、`python/htmlcov`、`python/coverage.xml`、`python/pyiceberg/avro/decoder_fast.c`、`python/pyiceberg/avro/*.html`、`python/pyiceberg/avro/*.so` 等。这些规则是针对 pyiceberg 构建产物（mypy 缓存、覆盖率、Cython 编译产物）的，目录删除后失效。

### `README.md`

**修改目的**：移除 README 中的 Python CI 徽章。

**工作逻辑**：删除指向 `python-ci.yml` 的 GitHub Actions 徽章行 `[![](https://github.com/apache/iceberg/actions/workflows/python-ci.yml/badge.svg)](...)`（1 行）。删除后 README 顶部只保留 Java CI 徽章和 Slack 徽章。

## 小结

该提交从 Iceberg 主仓库整体删除 `python/` 目录及全部相关工程化引用，标志着 `pyiceberg` 迁出主仓库、作为独立项目（`apache/iceberg-python`）演进，使 Iceberg 主仓库回归为以 Java 为核心的单一语言仓库，是一次重要的仓库治理调整。
