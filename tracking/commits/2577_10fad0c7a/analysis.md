# 提交 2577：Docs: Add OLake (ELT) (#13929)

## 提交信息

- **序号**：2577 / 4088
- **哈希**：10fad0c7a8620af858c2d338d5ddf30a6e9c4968
- **短哈希**：10fad0c7a
- **日期**：2025-08-29 13:05:55 -0500
- **作者**：Badal Prasad Singh
- **提交说明**：Docs: Add OLake (ELT) (#13929)
- **PR/Issue**：#13929

## 总体目的

此次提交将 OLake 添加到 Iceberg 文档站点（mkdocs）的导航菜单中。与提交 2570（将 OLake 添加到 `site/docs/vendors.md` 厂商列表）不同，此提交针对的是 `docs/mkdocs.yml` 配置文件中的 `nav` 导航结构，在集成工具列表里新增 OLake 的文档外链条目。

Iceberg 文档站点使用 MkDocs 构建，导航结构定义在 `mkdocs.yml` 的 `nav` 部分。在"集成工具/引擎"导航组中已有 BigQuery、Impala、Memiiso Debezium、Presto、Redpanda、RisingWave 等条目，此次新增 OLake 条目，使文档站点的访问者能从导航菜单直接跳转到 OLake 文档，了解如何通过 OLake 将数据库数据接入 Iceberg 湖仓。

## 如何达成设计目的

- 在 `docs/mkdocs.yml` 的 `nav` 导航结构中，集成工具列表的 Memiiso Debezium 之后、Presto 之前插入一行 `- OLake: https://olake.io/docs`。
- 保持字母序排列（Memiiso -> OLake -> Presto）。

## 修改详情

### `docs/mkdocs.yml` (+1)

**修改目的**：在文档导航菜单中新增 OLake 外链条目。

**工作逻辑**：在 `nav` 的集成工具列表中新增 `- OLake: https://olake.io/docs`，位置遵循字母序，位于 Memiiso Debezium 之后。

## 总结

一次纯文档导航配置提交，在 `docs/mkdocs.yml` 的导航菜单中新增 OLake 文档外链条目，使 Iceberg 文档站点访问者可从导航直接访问 OLake 文档。与提交 2570（vendors 页面正文）互补，共同完成 OLake 在 Iceberg 文档中的曝光。无代码逻辑变更。
