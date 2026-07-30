# 提交 2614：Build: Add Docs Build CI (#14025)

## 提交信息

- **序号**：2614 / 4088
- **哈希**：8871bbcf4ffce83be7d1be8d75bf06e5ce7b36e4
- **短哈希**：8871bbcf4
- **日期**：2025-09-08 19:59:18 +0200
- **作者**：Manu Zhang
- **提交说明**：Build: Add Docs Build CI
- **PR/Issue**：#14025

## 总体目的

Iceberg 仓库中存在多个模块的 CI 工作流（Java、Spark、Flink、Hive、Delta、Kafka Connect 等），但此前没有一个专门的 CI 用于验证文档站点（mkdocs）能否成功构建。这导致文档相关的改动（如 mkdocs 依赖升级、文档结构变更）可能在合并后才暴露构建失败，例如前一个提交（2609）就是 revert 了一次导致文档构建出问题的依赖升级。

本提交新增一个 `Docs Build CI` 工作流，专门在 PR 修改了 `docs/`、`site/`、`format/` 或该工作流自身时触发，运行 `make build` 构建文档站点，确保文档改动不会破坏构建。同时，将其余各模块 CI 的 `paths-ignore` 列表加入新的 `docs-ci.yml`，避免文档改动重复触发这些模块 CI，节省 CI 资源。

## 如何达成设计目的

1. **新增 `docs-ci.yml` 工作流**：定义在 PR 触发条件为 `docs/**`、`site/**`、`format/**`、`.github/workflows/docs-ci.yml` 路径变更时运行；job 使用 `ubuntu-latest`，checkout 代码后 setup Python 3.x，在 `./site` 目录执行 `make build` 构建 mkdocs 文档。
2. **更新其余 6 个模块 CI 的 `paths-ignore`**：在 delta-conversion、flink、hive、java、kafka-connect、spark 各 CI 的 pull_request `paths-ignore` 中加入 `.github/workflows/docs-ci.yml`，使文档工作流文件本身的变更不会触发这些模块 CI（与已有的对其他 CI 文件的 ignore 一致）。

## 修改详情

### `.github/workflows/docs-ci.yml` (新建, +38 lines)

**修改目的**：新增文档构建 CI 工作流。

**工作逻辑**：
- 触发条件：`pull_request`，仅当 `docs/**`、`site/**`、`format/**`、`.github/workflows/docs-ci.yml` 路径有变更时触发。
- Job `build-docs` 运行在 `ubuntu-latest`。
- 步骤：`actions/checkout@v4` 拉取代码；`actions/setup-python@v5` 设置 Python 3.x；在 `working-directory: ./site` 下执行 `make build`，即调用 site 目录的 Makefile 构建 mkdocs 文档站点。若构建失败则 CI 失败，阻止合并。

### `.github/workflows/delta-conversion-ci.yml` (+1 line)

**修改目的**：在 paths-ignore 中加入 docs-ci.yml。

**工作逻辑**：在 pull_request 的 `paths-ignore` 列表中新增 `.github/workflows/docs-ci.yml`，使文档 CI 工作流文件变更不触发 delta-conversion CI。

### `.github/workflows/flink-ci.yml` (+1 line)

**修改目的**：同上，加入 docs-ci.yml 到 paths-ignore。

### `.github/workflows/hive-ci.yml` (+1 line)

**修改目的**：同上，加入 docs-ci.yml 到 paths-ignore。

### `.github/workflows/java-ci.yml` (+1 line)

**修改目的**：同上，加入 docs-ci.yml 到 paths-ignore。

### `.github/workflows/kafka-connect-ci.yml` (+1 line)

**修改目的**：同上，加入 docs-ci.yml 到 paths-ignore。

### `.github/workflows/spark-ci.yml` (+1 line)

**修改目的**：同上，加入 docs-ci.yml 到 paths-ignore。

## 总结

本提交新增了专门的文档构建 CI，填补了文档站点缺少自动化构建验证的空白，有助于在 PR 阶段及时发现文档构建问题（如依赖升级导致的回归）。同时通过更新各模块 CI 的 ignore 列表，避免文档变更不必要地触发模块 CI，优化 CI 资源使用。这与前一个 revert 提交（2609）形成呼应——有了文档 CI 后，类似问题可在合并前被拦截。
