# 提交 2358：[docs] Add Confluent to vendors page (#13512)

## 提交信息

- **序号**：2358 / 4088
- **哈希**：d3d5662aee19dd1799fd12740c64eadcb7f8da8b
- **短哈希**：d3d5662a
- **日期**：2025-07-15 16:26:42 +0200
- **作者**：Robin Moffatt
- **提交说明**：[docs] Add Confluent to vendors page (#13512)
- **PR/Issue**：#13512

## 总体目的

这个提交将 Confluent 添加到 Iceberg 官方文档的厂商页面中。Confluent 通过其 Tableflow 产品提供从 Apache Kafka 主题到 Apache Iceberg 表的托管服务，自动处理 schema 演进（通过 Schema Registry 集成）和表维护任务（如 compaction）。

Iceberg 厂商页面列出支持 Iceberg 的商业产品。Confluent 的 Tableflow 是一个与 Iceberg 相关的数据集成方案，将其添加到列表中方便用户发现可用的 Iceberg 集成服务。

## 如何达成设计目的

在厂商页面的 Cloudera 条目之后、Crunchy Data 条目之前插入 Confluent 条目，保持字母顺序。

## 修改详情

### `site/docs/vendors.md` (+4/-0 lines)

**修改目的**：新增 Confluent 厂商条目。

**工作逻辑**：在 Cloudera 段落之后插入 `### [Confluent](https://confluent.io)` 标题和描述段落，介绍 Confluent 的 Tableflow 产品——一个将 Kafka 主题数据流式传输到 Iceberg 表的托管服务，支持通过 Schema Registry 自动处理 schema 演进和自动执行 compaction 等表维护任务。位置选择在 "C" 字母段（Cloudera 之后、Crunchy Data 之前）以保持字母排序。

## 总结

该提交将 Confluent 及其 Tableflow 产品收录到 Iceberg 厂商页面，方便用户发现该 Kafka-to-Iceberg 集成方案。纯文档变更，无代码改动。
