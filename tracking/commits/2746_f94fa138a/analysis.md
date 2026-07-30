# 提交 2746：add onelake to docs

## 提交信息

- **序号**：2746 / 4088
- **哈希**：f94fa138a8c5e675983dfd97dc97742543568f58
- **短哈希**：f94fa138a
- **日期**：2025-10-14 09:53:58 -0700
- **作者**：Kevin Liu
- **提交说明**：add onelake to docs
- **PR/Issue**：#14331

## 总体目的

这是一个文档更新提交，将 Microsoft OneLake 添加到 Iceberg 官方文档的供应商（vendors）页面和导航中。Iceberg 的 vendors 页面列出了支持 Apache Iceberg 的各种数据平台和服务，帮助用户了解生态系统中的集成选项。

Microsoft OneLake 是 Microsoft Fabric 内置的统一数据湖服务，近期添加了对 Apache Iceberg REST Catalog (IRC) 规范的支持。将其添加到文档中使 Iceberg 用户了解 OneLake 作为 Iceberg 表存储选项的可用性。

## 如何达成设计目的

通过两个文件的修改完成文档更新：
1. 在 `vendors.md` 中添加 OneLake 的描述段落
2. 在 `nav.yml` 的导航中添加 OneLake 的外部链接

## 修改详情

### `site/docs/vendors.md` (+3/-0 lines)

**修改目的**：添加 Microsoft OneLake 的供应商描述。

**工作逻辑**：在 IOMETE 和 PuppyGraph 之间插入 OneLake 的描述段落，包含：
- 指向 OneLake 文档的链接
- OneLake 的简要介绍：Microsoft Fabric 内置的统一数据湖
- 两个关键 API 的说明：
  - Tables API：支持 Apache Iceberg REST Catalog (IRC) 规范，简化 Iceberg 表的创建、管理和集成
  - Files API：提供完整的 ADLS（Azure Data Lake Storage）兼容性，支持文件操作和 ADLS 工具互操作

### `site/nav.yml` (+1/-0 lines)

**修改目的**：在导航的供应商列表中添加 OneLake 链接。

**工作逻辑**：在导航的 vendors 子项中，按字母顺序在 Memiio Debezium 和 Nimtable 之间添加 `- Microsoft OneLake: https://aka.ms/onelakeircdocs`。

## 总结

本提交是简单的文档更新，将 Microsoft OneLake 添加到 Iceberg 官方文档的供应商页面和导航中。OneLake 作为 Microsoft Fabric 的统一数据湖服务，通过支持 Iceberg REST Catalog 规范成为 Iceberg 生态系统的集成选项之一。这类文档更新有助于用户了解 Iceberg 生态系统中可用的平台和服务。
