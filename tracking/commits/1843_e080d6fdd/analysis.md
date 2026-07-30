# 提交 1843：Docs: Update recent talks from Iceberg Meetups (#12481)

## 提交信息

- **序号**：1843 / 4088
- **哈希**：e080d6fdd5fe5129c118ce57aa009eef9575edab
- **短哈希**：e080d6fdd
- **日期**：2025-03-12 06:24:13 +0100
- **作者**：sida-shen
- **提交说明**：Docs: Update recent talks from Iceberg Meetups (#12481)
- **PR/Issue**：#12481

## 总体目的

本提交更新了 Iceberg 官网 talks 页面，新增了 29 场来自 Iceberg Meetup 的近期演讲视频链接。这些演讲来自 2024 年 11 月至 2025 年 2 月期间举办的多次 Iceberg Meetup 活动，涵盖了 Iceberg 生态的各个方面，包括 S3 Tables 集成、Catalog 机制、安全性、删除机制、V3 规范、性能优化、流式数据、Rust 实现等主题。

talks 页面是 Iceberg 社区的重要资源，帮助用户了解 Iceberg 的最新发展、最佳实践和社区动态。定期更新演讲列表有助于社区成员获取最新的技术信息和实践经验。

## 如何达成设计目的

在 `site/docs/talks.md` 文件的 "Iceberg Talks" 介绍段落后、原有最早的演讲条目（"Eliminating Shuffles in DELETE, UPDATE, MERGE"）之前，按时间倒序插入 29 个新的演讲条目。每个条目包含标题（带 YouTube 链接）、日期和作者信息。

## 修改详情

### `site/docs/talks.md` (修改)

**修改目的**：新增 29 场 Iceberg Meetup 演讲条目。

**工作逻辑**：在 talks.md 中插入 29 个 `### [标题](YouTube链接)` 格式的演讲条目，每条包含 `**Date**` 和 `**Authors**` 字段。按时间从新到旧排列，涵盖：

- 2025-02-27 的 4 场演讲：Supporting S3 Tables in Daft、Iceberg Catalogs、Security for an Apache Iceberg Lakehouse、The Invisible Ink of Iceberg Deletions
- 2025-02-21 的 5 场演讲：Iceberg table format version - Iceberg v3 spec、Apache Iceberg Case Study in LY Corporation、Conflict resolution mechanism in Iceberg、Getting Started with Iceberg on Snowflake、Databricks Frozen Over
- 2025-01-30 的 13 场演讲：Optimizing Query Performance、Streaming data with Redpanda、Automated ERD construction、From Logs to Insights、Storage-Acceleration、Iceberg Metadata for Variant Support、Fireside Chat、Optimizing Iceberg Query Performance by NDVs、Towards Actionable Metadata、Apache Iceberg V3 and Beyond、Evolution of Position Deletes、Data-Centric AI、Ursa Augmenting Iceberg、Navigating Iceberg Adoption at Pinterest、Bridging Python and Apache Iceberg
- 2024-11-04 的 4 场演讲：Lakekeeper: Rust based Iceberg Catalog、Iceberg at Netflix、How We Implemented the Iceberg Connector in Rust、Accelerate your Iceberg workloads on S3、Lessons From Building Iceberg Capabilities In Daft

## 小结

本提交是纯文档内容更新，新增 29 场演讲视频链接，不涉及任何代码逻辑。回迁到 1.4.x 无风险，可直接应用。
