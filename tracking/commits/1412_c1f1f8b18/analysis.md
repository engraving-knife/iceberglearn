# 提交 1412：Build: Upgrade to Gradle 8.11.1 (#11619)

## 提交信息

- **序号**：1412 / 4088
- **哈希**：c1f1f8b18e0fd1a36e170f05e9c7fa4f2bdd7b8d
- **短哈希**：c1f1f8b18
- **日期**：2024-11-21（Thu Nov 21 16:15:42 2024 +0100）
- **作者**：JB Onofré <jbonofre@apache.org>
- **提交说明**：Build: Upgrade to Gradle 8.11.1 (#11619)
- **PR/Issue**：#11619

## 总体目的

Iceberg 仓库使用 Gradle Wrapper 来固定构建所用 Gradle 版本，确保所有开发者和 CI 在同一版本上构建。此前仓库升级到 Gradle 8.11 后，Gradle 官方发布了 8.11.1 维护版本，主要修复了 8.11 中的一些缺陷。本提交将 Gradle Wrapper 配置从 8.11 升级到 8.11.1，使构建工具链保持最新稳定状态，避免在 8.11 版本中存在的潜在问题影响 Iceberg 的构建可靠性。

这是一次纯构建工具版本升级，不涉及任何业务代码或测试代码改动。

## 如何达成设计目的

通过修改两处来更新 Gradle Wrapper：

1. 修改 `gradle/wrapper/gradle-wrapper.properties` 中的 `distributionUrl` 指向新的 `gradle-8.11.1-bin.zip`，并同步更新 `distributionSha256Sum` 为 8.11.1 发行包的校验和，确保下载的发行包完整可信。
2. 修改 `gradlew` 脚本中兜底下载 `gradle-wrapper.jar` 的 URL，从 `v8.11.0` 改为 `v8.11.1`（这条路径仅在仓库未带 wrapper jar 时触发，但保持版本一致性仍然必要）。

## 修改详情

### `gradle/wrapper/gradle-wrapper.properties`

**修改目的**：将 Wrapper 指向 Gradle 8.11.1 发行包。

**工作逻辑**：
- `distributionSha256Sum` 由 `57dafb5c2622c6cc08b993c85b7c06956a2f53536432a30ead46166dbca0f1e9`（8.11）改为 `f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6`（8.11.1）。
- `distributionUrl` 由 `https\://services.gradle.org/distributions/gradle-8.11-bin.zip` 改为 `https\://services.gradle.org/distributions/gradle-8.11.1-bin.zip`。

### `gradlew`

**修改目的**：同步兜底下载 wrapper jar 时所用的 Gradle 版本。

**工作逻辑**：将脚本中检测到 `gradle/wrapper/gradle-wrapper.jar` 不存在时执行的 `curl` 命令目标 URL 由 `https://raw.githubusercontent.com/gradle/gradle/v8.11.0/gradle/wrapper/gradle-wrapper.jar` 改为 `https://raw.githubusercontent.com/gradle/gradle/v8.11.1/gradle/wrapper/gradle-wrapper.jar`。

## 小结

- **成效**：仓库的 Gradle Wrapper 升级到 8.11.1，构建工具链与上游最新维护版本对齐，避免 8.11 中潜在缺陷。
- **影响范围**：仅 `gradle/wrapper/gradle-wrapper.properties` 与 `gradlew` 两个构建脚本文件，3 行改动，无产品代码变更。
- **回迁到 1.4.x 的注意事项**：1.4.x 作为维护分支，其 Gradle 版本可能仍停留在较早版本（如 8.4 或更早）。是否需要回迁该升级取决于 1.4.x 是否计划继续发布补丁版本，以及其当前使用的 Gradle 版本是否存在已知问题。一般而言，**回迁此升级风险极低**（仅工具版本），但若 1.4.x 已临近终止维护或其构建在新版本下出现兼容性问题，可保持现有版本不动。建议在回迁前先在 1.4.x 分支上以 8.11.1 完整运行一遍 CI 验证。
