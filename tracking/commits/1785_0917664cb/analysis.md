# 提交 1785：Build: Upgrade to Gradle 8.13 (#12398)

## 提交信息

- **序号**：1785 / 4088
- **哈希**：0917664cbec89c614422faf9320f3cdcd89cd878
- **短哈希**：0917664cb
- **日期**：2025-02-25 17:42:10 +0100
- **作者**：JB Onofré
- **提交说明**：Build: Upgrade to Gradle 8.13 (#12398)
- **PR/Issue**：#12398

## 总体目的

这个提交将项目的 Gradle 构建工具从 8.12.1 版本升级到 8.13 版本。Gradle 是 Iceberg 项目使用的主要构建工具，负责依赖管理、编译、测试、打包等构建流程。Gradle 8.13 是一个次版本升级，可能包含性能改进、新功能和 bug 修复。保持构建工具的最新版本有助于获得更好的构建性能和兼容性。

## 如何达成设计目的

提交通过更新 Gradle Wrapper 配置文件来完成版本升级。Gradle Wrapper 是 Gradle 的版本管理机制，它确保所有开发者使用相同版本的 Gradle 来构建项目。

## 修改详情

### `gradle/wrapper/gradle-wrapper.properties`（修改, +2/-2 lines）

**修改目的**：更新 Gradle Wrapper 指向新版本。

**工作逻辑**：
- 将 `distributionSha256Sum` 从 `8d97a97984f6cbd2b85fe4c60a743440a347544bf18818048e611f5288d46c94`（8.12.1 的校验和）更新为 `20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78`（8.13 的校验和）。
- 将 `distributionUrl` 从 `https\://services.gradle.org/distributions/gradle-8.12.1-bin.zip` 更新为 `https\://services.gradle.org/distributions/gradle-8.13-bin.zip`。
- `distributionSha256Sum` 用于验证下载的 Gradle 发行版的完整性，确保下载的版本未被篡改。

### `gradlew`（修改, +1/-1 lines）

**修改目的**：更新 gradlew 脚本中下载 gradle-wrapper.jar 的 URL。

**工作逻辑**：
- 将 gradle-wrapper.jar 的下载 URL 从 `https://raw.githubusercontent.com/gradle/gradle/v8.12.1/gradle/wrapper/gradle-wrapper.jar` 更新为 `https://raw.githubusercontent.com/gradle/gradle/v8.13.0/gradle/wrapper/gradle-wrapper.jar`。
- 这个 URL 用于在 gradle-wrapper.jar 文件不存在时自动下载它，确保构建环境的完整性。

## 小结

- **成效**：将项目构建工具 Gradle 从 8.12.1 升级到 8.13，获取新版本的改进和修复。
- **影响范围**：影响整个项目的构建流程，但不影响运行时代码逻辑。所有开发者和 CI 环境都会使用新版本 Gradle。
- **回迁到 1.4.x 的注意事项**：中等优先级回迁。Gradle 版本升级通常向后兼容，但需验证 1.4.x 分支的构建脚本在新版本 Gradle 下能正常工作。回迁时需同时更新 `gradle-wrapper.properties` 和 `gradlew` 两个文件。无前置代码依赖。
