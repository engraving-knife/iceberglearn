# 提交 1784：Docs: Add Stackable to the Vendors page (#12344)

## 提交信息

- **序号**：1784 / 4088
- **哈希**：2473b4a1bc6b5e65489cd0d2a72723d814cf7803
- **短哈希**：2473b4a1b
- **日期**：2025-02-25 12:29:21 +0100
- **作者**：Lars Francke
- **提交说明**：Docs: Add Stackable to the Vendors page (#12344)
- **PR/Issue**：#12344

## 总体目的

这个提交将 Stackable 公司添加到 Iceberg 项目文档站点的供应商（Vendors）页面。Stackable 是 Stackable Data Platform 的提供商，该平台是一个模块化的开源数据平台，通过 Trino、Apache NiFi 和 Apache Spark 无缝集成 Apache Iceberg。将 Stackable 添加到供应商页面有助于用户了解支持 Iceberg 的商业产品和服务生态。

## 如何达成设计目的

提交通过在 `site/docs/vendors.md` 文件中 Snowflake 和 Starburst 之间插入 Stackable 的描述段落来达成目标。同时移除了文件末尾的空行。

## 修改详情

### `site/docs/vendors.md`（修改, +8/-1 lines）

**修改目的**：添加 Stackable 供应商信息。

**工作逻辑**：
- 在 Snowflake 条目之后、Starburst 条目之前插入 Stackable 的描述，包含：
  - 供应商名称和链接：`### [Stackable](https://stackable.tech)`
  - 平台简介：Stackable Data Platform 是模块化、开源的数据平台
  - Iceberg 集成方式：通过 Trino、Apache NiFi 和 Apache Spark 集成 Apache Iceberg
  - 平台特点：完全开源、无供应商锁定、支持私有/公有云、24/7 支持和 SLA
- 移除文件末尾的空行。

## 小结

- **成效**：将 Stackable 添加为 Iceberg 供应商页面中的供应商条目。
- **影响范围**：仅影响文档站点的供应商页面，不影响任何功能代码。
- **回迁到 1.4.x 的注意事项**：低优先级回迁。纯文档变更，无风险。可根据 1.4.x 分支的文档维护策略决定是否回迁。无前置依赖。
