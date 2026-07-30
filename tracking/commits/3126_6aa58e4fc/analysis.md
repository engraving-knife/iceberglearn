# 提交 3126：Build: Bump com.aliyun:credentials-java from 0.3.2 to 0.3.12 (#15076)

## 提交信息

- **序号**：3126 / 4088
- **哈希**：6aa58e4fc994e0aee388e9e56e0be353a6bd55c4
- **短哈希**：6aa58e4fc
- **日期**：2026-01-17
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump com.aliyun:credentials-java from 0.3.2 to 0.3.12 (#15076)
- **PR/Issue**：#15076

## 总体目的

这是一个由 GitHub Dependabot 自动生成的依赖升级提交，目标是把阿里云（Aliyun）的 `com.aliyun:credentials-java` 凭证库从 `0.3.2` 升级到 `0.3.12`。

`credentials-java` 是阿里云 SDK 体系的通用凭证管理库，负责为访问阿里云服务（本仓库中主要是 OSS 对象存储）提供 AK/SK、STS 临时凭证、RAM 角色等多种认证方式的统一封装。在 Iceberg 项目中，该依赖被 `iceberg-aliyun` 模块以 `implementation` 方式引入（见 `build.gradle` 中 `project(':iceberg-aliyun')` 子工程），用于支撑基于阿里云 OSS 的表存储后端。保持凭证库为最新版本有助于获取安全修复和兼容性改进，降低供应链风险。

Dependabot 标注本次升级类型为 `version-update:semver-patch`，即补丁版本升级，按语义化版本约定预期为向后兼容的缺陷修复与小幅增强，不应引入破坏性 API 变更。由于从 0.3.2 直接到 0.3.12 跨越了 10 个补丁版本，说明上游在此期间累积了若干修复，及时升级可避免长期滞后。

## 如何达成设计目的

改动极其聚焦：仅修改 Gradle 版本目录文件 `gradle/libs.versions.toml` 中 `aliyun-credentials-java` 的版本号声明，由依赖消费方 `iceberg-aliyun` 模块在构建时自动解析到新版本，无需改动任何源码。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：将 aliyun-credentials-java 版本从 0.3.2 提升到 0.3.12。

**工作逻辑**：
该文件是 Iceberg 的集中式依赖版本目录（Gradle Version Catalog）。改动位于 `[versions]` 段，将 `aliyun-credentials-java = "0.3.2"` 改为 `aliyun-credentials-java = "0.3.12"`。在 `[libraries]` 段中已有声明 `aliyun-credentials-java = { module = "com.aliyun:credentials-java", version.ref = "aliyun-credentials-java" }`，通过 `version.ref` 引用上述版本号；`build.gradle` 的 `iceberg-aliyun` 子工程再以 `implementation libs.aliyun.credentials.java` 消费它。因此只需改这一处版本常量，整个项目中该依赖即统一升级。

## 总结

本次为纯依赖补丁版本升级，通过更新版本目录中一处版本号，将阿里云凭证库升级到 0.3.12，为 `iceberg-aliyun` 模块带来上游累积的安全与稳定性修复，风险低、影响面局限于 OSS 集成的认证链路。
