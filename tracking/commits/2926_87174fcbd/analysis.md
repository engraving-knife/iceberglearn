# 提交 2926：Build: Bump actions/upload-artifact from 4 to 5 (#14688)

## 提交信息

- **序号**：2926 / 4088
- **哈希**：87174fcbd4f3130d4b863458011467eab42b0224
- **短哈希**：87174fcbd
- **日期**：2025-11-25 23:48:54 -0800
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump actions/upload-artifact from 4 to 5 (#14688)
- **PR/Issue**：#14688

## 总体目的

这是由 Dependabot 自动生成的依赖升级提交，目的是将 GitHub Actions 工作流中使用的 `actions/upload-artifact` 从 v4 主版本升级到 v5 主版本。

`actions/upload-artifact` 是 GitHub 官方提供的 Action，用于在 CI 运行过程中上传构建产物（artifact）到工作流运行记录中，便于后续下载、排查或归档。在 Iceberg 仓库中，该 Action 主要用于在各 CI 任务失败（`if: failure()`）时上传测试日志（`name: test logs`），方便开发者定位集成测试、二进制兼容性检查、JMH 基准测试等环节的失败原因。

Dependabot 元数据表明此次升级类型为 `version-update:semver-major`，即跨越主版本（4 → 5）。主版本升级通常意味着可能存在破坏性变更（breaking change），需要工作流维护者关注 v5 的 Release Notes 中关于行为变化、输入参数或权限要求的说明。由于该 Action 在 Iceberg 中仅在失败时上传日志，使用场景单一且输入参数简单（仅 `name` 和日志路径），升级风险较低。

## 如何达成设计目的

Dependabot 扫描仓库 `.github/workflows/` 目录下所有 YAML 工作流文件，将所有 `actions/upload-artifact@v4` 引用统一替换为 `actions/upload-artifact@v5`。本次共涉及 9 个工作流文件、10 处引用（其中 `delta-conversion-ci.yml` 含两处分别对应 Scala 2.12 与 2.13 的任务），改动机械且一致。

## 修改详情

### `.github/workflows/api-binary-compatibility.yml` (+1/-1 lines)

**修改目的**：将 API 二进制兼容性检查任务中的 artifact 上传 Action 升级到 v5。

**工作逻辑**：该工作流运行 `./gradlew revapi` 进行反向 API 兼容性检查，当检查失败时使用 `actions/upload-artifact` 上传测试日志。将 `@v4` 改为 `@v5`。

### `.github/workflows/delta-conversion-ci.yml` (+2/-2 lines)

**修改目的**：升级 Delta Lake 转换 CI 中两处 artifact 上传引用。

**工作逻辑**：该工作流针对 Spark 3.5 + Scala 2.12 和 Spark 3.5 + Scala 2.13 两个矩阵任务各有一个失败时上传日志的步骤，两处均从 `@v4` 升级为 `@v5`。

### `.github/workflows/flink-ci.yml` (+1/-1 lines)

**修改目的**：升级 Flink CI 中失败日志上传的 Action 版本。

### `.github/workflows/hive-ci.yml` (+1/-1 lines)

**修改目的**：升级 Hive CI 中失败日志上传的 Action 版本。

### `.github/workflows/java-ci.yml` (+1/-1 lines)

**修改目的**：升级核心 Java CI 中失败日志上传的 Action 版本。

### `.github/workflows/jmh-benchmarks.yml` (+1/-1 lines)

**修改目的**：升级 JMH 基准测试工作流中失败日志上传的 Action 版本。

### `.github/workflows/kafka-connect-ci.yml` (+1/-1 lines)

**修改目的**：升级 Kafka Connect CI 中失败日志上传的 Action 版本。

### `.github/workflows/recurring-jmh-benchmarks.yml` (+1/-1 lines)

**修改目的**：升级周期性 JMH 基准测试工作流中失败日志上传的 Action 版本。

### `.github/workflows/spark-ci.yml` (+1/-1 lines)

**修改目的**：升级 Spark CI 中失败日志上传的 Action 版本。

## 总结

该提交通过将 9 个 CI 工作流文件中 10 处 `actions/upload-artifact` 引用从 v4 升级到 v5，保持 Iceberg 仓库的 GitHub Actions 依赖处于最新主版本。这是一次低风险的基础设施维护，确保失败日志上传能力与上游 Action 的最新行为兼容。
