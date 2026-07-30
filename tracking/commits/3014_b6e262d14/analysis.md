# 提交 3014：Build: Bump actions/upload-artifact from 5 to 6 (#14840)

## 提交信息

- **序号**：3014 / 4088
- **哈希**：b6e262d144ea3a76f41d48bc5b7a9aaa3b348eb0
- **短哈希**：b6e262d14
- **日期**：2025-12-14 06:58:39 -0800
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump actions/upload-artifact from 5 to 6 (#14840)
- **PR/Issue**：#14840

## 总体目的

`actions/upload-artifact` 是 GitHub 官方提供的 Action，用于在 GitHub Actions 工作流中把构建产物（如测试报告、构建 JAR、覆盖率数据等）上传为 workflow artifact，便于事后下载、调试与归档。Iceberg 在多个 CI 工作流中用它上传构建/测试产物（例如各引擎模块 CI 失败时上传日志与报告）。

本提交是 dependabot 触发的升级，把 `actions/upload-artifact` 从 v5 主版本升到 v6 主版本。作为主版本（major）升级，v6 相对 v5 可能引入不向后兼容的行为变更（如上传语义、artifact 保留策略、合并行为、API 响应格式等），需在工作流层面适配。升级动机是跟进官方 Action 的最新主版本，获取其改进与新特性，并避免继续使用将被逐步弃用的旧主版本。

## 如何达成设计目的

机械替换：在所有引用 `actions/upload-artifact@v5` 的 workflow 文件中把 `v5` 改为 `v6`。改动覆盖 9 个工作流文件，共 10 处引用（`delta-conversion-ci.yml` 有两处）。dependabot 元数据标注 `update-type: version-update:semver-major`。

## 修改详情

### `.github/workflows/api-binary-compatibility.yml` (+1/-1 lines)

**修改目的**：升级该工作流中的 upload-artifact Action 到 v6。

**工作逻辑**：
`actions/upload-artifact@v5` → `actions/upload-artifact@v6`。该工作流负责 API 二进制兼容性检查（likely 通过 `revapi` 等），上传兼容性报告产物。

### `.github/workflows/delta-conversion-ci.yml` (+2/-2 lines)

**修改目的**：升级该工作流中的两处 upload-artifact 引用到 v6。

**工作逻辑**：
两处 `actions/upload-artifact@v5` → `@v6`。该工作流负责 Delta 数据转换（Iceberg ↔ Delta Lake）的 CI，上传构建/测试产物。

### `.github/workflows/flink-ci.yml` (+1/-1 lines)

**修改目的**：升级 Flink CI 工作流中的 upload-artifact 到 v6。

**工作逻辑**：
`@v5` → `@v6`。Flink 引擎模块 CI，上传测试产物。

### `.github/workflows/hive-ci.yml` (+1/-1 lines)

**修改目的**：升级 Hive CI 工作流中的 upload-artifact 到 v6。

**工作逻辑**：
`@v5` → `@v6`。Hive 集成模块 CI，上传测试产物。

### `.github/workflows/java-ci.yml` (+1/-1 lines)

**修改目的**：升级主 Java CI 工作流中的 upload-artifact 到 v6。

**工作逻辑**：
`@v5` → `@v6`。核心 Java 构建/测试 CI，上传构建与测试产物。

### `.github/workflows/jmh-benchmarks.yml` (+1/-1 lines)

**修改目的**：升级 JMH 基准测试工作流中的 upload-artifact 到 v6。

**工作逻辑**：
`@v5` → `@v6`。JMH 性能基准工作流，上传基准结果产物。

### `.github/workflows/kafka-connect-ci.yml` (+1/-1 lines)

**修改目的**：升级 Kafka Connect CI 工作流中的 upload-artifact 到 v6。

**工作逻辑**：
`@v5` → `@v6`。Kafka Connect 集成模块 CI，上传测试产物。

### `.github/workflows/recurring-jmh-benchmarks.yml` (+1/-1 lines)

**修改目的**：升级周期性 JMH 基准工作流中的 upload-artifact 到 v6。

**工作逻辑**：
`@v5` → `@v6`。定期执行的 JMH 基准工作流，上传基准结果产物。

### `.github/workflows/spark-ci.yml` (+1/-1 lines)

**修改目的**：升级 Spark CI 工作流中的 upload-artifact 到 v6。

**工作逻辑**：
`@v5` → `@v6`。Spark 引擎模块 CI，上传测试产物。

## 总结

该提交是一次 dependabot 驱动的 GitHub Action `actions/upload-artifact` 主版本升级（v5 → v6），在 9 个 CI 工作流文件共 10 处引用中机械替换 `@v5` 为 `@v6`，使所有上传构建/测试产物的工作流统一跟进到最新主版本。属于 CI 基础设施维护，主要获取 v6 的改进与新特性并避免使用将被弃用的旧主版本；由于是 major 升级，需关注 v6 可能的行为变更对产物上传的影响。
