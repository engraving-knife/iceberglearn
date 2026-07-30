# 提交 4028：Docs: add Collate to vendors.md (#17123)

## 提交信息

- **序号**：4028 / 4088
- **哈希**：7a882c2d34dda1e0fdb4a3b588453ab1788867c4
- **短哈希**：7a882c2d3
- **日期**：2026-07-14 09:41:17 +0200
- **作者**：William E. Murray
- **提交说明**：Docs: add Collate to vendors.md (#17123)
- **PR/Issue**：#17123

## 总体目的

本提交在 Iceberg 网站的厂商列表页（vendors.md）中新增 Collate 公司的条目。Collate 是一个基于 OpenMetadata 构建的 AI 原生数据目录和治理平台，能够将 Apache Iceberg 表整合为统一的治理上下文，提供列级血缘、数据剖析和无代码数据质量测试，通过已查询 Iceberg 的引擎（Trino、Snowflake、BigQuery）连接。

## 如何达成设计目的

在 `site/docs/vendors.md` 中按字母顺序在 Cloudera 和 Confluent 之间插入 Collate 的条目，包含公司简介和产品说明。

## 修改详情

### `site/docs/vendors.md` (+4/-0 lines)

**修改目的**：新增 Collate 厂商条目。

**工作逻辑**：
```markdown
### [Collate](https://www.getcollate.io/)

Collate is an AI-native data catalog and governance platform built on [OpenMetadata](https://open-metadata.org/), the open-source (Apache 2.0) context layer. It brings together every Apache Iceberg table into a single source of governed context — with column-level lineage, data profiling, and no-code data quality tests — by connecting through the engines that already query Iceberg (e.g., Trino, Snowflake, BigQuery).
```

## 总结

这是一次纯文档提交，向 Iceberg 厂商列表中添加 Collate 条目，介绍其基于 OpenMetadata 的数据目录和治理平台对 Iceberg 的支持。属于生态推广类文档维护。
