# 提交 0406：Build: Bump actions/cache from 3 to 4

## 提交信息

- **序号**：0406
- **哈希**：20ff1ab33d2e032feb845eb2609bd6eb2c154f2d
- **短哈希**：20ff1ab33
- **日期**：2024 年 1 月 23 日（Tue Jan 23 07:58:53 2024 +0100）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump actions/cache from 3 to 4 (#9532)
- **PR/Issue**：#9532

完整提交说明如下：

```
Bumps [actions/cache](https://github.com/actions/cache) from 3 to 4.
- [Release notes](https://github.com/actions/cache/releases)
- [Changelog](https://github.com/actions/cache/blob/main/RELEASES.md)
- [Commits](https://github.com/actions/cache/compare/v3...v4)

---
updated-dependencies:
- dependency-name: actions/cache
  dependency-type: direct:production
  update-type: version-update:semver-major
...

Signed-off-by: dependabot[bot] <support@github.com>
Co-authored-by: dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
```

## 总体目的

这是一个由 GitHub Dependabot 自动生成的依赖版本升级提交，将仓库中所有 GitHub Actions 工作流引用的 `actions/cache` 从 v3 主版本升级到 v4。`actions/cache` 是 GitHub 官方提供的缓存动作，被广泛用于在 CI 流水线中缓存 Gradle/Maven 等构建工具的依赖与产物，以减少重复下载、加快构建速度并降低公共网络出口压力。

Iceberg 项目的 CI 体量很大，横跨 Flink、Spark（多个版本）、Hive、Delta 转换、纯 Java、JMH 基准测试等多条流水线，每条流水线在 setup Java 之后都会挂载一个 `actions/cache` 步骤来缓存 `~/.gradle/caches`、`~/.gradle/wrapper/dists` 等目录。因此 `actions/cache` 是整个仓库所有 CI 的共享底座，它的版本升级是一次跨多个工作流的"基础设施升级"，影响面广但单点改动机械、风险较低。

Dependabot 在说明中明确标注 `update-type: version-update:semver-major`，即这是一次 semver 主版本升级（v3 → v4）。主版本升级通常意味着存在不向后兼容的变更，但本提交仅是把 `uses: actions/cache@v3` 替换为 `uses: actions/cache@v4`，并未修改任何 `with:` 参数（`path`、`key`、`restore-keys` 等都保持原样）。这说明在该项目使用场景下，v4 与 v3 的 API 是兼容的，升级本身不需要业务侧适配。选择直接升级是为了跟进官方维护：v3 进入维护期后，新的 bug 修复和功能改进只会出现在 v4 上，长期停留在旧版本会积累技术债。

## 如何达成设计目的

实现路径非常直接：Dependabot 扫描了仓库下所有 `.github/workflows/*.yml` 文件，找出其中 `uses: actions/cache@v3` 的引用，统一替换为 `actions/cache@v4`。共涉及 7 个工作流文件、11 处替换点（每个工作流可能包含多个 job，每个 job 各有一个 cache 步骤）。替换是纯文本级别的版本号变更，不涉及缓存键、路径或恢复策略的调整，保持了既有缓存行为的连续性。

## 修改详情

### .github/workflows/delta-conversion-ci.yml

**修改目的**：将 Delta 转换 CI 工作流中的 `actions/cache` 从 v3 升级到 v4。

**工作逻辑**：该工作流有两个 job（matrix 构建与测试），每个 job 在 setup-java 之后都挂了一个 `actions/cache` 步骤来缓存 `~/.gradle/caches`、`~/.gradle/wrapper/dists` 等目录。本提交将这两处 `actions/cache@v3` 改为 `actions/cache@v4`，共 2 处替换。

### .github/workflows/flink-ci.yml

**修改目的**：将 Flink CI 工作流中的 `actions/cache` 从 v3 升级到 v4。

**工作逻辑**：Flink CI 中有一个 job 在 setup-java 后挂载缓存步骤，缓存 Gradle 相关目录。本提交将该处 `actions/cache@v3` 改为 `actions/cache@v4`，共 1 处替换。

### .github/workflows/hive-ci.yml

**修改目的**：将 Hive CI 工作流中的 `actions/cache` 从 v3 升级到 v4。

**工作逻辑**：Hive CI 包含两个 job（matrix 测试 job 与一个 java-version 为 8 的 job），每个 job 都有独立的 cache 步骤。本提交将这两处 `actions/cache@v3` 改为 `actions/cache@v4`，共 2 处替换。

### .github/workflows/java-ci.yml

**修改目的**：将 Java CI 工作流中的 `actions/cache` 从 v3 升级到 v4。

**工作逻辑**：Java CI 中有一个 job 在 setup-java 后挂载缓存步骤，缓存 Gradle 相关目录。本提交将该处 `actions/cache@v3` 改为 `actions/cache@v4`，共 1 处替换。

### .github/workflows/jmh-benchmarks.yml

**修改目的**：将 JMH 基准测试工作流中的 `actions/cache` 从 v3 升级到 v4。

**工作逻辑**：JMH 基准测试工作流在 setup-java（java-version: 11）后挂载缓存步骤，缓存 Gradle 相关目录。本提交将该处 `actions/cache@v3` 改为 `actions/cache@v4`，共 1 处替换。

### .github/workflows/recurring-jmh-benchmarks.yml

**修改目的**：将周期性 JMH 基准测试工作流中的 `actions/cache` 从 v3 升级到 v4。

**工作逻辑**：该工作流用于定时执行 JMH 基准测试，在 setup-java（java-version: 11）后挂载缓存步骤。本提交将该处 `actions/cache@v3` 改为 `actions/cache@v4`，共 1 处替换。

### .github/workflows/spark-ci.yml

**修改目的**：将 Spark CI 工作流中的 `actions/cache` 从 v3 升级到 v4。

**工作逻辑**：Spark CI 是改动最多的工作流，包含 3 个 job（不同 Spark 版本 / JVM 组合的 matrix 测试）。每个 job 都在 setup-java 后挂载独立的缓存步骤。本提交将这三处 `actions/cache@v3` 改为 `actions/cache@v4`，共 3 处替换。

## 小结

这是一个典型且机械的 Dependabot 依赖升级提交：跨 7 个工作流文件、共 11 处将 `actions/cache@v3` 替换为 `actions/cache@v4`，不修改任何缓存参数。它的意义在于跟进 GitHub 官方 Actions 的主版本演进，让所有 CI 流水线（Delta、Flink、Hive、Java、JMH、Spark）统一跑在受维护的 v4 上，避免因 v3 进入维护期而累积依赖债。改动风险低、影响面集中于 CI 基础设施层，不触及任何业务代码与测试逻辑。
