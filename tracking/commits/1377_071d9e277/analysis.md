# 提交 1377：Build: Bump kafka from 3.8.1 to 3.9.0 (#11508)

## 提交信息

- **序号**：1377 / 4088
- **哈希**：071d9e277833cfb13421be345c67f4a1876b4840
- **短哈希**：071d9e277
- **日期**：2024-11-14（Thu Nov 14 06:35:32 2024 -0800）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump kafka from 3.8.1 to 3.9.0 (#11508)
- **PR/Issue**：#11508

## 总体目的

由 Dependabot 自动发起的依赖版本升级。Iceberg 的 `iceberg-kafka-connect` 模块依赖 Apache Kafka 的三个构件：`kafka-clients`、`connect-api`、`connect-json`，它们在 `gradle/libs.versions.toml` 中通过统一的版本引用 `version.ref = "kafka"` 管理。Kafka 上游发布了 3.9.0（相对 3.8.1 的 semver-minor 升级），Dependabot 自动将版本号从 3.8.1 提升到 3.9.0，使 Iceberg 的 Kafka Connect 集成跟上上游最新稳定版，获得 bug 修复与改进。

## 如何达成设计目的

Dependabot 修改 `gradle/libs.versions.toml` 中唯一的版本声明行 `kafka = "3.8.1"` 改为 `kafka = "3.9.0"`。由于三个库别名（`kafka-clients`、`kafka-connect-api`、`kafka-connect-json`）都使用 `version.ref = "kafka"`，改这一行即可同时升级三个构件。`iceberg-kafka-connect` 模块的 `build.gradle` 中 `compileOnly libs.kafka.clients` / `libs.kafka.connect.api` / `libs.kafka.connect.json` 与集成测试的 `integrationImplementation libs.kafka.*` 会自动解析到新版本。

## 修改详情

### `gradle/libs.versions.toml`

修改目的：升级 Kafka 依赖版本。

工作逻辑：将 `[versions]` 段中的 `kafka = "3.8.1"` 改为 `kafka = "3.9.0"`。该版本被以下三个库别名引用（均 `version.ref = "kafka"`）：
- `kafka-clients = { module = "org.apache.kafka:kafka-clients", version.ref = "kafka" }`
- `kafka-connect-api = { module = "org.apache.kafka:connect-api", version.ref = "kafka" }`
- `kafka-connect-json = { module = "org.apache.kafka:connect-json", version.ref = "kafka" }`

这三个构件被 `kafka-connect/build.gradle` 用于 `iceberg-kafka-connect`、`iceberg-kafka-connect-events`、`iceberg-kafka-connect-runtime` 三个子模块的 compileOnly 与集成测试 implementation。改动仅 1 行（1 insertion, 1 deletion）。

## 小结

- 成效：Kafka 依赖从 3.8.1 升级到 3.9.0（semver-minor），`iceberg-kafka-connect` 模块及其运行时/事件子模块同步使用新版 Kafka 客户端与 Connect API。
- 影响范围：仅依赖版本号，1 行变更。影响 `iceberg-kafka-connect` 模块构建与运行时依赖，不影响其它模块（core/spark/flink 等不依赖 kafka）。
- 回迁到 1.4.x 的注意事项：**可选回迁，需评估兼容性**：
  1. 这是 semver-minor 升级，Kafka 通常在 minor 版本间保持客户端协议兼容，风险较低。但 1.4.x 作为维护分支，若已发布且 Kafka Connect 运行时环境固定为 3.8.x，升级到 3.9.0 需确认目标部署环境的 Kafka broker 版本兼容性（Kafka 客户端一般可连接同等或更高版本的 broker）。
  2. 若 1.4.x 的 `iceberg-kafka-connect` 模块仍在活跃维护且 CI 通过，可回迁以获得 3.9.0 的修复；若 1.4.x 已接近 EOL 或不再发布 kafka-connect 产物，则无需回迁。
  3. 回迁时只需改 `gradle/libs.versions.toml` 一行，但应跑一遍 `iceberg-kafka-connect` 的集成测试验证无回归。
  4. 注意 Dependabot 升级只保证编译/依赖解析通过，不保证运行时行为完全一致——Kafka 3.9.0 可能有配置项或行为微调，需关注 Kafka 3.9.0 release notes 中影响 connect-api/clients 的变更。
