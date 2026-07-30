# 提交 3332：Build: Bump actions/upload-artifact from 6 to 7 (#15479)

## 提交信息

- **序号**：3332 / 4088
- **哈希**：e79b037b335c17998557190cb101041247856f63
- **短哈希**：e79b037b3
- **日期**：2026-02-28 22:09:46 -0800
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump actions/upload-artifact from 6 to 7 (#15479)
- **PR/Issue**：#15479

## 总体目的

这是 Dependabot 自动生成的 GitHub Actions 依赖升级提交，将 `actions/upload-artifact` 从 v6 升级到 v7。

`actions/upload-artifact` 是 GitHub 官方提供的 Action，用于在 CI 工作流中把构建产物（如测试日志、报告、构建包等）上传为 workflow artifact，便于后续下载、排查或跨 job 共享。在 Iceberg 仓库中，该 Action 主要用于在 CI 失败时上传测试日志（`name: test logs`），以及在 JMH 基准测试工作流中上传基准结果（`name: benchmark-results`）。这些产物是开发者诊断 CI 失败与回顾性能基准的关键数据。

本次升级属于语义化版本的 **major（主版本）** 升级（`v6` → `v7`，`update-type: version-update:semver-major`）。主版本升级意味着上游可能引入了不兼容的行为变更，需要关注 v7 是否调整了输入参数（如 `name`、`path`、`retention-days`、`compression-level` 等）、产物命名规则、上传大小/数量限制，或对 Node.js 运行时版本的要求。Dependabot 将其标记为 `direct:production`，是因为它直接影响生产 CI 流程。预期影响是 CI 仍能正常上传产物，但需关注 v7 的行为差异（如 artifact 命名冲突处理、上传策略变更）。

值得注意的是，本次改动涉及 9 个工作流文件，是本批 Dependabot 提交中覆盖文件最多的一次。其中 `delta-conversion-ci.yml` 有两处引用（两个并行矩阵 job 各一处），其余各一处。

## 如何达成设计目的

改动逐文件将工作流中 `uses: actions/upload-artifact@v6` 替换为 `uses: actions/upload-artifact@v7`，保留各步骤的触发条件（`if: failure()` 或 `if: ${{ always() }}`）与 `with` 参数（`name`、`path`）不变。由于各工作流相互独立，改动是机械式的全量替换，无需集中配置。

## 修改详情

### `.github/workflows/api-binary-compatibility.yml` (+1/-1 lines)

**修改目的**：将 revapi 检查失败时的日志上传从 v6 切换到 v7。

**工作逻辑**：
将 `uses: actions/upload-artifact@v6`（位于 `if: failure()` 的步骤）改为 `@v7`，`with: name: test logs` 等参数不变。该步骤在 API/二进制兼容性检查（`./gradlew revapi`）失败时上传相关日志供排查。

### `.github/workflows/delta-conversion-ci.yml` (+2/-2 lines)

**修改目的**：将 Delta 转换模块两个矩阵 job 的失败日志上传从 v6 切换到 v7。

**工作逻辑**：
该工作流有两个 job（Spark 3.5 + Scala 2.12、Spark 3.5 + Scala 2.13），各自在 `if: failure()` 步骤中上传 `test logs`。两处 `actions/upload-artifact@v6` 均改为 `@v7`，参数不变。

### `.github/workflows/flink-ci.yml` (+1/-1 lines)

**修改目的**：将 Flink CI 失败时的日志上传从 v6 切换到 v7。

**工作逻辑**：
将 `uses: actions/upload-artifact@v6`（`if: failure()` 步骤）改为 `@v7`，`with: name: test logs` 等参数不变。

### `.github/workflows/hive-ci.yml` (+1/-1 lines)

**修改目的**：将 Hive/MR CI 失败时的日志上传从 v6 切换到 v7。

**工作逻辑**：
将 `uses: actions/upload-artifact@v6`（`if: failure()` 步骤）改为 `@v7`，参数不变。

### `.github/workflows/java-ci.yml` (+1/-1 lines)

**修改目的**：将核心 Java CI 失败时的日志上传从 v6 切换到 v7。

**工作逻辑**：
将 `uses: actions/upload-artifact@v6`（`if: failure()` 步骤）改为 `@v7`，参数不变。

### `.github/workflows/jmh-benchmarks.yml` (+1/-1 lines)

**修改目的**：将 JMH 基准测试结果的产物上传从 v6 切换到 v7。

**工作逻辑**：
将 `uses: actions/upload-artifact@v6` 改为 `@v7`。注意此处触发条件是 `if: ${{ always() }}`（无论成功失败都上传），`with: name: benchmark-results`，用于保存基准测试输出文件供后续分析。

### `.github/workflows/kafka-connect-ci.yml` (+1/-1 lines)

**修改目的**：将 Kafka Connect CI 失败时的日志上传从 v6 切换到 v7。

**工作逻辑**：
将 `uses: actions/upload-artifact@v6`（`if: failure()` 步骤）改为 `@v7`，参数不变。

### `.github/workflows/recurring-jmh-benchmarks.yml` (+1/-1 lines)

**修改目的**：将定期 JMH 基准测试结果的产物上传从 v6 切换到 v7。

**工作逻辑**：
将 `uses: actions/upload-artifact@v6` 改为 `@v7`。此处触发条件同样为 `if: ${{ always() }}`，上传 `benchmark-results`，该工作流是定期触发（recurring）的基准测试，结果用于性能趋势跟踪。

### `.github/workflows/spark-ci.yml` (+1/-1 lines)

**修改目的**：将 Spark CI 失败时的日志上传从 v6 切换到 v7。

**工作逻辑**：
将 `uses: actions/upload-artifact@v6`（`if: failure()` 步骤）改为 `@v7`，参数不变。

## 总结

本次提交通过 Dependabot 将 GitHub Actions 的 `actions/upload-artifact` 从 v6 升级到 v7（major 级），覆盖 9 个工作流文件共 10 处引用。改动是机械式的版本号替换，保留各步骤的触发条件与参数不变，使 Iceberg 的 CI 产物上传（失败日志与基准结果）跟随官方 Action 的最新主版本。作为 major 升级，需关注 v7 可能的输入参数与行为差异，但 Iceberg 现有用法（仅用 `name`/`path`）通常不受影响。
