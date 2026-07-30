# 提交 3190：Build: Bump software.amazon.s3.accessgrants:aws-s3-accessgrants-java-plugin (#15200)

## 提交信息

- **序号**：3190 / 4088
- **哈希**：b3da7d176cd590cd3436397cd58d46f173f90e3a
- **短哈希**：b3da7d176
- **日期**：2026-01-31
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump software.amazon.s3.accessgrants:aws-s3-accessgrants-java-plugin (#15200)
- **PR/Issue**：#15200

## 总体目的

这是 Dependabot 自动发起的依赖升级，将 AWS S3 Access Grants Java 插件（v2）从 `2.3.0` 升至 `2.4.0`。该插件是 AWS SDK for Java v2 的扩展，用于在 S3 客户端上启用 S3 Access Grants 能力——即通过集中的访问授权服务管控对 S3 数据的细粒度访问，而不是直接依赖 IAM 策略。在 Iceberg 的 AWS 集成中，它用于在需要 Access Grants 模式访问 S3 数据的场景下为 S3 FileIO 提供授权链路，属生产依赖。

本次为语义化版本的 **minor** 升级（`2.3.0` → `2.4.0`）。按 SemVer 约定，minor 升级引入向后兼容的新功能，不破坏既有 API。相较于 patch 升级，minor 升级可能带来新的可选特性或行为扩展，但通常不会破坏调用方。预期效果是获取 Access Grants 插件 `2.4.0` 的新能力与缺陷修复，并保持与 AWS SDK BOM 的协同（该插件构建于 AWS SDK v2 之上）。

## 如何达成设计目的

Dependabot 仅修改版本目录文件 `gradle/libs.versions.toml` 中 `awssdk-s3accessgrants` 版本变量。该变量经 `[libraries]` 段对应条目以 `version.ref` 引用，Gradle 解析时自动把新版本应用到 AWS 模块依赖图，无需改动源码。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：将 AWS S3 Access Grants 插件版本从 `2.3.0` 提升至 `2.4.0`。

**工作逻辑**：
将 `[versions]` 段中的 `awssdk-s3accessgrants = "2.3.0"` 改为 `awssdk-s3accessgrants = "2.4.0"`。由于 `[libraries]` 段通过 `version.ref = "awssdk-s3accessgrants"` 引用该变量，这一处改动即可让 Access Grants 插件升级到 `2.4.0`，从而把上游 minor 版本的新特性与修复纳入 Iceberg 的 S3 访问授权链路。

## 总结

本次提交通过单点修改版本变量，将 AWS S3 Access Grants Java 插件 minor 升级到 `2.4.0`，引入上游新功能与修复，属常规依赖维护；由于属 AWS SDK v2 扩展，需与 `awssdk-bom` 版本保持兼容协同。
