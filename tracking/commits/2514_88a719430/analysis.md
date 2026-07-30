# 提交 2514：Build: Bump org.immutables:value from 2.11.2 to 2.11.3 (#13839)

## 提交信息

- **序号**：2514 / 4088
- **哈希**：88a7194302fedb96ea75eb573c5803aeb0f3e749
- **短哈希**：88a719430
- **日期**：2025-08-17 22:36:23 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump org.immutables:value from 2.11.2 to 2.11.3 (#13839)
- **PR/Issue**：#13839

## 总体目的

这是一个由 Dependabot 自动生成的依赖升级提交，将 Immutables 库从 2.11.2 升级到 2.11.3。

`org.immutables:value` 是一个 Java 编译时注解处理库，用于生成不可变对象（immutable objects）的代码。Iceberg 项目使用 Immutables 来为配置类、API 模型等生成不可变实现，这有助于确保对象的线程安全性和设计上的不可变性保证。

此次升级属于补丁版本更新（semver-patch），通常包含 bug 修复和小改进。

## 如何达成设计目的

Dependabot 自动检测到 Immutables 有新版本发布，在 `gradle/libs.versions.toml` 中将版本号从 2.11.2 更新为 2.11.3。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：更新 Immutables 依赖版本号。

**工作逻辑**：将 Gradle 版本目录文件中 `immutables` 的版本号从 `2.11.2` 改为 `2.11.3`，使所有引用该库的项目模块自动使用新版本。

## 总结

这是一个常规的依赖维护提交，将 Immutables 编译时代码生成库升级到最新补丁版本。由于 Immutables 在编译时运行，此类升级主要影响代码生成质量，对运行时行为影响较小。
