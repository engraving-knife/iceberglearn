# 提交 2287：Build: Bump testcontainers from 1.21.2 to 1.21.3 (#13416)

## 提交信息

- **序号**：2287 / 4088
- **哈希**：cce5a28cf79d8833e845aa431fe313c91c46c169
- **短哈希**：cce5a28cf
- **日期**：2025-06-30 07:46:21 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump testcontainers from 1.21.2 to 1.21.3 (#13416)
- **PR/Issue**：#13416

## 总体目的

本提交由 Dependabot 自动生成，将 Testcontainers 依赖从 1.21.2 升级到 1.21.3。Testcontainers 是一个 Java 库，提供轻量级、一次性的 Docker 容器实例用于集成测试。Iceberg 项目在测试中使用 Testcontainers 运行各种数据库和服务的容器化实例（如 PostgreSQL、MinIO、MySQL 等）以进行集成测试。

这是一次补丁版本升级（1.21.2 → 1.21.3），通常包含缺陷修复和小的兼容性改进。定期升级有助于确保集成测试的稳定性和与最新 Docker 环境的兼容性。

## 如何达成设计目的

- 修改 `gradle/libs.versions.toml` 中 `testcontainers` 版本变量，从 `1.21.2` 改为 `1.21.3`。
- 通过版本目录集中管理，所有引用该变量的 testcontainers 模块依赖自动应用新版本。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 testcontainers 版本号。

**工作逻辑**：将 `testcontainers = "1.21.2"` 改为 `testcontainers = "1.21.3"`。所有通过 `libs.testcontainers` 及其子模块坐标（如 `libs.testcontainers.postgresql`、`libs.testcontainers.minio` 等）引用该版本的依赖会自动解析为新版本，确保测试中使用的 Testcontainers 库一致升级。

## 总结

本提交是常规的 Dependabot 依赖升级，将 Testcontainers 从 1.21.2 升级到 1.21.3，仅需一行版本目录修改。保持测试基础设施依赖的时效性，确保集成测试的稳定性。
