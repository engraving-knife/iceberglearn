# 提交 3200：doc: Update AWS vendor doc (#15222)

## 提交信息

- **序号**：3200 / 4088
- **哈希**：336fc0e48323f80bfa5bf4f42bd3cf32227a5a87
- **短哈希**：336fc0e48
- **日期**：2026-02-03
- **作者**：Shawn Chang
- **提交说明**：doc: Update AWS vendor doc (#15222)
- **PR/Issue**：#15222

## 总体目的

该提交更新了 Iceberg 官网厂商支持页（vendors）中关于 AWS（Amazon Web Services）的描述文字。背景是 AWS 新近推出了 Amazon S3 Tables——一项完全托管 Iceberg 表的服务，提供自动化的表维护、优化与成本管理能力。原厂商文档在描述 AWS 对 Iceberg 的支持时，仅强调 S3 作为底层存储层，并列举 Athena、EMR、Glue 等服务，未提及 S3 Tables 这一原生托管 Iceberg 表能力。

本次更新把 S3 与 S3 Tables 一并写入 AWS 的能力介绍，使文档与 AWS 当前实际提供的产品矩阵保持一致，帮助用户了解可直接使用托管 Iceberg 表的 AWS 入口，而非仅自行在 S3 上搭建。同时将原段末以 S3 为收尾的表述改为以整体 AWS 服务集合收尾，行文更完整。

## 如何达成设计目的

仅修改 `site/docs/vendors.md` 中 AWS 段落的一行文字，重写该段描述：在前端服务列表之前插入对 S3（存储）与 S3 Tables（托管 Iceberg 表）的说明，并调整段尾总结措辞。

## 修改详情

### `site/docs/vendors.md` (+1/-1 lines)

**修改目的**：在 AWS 厂商描述中补充 S3 Tables 托管 Iceberg 表能力并优化行文。

**工作逻辑**：
原段落将 S3 仅作为"底层存储层"在段末提及，并写道"With Amazon S3 as the underlying storage layer, AWS enables a high-performance..."。新段落改为：

- 开篇先点明 `[Amazon S3]` 提供"virtually unlimited, highly durable storage for data lakes"，再介绍 `[Amazon S3 Tables]` 提供"fully managed Iceberg tables with automated maintenance, optimization, and cost management"，把存储与托管表能力前置；
- 随后保留对 Athena、EMR（Spark/Flink/Hive/Presto/Trino）、Glue 的描述，并在 Trino 后补一个 Oxford 逗号使列举更规范；
- 段尾改为"Together, these AWS services enable a high-performance and cost-effective data lakehouse solution powered by Iceberg"，从单一 S3 收尾改为整体 AWS 服务集合收尾。

链接 `https://aws.amazon.com/s3/features/tables/` 指向 S3 Tables 官方介绍页，便于用户进一步查阅。

## 总结

这是一处文档更新，将 AWS 厂商支持页对 S3 与新推出的 S3 Tables（托管 Iceberg 表）的描述补充到位，使文档反映 AWS 当前产品能力，对选择 AWS 作为 Iceberg 部署平台的用户有实际指引价值。改动范围极小，仅一段文字。
