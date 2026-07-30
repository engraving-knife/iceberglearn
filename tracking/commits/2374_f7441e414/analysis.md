# 提交 2374：Build: Bump org.immutables:value from 2.11.0 to 2.11.1 (#13603)

## 提交信息

- **序号**：2374 / 4088
- **哈希**：f7441e414b557415dd3b57a3f611497971fa9dc6
- **短哈希**：f7441e414
- **日期**：2025-07-21 09:12:44 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump org.immutables:value from 2.11.0 to 2.11.1 (#13603)
- **PR/Issue**：#13603

## 总体目的

本提交是由 Dependabot 自动生成的依赖版本升级，将 Immutables 库的 value 模块从 2.11.0 升级到 2.11.1。Immutables 是一个 Java 代码生成库，通过注解处理器自动生成不可变值对象（immutable value objects）的代码，在 Iceberg 项目中用于生成不可变的数据模型类。

此次升级为补丁版本（patch）升级（2.11.0 -> 2.11.1），属于 semver 语义化版本中的兼容性升级，通常包含 bug 修复和小改进，不引入破坏性变更。

## 如何达成设计目的

Dependabot 自动检测到 immutables-value 有新版本可用，通过修改 Gradle 版本目录文件中的版本号声明来完成升级。所有依赖 immutables-value 的模块都会自动引用新版本。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：将 immutables-value 依赖版本从 2.11.0 升级到 2.11.1。

**工作逻辑**：在版本目录文件中，将 `immutables-value = "2.11.0"` 修改为 `immutables-value = "2.11.1"`。Gradle 构建时会自动引用此版本号。

## 总结

本提交是一个常规的依赖版本升级，由 Dependabot 自动完成。仅修改 1 行配置，将 immutables-value 从 2.11.0 升级到 2.11.1。这类补丁版本升级风险极低，是项目维护的常规操作。
