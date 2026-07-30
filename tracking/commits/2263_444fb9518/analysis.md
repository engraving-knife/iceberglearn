# 提交 2263：Build: Bump testcontainers from 1.21.1 to 1.21.2 (#13363)

## 提交信息

- **序号**：2263 / 4088
- **哈希**：444fb9518ce4437fd901d9d9442a415391ade295
- **短哈希**：444fb9518
- **日期**：2025-06-22 11:45:21 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump testcontainers from 1.21.1 to 1.21.2
- **PR/Issue**：#13363

## 总体目的

本提交由 Dependabot 自动生成，将 `testcontainers` Java 依赖从 1.21.1 升级到 1.21.2。Testcontainers 是一个 Java 库，提供轻量级的、一次性的 Docker 容器实例用于集成测试。Iceberg 项目在多个模块的集成测试中使用 Testcontainers 来启动各种存储服务的模拟容器（如 MinIO、Azurite、PostgreSQL 等）。

此次升级涉及 Testcontainers 库的多个组件：`org.testcontainers:testcontainers`（核心库）、`org.testcontainers:junit-jupiter`（JUnit 5 集成）和 `org.testcontainers:minio`（MinIO 容器支持）。这是一个 patch 版本升级（1.21.1 -> 1.21.2），通常包含 bug 修复和改进。

## 如何达成设计目的

- 修改 `gradle/libs.versions.toml` 中 testcontainers 的版本号从 `1.21.1` 改为 `1.21.2`。

## 修改详情

### `gradle/libs.versions.toml` (修改, +1/-1 lines)

**修改目的**：升级 testcontainers 版本。

**工作逻辑**：在 Gradle 版本目录文件中将 testcontainers 的版本号从 `1.21.1` 修改为 `1.21.2`。Gradle 版本目录（libs.versions.toml）是集中管理依赖版本的地方，所有依赖 testcontainers 核心库、junit-jupiter 和 minio 模块的项目都会自动使用新版本。

## 总结

本提交是 Dependabot 自动生成的依赖升级，将 Testcontainers 从 1.21.1 升级到 1.21.2（patch 版本）。仅修改 1 行 Gradle 版本目录配置，属于常规的依赖维护工作，确保集成测试框架保持最新。该升级影响所有使用 Testcontainers 的模块的集成测试。
