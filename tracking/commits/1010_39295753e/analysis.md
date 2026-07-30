# 提交 1010：Build: Bump kafka from 3.7.1 to 3.8.0 (#10797)

## 提交信息

- **序号**：1010 / 4088
- **哈希**：39295753e8e949096236a5dc8b063c77f976650b
- **短哈希**：39295753e
- **日期**：2024-08-02 15:15:06 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump kafka from 3.7.1 to 3.8.0 (#10797)
- **PR/Issue**：#10797

## 总体目的

本提交是由 dependabot 自动生成的依赖升级 PR，将 Iceberg 依赖的 Apache Kafka 版本从 3.7.1 升级到 3.8.0。受影响的 Kafka 制品有三个，全部使用同一个版本号引用：

- `org.apache.kafka:kafka-clients`：Kafka 客户端库，Iceberg 的 Kafka 集成与 Kafka connect 相关模块使用。
- `org.apache.kafka:connect-api`：Kafka Connect API，用于 Iceberg 的 Kafka connect connector。
- `org.apache.kafka:connect-json`：Kafka Connect 的 JSON 序列化支持。

Kafka 3.8.0 是一个 minor 版本升级（semver-minor），通常包含新特性、bug 修复与性能改进，且对 API 保持向后兼容。定期升级 Kafka 版本可以让 Iceberg 跟随 Kafka 社区的修复与改进，避免使用过时依赖。本提交属于常规的依赖维护工作。

## 如何达成设计目的

实现方式极简：所有三个 Kafka 制品在 `gradle/libs.versions.toml` 中都通过同一个版本引用 `kafka`，因此只需把 `[versions]` 段中的 `kafka = "3.7.1"` 改为 `kafka = "3.8.0"`，所有引用 `version.ref = "kafka"` 的库坐标会自动升级到 3.8.0，无需修改任何 `[libraries]` 段或 build.gradle。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：升级 Kafka 版本号。

**工作逻辑**：`[versions]` 段中 `kafka = "3.7.1"` 改为 `kafka = "3.8.0"`。由于 `kafka-clients`、`connect-api`、`connect-json` 三个库坐标均通过 `version.ref = "kafka"` 引用该版本，三者会同步升级到 3.8.0。

## 小结

- **成效**：将 Iceberg 依赖的 `org.apache.kafka:kafka-clients`、`connect-api`、`connect-json` 从 3.7.1 升级到 3.8.0，跟随 Kafka 社区的 minor 版本改进与修复。
- **影响范围**：仅依赖版本声明，1 个文件、1 行改动，无源代码变更。
- **回迁到 1.4.x 的注意事项**：可以回迁，风险低。Kafka 3.8.0 是 minor 升级，API 向后兼容。回迁前需确认 1.4.x 上 Kafka 相关测试（如有 Kafka integration test）能在 3.8.0 下通过；若 1.4.x 上有其它模块对 Kafka 3.7.x 有硬性要求（极少见），需评估。整体属于低风险回迁，但价值也有限——1.4.x 通常只需在出现安全问题时才升级依赖。
