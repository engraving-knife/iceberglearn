# 提交 0221：Build: Bump actions/setup-java from 3 to 4 (#9200)

## 提交信息

- **序号**：0221 / 4088
- **哈希**：8b7a280a9fd0b51eb43538fbb9f6879d1c92de18
- **短哈希**：8b7a280a9
- **日期**：2023-12-05 18:45:02 +0100
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump actions/setup-java from 3 to 4 (#9200)
- **PR/Issue**：#9200

## 总体目的

这是一个由 Dependabot 自动生成的依赖升级提交，目的是将 GitHub Actions 工作流中使用的 `actions/setup-java` 动作从 v3 升级到 v4。`actions/setup-java` 是 GitHub 官方提供的 Action，用于在 CI 运行器上配置 Java 开发环境（JDK）。

升级动机主要是：v3 已经是较旧的版本，v4 是同一 Action 的最新主版本，包含错误修复、性能改进、对新运行器环境的兼容性提升以及潜在的安全补丁。Dependabot 将此次升级标记为 `version-update:semver-major`，意味着这是一个跨主版本升级，通常伴随行为变化或新的默认值。

对于 Iceberg 项目而言，保持 CI 基础设施依赖的最新状态有助于避免 Action 弃用导致的构建流水线中断，并维持与最新 GitHub Actions 运行时环境的兼容性。这种"机械式"的依赖维护是大型开源项目日常运作的一部分。

## 如何达成设计目的

改动方式非常直接：在所有引用 `actions/setup-java@v3` 的 GitHub Actions 工作流文件中，将版本标签从 `v3` 替换为 `v4`。其余配置（如 `distribution: zulu`、`java-version` 等）保持不变，只升级 Action 自身的版本。

## 修改详情

### `.github/workflows/` 下的 9 个工作流文件

**修改目的**：将 `actions/setup-java` 这个 GitHub Action 从 v3 升级到 v4，使所有 CI 流水线统一使用最新的 Java 环境配置动作。

**工作逻辑**：改动覆盖了以下 9 个工作流文件，每个文件中将一处或多处 `uses: actions/setup-java@v3` 替换为 `uses: actions/setup-java@v4`：

- `api-binary-compatibility.yml`（1 处）—— API 二进制兼容性检查
- `delta-conversion-ci.yml`（2 处）—— Delta 转换 CI
- `flink-ci.yml`（1 处）—— Flink 集成 CI
- `hive-ci.yml`（2 处）—— Hive 集成 CI
- `java-ci.yml`（3 处）—— 主 Java CI
- `jmh-benchmarks.yml`（1 处）—— JMH 基准测试
- `publish-snapshot.yml`（1 处）—— 快照发布
- `recurring-jmh-benchmarks.yml`（1 处）—— 定期基准测试
- `spark-ci.yml`（3 处）—— Spark 集成 CI

合计 15 处替换，新增/删除各 15 行。值得注意的是 `actions/checkout` 已经在 v4（说明此前已经升级过 checkout），此次升级仅针对 `setup-java`，保持两个 Action 版本对齐。

## 小结

一次纯粹的 CI 基础设施依赖升级，将所有 GitHub Actions 工作流中的 `actions/setup-java` 从 v3 升至 v4，无任何产品代码变更，属于必要的工程卫生维护。
