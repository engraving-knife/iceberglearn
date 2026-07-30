# 提交 2912：Build: Bump software.amazon.awssdk:bom from 2.38.7 to 2.39.2 (#14666)

## 提交信息

- **序号**：2912 / 4088
- **哈希**：fe40f5db6c82b300f408fbf5b588edaee4efa8cc
- **短哈希**：fe40f5db6
- **日期**：2025-11-22 23:25:17 -0800
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.38.7 to 2.39.2
- **PR/Issue**：#14666

## 总体目的

这是由 dependabot 自动生成的依赖版本升级提交。AWS SDK for Java 2（`software.amazon.awssdk:bom`）是 Iceberg 项目中用于与 AWS 服务（特别是 S3 存储）交互的核心依赖。该 BOM（Bill of Materials）统一管理 AWS SDK 各模块的版本。此次将版本从 2.38.7 升级到 2.39.2，属于 semver-minor 级别的版本更新，通常包含新功能添加、bug 修复和安全补丁。

保持 AWS SDK 版本最新有助于获取最新的功能改进、性能优化和安全修复，同时确保与 AWS 服务的最新 API 兼容。由于 Iceberg 广泛使用 S3 作为存储后端，AWS SDK 的稳定性对整个项目至关重要。

## 如何达成设计目的

dependabot 通过修改 Gradle 版本目录（version catalog）文件 `gradle/libs.versions.toml` 中 `awssdk-bom` 的版本声明来完成升级。这种集中式版本管理方式使得所有引用 `awssdk-bom` 的模块自动使用新版本，无需逐个修改各模块的构建文件。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：将 AWS SDK BOM 版本从 2.38.7 升级到 2.39.2。

**工作逻辑**：
将 `awssdk-bom = "2.38.7"` 修改为 `awssdk-bom = "2.39.2"`。这是一个跨越 minor 版本的升级（2.38.x 到 2.39.x），可能引入新的 API 和行为变更，但 AWS SDK 2.x 遵循语义化版本，minor 版本升级保持向后兼容。

## 总结

本提交是 AWS SDK for Java 2 的常规依赖版本升级，从 2.38.7 升至 2.39.2。作为 semver-minor 升级，预期向后兼容，可能包含新功能、bug 修复和安全补丁。Iceberg 使用 S3 作为主要存储后端之一，保持 AWS SDK 最新有助于确保稳定性和安全性。
