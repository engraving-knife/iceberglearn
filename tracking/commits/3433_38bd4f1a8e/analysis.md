# 提交 3433：Build: Bump testcontainers from 2.0.3 to 2.0.4 (#15716)

## 提交信息

- **序号**：3433 / 4088
- **哈希**：38bd4f1a8e22932f4be57719e16c405a6f30e94d
- **短哈希**：38bd4f1a8e
- **日期**：2026-03-21 23:40:49 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump testcontainers from 2.0.3 to 2.0.4 (#15716)
- **PR/Issue**：#15716

## 总体目的

由 Dependabot 自动生成的依赖升级提交，将 `testcontainers` 从 2.0.3 升级到 2.0.4。`testcontainers` 是一个 Java 测试库，提供轻量级 Docker 容器用于集成测试。此次升级涉及三个 testcontainers 模块：`testcontainers`、`testcontainers-junit-jupiter` 和 `testcontainers-minio`。这是补丁版本升级，包含 bug 修复和小改进。

## 如何达成设计目的

- 在 `gradle/libs.versions.toml` 中将 `testcontainers` 版本从 2.0.3 更新到 2.0.4

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 testcontainers 版本。

**工作逻辑**：
- 将 `testcontainers` 的版本号从 `2.0.3` 更新为 `2.0.4`
- 该版本号被 `testcontainers`、`testcontainers-junit-jupiter` 和 `testcontainers-minio` 三个模块共用

## 总结

本提交是 Dependabot 自动生成的依赖升级，将 `testcontainers`（包括核心库、JUnit Jupiter 集成和 MinIO 模块）从 2.0.3 升级到 2.0.4，属于补丁版本升级。
