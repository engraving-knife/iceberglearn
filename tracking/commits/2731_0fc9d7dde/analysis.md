# 提交 2731：Build: Bump org.scala-lang.modules:scala-collection-compat_2.13

## 提交信息

- **序号**：2731 / 4088
- **哈希**：0fc9d7dde33711200fab69a643a2f972d2c972d2c94235
- **短哈希**：0fc9d7dde
- **日期**：2025-10-11 22:20:16 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump org.scala-lang.modules:scala-collection-compat_2.13
- **PR/Issue**：#14303

## 总体目的

这是由 GitHub Dependabot 自动生成的依赖升级提交。scala-collection-compat 是 Scala 官方提供的兼容性库，用于在不同 Scala 版本之间提供统一的集合 API（特别是 Scala 2.12 与 2.13 之间的集合 API 差异）。Iceberg 的 Spark 集成模块使用 Scala 编写，需要此库来保证跨 Scala 版本的兼容性。

本次升级将该依赖从 2.13.0 升级到 2.14.0，属于 semver-minor（次版本）升级，意味着可能有新功能添加但保持向后兼容。Dependabot 定期检查依赖更新并自动创建 PR，目的是保持项目依赖处于最新状态，及时获取 bug 修复、安全补丁和新特性。

## 如何达成设计目的

Dependabot 通过以下流程完成升级：
1. 检测到 `gradle/libs.versions.toml` 中 `scala-collection-compat` 的版本（2.13.0）有新版本（2.14.0）可用
2. 自动修改版本目录文件中的版本声明
3. 创建 PR 并附带版本对比信息（release notes、commits 链接）

由于 Iceberg 使用 Gradle 版本目录（version catalog）集中管理依赖版本，只需修改一处即可全局生效。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 scala-collection-compat 依赖版本。

**工作逻辑**：将 `scala-collection-compat = "2.13.0"` 修改为 `scala-collection-compat = "2.14.0"`。该文件是 Gradle 版本目录，项目中所有引用 `scala-collection-compat` 的模块会自动使用新版本。

## 总结

这是常规的依赖维护升级，将 Scala 集合兼容库从 2.13.0 升级到 2.14.0。作为 semver-minor 升级，预期向后兼容，风险较低。这类自动化的依赖升级有助于项目保持依赖的健康状态。
