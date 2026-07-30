# 提交 1779：Build: Bump testcontainers from 1.20.4 to 1.20.5 (#12380)

## 提交信息

- **序号**：1779 / 4088
- **哈希**：94983060c930d839b07b81dbc215d0148d26d194
- **短哈希**：94983060c
- **日期**：2025-02-24 14:17:57 +0100
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump testcontainers from 1.20.4 to 1.20.5 (#12380)
- **PR/Issue**：#12380

## 总体目的

这是由 Dependabot 自动生成的依赖升级提交，将 Testcontainers 从 1.20.4 版本升级到 1.20.5 版本。Testcontainers 是一个 Java 测试库，提供轻量级 Docker 容器用于集成测试。Iceberg 项目在集成测试中使用 Testcontainers 来启动各种后端服务容器（如 MinIO 对象存储等）。此次升级为补丁版本升级（semver-patch），涉及三个组件：testcontainers 核心、junit-jupiter 集成和 minio 模块。

## 如何达成设计目的

提交通过更新 `gradle/libs.versions.toml` 文件中 testcontainers 的版本号来完成升级。由于三个 Testcontainers 组件共享同一个版本号变量，只需修改一处即可同时升级所有组件。

## 修改详情

### `gradle/libs.versions.toml`（修改, +1/-1 lines）

**修改目的**：升级 Testcontainers 依赖版本。

**工作逻辑**：将 `testcontainers = "1.20.4"` 修改为 `testcontainers = "1.20.5"`，同时影响 testcontainers、junit-jupiter 和 minio 三个组件。

## 小结

- **成效**：将 Testcontainers 三个组件统一升级到 1.20.5 补丁版本。
- **影响范围**：仅影响使用 Testcontainers 的集成测试。补丁版本升级通常无破坏性变更。
- **回迁到 1.4.x 的注意事项**：低优先级回迁。补丁版本升级风险低。无前置依赖。
