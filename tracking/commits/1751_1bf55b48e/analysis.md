# 提交 1751：Docs: Add Apache Amoro docs (#11966)

## 提交信息

- **序号**：1751 / 4088
- **哈希**：1bf55b48e0ac6f663da1d2320373b118f4b004c0
- **短哈希**：1bf55b48e
- **日期**：2025-02-19 08:58:04 +0100
- **作者**：ConradJam
- **提交说明**：Docs: Add Apache Amoro docs (#11966)
- **PR/Issue**：#11966

## 总体目的

本提交旨在为 Iceberg 官方文档新增 Apache Amoro（孵化中）的集成说明。Apache Amoro 是一个基于开放数据湖格式构建的 Lakehouse 管理系统，能够与 Flink、Spark、Trino 等计算引擎协同工作，为 Lakehouse 提供可插拔的表维护功能（如自动优化、数据过期等），帮助数据平台构建流批融合、湖原生架构。

在此之前，Iceberg 官方文档中已有 Presto、Dremio、StarRocks、Amazon Athena 等计算引擎/服务的集成链接，但缺少 Apache Amoro 的相关文档。本提交通过新增一个独立的 Amoro 文档页面并将其添加到 mkdocs 导航中，使 Iceberg 用户能够了解如何使用 Amoro 来管理 Iceberg 表。

## 如何达成设计目的

提交通过两个文件的修改达成目的：
1. 新建 `docs/docs/amoro.md` 文件，详细介绍 Apache Amoro 的核心概念、架构、Self-optimizing 机制、支持的表格式（Iceberg Format、Mixed-Iceberg Format、Mixed-Hive Format）等。
2. 在 `docs/mkdocs.yml` 的导航配置中添加 Amoro 页面入口，使其出现在文档站点的导航菜单中。

## 修改详情

### `docs/docs/amoro.md`（新增, +69 lines）

**修改目的**：新增 Apache Amoro 集成文档页面。

**工作逻辑**：文档包含以下章节：
1. **概述**：介绍 Apache Amoro（孵化中）是 Lakehouse 管理系统，与 Flink/Spark/Trino 协作，提供表维护功能。AMS（Amoro Management Service）提供统一 catalog 服务。
2. **Auto Self-optimizing**：介绍 Amoro 的自动自优化机制，包括文件压缩、去重、排序等。描述了 AMS 检测/规划任务、Optimizer 分布式执行的架构。列出三大核心特性：自动化异步透明、资源隔离与共享、灵活可扩展部署。
3. **Table Format**：说明 Amoro 支持 Iceberg 所有 catalog 类型（REST、Hadoop、Hive、Glue、JDBC、Nessie 等）和存储类型（Hadoop、S3、GCS、ECS、OSS 等），并介绍了三种表格式：
   - **Iceberg Format**：从 Amoro v0.4 起支持 Iceberg v1/v2 格式，通过小文件合并、eq-delete 转 pos-delete 等手段维护表性能。
   - **Mixed-Iceberg Format**：类似数据库聚簇索引，通过 BaseStore（存量数据）+ ChangeStore（变更数据）+ LogStore（缓存层）实现高新鲜度 OLAP。
   - **Mixed-Hive Format**：使用 Hive 表作为 BaseStore、Iceberg 表作为 ChangeStore，兼容 Hive 数据。

### `docs/mkdocs.yml`（修改, +1/-0 lines）

**修改目的**：将 Amoro 文档页面添加到文档站点导航。

**工作逻辑**：在 `nav` 配置的引擎/服务列表中，在 Starrocks 之后、Amazon Athena 之前新增 `Amoro: amoro.md` 一行，使其在文档导航菜单中显示。

## 小结

- **成效**：为 Iceberg 官方文档新增了 Apache Amoro 集成说明，使用户了解如何使用 Amoro 管理 Iceberg 表。
- **影响范围**：仅涉及文档（新增一个文档页面 + 导航配置），不影响代码逻辑。
- **回迁到 1.4.x 的注意事项**：纯文档变更，无代码依赖，可安全回迁。注意后续提交 1754（Docs: Fix refs in Apache Amoro docs）修复了本文档中的引用链接问题，建议一并回迁。
