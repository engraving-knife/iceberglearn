# 提交 0039：Build: Bump org.testcontainers:testcontainers from 1.17.6 to 1.19.1 (#8780)

## 提交信息

- **序号**：0039 / 4088
- **哈希**：4b020a41a292f6320d30fb7ff2c0ac872f6ef921
- **短哈希**：4b020a41a
- **日期**：2023-10-11 10:48:41 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump org.testcontainers:testcontainers from 1.17.6 to 1.19.1 (#8780)
- **PR/Issue**：#8780

## 总体目的

这个提交由 Dependabot 自动生成，把 Iceberg 依赖的 [Testcontainers](https://github.com/testcontainers/testcontainers-java) 从 `1.17.6` 升级到 `1.19.1`，属于 `version-update:semver-minor`（次版本号升级），跨两个次版本（1.18 → 1.19），风险中等。

Testcontainers 是一个 Java 测试库，允许在 JUnit 测试中以 Docker 容器方式动态拉起真实的外部依赖（如数据库、消息队列、对象存储模拟器等），用于编写可靠的集成测试。在 Iceberg 中，它仅以 `testImplementation` 作用域引入，**不影响生产产物**，只服务于测试代码。具体被两个云存储集成模块使用：

- `iceberg-aws`（[`build.gradle:494`](../../../../build.gradle) `testImplementation libs.testcontainers`）：AWS 集成测试使用 Testcontainers 拉起 LocalStack 等本地云服务模拟器，验证 S3/Glue/DynamoDB 等交互。
- `iceberg-azure`（[`build.gradle:540`](../../../../build.gradle) `testImplementation libs.testcontainers`）：Azure 集成测试使用 Testcontainers 拉起 Azurite 等 ADLS 模拟器，验证 `ADLSFileIO` 的读写删除等行为。

1.17.6 到 1.19.1 之间，Testcontainers 主要改进了对新版 Docker 客户端、Apple Silicon（aarch64）原生镜像、容器网络与生命周期管理的支持，并修复了若干容器启停时的稳定性 bug。由于 Iceberg 的集成测试依赖容器正确启停，升级有助于在 CI 环境减少偶发失败。

## 如何达成设计目的

改动极简：仅在 Gradle 版本目录 [`gradle/libs.versions.toml`](../../../../gradle/libs.versions.toml) 中把 `testcontainers = "1.17.6"` 改为 `testcontainers = "1.19.1"`。两个云模块的测试依赖会自动解析到新版本。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 `org.testcontainers:testcontainers` 的版本从 1.17.6 升级到 1.19.1。

**工作逻辑**：`gradle/libs.versions.toml` 是 Gradle 版本目录（Version Catalog），集中声明所有依赖版本。改动位于版本声明区（约第 69 行），原行 `testcontainers = "1.17.6"` 被改为 `testcontainers = "1.19.1"`。该版本常量在 Iceberg 构建中被以下位置以 `testImplementation` 方式引用：

- `iceberg-aws`（[`build.gradle:494`](../../../../build.gradle)）：AWS 集成测试基座，配合 `libs.s3mock.junit5`、`libs.mockserver.netty` 等一起构建本地 S3/Glue 模拟环境。
- `iceberg-azure`（[`build.gradle:540`](../../../../build.gradle)）：Azure 集成测试基座，验证 `ADLSFileIO`、`AzureProperties` 等的行为。

由于仅用于测试范围，本次升级不进入任何发布产物，对用户无感。主要潜在风险是 Testcontainers 1.18/1.19 对 Docker API 版本与容器运行时（Docker / Podman）的要求可能提升，CI 环境需确认 Docker 可用且版本满足。Dependabot 在 PR 描述中给出了 [release notes](https://github.com/testcontainers/testcontainers-java/releases)、[changelog](https://github.com/testcontainers/testcontainers-java/blob/main/CHANGELOG.md) 和 [commits 对比](https://github.com/testcontainers/testcontainers-java/compare/1.17.6...1.19.1) 供维护者审阅。

## 小结

该提交由 Dependabot 将 Testcontainers 从 1.17.6 升级到 1.19.1，使 Iceberg 的 AWS/Azure 集成测试基座跟进上游改进，提升 CI 中容器化集成测试的稳定性与兼容性，因仅用于测试范围，对生产产物无影响。
