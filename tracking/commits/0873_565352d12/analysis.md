# 提交 0873：Build: Upgrade to gradle 8.8 (#8486)

## 提交信息

- **序号**：0873 / 4088
- **哈希**：565352d12be30735d9224f894099defd6cd45b09
- **短哈希**：565352d12
- **日期**：2024-06-25 17:29:23 +0200
- **作者**：JB Onofré
- **提交说明**：Build: Upgrade to gradle 8.8 (#8486)
- **PR/Issue**：#8486

## 总体目的

本提交将 Iceberg 项目所使用的 Gradle 构建工具版本从 8.1.1 升级到 8.8。这是常规的构建工具升级，目的是跟进 Gradle 上游的新版本，获得性能改进、Bug 修复与新特性，并使构建链路保持现代化。

与此同时，本提交还顺带完成了 Gradle 插件 `gradle-revapi`（用于二进制兼容性检查）从 Palantir 维护的 `com.palantir.gradle.revapi:gradle-revapi:1.7.0` 切换到 `io.github.nastra.gradle.revapi:gradle-revapi:1.8.1`。这是因为 Palantir 已停止维护该插件，社区 fork 版本由 nastra 接手维护，且需要兼容新版 Gradle 8.8。

## 如何达成设计目的

实现方式分三部分：

1. **升级 Gradle wrapper**：修改 `gradle/wrapper/gradle-wrapper.properties`，将 `distributionUrl` 由 `gradle-8.1.1-bin.zip` 改为 `gradle-8.8-bin.zip`，同步更新 `distributionSha256Sum` 校验和，确保下载到的发行版完整可信。
2. **升级 gradlew 启动脚本**：脚本本身由 Gradle 自动生成，本次随 wrapper 升级一并更新到 Gradle 8.8 自带的版本，包含若干兼容性修复（如 `command -v java` 替代 `which java`、`CDPATH` 处理、shellcheck disable 注释调整、JVM 参数转义说明改进等），以及自动下载 `gradle-wrapper.jar` 时引用的源码 tag 由 `v8.1.1` 改为 `v8.8.0`。
3. **替换 revapi 插件坐标**：在 `build.gradle` 中将 `classpath 'com.palantir.gradle.revapi:gradle-revapi:1.7.0'` 替换为 `classpath 'io.github.nastra.gradle.revapi:gradle-revapi:1.8.1'`，将 `apply plugin: 'com.palantir.revapi'` 替换为 `apply plugin: 'io.github.nastra.revapi'`。同时新增一段配置：`tasks.named("revapiAnalyze").configure { dependsOn(":iceberg-common:jar") }`，让 `revapiAnalyze` 任务显式依赖于 `iceberg-common:jar`，避免在新版插件下出现依赖产物未先生成的时序问题。

## 修改详情

### `build.gradle`

**修改目的**：将 revapi 插件从 Palantir 版本切换到 nastra fork 版本，并修正新插件下任务依赖关系。

**工作逻辑**：
- `buildscript.classpath` 中将 `com.palantir.gradle.revapi:gradle-revapi:1.7.0` 改为 `io.github.nastra.gradle.revapi:gradle-revapi:1.8.1`，确保构建脚本能够解析到 nastra 维护的新版本插件。
- `subprojects` 块中，对处于 `REVAPI_PROJECTS` 列表内的子项目，将插件 id 从 `com.palantir.revapi` 改为 `io.github.nastra.revapi`，与新坐标保持一致。
- 在 `revapi` 配置块之后追加 `tasks.named("revapiAnalyze").configure { dependsOn(":iceberg-common:jar") }`，强制 `revapiAnalyze` 任务在执行前先构建 `iceberg-common` 模块的 jar 产物。这是新插件引入的必要修正：nastra 版插件对任务依赖推断更严格，需要显式声明才能拿到被分析模块的 jar。

### `gradle/wrapper/gradle-wrapper.properties`

**修改目的**：将 Gradle wrapper 指向 8.8 版本。

**工作逻辑**：
- 移除原注释行 `# checksum was taken from https://gradle.org/release-checksums`。
- 将 `distributionSha256Sum` 更新为 8.8 版本对应的校验和 `a4b4158601f8636cdeeab09bd76afb640030bb5b144aafe261a5e8af027dc612`。
- 将 `distributionUrl` 更新为 `https\://services.gradle.org/distributions/gradle-8.8-bin.zip`。

### `gradlew`

**修改目的**：同步升级 Gradle 自动生成的 Unix 启动脚本至 8.8 版本。

**工作逻辑**：脚本由 Gradle wrapper 任务自动生成，主要变化包括：
- 模板源 URL 由 `subprojects/plugins/...` 路径更新为 `platforms/jvm/plugins-application/...`（Gradle 内部代码结构重组后的新路径）。
- `APP_HOME` 计算处增加注释，说明丢弃 `cd` 输出以避免 `$CDPATH` 干扰（对应 gradle#25036 修复）。
- 自动下载 `gradle-wrapper.jar` 的源码 tag 由 `v8.1.1` 改为 `v8.8.0`。
- 用 `command -v java` 替代 `which java`，并调整为 `if ! ... then ... fi` 结构。
- shellcheck disable 注释新增 `SC2039`，JVM 参数收集说明重写。

## 小结

- **成效**：将项目构建工具升级到 Gradle 8.8，并将已停止维护的 Palantir revapi 插件替换为社区接手的 nastra fork 版本（1.8.1），保证了 revapi 二进制兼容性检查能在新版 Gradle 下继续工作。
- **影响范围**：仅影响构建基础设施，涉及 `build.gradle`、`gradle/wrapper/gradle-wrapper.properties`、`gradlew` 三个文件，不改动任何生产代码或测试代码。
- **回迁到 1.4.x 的注意事项**：属于构建工具升级，可酌情回迁。回迁前需确认 1.4.x 分支当前所用的 Gradle 版本以及 revapi 插件坐标是否兼容；若 1.4.x 已使用相近版本的 Gradle 与 nastra 版 revapi 插件，则无需回迁。Gradle 版本跳跃较大时（8.1.1 → 8.8）需注意某些插件或构建脚本的兼容性，建议回迁后完整跑一次构建验证。
