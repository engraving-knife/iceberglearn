# 提交 3161：Build: Bump gradle-wrapper to 8.14.4 (#15143)

## 提交信息

- **序号**：3161 / 4088
- **哈希**：d75c162c9217d61a350a35e8a933edc1cf021b1a
- **短哈希**：d75c162c9
- **日期**：2026-01-26 11:14:29 -0800
- **作者**：zengyz
- **提交说明**：Build: Bump gradle-wrapper to 8.14.4
- **PR/Issue**：#15143

## 总体目的

本提交将 Iceberg 项目使用的 Gradle Wrapper 从 `8.14.3` 升级到 `8.14.4`。Gradle Wrapper（`gradlew` 脚本与 `gradle-wrapper.properties`）是项目约定的 Gradle 构建工具版本锚点，确保所有开发者在本地与 CI 中使用完全一致的 Gradle 版本进行构建。Iceberg 是一个大型多模块 Java 项目，构建过程依赖 Gradle 的依赖解析、增量编译、Shadow 打包、Spotless 格式化、JMH 基准测试等插件，Gradle 版本的稳定性与 bug 修复直接影响构建可靠性与开发者体验。

本次为补丁（patch）级别升级（8.14.3 → 8.14.4），属于 Gradle 8.14 系列的维护性更新，通常只包含 bug 修复与回归修复，不引入新特性或破坏性变更。除更新 wrapper 配置外，作者还同步更新了 `gradlew` 脚本中"当 wrapper jar 缺失时从 GitHub 下载"的回退版本号，使两处版本引用保持一致。

## 如何达成设计目的

修改 `gradle/wrapper/gradle-wrapper.properties` 中的 `distributionUrl` 与 `distributionSha256Sum` 指向 8.14.4 发行包及其校验和，并同步修改 `gradlew` 脚本中回退下载 URL 的版本路径。这样所有执行 `./gradlew` 的环境都会自动拉取并使用 8.14.4 版本的 Gradle。

## 修改详情

### `gradle/wrapper/gradle-wrapper.properties` (+2/-2 lines)

**修改目的**：将 wrapper 指向的 Gradle 发行版从 8.14.3 切换到 8.14.4。

**工作逻辑**：将 `distributionSha256Sum` 从 `bd71102213493060956ec229d946beee57158dbd89d0e62b91bca0fa2c5f3531`（8.14.3 的校验和）改为 `f1771298a70f6db5a29daf62378c4e18a17fc33c9ba6b14362e0cdf40610380d`（8.14.4 的校验和）；将 `distributionUrl` 从 `https\://services.gradle.org/distributions/gradle-8.14.3-bin.zip` 改为 `https\://services.gradle.org/distributions/gradle-8.14.4-bin.zip`。`distributionSha256Sum` 用于在下载发行包后校验完整性，防止篡改或损坏，升级时必须同步更新为对应版本的校验和，否则 wrapper 会因校验失败而拒绝启动。

### `gradlew` (+1/-1 lines)

**修改目的**：同步更新 `gradlew` 脚本中 wrapper jar 缺失时的回退下载版本。

**工作逻辑**：`gradlew` 脚本中有一段容错逻辑：若 `$APP_HOME/gradle/wrapper/gradle-wrapper.jar` 不存在，则用 `curl` 从 GitHub 上对应 Gradle 版本的仓库路径下载该 jar。将 URL 中的版本从 `v8.14.3` 改为 `v8.14.4`，使回退下载的 wrapper jar 与新版本一致。这避免了在缺少 wrapper jar 的环境下回退下载到旧版本 jar 而与 properties 中声明的新 Gradle 版本不匹配的隐患。

## 总结

本提交通过将 Gradle Wrapper 从 8.14.3 升级到 8.14.4 并同步更新回退下载版本，使项目构建工具保持在上游最新 patch 版本，获取 8.14.4 的 bug 修复；作为同系列 patch 升级，预期对 Iceberg 的多模块构建流水线保持兼容，并保证 wrapper 配置（distributionUrl/校验和）与 gradlew 回退逻辑两处版本引用一致。
