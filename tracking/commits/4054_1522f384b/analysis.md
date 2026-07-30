# 提交 4054：Site: Add StarTree to vendors documentation (#17197)

## 提交信息

- **序号**：4054 / 4088
- **哈希**：1522f384b6fa8b3e4fcd91dd0bc5a6734b88264a
- **短哈希**：1522f384b
- **日期**：2026-07-16 16:40:05 -0500
- **作者**：James Dilworth
- **提交说明**：Site: Add StarTree to vendors documentation (#17197)
- **PR/Issue**：#17197

## 总体目的

这个提交在 Iceberg 官方网站的厂商（vendors）文档页面中新增 StarTree 的介绍条目。vendors.md 页面列举了支持 Apache Iceberg 的商业产品和平台，帮助用户了解生态中可用的解决方案。StarTree 是一家基于 Apache Pinot 的实时分析平台厂商，能够对 Iceberg 中的数据提供高性能、高并发的查询能力。

通过在文档中加入 StarTree 的介绍，丰富了 Iceberg 生态厂商列表，使需要 SLA 驱动的实时分析（如可观测性、面向客户的分析、异常检测等）的用户能找到适合的解决方案。

## 如何达成设计目的

在 `site/docs/vendors.md` 中按字母顺序在 Starburst 条目之后、StreamNative 条目之前插入 StarTree 的小节，包含厂商名称（带官网链接）和一段描述性文字，说明其平台能力、基于 Pinot 的索引技术、对 Parquet 页级数据的精确抓取，以及适用场景和部署方式。

## 修改详情

### `site/docs/vendors.md` (+6/-0 lines)

**修改目的**：新增 StarTree 厂商条目。

**工作逻辑**：
```markdown
### [StarTree](https://startree.ai/)

StarTree is a real-time analytics platform that is able to deliver consistently fast, highly concurrent queries on data stored in Apache Iceberg. Built on the indexing capabilities of Apache Pinot, StarTree can precisely fetch page-level data from Parquet files, reducing unnecessary scanning and data transfer.

This makes it practical and cost-effective to support [SLA-driven analytics on the lakehouse](...). StarTree can power observability, customer-facing analytics, anomaly detection, and interactive business intelligence workloads without requiring data to be duplicated, pre-aggregated, or materialized into a separate serving system. StarTree is available as a managed cloud service or can be deployed within an [enterprise cloud environment](...). Learn more in the [StarTree Docs](...).
```
条目位于 Starburst 和 StreamNative 之间，保持字母顺序。内容涵盖：平台定位（实时分析）、技术基础（Apache Pinot 索引）、核心能力（页级数据精确抓取、减少扫描）、适用场景（可观测性、面向客户分析、异常检测、BI）、部署方式（托管云服务或企业自部署）。

## 总结

纯文档提交，在 Iceberg 官方网站厂商页面新增 StarTree 条目，丰富了 Iceberg 生态厂商列表。内容介绍了 StarTree 基于 Apache Pinot 的实时分析能力及其对 Iceberg 数据的页级精确查询优势。无代码改动。
