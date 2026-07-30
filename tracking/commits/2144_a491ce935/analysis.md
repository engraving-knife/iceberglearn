# 提交 2144：Build: Bump software.amazon.awssdk:bom from 2.31.35 to 2.31.45

## 提交信息

- **序号**：2144 / 4088
- **哈希**：a491ce93568b71a2f7ced839a345c6b86d8300df
- **短哈希**：a491ce935
- **日期**：2025-05-19 08:18:16 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.31.35 to 2.31.45 (#13088)
- **PR/Issue**：#13088

## 总体目的

这个提交是 Dependabot 自动生成的依赖更新，将 AWS SDK for Java 2 的 BOM（Bill of Materials）从 2.31.35 升级到 2.31.45。AWS SDK BOM 用于统一管理所有 AWS SDK 组件的版本，确保各组件版本兼容。这是一个 patch 版本范围内的升级（2.31.35 到 2.31.45），属于版本更新中的 semver-patch 类型，通常包含 bug 修复和安全补丁。

## 如何达成设计目的

1. 在 gradle/libs.versions.toml 中将 AWS SDK BOM 的版本引用从 2.31.35 更新到 2.31.45。

## 修改详情

### `gradle/libs.versions.toml` (修改, +1/-1 line)

**修改目的**：升级 AWS SDK BOM 版本。

**工作逻辑**：将 software.amazon.awssdk:bom 的版本号从 2.31.35 改为 2.31.45。这会自动影响所有依赖 AWS SDK 的模块使用的 AWS SDK 组件版本。

## 总结

这是一个由 Dependabot 自动生成的 AWS SDK BOM patch 版本升级，从 2.31.35 到 2.31.45。属于常规的安全和 bug 修复更新，风险低，保持 AWS SDK 依赖的最新状态。
