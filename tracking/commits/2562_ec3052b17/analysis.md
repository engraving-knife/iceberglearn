# 提交 2562：Docs: Add dltHub to list of vendors (#13920)

## 提交信息

- **序号**：2562 / 4088
- **哈希**：ec3052b178e2f0c3116ba5f4a4e4f8de3a9e30ef
- **短哈希**：ec3052b17
- **日期**：2025-08-25 10:58:36 -0500
- **作者**：Shreyas
- **提交说明**：Docs: Add dltHub to list of vendors (#13920)
- **PR/Issue**：#13920

## 总体目的

该提交将 dltHub 添加到 Iceberg 项目的厂商列表（vendors page）中。Iceberg 的厂商页面列出了支持 Apache Iceberg 的各类商业和开源产品/服务，方便用户了解生态系统中可用的工具和平台。

dltHub 提供了 `dlt`（data load tool）——一个开源的 Python 库，用于构建生产级的数据提取和加载（EL）管道。dlt 支持将各种数据源加载到 Apache Iceberg 表中，具有自动 schema 推断和演进、多种 catalog 支持（SQL、REST、云原生）、灵活部署等特点。将 dltHub 添加到厂商列表有助于 Iceberg 社区了解这一集成选项。

## 如何达成设计目的

- 在 `site/docs/vendors.md` 文件中 Databricks 条目之后新增 dltHub 条目，包含公司简介、主要特性列表和快速入门链接。

## 修改详情

### `site/docs/vendors.md` (+12/-1)

**修改目的**：新增 dltHub 厂商条目。

**工作逻辑**：在 Databricks 和 Dremio 之间插入 dltHub 的描述，包含公司/产品简介、4 个特性要点（Pythonic pipelines、Automated schema management、Catalog support、Flexible deployment）以及快速入门链接。

## 总结

该提交在 Iceberg 厂商页面新增了 dltHub 条目，介绍了 dlt 开源 Python 库将数据加载到 Iceberg 表的能力，扩展了 Iceberg 生态系统的文档覆盖。
