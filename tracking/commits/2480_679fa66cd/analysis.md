# 提交 2480：Build: Bump com.gradleup.shadow:shadow-gradle-plugin from 8.3.8 to 8.3.9 (#13776)

## 提交信息

- **序号**：2480 / 4088
- **哈希**：679fa66cd2857eb2113fef21dc605e0fc4b1a6ca
- **短哈希**：679fa66cd
- **日期**：2025-08-10 21:22:13 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump com.gradleup.shadow:shadow-gradle-plugin from 8.3.8 to 8.3.9 (#13776)
- **PR/Issue**：#13776

## 总体目的

该提交由 Dependabot 自动生成，将 `com.gradleup.shadow:shadow-gradle-plugin` 从 8.3.8 升级到 8.3.9，以获取该 Gradle 插件的最新补丁修复。

Shadow Gradle Plugin 用于创建 fat/uber JAR 文件（将依赖打包到单个 JAR 中）。8.3.9 是一个补丁版本（semver-patch），通常包含 bug 修复和小改进，不引入破坏性变更。Dependabot 定期扫描项目依赖并自动创建 PR 来升级到最新版本，以保持依赖的安全性和稳定性。

## 如何达成设计目的

在 `build.gradle` 的 `buildscript` 依赖块中，将 `shadow-gradle-plugin` 的版本号从 `8.3.8` 改为 `8.3.9`。这是单行版本号修改。

## 修改详情

### `build.gradle` (+1/-1 lines)

**修改目的**：升级 shadow-gradle-plugin 版本。

**工作逻辑**：

修改前：
```groovy
classpath 'com.gradleup.shadow:shadow-gradle-plugin:8.3.8'
```

修改后：
```groovy
classpath 'com.gradleup.shadow:shadow-gradle-plugin:8.3.9'
```

在 `buildscript` 的 `dependencies` 块中更新版本号，Gradle 在构建时会自动拉取新版本的插件。

## 总结

这是一个由 Dependabot 自动生成的依赖升级提交，将 shadow-gradle-plugin 从 8.3.8 升级到 8.3.9（补丁版本）。该提交仅修改一行版本号配置，获取插件的最新 bug 修复，属于常规的依赖维护工作。
