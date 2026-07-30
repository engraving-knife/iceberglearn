# 提交 2875：Build: Bump testcontainers from 2.0.1 to 2.0.2 (#14595)

## 提交信息

- **序号**：2875 / 4088
- **哈希**：788463b1fb7c7208d603ff316e5034a0dcec245b
- **短哈希**：788463b1f
- **日期**：2025-11-16 00:00:16 -0800
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump testcontainers from 2.0.1 to 2.0.2 (#14595)
- **PR/Issue**：#14595

## 总体目的

Testcontainers 是一个 Java 测试库，用于在测试中提供轻量级、一次性的 Docker 容器实例（如数据库、消息队列、S3 兼容存储等）。Iceberg 项目使用 Testcontainers 进行集成测试，特别是针对 MinIO（S3 兼容存储）等外部服务的测试。

此提交由 Dependabot 自动生成，将 Testcontainers 依赖从 2.0.1 升级到 2.0.2。这是一个补丁版本（patch）升级，通常包含 bug 修复和小的改进，不涉及破坏性 API 变更。升级涉及的三个 Testcontainers 组件同步升级：`org.testcontainers:testcontainers`（核心库）、`org.testcontainers:testcontainers-junit-jupiter`（JUnit 5 集成）、`org.testcontainers:testcontainers-minio`（MinIO 模块）。

## 如何达成设计目的

通过修改 Gradle 版本目录文件 `gradle/libs.versions.toml` 中的 `testcontainers` 版本号，从 `2.0.1` 改为 `2.0.2`。版本目录中的版本号变更会自动传播到所有引用该版本号的依赖项声明中，实现三个 Testcontainers 组件的统一升级。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 testcontainers 版本号。

**工作逻辑**：将 `[versions]` 区段中的 `testcontainers = "2.0.1"` 改为 `testcontainers = "2.0.2"`。该版本变量被 `[libraries]` 区段中的 `testcontainers`、`testcontainers-junit-jupiter`、`testcontainers-minio` 三个库引用，因此一处修改即可同步升级所有三个组件。

## 总结

该提交是 Dependabot 自动生成的依赖升级，将 Testcontainers 从 2.0.1 升级到 2.0.2（补丁版本升级）。这是一次低风险的维护性升级，确保测试基础设施使用最新版本，获取 bug 修复和稳定性改进。三个 Testcontainers 组件（核心、JUnit Jupiter 集成、MinIO 模块）通过版本目录统一升级。
