# 提交 2165：Docs: Add Tinybird to the list of vendors and blog posts (#13128)

## 提交信息

- **序号**：2165 / 4088
- **哈希**：5a096b764fa88e4a71d0d53e90aaa58ecb6d6db8
- **短哈希**：5a096b764
- **日期**：2025-05-26 16:56:15 +0200
- **作者**：Víctor Ramírez
- **提交说明**：Docs: Add Tinybird to the list of vendors and blog posts (#13128)
- **PR/Issue**：#13128

## 总体目的

Tinybird 是一个实时数据平台，近期新增了对 Apache Iceberg 的原生支持（通过 ClickHouse 的 `iceberg()` 表函数）。该提交将 Tinybird 添加到 Iceberg 官方文档的厂商列表（vendors）和博客列表（blogs）中，让用户了解 Tinybird 作为 Iceberg 生态系统的集成选项。同时将 Tinybird 添加到文档站点的导航中。这是社区生态系统扩展的常规文档更新。

## 如何达成设计目的

- 在 `docs/mkdocs.yml` 导航中添加 Tinybird 链接条目。
- 在 `site/docs/blogs.md` 中添加 Tinybird 关于 Iceberg 实时分析的博客文章条目。
- 在 `site/docs/vendors.md` 中添加 Tinybird 厂商介绍段落，描述其功能和 Iceberg 集成方式。

## 修改详情

### `docs/mkdocs.yml` (修改, +1 line)

**修改目的**：将 Tinybird 添加到文档导航中。

**工作逻辑**：在导航列表的 Estuary 条目之后、RisingWave 条目之前，添加 `Tinybird: https://www.tinybird.co/docs/forward/get-data-in/table-functions/iceberg` 链接。

### `site/docs/blogs.md` (修改, +5 lines)

**修改目的**：添加 Tinybird 的博客文章到 Iceberg 博客列表。

**工作逻辑**：在博客列表最前面（最新）添加 Tinybird 的博客条目"Real-Time Analytics on Apache Iceberg with Tinybird"，包含发布日期（2025年5月20日）、公司（Tinybird）和作者（Alberto Romeu）信息。

### `site/docs/vendors.md` (修改, +8 lines)

**修改目的**：添加 Tinybird 厂商介绍。

**工作逻辑**：在 Tabular 和 Upsolver 之间插入 Tinybird 厂商段落，描述 Tinybird 是实时数据平台，通过 ClickHouse 的 `iceberg()` 表函数支持原生查询 S3 上的 Iceberg 表，提供低延迟、高并发的数据访问能力。

## 总结

这是一个常规的生态系统文档更新，将新增的 Iceberg 集成厂商 Tinybird 添加到官方文档的导航、博客列表和厂商列表中，丰富了 Iceberg 生态系统的可见性。
