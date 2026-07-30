# 提交 2044：Build: Upgrade to Gradle 8.14

## 提交信息

- **序号**：2044 / 4088
- **哈希**：94240f50e358cf4b068c52b56e3a07ec27f1fb6a
- **短哈希**：94240f50e
- **日期**：2025-04-28 08:24:25 +0200
- **作者**：JB Onofré
- **提交说明**：Build: Upgrade to Gradle 8.14 (#12898)
- **PR/Issue**：#12898

## 总体目的

本提交将 Iceberg 项目的构建工具 Gradle 从 8.13 升级到 8.14。Gradle 8.14 是 Gradle 构建工具的最新版本，包含性能改进、bug 修复和新功能。升级构建工具版本有助于保持项目的现代化并享受最新的构建优化。

## 如何达成设计目的

通过更新 Gradle Wrapper 配置文件来升级 Gradle 版本。Gradle Wrapper 确保所有开发者使用相同的 Gradle 版本。

## 修改详情

### `gradle/wrapper/gradle-wrapper.properties` (修改, +2/-2 lines)

**修改目的**：更新 Gradle Wrapper 指向 8.14 版本。

**工作逻辑**：
- `distributionSha256Sum`：从 `20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78` 更新为 `61ad310d3c7d3e5da131b76bbf22b5a4c0786e9d892dae8c1658d4b484de3caa`（新版本的校验和）
- `distributionUrl`：从 `https\://services.gradle.org/distributions/gradle-8.13-bin.zip` 更新为 `https\://services.gradle.org/distributions/gradle-8.14-bin.zip`

### `gradlew` (修改, +1/-1 lines)

**修改目的**：更新 Gradle Wrapper 脚本中下载 wrapper jar 的 URL。

**工作逻辑**：
将 fallback 下载 URL 从 `https://raw.githubusercontent.com/gradle/gradle/v8.13.0/gradle/wrapper/gradle-wrapper.jar` 更新为 `https://raw.githubusercontent.com/gradle/gradle/v8.14.0/gradle/wrapper/gradle-wrapper.jar`，用于在 wrapper jar 文件不存在时自动下载。

## 总结

本提交将项目的 Gradle 构建工具从 8.13 升级到 8.14，通过更新 Gradle Wrapper 配置（distribution URL 和 SHA256 校验和）和 wrapper 脚本中的 fallback 下载 URL 实现。属于构建基础设施维护。
