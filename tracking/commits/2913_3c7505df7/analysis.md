# 提交 2913：Build: Bump org.immutables:value from 2.11.6 to 2.11.7 (#14665)

## 提交信息

- **序号**：2913 / 4088
- **哈希**：3c7505df7130705e1d0a31daac3053a9c538599b
- **短哈希**：3c7505df7
- **日期**：2025-11-22 23:25:47 -0800
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump org.immutables:value from 2.11.6 to 2.11.7
- **PR/Issue**：#14665

## 总体目的

这是 dependabot 自动生成的依赖升级提交。`org.immutables:value` 是 Immutables 注解处理库，Iceberg 项目使用它生成不可变值对象（如各种 REST 请求/响应模型、配置对象等）。此次将版本从 2.11.6 升级到 2.11.7，属于 semver-patch 级别更新，通常包含 bug 修复和小改进。保持 Immutables 版本最新有助于获取注解处理器的修复，避免编译期或生成代码中的潜在问题。

## 如何达成设计目的

通过修改 Gradle 版本目录文件 `gradle/libs.versions.toml` 中 `immutables-value` 的版本声明完成升级，所有引用该版本的模块自动使用新版本。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：将 Immutables value 版本从 2.11.6 升级到 2.11.7。

**工作逻辑**：将 `immutables-value = "2.11.6"` 修改为 `immutables-value = "2.11.7"`，patch 级别升级，向后兼容。

## 总结

本提交是 Immutables 库的常规 patch 版本升级（2.11.6 到 2.11.7），预期向后兼容，包含 bug 修复。Immutables 用于生成不可变对象，对 Iceberg 的 REST 模型和配置对象至关重要。
