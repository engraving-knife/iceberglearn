# 提交 3265：infra: set github actions max-parallel to 15 (#15339)

## 提交信息

- **序号**：3265 / 4088
- **哈希**：ebaafdeb35ed892e95f1d9b4666a18d393c95876
- **短哈希**：ebaafdeb3
- **日期**：2026-02-16
- **作者**：Kevin Liu
- **提交说明**：infra: set github actions max-parallel to 15 (#15339)
- **PR/Issue**：#15339

## 总体目的

Apache 软件基金会（ASF）基础设施团队对其托管的 GitHub 仓库制定了 [GitHub Actions 政策](https://infra.apache.org/github-actions-policy.html)（max-parallel 说明见 https://s.apache.org/max-parallel ），对工作流的作业并发度作出明确要求：所有工作流**必须（MUST）**将作业并发度控制在不超过 20（即一个工作流在同一时刻跨所有 matrix 运行的作业数不得超过 20，以防共享 runner 资源被耗尽）；并**建议（SHOULD）**将并发度控制在不超过 15——政策原文指出"20 是上限，并不意味着你应当追求 20"。为执行该政策，ASF 基础设施团队提供了扫描器（`infrastructure-gha-workflow-scanner`），会检查各仓库中使用 matrix 策略的作业是否设置了 `strategy.max-parallel`，未设置的会被标记为策略违规。

Iceberg 仓库的 CI 工作流大量使用 matrix 策略来对多 JVM（17/21）、多 Spark 版本（3.4/3.5/4.0/4.1）、多 Flink 版本（1.20/2.0/2.1）、多操作系统（ubuntu/macos）以及动态 benchmark 列表等组合并行构建与测试。这些 matrix 作业原先均未显式设置 `max-parallel`，意味着 GitHub 会按默认上限（最多 20 个 matrix 作业同时运行）调度。这既可能触发 ASF 政策扫描器的违规告警，也可能在多个 CI 工作流同时触发（例如一个 PR 同时跑 spark-ci、flink-ci、java-ci 等）时让 Iceberg 占用过多共享 runner，影响其他 Apache 项目的 CI 排期。

本次提交为 9 个 CI 工作流文件中共 12 个使用 matrix 策略的作业统一添加 `max-parallel: 15`，使每个 matrix 策略块在同一时刻最多并行运行 15 个作业组合，超出部分排队等待。该值恰好取 ASF 政策建议的上限 15，既满足"必须 ≤20"的硬性要求，也符合"建议 ≤15"的指导，从而消除扫描器违规、合理约束 Iceberg 对 ASF 共享 GitHub Actions runner 的占用。这是 Apache 各仓库（airflow 设 20、iceberg-cpp 设 15、daffodil 设 ≤15 等）统一合规整改的一部分，Iceberg 系列仓库中作者此前已对 iceberg-cpp 做过同类修改（PR #565）。

## 如何达成设计目的

逐一在 9 个工作流 YAML 文件里、每个含 `strategy: matrix:` 的作业的 `strategy` 块中加入 `max-parallel: 15`（置于 `matrix` 之前，与既有的 `fail-fast: false` 并列）。改动纯增量、无删除，且 `max-parallel` 只影响并发上限——超出 15 的 matrix 组合不会被取消，只是排队依次运行，因此不改变测试覆盖范围与结果正确性，仅调整资源调度节奏。

## 修改详情

### `.github/workflows/delta-conversion-ci.yml` (+2 lines)

**修改目的**：为 Delta 转换模块 CI 的两个 matrix 作业设置并发上限。

**工作逻辑**：
在 `delta-conversion-scala-2-12-tests` 与 `delta-conversion-scala-2-13-tests` 两个作业的 `strategy` 块（matrix 为 `jvm: [17, 21]`，共 2 个组合）中加入 `max-parallel: 15`。该 matrix 仅 2 个组合，15 的上限实际不限制其并发，但满足 ASF 扫描器对"matrix 作业必须显式设置 max-parallel"的要求。

### `.github/workflows/docs-ci.yml` (+1 line)

**修改目的**：为文档构建 CI 的 matrix 作业设置并发上限。

**工作逻辑**：
在 `build-docs` 作业的 `strategy` 块（matrix 为 `os: [ubuntu-latest, macos-latest]`，共 2 个组合）中加入 `max-parallel: 15`。同样地，matrix 组合数远小于 15，该设置主要服务于合规性而非实际限流。

### `.github/workflows/flink-ci.yml` (+1 line)

**修改目的**：为 Flink CI 的 matrix 作业设置并发上限。

**工作逻辑**：
在 `flink-scala-2-12-tests` 作业的 `strategy` 块（matrix 为 `jvm: [17, 21]` × `flink: ['1.20', '2.0', '2.1']`，共 6 个组合）中加入 `max-parallel: 15`。6 个组合低于 15，上限不构成实际限制，但满足合规要求。

### `.github/workflows/hive-ci.yml` (+1 line)

**修改目的**：为 Hive CI 的 matrix 作业设置并发上限。

**工作逻辑**：
在 `hive2-tests` 作业的 `strategy` 块（matrix 为 `jvm: [17, 21]`，共 2 个组合）中加入 `max-parallel: 15`。

### `.github/workflows/java-ci.yml` (+3 lines)

**修改目的**：为 Java 核心 CI 的三个 matrix 作业设置并发上限。

**工作逻辑**：
在 `core-tests`、`build-checks`、`build-javadoc` 三个作业的 `strategy` 块（均为 `jvm: [17, 21]`，各 2 个组合）中分别加入 `max-parallel: 15`。这是本次改动中涉及作业数最多的工作流。

### `.github/workflows/jmh-benchmarks.yml` (+1 line)

**修改目的**：为 JMH 基准测试工作流的 matrix 作业设置并发上限。

**工作逻辑**：
在 benchmark 作业的 `strategy` 块中加入 `max-parallel: 15`，与既有的 `fail-fast: false` 并列（`fail-fast: false` 保证某个 benchmark 失败不取消其余 benchmark）。该 matrix 的 `benchmark` 列表由 `fromJson(needs.matrix.outputs.matrix)` 动态生成，组合数可能较多，因此 15 的上限在此处有实际限流意义：当动态 benchmark 数量超过 15 时，多余的会排队运行，避免一次性占满 runner。

### `.github/workflows/kafka-connect-ci.yml` (+1 line)

**修改目的**：为 Kafka Connect CI 的 matrix 作业设置并发上限。

**工作逻辑**：
在 `kafka-connect-tests` 作业的 `strategy` 块（matrix 为 `jvm: [17, 21]`，共 2 个组合）中加入 `max-parallel: 15`。

### `.github/workflows/recurring-jmh-benchmarks.yml` (+1 line)

**修改目的**：为周期性 JMH 基准测试工作流的 matrix 作业设置并发上限。

**工作逻辑**：
在 benchmark 作业的 `strategy` 块中加入 `max-parallel: 15`，与既有的 `fail-fast: false` 并列。该 matrix 显式列出了多个 benchmark 名称（如 `SparkParquetReadersFlatDataBenchmark` 等），15 上限在 benchmark 数量较多时起到实际限流作用。

### `.github/workflows/spark-ci.yml` (+1 line)

**修改目的**：为 Spark CI 的 matrix 作业设置并发上限。

**工作逻辑**：
在 `spark-tests` 作业的 `strategy` 块（matrix 为 `jvm: [17, 21]` × `spark: ['3.4', '3.5', '4.0', '4.1']`，共 8 个组合）中加入 `max-parallel: 15`。8 个组合低于 15，主要满足合规；但当未来 Spark 版本增多使组合数超过 15 时，该上限会实际生效。

## 总结

本次提交按照 Apache 基础设施 GitHub Actions 政策（必须 ≤20、建议 ≤15）为 Iceberg 仓库 9 个 CI 工作流中共 12 个含 matrix 策略的作业统一添加 `max-parallel: 15`，使每个 matrix 最多 15 个作业组合同时运行、超出部分排队。这消除了 ASF 工作流扫描器的 max-parallel 违规标记，合理约束了 Iceberg 对共享 runner 的并发占用，是 Apache 各仓库统一合规整改的一部分。改动纯增量、不改变测试覆盖与结果，仅在 benchmark 等动态/较大 matrix 场景下产生实际限流效果。
