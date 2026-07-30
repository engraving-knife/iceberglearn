# 提交 2757：Add Google Cloud vendor description (#14338)

## 提交信息

- **序号**：2757 / 4088
- **哈希**：6d7111b5ccc29513a530a02014556d5a272b6350
- **短哈希**：6d7111b5c
- **日期**：2025-10-16 10:49:12 -0700
- **作者**：Alex Stephen
- **提交说明**：Add Google Cloud vendor description (#14338)
- **PR/Issue**：#14338

## 总体目的

本提交将 Google Cloud 作为支持 Apache Iceberg 的供应商加入到 Iceberg 官方文档的 vendors.md 列表中。

背景在于：Google Cloud 通过 BigLake 系列产品为 Apache Iceberg 提供了一等公民级别的支持。BigLake metastore 是一个无服务器（serverless）的元数据存储，可跨 Apache Spark、BigQuery 及第三方平台创建和管理 Iceberg 表，提供统一的数据视图和访问控制，并支持 Apache Iceberg REST Catalog 以便与开源及第三方引擎集成。BigLake tables for Apache Iceberg 配合 BigQuery 可提供企业级、全托管的 Iceberg 体验。

将 Google Cloud 加入供应商文档有助于让用户了解 Google Cloud 生态对 Iceberg 的支持能力，便于在选型时参考。

## 如何达成设计目的

在 `site/docs/vendors.md` 文件中，按字母顺序在 Fivetran 与 IBM watsonx.data 之间插入一个新的 `### Google Cloud` 章节，包含一段描述 Google Cloud 通过 BigLake 支持 Iceberg 的说明文字，并附上相关产品链接。

## 修改详情

### `site/docs/vendors.md` (+12/-0 lines)

**修改目的**：在 Iceberg 供应商文档中新增 Google Cloud 条目。

**工作逻辑**：在 Fivetran 章节之后、IBM watsonx.data 章节之前，新增 `### [Google Cloud](https://cloud.google.com)` 小节。内容描述：Google Cloud 通过 BigLake 为 Apache Iceberg 提供一等公民支持，帮助构建开放、托管、高性能的 Iceberg 湖仓；BigLake metastore 是无服务器元数据存储，跨 Spark、BigQuery 和第三方平台管理 Iceberg 表，提供统一数据视图和访问控制，支持 Iceberg REST Catalog；BigLake tables for Apache Iceberg 配合 BigQuery 提供企业级全托管体验。包含指向 `cloud.google.com/biglake` 的链接。

## 总结

这是一个纯文档类提交，将 Google Cloud 作为 Iceberg 生态供应商加入到官方文档。改动仅 12 行新增，但完善了 Iceberg 供应商列表，向用户告知 Google Cloud BigLake 对 Iceberg 的 metastore 和托管表支持。该 PR 由 Alex Stephen 发起，Kevin Liu 和 Yuya Ebihara 共同参与（根据 review 反馈更新了文档内容）。
