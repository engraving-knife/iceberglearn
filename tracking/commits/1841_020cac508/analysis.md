# 提交 1841：Site: Adds AWS to vendors page (#12468)

## 提交信息

- **序号**：1841 / 4088
- **哈希**：020cac508be54249586edbf20513fd24e146d0f2
- **短哈希**：020cac508
- **日期**：2025-03-11 11:31:03 -0500
- **作者**：Rich Bowen
- **提交说明**：Site: Adds AWS to vendors page (#12468)
- **PR/Issue**：#12468

## 总体目的

本提交在 Iceberg 官网的 vendors 页面中新增了 Amazon Web Services (AWS) 的条目。vendors 页面列出了在产品中支持和集成 Apache Iceberg 的厂商。AWS 作为 Iceberg 生态的重要参与者，其多个服务（Amazon Athena、Amazon EMR、AWS Glue、Amazon S3）都深度集成了 Iceberg，但此前未在 vendors 页面列出。

新增的 AWS 条目详细描述了 AWS 提供的 Iceberg 支持服务套件，包括：Amazon Athena（无服务器交互式查询引擎）、Amazon EMR（与 Spark/Flink/Hive/Presto/Trino 集成）、AWS Glue（数据集成、schema 演进、分区管理）、Amazon S3（底层存储层）。这有助于用户了解 AWS 生态中 Iceberg 的使用方式。

## 如何达成设计目的

在 `site/docs/vendors.md` 文件中，在现有内容之前（Bodo 条目之前）新增一个 `### [Amazon Web Services (AWS)](https://aws.com)` 章节，包含一段描述性文字和多个外链。内容经过多人协作审查（Co-authored by Tom Tanaka，并 Incorporate Ajantha's feedback）。

## 修改详情

### `site/docs/vendors.md` (修改)

**修改目的**：新增 AWS 厂商条目。

**工作逻辑**：在 vendors.md 的介绍段落之后、Bodo 条目之前，插入一个 `### [Amazon Web Services (AWS)](https://aws.com)` 三级标题章节。章节内容为一段描述性文字，包含以下外链：
- AWS 官网：`https://aws.com`
- AWS Iceberg 服务总览：`https://aws.amazon.com/what-is/apache-iceberg/#seo-faq-pairs#what-aws-services-support-iceberg`
- Amazon Athena：`https://aws.amazon.com/athena/`
- Amazon EMR：`https://aws.amazon.com/emr/`
- AWS Glue：`https://aws.amazon.com/glue/`
- Amazon S3：`https://aws.amazon.com/s3/`

描述文字涵盖：Athena 的无服务器查询能力、EMR 与 Spark/Flink/Hive/Presto/Trino 的集成、Glue 的数据集成和 schema 演进/维护/优化/分区管理、S3 作为底层存储层构建高性能数据湖仓。

## 小结

本提交是纯文档内容新增，在 vendors 页面添加 AWS 条目，不涉及任何代码逻辑。回迁到 1.4.x 无风险，可直接应用。
