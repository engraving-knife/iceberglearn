# 提交 2166：Build: Upgrade to Gradle 8.14.1 (#13149)

## 提交信息

- **序号**：2166 / 4088
- **哈希**：69b0bb05aa5747587e7c7fb7658dae7b600a2ce6
- **短哈希**：69b0bb05a
- **日期**：2025-05-27 14:25:45 +0200
- **作者**：JB Onofré
- **提交说明**：Build: Upgrade to Gradle 8.14.1 (#13149)
- **PR/Issue**：#13149

## 总体目的

该提交将项目使用的 Gradle 构建工具从 8.14 版本升级到 8.14.1 版本。Gradle 8.14.1 是 8.14 的补丁版本，通常包含 bug 修复和稳定性改进。保持构建工具的最新版本有助于获得最新的 bug 修复、安全补丁和性能优化，确保构建过程的可靠性。

## 如何达成设计目的

- 更新 `gradle-wrapper.properties` 中的 `distributionUrl` 和 `distributionSha256Sum`，指向 Gradle 8.14.1 的下载地址和校验和。
- 更新 `gradlew` 脚本中 fallback 下载 gradle-wrapper.jar 的 URL，从 v8.14.0 改为 v8.14.1。

## 修改详情

### `gradle/wrapper/gradle-wrapper.properties` (修改, +2/-2 lines)

**修改目的**：更新 Gradle Wrapper 配置以使用 8.14.1 版本。

**工作逻辑**：将 `distributionSha256Sum` 更新为 8.14.1 对应的校验和值，将 `distributionUrl` 从 `gradle-8.14-bin.zip` 改为 `gradle-8.14.1-bin.zip`。

### `gradlew` (修改, +1/-1 lines)

**修改目的**：更新 gradlew 脚本中的 fallback 下载 URL。

**工作逻辑**：将 gradle-wrapper.jar 的 fallback 下载 URL 从 `https://raw.githubusercontent.com/gradle/gradle/v8.14.0/gradle/wrapper/gradle-wrapper.jar` 改为 `https://raw.githubusercontent.com/gradle/gradle/v8.14.1/gradle/wrapper/gradle-wrapper.jar`。

## 总结

这是一个常规的构建工具版本升级提交，将 Gradle 从 8.14 升级到 8.14.1 补丁版本，获取最新的 bug 修复和稳定性改进。
