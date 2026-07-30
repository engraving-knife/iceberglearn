# 提交 2290：Build: Bump org.immutables:value from 2.10.1 to 2.11.0 (#13420)

## 提交信息

- **序号**：2290 / 4088
- **哈希**：18822b73cb7db9adce2eb93a29fb44f2681ea455
- **短哈希**：18822b73c
- **日期**：2025-06-30 10:16:24 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump org.immutables:value from 2.10.1 to 2.11.0 (#13420)
- **PR/Issue**：#13420

## 总体目的

本提交由 Dependabot 自动生成，将 Immutables 库从 2.10.1 升级到 2.11.0。Immutables 是一个 Java 注解处理器，用于生成不可变对象、构建器和其他模式代码。在 Iceberg 项目中，Immutables 被广泛用于生成不可变的数据模型类。

此次升级属于 semver-minor（次要版本）更新，意味着包含向后兼容的新功能和改进。保持 Immutables 在最新版本有助于获取最新的代码生成优化和 bug 修复。

## 如何达成设计目的

Dependabot 自动检测到 `gradle/libs.versions.toml` 中声明的 Immutables 版本有新版本可用，创建 PR 将版本号从 `2.10.1` 修改为 `2.11.0`。Iceberg 使用 Gradle 版本目录（version catalog）管理依赖，所有依赖版本集中声明在 `libs.versions.toml` 文件中。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：将 Immutables 库版本从 2.10.1 升级到 2.11.0。

**工作逻辑**：在 Gradle 版本目录文件中，找到 `immutables` 的版本声明行，将版本值从 `2.10.1` 修改为 `2.11.0`。由于版本目录集中管理所有依赖版本，此单行修改会自动影响所有引用该依赖的模块。

## 总结

这是一个常规的依赖版本升级提交，通过 Dependabot 自动完成。Immutables 是 Iceberg 项目中重要的代码生成工具，此次升级确保项目使用最新稳定版本，获取相关改进和修复。
