# 提交 2763：Build: bump testcontainers from 1.21.3 to 2.0.1 (#14366)

## 提交信息

- **序号**：2763 / 4088
- **哈希**：80e59d1a3c23f821123f11de5e9f15d1c1879115
- **短哈希**：80e59d1a3
- **日期**：2025-10-18 16:22:45 -0700
- **作者**：sullis
- **提交说明**：Build: bump testcontainers from 1.21.3 to 2.0.1 (#14366)
- **PR/Issue**：#14366

## 总体目的

本提交将 Iceberg 项目使用的 testcontainers 依赖从 1.21.3 升级到 2.0.1，并同步调整了相关子模块的 artifact 坐标以适配 2.0 的新命名规范。

背景在于：testcontainers 是一个用于在测试中提供轻量级 Docker 容器（如 MinIO、数据库等）的 Java 库。Iceberg 在集成测试中使用 testcontainers 来启动 MinIO 等服务。testcontainers 2.0 是一个大版本升级（从 1.x 到 2.x），其中一项破坏性变化是子模块的 artifactId 命名调整：1.x 中 `junit-jupiter` 和 `minio` 这两个子模块的 artifactId 与父 artifact `testcontainers` 同名，而在 2.0 中改为 `testcontainers-junit-jupiter` 和 `testcontainers-minio`，以保持命名一致性。

因此本次升级不仅是版本号变更，还必须同步修改 `libs.versions.toml` 中两个子模块的坐标，否则依赖无法正确解析。

## 如何达成设计目的

在 `gradle/libs.versions.toml` 中：
1. 将 `testcontainers` 版本引用从 `1.21.3` 改为 `2.0.1`。
2. 将 `testcontainers-junit-jupiter` 的 artifact 坐标从 `org.testcontainers:junit-jupiter` 改为 `org.testcontainers:testcontainers-junit-jupiter`。
3. 将 `testcontainers-minio` 的 artifact 坐标从 `org.testcontainers:minio` 改为 `org.testcontainers:testcontainers-minio`。

## 修改详情

### `gradle/libs.versions.toml` (+3/-3 lines)

**修改目的**：升级 testcontainers 版本并适配 2.0 的 artifact 命名。

**工作逻辑**：
- `[versions]` 段：`testcontainers = "1.21.3"` → `testcontainers = "2.0.1"`。
- `[libraries]` 段：
  - `testcontainers-junit-jupiter = { module = "org.testcontainers:junit-jupiter", ... }` → `{ module = "org.testcontainers:testcontainers-junit-jupiter", ... }`。
  - `testcontainers-minio = { module = "org.testcontainers:minio", ... }` → `{ module = "org.testcontainers:testcontainers-minio", ... }`。
  - 主 `testcontainers` 模块坐标 `org.testcontainers:testcontainers` 保持不变（版本随引用升级）。

## 总结

本提交将 testcontainers 从 1.21.3 升级到 2.0.1（大版本升级），并同步调整 `junit-jupiter` 和 `minio` 两个子模块的 artifactId 以适配 2.0 的新命名规范（加 `testcontainers-` 前缀）。testcontainers 2.0 可能引入了 API 变化和行为调整，但由于 Iceberg 仅在测试中使用，且本次改动仅涉及依赖坐标，对生产代码无影响。升级后需确保使用 testcontainers 的集成测试（如 MinIO 相关测试）在 2.0 API 下仍能正常运行。
