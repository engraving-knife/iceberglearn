# 提交 3437：Build: Bump software.amazon.awssdk:bom from 2.42.13 to 2.42.18 (#15720)

## 提交信息

- **序号**：3437 / 4088
- **哈希**：56ef45f7d67cea283ab18cc26b39a73fa030951c
- **短哈希**：56ef45f7d6
- **日期**：2026-03-21 23:42:10 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.42.13 to 2.42.18
- **PR/Issue**：#15720

## 总体目的

这是一个由 Dependabot 自动生成的依赖版本升级提交。将 AWS SDK for Java 的 BOM（Bill of Materials）从 2.42.13 升级到 2.42.18，这是一个补丁版本升级，涵盖 5 个小版本的累积更新。

AWS SDK BOM 用于统一管理 AWS SDK 所有模块的版本号，确保各模块之间版本兼容。在 Iceberg 项目中，aws 模块大量使用 AWS SDK 来与 S3、DynamoDB、Glue 等 AWS 服务交互。

## 如何达成设计目的

- Dependabot 自动检测到 AWS SDK BOM 有新版本发布
- 在 `gradle/libs.versions.toml` 版本目录文件中更新版本号
- BOM 的更新会自动传递到所有依赖 AWS SDK 的子模块

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 AWS SDK BOM 版本号。

**工作逻辑**：
- 将 `software.amazon.awssdk:bom` 的版本号从 `2.42.13` 修改为 `2.42.18`
- 该文件是 Gradle 版本目录，集中管理项目所有依赖版本
- BOM 更新后，所有 AWS SDK 组件的版本会统一对齐到 2.42.18

## 总结

这是 Dependabot 自动生成的常规依赖升级提交，将 AWS SDK for Java BOM 从 2.42.13 升级到 2.42.18，属于补丁版本升级，包含 AWS SDK 的 bug 修复和改进。
