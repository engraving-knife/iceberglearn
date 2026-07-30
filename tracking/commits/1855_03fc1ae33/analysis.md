# 提交 1855：Kafka Connect: Add config for transactional ID prefix (#11780)

## 提交信息

- **序号**：1855 / 4088
- **哈希**：03fc1ae33096716e9afdc7c4f1dce91de48f32bc
- **短哈希**：03fc1ae33
- **日期**：2025-03-14 08:21:41 -0700
- **作者**：Thomas Jaeckle
- **提交说明**：Kafka Connect: Add config for transactional ID prefix (#11780)
- **PR/Issue**：#11780

## 总体目的

Iceberg Kafka Connect sink 在协调器（coordinator）提交阶段会创建一个 Kafka 生产者用于写控制/提交消息。为了保证 exactly-once 语义，这个生产者必须用稳定的 `transactional.id` 初始化（Kafka 用 `transactional.id` 做事务 fencing，防止僵尸生产者）。

此前 transactional id 的构成是 `name + transactionalSuffix`，其中 `name` 是 connector 名称、`transactionalSuffix` 是已有的可配置后缀。这带来两个问题：

1. 在某些部署环境（如多租户 Kafka 集群、共享 transactional.id 命名空间）下，不同集群或不同环境的 connector 可能产生相同的 transactional id 前缀部分，导致 transactional.id 冲突，触发 Kafka 的 transaction fencing，使生产者被意外中止。
2. 运维侧无法在 transactional.id 前面加一个隔离前缀（如租户 ID、环境名、集群名），只能靠改 connector name 或 suffix 来规避，灵活性差。

本提交新增配置项 `iceberg.coordinator.transactional.prefix`，允许用户为协调器生产者的 transactional id 指定一个可选前缀。新的事务 id 构成变为 `transactionalPrefix + name + transactionalSuffix`，默认前缀为空字符串（与改动前行为完全一致），从而在不破坏向后兼容的前提下提供隔离能力。

## 如何达成设计目的

1. 在 `IcebergSinkConfig` 中新增常量 `TRANSACTIONAL_PREFIX_PROP = "iceberg.coordinator.transactional.prefix"`，通过 `configDef.define(...)` 注册为 STRING 类型、默认 null、Importance.LOW 的配置项。
2. 新增 `transactionalPrefix()` 访问方法：读取配置值，若为 null 则返回空字符串 `""`（保证默认行为与改动前一致）。
3. 在 `Channel` 构造函数中，把 `transactionalId = name + config.transactionalSuffix()` 改为 `transactionalId = config.transactionalPrefix() + name + config.transactionalSuffix()`，把前缀拼到最前面。
4. 在 `docs/docs/kafka-connect.md` 的配置表里新增一行文档。

## 修改详情

### `docs/docs/kafka-connect.md` (修改, +1 line)

**修改目的**：文档化新配置项。

**工作逻辑**：在 commit 相关配置表里新增一行：`iceberg.coordinator.transactional.prefix | Prefix for the transactional id to use for the coordinator producer, default is to use no/empty prefix`。

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/IcebergSinkConfig.java` (修改, +17 lines)

**修改目的**：注册新配置项并提供访问方法。

**工作逻辑**：
- 新增常量 `TRANSACTIONAL_PREFIX_PROP = "iceberg.coordinator.transactional.prefix"`。
- 在 `configDef.define(...)` 序列中新增一项：类型 `STRING`、默认 `null`、`Importance.LOW`、描述 "Optional prefix of the transactional id for the coordinator"。
- 新增 `public String transactionalPrefix()`：`getString(TRANSACTIONAL_PREFIX_PROP)`，若为 null 返回 `""`，否则返回配置值。这样调用方无需处理 null。

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/channel/Channel.java` (修改, +1/-1 line)

**修改目的**：把前缀拼入 transactional id。

**工作逻辑**：`String transactionalId = config.transactionalPrefix() + name + config.transactionalSuffix();`。前缀在前、name 居中、suffix 在后，由 `clientFactory.createProducer(transactionalId)` 用拼好的 id 初始化生产者。默认前缀为空，行为与改动前一致。

## 小结

- **成效**：用户可通过 `iceberg.coordinator.transactional.prefix` 为协调器生产者的 transactional id 加前缀，实现多租户/多环境下的 transactional.id 隔离，避免 fencing 冲突。默认空前缀保证向后兼容。
- **影响范围**：kafka-connect 模块 2 个源文件（+18/-1）+ 1 个文档文件。仅影响协调器生产者的 transactional id 构成，不影响数据写入路径。
- **回迁到 1.4.x 的注意事项**：建议回迁，前提是 1.4.x 已包含 kafka-connect 模块且 `IcebergSinkConfig`/`Channel` 结构与 main 接近。改动是纯增量（新增配置 + 拼接前缀），无前置依赖，回迁风险低。需确认 1.4.x 的 `Channel` 构造函数中 transactional id 拼接逻辑与 main 一致。
