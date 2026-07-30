# 提交 2352：Docs: Add BladePipe to list of vendors and blog posts (#13510)

## 提交信息

- **序号**：2352 / 4088
- **哈希**：07afd84080b323c37cb11cbc657fb719891ed91c
- **短哈希**：07afd8408
- **日期**：2025-07-14 14:58:56 -0600
- **作者**：ChocZoe
- **提交说明**：Docs: Add BladePipe to list of vendors and blog posts (#13510)
- **PR/Issue**：#13510

## 总体目的

这个提交将 BladePipe 添加到 Iceberg 官方文档的厂商列表和博客列表中，是一个纯文档变更。BladePipe 是一个实时端到端数据集成工具，支持从 MySQL、Oracle、PostgreSQL、SQL Server、Kafka 等数据源将数据集成到 Iceberg 表中。

Iceberg 文档站点维护着一个厂商（vendors）页面，列出支持 Iceberg 的商业和开源产品。当新的集成工具支持 Iceberg 后，社区会将其添加到该列表，方便用户发现可用的集成方案。BladePipe 此前已实现对 Iceberg 的支持（支持 AWS Glue + S3、Nessie + MinIO/S3、REST Catalog + MinIO/S3 等组合），本提交将其正式收录到官方文档。

## 如何达成设计目的

改动分四部分：新增 BladePipe 专属文档页面、将其加入 mkdocs 导航、在博客列表添加相关文章、在厂商页面添加条目。

## 修改详情

### `docs/docs/bladepipe.md` (+119/-0 lines, 新文件)

**修改目的**：创建 BladePipe 的专属文档页面。

**工作逻辑**：新页面包含 BladePipe 的产品介绍（实时数据集成工具，40+ 连接器，亚秒级延迟）、支持的数据源（MySQL/MariaDB/AuroraMySQL、Oracle、PostgreSQL、SQL Server、Kafka）、支持的 Catalog 和存储组合（AWS Glue + S3、Nessie + MinIO/S3、REST Catalog + MinIO/S3），以及从 MySQL 到 Iceberg（Glue + S3）的入门指南。

### `docs/mkdocs.yml` (+1/-0 lines)

**修改目的**：将 BladePipe 页面加入文档导航。

### `site/docs/blogs.md` (+5/-0 lines)

**修改目的**：在博客列表中添加 BladePipe 相关文章。

### `site/docs/vendors.md` (+4/-0 lines)

**修改目的**：在厂商页面添加 BladePipe 条目。

## 总结

该提交将 BladePipe 数据集成工具收录到 Iceberg 官方文档的厂商列表和博客列表中，并为其创建了专属文档页面，方便用户了解和使用该工具进行 Iceberg 数据集成。纯文档变更，无代码改动。
