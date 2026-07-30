# 提交 0637：Build: Bump kafka from 3.6.1 to 3.7.0

## 提交信息

- **序号**：0637 / 4088
- **哈希**：15e2a16443038f87ecd808c68a764f14030890c8
- **短哈希**：15e2a1644
- **日期**：2024-03-27（Wed Mar 27 09:33:10 2024 -0700）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump kafka from 3.6.1 to 3.7.0 (#9855)
- **PR/Issue**：#9855

## 总体目的

本提交由 GitHub Dependabot 自动生成，将 Apache Kafka 从 3.6.1 升级到 3.7.0。Dependabot 在元数据中把三个受影响制品都标注为 `version-update:semver-minor`（次版本升级）：`org.apache.kafka:kafka-clients`、`org.apache.kafka:connect-api`、`org.apache.kafka:connect-json`（均为 `direct:production`）。这是本次五个依赖升级提交中唯一一次 minor 级别升级，也是改动幅度最大的一类——按 semver 规范，minor 升级允许引入向后兼容的新功能，但通常不破坏既有 API。

Kafka 在 Iceberg 中的角色是 Kafka Connect 集成运行时。Iceberg 的 `:iceberg-kafka-connect:iceberg-kafka-connect` 模块通过 `compileOnly` 引入 `kafka-clients`、`connect-api`、`connect-json` 三个制品，实现将 Kafka Connect Source/Sink Connector 接入 Iceberg 表（即把 Kafka 数据落入 Iceberg 表，或从 Iceberg 表读取数据投递到 Kafka）。`compileOnly` 表明这些 Kafka 制品在编译期可见、但不打进 Iceberg 发布产物——它们由用户在 Connect worker 运行时环境中提供，避免 Iceberg 与 Connect 框架自身携带的 Kafka 版本冲突。这是一套典型的"对接框架"依赖模式。

本次升级的目的：跟进 Kafka 3.7.0 这一新功能版本，让 Iceberg Kafka Connect 集成对齐最新的 Connect 框架 API 与客户端协议，获取 3.7.0 引入的改进（如 KIP 相关的新特性）并降低与新版 Kafka Connect 运行时的兼容性风险。

## 如何达成设计目的

Iceberg 采用 Gradle 版本目录集中管理依赖版本。Kafka 的版本通过单个版本别名 `kafka` 统一声明，再被 `kafka-clients`、`kafka-connect-api`、`kafka-connect-json` 三个库别名以 `version.ref = "kafka"` 引用。因此本次升级只需在 `gradle/libs.versions.toml` 中修改一行：

```toml
- kafka = "3.6.1"
+ kafka = "3.7.0"
```

三个制品共享同一版本别名，一处改动即同步升级，避免版本漂移。这种集中化版本管理对 Kafka 尤其重要：`kafka-clients`、`connect-api`、`connect-json` 三者必须来自同一 Kafka 发布版本（wire protocol 与 Connect API 在同一次发布中对齐），分别指定版本极易引发运行时不一致。

由于是 minor 升级，潜在风险高于 patch：Kafka 3.7.0 可能新增/废弃了 Connect API 中的某些方法、调整了 `SinkTask`/`SourceTask`/`Converter` 等接口语义，或修改了客户端协议默认值。Dependabot 本提交仅改版本号、不附带源码适配，意味着合并时 `:iceberg-kafka-connect` 既有代码在 3.7.0 下仍编译并通过 CI（Iceberg 使用 `compileOnly` 对接 Connect API 的子集落在 3.6→3.7 稳定范围内）。Iceberg 在 `settings.gradle` 中通过 `kafkaVersions` 系统属性条件化包含 `kafka-connect` 模块（`if (kafkaVersions.contains("3"))`），便于在不同 Kafka 大版本下做构建矩阵验证。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：把 Kafka（客户端与 Connect 框架 API）的锁定版本从 `3.6.1` 提升到 `3.7.0`。

**工作逻辑**：

改动位于版本声明区（第 63 行附近），原行 `kafka = "3.6.1"` 被改为 `kafka = "3.7.0"`，上下文如下：

```toml
jaxb-api = "2.3.1"
jaxb-runtime = "2.3.3"
jetty = "9.4.54.v20240208"
junit = "5.10.1"
kafka = "3.7.0"   # 由 3.6.1 升级
kryo-shaded = "4.0.3"
microprofile-openapi-api = "3.1.1"
mockito = "4.11.0"
```

该版本别名被库定义区三个库别名引用（在本提交时刻的 `libs.versions.toml` 中约第 154-156 行）：

- `kafka-clients = { module = "org.apache.kafka:kafka-clients", version.ref = "kafka" }`
- `kafka-connect-api = { module = "org.apache.kafka:connect-api", version.ref = "kafka" }`
- `kafka-connect-json = { module = "org.apache.kafka:connect-json", version.ref = "kafka" }`

三者又被 `kafka-connect/build.gradle` 中 `project(':iceberg-kafka-connect:iceberg-kafka-connect')` 依赖块消费（本提交时刻约第 47-49 行）：

- `compileOnly libs.kafka.clients`——Kafka 客户端，提供生产者/消费者协议支持。
- `compileOnly libs.kafka.connect.api`——Connect 框架 API，提供 `SourceTask`/`SinkTask`/`Connector`/`Converter` 等接口，是 Iceberg Kafka Connect 实现所对接的核心框架。
- `compileOnly libs.kafka.connect.json`——JSON 序列化支持（`JsonConverter` 等）。

`compileOnly` 保证这些制品仅在编译期可见、不进入 Iceberg 发布 jar，由用户 Connect 运行时环境提供。升级后，这三个制品及其传递依赖会按 3.7.0 解析，CI 在 `kafkaVersions` 包含 "3" 时构建并测试 `:iceberg-kafka-connect` 以验证兼容性。

## 小结

本提交是 Dependabot 触发的 Kafka minor 升级：仅修改 `gradle/libs.versions.toml` 一行，把 `kafka` 由 `3.6.1` 升至 `3.7.0`，连带把 `kafka-clients`、`connect-api`、`connect-json` 三个构件升版。Kafka 是 `:iceberg-kafka-connect` 模块对接 Kafka Connect 框架的运行时依赖，以 `compileOnly` 形式消费，不进入发布产物。这是本次五个升级中唯一的 minor 级别升级，改动幅度最大。

- **影响范围**：仅依赖版本声明一处，无源码或测试代码改动；运行时影响为 `:iceberg-kafka-connect` 编译/测试针对 Kafka 3.7.0 API。对 Iceberg 公共 API 无影响。
- **回迁到 1.4.x 的注意事项**：1.4.x 分支**完全没有 Kafka 相关依赖**——1.4.x 的 `gradle/libs.versions.toml` 中不存在 `kafka` 版本别名与 `kafka-clients`/`kafka-connect-api`/`kafka-connect-json` 库别名，`settings.gradle` 也不包含 `kafka-connect` 模块，工程树中无 `kafka-connect/` 目录。换言之整个 `:iceberg-kafka-connect` 模块在 1.4.x 上尚不存在（它是后续在 main 上引入的）。因此本提交**无法 cherry-pick 到 1.4.x**：不仅 `libs.versions.toml` 中找不到 `kafka = "3.6.1"` 这一上下文行，连消费它的模块也缺席。若 1.4.x 需要 Kafka Connect 能力，需整体回迁 `:iceberg-kafka-connect` 模块及其依赖声明，而非单独回迁本升级提交。
