# 提交 2474：Docs: Update the Kafka Connect readme (#13484)

## 提交信息

- **序号**：2474 / 4088
- **哈希**：44dff0d853a555ff91b69a366e48c355bbba7487
- **短哈希**：44dff0d85
- **日期**：2025-08-08 05:59:01 -0700
- **作者**：Mickael Maison
- **提交说明**：Docs: Update the Kafka Connect readme (#13484)
- **PR/Issue**：#13484

## 总体目的

该提交对 Kafka Connect 文档进行了一系列修正与改进，包括更新 Kafka Connect 框架的官方链接、修正命令名称、统一 JSON 代码块缩进格式，以及修复拼写错误。

Kafka Connect 文档中存在多处小问题：Kafka Connect 的介绍链接指向 Confluent 文档而非 Apache Kafka 官方文档；创建 topic 的命令缺少 `.sh` 后缀；多处 JSON 配置示例的缩进不一致；部分文本中存在拼写错误（如 `_kafka_metdata_topic` 应为 `_kafka_metadata_topic`）。这些问题虽小，但会影响用户按照文档操作时的准确性和体验。

## 如何达成设计目的

通过逐一修正 `kafka-connect.md` 文档中的问题来实现：

1. **更新链接**：将 Kafka Connect 介绍链接从 Confluent 文档改为 Apache Kafka 官方文档，并将"in and out of Kafka"改为"in and out of Apache Kafka"以强调官方属性。

2. **修正命令**：将 `bin/kafka-topics` 改为 `bin/kafka-topics.sh`，符合实际脚本名称。

3. **统一 JSON 缩进**：将多处 JSON 代码块中 `"name"` 和 `"config"` 的缩进从 0 改为 2 空格，统一为标准 JSON 格式。

4. **修复拼写**：将 `_kafka_metdata_topic` 修正为 `_kafka_metadata_topic`（少了一个 a）。

## 修改详情

### `docs/docs/kafka-connect.md` (+19/-19 lines)

**修改目的**：修正 Kafka Connect 文档中的链接、命令、格式和拼写问题。

**工作逻辑**：

- 第 20-23 行：Kafka Connect 链接从 `https://docs.confluent.io/platform/current/connect/index.html` 改为 `https://kafka.apache.org/documentation/#connect`，"Kafka" 改为 "Apache Kafka"。
- 第 241 行：`bin/kafka-topics` 改为 `bin/kafka-topics.sh`。
- 第 276-360 行：三处 JSON 配置示例中 `"name"` 和 `"config"` 键增加 2 空格缩进，闭合括号 `}` 也调整缩进对齐。
- 第 452-483 行：修正 JSON 示例和 SinkRecord 示例中的缩进，并移除多余的右括号。
- 第 506 行：修复拼写错误 `_kafka_metdata_topic` → `_kafka_metadata_topic`。

## 总结

这是一个纯文档修正提交，对 Kafka Connect 文档进行了链接更新、命令修正、JSON 格式统一和拼写修复。该提交不涉及任何代码修改，仅提升文档的准确性和可读性，使用户能够更顺畅地按照文档操作。
