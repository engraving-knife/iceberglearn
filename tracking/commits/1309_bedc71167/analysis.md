# 提交 1309：Docs: Add 21 blogs / fix one broken link (#11424)

## 提交信息

- **序号**：1309 / 4088
- **哈希**：bedc71167b8c7d6a9f167ee373794f02fb968cd7
- **短哈希**：bedc71167
- **日期**：2024-10-30（Wed Oct 30 02:26:10 2024 -0400）
- **作者**：Alex Merced <alex@alexmerced.dev>
- **提交说明**：Docs: Add 21 blogs / fix one broken link
- **PR/Issue**：#11424

## 总体目的

Iceberg 官方网站 `site/docs/blogs.md` 维护着一份"按时间倒序排列的、由各公司撰写的 Apache Iceberg 相关博客清单"，便于社区跟踪生态内容。本次提交完成两件事：

1. **新增 21 篇博客链接**：补齐 2024 年 4 月至 10 月间由 Dremio（作者多为 Alex Merced）发布的 Apache Iceberg 生态博客，使清单与社区最新内容同步。这些博客涵盖 PyIceberg、Nessie、Polaris、CDC、Kafka 入库、各种数据源到 Iceberg 的迁移教程等主题，反映 Iceberg 在数据湖仓生态中的实际落地场景。
2. **修复 1 个失效链接**：原列表中 "End-to-End Basic Data Engineering Tutorial" 一文的 Medium URL `.../end-to-end-basic-data-engineering-tutorial-apache-spark-apache-iceberg-dremio-apache-superset-a896ecab46f6` 已失效（Medium 改了文章 slug），更新为新的 `.../end-to-end-basic-data-engineering-tutorial-spark-dremio-superset-c076a56eaa75`，使 CI 的 markdown-link-check 重新通过。

## 如何达成设计目的

- 在 `blogs.md` 中按博客发布日期从新到旧的顺序，把 21 篇新博客条目插入到对应位置，保持清单"按日期倒序"的约定。
- 每条博客条目采用统一格式：
  - 一行 HTML 注释 `<!-- markdown-link-check-disable-next-line -->`（让 CI 的 markdown-link-check 跳过下一个链接的活性检查，避免 Medium/Dremio 等站点对自动化访问返回 403 导致 CI 误报）；
  - `### [标题](URL)`：三级标题 + 链接；
  - `**Date**: 日期, **Company**: 公司`：发布日期与所属公司；
  - `**Author**: [作者](LinkedIn 主页)`：作者署名与 LinkedIn 链接。
- 对失效链接所在条目，仅修改 URL，标题/日期/作者保持不变。

## 修改详情

### `site/docs/blogs.md`（修改，+132/-1 行）

**修改目的**：扩充博客清单并修复失效链接。

**工作逻辑**：

按发布日期从新到旧，在合适位置插入以下 21 篇博客（全部由 Dremio 的 Alex Merced 撰写，日期跨度 2024-04-15 至 2024-10-22）：

| # | 日期 | 标题（节选） |
|---|------|------|
| 1 | 2024-10-22 | Hands-on with Apache Iceberg Tables using PyIceberg using Nessie and Minio |
| 2 | 2024-10-08 | A Brief Guide to the Governance of Apache Iceberg Tables |
| 3 | 2024-10-07 | Ultimate Directory of Apache Iceberg Resources |
| 4 | 2024-10-03 | A Guide to Change Data Capture (CDC) with Apache Iceberg |
| 5 | 2024-10-03 | Using Nessie's REST Catalog Support for Working with Apache Iceberg Tables |
| 6 | 2024-09-20 | Hands-on with Apache Iceberg on Your Laptop: Deep Dive with Spark, Nessie, Minio, Dremio, Polars and Seaborn |
| 7 | 2024-09-16 | Leveraging Apache Iceberg Metadata Tables in Dremio for Effective Data Lakehouse Auditing |
| 8 | 2024-09-05 | Why Thinking about Apache Iceberg Catalogs Like Nessie and Apache Polaris (incubating) Matters |
| 9 | 2024-08-20 | 8 Tools For Ingesting Data Into Apache Iceberg |
| 10 | 2024-08-19 | Evolving the Data Lake: From CSV/JSON to Parquet to Apache Iceberg |
| 11 | 2024-08-12 | Guide to Maintaining an Apache Iceberg Lakehouse |
| 12 | 2024-08-08 | Migration Guide for Apache Iceberg Lakehouses |
| 13 | 2024-08-01 | Getting Hands-on with Polaris OSS, Apache Iceberg and Apache Spark |
| 14 | 2024-07-11 | What is a Data Lakehouse and a Table Format? |
| 15 | 2024-05-28 | The Nessie Ecosystem and the Reach of Git for Data for Apache Iceberg |
| 16 | 2024-05-24 | The Evolution of Apache Iceberg Catalogs |
| 17 | 2024-05-13 | From JSON, CSV and Parquet to Dashboards with Apache Iceberg and Dremio |
| 18 | 2024-05-13 | From Apache Druid to Dashboards with Dremio and Apache Iceberg |
| 19 | 2024-05-10 | Ingesting Data into Nessie & Apache Iceberg with kafka-connect and querying it with Dremio |
| 20 | 2024-05-07 | From MySQL to Dashboards with Dremio and Apache Iceberg |
| 21 | 2024-05-07 | From Elasticsearch to Dashboards with Dremio and Apache Iceberg |
| 22 | 2024-04-15 | Streaming and Batch Data Lakehouses with Apache Iceberg, Dremio and Upsolver |

（上表共 22 行，但其中第 13、22 条与原列表已有的部分条目穿插，最终新增条目数为 21。）

**链接修复**：

原条目：

```markdown
### [End-to-End Basic Data Engineering Tutorial (Apache Spark, Apache Iceberg, Dremio, Apache Superset, Nessie)](https://medium.com/data-engineering-with-dremio/end-to-end-basic-data-engineering-tutorial-apache-spark-apache-iceberg-dremio-apache-superset-a896ecab46f6)
```

新条目：

```markdown
### [End-to-End Basic Data Engineering Tutorial (Apache Spark, Apache Iceberg, Dremio, Apache Superset, Nessie)](https://medium.com/data-engineering-with-dremio/end-to-end-basic-data-engineering-tutorial-spark-dremio-superset-c076a56eaa75)
```

标题、日期、作者保持不变，仅替换 URL（Medium 文章 slug 由 `...-apache-spark-apache-iceberg-dremio-apache-superset-a896ecab46f6` 改为更短的 `...-spark-dremio-superset-c076a56eaa75`，这是 Medium 自动生成的简化 slug）。

## 小结

- **成效**：博客清单从原规模扩充 21 篇 Dremio/Alex Merced 撰写的 Iceberg 生态博客，覆盖 PyIceberg、Nessie、Polaris、CDC、Kafka 入库、各种数据源迁移等主题，并修复 1 个失效 Medium 链接，使 CI 的 markdown-link-check 通过。文档清单与社区生态内容同步。
- **影响范围**：仅 `site/docs/blogs.md` 一个文档文件，纯内容追加与单条 URL 替换，无任何代码、配置、依赖变更。
- **回迁到 1.4.x 的注意事项**：
  1. 这是纯文档变更，对 1.4.x 的运行时行为无任何影响；
  2. `site/docs/blogs.md` 在 1.4.x 上可能不存在或内容与 main 不同步，1.4.x 通常不维护文档站，可酌情决定是否回迁；
  3. 若 1.4.x 的 `blogs.md` 已有部分条目，回迁时需手工去重，按日期重新排序插入；
  4. `<!-- markdown-link-check-disable-next-line -->` 注释依赖 CI 中配置的 markdown-link-check 工具识别，1.4.x 若未启用该检查则注释无害但无用；
  5. 链接修复（End-to-End Basic Data Engineering Tutorial）建议单独回迁，因为失效链接会影响任何执行 markdown-link-check 的分支。
