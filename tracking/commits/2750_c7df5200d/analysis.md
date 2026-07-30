# 提交 2750：add DuckDB to vendors.md (#14327)

## 提交信息

- **序号**：2750 / 4088
- **哈希**：c7df5200df462764ba0b3e81484243532c941caf
- **短哈希**：c7df5200d
- **日期**：2025-10-14 16:27:34 -0700
- **作者**：guillesd
- **提交说明**：add DuckDB to vendors.md (#14327)
- **PR/Issue**：#14327

## 总体目的

本提交将 DuckDB 添加到 Iceberg 项目的供应商文档列表（vendors.md）中。Iceberg 的 vendors.md 页面用于列举支持 Apache Iceberg 表格式的第三方厂商、引擎和工具，便于用户了解生态中可用的集成方案。

DuckDB 是一个开源的进程内 SQL 数据库，专为快速分析查询而优化。它通过 `duckdb-iceberg` 扩展原生支持 Iceberg，能够 attach 到 Iceberg catalog、查询数据并写入 Iceberg 表。所有功能都在 DuckDB 内部原生实现，无需外部依赖。

将 DuckDB 加入供应商文档有助于提升 Iceberg 生态的可见性，让用户知道除了 Spark、Flink、Trino 等引擎外，还有一个轻量级的分析数据库 DuckDB 可以直接与 Iceberg 表交互。

## 如何达成设计目的

通过在 `site/docs/vendors.md` 文件中，按照字母顺序在 Dremio 与 Estuary 之间插入一个新的 DuckDB 章节。该章节包含 DuckDB 的简要介绍、特点（轻量、可扩展）以及核心扩展 `duckdb-iceberg` 的链接，并说明其原生支持 Iceberg catalog 的 attach、查询和写入功能。

## 修改详情

### `site/docs/vendors.md` (+4/-0 lines)

**修改目的**：在 Iceberg 供应商文档中新增 DuckDB 条目。

**工作逻辑**：在 Dremio 章节之后、Estuary 章节之前，新增 `### DuckDB` 小节，包含一段说明文字。内容描述了 DuckDB 是开源、进程内、面向分析的 SQL 数据库，并通过 `duckdb-iceberg` 扩展原生实现 Iceberg 的 attach、查询和写入能力，无外部依赖。

## 总结

这是一个纯文档类提交，将 DuckDB 作为 Iceberg 生态供应商加入到官方文档中。改动极小（仅 4 行新增），但有助于完善 Iceberg 生态的可见性，向用户告知 DuckDB 已可通过其 iceberg 扩展与 Iceberg 表交互。
