# 提交 1624：Adding Crunchy Data to Iceberg Vendors list (#12020)

## 提交信息

- **序号**：1624 / 4088
- **哈希**：ce2af527a8712fdf23aa9f5094b40c9e8b4056ce
- **短哈希**：ce2af527a
- **日期**：2025-01-23（Thu Jan 23 10:58:49 2025 -0600）
- **作者**：Elizabeth Christensen <85628096+elizabeth-christensen@users.noreply.github.com>
- **提交说明**：Adding Crunchy Data to Iceberg Vendors list (#12020)
- **PR/Issue**：#12020

## 总体目的

Iceberg 官网维护一个厂商列表页面（`site/docs/vendors.md`），列出基于 Apache Iceberg 构建产品或提供 Iceberg 相关服务的厂商，供用户了解生态中的商业支持选项。该列表按字母序排列各厂商条目，每个条目包含厂商名称（链接到官网）、产品描述与 Iceberg 集成方式。

Crunchy Data 是一家专注于 PostgreSQL 的公司，其产品 Crunchy Data Warehouse 是基于 PostgreSQL 构建的现代数据仓库，支持完全事务性的 Iceberg 表与高性能分析。该产品通过混合查询引擎（PostgreSQL + DuckDB）实现对 Iceberg 表的高性能分析查询，并能直接从 PostgreSQL 数据库创建 Iceberg 表。

本提交把 Crunchy Data 加入 Iceberg 厂商列表，按字母序插入到 Cloudera 与 Dremio 之间，使 Crunchy Data Warehouse 的 Iceberg 支持能力被社区知晓。

## 如何达成设计目的

在 `site/docs/vendors.md` 中 Cloudera 条目之后、Dremio 条目之前，新增一个 `### [Crunchy Data](https://www.crunchydata.com/)` 三级标题段落，包含产品描述（Crunchy Data Warehouse 基于 PostgreSQL、支持事务性 Iceberg 表、通过 Crunchy Bridge 在 AWS 上提供托管服务、混合查询引擎结合 PostgreSQL 与 DuckDB）与相关产品链接。

## 修改详情

### `site/docs/vendors.md`（修改，+4 行）

**修改目的**：新增 Crunchy Data 厂商条目。

**工作逻辑**：在 Cloudera 段落之后插入：

```markdown
### [Crunchy Data](https://www.crunchydata.com/)

[Crunchy Data Warehouse](https://www.crunchydata.com/products/warehouse) is a modern data warehouse built on PostgreSQL. Crunchy Data Warehouse extends unmodified PostgreSQL to provide support for fully transactional Iceberg tables and high performance analytics. Crunchy Data Warehouse is available as a managed service on AWS via [Crunchy Bridge](https://www.crunchydata.com/products/crunchy-bridge), fully managed PostgreSQL as a service. Crunchy Data Warehouse can create Iceberg tables directly from a PostgreSQL database or external URLs and can read, query, and update Iceberg tables using PostgreSQL syntax. Using a hybrid query engine that combines PostgreSQL and DuckDB, Crunchy Data Warehouse enables high performance analytical queries of Iceberg tables.
```

条目按字母序位于 Cloudera（C）与 Dremio（D）之间。包含厂商官网链接、产品链接（Crunchy Data Warehouse）、托管服务链接（Crunchy Bridge），描述了产品的 Iceberg 集成方式（创建、读取、查询、更新 Iceberg 表，使用 PostgreSQL 语法，混合 PostgreSQL + DuckDB 查询引擎）。

## 小结

- **成效**：把 Crunchy Data 加入 Iceberg 官网厂商列表，使 Crunchy Data Warehouse 的 Iceberg 支持能力被社区知晓，丰富 Iceberg 生态展示。
- **影响范围**：仅 `site/docs/vendors.md` 一个文档文件，新增 4 行，无代码影响。
- **回迁到 1.4.x 的注意事项**：回迁零风险，纯文档新增。可直接 cherry-pick。需注意 1.4.x 的 vendors.md 是否与 main 结构一致——若 1.4.x 上该文件已有 Cloudera 与 Dremio 条目，则可在对应位置插入 Crunchy Data 条目。厂商列表是持续更新的社区资源，回迁时按字母序定位插入点即可。该条目内容为厂商产品描述，不涉及技术准确性问题，回迁安全。
