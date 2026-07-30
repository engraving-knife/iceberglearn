# 提交 1836：Build: Bump com.gorylenko.gradle-git-properties:gradle-git-properties (#12486)

## 提交信息

- **序号**：1836 / 4088
- **哈希**：456bbe98b0b0982278a61af4c44d32e1c27417e2
- **短哈希**：456bbe98b
- **日期**：2025-03-10 08:57:02 +0100
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump com.gorylenko.gradle-git-properties:gradle-git-properties from 2.4.2 to 2.5.0 (#12486)
- **PR/Issue**：#12486

## 总体目的

本提交由 Dependabot 自动生成，将 Gradle 插件 `com.gorylenko.gradle-git-properties:gradle-git-properties` 从 2.4.2 升级到 2.5.0。这是一个次版本升级（2.4.2 → 2.5.0，semver-minor），属于常规依赖维护。

`gradle-git-properties` 插件用于在构建时生成 `git.properties` 文件，该文件包含当前构建对应的 Git 提交信息（如 commit id、branch、commit time 等）。Iceberg 在构建过程中使用此插件将 Git 元数据嵌入到构建产物中，便于运行时追踪构建来源。升级到 2.5.0 可能带来新的配置选项或 bug 修复。

## 如何达成设计目的

Dependabot 自动识别 `build.gradle` 中 buildscript classpath 里的 `gradle-git-properties` 插件版本声明，将其从 `2.4.2` 更新为 `2.5.0`。该插件在 buildscript 的 dependencies 块中声明，作为构建脚本的依赖，影响整个构建过程。

## 修改详情

### `build.gradle` (修改, 1 line)

**修改目的**：升级 gradle-git-properties 插件版本。

**工作逻辑**：在 `build.gradle` 文件的 buildscript dependencies 块中，将 `classpath 'com.gorylenko.gradle-git-properties:gradle-git-properties:2.4.2'` 改为 `classpath 'com.gorylenko.gradle-git-properties:gradle-git-properties:2.5.0'`。该插件被 Iceberg 的构建脚本应用，用于在构建产物中生成 `git.properties` 文件，记录构建时的 Git 状态信息。版本升级后，插件行为可能略有变化（2.5.0 是 minor 版本升级），但通常保持向后兼容。

## 小结

本提交是 Dependabot 自动生成的构建插件升级，改动仅 1 行，不涉及任何业务代码逻辑。回迁到 1.4.x 时需注意：2.5.0 是 minor 版本升级，应验证构建脚本中插件的配置项是否仍然兼容；如果 1.4.x 的构建配置与 main 分支有差异，需确认插件升级不影响构建流程。
