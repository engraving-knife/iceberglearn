# 提交 2929：Build: Bump actions/checkout from 3 to 6 (#14691)

## 提交信息

- **序号**：2929 / 4088
- **哈希**：42719ef41eefc56968b528c51550f3ac63682eb2
- **短哈希**：42719ef41
- **日期**：2025-11-27 14:57:09 +0100
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump actions/checkout from 3 to 6 (#14691)
- **PR/Issue**：#14691

## 总体目的

这是由 Dependabot 自动生成的依赖升级提交，目的是将 GitHub Actions 工作流中使用的 `actions/checkout` 升级到 v6。

`actions/checkout` 是 GitHub 官方提供的代码检出 Action，几乎所有 CI 工作流的第一步都用它把仓库源码克隆到 runner 上，供后续构建、测试、文档生成等步骤使用。它是 GitHub Actions 生态中使用最广泛的 Action 之一。

Dependabot 元数据显示此次升级类型为 `version-update:semver-major`。值得注意的是，本仓库此前各工作流中混用了 `actions/checkout@v3` 与 `actions/checkout@v4` 两种版本（例如 `api-binary-compatibility.yml` 原先用 `@v4`，而 `delta-conversion-ci.yml` 等使用 `@v4`，部分文件此前可能停留在更旧版本）。此次升级将所有引用统一拉齐到最新的 `@v6`，既完成了主版本升级，也消除了仓库内 checkout 版本不一致的问题。主版本升级通常伴随 Node.js 运行时升级、行为或输入参数的细微变化，但 checkout 的核心用法（克隆仓库）高度稳定，升级风险很低。

## 如何达成设计目的

Dependabot 扫描 `.github/workflows/` 目录下全部 YAML 工作流文件，将其中所有 `actions/checkout@vN`（v3/v4）引用统一替换为 `actions/checkout@v6`。本次共涉及 15 个工作流文件、19 处引用（部分工作流含多个 job 或矩阵步骤，因此有多处检出步骤）。

## 修改详情

### `.github/workflows/api-binary-compatibility.yml` (+1/-1 lines)

**修改目的**：将 API 二进制兼容性检查任务中的代码检出 Action 升级到 v6。

**工作逻辑**：该步骤 `uses: actions/checkout` 配合 `fetch-depth: 0` 以拉取全部历史与标签，因为 revapi 依赖 `git describe` 的标签来定位比较基线。将版本由 `@v4` 升级为 `@v6`。

### `.github/workflows/delta-conversion-ci.yml` (+2/-2 lines)

**修改目的**：升级 Delta Lake 转换 CI 中 Scala 2.12 与 2.13 两个任务的检出步骤。

### `.github/workflows/docs-ci.yml` (+1/-1 lines)

**修改目的**：升级文档 CI（在 ubuntu/macos 矩阵上构建文档）中的检出步骤。

### `.github/workflows/flink-ci.yml` (+1/-1 lines)

**修改目的**：升级 Flink CI 中的检出步骤。

### `.github/workflows/hive-ci.yml` (+1/-1 lines)

**修改目的**：升级 Hive CI 中的检出步骤。

### `.github/workflows/java-ci.yml` (+3/-3 lines)

**修改目的**：升级核心 Java CI 中多处检出步骤（对应多个 job）。

### `.github/workflows/jmh-benchmarks.yml` (+2/-2 lines)

**修改目的**：升级 JMH 基准测试工作流中的检出步骤。

### `.github/workflows/kafka-connect-ci.yml` (+1/-1 lines)

**修改目的**：升级 Kafka Connect CI 中的检出步骤。

### `.github/workflows/license-check.yml` (+1/-1 lines)

**修改目的**：升级许可证检查工作流中的检出步骤。

### `.github/workflows/open-api.yml` (+1/-1 lines)

**修改目的**：升级 OpenAPI 规范校验工作流中的检出步骤。

### `.github/workflows/publish-iceberg-rest-fixture-docker.yml` (+1/-1 lines)

**修改目的**：升级 REST fixture Docker 镜像发布工作流中的检出步骤。

### `.github/workflows/publish-snapshot.yml` (+1/-1 lines)

**修改目的**：升级快照发布工作流中的检出步骤。

### `.github/workflows/recurring-jmh-benchmarks.yml` (+1/-1 lines)

**修改目的**：升级周期性 JMH 基准测试工作流中的检出步骤。

### `.github/workflows/site-ci.yml` (+1/-1 lines)

**修改目的**：升级站点构建 CI 中的检出步骤。

### `.github/workflows/spark-ci.yml` (+1/-1 lines)

**修改目的**：升级 Spark CI 中的检出步骤。

## 总结

该提交将 15 个 CI 工作流文件中 19 处 `actions/checkout` 引用统一升级到 v6，既完成了主版本升级又将仓库内混用的 v3/v4 版本拉齐一致。这是一次低风险的基础设施维护，确保代码检出能力与上游 Action 最新版本保持同步。
