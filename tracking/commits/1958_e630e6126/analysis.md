# 提交 1958：Updated vendors documentation to add SingleStore (#12708)

## 提交信息

- **序号**：1958 / 4088
- **哈希**：e630e6126ab9cfc3ddeef73f021a056cb612d200
- **短哈希**：e630e6126
- **日期**：2025-04-03 09:00:31 -0700
- **作者**：Andrew Koller
- **提交说明**：Updated vendors documentation to add SingleStore (#12708)
  - Updated vendors documentation to add SingleStore
  - update based on feedback
  - Co-authored-by: Andrew Koller
- **PR/Issue**：#12708

## 总体目的

Iceberg 官网的 vendors（厂商）页面 `site/docs/vendors.md` 列出了集成 Apache Iceberg 的各类厂商产品。SingleStore 是一个高性能、可扩展的分布式 SQL 平台，支持原生读取和管理 Iceberg 表。本提交在该页面新增 SingleStore 的条目，让用户了解 SingleStore 对 Iceberg 的集成能力。

## 如何达成设计目的

在 `site/docs/vendors.md` 中按字母顺序，在 RisingWave 与 Snowflake 之间插入 SingleStore 的章节，包含产品链接与一段描述其 Iceberg 集成能力的说明文字。

## 修改详情

### `site/docs/vendors.md` (修改, +6/-0 lines)

**修改目的**：新增 SingleStore 厂商条目。

**工作逻辑**：在 RisingWave 条目之后、Snowflake 条目之前插入：
```markdown
### [SingleStore](https://singlestore.com/)

SingleStore is a high‑performance, scalable, distributed SQL platform that makes real‑time analytics and transactional processing available at scale. Its native Apache Iceberg integration removes costly ETL steps and powers intelligent, millisecond‑response applications.

By directly reading and [managing](https://docs.singlestore.com/cloud/load-data/data-sources/iceberg-ingest/) data from Iceberg tables, SingleStore unlocks enterprises' dormant data, boost generative AI development, and ensure seamless schema evolution with low‑latency queries. Available [self-managed](https://docs.singlestore.com/db/v8.9/) or in the cloud, it bridges the gap between traditional data lakes and real‑time analytics.
```

## 总结

本提交在 Iceberg 官网 vendors 文档中新增 SingleStore 条目，介绍其作为分布式 SQL 平台对 Iceberg 表的原生读取与管理集成能力。
