# 提交 0059：Infra: Cleanup labeler.yml (#8795)

## 提交信息

- **序号**：0059 / 4088
- **哈希**：96d2fa77f2614fd78414c518e3c0695834168c43
- **短哈希**：96d2fa77f
- **日期**：2023-10-16 13:10:35 +0530
- **作者**：Ajantha Bhat
- **提交说明**：Infra: Cleanup labeler.yml (#8795)
- **PR/Issue**：#8795

## 总体目的

这是一个仓库基础设施（Infra）维护提交，由人工提交（非 Dependabot），目的是清理 GitHub Pull Request Labeler 的配置文件 [.github/labeler.yml](file:///Users/fengxiaohang/trae/iceberglearn/.github/labeler.yml)，使其准确反映 Iceberg 当前的模块目录结构。

GitHub Labeler（https://github.com/marketplace/actions/labeler）是一个根据 PR 改动文件路径自动打标签的 GitHub Action。Iceberg 在 `labeler.yml` 中为每个标签配置了一组文件路径 glob，例如 `SPARK` 标签对应 `spark/**/*` 等路径，这样 PR 改动了哪个模块就会被自动打上对应的模块标签，方便维护者分类审查与 CI 路由。

随着 Iceberg 模块结构的演进，配置中积累了若干"死规则"——指向已经不存在的目录的路径。例如历史上 Iceberg 曾有 `spark2/`、`spark3/`、`spark3-extensions/`、`spark-runtime/`、`flink-runtime/` 等独立目录，但在 PR #3256 "Build: Move Spark version modules under spark directory" 等重构中，Spark 各版本模块已被统一收敛到 `spark/` 目录下，Flink 也类似收敛到 `flink/` 目录下。这些旧目录早已不存在，对应的 labeler 规则永远匹配不到任何文件，成为无效噪音。与此同时，较新的 `hive3-orc-bundle/` 模块（Hive 3 的 ORC bundle 模块）尚未被加入 HIVE 标签的路径列表，导致改动该模块的 PR 不会被自动打上 HIVE 标签。

本次提交一次性解决这两个问题：删除 5 条失效的 SPARK/FLINK 路径规则，并为 HIVE 标签补上 `hive3-orc-bundle/**/*` 路径。这对 Iceberg 演进的意义在于保持基础设施配置与代码结构同步，确保自动化标签机制持续有效，避免维护者因标签失准而漏看或误分类 PR。

## 如何达成设计目的

通过对 `labeler.yml` 做"删旧补新"的最小改动来达成目的：在 HIVE 标签下新增一行 `hive3-orc-bundle/**/*`；在 SPARK 标签下删除 `spark-runtime/**/*`、`spark2/**/*`、`spark3/**/*`、`spark3-extensions/**/*` 四行，只保留 `spark/**/*`；在 FLINK 标签下删除 `flink-runtime/**/*`，只保留 `flink/**/*`。由于 Spark/Flink 各版本模块已统一收纳在 `spark/` 与 `flink/` 目录下，保留的单条 `spark/**/*`、`flink/**/*` 规则已经能覆盖所有相关 PR，被删除的规则本就是冗余且失效的。

## 修改详情

### `.github/labeler.yml`

**修改目的**：让 labeler 配置与当前模块目录结构一致——补上漏配的 `hive3-orc-bundle`，删除指向已不存在目录的失效规则。

**工作逻辑**：具体改动有三处：

1. **HIVE 标签新增 `hive3-orc-bundle/**/*`**：Hive 3 的 ORC bundle 模块（`hive3-orc-bundle/`）是 HIVE 模块族的一部分，但此前未列入 HIVE 标签路径。补上后，任何改动该模块的 PR 都会被自动打上 HIVE 标签，便于 Hive 维护者及时发现与审查。

2. **SPARK 标签精简为仅 `spark/**/*`**：删除 `spark-runtime/**/*`、`spark2/**/*`、`spark3/**/*`、`spark3-extensions/**/*` 四条规则。这些目录在 Spark 版本模块统一收敛到 `spark/` 目录的重构（PR #3256 等）之后已不存在，规则早已失效。保留的 `spark/**/*` 已能匹配所有 Spark 相关 PR。

3. **FLINK 标签精简为仅 `flink/**/*`**：删除 `flink-runtime/**/*`。该目录同样在 Flink 模块收敛重构后已不存在，规则失效。保留的 `flink/**/*` 已能覆盖所有 Flink 相关 PR。

整体净效果：1 行新增、5 行删除，配置从 6 行（HIVE+SPARK+FLINK 段）收敛为更简洁的 4 行，且每条规则都对应真实存在的目录。

## 小结

通过补配 `hive3-orc-bundle` 并删除指向已废弃 `spark2/spark3/spark3-extensions/spark-runtime/flink-runtime` 目录的失效规则，使 GitHub Labeler 配置重新与 Iceberg 当前模块结构对齐，保证 PR 自动标签机制持续有效。
