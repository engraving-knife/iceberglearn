# 提交 3329：Build: Bump com.gradleup.shadow:shadow-gradle-plugin (#15483)

## 提交信息

- **序号**：3329 / 4088
- **哈希**：a6573b0bd34dfa48ee0350d4fad03c459ba6b9c6
- **短哈希**：a6573b0bd
- **日期**：2026-02-28 22:09:08 -0800
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump com.gradleup.shadow:shadow-gradle-plugin (#15483)
- **PR/Issue**：#15483

## 总体目的

这是 Dependabot 自动生成的依赖升级提交，将 `com.gradleup.shadow:shadow-gradle-plugin` 从 `8.3.9` 升级到 `8.3.10`。

Shadow Gradle Plugin（由 GradleUp 维护，是原 `com.github.johnrengelman.shadow` 的延续分支）是一个用于创建"fat/uber JAR"的 Gradle 插件，能够将项目依赖与项目自身类合并到一个 JAR 中，并支持依赖重定位（relocation/shading）、去除签名、合并服务文件等能力。在 Iceberg 中，shadow 插件用于构建各运行时（runtime）模块的胖 JAR——例如 `iceberg-spark-runtime`、`iceberg-flink-runtime`、`iceberg-mr` 等。这些 runtime JAR 需要把 Iceberg 自身及其依赖打包为单一可部署产物，同时通过 shading 重定位易冲突的依赖（如 Guava、Jackson 等），以避免与 Spark/Flink/Hive 宿主环境的依赖版本冲突。

值得注意的是，本次改动发生在根 `build.gradle` 的 `buildscript` 依赖块中，即作为构建脚本自身的 classpath 依赖，而非被产品代码引用的库依赖。这意味着 shadow 插件仅在构建期生效，不会进入 Iceberg 的运行时 classpath。Dependabot 仍将其标记为 `direct:production`，是因为它直接影响生产产物的构建。

本次升级属于语义化版本的 **patch（补丁）** 升级（`8.3.9` → `8.3.10`，`update-type: version-update:semver-patch`），仅包含缺陷修复与小的内部改进，不引入 API 破坏性变更。预期影响是获得上游在 fat JAR 打包、依赖重定位或与新版 Gradle 兼容性方面的修复，对 Iceberg 产物的最终内容通常无可见变化。

## 如何达成设计目的

改动仅修改根 `build.gradle` 的 `buildscript.dependencies` 块中 shadow 插件的 classpath 版本字符串。该插件在构建脚本解析阶段被加载，作用于应用了 `shadow` 插件的各 runtime 子模块的打包任务，无需改动各子模块的构建脚本。

## 修改详情

### `build.gradle` (+1/-1 lines)

**修改目的**：将 shadow-gradle-plugin 构建脚本依赖从 8.3.9 提升到 8.3.10。

**工作逻辑**：
在根 `build.gradle` 的 `buildscript.dependencies` 块中，将 `classpath 'com.gradleup.shadow:shadow-gradle-plugin:8.3.9'` 修改为 `classpath 'com.gradleup.shadow:shadow-gradle-plugin:8.3.10'`。该 classpath 声明使 shadow 插件在构建脚本解析时可用，下游 runtime 模块通过 `apply plugin: 'com.gradleup.shadow'`（或 plugins DSL）引用它来执行 fat JAR 打包与依赖重定位。这是一次纯版本号变更，不涉及任何打包配置逻辑调整。

## 总结

本次提交通过 Dependabot 将 shadow-gradle-plugin 从 8.3.9 升级到 8.3.10（patch 级），以获取上游 fat JAR 打包与依赖重定位相关的缺陷修复。改动仅涉及根构建脚本的单行 classpath 版本，风险低，对 Iceberg runtime 产物的内容与功能无破坏性影响，属于日常构建工具链维护的一部分。
