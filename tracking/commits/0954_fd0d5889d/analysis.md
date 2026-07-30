# 提交 0954：Build: Bump org.testcontainers:testcontainers from 1.19.8 to 1.20.0 (#10730)

## 提交信息

- **序号**：0954 / 4088
- **哈希**：fd0d5889d9f0d9cbf70c53205fd37a8a219c09f7
- **短哈希**：fd0d5889d
- **日期**：2024-07-22（Mon Jul 22 08:41:28 2024 +0200）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump org.testcontainers:testcontainers from 1.19.8 to 1.20.0 (#10730)
- **PR/Issue**：#10730

## 总体目的

这是由 GitHub Dependabot 自动生成的依赖升级提交。Testcontainers 是一个用于在测试中临时启动 Docker 容器（如数据库、消息队列、对象存储模拟器等）的 Java 库，Iceberg 在集成测试中广泛使用它来启动 MinIO、PostgreSQL、Kafka 等容器以验证与各类外部系统的集成行为。

本次提交将 `org.testcontainers:testcontainers` 从 1.19.8 升级到 1.20.0（semver minor 版本升级）。1.20.0 是一个 minor 升级版本，通常会包含新特性、bug 修复和潜在的依赖更新。Dependabot 提交说明中也明确指出 update-type 为 `version-update:semver-minor`，属于向后兼容的版本升级。目的是保持测试基础设施依赖的最新状态，获取上游修复与改进，避免长期停留在旧版本上积累技术债。

## 如何达成设计目的

实现方式为修改 Gradle 版本目录文件 `gradle/libs.versions.toml`，将其中 `testcontainers` 的版本声明从 `1.19.8` 改为 `1.20.0`。所有引用该版本号的模块在构建时自动同步升级，无需修改任何业务或测试代码。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 Testcontainers 依赖版本从 1.19.8 升级到 1.20.0。

**工作逻辑**：仅修改版本目录中的一行声明：

```diff
-testcontainers = "1.19.8"
+testcontainers = "1.20.0"
```

修改后，所有通过 `${libs.testcontainers}` 引用该版本号的模块（Iceberg 各集成测试模块，如 S3、JDBC、Kafka 等集成测试）在构建时拉取 1.20.0 版本的 testcontainers 库。

## 小结

- **成效**：完成 Testcontainers 从 1.19.8 到 1.20.0 的 minor 版本升级，使测试基础设施依赖保持最新。
- **影响范围**：仅修改 `gradle/libs.versions.toml` 一个文件，1 行改动。影响所有使用 testcontainers 的集成测试模块，但不改变 Iceberg 自身生产代码逻辑。
- **回迁到 1.4.x 的注意事项**：纯测试依赖升级，**通常适合回迁**，风险较低。回迁前应确认 1.4.x 分支的集成测试在 testcontainers 1.20.0 下仍能通过（1.20.0 是 minor 升级，一般向后兼容，但需关注 Docker API 兼容性与容器镜像版本要求）。若 1.4.x 的 CI 环境的 Docker 版本较旧，需验证兼容性。
