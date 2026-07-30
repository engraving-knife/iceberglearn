# 提交 1441：Docs: Add RisingWave (#11642)

## 提交信息

- **序号**：1441 / 4088
- **哈希**：3a8bf57ede8a2499fb2b4a62447a0927e6ca6135
- **短哈希**：3a8bf57ed
- **日期**：2024-11-28（Thu Nov 28 15:04:04 2024 +0800）
- **作者**：hengm3467 <100685635+hengm3467@users.noreply.github.com>
- **提交说明**：Docs: Add RisingWave (#11642)
- **PR/Issue**：#11642
- **Co-author**：Fokko Driesprong <fokko@apache.org>
- **作用模块**：`docs/docs/risingwave.md`（新增）+ `docs/mkdocs.yml`（导航登记）

## 总体目的

Iceberg 官方文档站（基于 mkdocs 构建）维护一份「集成伙伴」文档集合，每篇介绍一个支持 Iceberg 表的第三方引擎/平台（如 Daft、Trino、ClickHouse 等），并在 `docs/mkdocs.yml` 的 `nav` 列表中登记，使其出现在网站导航上。这是 Iceberg 生态展示与对外接入指南的重要窗口。

RisingWave 是一个 Postgres 兼容的流式 SQL 数据库，专注于实时事件流数据处理，支持通过内置的 source/sink connector 读取与写入 Iceberg 表（批量读、流式写），并支持多种 catalog（rest、jdbc/sql、glue、storage、hive）。在本次提交前，Iceberg 官方文档没有 RisingWave 的接入指南，用户需要去 RisingWave 自己的文档查找 Iceberg 集成方法，缺少在 Iceberg 官网统一发现入口。

本提交新增一篇 RisingWave 集成文档，介绍其与 Iceberg 的集成能力、支持的特性、catalog 类型，并给出写入与读取 Iceberg 表的 SQL 示例；同时在 mkdocs 导航中登记，使该文档出现在 Iceberg 网站的导航菜单中。

## 如何达成设计目的

1. **新增 `docs/docs/risingwave.md`**：按 Iceberg 文档既有格式（YAML front matter + Apache License 注释 + 章节正文）撰写一篇 RisingWave 集成指南，包含：项目简介、支持特性（批量读/流式写、Iceberg V2、S3-compatible 存储）、支持的 catalog 列表、写入 Iceberg 表的 `CREATE SINK` SQL 示例（含 `create_table_if_not_exists` 参数说明）、读取 Iceberg 表的 `CREATE SOURCE` SQL 示例与查询示例。文档通过引用 RisingWave 官方文档（source/sink connector 与 catalog 文档）作为深入参考。
2. **登记到 `docs/mkdocs.yml`**：在 `nav` 列表的引擎清单中、`Daft: daft.md` 之后新增一行 `- RisingWave: risingwave.md`，使该文档成为网站导航的一项。

改动是纯文档新增，不涉及任何代码、构建或测试逻辑。

## 修改详情

### `docs/docs/risingwave.md`（新增，92 行）

**修改目的**：提供 RisingWave 与 Iceberg 集成的接入指南。

**工作逻辑**：文档结构如下：
- YAML front matter：`title: "RisingWave"`；
- Apache License 2.0 注释块；
- 一级标题 `# RisingWave`，简介 RisingWave（Postgres 兼容、实时事件流处理、可消费百万级事件/秒）；
- `## Supported Features`：批量读 + 流式写 Iceberg 表，链接到 RisingWave 的 source/sink connector 文档；
- `## Table Formats and Warehouse Locations`：仅支持 Iceberg V2 表格式 + S3-compatible 对象存储作为 warehouse；
- `## Catalogs`：列出支持的 catalog（`rest`、`jdbc`/`sql`、`glue`、`storage`、`hive`），链接到 RisingWave 的 catalog 文档；
- `## Getting Started`：
  - `### Writing Data to Iceberg Tables`：`CREATE SINK` 示例，从 `rw_data` 表/MV sink 到 Iceberg 表 `t1`，使用 `upsert` 类型 + `primary_key`，配置 `catalog.type=storage`、`warehouse.path=s3a://...` 与 S3 凭证；并说明自 RisingWave 2.1 起支持 `create_table_if_not_exists` 参数；
  - `### Reading from Iceberg Tables`：`CREATE SOURCE` 示例，从 Iceberg 表 `t1` 创建 source，配置 `catalog.type=storage`、warehouse 与 S3 凭证；并给出 `SELECT * FROM iceberg_t1_source;` 查询示例。

文件末尾注意：`No newline at end of file`（最后一行 `SELECT * FROM iceberg_t1_source;` 后无换行），与文档其它部分风格略有差异，但不影响 mkdocs 渲染。

### `docs/mkdocs.yml`

**修改目的**：把新文档登记到网站导航。

**工作逻辑**：在 `nav:` 的引擎清单中、`Daft: daft.md` 之后、`ClickHouse: ...` 之前插入一行：
```yaml
+  - RisingWave: risingwave.md
```
仅 1 行新增，无其它改动。

## 小结

- **成效**：新增 RisingWave 与 Iceberg 集成的接入指南文档，介绍其批量读/流式写能力、支持的 catalog 与 Iceberg V2/S3-compatible 限制，并给出 `CREATE SINK`/`CREATE SOURCE` 的 SQL 示例；同时在 mkdocs 导航中登记，使该文档出现在 Iceberg 网站导航中，便于用户发现与接入。共 2 个文件、新增 93 行。
- **影响范围**：仅文档（`docs/docs/risingwave.md` 新增 + `docs/mkdocs.yml` 1 行）；无代码、构建或测试影响。
- **回迁到 1.4.x 的注意事项**：这是文档新增，与产品版本功能无关。1.4.x 维护分支通常不单独维护网站文档（网站内容由 main 分支统一发布），无需回迁。即便 1.4.x 分支的 `docs/` 目录与 main 不同步，也不影响 1.4.x 的发布产物。如确需在 1.4.x 分支上保留该文档，可整体复制 `risingwave.md` 与 `mkdocs.yml` 的登记行，无兼容性风险。
