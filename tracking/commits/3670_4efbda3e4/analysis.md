# 提交 3670：Add Oracle as an Iceberg vendor (#16251)

## 提交信息

- **序号**：3670 / 4088
- **哈希**：4efbda3e45c86223d229af46e67e14a6232b5d10
- **短哈希**：4efbda3e4
- **日期**：2026-05-08 10:30:23 -0700
- **作者**：Alex Miller
- **提交说明**：Add Oracle as an Iceberg vendor (#16251)
- **PR/Issue**：#16251

## 总体目的

这个提交将 Oracle 添加到 Iceberg 网站的供应商页面（vendors.md）。

Iceberg 的 vendors 页面列出了支持 Apache Iceberg 的各类厂商和产品。Oracle 的 Autonomous AI Lakehouse 产品支持 Apache Iceberg，提供多云（OCI、Azure、GCP、AWS）的开放 lakehouse 架构，支持通过 REST Catalog 规范访问 Iceberg 表，并可与 Spark、Trino、Flink 等引擎互操作。本提交将 Oracle 加入供应商列表，按字母序置于 Microsoft OneLake 和 PuppyGraph 之间。

## 如何达成设计目的

在 `site/docs/vendors.md` 中新增 Oracle 的供应商条目，包含产品链接和描述。

## 修改详情

### `site/docs/vendors.md` (+4 lines)

**修改目的**：新增 Oracle 供应商条目。

**工作逻辑**：
```markdown
### [Oracle](https://oracle.com/)

Oracle [Autonomous AI Lakehouse](https://www.oracle.com/autonomous-database/autonomous-ai-lakehouse/) combines the openness of Apache Iceberg with the performance, automation, and security of Oracle Autonomous Database and Exadata. Available across Oracle Cloud Infrastructure (OCI), Microsoft Azure, Google Cloud, and AWS, Oracle provides a multicloud, open lakehouse architecture with high-performance access to Iceberg tables through integration with existing catalogs and support for the Apache Iceberg REST Catalog specification. ...
```
条目描述了 Oracle Autonomous AI Lakehouse 对 Iceberg 的支持，包括多云可用性、REST Catalog 规范支持、与 Spark/Trino/Flink 的互操作性，以及内置 AI、向量搜索、图分析等能力。

## 总结

这是一个纯文档提交，将 Oracle 添加到 Iceberg 网站的供应商列表中，描述了其 Autonomous AI Lakehouse 产品对 Apache Iceberg 的支持。这使 Iceberg 用户了解 Oracle 也是支持 Iceberg 的厂商之一。
