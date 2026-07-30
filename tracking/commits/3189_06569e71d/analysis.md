# 提交 3189：Build: Bump com.diffplug.spotless:spotless-plugin-gradle (#15199)

## 提交信息

- **序号**：3189 / 4088
- **哈希**：06569e71d52586eefc915e8725b5e21d928ea4ec
- **短哈希**：06569e71d
- **日期**：2026-01-31
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump com.diffplug.spotless:spotless-plugin-gradle (#15199)
- **PR/Issue**：#15199

## 总体目的

这是 Dependabot 自动发起的依赖升级，将 Spotless Gradle 插件从 `8.2.0` 升至 `8.2.1`。Spotless 是 Iceberg 构建链中用于代码格式化的工具插件，配置在根 `build.gradle` 的 `buildscript` classpath 中，负责在构建期对 Java/Scala 等源码统一执行格式化检查与修正，确保全仓库代码风格一致。

本次为语义化版本的 **patch** 升级（`8.2.0` → `8.2.1`）。按 SemVer 约定，patch 升级仅含向后兼容的缺陷修复，不会改变格式化规则或插件 API。因此该升级风险很低，预期效果是获得 Spotless 在 `8.2.1` 中提供的格式化器与 Gradle 集成相关的修复，保持现有格式化配置不变，不影响代码风格判定结果。

## 如何达成设计目的

Dependabot 仅修改根 `build.gradle` 中 `buildscript` 依赖声明里 Spotless 插件的版本字面量，Gradle 在加载构建脚本时会用新版本拉取插件，从而应用到全仓库的格式化任务。

## 修改详情

### `build.gradle` (+1/-1 lines)

**修改目的**：将 Spotless Gradle 插件版本从 `8.2.0` 提升至 `8.2.1`。

**工作逻辑**：
在 `buildscript { dependencies { classpath ... } }` 块中，将 `classpath 'com.diffplug.spotless:spotless-plugin-gradle:8.2.0'` 改为 `8.2.1`。该 classpath 声明决定了构建脚本可用的插件版本，修改后 Gradle 会以 `8.2.1` 解析并应用 Spotless 插件，使格式化检查与 `spotlessCheck`/`spotlessApply` 任务运行在更新版本上。

## 总结

本次提交通过更新构建脚本中的插件版本字面量，将代码格式化工具 Spotless patch 升级到 `8.2.1`，引入上游修复而保持格式化规则不变，属于构建工具链的低风险常规维护。
