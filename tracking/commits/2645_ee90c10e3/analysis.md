# 提交 2645：Site: Updates vendors.md adding Fivetran (#14088)

## 提交信息

- **序号**：2645 / 4088
- **哈希**：ee90c10e39cec0ccceb9425e03a3e0b5690daf3b
- **短哈希**：ee90c10e3
- **日期**：2025-09-16 12:35:42 -0500
- **作者**：fivetran-caseykarst
- **提交说明**：Site: Updates vendors.md adding Fivetran (#14088)
- **PR/Issue**：#14088

## 总体目的

Iceberg 项目的 `vendors.md` 页面列出了支持 Apache Iceberg 的厂商和产品。本提交由 Fivetran 公司贡献，将其产品添加到厂商列表中，使访问 Iceberg 官方文档的用户能了解到 Fivetran 作为 Iceberg 生态厂商的存在。

Fivetran 是一家数据移动（data movement）公司，其 Managed Data Lake Service 提供全托管的 Iceberg 数据湖，支持将 700+ 数据源写入用户选择的存储位置，并托管 Iceberg REST 兼容的 catalog 端点供下游消费。

## 如何达成设计目的

在 `vendors.md` 中按字母顺序在 Firebolt 之后、IBM watsonx.data 之前新增 Fivetran 的条目，包含产品名称、链接和简介描述。

## 修改详情

### `site/docs/vendors.md` (+4/-0 lines)

**修改目的**：添加 Fivetran 厂商条目。

**工作逻辑**：在 Firebolt 条目之后新增 Fivetran 小节，包含指向 fivetran.com 的链接、产品描述（Managed Data Lake Service、700+ 连接、Iceberg REST catalog 端点等）。

## 总结

这是一次纯文档更新，将 Fivetran 添加到 Iceberg 厂商列表页面，使其产品对 Iceberg 社区可见。不影响任何代码逻辑。
