# 提交 2138：Kafka Connect: Add BigQuery Metastore catalog

## 提交信息

- **序号**：2138 / 4088
- **哈希**：acffd4dc5d65303ab71d9a65903891b7b0c249b1
- **短哈希**：acffd4dc5
- **日期**：2025-05-16 20:49:43 +0200
- **作者**：Julien Guitton
- **提交说明**：Kafka Connect: Add BigQuery Metastore catalog (#13041)
- **PR/Issue**：#13041

## 总体目的

这个提交为 Kafka Connect 集成模块添加了 BigQuery Metastore catalog 的支持。Iceberg 的 Kafka Connect 集成允许用户通过 Kafka Connect 框架将 Kafka 消息流式写入 Iceberg 表。此前该模块已支持 REST、Glue、DynamoDB、Hadoop、Nessie、JDBC、Hive 等多种 catalog 类型，但缺少对 BigQuery Metastore catalog 的支持。这个提交补充了这一能力，使得使用 BigQuery 作为元数据存储的用户也能通过 Kafka Connect 将数据写入 Iceberg 表。同时更新了文档和构建配置。

## 如何达成设计目的

1. 在 Kafka Connect 的文档中添加 BigQuery Metastore catalog 的说明和使用示例。
2. 在 kafka-connect 的 build.gradle 中添加 iceberg-bigquery 模块和 Google Cloud BigQuery 相关依赖。
3. 更新文档中关于支持的 catalog 类型列表，加入 BigQuery Metastore。

## 修改详情

### `docs/docs/kafka-connect.md` (修改, +14/-2 lines)

**修改目的**：更新 Kafka Connect 文档，添加 BigQuery Metastore catalog 的说明和配置示例。

**工作逻辑**：
- 更新支持的 catalog 类型列表，在原有列表后添加 "BigQuery Metastore"。
- 在 Hive 示例的说明中补充了 GCS 存储使用 GCSFileIO 的提示。
- 新增了 "BigQuery Metastore example" 章节，提供了完整的配置示例，包括 catalog-impl（指向 BigQueryMetastoreCatalog）、project-id、location、warehouse、io-impl（GCSFileIO）以及 auto-create-props 中的 BigQuery 连接配置。

### `kafka-connect/build.gradle` (修改, +3/-0 lines)

**修改目的**：在 Kafka Connect runtime 模块中添加 BigQuery 相关依赖。

**工作逻辑**：添加了三个依赖：
- `implementation project(':iceberg-bigquery')` - Iceberg 的 BigQuery 模块，包含 BigQueryMetastoreCatalog 实现。
- `implementation 'com.google.cloud:google-cloud-bigquery'` - Google Cloud BigQuery 客户端库。
- `implementation 'com.google.cloud:google-cloud-core'` - Google Cloud 核心库。

## 总结

这个提交为 Kafka Connect 集成模块补充了 BigQuery Metastore catalog 的支持，通过添加依赖和文档使 BigQuery 用户能够通过 Kafka Connect 将数据写入 Iceberg 表。修改范围较小，主要涉及文档更新和构建依赖添加，实际的 BigQueryMetastoreCatalog 实现已在 iceberg-bigquery 模块中存在。
