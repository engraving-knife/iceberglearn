# 提交 3191：Build: Bump com.palantir.gradle.gitversion:gradle-git-version (#15201)

## 提交信息

- **序号**：3191 / 4088
- **哈希**：85245f74ed596be09ded720c6128f89b32fca9c6
- **短哈希**：85245f74e
- **日期**：2026-01-31
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump com.palantir.gradle.gitversion:gradle-git-version (#15201)
- **PR/Issue**：#15201

## 总体目的

这是 Dependabot 自动发起的依赖升级，将 Palantir 的 `gradle-git-version` 插件从 `4.2.0` 升至 `4.3.0`。该插件的作用是从 Git 标签与提交历史自动推导项目版本号，是 Iceberg 构建中用于确定发布版本字符串的工具插件，配置在根 `build.gradle` 的 `buildscript` classpath 中。它影响构建产物的版本标记，而非业务代码逻辑。

本次为语义化版本的 **minor** 升级（`4.2.0` → `4.3.0`）。按 SemVer 约定，minor 升级引入向后兼容的新功能，不破坏既有插件 DSL。预期效果是获取 `gradle-git-version` `4.3.0` 的改进（如对 Git 描述/标签解析的修复或对新版 Git 行为的兼容），同时保持版本推导结果与既有发布流程一致。

## 如何达成设计目的

Dependabot 仅修改根 `build.gradle` 中 `buildscript` 依赖声明里该插件的版本字面量，Gradle 在加载构建脚本时以新版本解析插件，从而应用到版本推导流程。

## 修改详情

### `build.gradle` (+1/-1 lines)

**修改目的**：将 `gradle-git-version` 插件版本从 `4.2.0` 提升至 `4.3.0`。

**工作逻辑**：
在 `buildscript { dependencies { classpath ... } }` 块中，将 `classpath 'com.palantir.gradle.gitversion:gradle-git-version:4.2.0'` 改为 `4.3.0`。该 classpath 声明决定了构建期可用的插件版本，修改后 Gradle 会以 `4.3.0` 加载该插件，使基于 Git 标签的版本号推导运行在更新版本上。

## 总结

本次提交通过更新构建脚本中的插件版本字面量，将版本推导插件 `gradle-git-version` minor 升级到 `4.3.0`，引入上游改进而保持版本推导流程不变，属于构建工具链的常规维护。
