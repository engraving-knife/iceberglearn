# 提交 3479：CI: Fix JMH benchmark workflows (#15800)

## 提交信息

- **序号**：3479 / 4088
- **哈希**：4eee56c9834d7271de0d95c56c415e8ef2fb3d88
- **短哈希**：4eee56c983
- **日期**：2026-03-27 20:35:04 -0700
- **作者**：Kevin Liu
- **提交说明**：CI: Fix JMH benchmark workflows (#15800)
- **PR/Issue**：#15800

## 总体目的

修复 JMH 基准测试的 GitHub Actions 工作流。原工作流使用旧的 Spark 3.5 / Scala 2.12 配置以及旧的 gradle 任务路径格式，已不再适应当前项目结构（项目已升级到支持 Spark 4.1 / Scala 2.13）。需要更新工作流输入参数、gradle 命令以及文档示例。

## 如何达成设计目的

1. 在 `jmh-benchmarks.yml`（手动触发的工作流）中，将 `spark_version` 输入从项目模块名格式（如 `iceberg-spark-3.5`）改为纯版本号格式（如 `4.1`），并新增 `scala_version` 输入参数。
2. 更新 gradle 命令，使用 `-DsparkVersions` 和 `-DscalaVersion` 系统属性，并使用完整的模块路径 `:iceberg-spark:iceberg-spark-${SPARK_VERSION}_${SCALA_VERSION}:jmh`。
3. 在 `recurring-jmh-benchmarks.yml`（定期运行的工作流）中，同样更新矩阵配置和 gradle 命令，并新增 `workflow_dispatch` 触发器以支持手动触发。
4. 修正 artifact 名称，从统一的 `benchmark-results` 改为按 benchmark 名称区分的 `benchmark-${{ matrix.benchmark }}`，避免多个 benchmark 结果互相覆盖。
5. 更新 `site/docs/benchmarks.md` 文档中所有示例命令，与新的命令格式保持一致。

## 修改详情

### `.github/workflows/jmh-benchmarks.yml` (+19/-10 lines)

**修改目的**：更新手动触发的 JMH benchmark 工作流以支持新的 Spark/Scala 版本配置。

**工作逻辑**：
- `spark_version` 输入描述从 "The spark project version to use, such as iceberg-spark-3.5" 改为 "The Spark version, such as 4.1"，默认值改为 `4.1`。
- 新增 `scala_version` 输入，默认值 `2.13`。
- matrix job 中将 `SPARK_VERSION` 来源从 `needs.matrix.outputs.spark_version` 改为 `github.event.inputs.spark_version`，新增 `SCALA_VERSION`。
- Run Benchmark 步骤的命令从 `./gradlew :iceberg-spark:${SPARK_VERSION}:jmh ...` 改为 `./gradlew -DsparkVersions=${SPARK_VERSION} -DscalaVersion=${SCALA_VERSION} :iceberg-spark:iceberg-spark-${SPARK_VERSION}_${SCALA_VERSION}:jmh ...`。
- artifact 名称从 `benchmark-results` 改为 `benchmark-${{ matrix.benchmark }}`。

### `.github/workflows/recurring-jmh-benchmarks.yml` (+10/-8 lines)

**修改目的**：更新定期运行的 JMH benchmark 工作流。

**工作逻辑**：
- 新增 `workflow_dispatch` 触发器，支持手动触发。
- 矩阵配置从 `spark_version: ['iceberg-spark-3.5']` 改为 `spark: ['4.1']` 和 `scala: ['2.13']`。
- 移除了 checkout 步骤中不再需要的 `repository` 和 `ref` 输入（这些输入仅在 `workflow_dispatch` 触发时才有意义，定期任务使用默认仓库即可）。
- 更新 gradle 命令格式与 jmh-benchmarks.yml 一致。
- artifact 名称改为按 benchmark 区分。

### `site/docs/benchmarks.md` (+38/-29 lines)

**修改目的**：更新文档中所有 benchmark 运行命令示例，从旧格式（`iceberg-spark-3.5_2.12`）更新为新格式（`-DsparkVersions=4.1 -DscalaVersion=2.13 :iceberg-spark:iceberg-spark-4.1_2.13:jmh`）。

**工作逻辑**：
- 文档开头新增说明 Spark 版本和 Scala 版本输入参数。
- 所有约 19 个 benchmark 示例命令统一更新为新格式。

## 总结

修复 JMH benchmark 工作流以适配当前项目结构（Spark 4.1 / Scala 2.13），主要改动包括：更新输入参数格式、使用新的 gradle 命令格式（带 `-DsparkVersions` 和 `-DscalaVersion`）、按 benchmark 区分 artifact 名称以避免覆盖、为定期任务增加手动触发支持，以及同步更新文档示例。
