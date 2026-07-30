# 提交 3015：Build: Bump actions/cache from 4 to 5 (#14839)

## 提交信息

- **序号**：3015 / 4088
- **哈希**：69b4191eb32969afdb41a4df020e863db96d9fff
- **短哈希**：69b4191eb
- **日期**：2025-12-14 07:12:48 -0800
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump actions/cache from 4 to 5 (#14839)
- **PR/Issue**：#14839

## 总体目的

`actions/cache` 是 GitHub 官方提供的 Action，用于在 GitHub Actions 工作流中缓存依赖与构建中间产物（如 Gradle/Maven 依赖、下载的发行包、编译缓存等），以加速后续 CI 运行并减少网络下载。Iceberg 作为一个多模块大型 Gradle 项目，CI 中需要拉取大量依赖（AWS SDK、Azure SDK、Spark、Flink、Hive、Parquet、ORC 等），缓存这些依赖与 Gradle 的下载/构建产物对 CI 时长有显著影响。多个工作流通过 `actions/cache@v4` 缓存 Gradle 依赖与相关分发。

本提交是 dependabot 触发的升级，把 `actions/cache` 从 v4 主版本升到 v5 主版本。作为主版本（major）升级，v5 相对 v4 可能引入不向后兼容的行为变更（如缓存键语义、恢复逻辑、压缩算法、缓存大小限制等）。升级动机是跟进官方 Action 的最新主版本，获取其改进与新特性，并避免继续使用将被逐步弃用的旧主版本。

## 如何达成设计目的

机械替换：在所有引用 `actions/cache@v4` 的 workflow 文件中把 `v4` 改为 `v5`。改动覆盖 8 个工作流文件，共 9 处引用（`delta-conversion-ci.yml` 有两处）。dependabot 元数据标注 `update-type: version-update:semver-major`。

## 修改详情

### `.github/workflows/delta-conversion-ci.yml` (+2/-2 lines)

**修改目的**：升级该工作流中的两处 cache Action 到 v5。

**工作逻辑**：
两处 `actions/cache@v4` → `@v5`。该工作流负责 Delta 数据转换 CI，缓存其构建/依赖产物以加速运行。

### `.github/workflows/flink-ci.yml` (+1/-1 lines)

**修改目的**：升级 Flink CI 工作流中的 cache 到 v5。

**工作逻辑**：
`@v4` → `@v5`。Flink 引擎模块 CI，缓存 Gradle 依赖与 Flink 分发等以加速构建。

### `.github/workflows/hive-ci.yml` (+1/-1 lines)

**修改目的**：升级 Hive CI 工作流中的 cache 到 v5。

**工作逻辑**：
`@v4` → `@v5`。Hive 集成模块 CI，缓存依赖以加速构建。

### `.github/workflows/java-ci.yml` (+1/-1 lines)

**修改目的**：升级主 Java CI 工作流中的 cache 到 v5。

**工作逻辑**：
`@v4` → `@v5`。核心 Java 构建/测试 CI，缓存 Gradle 依赖以加速。

### `.github/workflows/jmh-benchmarks.yml` (+1/-1 lines)

**修改目的**：升级 JMH 基准测试工作流中的 cache 到 v5。

**工作逻辑**：
`@v4` → `@v5`。JMH 性能基准工作流，缓存依赖以加速基准编译与运行。

### `.github/workflows/kafka-connect-ci.yml` (+1/-1 lines)

**修改目的**：升级 Kafka Connect CI 工作流中的 cache 到 v5。

**工作逻辑**：
`@v4` → `@v5`。Kafka Connect 集成模块 CI，缓存依赖以加速构建。

### `.github/workflows/recurring-jmh-benchmarks.yml` (+1/-1 lines)

**修改目的**：升级周期性 JMH 基准工作流中的 cache 到 v5。

**工作逻辑**：
`@v4` → `@v5`。定期执行的 JMH 基准工作流，缓存依赖以加速。

### `.github/workflows/spark-ci.yml` (+1/-1 lines)

**修改目的**：升级 Spark CI 工作流中的 cache 到 v5。

**工作逻辑**：
`@v4` → `@v5`。Spark 引擎模块 CI，缓存 Gradle 依赖与 Spark 分发等以加速构建。

## 总结

该提交是一次 dependabot 驱动的 GitHub Action `actions/cache` 主版本升级（v4 → v5），在 8 个 CI 工作流文件共 9 处引用中机械替换 `@v4` 为 `@v5`，使所有使用依赖缓存的工作流统一跟进到最新主版本。属于 CI 基础设施维护，主要获取 v5 的改进与新特性并避免使用将被弃用的旧主版本；由于是 major 升级，需关注 v5 可能的缓存行为变更对 CI 命中率与构建时长的影响。
