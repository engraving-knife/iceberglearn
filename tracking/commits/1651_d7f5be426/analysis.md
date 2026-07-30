# 提交 1651：Docs: Add IBM to Iceberg vendors list (#12101)

## 提交信息

- **序号**：1651 / 4088
- **哈希**：d7f5be426a8664ca80beef982b72fce6c1cd7d63
- **短哈希**：d7f5be426
- **日期**：2025-01-28（Tue Jan 28 07:34:39 2025 -0800）
- **作者**：Ali LeClerc <51137892+alileclerc@users.noreply.github.com>
- **提交说明**：Docs: Add IBM to Iceberg vendors list (#12101)
- **PR/Issue**：#12101

## 总体目的

Iceberg 官网文档的 vendors 页面（`site/docs/vendors.md`）列出了基于 Apache Iceberg 构建产品/服务的厂商，供用户了解生态采用情况。IBM 的 watsonx.data 是一个开放数据湖仓产品，以 Apache Iceberg 作为核心表格式，但此前未收录在 vendors 列表中。本提交把 IBM watsonx.data 加入厂商列表，使文档与实际生态采用情况保持一致，提升 IBM 产品的可见性与 Iceberg 生态的完整性展示。

## 如何达成设计目的

在 `site/docs/vendors.md` 中按字母顺序在 Dremio 之后、IOMETE 之前插入 IBM watsonx.data 的厂商条目，遵循既有条目格式：以 `### [产品名](官网链接)` 为标题，后跟一段产品介绍（说明其如何采用 Iceberg 及核心特性）。

## 修改详情

### `site/docs/vendors.md`（修改，+6）

**修改目的**：新增 IBM watsonx.data 厂商条目。

**工作逻辑**：在 Dremio 条目之后插入：

```
### [IBM watsonx.data](https://www.ibm.com/products/watsonx-data)

[IBM watsonx.data](https://www.ibm.com/products/watsonx.data) is an open data lakehouse for AI and analytics. It uses Apache Iceberg as a core table format, providing features like schema evolution, time travel, and partitioning. ... watsonx.data simplifies the integration of Iceberg tables, ...

Developers can leverage the benefits of Iceberg tables and take advantage of high performance compute capabilities like [Velox](https://velox-lib.io/), [Presto](https://prestodb.io/), [Apache Gluten](https://gluten.apache.org/), which are part of the watsonx.data ecosystem.
```

两段文字：第一段介绍 watsonx.data 基于 Iceberg 提供的数据湖仓能力（schema evolution、time travel、partitioning）；第二段提及生态中的计算引擎（Velox、Presto、Apache Gluten）。链接指向 IBM 官网与各开源项目。

## 小结

- **成效**：补全 Iceberg 生态厂商文档，收录 IBM watsonx.data。
- **影响范围**：仅网站文档，不影响代码或构建。
- **回迁到 1.4.x 的注意事项**：纯文档变更，回迁安全。1.4.x 若维护独立文档站点可同步；注意条目位置应保持字母序（IBM 在 Dremio 与 IOMETE 之间）。链接有效性需 IBM 官网维护。
