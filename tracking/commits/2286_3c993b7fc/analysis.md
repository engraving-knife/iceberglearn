# 提交 2286：Build: Bump com.palantir.gradle.gitversion:gradle-git-version (#13422)

## 提交信息

- **序号**：2286 / 4088
- **哈希**：3c993b7fc84388d1a6ca96829cd63fd04f3cbdd1
- **短哈希**：3c993b7fc
- **日期**：2025-06-30 07:45:55 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump com.palantir.gradle.gitversion:gradle-git-version (#13422)
- **PR/Issue**：#13422

## 总体目的

本提交由 Dependabot 自动生成，将 Palantir 的 `gradle-git-version` Gradle 插件从 3.3.0 升级到 3.4.0。该插件用于从 Git 仓库状态（标签、提交数等）自动推导项目版本号，是 Iceberg 构建脚本中确定版本号的关键依赖。

升级到 3.4.0 可获取上游的改进和缺陷修复，确保版本号推导的可靠性。这是一个构建工具链依赖的次版本升级。

## 如何达成设计目的

- 修改 `build.gradle` 顶部 `buildscript.dependencies` 中的 classpath 依赖，将 `gradle-git-version` 从 `3.3.0` 改为 `3.4.0`。
- 由于该插件在 buildscript classpath 中声明，升级后所有使用该插件的 Gradle 构建配置自动应用新版本。

## 修改详情

### `build.gradle` (+1/-1 lines)

**修改目的**：升级 gradle-git-version 插件版本。

**工作逻辑**：在 `buildscript { dependencies { classpath ... } }` 块中，将 `classpath 'com.palantir.gradle.gitversion:gradle-git-version:3.3.0'` 改为 `classpath 'com.palantir.gradle.gitversion:gradle-git-version:3.4.0'`。该插件在构建配置阶段从 Git 标签和提交历史推导版本号，升级后使用新版插件的逻辑。

## 总结

本提交是常规的 Dependabot 构建插件升级，将 gradle-git-version 从 3.3.0 升级到 3.4.0，一行修改。保持构建工具链依赖的时效性，确保版本号推导的可靠性。
