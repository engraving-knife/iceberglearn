# 提交 1142：Build: Upgrade to Gradle 8.10.1 (#11104)

## 提交信息

- **序号**：1142
- **哈希**：e40fe4cdbe9705415955fe2da201a6ad65dbfb9a
- **短哈希**：e40fe4cdb
- **日期**：2024-09-10（Tue Sep 10 11:13:39 2024 +0200）
- **作者**：JB Onofré <jbonofre@apache.org>
- **提交说明**：Build: Upgrade to Gradle 8.10.1 (#11104)
- **PR/Issue**：#11104

## 总体目的

Iceberg 项目使用 Gradle 作为构建工具，并通过 Gradle Wrapper 锁定具体版本以保证所有开发者与 CI 环境使用一致的构建版本。此前仓库使用的是 Gradle 8.10（8.10.0），社区在 8.10 发布后不久推出了 8.10.1 维护版本，主要修复了 8.10 中发现的若干缺陷与回归问题。本提交将 Gradle Wrapper 从 8.10 升级到 8.10.1，以获取最新的修复与稳定性提升，确保构建环境始终跟进 Gradle 官方维护版本。

## 如何达成设计目的

通过更新两个 Wrapper 相关文件完成升级：

1. `gradle/wrapper/gradle-wrapper.properties`：更新 `distributionUrl` 指向新版本的 zip 包，并同步更新 `distributionSha256Sum` 校验值，保证下载的 Gradle 发行包完整且未被篡改。
2. `gradlew`：修改引导脚本中针对缺失 `gradle-wrapper.jar` 时的兜底下载链接，从 `v8.10.0` 标签改为 `v8.10.1` 标签，与主版本保持一致。

这是 Gradle Wrapper 升级的标准操作，无任何业务代码或构建脚本逻辑变更。

## 修改详情

### `gradle/wrapper/gradle-wrapper.properties`

**修改目的**：将 Wrapper 指向 Gradle 8.10.1 发行包。

**工作逻辑**：

- `distributionSha256Sum` 由 `5b9c5eb3f9fc2c94abaea57d90bd78747ca117ddbbf96c859d3741181a12bf2a`（8.10）改为 `1541fa36599e12857140465f3c91a97409b4512501c26f9631fb113e392c5bd1`（8.10.1）。
- `distributionUrl` 由 `https\://services.gradle.org/distributions/gradle-8.10-bin.zip` 改为 `https\://services.gradle.org/distributions/gradle-8.10.1-bin.zip`。

Wrapper 首次执行时会根据 `distributionUrl` 下载对应版本，并通过 `distributionSha256Sum` 验证完整性，校验通过后才会执行。

### `gradlew`

**修改目的**：同步兜底下载链接到 8.10.1。

**工作逻辑**：在 `gradlew` 脚本中，存在一段兜底逻辑：当 `$APP_HOME/gradle/wrapper/gradle-wrapper.jar` 不存在时，从 GitHub 上 Gradle 对应标签下载该 jar。该提交将 URL 中的 `v8.10.0` 改为 `v8.10.1`，确保兜底场景下下载的 wrapper jar 也与目标版本对齐。

## 小结

- **成效**：项目构建工具升级到 Gradle 8.10.1，跟进 Gradle 官方维护版本，获取 8.10.1 中的稳定性修复。
- **影响范围**：仅 `gradle/wrapper/gradle-wrapper.properties` 与 `gradlew` 两个文件，共 3 行变更，无业务代码或构建脚本逻辑变更。
- **回迁到 1.4.x 的注意事项**：这是构建工具版本升级，与产品运行时功能无关。1.4.x 作为维护分支：
  - 若 1.4.x 当前使用的 Gradle 版本（可能更老，例如 8.x 较低版本）能够正常构建，则**不必强制回迁**，避免引入新的构建环境变更带来的兼容性风险。
  - 若 1.4.x 在新环境下（如 JDK 升级、CI 镜像更新）出现 8.10.0 的已知问题，则可考虑回迁此升级，因为 8.10.0 → 8.10.1 是补丁版本，向后兼容。
  - 回迁时需同时更新 `gradle-wrapper.properties`、`gradlew`，以及 Windows 下的 `gradlew.bat`（本提交未涉及 `gradlew.bat`，需检查其是否需要同步）。
