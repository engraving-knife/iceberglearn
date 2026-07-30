# 提交 3633：Build: Bump testcontainers from 2.0.4 to 2.0.5 (#16201)

## 提交信息

- **序号**：3633 / 4088
- **哈希**：bd96c74ae3602186b0f46dcaa361e76c1f274bcb
- **短哈希**：bd96c74ae
- **日期**：2026-05-02 23:04:38 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump testcontainers from 2.0.4 to 2.0.5 (#16201)
- **PR/Issue**：#16201

## 总体目的

这个提交将 Testcontainers Java 库从版本 2.0.4 升级到 2.0.5，这是一个 patch 级别的版本更新。

Testcontainers 是一个 Java 测试库，提供轻量级的、一次性的 Docker 容器实例，用于集成测试。Iceberg 使用 Testcontainers 进行需要外部服务（如数据库、MinIO 等）的集成测试。升级到 2.0.5 可以获得最新的 bug 修复和改进。

本次升级影响以下三个 Testcontainers 模块：
- `org.testcontainers:testcontainers`
- `org.testcontainers:testcontainers-junit-jupiter`
- `org.testcontainers:testcontainers-minio`

## 如何达成设计目的

通过 Dependabot 自动生成的 PR，更新 `gradle/libs.versions.toml` 中的 `testcontainers` 版本号。由于所有 Testcontainers 模块共享同一个版本变量，一个修改即可更新所有模块。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 testcontainers 版本。

**工作逻辑**：
```toml
testcontainers = "2.0.5"  # 从 2.0.4 升级
```

## 总结

这是一个 Dependabot 自动依赖升级提交，将 Testcontainers 从 2.0.4 升级到 2.0.5（patch 版本）。作为 patch 级别升级，通常包含 bug 修复，风险较低。Testcontainers 仅用于测试，不影响生产代码。
