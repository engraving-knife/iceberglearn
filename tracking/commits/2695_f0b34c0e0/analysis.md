# 提交 2695：Build: Bump org.assertj:assertj-core from 3.27.5 to 3.27.6 (#14201)

## 提交信息

- **序号**：2695 / 4088
- **哈希**：f0b34c0e0c110c6bb8dc0d32abf1979604a1c558
- **短哈希**：f0b34c0e0
- **日期**：2025-09-29 08:58:39 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump org.assertj:assertj-core from 3.27.5 to 3.27.6 (#14201)
- **PR/Issue**：#14201

## 总体目的

Dependabot 自动将测试断言库 `org.assertj:assertj-core` 从 `3.27.5` 升级到 `3.27.6`。AssertJ 是 Iceberg 测试代码中广泛使用的流式断言库，提供丰富的断言 API。本次为 semver-patch 升级，Dependabot 元数据为 `direct:production`、`version-update:semver-patch`，属补丁级更新，通常包含 bug 修复与小改进，保持 API 兼容。

虽然该依赖主要用于测试，但 Dependabot 将其归类为 `direct:production`（这一标签在 Dependabot 语境中通常表示直接声明在生产依赖图中，而非仅测试作用域），实际作用域以构建脚本为准。升级可获取上游最新修复，保持测试基础设施健康。

## 如何达成设计目的

修改 Gradle 版本目录 `gradle/libs.versions.toml` 中 `assertj-core` 别名的版本号，从 `3.27.5` 改为 `3.27.6`。该别名是项目所有模块引用 AssertJ 的统一入口。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 AssertJ Core 版本。

**工作逻辑**：将 `assertj-core = "3.27.5"` 改为 `assertj-core = "3.27.6"`。构建时所有引用该别名的测试模块自动拉取新版本。

## 总结

Dependabot 自动将 AssertJ Core 从 3.27.5 升级到 3.27.6 的补丁级更新，属常规测试依赖维护。改动仅一行版本号，通过 Gradle 版本目录集中生效，风险低，旨在获取上游修复。
