# 提交 2223：Build: Upgrade to Gradle 8.14.2 (#13259)

## 提交信息

- **序号**：2223 / 4088
- **哈希**：5b40f9f88f8ca8d76f9b57a66ae701a1c918681c
- **短哈希**：5b40f9f88
- **日期**：2025-06-07 09:19:46 +0200
- **作者**：JB Onofré
- **提交说明**：Build: Upgrade to Gradle 8.14.2 (#13259)
- **PR/Issue**：#13259

## 总体目的

这个提交是构建工具升级，将项目的 Gradle 版本从 8.14.1 升级到 8.14.2。Gradle 8.14.2 是一个补丁版本，通常包含 bug 修复和小的改进，不涉及破坏性变更。定期升级构建工具版本是良好的维护实践，可以获得最新的 bug 修复、安全补丁和性能改进。该升级确保 Iceberg 项目使用最新稳定的 Gradle 版本进行构建。

## 如何达成设计目的

- 更新 `gradle-wrapper.properties` 中的 `distributionUrl` 指向 gradle-8.14.2-bin.zip。
- 更新 `distributionSha256Sum` 为 8.14.2 版本对应的校验和。
- 更新 `gradlew` 脚本中下载 gradle-wrapper.jar 的 URL 指向 v8.14.2 标签。

## 修改详情

### `gradle/wrapper/gradle-wrapper.properties` (修改, +2/-2 lines)

**修改目的**：更新 Gradle wrapper 分发版本。

**工作逻辑**：将 `distributionUrl` 从 `gradle-8.14.1-bin.zip` 改为 `gradle-8.14.2-bin.zip`，同时更新 `distributionSha256Sum` 为新版本对应的哈希值（`7197a12f450794931532469d4ff21a59ea2c1cd59a3ec3f89c035c3c420a6999`）。

### `gradlew` (修改, +1/-1 line)

**修改目的**：更新 gradlew 脚本中下载 wrapper jar 的 URL。

**工作逻辑**：将下载 `gradle-wrapper.jar` 的 URL 从 `https://raw.githubusercontent.com/gradle/gradle/v8.14.1/gradle/wrapper/gradle-wrapper.jar` 改为 `https://raw.githubusercontent.com/gradle/gradle/v8.14.2/gradle/wrapper/gradle-wrapper.jar`。

## 总结

该提交是构建工具补丁版本升级，将 Gradle 从 8.14.1 升级到 8.14.2。改动仅涉及 wrapper 配置文件和 gradlew 脚本中的版本号和 URL 更新，无功能性代码变更。属于常规的构建依赖维护。
