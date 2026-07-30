# 提交 2452：Doc: Add Databricks to vendors.md (#13734)

## 提交信息

- **序号**：2452 / 4088
- **哈希**：de22fd0f998ed464251acbe31b976b4bb9669312
- **短哈希**：de22fd0f9
- **日期**：2025-08-04 13:41:08 -0700
- **作者**：lnc
- **提交说明**：Doc: Add Databricks to vendors.md (#13734)
- **PR/Issue**：#13734

## 总体目的

该提交在 Iceberg 项目的厂商列表文档（vendors.md）中添加了 Databricks 的条目。Iceberg 作为一个开放的表格式，支持多种数据处理引擎和平台。vendors.md 页面列出了所有支持 Iceberg 的厂商和产品，方便用户了解生态系统的全貌。

Databricks 是数据湖仓（Lakehouse）架构的领先厂商，其平台通过 Unity Catalog 支持管理包括 Apache Iceberg 在内的多种开放数据格式。将 Databricks 添加到厂商列表是对生态系统现状的准确反映。

## 如何达成设计目的

在 `site/docs/vendors.md` 文件中，在 Crunchy Data Warehouse 和 Dremio 之间插入了一个新的 Databricks 条目，包含厂商名称、链接和简要描述。描述内容涵盖 Databricks 的开放湖仓架构、数据智能平台以及通过 Unity Catalog 管理多种数据格式（包括 Iceberg）的能力。

## 修改详情

### `site/docs/vendors.md` (+4/-0 lines)

**修改目的**：在厂商列表中添加 Databricks 条目。

**工作逻辑**：
在 Crunchy Data Warehouse 条目之后、Dremio 条目之前，添加以下内容：

```markdown
### [Databricks](https://www.databricks.com/)

[Databricks](https://www.databricks.com/) uses an open lakehouse architecture to power its Data Intelligence Platform and provide a unified foundation for all data and governance, combined with AI models tuned to an organization's unique characteristics. Through [Unity Catalog](https://www.databricks.com/product/unity-catalog), users can manage and govern all structured data, unstructured data, business metrics and AI models across open data formats like Delta Lake, Apache Iceberg, Hudi, Parquet and more.
```

该条目遵循了 vendors.md 中其他厂商条目的格式：三级标题包含厂商名称和链接，随后是一段描述性文字说明厂商对 Iceberg 的支持情况。

## 总结

这是一个纯文档提交，在 Iceberg 的厂商列表中添加了 Databricks。这反映了 Databricks 通过 Unity Catalog 对 Apache Iceberg 的支持，丰富了 Iceberg 生态系统的文档记录。该提交不涉及任何代码修改。
