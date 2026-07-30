# 提交 2273：Build: Run CI actions with JDK 17 (#13385)

## 提交信息

- **序号**：2273 / 4088
- **哈希**：0dd3c2b632aadb2cf0f4df7c135b79e3f2b56b17
- **短哈希**：0dd3c2b63
- **日期**：2025-06-25 16:58:47 +0200
- **作者**：Eduard Tudenhoefner
- **提交说明**：Build: Run CI actions with JDK 17 (#13385)
- **PR/Issue**：#13385

## 总体目的

本提交将三个 GitHub Actions CI 工作流使用的 JDK 版本从 11 升级到 17，是 Iceberg 项目整体 JDK 基线迁移的延续。涉及的工作流包括：API 二进制兼容性检查（api-binary-compatibility）、JMH 基准测试（jmh-benchmarks）和定期 JMH 基准测试（recurring-jmh-benchmarks）。

随着项目逐步将最低 JDK 要求提升到 17（参见 2270 快照发布迁移、2271 Spark 4.0 要求 JDK 17/21），CI 工作流也必须同步升级，否则在 JDK 11 下运行的 CI 作业无法正确编译和验证需要 JDK 17 的代码。特别是 API 兼容性检查（revapi）和基准测试需要完整构建项目，JDK 版本不匹配会导致构建失败或结果不准确。统一升级到 JDK 17 确保 CI 环境与项目基线一致。

## 如何达成设计目的

- 修改三个工作流 YAML 文件中 `actions/setup-java` 步骤的 `java-version` 输入，统一从 `11` 改为 `17`。
- 保留各工作流原有的 `distribution: zulu` 和其他配置不变。
- 三个文件的修改模式完全一致，均为单行版本号变更。

## 修改详情

### `.github/workflows/api-binary-compatibility.yml` (+1/-1 lines)

**修改目的**：将 API 二进制兼容性检查工作流的 JDK 从 11 升级到 17。

**工作逻辑**：该工作流运行 `./gradlew revapi --rerun-tasks` 进行 API 兼容性检查。revapi 插件需要编译项目后比对 API 差异，JDK 17 确保能正确编译包含 JDK 17 特性的代码和新模块。修改位于 `setup-java` 步骤，将 `java-version: 11` 改为 `java-version: 17`。

### `.github/workflows/jmh-benchmarks.yml` (+1/-1 lines)

**修改目的**：将 JMH 基准测试工作流的 JDK 从 11 升级到 17。

**工作逻辑**：该工作流运行 JMH 基准测试，需要完整编译项目并执行性能测试。JDK 17 确保基准测试在与项目基线一致的环境下运行，使结果具有可比性和参考价值。同样将 `java-version: 11` 改为 `java-version: 17`。

### `.github/workflows/recurring-jmh-benchmarks.yml` (+1/-1 lines)

**修改目的**：将定期 JMH 基准测试工作流的 JDK 从 11 升级到 17。

**工作逻辑**：与 jmh-benchmarks 类似，这是定期（recurring）执行的性能基准测试工作流。同样将 `java-version: 11` 改为 `java-version: 17`，确保定期性能跟踪数据基于 JDK 17 环境产生。

## 总结

本提交将三个 CI 工作流的 JDK 从 11 统一升级到 17，是项目 JDK 基线迁移的重要组成部分。通过确保 CI 环境与项目最低 JDK 要求一致，避免了构建失败和测试结果失真，保障了持续集成的正确性和可靠性。
